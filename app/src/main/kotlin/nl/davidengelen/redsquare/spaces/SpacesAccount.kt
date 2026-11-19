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
        val ready: Boolean = false, val savingNavigation: Boolean = false, val loadingChannel: Boolean = false, val hasEarlier: Boolean = false, val hasLater: Boolean = false, val scrollToMessage: String? = null, val windowFrom: Long = 0)
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
    private val historyWindows = ConcurrentHashMap<String, Long>()
    val ownerKey: String get() = wallet.publicKey
    private val draftVersions = ConcurrentHashMap<String, AtomicLong>()
    private val dirtyDrafts = ConcurrentHashMap<String, String>()
    private val navigationSaving = AtomicBoolean()
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
        val window = selected?.let { current.historyWindow(it, historyWindows[it]) }
        val messages = selected?.let { current.timeline(it, from = window?.from) }.orEmpty()
        val invitation = selected?.let(current::invitation)
        mutable.update { previous ->
            if (previous.selected != selected) previous.copy(communities = communities)
            else previous.copy(communities = communities, messages = messages, invitation = invitation,
                hasEarlier = window?.hasEarlier == true, hasLater = window?.hasLater == true, windowFrom = window?.from ?: 0)
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
        mutable.update { it.copy(selected = space, messages = emptyList(), draft = "", invitation = null, problem = null, loadingChannel = space != null, scrollToMessage = null) }
        scope.launch {
            try {
                val current = client ?: return@launch
                val (draft, messages) = localAccess.withLock {
                    space?.let { dirtyDrafts[it] ?: current.draft(it) }.orEmpty() to space?.let { current.timeline(it, from = historyWindows[it]) }.orEmpty()
                }
                mutable.update { if (it.selected == space && selection.get() == token) it.copy(
                    draft = if (space == null || draftVersions[space]?.get() == draftVersion) draft else it.draft,
                    messages = messages, loadingChannel = false) else it }
                publish(); wake.trySend(Unit)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { if (it.selected == space && selection.get() == token)
                it.copy(problem = Problem.SAVE, loadingChannel = false) else it } }
        }
    }
    fun editDraft(text: String) {
        mutable.value.selected?.let { editDraft(it, text) }
    }
    fun editDraft(space: String, text: String) {
        if (closed.get() || navigationSaving.get()) return
        dirtyDrafts[space] = text
        val version = draftVersions.getOrPut(space) { AtomicLong() }.incrementAndGet()
        mutable.update { if (it.selected == space) it.copy(draft = text) else it }
        scope.launch {
            try { localAccess.withLock {
                if (draftVersions[space]?.get() == version) {
                    requireNotNull(client).saveDraft(space, text)
                    dirtyDrafts.remove(space, text)
                }
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
                val current = requireNotNull(client)
                if (mutable.value.selected == space && mutable.value.draft == before.draft) current.saveDraft(space, before.draft)
                val queued = current.send(space, before.draft)
                historyWindows.remove(space)
                mutable.update { if (it.selected == space && it.draft == before.draft) {
                    draftVersions.getOrPut(space) { AtomicLong() }.incrementAndGet()
                    dirtyDrafts.remove(space, before.draft)
                    it.copy(draft = "", scrollToMessage = queued)
                } else if (it.selected == space) it.copy(scrollToMessage = queued) else it }
            }
            publish(); wake.trySend(Unit)
        }
    }
    fun readingPosition(space: String, followLatest: Boolean) {
        val before = mutable.value
        if (before.selected != space || before.loadingChannel) return
        if (followLatest) {
            val changed = historyWindows.remove(space) != null
            if (changed && before.hasLater) scope.launch { publish() }
        } else historyWindows.putIfAbsent(space, before.windowFrom)
    }
    fun consumeScrollTarget(message: String) {
        mutable.update { if (it.scrollToMessage == message) it.copy(scrollToMessage = null) else it }
    }
    fun olderMessages() {
        val space = mutable.value.selected ?: return
        localAction(Problem.CONNECTION) { network.withLock {
            val current = requireNotNull(client)
            val before = current.historyWindow(space, historyWindows[space])
            val start = (before.from - 50).coerceAtLeast(0)
            while (current.historyWindow(space).loadedFrom > start) current.older(space)
            historyWindows[space] = start
            publish()
            mutable.update { if (it.selected == space) it.copy(scrollToMessage = it.messages.firstOrNull()?.id) else it }
        } }
    }
    fun latestMessages() {
        val space = mutable.value.selected ?: return
        historyWindows.remove(space)
        scope.launch {
            publish()
            mutable.update { if (it.selected == space) it.copy(scrollToMessage = it.messages.lastOrNull()?.id) else it }
        }
    }
    fun restoreRejectedDraft(messageId: String) {
        val before = mutable.value
        val space = before.selected ?: return
        if (before.draft.isNotEmpty() || before.working) return
        val text = before.messages.singleOrNull { it.id == messageId && it.rejected }?.text ?: return
        // Reuse the ordinary immediate, revision-fenced editing path. A queued
        // blank save cannot overwrite this user action or newer typing.
        editDraft(space, text)
    }
    fun create(name: String) = localAction {
        val space = localAccess.withLock { requireNotNull(client).create(name) }
        select(space); publish(); wake.trySend(Unit)
    }
    fun invite(days: Int = 7) {
        val space = mutable.value.selected ?: return
        localAction { localAccess.withLock { requireNotNull(client).invite(space, days * 86_400_000L) }; publish(); wake.trySend(Unit) }
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
        if (closed.get() || navigationSaving.get() || mutable.value.working || mutable.value.loading) return
        mutable.update { it.copy(working = true, problem = null) }
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(problem = problem) } }
            finally { mutable.update { it.copy(working = false) } }
        }
    }
    /** Match the existing application's guard: save and commit under one
     * editing gate, before a wallet/node switch can retire this account. */
    suspend fun guardNavigation(commit: suspend () -> Boolean): Boolean {
        if (closed.get() || !navigationSaving.compareAndSet(false, true)) return false
        mutable.update { it.copy(savingNavigation = true) }
        try {
            val saved = withContext(Dispatchers.IO) {
                localAccess.withLock {
                    try {
                        val current = client
                        if (current == null) dirtyDrafts.isEmpty()
                        else {
                            for ((space, text) in dirtyDrafts.entries.toList()) {
                                current.saveDraft(space, text)
                                dirtyDrafts.remove(space, text)
                            }
                            true
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                }
            }
            if (!saved) { mutable.update { it.copy(problem = Problem.SAVE) }; return false }
            currentCoroutineContext().ensureActive()
            return commit()
        } finally {
            navigationSaving.set(false)
            mutable.update { it.copy(savingNavigation = false) }
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
