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
    val origin: IdentityNameOrigin = IdentityNameOrigin.REGISTRAR,
    val cached: Boolean = false, val checkedAt: Long? = null)
private val LocalIdentities = staticCompositionLocalOf<RegisteredIdentities?> { null }
/** The signed ownership cache is current for one day after the selected backend confirmed it. */
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

/** Searches known display labels without adding new directory requests. */
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
    private val directoryRetry = VisibleNameRetry(scope, { it in visible && it !in labels }, ::requestDirectory)
    private val selectedRetry = VisibleNameRetry(scope, { it in visible && it !in labels }, ::request)
    private val displayStores = buildMap {
        wallet?.publicKey?.let { owner ->
            put(IdentityNameOrigin.SELECTED_NODE, DirectoryNameStore(context, owner, backend, registrar,
                endpoint, IdentityNameOrigin.SELECTED_NODE))
            directoryEndpoint?.let { provider ->
                put(IdentityNameOrigin.DIRECTORY, DirectoryNameStore(context, owner, backend, registrar,
                    provider, IdentityNameOrigin.DIRECTORY))
            }
        }
    }
    private val proofInFlight = mutableSetOf<String>()
    private val proofLimit = Semaphore(4)
    private var selectedConnection: SaitoPeer? = null


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
            directoryRetry.release(publicKey)
            selectedRetry.release(publicKey)
        }
    }

    private fun requestDirectory(publicKey: String) {
        if (wallet != null && directoryEndpoint != null && publicKey !in labels &&
            directoryInFlight.add(publicKey)) directoryPending.trySend(publicKey)
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
                closeSelectedWhenIdle()
            } else {
                if (!directoryRetry.isScheduled(publicKey)) requestDirectory(publicKey)
                if (wallet != null) pending.send(publicKey) else inFlight.remove(publicKey)
            }
        }
    }

    suspend fun discover() = coroutineScope {
        for (store in displayStores.values) {
            try {
                val saved = withContext(Dispatchers.IO) { store.load() }
                saved.forEach { showDirectoryLabel(it, cached = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* One unavailable display cache cannot hide the other provider's names. */ }
        }
        if (directoryEndpoint != null) launch { discoverDirectory() }
        discoverSelected()
    }

    private fun showDirectoryLabel(saved: DirectoryNameStore.Saved, cached: Boolean) {
        val previous = directoryLabels[saved.key]
        if (previous?.origin == IdentityNameOrigin.DIRECTORY && saved.origin != IdentityNameOrigin.DIRECTORY) return
        directoryLabels[saved.key] = RegisteredIdentityLabel(saved.name, origin = saved.origin,
            cached = cached, checkedAt = saved.checkedAt)
        while (directoryLabels.size > DirectoryNameStore.LIMIT) {
            val discard = directoryLabels.keys.firstOrNull { it !in visible } ?: directoryLabels.keys.first()
            directoryLabels.remove(discard)
        }
    }

    private suspend fun saveDirectoryLabels(names: Map<String, String>, origin: IdentityNameOrigin) {
        if (names.isEmpty()) return
        val now = System.currentTimeMillis()
        val saved = names.map { (key, name) -> DirectoryNameStore.Saved(key, name, origin, now) }
        try { withContext(Dispatchers.IO) {
            val commitJob = currentCoroutineContext().job
            synchronized(SaitoLocalState.lock) {
                commitJob.ensureActive()
                displayStores.getValue(origin).save(saved)
            }
        } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* The reported label remains useful even when its disposable cache cannot be written. */ }
        saved.forEach { showDirectoryLabel(it, cached = false) }
    }

    private suspend fun discoverDirectory() {
        for (first in directoryPending) {
            val keys = mutableListOf(first)
            delay(20)
            while (keys.size < 50) keys += directoryPending.tryReceive().getOrNull() ?: break
            val dropped = keys.filter { it !in visible || it in labels }
            dropped.forEach { directoryInFlight.remove(it) }
            keys.removeAll(dropped.toSet())
            if (keys.isEmpty()) continue
            var successfulRead = false
            try {
                val names = withContext(Dispatchers.IO) {
                    // A disposable read-only peer identity is separate from the user's account wallet.
                    SaitoPeer(SaitoWallet.create(), requireNotNull(directoryEndpoint)).use { peer ->
                        RegistryLookup(peer, registrar).hints(keys)
                    }
                }
                successfulRead = true
                saveDirectoryLabels(names, IdentityNameOrigin.DIRECTORY)
            } catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* A directory outage never changes author identity or ownership. */ }
            finally {
                keys.forEach { directoryInFlight.remove(it) }
                directoryRetry.afterRead(keys, successfulRead)
            }
        }
    }

    private suspend fun discoverSelected() {
        try {
            for (first in pending) {
                val keys = mutableListOf(first)
                delay(20)
                while (keys.size < 50) keys += pending.tryReceive().getOrNull() ?: break
                val dropped = keys.filter { it !in visible || labels[it]?.saveError == false }
                dropped.forEach { inFlight.remove(it) }
                keys.removeAll(dropped.toSet())
                if (keys.isEmpty()) { closeSelectedWhenIdle(); continue }
                var successfulRead = false
                try {
                    val peer = selectedConnection ?: SaitoPeer(requireNotNull(wallet), endpoint)
                        .also { selectedConnection = it }
                    val hints = withContext(Dispatchers.IO) { RegistryLookup(peer, registrar).hints(keys) }
                    successfulRead = true
                    // Start ownership requests eagerly, but never await their callbacks to display a map.
                    // The authenticated connection can serve the next map while these checks are pending.
                    hints.forEach { (key, name) -> verifyHint(peer, key, name) }
                    saveDirectoryLabels(hints, IdentityNameOrigin.SELECTED_NODE)
                } catch (_: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    selectedConnection?.close(); selectedConnection = null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { selectedConnection?.close(); selectedConnection = null }
                finally {
                    keys.forEach { inFlight.remove(it) }
                    selectedRetry.afterRead(keys, successfulRead)
                    closeSelectedWhenIdle()
                }
            }
        } finally { selectedConnection?.close(); selectedConnection = null }
    }

    private fun verifyHint(peer: SaitoPeer, publicKey: String, name: String) {
        if (!proofInFlight.add(publicKey)) return
        scope.launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try {
                proofLimit.withPermit {
                    if (!withContext(Dispatchers.Main.immediate) {
                        publicKey in visible && labels[publicKey]?.saveError != false
                    }) return@withPermit
                    val record = RegistryLookup(peer, registrar).namecheck(name) ?: return@withPermit
                    require(record.publicKey == publicKey && record.active)
                    val commitJob = currentCoroutineContext().job
                    val saveError = try {
                        synchronized(SaitoLocalState.lock) {
                            commitJob.ensureActive()
                            cache(publicKey).save(record, backend)
                        }
                        false
                    }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { true }
                    withContext(Dispatchers.Main) {
                        labels[publicKey] = RegisteredIdentityLabel(record.identifier, saveError)
                    }
                }
            } catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Display labels do not become ownership evidence after a failed proof. */ }
            finally { withContext(NonCancellable + Dispatchers.Main) {
                proofInFlight.remove(publicKey)
                closeSelectedWhenIdle()
            } }
        }
    }

    private fun closeSelectedWhenIdle() {
        if (inFlight.isEmpty() && proofInFlight.isEmpty()) {
            selectedConnection?.close()
            selectedConnection = null
        }
    }

    private fun cache(publicKey: String) = RegisteredNameStore(context, publicKey, registrar)
}

/** Both read-only lookup sources share bounded backoff; only retained rows keep retries alive. */
private class VisibleNameRetry(private val scope: CoroutineScope, private val needed: (String) -> Boolean,
    private val request: (String) -> Unit) {
    private val jobs = mutableMapOf<String, Job>()
    private val failures = mutableMapOf<String, Int>()

    fun isScheduled(key: String): Boolean = key in jobs

    fun release(key: String) { jobs.remove(key)?.cancel(); failures.remove(key) }

    fun afterRead(keys: Collection<String>, successful: Boolean) {
        keys.forEach { key ->
            if (!needed(key) || !scope.isActive) { release(key); return@forEach }
            jobs.remove(key)?.cancel()
            val pause = if (successful) { failures.remove(key); 5 * 60_000L }
                else {
                    val attempt = ((failures[key] ?: 0) + 1).coerceAtMost(7)
                    failures[key] = attempt
                    (1_000L shl (attempt - 1)).coerceAtMost(60_000L)
                }
            jobs[key] = scope.launch {
                delay(pause)
                jobs.remove(key)
                if (needed(key)) request(key)
            }
        }
    }
}
