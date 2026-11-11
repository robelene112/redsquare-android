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
internal enum class IdentityNameOrigin { GENERATED, REGISTRAR, SELECTED_NODE, DIRECTORY }
internal data class RegisteredIdentityLabel(val name: String, val saveError: Boolean = false,
    val origin: IdentityNameOrigin = IdentityNameOrigin.REGISTRAR)
private val LocalIdentities = staticCompositionLocalOf<RegisteredIdentities?> { null }
/** Cached names only remain display identities for one day after the selected backend confirmed them. */
internal const val REGISTERED_NAME_FRESHNESS_MILLIS = 24 * 60 * 60 * 1000L

internal fun registeredNameIsCurrent(saved: RegisteredNameStore.Saved, backend: SaitoBackend, nowMillis: Long): Boolean =
    saved.backend == backend && saved.checkedAt <= nowMillis &&
        nowMillis - saved.checkedAt <= REGISTERED_NAME_FRESHNESS_MILLIS

/** One wallet/backend scope shares labels across navigation and batches visible authors. */
@Composable
internal fun RegisteredIdentityScope(wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend,
    directoryEndpoint: String? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val registrar = LocalRegistryAuthority.current
    val scope = rememberCoroutineScope()
    val identities = remember(wallet, endpoint, backend, registrar, directoryEndpoint) {
        val lifetime = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        RegisteredIdentities(context, wallet, endpoint, backend, registrar, lifetime, directoryEndpoint)
    }
    DisposableEffect(identities) { onDispose { identities.close() } }
    LaunchedEffect(identities) { identities.discover() }
    CompositionLocalProvider(LocalIdentities provides identities, content = content)
}

/** Anonymous labels render immediately; cache and signed discovery never gate content. */
@Composable
internal fun registeredIdentity(publicKey: String): RegisteredIdentityLabel {
    val identities = requireNotNull(LocalIdentities.current)
    DisposableEffect(identities, publicKey) {
        identities.retain(publicKey)
        onDispose { identities.release(publicKey) }
    }
    return identities.labels[publicKey] ?: identities.directoryLabels[publicKey]
        ?: RegisteredIdentityLabel("Anon-${publicKey.take(6)}", origin = IdentityNameOrigin.GENERATED)
}

/** Searches verified cached names for known keys without querying a global directory. */
@Composable
internal fun knownRegisteredIdentities(publicKeys: Set<String>): Map<String, RegisteredIdentityLabel> {
    val identities = requireNotNull(LocalIdentities.current)
    LaunchedEffect(identities, publicKeys) { identities.loadCached(publicKeys) }
    return identities.directoryLabels.toMap() + identities.labels.toMap()
}

private class RegisteredIdentities(
    private val context: Context,
    private val wallet: SaitoWallet?,
    private val endpoint: String,
    private val backend: SaitoBackend,
    private val registrar: String,
    private val scope: CoroutineScope,
    private val directoryEndpoint: String?,
) {
    val labels = mutableStateMapOf<String, RegisteredIdentityLabel>()
    val directoryLabels = mutableStateMapOf<String, RegisteredIdentityLabel>()
    private val inFlight = mutableSetOf<String>()
    private val pending = Channel<String>(Channel.UNLIMITED)
    private val directoryInFlight = mutableSetOf<String>()
    private val directoryPending = Channel<String>(Channel.UNLIMITED)
    private val visible = mutableMapOf<String, Int>()
    private val directoryRetries = mutableMapOf<String, Job>()
    private val failures = mutableMapOf<String, Int>()

    fun close() { scope.cancel(); pending.close(); directoryPending.close() }

    fun retain(publicKey: String) {
        if (!SaitoWallet.isPublicKey(publicKey)) return
        visible[publicKey] = (visible[publicKey] ?: 0) + 1
        request(publicKey)
    }

    fun release(publicKey: String) {
        val remaining = (visible[publicKey] ?: 1) - 1
        if (remaining > 0) visible[publicKey] = remaining
        else {
            visible.remove(publicKey)
            directoryRetries.remove(publicKey)?.cancel()
            failures.remove(publicKey)
        }
    }

    private fun requestDirectory(publicKey: String) {
        if (wallet != null && directoryEndpoint != null && publicKey !in labels &&
            directoryInFlight.add(publicKey)) directoryPending.trySend(publicKey)
    }

    private fun retryDirectory(publicKey: String, successfulRead: Boolean) {
        if (publicKey !in visible || directoryEndpoint == null) return
        directoryRetries.remove(publicKey)?.cancel()
        val pause = if (successfulRead) { failures.remove(publicKey); 5 * 60_000L }
            else { val attempt = ((failures[publicKey] ?: 0) + 1).coerceAtMost(7)
                failures[publicKey] = attempt
                (1_000L shl (attempt - 1)).coerceAtMost(60_000L) }
        directoryRetries[publicKey] = scope.launch {
            delay(pause)
            directoryRetries.remove(publicKey)
            if (publicKey in visible) requestDirectory(publicKey)
        }
    }

    suspend fun loadCached(publicKeys: Set<String>) {
        val missing = publicKeys.filter { it !in labels }
        val saved = withContext(Dispatchers.IO) {
            missing.mapNotNull { key ->
                try {
                    cache(key).load()?.takeIf { registeredNameIsCurrent(it, backend, System.currentTimeMillis()) }
                        ?.name?.identifier?.let { key to RegisteredIdentityLabel(it) }
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }
        }
        // A visible row can have obtained a newer proof while disk validation was suspended.
        saved.forEach { (key, label) -> if (key !in labels) labels[key] = label }
    }

    fun request(publicKey: String) {
        if (!SaitoWallet.isPublicKey(publicKey)) return
        if (publicKey !in directoryLabels && publicKey !in directoryRetries) requestDirectory(publicKey)
        // A newly mounted view can retry a failed save. Other repeated rows share one result.
        if (labels[publicKey]?.saveError == false || !inFlight.add(publicKey)) return
        scope.launch {
            val cached = try {
                withContext(Dispatchers.IO) {
                    cache(publicKey).load()?.takeIf { registeredNameIsCurrent(it, backend, System.currentTimeMillis()) }
                        ?.name?.identifier
                }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (cached != null) {
                labels[publicKey] = RegisteredIdentityLabel(cached)
                inFlight.remove(publicKey)
            } else if (wallet != null) pending.send(publicKey)
            else inFlight.remove(publicKey)
        }
    }

    suspend fun discover() = coroutineScope {
        if (directoryEndpoint != null) launch { discoverDirectory() }
        discoverSelected()
    }

    private suspend fun discoverDirectory() {
        for (first in directoryPending) {
            val keys = mutableListOf(first)
            delay(20)
            while (keys.size < 50) keys += directoryPending.tryReceive().getOrNull() ?: break
            var successfulRead = false
            try {
                val names = withContext(Dispatchers.IO) {
                    // A disposable read-only peer identity is separate from the user's account wallet.
                    SaitoPeer(SaitoWallet.create(), requireNotNull(directoryEndpoint)).use { peer ->
                        RegistryLookup(peer, registrar).hints(keys)
                    }
                }
                successfulRead = true
                names.forEach { (key, name) ->
                    directoryLabels[key] = RegisteredIdentityLabel(name, origin = IdentityNameOrigin.DIRECTORY)
                }
            } catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* A directory outage never changes author identity or ownership. */ }
            finally { keys.forEach { directoryInFlight.remove(it); retryDirectory(it, successfulRead) } }
        }
    }

    private suspend fun discoverSelected() {
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
