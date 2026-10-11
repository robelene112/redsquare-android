package nl.davidengelen.redsquare.registry

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import nl.davidengelen.redsquare.saito.*

internal val LocalRegistryAuthority = staticCompositionLocalOf { SaitoRegistry.REGISTRAR }
internal data class RegisteredIdentityLabel(val name: String, val saveError: Boolean = false)
private val LocalIdentities = staticCompositionLocalOf<RegisteredIdentities?> { null }

/** One wallet/backend scope shares labels across navigation and batches visible authors. */
@Composable
internal fun RegisteredIdentityScope(wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val registrar = LocalRegistryAuthority.current
    val scope = rememberCoroutineScope()
    val identities = remember(wallet, endpoint, backend, registrar) {
        RegisteredIdentities(context, wallet, endpoint, backend, registrar, scope)
    }
    LaunchedEffect(identities) { identities.discover() }
    CompositionLocalProvider(LocalIdentities provides identities, content = content)
}

/** Anonymous labels render immediately; cache and signed discovery never gate content. */
@Composable
internal fun registeredIdentity(publicKey: String): RegisteredIdentityLabel {
    val identities = requireNotNull(LocalIdentities.current)
    LaunchedEffect(identities, publicKey) { identities.request(publicKey) }
    return identities.labels[publicKey] ?: RegisteredIdentityLabel("Anon-${publicKey.take(6)}")
}

/** Searches verified cached names for known keys without querying a global directory. */
@Composable
internal fun knownRegisteredIdentities(publicKeys: Set<String>): Map<String, RegisteredIdentityLabel> {
    val identities = requireNotNull(LocalIdentities.current)
    LaunchedEffect(identities, publicKeys) { identities.loadCached(publicKeys) }
    return identities.labels.toMap()
}

private class RegisteredIdentities(
    private val context: Context,
    private val wallet: SaitoWallet?,
    private val endpoint: String,
    private val backend: SaitoBackend,
    private val registrar: String,
    private val scope: CoroutineScope,
) {
    val labels = mutableStateMapOf<String, RegisteredIdentityLabel>()
    private val inFlight = mutableSetOf<String>()
    private val pending = Channel<String>(Channel.UNLIMITED)

    suspend fun loadCached(publicKeys: Set<String>) {
        val missing = publicKeys.filter { it !in labels }
        val saved = withContext(Dispatchers.IO) {
            missing.mapNotNull { key ->
                try { cache(key).load()?.name?.identifier?.let { key to RegisteredIdentityLabel(it) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }
        }
        // A visible row can have obtained a newer proof while disk validation was suspended.
        saved.forEach { (key, label) -> if (key !in labels) labels[key] = label }
    }

    fun request(publicKey: String) {
        // A newly mounted view can retry a failed save. Other repeated rows share one result.
        if (labels[publicKey]?.saveError == false || !inFlight.add(publicKey)) return
        scope.launch {
            val cached = try { withContext(Dispatchers.IO) { cache(publicKey).load()?.name?.identifier } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (cached != null) {
                labels[publicKey] = RegisteredIdentityLabel(cached)
                inFlight.remove(publicKey)
            } else if (wallet != null) pending.send(publicKey)
            else inFlight.remove(publicKey)
        }
    }

    suspend fun discover() {
        for (first in pending) {
            val keys = mutableListOf(first)
            delay(20) // Gather authors composed in the same frame without blocking rendering.
            while (keys.size < 50) keys += pending.tryReceive().getOrNull() ?: break
            try {
                withContext(Dispatchers.IO) {
                    SaitoPeer(requireNotNull(wallet), endpoint).use { peer ->
                        val lookup = RegistryLookup(peer, registrar)
                        val hints = lookup.hints(keys)
                        val limit = Semaphore(4)
                        coroutineScope {
                            hints.map { (publicKey, name) -> launch {
                                limit.withPermit {
                                    try {
                                        val record = lookup.namecheck(name) ?: return@withPermit
                                        require(record.publicKey == publicKey && record.active)
                                        val saveError = try { cache(publicKey).save(record, backend); false }
                                        catch (cancelled: CancellationException) { throw cancelled }
                                        catch (_: Exception) { true }
                                        withContext(Dispatchers.Main) {
                                            labels[publicKey] = RegisteredIdentityLabel(record.identifier, saveError)
                                        }
                                    } catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { /* A hint is never ownership evidence. */ }
                                }
                            } }.joinAll()
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Unavailable discovery preserves cached/generated labels. */ }
            finally { keys.forEach { inFlight.remove(it) } }
        }
    }

    private fun cache(publicKey: String) = RegisteredNameStore(context, publicKey, registrar)
}
