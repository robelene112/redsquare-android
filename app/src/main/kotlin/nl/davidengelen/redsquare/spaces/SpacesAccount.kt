package nl.davidengelen.redsquare.spaces

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.IOException
import java.security.MessageDigest
import java.time.Clock

/** Account-owned work survives navigation. Local writes never wait for a held
 * read callback; only network work is serialized. Every snapshot keeps its scope. */
internal class SpacesAccount(context: Context, private val wallet: SaitoWallet, private val backend: SaitoBackend,
    private val endpoint: String, parent: CoroutineScope) {
    enum class Availability { CHECKING, ONLINE, OFFLINE, UNSUPPORTED, CHANGED_IDENTITY, STORAGE_UNAVAILABLE }
    enum class Problem { CONNECTION, SAVE, INVITATION }
    data class State(val loading: Boolean = true, val availability: Availability = Availability.CHECKING,
        val communities: List<SpaceClient.Community> = emptyList(), val selected: String? = null,
        val messages: List<SpaceClient.Message> = emptyList(), val draft: String = "",
        val invitation: SpaceClient.Invitation? = null, val preview: SpaceClient.InvitationPreview? = null,
        val problem: Problem? = null, val working: Boolean = false, val refreshing: Boolean = false,
        val ready: Boolean = false)
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]) + Dispatchers.IO)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val network = Mutex()
    private val localAccess = Mutex()
    private val closed = AtomicBoolean()
    private val selection = AtomicLong()
    private val previews = AtomicLong()
    val ownerKey: String get() = wallet.publicKey
    private val draftVersions = ConcurrentHashMap<String, AtomicLong>()
    @Volatile private var peer: SaitoPeer? = null
    @Volatile private var client: SpaceClient? = null
    private var local: AndroidSpaceClientStore? = null
    private var cachedService: String? = null
    private var cacheFailed = false
    private var ticker: Job? = null
    private val namespace = MessageDigest.getInstance("SHA-256").digest("${wallet.publicKey}:$endpoint".toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private val filename = "spaces-directory-$namespace.v1"
    private val directory = EncryptedLocalFile(appContext, filename,
        "${appContext.packageName}.spaces.directory.$namespace", filename)
    private class MissingService : IOException()
    private class ChangedIdentity : IOException()

    init {
        scope.launch {
            try {
                val saved = directory.load()
                if (saved != null) {
                    require(saved.version == 1.toByte() && SaitoWallet.isPublicKey(saved.text))
                    cachedService = saved.text
                    installClient(saved.text)
                    publish()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                cacheFailed = true
                mutable.update { it.copy(availability = Availability.STORAGE_UNAVAILABLE) }
            }
            mutable.update { it.copy(loading = false) }
            for (ignored in wake) {
                if (cacheFailed) continue
                mutable.update { it.copy(refreshing = true) }
                try { network.withLock {
                    val service = authenticatedPeer().servicePublicKey("spaces-v1")
                    if (cachedService != null && cachedService != service) throw ChangedIdentity()
                    if (client == null) {
                        directory.save(EncryptedLocalFile.Contents(1, service))
                        cachedService = service
                        installClient(service)
                    }
                    mutable.update { it.copy(availability = Availability.ONLINE, ready = true) }
                    val current = requireNotNull(client)
                    // Deliver authorized originals for all cached communities, even
                    // when the user navigated to another one before the response.
                    for (community in current.communities()) {
                        current.flush(community.id)
                    }
                    val selected = mutable.value.selected
                    if (selected != null) {
                        val community = current.community(selected)
                        if (community.joined) {
                            current.refresh(selected)
                            if (!current.community(selected).pending) {
                                current.admitPending(selected)
                                current.flush(selected)
                            }
                        } else {
                            // A queued join is not membership. A private read can
                            // remain denied until the inviter accepts its request.
                            current.refresh(selected)
                        }
                    }
                    publish()
                } } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: MissingService) { mutable.update { it.copy(availability = Availability.UNSUPPORTED) } }
                catch (_: ChangedIdentity) { mutable.update { it.copy(availability = Availability.CHANGED_IDENTITY) } }
                catch (_: Exception) {
                    peer?.close(); peer = null
                    mutable.update { it.copy(availability = Availability.OFFLINE) }
                    try { publish() } catch (_: Exception) { mutable.update { it.copy(availability = Availability.STORAGE_UNAVAILABLE) } }
                } finally { mutable.update { it.copy(refreshing = false) } }
            }
        }
    }

    private fun installClient(service: String) {
        check(client == null)
        val storage = AndroidSpaceClientStore(appContext, wallet.publicKey, "$backend:$endpoint", service)
        local = storage
        client = SpaceClient(wallet, backend.webUrl, service, storage, Clock.systemUTC()) { original ->
            val connection = authenticatedPeer()
            if (connection.servicePublicKey("spaces-v1") != service) throw ChangedIdentity()
            connection.request(original) { check(!closed.get()) }.toString(Charsets.UTF_8)
        }
        mutable.update { it.copy(ready = true) }
    }

    private suspend fun authenticatedPeer(): SaitoPeer {
        val connection = peer ?: SaitoPeer(wallet, endpoint).also { peer = it }
        if ("spaces-v1" !in connection.services()) throw MissingService()
        return connection
    }

    private suspend fun publish() = localAccess.withLock {
        val current = client ?: return@withLock
        val communities = current.communities()
        val selected = mutable.value.selected?.takeIf { id -> communities.any { it.id == id } }
        val messages = selected?.let(current::timeline).orEmpty()
        val invitation = selected?.let(current::invitation)
        mutable.update { previous ->
            if (previous.selected != selected) previous.copy(communities = communities)
            else previous.copy(communities = communities, messages = messages, invitation = invitation)
        }
    }

    fun visible(active: Boolean) {
        ticker?.cancel(); ticker = null
        if (active) ticker = scope.launch {
            while (isActive) { wake.trySend(Unit); delay(5_000) }
        }
    }
    fun refresh() { wake.trySend(Unit) }
    fun select(space: String?) {
        if (closed.get()) return
        val token = selection.incrementAndGet()
        val draftVersion = space?.let { draftVersions.getOrPut(it) { AtomicLong() }.get() }
        mutable.update { it.copy(selected = space, messages = emptyList(), draft = "", invitation = null, problem = null) }
        scope.launch {
            try {
                val current = client ?: return@launch
                val (draft, messages) = localAccess.withLock {
                    space?.let(current::draft).orEmpty() to space?.let(current::timeline).orEmpty()
                }
                mutable.update { if (it.selected == space && selection.get() == token) it.copy(
                    draft = if (space == null || draftVersions[space]?.get() == draftVersion) draft else it.draft,
                    messages = messages) else it }
                publish(); wake.trySend(Unit)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(problem = Problem.SAVE) } }
        }
    }
    fun editDraft(text: String) {
        val space = mutable.value.selected ?: return
        if (closed.get()) return
        val version = draftVersions.getOrPut(space) { AtomicLong() }.incrementAndGet()
        mutable.update { it.copy(draft = text) }
        scope.launch {
            try { localAccess.withLock {
                if (draftVersions[space]?.get() == version) client?.saveDraft(space, text)
            } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { if (it.selected == space) it.copy(problem = Problem.SAVE) else it } }
        }
    }
    fun send() {
        val before = mutable.value
        val space = before.selected ?: return
        localAction {
            localAccess.withLock {
                requireNotNull(client).send(space, before.draft)
                mutable.update { if (it.selected == space && it.draft == before.draft) {
                    draftVersions.getOrPut(space) { AtomicLong() }.incrementAndGet()
                    it.copy(draft = "")
                } else it }
            }
            publish(); wake.trySend(Unit)
        }
    }
    fun create(name: String) = localAction {
        val space = localAccess.withLock { requireNotNull(client).create(name) }
        select(space); publish(); wake.trySend(Unit)
    }
    fun invite() {
        val space = mutable.value.selected ?: return
        localAction { localAccess.withLock { requireNotNull(client).invite(space) }; publish(); wake.trySend(Unit) }
    }
    fun preview(link: String) {
        val token = previews.incrementAndGet()
        mutable.update { it.copy(preview = null) }
        localAction(Problem.INVITATION) { network.withLock {
            val current = client ?: run {
                val service = authenticatedPeer().servicePublicKey("spaces-v1")
                directory.save(EncryptedLocalFile.Contents(1, service)); cachedService = service; installClient(service)
                requireNotNull(client)
            }
            try {
                val preview = current.preview(link)
                if (previews.get() == token) mutable.update { it.copy(preview = preview) }
            } catch (failure: Exception) { if (previews.get() == token) throw failure }
        } }
    }
    fun join(link: String) = localAction(Problem.INVITATION) { network.withLock {
        val space = requireNotNull(client).join(link)
        mutable.update { it.copy(preview = null) }
        select(space); publish(); wake.trySend(Unit)
    } }
    fun dismissPreview() { previews.incrementAndGet(); mutable.update { it.copy(preview = null) } }
    fun clearProblem() { mutable.update { it.copy(problem = null) } }
    private fun localAction(problem: Problem = Problem.SAVE, action: suspend () -> Unit) {
        if (closed.get() || mutable.value.working || mutable.value.loading) return
        mutable.update { it.copy(working = true, problem = null) }
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(problem = problem) } }
            finally { mutable.update { it.copy(working = false) } }
        }
    }
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel(); wake.close(); peer?.close()
        CoroutineScope(Dispatchers.IO).launch {
            scope.coroutineContext[Job]?.join()
            peer?.close(); peer = null
            local?.close()
        }
    }
}
