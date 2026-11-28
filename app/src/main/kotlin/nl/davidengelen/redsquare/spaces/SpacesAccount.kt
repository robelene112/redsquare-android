package nl.davidengelen.redsquare.spaces

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.IOException
import java.time.Clock

private const val READING_POSITION_SETTLE_MILLIS = 150L

/** Account-owned work survives navigation. Local writes never wait for a held
 * read callback; only network work is serialized. Every snapshot keeps its scope. */
internal class SpacesAccount(context: Context, private val wallet: SaitoWallet, val backend: SaitoBackend,
    private val endpoint: String, parent: CoroutineScope, prioritiseInitialDestination: Boolean = false) {
    private val initialDestinationPending = AtomicBoolean(prioritiseInitialDestination)
    enum class Availability { CHECKING, ONLINE, OFFLINE, UNSUPPORTED, CHANGED_IDENTITY, STORAGE_UNAVAILABLE }
    enum class Problem { CONNECTION, SAVE, INVITATION, PROTECTED_CHAT_UNSUPPORTED, DEVICE_RECOVERY_UNSUPPORTED,
        DEVICE_RECOVERY_NO_APPROVER, DEVICE_ALREADY_ACTIVE, RECOVERY_OTHER_MODULE, ARTICLE_MISSING, DESTINATION_MISSING }
    data class Target(val space: String, val channel: String, val thread: String? = null)
    private data class ArticleDraftTarget(val channel: Target, val article: String?)
    data class MessageScroll(val id: String, val showBody: Boolean = false)
    data class ArticleFocus(val id: String, val fromCache: Boolean)

    data class State(val loading: Boolean = true, val availability: Availability = Availability.CHECKING,
        val communities: List<SpaceClient.Community> = emptyList(), val communityListLoaded: Boolean = false, val selected: String? = null,
        val selectedChannel: String? = null, val threadParent: SpaceClient.Message? = null, val current: SpaceClient.Community? = null, val publicRulesToken: String? = null,
        val anchor: SpaceClient.ReadingAnchor? = null, val created: String? = null,
        val listingIntent: Boolean? = null, val previewIssue: SpaceClient.ReadProblem? = null, val publicLink: String? = null,
        val archiveChange: SpaceClient.ArchiveChange? = null, val roleChange: SpaceClient.RoleChange? = null,
        val channelEdit: SpaceClient.ChannelEdit? = null, val channelCreation: SpaceClient.ChannelCreation? = null,
        val channelAccessChange: SpaceClient.ChannelAccessChange? = null,
        val dissolutionChange: SpaceClient.DissolutionChange? = null, val visibilityChange: SpaceClient.VisibilityChange? = null, val categoryChange: SpaceClient.CategoryChange? = null, val categories: List<SpaceClient.Category> = emptyList(),
        val ownershipOffer: SpaceOwnershipOffer? = null, val ownershipChange: SpaceClient.OwnershipChange? = null,
        val membershipChange: SpaceClient.MembershipChange? = null, val bannedMembers: Set<String> = emptySet(),
        val joinedInvitation: String? = null, val openingPublicReference: String? = null, val openedPublicReference: String? = null,
        val openedRecovery: SpaceCrypto.Scope? = null,
        val contentLoaded: Boolean = false, val requiredNode: String? = null, val articleFocus: ArticleFocus? = null,
        val fileLeaseId: String? = null,
        val profiles: Map<String, SpaceProfile> = emptyMap(), val channels: List<SpaceClient.Channel> = emptyList(),
        val members: List<SpaceClient.Member> = emptyList(), val accessProblems: Map<String, SpaceClient.ReadProblem> = emptyMap(),
        val discoverySupported: Boolean? = null, val unreadSupported: Boolean = false,
        val dissolutionSupported: Boolean = false, val visibilitySupported: Boolean = false, val categoriesSupported: Boolean = false, val archiveSupported: Boolean = false, val rolesSupported: Boolean = false, val membershipSupported: Boolean = false, val ownershipSupported: Boolean = false, val publicChannelEditSupported: Boolean = false,
        val privateChannelAccessSupported: Boolean = false, val privateChannelEditSupported: Boolean = false,
        val directoryItems: List<SpaceClient.PublicCommunity> = emptyList(), val directoryQuery: String = "",
        val directoryLoading: Boolean = false, val directoryCached: Boolean = false, val directoryFailed: Boolean = false,
        val directoryLoaded: Boolean = false,
        val directoryCursor: Long = 0, val directoryHasMore: Boolean = false,
        val notificationPolicy: SpaceNotificationPolicy? = null, val pinStatus: SpaceClient.PinStatus? = null, val messages: List<SpaceClient.Message> = emptyList(), val draft: String = "",
        val invitation: SpaceClient.Invitation? = null, val preview: SpaceClient.InvitationPreview? = null,
        val problem: Problem? = null, val working: Boolean = false, val refreshing: Boolean = false,
        val savedDestination: Boolean = false, val savedHistoryCheckedAt: Long? = null,
        val projectingDestination: Boolean = false, val destinationReadDenied: Boolean = false,
        val exactDestination: Boolean = false,
        val accessRevision: Long = 0,
        val ready: Boolean = false, val savingNavigation: Boolean = false, val loadingChannel: Boolean = false, val hasEarlier: Boolean = false, val hasLater: Boolean = false, val scrollToMessage: MessageScroll? = null, val windowFrom: Long = 0, val followingLatest: Boolean = true)
    private val appContext = context.applicationContext
    private val fixtureTrace = (nl.davidengelen.redsquare.BuildConfig.DEBUG ||
        nl.davidengelen.redsquare.BuildConfig.SPACES_SCALE_FIXTURE) && endpoint.startsWith("ws://127.0.0.1:") &&
        android.os.Build.HARDWARE in listOf("ranchu", "goldfish")
    private fun trace(message: String) { if (fixtureTrace) android.util.Log.d("SpacesAccount138", message) }
    private inline fun <T> traceLocalWork(operation: String, action: () -> T): T {
        if (!fixtureTrace) return action()
        val started = android.os.SystemClock.elapsedRealtime()
        trace("local-work operation=$operation startedAtMs=$started")
        return try { action() } finally {
            trace("local-work operation=$operation endedAtMs=${android.os.SystemClock.elapsedRealtime()} heldMs=${android.os.SystemClock.elapsedRealtime() - started}")
        }
    }
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]) + Dispatchers.IO)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var attentionAfterSpace: String? = null
    private var backgroundAfterSpace: String? = null
    private val channelDrawerVisible = AtomicBoolean()
    private val userScrollingConversation = AtomicReference<Target?>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val network = Mutex()
    private val localAccess = Mutex()
    private val fileGates = ConcurrentHashMap<Target, Mutex>()
    private val sweptFileChannels = ConcurrentHashMap.newKeySet<Target>()
    private val managementChanges = Any()
    private val urgentOutbox = ConcurrentHashMap.newKeySet<String>()
    private val closed = AtomicBoolean()
    private val selection = AtomicLong()
    private val navigationVersion = AtomicLong()
    private val navigationChanges = Any()
    private var referenceRequest: Job? = null
    private val wasVisible = AtomicBoolean()
    private val previews = AtomicLong()
    private val directoryRequests = AtomicLong()
    private fun State.target(): Target? = selected?.let { space -> selectedChannel?.let { Target(space, it, threadParent?.id) } }
    private fun SpaceClient.parent(target: Target): SpaceClient.Message? = target.thread?.let {
        requireNotNull(message(target.space, it, target.channel))
    }
    private fun SpaceClient.saveDraft(target: Target, text: String) {
        val parent = parent(target)
        if (parent == null) saveDraft(target.space, text, target.channel) else saveThreadDraft(target.space, parent, text, target.channel)
    }
    private fun SpaceClient.anchor(target: Target): SpaceClient.ReadingAnchor? {
        val parent = parent(target)
        return if (parent == null) readingAnchor(target.space, target.channel) else threadReadingAnchor(target.space, parent, target.channel)
    }
    private fun SpaceClient.saveAnchor(target: Target, anchor: SpaceClient.ReadingAnchor?) {
        val parent = parent(target)
        if (parent == null) saveReadingAnchor(target.space, target.channel, anchor) else saveThreadReadingAnchor(target.space, parent, anchor, target.channel)
    }
    private fun SpaceClient.window(target: Target): SpaceClient.HistoryWindow {
        val parent = parent(target)
        return if (parent == null) historyWindow(target.space, windowFrom(target), target.channel)
        else threadHistoryWindow(target.space, parent, windowFrom(target), target.channel)
    }
    private val historyWindows = ConcurrentHashMap<Target, Long>()
    private data class DestinationVisit(val origin: Target?, val target: Target, val address: SpaceDestination, val windowFrom: Long?)
    private data class ExactRoute(val link: String, val activity: SpaceActivityGroup?, val fromNotification: Boolean, val origin: Target?)
    @Volatile private var destinationVisit: DestinationVisit? = null

    private fun windowFrom(target: Target): Long? {
        val visit = destinationVisit
        return if (visit?.target == target) visit.windowFrom else historyWindows[target]
    }

    private fun updateWindow(target: Target, start: Long?) = synchronized(navigationChanges) {
        val visit = destinationVisit
        if (visit?.target == target) destinationVisit = visit.copy(windowFrom = start)
        else if (start == null) historyWindows.remove(target) else historyWindows[target] = start
    }

    fun returnFromDestination(): Boolean {
        val visit = destinationVisit ?: return false
        restoreOrigin(visit.origin)
        return true
    }
    fun restoreOrigin(origin: Target?) {
        selectRoute(origin?.space, origin?.channel, restoreThread = origin?.thread)
    }
    fun destinationOrigin(): Target? {
        val visit = destinationVisit
        return if (visit == null) mutable.value.target() else visit.origin
    }
    val ownerKey: String get() = wallet.publicKey
    val avatarImages = SpaceAvatarImages(wallet.publicKey, backend)
    private val draftVersions = ConcurrentHashMap<Target, AtomicLong>()
    private val dirtyDrafts = ConcurrentHashMap<Target, String>()
    private val dirtyArticleDrafts = ConcurrentHashMap<ArticleDraftTarget, SpaceArticleDraft>()
    private class SavedViewport(val anchor: SpaceClient.ReadingAnchor?, val saveAfter: Long? = null)
    private val dirtyAnchors = ConcurrentHashMap<Target, SavedViewport>()
    private val writingAnchors = AtomicBoolean()
    private val dirtyListings = ConcurrentHashMap<String, Boolean>()
    private val listingVersions = ConcurrentHashMap<String, AtomicLong>()
    private val navigationSaving = AtomicBoolean()
    @Volatile private var peer: SaitoPeer? = null
    @Volatile private var client: SpaceClient? = null
    private var local: AndroidSpaceClientStore? = null
    @Volatile private var fileStore: SpaceFileLocal? = null
    private var cachedService: String? = null
    private var cacheFailed = false
    private var ticker: Job? = null
    private val directory = spaceServiceDirectory(appContext, wallet.publicKey, endpoint)
    private class MissingService : IOException()
    private class ChangedIdentity : IOException()

    init {
        if (fixtureTrace) scope.launch {
            state.map { value ->
                val current = value.current
                val channel = value.channels.firstOrNull { it.id == value.selectedChannel }
                "state space=${value.selected?.take(10)} channel=${value.selectedChannel?.take(10)} " +
                    "working=${value.working} ready=${value.ready} loading=${value.loadingChannel} saving=${value.savingNavigation} " +
                    "joined=${current?.joined} canSend=${current?.canSend} pending=${current?.pending} " +
                    "kind=${channel?.profile?.kind} channelReady=${channel?.ready} availability=${value.availability} " +
                    "contentLoaded=${value.contentLoaded} messages=${value.messages.size} refreshing=${value.refreshing} " +
                    "problem=${value.problem} access=${value.selected?.let { value.accessProblems[it] }}"
            }.distinctUntilChanged().collect { trace(it) }
        }
        scope.launch {
            try {
                val saved = directory.load()
                if (saved != null) {
                    require(saved.version == 1.toByte() && SaitoWallet.isPublicKey(saved.text))
                    cachedService = saved.text
                    installClient(saved.text)
                    if (!initialDestinationPending.get()) publish()
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
                try {
                    val (current, unreadSupported) = network.withLock {
                        val service = peerCall { authenticatedPeer().servicePublicKey("spaces-v1") }
                        if (cachedService != null && cachedService != service) throw ChangedIdentity()
                        if (client == null) {
                            directory.save(EncryptedLocalFile.Contents(1, service))
                            cachedService = service
                            installClient(service)
                        }
                        val services = peerCall { authenticatedPeer().services() }
                        mutable.update { it.copy(availability = Availability.ONLINE, ready = true,
                            discoverySupported = "spaces-directory-v1" in services, unreadSupported = "spaces-unread-v1" in services,
                            archiveSupported = "spaces-channel-archive-v1" in services, rolesSupported = "spaces-role-management-v1" in services,
                            membershipSupported = "spaces-membership-management-v1" in services,
                            ownershipSupported = "spaces-ownership-management-v1" in services,
                            categoriesSupported = "spaces-category-management-v1" in services,
                            visibilitySupported = "spaces-visibility-management-v1" in services,
                            dissolutionSupported = "spaces-dissolution-v1" in services,
                            publicChannelEditSupported = "spaces-public-channel-edit-v1" in services,
                            privateChannelAccessSupported = "spaces-private-channel-access-v1" in services,
                            privateChannelEditSupported = "spaces-private-channel-edit-v1" in services) }
                        val current = requireNotNull(client)
                        flushUrgent(current)
                        current to ("spaces-unread-v1" in services)
                    }
                    val ids = current.communityIds()
                    val selectedSpace = network.withLock {
                        mutable.value.takeUnless { it.openingDestination() }?.target()?.let { target ->
                            if (target.space in ids) refreshBackground(current, target.space)
                            try {
                                val selected = current.community(target.space, channel = target.channel)
                                // A new local creation or public join can enter
                                // after this cycle's outbox inventory was taken.
                                // Flush it before asking the node for access. A
                                // private invitation still needs reads for Welcome.
                                if (!selected.joined && selected.pending && !selected.membershipRequested) return@let
                                flushUrgent(current)
                                trace("refresh selected=${target.space.take(10)} channel=${target.channel.take(10)} start")
                                current.refresh(target.space, channel = target.channel, from = if (target.thread == null) windowFrom(target) else null)
                                flushUrgent(current)
                                current.parent(target)?.let { current.refreshThread(target.space, it, target.channel, windowFrom(target)) }
                                if (unreadSupported && selected.joined) current.attention(target.space, target.channel)
                                trace("refresh selected=${target.space.take(10)} channel=${target.channel.take(10)} complete")
                                mutable.update { it.copy(accessProblems = it.accessProblems - target.space,
                                    savedDestination = it.savedDestination && it.target() != target) }
                            } catch (denied: SpaceClient.ReadDenied) {
                                mutable.update { it.copy(accessProblems = it.accessProblems + (denied.spaceId to denied.problem)) }
                            }
                        }
                        flushUrgent(current)
                        mutable.value.selected
                    }
                    if (!mutable.value.openingDestination()) {
                        publish(includeCommunities = canRefreshCommunityList())
                        if (nl.davidengelen.redsquare.BuildConfig.SPACES_SCALE_FIXTURE) {
                            val reply = SpacesScaleMeasurement.expectedReply()
                            val target = mutable.value.target()
                            if (reply != null && target != null) localAccess.withLock {
                                val lookedUp = current.message(target.space, reply, target.channel)
                                if (lookedUp?.position != null && !lookedUp.pending && !lookedUp.rejected && !lookedUp.deleted)
                                    SpacesScaleMeasurement.projectedReply(reply)
                            }
                        }
                    }
                    trace("refresh projected")
                    // Only the on-screen Space is checked on every cycle. Keep
                    // membership and queued work rotating through the rest, and
                    // give an interactive read the network lock between Spaces.
                    refreshOtherSpaces(current, ids, selectedSpace)
                    if (unreadSupported && wasVisible.get()) {
                        if (viewingSpaceLists()) refreshVisibleAttention(current, mutable.value.communities)
                        deliverActivity(current)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
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
        val installed = SpaceClient(wallet, backend.webUrl, service, storage, Clock.systemUTC(), onDeliveryAttempt = { delivery ->
            val started = if (fixtureTrace) android.os.SystemClock.elapsedRealtime() else 0L
            if (delivery == SpaceClient.Delivery.CONTROL) publishManagement() else publish(includeCommunities = canRefreshCommunityList())
            if (fixtureTrace) trace("delivery projectionMs=${android.os.SystemClock.elapsedRealtime() - started}")
        }, onReadScopeChanged = { revision ->
            mutable.update { if (it.accessRevision < revision) it.copy(accessRevision = revision) else it }
        }) { original -> peerCall {
            val started = if (fixtureTrace) android.os.SystemClock.elapsedRealtime() else 0L
            val connection = authenticatedPeer()
            if (connection.servicePublicKey("spaces-v1") != service) throw ChangedIdentity()
            val received = connection.request(original) { check(!closed.get()) }.toString(Charsets.UTF_8)
            if (nl.davidengelen.redsquare.BuildConfig.SPACES_SCALE_FIXTURE) SpacesScaleMeasurement.received(original.id)
            if (fixtureTrace) trace("request=${original.id.take(10)} returnedMs=${android.os.SystemClock.elapsedRealtime() - started}")
            received
        } }
        local = storage
        fileStore = SpaceFileLocal(appContext, wallet.publicKey, "$backend:$endpoint", service)
        client = installed
        mutable.update { it.copy(ready = true) }
        scope.launch {
            try {
                val restored = retainedSpaceArchives(appContext, wallet.publicKey, service)
                if (restored.isNotEmpty()) {
                    localAccess.withLock { installed.restoreHistoryKeys(restored) }
                    publish()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(problem = Problem.SAVE) } }
        }
    }

    private suspend fun <T> peerCall(action: suspend () -> T): T = try { action() }
    catch (timeout: TimeoutCancellationException) {
        // A peer's own deadline must not terminate the long-lived account
        // worker. Cancellation of this account still propagates unchanged.
        currentCoroutineContext().ensureActive()
        throw IOException("Community peer request timed out", timeout)
    }

    private suspend fun authenticatedPeer(): SaitoPeer = peerCall {
        val connection = peer ?: SaitoPeer(wallet, endpoint).also { peer = it }
        if ("spaces-v1" !in connection.services()) throw MissingService()
        connection
    }

    private fun managementProjection(current: SpaceClient, target: Target?): SpaceClient.Management =
        target?.let { current.management(it.space, it.channel) } ?: SpaceClient.Management()
    private fun State.withManagement(changes: SpaceClient.Management): State {
        val pending = listOf(changes.archive?.status, changes.role?.status, changes.edit?.status,
            changes.creation?.status, changes.access?.status, changes.membership?.status, changes.ownership?.status, changes.category?.status, changes.visibility?.status, changes.dissolution?.status).any {
            it == SpaceClient.ChangeStatus.QUEUED || it == SpaceClient.ChangeStatus.AWAITING_CONFIRMATION
        }
        return copy(dissolutionChange = changes.dissolution, visibilityChange = changes.visibility, categoryChange = changes.category, archiveChange = changes.archive, roleChange = changes.role, channelEdit = changes.edit,
            channelCreation = changes.creation, channelAccessChange = changes.access, membershipChange = changes.membership, ownershipChange = changes.ownership, ownershipOffer = changes.ownershipOffer,
            current = if (pending) current?.copy(pending = true, canSend = false, canManage = false) else current)
    }

    /** A durable control attempt must not rebuild the entire readable timeline before transport. */
    private fun publishManagement() {
        val current = client ?: return
        val target = mutable.value.target() ?: return
        synchronized(managementChanges) {
            val changes = managementProjection(current, target)
            mutable.update { if (it.target() == target && !it.loadingChannel) it.withManagement(changes) else it }
        }
    }
    private fun viewingSpaceLists() = wasVisible.get() && (mutable.value.selected == null || channelDrawerVisible.get())

    private fun State.openingDestination() = working || projectingDestination || loadingChannel

    private fun canRefreshCommunityList(): Boolean {
        val state = mutable.value
        return wasVisible.get() && !initialDestinationPending.get() && !state.openingDestination() &&
            (state.selected == null || channelDrawerVisible.get())
    }

    private suspend fun refreshBackground(current: SpaceClient, space: String) {
        try {
            trace("refresh background=${space.take(10)} flush-start")
            current.flush(space)
            trace("refresh background=${space.take(10)} admit-start")
            current.admitPending(space)
            current.flush(space)
            flushUrgent(current)
            trace("refresh background=${space.take(10)} complete")
            mutable.update { it.copy(accessProblems = it.accessProblems - space) }
        } catch (denied: SpaceClient.ReadDenied) {
            mutable.update { it.copy(accessProblems = it.accessProblems + (denied.spaceId to denied.problem)) }
        }
    }

    private suspend fun refreshOtherSpaces(current: SpaceClient, communities: List<String>, selected: String?) {
        fun canContinue(): Boolean {
            val view = mutable.value
            val scrolling = userScrollingConversation.get()
            return !view.openingDestination() && (scrolling == null || scrolling != view.target())
        }
        if (!canContinue()) return
        val ids = communities.filterNot { it == selected }.sorted()
        if (ids.isEmpty()) return
        val previous = backgroundAfterSpace
        val start = ids.indexOfFirst { previous == null || it > previous }.coerceAtLeast(0)
        var refreshed = false
        for (offset in 0 until minOf(ids.size, 4)) {
            if (!canContinue()) break
            val space = ids[(start + offset) % ids.size]
            val refreshedSpace = network.withLock {
                if (!canContinue()) false else {
                    refreshBackground(current, space)
                    true
                }
            }
            if (!refreshedSpace) break
            backgroundAfterSpace = space
            refreshed = true
        }
        if (refreshed && canRefreshCommunityList()) publish()
    }

    private suspend fun refreshVisibleAttention(current: SpaceClient, communities: List<SpaceClient.Community>) {
        val joined = communities.filter { it.joined && !it.dissolved }.map { it.id }.sorted()
        if (joined.isEmpty()) return
        val checkedHeads = mutableSetOf<String>()
        val finished = mutableSetOf<String>()
        fun canContinue() = viewingSpaceLists() && !mutable.value.openingDestination() && urgentOutbox.isEmpty()
        // Return the network lock between Spaces and pages so an interactive
        // preview or queued send can take priority over the rest of the burst.
        var changed = false
        for (turn in 0 until 4) {
            if (!canContinue() || finished.size == joined.size) break
            val candidates = joined.filterNot(finished::contains)
            val after = attentionAfterSpace
            val next = candidates.firstOrNull { after == null || it > after } ?: candidates.first()
            attentionAfterSpace = next
            try {
                if (checkedHeads.add(next)) {
                    val checked = network.withLock {
                        if (!canContinue()) return@withLock false
                        current.refreshUnread(next)
                        true
                    }
                    if (!checked) break
                    changed = true
                }
                val fetched = network.withLock {
                    if (canContinue()) current.refreshNextAttention(next) else null
                } ?: break
                if (!fetched) finished.add(next)
            } catch (_: SpaceClient.ReadDenied) {
                // The foreground still owns access state for a retired channel.
                finished.add(next)
            }
        }
        if (changed && canContinue()) publish()
    }

    private var notificationCursor: String? = null

    private suspend fun deliverActivity(current: SpaceClient) = localAccess.withLock {
        if (!wasVisible.get() || closed.get() || mutable.value.working) return@withLock
        val since = System.currentTimeMillis() - 30 * 60_000L
        val candidates = current.pendingActivityNotifications(notificationCursor, since)
        for (group in candidates.groups) {
            if (!wasVisible.get() || closed.get() || mutable.value.working) return@withLock
            val posted = runCatching { SpaceActivityNotifications.post(appContext, ownerKey, backend, endpoint, group) }.getOrDefault(false)
            if (posted) current.markActivityNotified(group)
        }
        notificationCursor = candidates.next
    }

    private suspend fun flushUrgent(current: SpaceClient) {
        for (space in urgentOutbox.toList()) if (urgentOutbox.remove(space)) {
            try { current.flush(space) }
            catch (failure: Exception) { urgentOutbox.add(space); throw failure }
        }
    }

    private suspend fun publish(includeCommunities: Boolean = true) = localAccess.withLock { traceLocalWork("publish") {
        val current = client ?: return@withLock
        val before = mutable.value
        val target = before.target()
        val visit = destinationVisit?.takeIf { it.target == target }
        val deniedDestination = visit != null && !current.destinationAccessAllowed(visit.address)
        val projection = current.presentation(target?.space, target?.channel, target?.let(::windowFrom), target?.let { current.parent(it) },
            includeCommunities = includeCommunities)
        val communities = projection.communities
        val profiles = projection.profiles
        val community = projection.current
        val channels = projection.channels.filterNot { deniedDestination && it.id == target?.channel }
        val members = if (deniedDestination) emptyList() else projection.members
        val checkedAt = target?.let { current.savedContentCheckedAt(it.space, it.channel) }
        val bannedMembers = projection.bannedMembers
        val window = projection.window
        val messages = if (deniedDestination) emptyList() else projection.messages
        val pins = target?.let { current.pinStatus(it.space, it.channel) }
        val notifications = target?.let { current.notificationPolicy(it.space, it.channel) }
        val invitation = projection.invitation
        val publicRulesToken = target?.let { profiles[it.space] }?.takeIf { it.visibility == SpaceVisibility.PUBLIC }?.publicRulesToken
        val listingIntent = projection.listingIntent
        val publicLink = projection.publicLink
        val fileLease = target?.takeIf { route ->
            channels.any { it.id == route.channel && it.profile.kind == SpaceChannelKind.FILE }
        }?.let { current.fileStorage(it.space)?.id }
        synchronized(managementChanges) {
            val changes = managementProjection(current, target)
            mutable.update { previous ->
                val updated = previous.copy(communities = if (includeCommunities) communities else previous.communities,
                    communityListLoaded = includeCommunities || previous.communityListLoaded,
                    profiles = if (includeCommunities) profiles else previous.profiles + profiles)
                if (updated.target() != target || updated.loadingChannel) updated
                else updated.copy(
                    savedHistoryCheckedAt = checkedAt, destinationReadDenied = deniedDestination,
                    current = community, channels = channels, categories = projection.categories, members = members,
                    bannedMembers = bannedMembers, notificationPolicy = notifications, pinStatus = pins,
                    messages = messages, threadParent = projection.threadParent, invitation = invitation,
                    publicRulesToken = publicRulesToken, fileLeaseId = fileLease,
                    listingIntent = target?.let { dirtyListings[it.space] } ?: listingIntent, publicLink = publicLink,
                    contentLoaded = window?.loaded == true, hasEarlier = window?.hasEarlier == true,
                    hasLater = window?.hasLater == true, windowFrom = window?.from ?: 0,
                    followingLatest = target == null || windowFrom(target) == null).withManagement(changes)
            }
        }
    } }

    fun channelDrawerVisible(visible: Boolean) {
        if (channelDrawerVisible.getAndSet(visible) != visible && visible) wake.trySend(Unit)
    }

    fun conversationScrolling(target: Target, scrolling: Boolean) {
        // The selected channel and queued sends keep their normal refresh.
        // Let the reader's gesture settle before starting another Space's pass.
        userScrollingConversation.updateAndGet { previous ->
            when {
                scrolling && wasVisible.get() && mutable.value.target() == target -> target
                previous == target -> null
                else -> previous
            }
        }
    }

    fun detachScreen() {
        synchronized(navigationChanges) {
            initialDestinationPending.set(false)
            navigationVersion.incrementAndGet()
            referenceRequest?.cancel()
            mutable.update { if (it.projectingDestination) it.copy(projectingDestination = false) else it }
        }
        visible(false)
    }

    fun visible(active: Boolean) {
        if (wasVisible.getAndSet(active) == active) return
        if (!active) userScrollingConversation.set(null)
        ticker?.cancel(); ticker = null
        if (active) ticker = scope.launch {
            while (isActive) { wake.trySend(Unit); delay(5_000) }
        }
        if (nl.davidengelen.redsquare.BuildConfig.SPACES_SCALE_FIXTURE) {
            val job = scope.coroutineContext[Job]
            SpacesScaleMeasurement.visible(wasVisible.get(), ticker?.isActive == true, System.identityHashCode(this),
                job?.children?.count { it.isActive } ?: 0, mutable.subscriptionCount.value)
            if (!active) scope.launch {
                delay(750)
                if (!wasVisible.get() && ticker?.isActive != true) {
                    val sample = currentCoroutineContext()[Job]
                    SpacesScaleMeasurement.idle(System.identityHashCode(this@SpacesAccount),
                        job?.children?.count { it !== sample && it.isActive } ?: 0, mutable.subscriptionCount.value)
                }
            }
        }
    }
    fun refresh() { wake.trySend(Unit) }
    fun select(space: String?, channel: String? = null) {
        initialDestinationPending.set(false)
        selectRoute(space, channel)
    }
    fun openThread(message: SpaceClient.Message) {
        val target = mutable.value.target() ?: return
        if (target.thread != null) return
        selectRoute(target.space, target.channel, thread = message)
    }
    fun closeThread() { mutable.value.target()?.let { select(it.space, it.channel) } }
    fun openPinned(target: Target, reference: SpaceClient.PinnedMessage): Boolean {
        if (mutable.value.target() != target || reference.message.deleted || reference.message.missingKeys ||
            (reference.threadId != null && reference.parent == null)) return false
        return selectRoute(target.space, target.channel, thread = reference.parent, jump = reference.message) != null
    }
    private fun selectRoute(space: String?, channel: String? = null, expected: Long? = null, thread: SpaceClient.Message? = null,
        jump: SpaceClient.Message? = null, article: ArticleFocus? = null, requestChannelKeys: Boolean = true, savedDestination: Boolean = false, exact: ExactRoute? = null,
        restoreThread: String? = null): Long? {
        if (closed.get()) return null
        return synchronized(navigationChanges) {
            if (expected != null && navigationVersion.get() != expected) return@synchronized null
            val route = navigationVersion.incrementAndGet()
            val token = selection.incrementAndGet()
            val origin = if (exact == null) null else exact.origin ?: destinationOrigin()
            if (exact == null) destinationVisit = null
            fun currentRoute() = selection.get() == token && (exact == null || navigationVersion.get() == route)
            fun reset(state: State) = state.copy(selected = space, selectedChannel = channel, threadParent = null, current = null,
                notificationPolicy = null, pinStatus = null, messages = emptyList(), channels = emptyList(), members = emptyList(), draft = "", invitation = null, anchor = null,
                problem = null, listingIntent = null, publicLink = null, archiveChange = null, roleChange = null, channelEdit = null, channelCreation = null, categoryChange = null, visibilityChange = null, dissolutionChange = null, categories = emptyList(), channelAccessChange = null, membershipChange = null, ownershipOffer = null, ownershipChange = null, bannedMembers = emptySet(),
                contentLoaded = false, loadingChannel = space != null, scrollToMessage = null, articleFocus = article,
                savedDestination = savedDestination, savedHistoryCheckedAt = null,
                projectingDestination = false, destinationReadDenied = false, exactDestination = exact != null)
            mutable.update { if (exact == null) reset(it) else it.copy(projectingDestination = true) }
            scope.launch {
                try {
                    val current = client ?: return@launch
                    localAccess.withLock { traceLocalWork("select-route") {
                        if (!currentRoute()) return@withLock
                        persistLocal(current)
                        if (space == null) {
                            mutable.update { if (selection.get() == token) it.copy(loadingChannel = false) else it }
                            return@withLock
                        }
                        val exactAddress = exact?.let { SpaceDestination.parse(it.link) }
                        if (exactAddress != null && current.cachedDestination(exactAddress) == null) {
                            mutable.update { if (currentRoute()) it.copy(
                                previewIssue = SpaceClient.ReadProblem.UNAVAILABLE, projectingDestination = false) else it }
                            return@withLock
                        }
                        val restoredParent = restoreThread?.let { requireNotNull(current.message(space, it, channel)) }
                        if (exact == null) {
                            val selectedParent = thread ?: restoredParent
                            if (selectedParent != null) current.selectThread(space, selectedParent, channel, requestChannelKeys)
                            else if (channel != null) current.selectChannel(space, channel, requestChannelKeys)
                        }
                        val selectedChannel = channel ?: current.selectedChannelId(space)
                        val parent = if (exact != null) thread else current.selectedThread(space, selectedChannel)
                        val target = Target(space, selectedChannel, parent?.id)
                        val version = draftVersions.getOrPut(target) { AtomicLong() }.get()
                        val anchor = if (jump == null && exact == null) current.anchor(target) else null
                        if (exactAddress == null) synchronized(navigationChanges) {
                            if (!currentRoute()) return@withLock
                            jump?.position?.let { historyWindows[target] = it }
                            if (anchor != null) historyWindows.putIfAbsent(target, anchor.windowFrom)
                        }
                        val start = if (exactAddress != null) jump?.position else windowFrom(target)
                        val projection = current.presentation(space, target.channel, start, parent, includeCommunities = false)
                        val community = requireNotNull(projection.current)
                        val window = requireNotNull(projection.window)
                        val draft = dirtyDrafts[target] ?: projection.draft
                        val messages = projection.messages
                        val channels = projection.channels
                        val members = projection.members
                        val profile = projection.profiles.getValue(space)
                        val rulesToken = profile.takeIf { it.visibility == SpaceVisibility.PUBLIC }?.publicRulesToken
                        val publicLink = projection.publicLink
                        val invitation = projection.invitation
                        val listingIntent = projection.listingIntent
                        val archiveChange = projection.management.archive
                        val roleChange = projection.management.role
                        val membershipChange = projection.management.membership
                        val ownershipOffer = projection.management.ownershipOffer
                        val ownershipChange = projection.management.ownership
                        val bannedMembers = projection.bannedMembers
                        val channelEdit = projection.management.edit
                        val channelCreation = projection.management.creation
                        val channelAccessChange = projection.management.access
                        val checkedAt = current.savedContentCheckedAt(space, target.channel)
                        val notificationPolicy = current.notificationPolicy(space, target.channel)
                        val pinStatus = current.pinStatus(space, target.channel)
                        synchronized(navigationChanges) {
                            if (!currentRoute()) return@withLock
                            if (exactAddress != null && !current.destinationAccessAllowed(exactAddress)) {
                                mutable.update { it.copy(previewIssue = SpaceClient.ReadProblem.UNAVAILABLE, projectingDestination = false) }
                                return@withLock
                            }
                            if (exact != null && exactAddress != null) {
                                if (exact.activity != null) current.markActivitySeen(exact.activity)
                                else if (exact.fromNotification) current.markDestinationActivitySeen(exactAddress)
                                destinationVisit = DestinationVisit(origin, target, exactAddress, start)
                            }
                            mutable.update { view ->
                                val next = if (exactAddress == null) view else reset(view)
                                if (next.selected != space) view else next.copy(
                                    selectedChannel = target.channel, threadParent = projection.threadParent, current = community,
                                    channels = channels,
                                    savedHistoryCheckedAt = checkedAt, categories = projection.categories, categoryChange = projection.management.category,
                                    visibilityChange = projection.management.visibility, dissolutionChange = projection.management.dissolution,
                                    members = members, anchor = anchor, archiveChange = archiveChange, roleChange = roleChange,
                                    membershipChange = membershipChange, ownershipOffer = ownershipOffer, ownershipChange = ownershipChange,
                                    bannedMembers = bannedMembers, channelEdit = channelEdit, channelCreation = channelCreation,
                                    channelAccessChange = channelAccessChange, profiles = next.profiles + (space to profile),
                                    publicRulesToken = rulesToken, publicLink = publicLink, invitation = invitation,
                                    listingIntent = dirtyListings[space] ?: listingIntent,
                                    draft = if (draftVersions[target]?.get() == version) draft else view.draft,
                                    notificationPolicy = notificationPolicy, pinStatus = pinStatus,
                                    scrollToMessage = jump?.let { MessageScroll(it.id, showBody = true) },
                                    openedPublicReference = exact?.link ?: next.openedPublicReference,
                                    messages = messages, loadingChannel = false, contentLoaded = window.loaded, hasEarlier = window.hasEarlier,
                                    hasLater = window.hasLater, windowFrom = window.from, followingLatest = start == null)
                            }
                        }
                    } }
                    wake.trySend(Unit)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutable.update { if (currentRoute())
                    it.copy(problem = Problem.SAVE, loadingChannel = false, projectingDestination = false) else it } }
            }
            route
        }
    }
    fun editDraft(text: String) { mutable.value.target()?.let { editDraft(it.space, it.channel, text, it.thread) } }
    fun editDraft(space: String, text: String) {
        mutable.value.target()?.takeIf { it.space == space }?.let { editDraft(space, it.channel, text, it.thread) }
    }
    fun editDraft(space: String, channel: String, text: String, thread: String? = null) {
        if (closed.get() || navigationSaving.get()) return
        val target = Target(space, channel, thread)
        dirtyDrafts[target] = text
        val version = draftVersions.getOrPut(target) { AtomicLong() }.incrementAndGet()
        mutable.update { if (it.target() == target) it.copy(draft = text) else it }
        scope.launch {
            try { localAccess.withLock {
                if (draftVersions[target]?.get() == version) {
                    requireNotNull(client).saveDraft(target, text)
                    dirtyDrafts.remove(target, text)
                }
            } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { if (it.target() == target) it.copy(problem = Problem.SAVE) else it } }
        }
    }
    fun send() = queueSend()

    internal data class FileLibrary(
        val supported: Boolean,
        val lease: SpaceFileLease?,
        val usage: SpaceStorageUsage?,
        val upload: SpaceFileLocal.Upload?,
        val setup: SpaceFileLocal.Setup?,
        val awaitingRemoval: String?,
        val offline: Map<String, SpaceFileDescriptor>,
        val expired: Boolean,
        val checkedProvider: Boolean,
    )

    fun mayManageFileStorage(target: Target): Boolean = state.value.target() == target &&
        state.value.members.any { it.publicKey == wallet.publicKey && it.role == SpaceRole.OWNER }

    fun mayRemoveFile(target: Target, file: SpaceClient.Message): Boolean =
        state.value.target() == target && state.value.current?.canSend == true && file.position != null &&
            !file.pending && !file.deleted && !file.rejected &&
            (file.author == wallet.publicKey || state.value.current?.canManage == true)

    suspend fun fileLibrary(target: Target, records: List<SpaceClient.Message>, observeProvider: Boolean): FileLibrary =
        fileAction(target, exclusive = false) { current, disk, checkRoute ->
            val lease = current.fileStorage(target.space)
            val expired = lease != null && System.currentTimeMillis() >= lease.offer.expiresAt
            val online = mutable.value.availability == Availability.ONLINE && mutable.value.current?.joined == true
            var probe = "authority"
            val usage = if (lease == null || expired || !online || !observeProvider) null else try {
                val permission = current.fileStorageUsagePermission(target.space, target.channel)
                checkRoute()
                probe = "provider"
                provider(lease.offer.origin).usage(lease, permission, System::currentTimeMillis)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                trace("file-storage-probe phase=$probe result=${failure.javaClass.simpleName}")
                null
            }
            checkRoute()
            val pending = disk.pending(target)
            if (!observeProvider && sweptFileChannels.add(target)) disk.removeAbandonedStaging(target, pending, System.currentTimeMillis())
            FileLibrary(current.fileStorageSupported(), lease, usage, pending, disk.setup(target.space),
                disk.removal(target), records.mapNotNull { item ->
                    val file = disk.offlineDescriptor(target, item.id) ?: return@mapNotNull null
                    if (disk.offlineCopy(target, item.id, file) == null) null else item.id to file
                }.toMap(), expired, checkedProvider = online && observeProvider)
        }

    suspend fun reviewFileStorage(target: Target, origin: String): SpaceStorageOffer =
        fileAction(target) { current, _, _ ->
            require(mayManageFileStorage(target) && current.fileStorageSupported())
            verifySpaceStorageOrigin(origin)
            require(!java.net.URI.create(origin).host.equals(java.net.URI.create(backend.webUrl).host, ignoreCase = true))
            val offer = provider(origin).offer(target.space, wallet.publicKey)
            offer.verifyScope(target.space, requireNotNull(cachedService), wallet.publicKey, System.currentTimeMillis())
            offer
        }

    suspend fun confirmFileStorage(target: Target, offer: SpaceStorageOffer) =
        fileAction(target, setup = true) { current, disk, checkRoute ->
            require(mayManageFileStorage(target) && disk.setup(target.space) == null)
            checkRoute()
            disk.saveSetup(target.space, SpaceFileLocal.Setup(offer.envelope, ""))
            finishFileStorage(target, current, disk, checkRoute)
        }

    suspend fun resumeFileStorage(target: Target) = fileAction(target, setup = true) { current, disk, checkRoute ->
        require(mayManageFileStorage(target))
        finishFileStorage(target, current, disk, checkRoute)
    }

    suspend fun discardFileStorageReview(target: Target) = fileAction(target, setup = true) { _, disk, _ ->
        require(mayManageFileStorage(target))
        disk.saveSetup(target.space, null)
    }

    private suspend fun finishFileStorage(target: Target, current: SpaceClient, disk: SpaceFileLocal, checkRoute: () -> Unit) {
        val pending = requireNotNull(disk.setup(target.space))
        val terms = SpaceStorageOffer.read(pending.terms)
        if (current.fileStorage(target.space)?.offer?.id == terms.id) {
            disk.saveSetup(target.space, null)
            return
        }
        val consent = network.withLock {
            checkRoute()
            if (pending.operation.isNotEmpty()) current.flush(target.space)
            val operation = pending.operation.ifEmpty {
                current.approveFileStorage(target.space, terms).also {
                    disk.saveSetup(target.space, pending.copy(operation = it))
                    urgentOutbox.add(target.space)
                }
            }
            current.flush(target.space)
            requireNotNull(current.fileStorageApproval(target.space, operation)) { "Storage consent is awaiting the node" }
        }
        checkRoute()
        val committed = provider(terms.origin).commit(consent)
        network.withLock {
            checkRoute()
            current.activateFileStorage(target.space, consent, committed)
            current.flush(target.space)
        }
        require(current.fileStorage(target.space)?.offer?.id == terms.id)
        disk.saveSetup(target.space, null)
    }

    suspend fun prepareFileUpload(target: Target, uri: Uri) = fileAction(target) { current, disk, checkRoute ->
        checkRoute()
        require(current.fileStorageSupported() && mutable.value.current?.canSend == true)
        disk.prepare(target, uri)
    }

    suspend fun sendPreparedFile(target: Target) = fileAction(target) { current, disk, checkRoute ->
        val pending = requireNotNull(disk.pending(target))
        val lease = requireNotNull(current.fileStorage(target.space))
        val sealed = pending.file.sealed
        val prior = current.filePublication(target.space, target.channel, sealed.id) ?: pending.operation
        val retryRejected = prior != null && current.fileOperation(target.space, prior) == SpaceClient.ChangeStatus.REJECTED
        val existing = prior.takeUnless { retryRejected }
        val original = existing ?: run {
            val permission = network.withLock { current.fileUploadPermission(target.space, target.channel, sealed) }
            checkRoute()
            val proof = disk.upload(target, pending).inputStream().use { provider(lease.offer.origin).put(permission, it) }
            checkRoute()
            val stage = SpaceFileStage.read(proof, requireNotNull(cachedService), lease.offer.provider)
            localAccess.withLock {
                checkRoute()
                current.publishFile(target.space, target.channel, pending.file, stage, retryRejected)
            }
        }
        if (pending.operation != original) disk.notePublication(target, pending, original)
        urgentOutbox.add(target.space)
        wake.trySend(Unit)
        network.withLock {
            checkRoute()
            current.flush(target.space)
        }
        val accepted = current.message(target.space, original, target.channel)
        require(accepted?.position != null && !accepted.rejected && !accepted.pending) { "File sharing was not accepted" }
        disk.clearPending(target, pending)
    }

    suspend fun discardFileUpload(target: Target) = fileAction(target) { current, disk, checkRoute ->
        val pending = requireNotNull(disk.pending(target))
        val existing = pending.operation ?: current.filePublication(target.space, target.channel, pending.file.sealed.id)
        require(existing == null || current.fileOperation(target.space, existing) == SpaceClient.ChangeStatus.REJECTED)
        val lease = requireNotNull(current.fileStorage(target.space))
        val permission = network.withLock { current.fileAbandonPermission(target.space, target.channel, pending.file.sealed.id) }
        if (permission != null) provider(lease.offer.origin).remove(permission)
        checkRoute()
        disk.clearPending(target, pending)
    }

    data class OpenedSpaceFile(val uri: Uri, val mime: String)

    /** Keep a file action attached to the account while its route can change. */
    fun launchFileOperation(target: Target, action: suspend () -> Unit) {
        val route = navigationVersion.get()
        scope.launch(Dispatchers.Main.immediate) {
            if (!closed.get() && navigationVersion.get() == route && mutable.value.target() == target) action()
        }
    }

    suspend fun observeVoiceRoom(target: Target): SpaceVoiceRoom = withContext(Dispatchers.IO) {
        val current = requireNotNull(client)
        requireRoomTarget(target)
        if (!current.voiceRoomsSupported()) throw UnsupportedOperationException("Node has no signed room capability")
        current.voiceRoom(target.space, target.channel).also { requireRoomTarget(target) }
    }

    suspend fun roomAuthority(target: Target): SpaceRoomAuthority = withContext(Dispatchers.IO) {
        requireRoomTarget(target)
        SpaceRoomAuthority(appContext, wallet, backend, endpoint,
            SpaceVoiceSignalKey.Address(requireNotNull(cachedService), target.space, target.channel))
    }

    suspend fun moderateVoiceRoom(target: Target, session: String, remove: Boolean): SpaceVoiceRoom = withContext(Dispatchers.IO) {
        val current = requireNotNull(client)
        requireRoomTarget(target)
        current.moderateVoiceRoom(target.space, target.channel, session, remove).also { requireRoomTarget(target) }
    }

    suspend fun pauseSpeaking(target: Target, member: String, durationMillis: Long): SpaceVoiceRoom = withContext(Dispatchers.IO) {
        requireRoomTarget(target)
        val current = requireNotNull(client)
        applyWritingRestriction(current, target.space, member, target.channel, Math.addExact(System.currentTimeMillis(), durationMillis))
        current.voiceRoom(target.space, target.channel).also { requireRoomTarget(target) }
    }

    fun writeRestrictionsSupported(): Boolean = client?.writeRestrictionsSupported() == true

    suspend fun restrictionOptions(space: String, member: String): SpaceRestrictionOptions = withContext(Dispatchers.IO) {
        requireModerationStaff(space)
        localAccess.withLock { requireNotNull(client).restrictionOptions(space, member) }
    }

    suspend fun setMemberWritingRestriction(space: String, member: String, channel: String?, until: Long?): SpaceRestrictionOptions =
        withContext(Dispatchers.IO) {
            requireModerationStaff(space)
            val current = requireNotNull(client)
            applyWritingRestriction(current, space, member, channel, until)
            publish()
            requireModerationStaff(space)
            localAccess.withLock { current.restrictionOptions(space, member) }
        }

    private suspend fun applyWritingRestriction(current: SpaceClient, space: String, member: String, channel: String?, until: Long?) {
        network.withLock {
            current.flush(space)
            fun activeExpiry() = current.scopedWriteRestriction(space, member, channel)?.takeIf { it > System.currentTimeMillis() }
            if (activeExpiry() != until) {
                current.setWriteRestriction(space, member, channel, until)
                current.flush(space)
            }
            require(activeExpiry() == until)
        }
    }

    private fun requireRoomTarget(target: Target) {
        val current = mutable.value
        require(!closed.get() && current.target() == target && target.thread == null &&
            current.channels.any { it.id == target.channel && it.profile.kind == SpaceChannelKind.VOICE })
    }

    suspend fun openSpaceFile(target: Target, record: String, keep: Boolean): OpenedSpaceFile? =
        fileAction(target) { current, disk, checkRoute ->
            fun descriptor(): SpaceFileDescriptor {
                val row = requireNotNull(current.message(target.space, record, target.channel))
                require(row.isFile && !row.rejected && !row.pending)
                if (row.deleted) return requireNotNull(disk.offlineDescriptor(target, record))
                return SpaceFileDescriptor.read(requireNotNull(row.text))
            }
            val file = descriptor()
            val offline = disk.offlineCopy(target, record, file)
            if (offline != null) {
                disk.verify(offline)
                checkRoute()
                descriptor()
                if (keep) null else OpenedSpaceFile(SpaceFileContentProvider.share(appContext, offline, false), file.mime)
            } else {
                val grant = network.withLock { current.fileDownloadPermission(target.space, target.channel, record) }
                require(grant.grant.objectId == file.sealed.id && grant.grant.sha256 == file.sealed.sha256 &&
                    grant.grant.bytes == file.sealed.bytes)
                checkRoute()
                val copy = provider(grant.origin).download(grant.grant) { disk.stageDownload(target, file, it) }
                try {
                    network.withLock { current.confirmFileDownload(target.space, target.channel, record, grant) }
                    checkRoute()
                    descriptor()
                    if (keep) {
                        disk.saveOffline(target, record, copy)
                        null
                    } else OpenedSpaceFile(SpaceFileContentProvider.share(appContext, copy, true), file.mime)
                } catch (failure: Exception) {
                    copy.path.delete()
                    throw failure
                }
            }
        }

    suspend fun removeFileOffline(target: Target, record: String) = fileAction(target) { _, disk, _ ->
        disk.removeOffline(target, record)
    }

    suspend fun removeFileFromSpace(target: Target, record: SpaceClient.Message) =
        fileAction(target) { current, disk, checkRoute ->
            require(mayRemoveFile(target, record) && disk.removal(target) == null)
            disk.saveRemoval(target, record.id)
            finishFileRemoval(target, current, disk, checkRoute)
        }

    suspend fun resumeFileRemoval(target: Target) = fileAction(target) { current, disk, checkRoute ->
        finishFileRemoval(target, current, disk, checkRoute)
    }

    private suspend fun finishFileRemoval(target: Target, current: SpaceClient, disk: SpaceFileLocal, checkRoute: () -> Unit) {
        val id = requireNotNull(disk.removal(target))
        val permission = network.withLock {
            checkRoute()
            current.flush(target.space)
            val file = requireNotNull(current.message(target.space, id, target.channel))
            if (!file.deleted) {
                require(mayRemoveFile(target, file))
                current.deleteMessage(target.space, file, target.channel)
                current.flush(target.space)
            }
            current.fileRemovalPermission(target.space, target.channel, id)
        }
        val lease = requireNotNull(current.fileStorage(target.space))
        provider(lease.offer.origin).remove(permission)
        disk.saveRemoval(target, null)
    }

    private fun provider(origin: String) = SpaceStorageProviderClient(origin)

    private suspend fun <T> fileAction(target: Target, exclusive: Boolean = true, setup: Boolean = false,
        action: suspend (SpaceClient, SpaceFileLocal, () -> Unit) -> T): T =
        withContext(Dispatchers.IO) {
            val version = navigationVersion.get()
            fun checkRoute() {
                if (closed.get() || navigationVersion.get() != version || mutable.value.target() != target ||
                    mutable.value.channels.none { it.id == target.channel && it.profile.kind == SpaceChannelKind.FILE })
                    throw CancellationException("File channel changed")
            }
            suspend fun run(): T {
                checkRoute()
                return action(requireNotNull(client), requireNotNull(fileStore), ::checkRoute).also { checkRoute() }
            }
            when {
                setup -> requireNotNull(fileStore).setupGate(target.space).withLock { run() }
                exclusive -> fileGates.computeIfAbsent(target) { Mutex() }.withLock { run() }
                else -> run()
            }
        }

    suspend fun readArticle(target: Target, article: String): SpaceClient.Message? = withContext(Dispatchers.IO) {
        localAccess.withLock {
            if (closed.get() || mutable.value.target() != target) throw CancellationException("Article channel changed")
            if (mutable.value.channels.none { it.id == target.channel && it.profile.kind == SpaceChannelKind.ARTICLE }) return@withLock null
            requireNotNull(client).message(target.space, article, target.channel)?.takeIf { it.isArticle }
        }
    }

    suspend fun articleDraft(target: Target, article: String?): SpaceArticleDraft? = withContext(Dispatchers.IO) {
        val slot = ArticleDraftTarget(target, article)
        localAccess.withLock {
            if (closed.get() || mutable.value.target() != target) throw CancellationException("Article channel changed")
            dirtyArticleDrafts[slot] ?: requireNotNull(client).articleDraft(target.space, target.channel, article)
        }
    }

    fun editArticleDraft(target: Target, article: String?, draft: SpaceArticleDraft) {
        if (target.thread != null || mutable.value.target() != target || navigationSaving.get()) return
        val slot = ArticleDraftTarget(target, article)
        dirtyArticleDrafts[slot] = draft
        scope.launch {
            delay(300)
            try {
                localAccess.withLock {
                    if (dirtyArticleDrafts[slot] == draft) saveArticleDraft(requireNotNull(client), slot, draft)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { if (it.target() == target) it.copy(problem = Problem.SAVE) else it } }
        }
    }

    fun canEditArticle(record: SpaceClient.Message): Boolean =
        record.author == wallet.publicKey && !record.pending && !record.rejected && !record.deleted &&
            record.position != null && record.change?.let { it.rejected || it.cancelled } != false

    suspend fun articleLink(target: Target, article: String): String = withContext(Dispatchers.IO) {
        localAccess.withLock {
            if (closed.get() || mutable.value.target() != target) throw CancellationException("Article channel changed")
            requireNotNull(client).articleLink(target.space, target.channel, article)
        }
    }

    fun publishArticle(target: Target, reviewed: SpaceArticleDraft, articleId: String?, onQueued: (String) -> Unit) {
        val slot = ArticleDraftTarget(target, articleId)
        queueArticle(target, onQueued) {
            dirtyArticleDrafts[slot]?.let { saveArticleDraft(this, slot, it) }
            val content = reviewed.content()
            if (articleId == null) publishArticle(target.space, content, target.channel, reviewed)
            else {
                editArticle(target.space, articleId, content, target.channel, reviewed)
                articleId
            }
        }
    }

    fun shareStackArticle(target: Target, link: nl.davidengelen.redsquare.saito.StackLink, article: nl.davidengelen.redsquare.saito.StackArticle,
        onQueued: (String) -> Unit) {
        require(article.author == link.author && article.revision == link.signature)
        queueArticle(target, onQueued) { shareStackArticle(target.space, link.backend, article.envelope, target.channel) }
    }

    private fun queueArticle(target: Target, onQueued: (String) -> Unit, queue: SpaceClient.() -> String) {
        if (target.thread != null || mutable.value.target() != target) return
        val route = navigationVersion.get()
        fun onOriginalRoute() = navigationVersion.get() == route && mutable.value.target() == target
        localAction(isCurrent = ::onOriginalRoute) {
            val id = localAccess.withLock {
                if (!onOriginalRoute()) return@localAction
                requireNotNull(client).queue().also {
                    urgentOutbox.add(target.space)
                    updateWindow(target, null)
                }
            }
            wake.trySend(Unit)
            publish()
            withContext(Dispatchers.Main.immediate) {
                if (onOriginalRoute()) onQueued(id)
            }
        }
    }

    fun stackArticleLibrary(link: nl.davidengelen.redsquare.saito.StackLink) =
        nl.davidengelen.redsquare.stack.StackLibrary(appContext, wallet, link.backend,
            if (link.backend == backend) endpoint else link.backend.peerUrl, requireNotNull(link.author), requireNotNull(link.signature))

    fun sendProtected(space: String, beforePublishing: suspend () -> Boolean) {
        val before = mutable.value
        val matches = before.selected == space && before.current?.protectedChat != null
        trace("protected-send clicked matches=$matches hasDraft=${before.draft.isNotBlank()} working=${before.working}")
        if (!matches || before.draft.isBlank() || before.working) return
        val version = selection.get()
        scope.launch {
            val allowed = withContext(Dispatchers.Main.immediate) { beforePublishing() }
            val sameRoute = selection.get() == version && wasVisible.get()
            trace("protected-send permission=$allowed sameRoute=$sameRoute")
            if (allowed && sameRoute) queueSend(space, before.draft)
        }
    }

    private fun queueSend(space: String? = null, requestedDraft: String? = null) {
        val before = mutable.value
        val target = before.target() ?: return
        if (space != null && (target.space != space || before.current?.protectedChat == null)) return
        val text = requestedDraft ?: before.draft
        if (text.isBlank()) return
        trace("send protected=${space != null} draftChanged=${before.draft != text} working=${before.working}")
        localAction {
            localAccess.withLock {
                val current = requireNotNull(client)
                if (mutable.value.target() == target && mutable.value.draft == text)
                    current.saveDraft(target, text)
                val parent = current.parent(target)
                val queued = if (parent == null) current.send(target.space, text, channel = target.channel)
                else current.reply(target.space, parent, text, target.channel)
                urgentOutbox.add(target.space)
                wake.trySend(Unit)
                updateWindow(target, null)
                if (destinationVisit?.target != target) {
                    dirtyAnchors.remove(target)
                    current.saveAnchor(target, null)
                }
                mutable.update { if (it.target() == target && it.draft == text) {
                    draftVersions.getOrPut(target) { AtomicLong() }.incrementAndGet()
                    dirtyDrafts.remove(target, text)
                    it.copy(draft = "", anchor = null, followingLatest = true, scrollToMessage = MessageScroll(queued))
                } else if (it.target() == target) it.copy(anchor = null, followingLatest = true, scrollToMessage = MessageScroll(queued)) else it }
            }
            publish(); wake.trySend(Unit)
        }
    }
    fun toggleReaction(target: Target, message: SpaceClient.Message, emoji: String, onFinished: (Boolean) -> Unit) {
        if (closed.get() || navigationSaving.get() || mutable.value.target() != target) {
            onFinished(false)
            return
        }
        if (nl.davidengelen.redsquare.BuildConfig.SPACES_SCALE_FIXTURE)
            SpacesScaleMeasurement.reactionPressed(message, emoji)
        scope.launch {
            var saved = false
            try {
                localAccess.withLock {
                    val current = requireNotNull(client)
                    val updated = current.toggleReaction(target.space, target.channel, message.id, emoji)
                    mutable.update { view ->
                        if (view.target() != target || view.destinationReadDenied) view else view.copy(
                            messages = view.messages.map { if (it.id == updated.id) updated else it },
                            threadParent = view.threadParent?.let { if (it.id == updated.id) updated else it }
                        )
                    }
                }
                urgentOutbox.add(target.space)
                wake.trySend(Unit)
                saved = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { if (it.target() == target) it.copy(problem = Problem.SAVE) else it } }
            finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    onFinished(saved && mutable.value.target() == target)
                }
            }
        }
    }
    suspend fun loadReactionPage(target: Target, message: SpaceClient.Message, afterEmoji: String?): SpaceClient.ReactionPage = withContext(Dispatchers.IO) {
        localAccess.withLock {
            if (closed.get() || mutable.value.target() != target) throw CancellationException("Conversation changed")
            requireNotNull(client).reactionPage(target.space, message, afterEmoji, target.channel)
        }
    }
    fun setNotificationPolicy(target: Target, mode: SpaceNotificationMode? = null, allowEveryone: Boolean? = null) {
        if (mutable.value.target() != target) return
        localAction(isCurrent = { mutable.value.target() == target }) {
            localAccess.withLock {
                val current = requireNotNull(client)
                val before = current.notificationPolicy(target.space, target.channel)
                current.setNotificationPolicy(target.space, before.copy(mode = mode ?: before.mode,
                    allowEveryone = allowEveryone ?: before.allowEveryone), target.channel)
            }
            publish()
        }
    }
    fun pinMessage(target: Target, message: SpaceClient.Message, pinned: Boolean, onQueued: () -> Unit) =
        changeMessage(target, onQueued) { pinMessage(target.space, requireNotNull(this.message(target.space, message.id, target.channel)), pinned, target.channel) }
    suspend fun loadPinnedPage(target: Target, afterMessage: String?): SpaceClient.PinnedPage = withContext(Dispatchers.IO) {
        localAccess.withLock {
            if (closed.get() || mutable.value.target() != target) throw CancellationException("Conversation changed")
            requireNotNull(client).pinnedMessages(target.space, afterMessage, target.channel)
        }
    }
    suspend fun loadMessage(target: Target, messageId: String): SpaceClient.Message? = withContext(Dispatchers.IO) {
        try { localAccess.withLock {
            if (closed.get() || mutable.value.target() != target) throw CancellationException("Conversation changed")
            requireNotNull(client).message(target.space, messageId, target.channel)
        } } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }

    fun moderationReportsSupported(): Boolean = client?.moderationReportsSupported() == true

    suspend fun reportMessage(target: Target, message: String, reason: String): Unit = withContext(Dispatchers.IO) {
        check(!closed.get() && mutable.value.target() == target && moderationReportsSupported())
        requireNotNull(client).reportMessage(target.space, target.channel, message, reason)
        if (closed.get() || mutable.value.target() != target) throw CancellationException("Conversation changed")
    }

    suspend fun messageReports(space: String, before: Long) = withContext(Dispatchers.IO) {
        requireModerationStaff(space)
        requireNotNull(client).messageReports(space, before).also { requireModerationStaff(space) }
    }

    suspend fun adminLog(space: String, before: SpaceAdminCursor?) = withContext(Dispatchers.IO) {
        requireModerationStaff(space)
        requireNotNull(client).adminLog(space, before).also { requireModerationStaff(space) }
    }

    suspend fun reviewReportedMessage(space: String, report: SpaceMessageReport) = withContext(Dispatchers.IO) {
        network.withLock {
            requireModerationStaff(space)
            requireNotNull(client).reviewReportedMessage(space, report).also { requireModerationStaff(space) }
        }
    }

    fun canReviewModeration(space: String, current: State): Boolean =
        !closed.get() && current.selected == space && current.current?.dissolved != true &&
            current.members.any { it.publicKey == ownerKey && it.role in setOf(SpaceRole.OWNER, SpaceRole.ADMIN) }

    private fun requireModerationStaff(space: String) {
        if (!canReviewModeration(space, mutable.value)) throw CancellationException("Moderation access changed")
    }
    fun cancelSend(target: Target, messageId: String, onFinished: (Boolean) -> Unit) = cancelOperation(target, messageId, false, onFinished)
    fun cancelChange(target: Target, operationId: String, onFinished: (Boolean) -> Unit) = cancelOperation(target, operationId, true, onFinished)
    private fun cancelOperation(target: Target, operationId: String, change: Boolean, onFinished: (Boolean) -> Unit) {
        if (mutable.value.target() != target) return
        localAction(isCurrent = { mutable.value.target() == target }) {
            val cancelled = localAccess.withLock {
                val current = requireNotNull(client)
                if (change) current.cancelQueuedChange(target.space, operationId) else current.cancelQueuedMessage(target.space, operationId)
            }
            onFinished(cancelled); publish()
        }
    }
    fun editMessage(target: Target, message: SpaceClient.Message, text: String, onQueued: () -> Unit) =
        changeMessage(target, onQueued) { editMessage(target.space, message, text, target.channel) }
    fun deleteMessage(target: Target, message: SpaceClient.Message, onQueued: () -> Unit) =
        changeMessage(target, onQueued) { deleteMessage(target.space, message, target.channel) }
    private fun changeMessage(target: Target, onQueued: () -> Unit, change: SpaceClient.() -> String) {
        if (mutable.value.target() != target) return
        localAction(isCurrent = { mutable.value.target() == target }) {
            localAccess.withLock { requireNotNull(client).change() }
            onQueued(); publish(); wake.trySend(Unit)
        }
    }
    fun readingPosition(space: String, followLatest: Boolean) {
        mutable.value.target()?.takeIf { it.space == space }?.let { readingPosition(space, it.channel, followLatest, thread = it.thread) }
    }
    fun readingPosition(space: String, channel: String, followLatest: Boolean, anchor: SpaceClient.ReadingAnchor? = null,
        thread: String? = null, moving: Boolean = false) {
        val before = mutable.value; val target = Target(space, channel, thread)
        if (before.target() != target || before.loadingChannel) return
        // An exact link keeps its detached window until the reader explicitly
        // asks for the latest page. Layout changes can alter canScrollForward.
        val visiting = destinationVisit?.target == target
        val latest = followLatest && !before.hasLater && (!visiting || before.followingLatest)
        if (fixtureTrace && latest != before.followingLatest)
            trace("reading followLatest=$latest windowFrom=${before.windowFrom} channel=${channel.take(10)} thread=${thread?.take(10)}")
        if (latest || windowFrom(target) == null) updateWindow(target, if (latest) null else before.windowFrom)
        mutable.update {
            if (it.target() != target) return@update it
            // The list already renders its moving offset. Keep recording it
            // for persistence without recomposing the whole Space per pixel.
            val presented = if (latest) null else if (moving) it.anchor else anchor ?: it.anchor
            if (it.followingLatest == latest && it.anchor == presented) it
            else it.copy(followingLatest = latest, anchor = presented)
        }
        if (visiting || !latest && anchor == null) return
        dirtyAnchors[target] = SavedViewport(if (latest) null else anchor, SystemClock.elapsedRealtime() + READING_POSITION_SETTLE_MILLIS)
        scheduleAnchorWrites()
    }

    private fun scheduleAnchorWrites() {
        if (!writingAnchors.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    val next = dirtyAnchors.entries.asSequence().filter { it.value.saveAfter != null }
                        .minByOrNull { requireNotNull(it.value.saveAfter) } ?: break
                    val (target, snapshot) = next
                    val remaining = requireNotNull(snapshot.saveAfter) - SystemClock.elapsedRealtime()
                    if (remaining > 0) delay(remaining)
                    else saveSettledAnchor(target, snapshot)
                }
            } finally {
                writingAnchors.set(false)
                if (scope.isActive && dirtyAnchors.values.any { it.saveAfter != null }) scheduleAnchorWrites()
            }
        }
    }

    private suspend fun saveSettledAnchor(target: Target, snapshot: SavedViewport): Unit = localAccess.withLock {
        if (dirtyAnchors[target] !== snapshot) return@withLock
        try {
            requireNotNull(client).saveAnchor(target, snapshot.anchor)
            dirtyAnchors.remove(target, snapshot)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            // Keep the anchor for the regular navigation save without spinning on a failed store.
            if (dirtyAnchors.replace(target, snapshot, SavedViewport(snapshot.anchor))) {
                mutable.update { if (it.target() == target) it.copy(problem = Problem.SAVE) else it }
            }
        }
    }
    private fun persistLocal(current: SpaceClient) = traceLocalWork("persist-local") {
        for ((target, text) in dirtyDrafts.entries.toList()) {
            current.saveDraft(target, text)
            dirtyDrafts.remove(target, text)
        }
        for ((slot, draft) in dirtyArticleDrafts.entries.toList()) saveArticleDraft(current, slot, draft)
        for ((target, viewport) in dirtyAnchors.entries.toList()) {
            current.saveAnchor(target, viewport.anchor)
            dirtyAnchors.remove(target, viewport)
        }
        for ((space, listed) in dirtyListings.entries.toList()) {
            current.setListed(space, listed); dirtyListings.remove(space, listed)
        }
    }
    private fun saveArticleDraft(client: SpaceClient, target: ArticleDraftTarget, draft: SpaceArticleDraft) {
        client.saveArticleDraft(target.channel.space, target.channel.channel, target.article, draft)
        dirtyArticleDrafts.remove(target, draft)
    }
    fun consumeScrollTarget(message: MessageScroll) {
        mutable.update { if (it.scrollToMessage == message) it.copy(scrollToMessage = null) else it }
    }
    fun olderMessages() {
        val target = mutable.value.target() ?: return
        val selected = selection.get()
        fun stillSelected() = selection.get() == selected && mutable.value.target() == target
        localAction(Problem.CONNECTION, isCurrent = ::stillSelected) { network.withLock {
            if (!stillSelected()) return@withLock
            val current = requireNotNull(client)
            val before = current.window(target)
            val parent = current.parent(target)
            val start = if (parent == null) current.older(target.space, channel = target.channel, before = before.from)
            else current.olderThread(target.space, parent, before.from, target.channel)
            synchronized(navigationChanges) {
                if (!stillSelected()) return@withLock
                updateWindow(target, start)
            }
            publish()
            mutable.update { state ->
                if (stillSelected() && state.target() == target)
                    state.copy(scrollToMessage = state.messages.firstOrNull()?.let { MessageScroll(it.id) }) else state
            }
        } }
    }
    fun latestMessages() {
        val target = mutable.value.target() ?: return
        updateWindow(target, null)
        if (destinationVisit?.target == target) {
            mutable.update { if (it.target() == target) it.copy(anchor = null, followingLatest = true) else it }
            scope.launch { publish() }
            return
        }
        val snapshot = SavedViewport(null)
        dirtyAnchors[target] = snapshot
        mutable.update { if (it.target() == target) it.copy(anchor = null, followingLatest = true) else it }
        localAction {
            localAccess.withLock {
                if (dirtyAnchors[target] === snapshot) {
                    requireNotNull(client).saveAnchor(target, null)
                    dirtyAnchors.remove(target, snapshot)
                }
            }
            publish()
            mutable.update { if (it.target() == target) it.copy(scrollToMessage = it.messages.lastOrNull()?.let { message -> MessageScroll(message.id) }) else it }
        }
    }
    fun markRead(space: String, channel: String, position: Long, thread: String? = null) {
        val target = Target(space, channel, thread)
        scope.launch {
            try { localAccess.withLock {
                val current = requireNotNull(client)
                if (thread == null) current.markRead(space, channel, position)
                else current.markThreadRead(space, requireNotNull(current.message(space, thread, channel)), position, channel)
            }; publish(includeCommunities = canRefreshCommunityList()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { if (it.target() == target) it.copy(problem = Problem.SAVE) else it } }
        }
    }
    fun restoreUnsentDraft(messageId: String) {
        val before = mutable.value
        val space = before.selected ?: return
        if (before.draft.isNotEmpty() || before.working) return
        val text = before.messages.singleOrNull { it.id == messageId && (it.rejected || it.cancelled) }?.text ?: return
        // Reuse the ordinary immediate, revision-fenced editing path. A queued
        // blank save cannot overwrite this user action or newer typing.
        editDraft(space, text)
    }
    fun create(name: String) = create(SpaceProfile(name), listOf(SpaceChannelProfile("general")))
    fun create(profile: SpaceProfile, channels: List<SpaceChannelProfile>) {
        val route = navigationVersion.get()
        localAction { createAndOpen(profile, channels, route) }
    }
    fun create(draft: SpaceCreation) {
        val route = navigationVersion.get()
        localAction {
            val profile = SpaceProfile(draft.name, draft.description, draft.visibility, draft.listed, draft.houseRules, draft.image)
            val channels = draft.channels.map { SpaceChannelProfile(it.name, it.kind, it.category) }
            createAndOpen(profile, channels, route)
        }
    }
    fun createProtectedChat(name: String, directPeer: String?, legacyId: String?) {
        val route = navigationVersion.get()
        localAction {
            val space = network.withLock { requireNotNull(client).createProtectedChat(name, directPeer, legacyId) }
            urgentOutbox.add(space)
            mutable.update { it.copy(created = space) }
            selectRoute(space, expected = route)
            publish()
            wake.trySend(Unit)
        }
    }
    private suspend fun createAndOpen(profile: SpaceProfile, channels: List<SpaceChannelProfile>, route: Long) {
        val space = localAccess.withLock { requireNotNull(client).create(profile, channels) }
        urgentOutbox.add(space)
        wake.trySend(Unit)
        mutable.update { it.copy(created = space) }
        selectRoute(space, expected = route)
        publish()
    }
    fun dismissCreation() { synchronized(navigationChanges) { navigationVersion.incrementAndGet() } }
    fun consumeCreated(space: String) { mutable.update { if (it.created == space) it.copy(created = null) else it } }
    fun invite(days: Int = 7) { mutable.value.selected?.let { invite(it, days) } }
    fun invite(space: String, days: Int = 7) {
        localAction {
            localAccess.withLock { requireNotNull(client).invite(space, days * 86_400_000L) }
            urgentOutbox.add(space)
            wake.trySend(Unit)
            publish()
        }
    }
    fun inviteProtectedChat(space: String, recipient: String) {
        localAction {
            localAccess.withLock { requireNotNull(client).inviteFor(space, recipient) }
            urgentOutbox.add(space)
            publish()
            wake.trySend(Unit)
        }
    }
    fun preview(link: String) {
        val token = previews.incrementAndGet()
        mutable.update { it.copy(preview = null, previewIssue = null, requiredNode = null) }
        localAction(Problem.INVITATION) { network.withLock {
            val current = client ?: run {
                val service = peerCall { authenticatedPeer().servicePublicKey("spaces-v1") }
                directory.save(EncryptedLocalFile.Contents(1, service)); cachedService = service; installClient(service)
                requireNotNull(client)
            }
            try {
                val preview = current.preview(link)
                if (previews.get() == token) mutable.update { it.copy(preview = preview) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (previews.get() == token) throw failure }
        } }
    }
    fun join(link: String, acceptedRulesToken: String? = null) = joinInvitation(link, acceptedRulesToken, false)
    fun joinProtectedChat(link: String) = joinInvitation(link, null, true)

    suspend fun recoveredScopes(): List<SpaceCrypto.Scope> = withContext(Dispatchers.IO) {
        client?.recoveredScopes().orEmpty()
    }

    fun openRecoveredHistory(archive: SpaceCrypto.Scope, expectProtectedChat: Boolean) {
        val route = navigationVersion.get()
        localAction { network.withLock {
            val space = requireNotNull(client).openRecoveredHistory(archive, expectProtectedChat)
            if (selectRoute(space, expected = route) != null) mutable.update { it.copy(openedRecovery = archive) }
            publish()
        } }
    }

    fun consumeRecoveredHistory(archive: SpaceCrypto.Scope) {
        mutable.update { if (it.openedRecovery == archive) it.copy(openedRecovery = null) else it }
    }

    fun replaceDevice(archive: SpaceCrypto.Scope, expectProtectedChat: Boolean) {
        localAction { network.withLock {
            val space = requireNotNull(client).requestDeviceReplacement(archive, expectProtectedChat)
            urgentOutbox.add(space)
            wake.trySend(Unit)
            publish()
        } }
    }

    fun replaceDevice(link: String, expectProtectedChat: Boolean) {
        val route = navigationVersion.get(); val review = previews.get()
        localAction(Problem.INVITATION) { network.withLock {
            val space = requireNotNull(client).requestDeviceReplacement(link, expectProtectedChat)
            urgentOutbox.add(space)
            if (previews.get() == review && selectRoute(space, expected = route) != null) {
                mutable.update { it.copy(preview = null, joinedInvitation = link) }
            }
            wake.trySend(Unit)
            publish()
        } }
    }

    private fun joinInvitation(link: String, acceptedRulesToken: String?, protectedChat: Boolean) {
        val route = navigationVersion.get(); val review = previews.get()
        localAction(Problem.INVITATION) { network.withLock {
            val space = requireNotNull(client).join(link, acceptedRulesToken, expectProtectedChat = protectedChat)
            if (previews.get() == review && selectRoute(space, expected = route) != null) {
                mutable.update { it.copy(preview = null, joinedInvitation = link) }
            }
            publish(); wake.trySend(Unit)
        } }
    }
    fun consumeJoinedInvitation(link: String) {
        mutable.update { if (it.joinedInvitation == link) it.copy(joinedInvitation = null) else it }
    }
    fun openPublic(space: String) {
        val route = navigationVersion.get()
        var activeRoute = route
        localAction(Problem.CONNECTION, isCurrent = { navigationVersion.get() == activeRoute }) { network.withLock {
            try {
                val current = requireNotNull(client)
                val opened = current.openPublic(space)
                val accepted = selectRoute(space, expected = route)
                if (accepted != null) {
                    activeRoute = accepted
                    // Complete this explicit reading action before queued
                    // background admissions and unread summaries can take over.
                    current.refresh(space, channel = opened.channel)
                }
                publish(); wake.trySend(Unit)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                trace("public-read failed=${failure.javaClass.simpleName} frames=${failure.stackTrace.take(4).joinToString { "${it.className}.${it.methodName}:${it.lineNumber}" }}")
                if (navigationVersion.get() == activeRoute) throw failure
            }
        } }
    }
    fun openPublicLink(link: String) {
        val route = navigationVersion.get()
        var activeRoute = route
        localAction(Problem.INVITATION, isCurrent = { navigationVersion.get() == activeRoute }, reference = link) {
            val current = requireNotNull(client)
            val cached = localAccess.withLock { current.cachedPublicLink(link) }
            if (cached != null) {
                // A saved, verified public conversation follows the same local
                // selection and background refresh as a My Spaces entry.
                val accepted = selectRoute(cached.id, expected = route)
                if (accepted != null) {
                    activeRoute = accepted
                    markPublicReferenceOpened(link, accepted)
                }
                return@localAction
            }
            network.withLock {
                try {
                    val opened = current.openPublicLink(link)
                    val accepted = selectRoute(opened.id, expected = route)
                    if (accepted != null) {
                        activeRoute = accepted
                        markPublicReferenceOpened(link, accepted)
                        current.refresh(opened.id, channel = opened.channel)
                    }
                    publish(); wake.trySend(Unit)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    trace("public-read failed=${failure.javaClass.simpleName} frames=${failure.stackTrace.take(4).joinToString { "${it.className}.${it.methodName}:${it.lineNumber}" }}")
                    if (navigationVersion.get() == activeRoute) throw failure
                }
            }
        }
    }
    fun openArticleLink(link: String) {
        val route = navigationVersion.get()
        var activeRoute = route
        localAction(Problem.INVITATION, isCurrent = { navigationVersion.get() == activeRoute }, reference = link) {
            val address = SpaceArticleReference.parse(link)
            val current = requireNotNull(client)
            var cached = false
            val opened = try { network.withLock { current.openArticleLink(link) } }
            catch (denied: SpaceClient.ReadDenied) { publish(); throw denied }
            catch (failure: IOException) {
                if (failure is ChangedIdentity || failure is MissingService) throw failure
                cached = true
                localAccess.withLock { current.cachedArticleLink(link) } ?: throw failure
            }
            if (opened == null) {
                synchronized(navigationChanges) {
                    if (navigationVersion.get() == route) mutable.update { it.copy(problem = Problem.ARTICLE_MISSING) }
                }
                return@localAction
            }
            val accepted = selectRoute(address.community.spaceId, address.channel, expected = route,
                article = ArticleFocus(opened.id, cached)) ?: return@localAction
            activeRoute = accepted
            markPublicReferenceOpened(link, accepted)
        }
    }
    suspend fun searchChannels(space: String, accessRevision: Long): List<SpaceClient.Channel> =
        searchRead(space, accessRevision) { searchChannels(space) }

    suspend fun searchCached(space: String, query: String, filter: SpaceSearchFilter, after: SpaceSearchCursor?, accessRevision: Long): SpaceSearchPage =
        searchRead(space, accessRevision) { searchCached(space, query, filter, after) }

    private suspend fun <T> searchRead(space: String, accessRevision: Long, read: SpaceClient.() -> T): T =
        withContext(Dispatchers.IO) {
            localAccess.withLock {
                if (closed.get() || mutable.value.selected != space) throw CancellationException("Space changed")
                val current = requireNotNull(client)
                val result = current.read()
                if (closed.get() || mutable.value.selected != space || current.readScopeRevision != accessRevision)
                    throw CancellationException("Search scope changed")
                result
            }
        }

    suspend fun activityPage(after: String?, accessRevision: Long): SpaceActivityPage = withContext(Dispatchers.IO) {
        localAccess.withLock {
            if (closed.get()) throw CancellationException("Account changed")
            val current = requireNotNull(client)
            val page = current.activityPage(after)
            if (closed.get() || current.readScopeRevision != accessRevision) throw CancellationException("Activity scope changed")
            page
        }
    }

    suspend fun destinationLink(target: Target, kind: SpaceDestination.Kind, record: String? = null): String =
        withContext(Dispatchers.IO) {
            localAccess.withLock {
                if (closed.get() || mutable.value.target() != target) throw CancellationException("Space changed")
                requireNotNull(client).destinationLink(target.space, target.channel, kind, record,
                    target.thread.takeIf { kind == SpaceDestination.Kind.MESSAGE })
            }
        }

    fun openDestinationLink(link: String, activity: SpaceActivityGroup? = null, fromNotification: Boolean = false,
        origin: Target? = null): Boolean {
        val route = navigationVersion.get()
        var activeRoute = route
        return localAction(Problem.INVITATION, isCurrent = { navigationVersion.get() == activeRoute }, reference = link) {
            initialDestinationPending.set(false)
            val address = SpaceDestination.parse(link)
            require(activity == null || activity.target == address)
            val current = requireNotNull(client)
            var cached = false
            val opened = try { network.withLock { current.openDestination(address) } }
            catch (denied: SpaceClient.ReadDenied) {
                publish()
                synchronized(navigationChanges) {
                    if (navigationVersion.get() == route) mutable.update { it.copy(previewIssue = denied.problem) }
                }
                return@localAction
            } catch (failure: IOException) {
                if (failure is ChangedIdentity || failure is MissingService) throw failure
                cached = true
                localAccess.withLock { current.cachedDestination(address) } ?: throw failure
            }
            if (opened == null) {
                synchronized(navigationChanges) {
                    if (navigationVersion.get() == route) mutable.update { it.copy(problem = Problem.DESTINATION_MISSING) }
                }
                return@localAction
            }
            val article = opened.record?.takeIf { address.kind == SpaceDestination.Kind.ARTICLE }?.let { ArticleFocus(it.id, cached) }
            activeRoute = selectRoute(address.community.spaceId, address.channel, expected = route,
                thread = opened.parent, jump = opened.record, article = article, requestChannelKeys = false, savedDestination = cached,
                exact = ExactRoute(link, activity, fromNotification, origin)) ?: return@localAction
        }
    }

    fun consumeArticleFocus(focus: ArticleFocus) {
        mutable.update { if (it.articleFocus == focus) it.copy(articleFocus = null) else it }
    }
    private fun markPublicReferenceOpened(link: String, route: Long) = synchronized(navigationChanges) {
        if (navigationVersion.get() == route) mutable.update { it.copy(openedPublicReference = link) }
    }
    fun dismissPublicReference(link: String? = null): Boolean = synchronized(navigationChanges) {
        if (link != null && mutable.value.openedPublicReference == link) return@synchronized false
        navigationVersion.incrementAndGet()
        referenceRequest?.cancel()
        mutable.update { it.copy(projectingDestination = false) }
        true
    }
    fun consumePublicReference(link: String) { mutable.update { if (it.openedPublicReference == link) it.copy(openedPublicReference = null) else it } }
    fun joinPublic(acceptedRulesToken: String? = null) {
        val space = mutable.value.selected ?: return
        localAction { localAccess.withLock { requireNotNull(client).joinPublic(space, acceptedRulesToken) }; publish(); wake.trySend(Unit) }
    }
    fun revokeInvitation(space: String) {
        localAction { localAccess.withLock { requireNotNull(client).revokeInvitation(space) }; publish(); wake.trySend(Unit) }
    }
    fun archiveChannel(space: String, channel: String, archived: Boolean) {
        val route = navigationVersion.get()
        localAction(Problem.SAVE, isCurrent = { navigationVersion.get() == route }) {
            localAccess.withLock {
                require(mutable.value.archiveSupported)
                requireNotNull(client).archiveChannel(space, channel, archived)
            }
            publishManagement()
            urgentOutbox.add(space); wake.trySend(Unit)
        }
    }
    fun setChannelAccess(space: String, channel: String, member: String, granted: Boolean) {
        val route = navigationVersion.get()
        localAction(Problem.SAVE, isCurrent = { navigationVersion.get() == route }) {
            localAccess.withLock {
                require(mutable.value.privateChannelAccessSupported)
                val current = requireNotNull(client)
                if (granted) current.grantChannelAccess(space, channel, member) else current.revokeChannelAccess(space, channel, member)
            }
            publishManagement()
            urgentOutbox.add(space); wake.trySend(Unit)
        }
    }
    /** An old rejection is acknowledged only after these selected settings have actually refreshed. */
    fun refreshChannelSettings(space: String, channel: String, onRefreshed: () -> Unit) {
        val route = navigationVersion.get()
        val target = mutable.value.target()?.takeIf { it.space == space && it.channel == channel } ?: return
        localAction(Problem.CONNECTION, isCurrent = { navigationVersion.get() == route && mutable.value.target() == target }) {
            network.withLock {
                if (navigationVersion.get() != route || mutable.value.target() != target) return@withLock
                requireNotNull(client).refresh(space, channel, from = windowFrom(target.copy(thread = null)))
                publish()
                if (navigationVersion.get() == route && mutable.value.target() == target) {
                    mutable.update { it.copy(availability = Availability.ONLINE) }
                    onRefreshed()
                }
            }
        }
    }
    fun createChannel(space: String, profile: SpaceChannelProfile, privateMembers: Set<String>? = null, categoryId: String? = null) {
        val route = navigationVersion.get()
        val started = if (fixtureTrace) android.os.SystemClock.elapsedRealtime() else 0L
        if (fixtureTrace) trace("channel-create requestedAtMs=$started")
        localAction(Problem.SAVE, isCurrent = { navigationVersion.get() == route }) {
            localAccess.withLock {
                if (fixtureTrace) trace("channel-create lockMs=${android.os.SystemClock.elapsedRealtime() - started}")
                require(mutable.value.discoverySupported == true)
                if (privateMembers != null) {
                    require(mutable.value.privateChannelAccessSupported)
                    requireNotNull(client).createPrivateChannel(space, profile, privateMembers, categoryId)
                } else requireNotNull(client).createChannel(space, profile, categoryId)
            }
            if (fixtureTrace) trace("channel-create durableMs=${android.os.SystemClock.elapsedRealtime() - started}")
            publishManagement()
            urgentOutbox.add(space); wake.trySend(Unit)
            if (fixtureTrace) trace("channel-create projectedMs=${android.os.SystemClock.elapsedRealtime() - started}")
        }
    }
    fun editChannel(space: String, channel: String, profile: SpaceChannelProfile, categoryId: String? = null) {
        val route = navigationVersion.get()
        localAction(Problem.SAVE, isCurrent = { navigationVersion.get() == route }) {
            localAccess.withLock {
                require(navigationVersion.get() == route && mutable.value.target()?.copy(thread = null) == Target(space, channel))
                val current = requireNotNull(client)
                val selected = current.channels(space).single { it.id == channel }
                require(selected.metadataReady && if (selected.visibility == SpaceVisibility.PUBLIC)
                    mutable.value.publicChannelEditSupported else mutable.value.privateChannelEditSupported)
                current.editChannel(space, channel, profile, categoryId)
            }
            publishManagement()
            urgentOutbox.add(space); wake.trySend(Unit)
        }
    }
    fun saveCategory(space: String, category: String?, profile: SpaceCategoryProfile, privateCreation: Boolean) {
        val route = navigationVersion.get()
        localAction(Problem.SAVE, isCurrent = { navigationVersion.get() == route }) {
            localAccess.withLock {
                require(navigationVersion.get() == route && mutable.value.selected == space && mutable.value.categoriesSupported)
                val current = requireNotNull(client)
                if (category == null) current.createCategory(space, profile,
                    if (privateCreation) SpaceVisibility.PRIVATE else SpaceVisibility.PUBLIC)
                else {
                    require(current.categories(space).any { it.id == category }) { "Category is no longer available here" }
                    current.editCategory(space, category, profile)
                }
            }
            publishManagement()
            urgentOutbox.add(space); wake.trySend(Unit)
        }
    }
    fun changeMembership(space: String, member: String, action: SpaceClient.MembershipAction) {
        val route = navigationVersion.get()
        localAction(Problem.SAVE, isCurrent = { navigationVersion.get() == route }) {
            localAccess.withLock {
                require(mutable.value.membershipSupported)
                val current = requireNotNull(client)
                when (action) {
                    SpaceClient.MembershipAction.LEAVE -> { require(member == ownerKey); current.leave(space) }
                    SpaceClient.MembershipAction.REMOVE -> current.removeMember(space, member)
                    SpaceClient.MembershipAction.BAN -> current.banMember(space, member)
                    SpaceClient.MembershipAction.UNBAN -> current.unbanMember(space, member)
                }
            }
            publishManagement()
            urgentOutbox.add(space); wake.trySend(Unit)
        }
    }
    fun dissolve(space: String) {
        val route = navigationVersion.get()
        localAction(Problem.SAVE, isCurrent = { navigationVersion.get() == route }) {
            localAccess.withLock {
                require(navigationVersion.get() == route && mutable.value.selected == space && mutable.value.dissolutionSupported)
                requireNotNull(client).dissolve(space)
            }
            publishManagement()
            urgentOutbox.add(space); wake.trySend(Unit)
        }
    }
    fun changeVisibility(space: String, visibility: SpaceVisibility, listed: Boolean) {
        val route = navigationVersion.get()
        localAction(Problem.SAVE, isCurrent = { navigationVersion.get() == route }) {
            localAccess.withLock {
                require(navigationVersion.get() == route && mutable.value.selected == space && mutable.value.visibilitySupported)
                requireNotNull(client).changeVisibility(space, visibility, listed)
            }
            publishManagement()
            urgentOutbox.add(space); wake.trySend(Unit)
        }
    }
    fun changeOwnership(space: String, action: SpaceClient.OwnershipAction, member: String? = null, proposalId: String? = null) {
        val route = navigationVersion.get()
        localAction(Problem.SAVE, isCurrent = { navigationVersion.get() == route }) {
            localAccess.withLock {
                require(navigationVersion.get() == route && mutable.value.selected == space && mutable.value.ownershipSupported)
                val current = requireNotNull(client)
                when (action) {
                    SpaceClient.OwnershipAction.OFFER -> current.offerOwnership(space, requireNotNull(member))
                    SpaceClient.OwnershipAction.ACCEPT -> current.acceptOwnership(space, requireNotNull(proposalId))
                    SpaceClient.OwnershipAction.CANCEL -> current.cancelOwnership(space, requireNotNull(proposalId))
                    SpaceClient.OwnershipAction.DECLINE -> current.declineOwnership(space, requireNotNull(proposalId))
                }
            }
            publishManagement()
            urgentOutbox.add(space); wake.trySend(Unit)
        }
    }

    fun setAdministrator(space: String, memberKey: String, administrator: Boolean) {
        val route = navigationVersion.get()
        localAction(Problem.SAVE, isCurrent = { navigationVersion.get() == route }) {
            localAccess.withLock {
                require(mutable.value.rolesSupported)
                requireNotNull(client).setAdministrator(space, memberKey, administrator)
            }
            publishManagement()
            urgentOutbox.add(space); wake.trySend(Unit)
        }
    }
    fun setListed(space: String, listed: Boolean) {
        if (closed.get() || navigationSaving.get()) return
        dirtyListings[space] = listed
        val version = listingVersions.getOrPut(space) { AtomicLong() }.incrementAndGet()
        mutable.update { if (it.selected == space) it.copy(listingIntent = listed) else it }
        scope.launch {
            try {
                localAccess.withLock {
                    if (listingVersions[space]?.get() == version) {
                        requireNotNull(client).setListed(space, listed)
                        dirtyListings.remove(space, listed)
                    }
                }
                publish(); wake.trySend(Unit)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: IllegalArgumentException) {
                dirtyListings.remove(space, listed)
                mutable.update { if (it.selected == space) it.copy(problem = Problem.SAVE) else it }
            }
            catch (_: Exception) { mutable.update { if (it.selected == space) it.copy(problem = Problem.SAVE) else it } }
        }
    }
    fun discover(query: String = mutable.value.directoryQuery, more: Boolean = false) {
        if (closed.get()) return
        val token = directoryRequests.incrementAndGet()
        val trimmed = query.trim()
        val cursor = if (more && trimmed == mutable.value.directoryQuery) mutable.value.directoryCursor else 0L
        mutable.update { it.copy(directoryQuery = trimmed, directoryLoading = true, directoryFailed = false,
            directoryLoaded = it.directoryLoaded && trimmed == it.directoryQuery) }
        scope.launch {
            try {
                val current = client ?: return@launch
                if (cursor == 0L) {
                    val normalized = trimmed.lowercase(java.util.Locale.ROOT)
                    val cached = localAccess.withLock { current.cachedDirectory().filter {
                        (it.profile.name + " " + it.profile.description).lowercase(java.util.Locale.ROOT).contains(normalized)
                    } }
                    mutable.update { if (directoryRequests.get() == token) it.copy(directoryItems = cached, directoryCached = cached.isNotEmpty(), directoryHasMore = false) else it }
                }
                network.withLock {
                    if (directoryRequests.get() != token) return@withLock
                    val connection = authenticatedPeer()
                    if ("spaces-directory-v1" !in peerCall { connection.services() }) {
                        mutable.update { if (directoryRequests.get() == token) it.copy(discoverySupported = false) else it }
                        return@withLock
                    }
                    val page = current.discover(trimmed, cursor)
                    mutable.update { if (directoryRequests.get() == token && it.directoryQuery == trimmed) it.copy(
                        directoryItems = (if (cursor == 0L) page.items else it.directoryItems + page.items).distinctBy { item -> item.id },
                        directoryCached = false, directoryCursor = page.nextCursor, directoryHasMore = page.hasMore,
                        discoverySupported = true, directoryFailed = false, directoryLoaded = true) else it }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: ChangedIdentity) { mutable.update { it.copy(availability = Availability.CHANGED_IDENTITY) } }
            catch (_: Exception) { mutable.update { if (directoryRequests.get() == token) it.copy(directoryFailed = true) else it } }
            finally { mutable.update { if (directoryRequests.get() == token) it.copy(directoryLoading = false) else it } }
        }
    }
    fun dismissPreview() { previews.incrementAndGet(); mutable.update {
        it.copy(preview = null, previewIssue = null, requiredNode = null,
            problem = it.problem.takeUnless { problem -> problem == Problem.INVITATION })
    } }
    fun clearProblem() { mutable.update { it.copy(problem = null) } }
    private fun localAction(problem: Problem = Problem.SAVE, isCurrent: () -> Boolean = { true }, reference: String? = null,
        action: suspend () -> Unit): Boolean {
        fun report(update: (State) -> State) = synchronized(navigationChanges) {
            if (isCurrent()) mutable.update(update)
        }
        val request = synchronized(navigationChanges) {
            if (closed.get() || navigationSaving.get() || mutable.value.working || mutable.value.loading) return false
            mutable.update { state ->
                val working = state.copy(working = true, problem = null)
                if (reference == null) working else working.copy(openingPublicReference = reference, previewIssue = null, requiredNode = null)
            }
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try { action() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (denied: SpaceClient.ReadDenied) {
                    report { it.copy(accessProblems = it.accessProblems + (denied.spaceId to denied.problem),
                        previewIssue = if (problem == Problem.INVITATION) denied.problem else it.previewIssue) }
                }
                catch (different: SpaceClient.DifferentNode) { report { it.copy(requiredNode = different.node) } }
                catch (_: ChangedIdentity) { report { it.copy(availability = Availability.CHANGED_IDENTITY) } }
                catch (_: MissingService) { report { it.copy(availability = Availability.UNSUPPORTED) } }
                catch (_: SpaceClient.ProtectedChatUnsupported) { report { it.copy(problem = Problem.PROTECTED_CHAT_UNSUPPORTED) } }
                catch (_: SpaceClient.DeviceReplacementUnsupported) { report { it.copy(problem = Problem.DEVICE_RECOVERY_UNSUPPORTED) } }
                catch (_: SpaceClient.NoReplacementApprover) { report { it.copy(problem = Problem.DEVICE_RECOVERY_NO_APPROVER) } }
                catch (_: SpaceClient.ActiveDeviceAlreadyPresent) { report { it.copy(problem = Problem.DEVICE_ALREADY_ACTIVE) } }
                catch (_: SpaceClient.RecoveredHistoryOtherModule) { report { it.copy(problem = Problem.RECOVERY_OTHER_MODULE) } }
                catch (_: Exception) { report { it.copy(problem = problem) } }
            }
            if (reference != null) referenceRequest = job
            job.invokeOnCompletion {
                synchronized(navigationChanges) {
                    if (referenceRequest === job) referenceRequest = null
                    mutable.update { it.copy(working = false, openingPublicReference = null) }
                }
            }
            job
        }
        request.start()
        return true
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
                        if (current == null) dirtyDrafts.isEmpty() && dirtyArticleDrafts.isEmpty() && dirtyAnchors.isEmpty() && dirtyListings.isEmpty()
                        else {
                            persistLocal(current)
                            true
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                }
            }
            if (!saved) { mutable.update { it.copy(problem = Problem.SAVE) }; return false }
            currentCoroutineContext().ensureActive()
            synchronized(navigationChanges) { navigationVersion.incrementAndGet() }
            return commit()
        } finally {
            navigationSaving.set(false)
            mutable.update { it.copy(savingNavigation = false) }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        avatarImages.close()
        scope.cancel(); wake.close(); peer?.close()
        CoroutineScope(Dispatchers.IO).launch {
            scope.coroutineContext[Job]?.join()
            peer?.close(); peer = null
            local?.close()
        }
    }
}
