package nl.davidengelen.redsquare.spaces

import android.content.Context
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
internal class SpacesAccount(context: Context, private val wallet: SaitoWallet, val backend: SaitoBackend,
    private val endpoint: String, parent: CoroutineScope) {
    enum class Availability { CHECKING, ONLINE, OFFLINE, UNSUPPORTED, CHANGED_IDENTITY, STORAGE_UNAVAILABLE }
    enum class Problem { CONNECTION, SAVE, INVITATION }
    data class Target(val space: String, val channel: String, val thread: String? = null)
    data class State(val loading: Boolean = true, val availability: Availability = Availability.CHECKING,
        val communities: List<SpaceClient.Community> = emptyList(), val selected: String? = null,
        val selectedChannel: String? = null, val threadParent: SpaceClient.Message? = null, val current: SpaceClient.Community? = null, val publicRulesToken: String? = null,
        val anchor: SpaceClient.ReadingAnchor? = null, val created: String? = null,
        val listingIntent: Boolean? = null, val previewIssue: SpaceClient.ReadProblem? = null, val publicLink: String? = null,
        val archiveChange: SpaceClient.ArchiveChange? = null, val roleChange: SpaceClient.RoleChange? = null,
        val channelEdit: SpaceClient.ChannelEdit? = null, val channelCreation: SpaceClient.ChannelCreation? = null,
        val channelAccessChange: SpaceClient.ChannelAccessChange? = null,
        val dissolutionChange: SpaceClient.DissolutionChange? = null, val visibilityChange: SpaceClient.VisibilityChange? = null, val categoryChange: SpaceClient.CategoryChange? = null, val categories: List<SpaceClient.Category> = emptyList(),
        val ownershipOffer: SpaceOwnershipOffer? = null, val ownershipChange: SpaceClient.OwnershipChange? = null,
        val membershipChange: SpaceClient.MembershipChange? = null, val bannedMembers: Set<String> = emptySet(),
        val joinedInvitation: String? = null, val openedPublicReference: String? = null, val contentLoaded: Boolean = false, val requiredNode: String? = null,
        val profiles: Map<String, SpaceProfile> = emptyMap(), val channels: List<SpaceClient.Channel> = emptyList(),
        val members: List<SpaceClient.Member> = emptyList(), val accessProblems: Map<String, SpaceClient.ReadProblem> = emptyMap(),
        val discoverySupported: Boolean? = null, val unreadSupported: Boolean = false,
        val dissolutionSupported: Boolean = false, val visibilitySupported: Boolean = false, val categoriesSupported: Boolean = false, val archiveSupported: Boolean = false, val rolesSupported: Boolean = false, val membershipSupported: Boolean = false, val ownershipSupported: Boolean = false, val publicChannelEditSupported: Boolean = false,
        val privateChannelAccessSupported: Boolean = false, val privateChannelEditSupported: Boolean = false,
        val directoryItems: List<SpaceClient.PublicCommunity> = emptyList(), val directoryQuery: String = "",
        val directoryLoading: Boolean = false, val directoryCached: Boolean = false, val directoryFailed: Boolean = false,
        val directoryLoaded: Boolean = false,
        val directoryCursor: Long = 0, val directoryHasMore: Boolean = false,
        val messages: List<SpaceClient.Message> = emptyList(), val draft: String = "",
        val invitation: SpaceClient.Invitation? = null, val preview: SpaceClient.InvitationPreview? = null,
        val problem: Problem? = null, val working: Boolean = false, val refreshing: Boolean = false,
        val ready: Boolean = false, val savingNavigation: Boolean = false, val loadingChannel: Boolean = false, val hasEarlier: Boolean = false, val hasLater: Boolean = false, val scrollToMessage: String? = null, val windowFrom: Long = 0, val followingLatest: Boolean = true)
    private val appContext = context.applicationContext
    private val fixtureTrace = nl.davidengelen.redsquare.BuildConfig.DEBUG && endpoint.startsWith("ws://127.0.0.1:") &&
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
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val network = Mutex()
    private val localAccess = Mutex()
    private val managementChanges = Any()
    private val urgentOutbox = ConcurrentHashMap.newKeySet<String>()
    private val closed = AtomicBoolean()
    private val selection = AtomicLong()
    private val navigationVersion = AtomicLong()
    private val navigationChanges = Any()
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
        return if (parent == null) historyWindow(target.space, historyWindows[target], target.channel)
        else threadHistoryWindow(target.space, parent, historyWindows[target], target.channel)
    }
    private val historyWindows = ConcurrentHashMap<Target, Long>()
    val ownerKey: String get() = wallet.publicKey
    val avatarImages = SpaceAvatarImages(wallet.publicKey, backend)
    private val draftVersions = ConcurrentHashMap<Target, AtomicLong>()
    private val dirtyDrafts = ConcurrentHashMap<Target, String>()
    private data class SavedViewport(val anchor: SpaceClient.ReadingAnchor?)
    private val dirtyAnchors = ConcurrentHashMap<Target, SavedViewport>()
    private val anchorVersions = ConcurrentHashMap<Target, AtomicLong>()
    private val dirtyListings = ConcurrentHashMap<String, Boolean>()
    private val listingVersions = ConcurrentHashMap<String, AtomicLong>()
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
                    for (community in current.communities()) {
                        try {
                            trace("refresh background=${community.id.take(10)} flush-start")
                            current.flush(community.id)
                            trace("refresh background=${community.id.take(10)} admit-start")
                            current.admitPending(community.id)
                            current.flush(community.id)
                            flushUrgent(current)
                            trace("refresh background=${community.id.take(10)} unread-start")
                            if ("spaces-unread-v1" in services) current.refreshUnread(community.id)
                            trace("refresh background=${community.id.take(10)} complete")
                            mutable.update { it.copy(accessProblems = it.accessProblems - community.id) }
                        } catch (denied: SpaceClient.ReadDenied) {
                            mutable.update { it.copy(accessProblems = it.accessProblems + (denied.spaceId to denied.problem)) }
                        }
                    }
                    mutable.value.target()?.let { target ->
                        try {
                            val selected = current.community(target.space, channel = target.channel)
                            // A new local creation or public join can enter
                            // after this cycle's outbox inventory was taken.
                            // Flush it before asking the node for access. A
                            // private invitation still needs reads for Welcome.
                            if (!selected.joined && selected.pending && !selected.membershipRequested) return@let
                            flushUrgent(current)
                            trace("refresh selected=${target.space.take(10)} channel=${target.channel.take(10)} start")
                            current.refresh(target.space, channel = target.channel, from = if (target.thread == null) historyWindows[target] else null)
                            current.parent(target)?.let { current.refreshThread(target.space, it, target.channel, historyWindows[target]) }
                            trace("refresh selected=${target.space.take(10)} channel=${target.channel.take(10)} complete")
                            mutable.update { it.copy(accessProblems = it.accessProblems - target.space) }
                        } catch (denied: SpaceClient.ReadDenied) {
                            mutable.update { it.copy(accessProblems = it.accessProblems + (denied.spaceId to denied.problem)) }
                        }
                    }
                    flushUrgent(current)
                    publish()
                    trace("refresh projected")
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
        client = SpaceClient(wallet, backend.webUrl, service, storage, Clock.systemUTC(), onDeliveryAttempt = { delivery ->
            val started = if (fixtureTrace) android.os.SystemClock.elapsedRealtime() else 0L
            if (delivery == SpaceClient.Delivery.CONTROL) publishManagement() else publish()
            if (fixtureTrace) trace("delivery projectionMs=${android.os.SystemClock.elapsedRealtime() - started}")
        }) { original -> peerCall {
            val started = if (fixtureTrace) android.os.SystemClock.elapsedRealtime() else 0L
            val connection = authenticatedPeer()
            if (connection.servicePublicKey("spaces-v1") != service) throw ChangedIdentity()
            val received = connection.request(original) { check(!closed.get()) }.toString(Charsets.UTF_8)
            if (fixtureTrace) trace("request=${original.id.take(10)} returnedMs=${android.os.SystemClock.elapsedRealtime() - started}")
            received
        } }
        mutable.update { it.copy(ready = true) }
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
    private suspend fun flushUrgent(current: SpaceClient) {
        for (space in urgentOutbox.toList()) if (urgentOutbox.remove(space)) {
            try { current.flush(space) }
            catch (failure: Exception) { urgentOutbox.add(space); throw failure }
        }
    }

    private suspend fun publish() = localAccess.withLock { traceLocalWork("publish") {
        val current = client ?: return@withLock
        val before = mutable.value
        val target = before.target()
        val projection = current.presentation(target?.space, target?.channel, target?.let { historyWindows[it] }, target?.let { current.parent(it) })
        val communities = projection.communities
        val profiles = projection.profiles
        val community = projection.current
        val channels = projection.channels
        val members = projection.members
        val bannedMembers = projection.bannedMembers
        val window = projection.window
        val messages = projection.messages
        val invitation = projection.invitation
        val publicRulesToken = target?.let { profiles[it.space] }?.takeIf { it.visibility == SpaceVisibility.PUBLIC }?.publicRulesToken
        val listingIntent = projection.listingIntent
        val publicLink = projection.publicLink
        synchronized(managementChanges) {
            val changes = managementProjection(current, target)
            mutable.update { previous ->
                if (previous.target() != target || previous.loadingChannel) previous.copy(communities = communities, profiles = profiles)
                else previous.copy(communities = communities, profiles = profiles, current = community, channels = channels, categories = projection.categories, members = members, bannedMembers = bannedMembers,
                messages = messages, threadParent = projection.threadParent, invitation = invitation, publicRulesToken = publicRulesToken,
                listingIntent = target?.let { dirtyListings[it.space] } ?: listingIntent, publicLink = publicLink,
                contentLoaded = window?.loaded == true, hasEarlier = window?.hasEarlier == true,
                hasLater = window?.hasLater == true, windowFrom = window?.from ?: 0,
                followingLatest = target == null || historyWindows[target] == null).withManagement(changes)
            }
        }
    } }

    fun visible(active: Boolean) {
        if (wasVisible.getAndSet(active) && !active) synchronized(navigationChanges) { navigationVersion.incrementAndGet() }
        ticker?.cancel(); ticker = null
        if (active) ticker = scope.launch {
            while (isActive) { wake.trySend(Unit); delay(5_000) }
        }
    }
    fun refresh() { wake.trySend(Unit) }
    fun select(space: String?, channel: String? = null) { selectRoute(space, channel) }
    fun openThread(message: SpaceClient.Message) {
        val target = mutable.value.target() ?: return
        if (target.thread != null) return
        selectRoute(target.space, target.channel, thread = message)
    }
    fun closeThread() { mutable.value.target()?.let { select(it.space, it.channel) } }
    private fun selectRoute(space: String?, channel: String? = null, expected: Long? = null, thread: SpaceClient.Message? = null): Long? {
        if (closed.get()) return null
        return synchronized(navigationChanges) {
            if (expected != null && navigationVersion.get() != expected) return@synchronized null
            val route = navigationVersion.incrementAndGet()
            val token = selection.incrementAndGet()
            mutable.update { it.copy(selected = space, selectedChannel = channel, threadParent = null, current = null,
                messages = emptyList(), channels = emptyList(), members = emptyList(), draft = "", invitation = null, anchor = null,
                problem = null, listingIntent = null, publicLink = null, archiveChange = null, roleChange = null, channelEdit = null, channelCreation = null, categoryChange = null, visibilityChange = null, dissolutionChange = null, categories = emptyList(), channelAccessChange = null, membershipChange = null, ownershipOffer = null, ownershipChange = null, bannedMembers = emptySet(),
                contentLoaded = false, loadingChannel = space != null, scrollToMessage = null) }
            scope.launch {
                try {
                    val current = client ?: return@launch
                    localAccess.withLock { traceLocalWork("select-route") {
                        if (selection.get() != token) return@withLock
                        persistLocal(current)
                        if (space == null) {
                            mutable.update { if (selection.get() == token) it.copy(loadingChannel = false) else it }
                            return@withLock
                        }
                        if (thread != null) current.selectThread(space, thread, channel)
                        else if (channel != null) current.selectChannel(space, channel)
                        val selectedCommunity = current.community(space)
                        val parent = current.selectedThread(space, selectedCommunity.channel)
                        val target = Target(space, selectedCommunity.channel, parent?.id)
                        val version = draftVersions.getOrPut(target) { AtomicLong() }.get()
                        val anchor = current.anchor(target)
                        if (anchor != null) historyWindows.putIfAbsent(target, anchor.windowFrom)
                        val projection = current.presentation(space, target.channel, historyWindows[target], parent)
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
                        mutable.update { if (it.selected == space && selection.get() == token) it.copy(
                            selectedChannel = target.channel, threadParent = projection.threadParent, current = community, channels = channels, categories = projection.categories, categoryChange = projection.management.category, visibilityChange = projection.management.visibility, dissolutionChange = projection.management.dissolution, members = members, anchor = anchor,
                            archiveChange = archiveChange, roleChange = roleChange, membershipChange = membershipChange, ownershipOffer = ownershipOffer, ownershipChange = ownershipChange, bannedMembers = bannedMembers, channelEdit = channelEdit, channelCreation = channelCreation, channelAccessChange = channelAccessChange,
                            profiles = it.profiles + (space to profile), publicRulesToken = rulesToken, publicLink = publicLink, invitation = invitation,
                            listingIntent = dirtyListings[space] ?: listingIntent,
                            draft = if (draftVersions[target]?.get() == version) draft else it.draft,
                            messages = messages, loadingChannel = false, contentLoaded = window.loaded, hasEarlier = window.hasEarlier,
                            hasLater = window.hasLater, windowFrom = window.from, followingLatest = historyWindows[target] == null) else it }
                    } }
                    publish(); wake.trySend(Unit)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutable.update { if (it.selected == space && selection.get() == token)
                    it.copy(problem = Problem.SAVE, loadingChannel = false) else it } }
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
    fun send() {
        val before = mutable.value
        val target = before.target() ?: return
        localAction {
            localAccess.withLock {
                val current = requireNotNull(client)
                if (mutable.value.target() == target && mutable.value.draft == before.draft)
                    current.saveDraft(target, before.draft)
                val parent = current.parent(target)
                val queued = if (parent == null) current.send(target.space, before.draft, channel = target.channel)
                else current.reply(target.space, parent, before.draft, target.channel)
                historyWindows.remove(target)
                anchorVersions.getOrPut(target) { AtomicLong() }.incrementAndGet()
                dirtyAnchors.remove(target)
                current.saveAnchor(target, null)
                mutable.update { if (it.target() == target && it.draft == before.draft) {
                    draftVersions.getOrPut(target) { AtomicLong() }.incrementAndGet()
                    dirtyDrafts.remove(target, before.draft)
                    it.copy(draft = "", anchor = null, followingLatest = true, scrollToMessage = queued)
                } else if (it.target() == target) it.copy(anchor = null, followingLatest = true, scrollToMessage = queued) else it }
            }
            publish(); wake.trySend(Unit)
        }
    }
    fun cancelSend(target: Target, messageId: String, onFinished: (Boolean) -> Unit) {
        if (mutable.value.target() != target) return
        localAction(isCurrent = { mutable.value.target() == target }) {
            val cancelled = localAccess.withLock { requireNotNull(client).cancelQueuedMessage(target.space, messageId) }
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
    fun readingPosition(space: String, channel: String, followLatest: Boolean, anchor: SpaceClient.ReadingAnchor? = null, thread: String? = null) {
        val before = mutable.value; val target = Target(space, channel, thread)
        if (before.target() != target || before.loadingChannel) return
        val latest = followLatest && !before.hasLater
        if (latest) historyWindows.remove(target) else historyWindows.putIfAbsent(target, before.windowFrom)
        mutable.update { if (it.target() == target) it.copy(followingLatest = latest, anchor = if (latest) null else anchor ?: it.anchor) else it }
        if (!latest && anchor == null) return
        val snapshot = SavedViewport(if (latest) null else anchor)
        dirtyAnchors[target] = snapshot
        val version = anchorVersions.getOrPut(target) { AtomicLong() }.incrementAndGet()
        scope.launch {
            delay(150)
            try { localAccess.withLock {
                if (anchorVersions[target]?.get() == version) {
                    requireNotNull(client).saveAnchor(target, snapshot.anchor)
                    dirtyAnchors.remove(target, snapshot)
                }
            } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { if (it.target() == target) it.copy(problem = Problem.SAVE) else it } }
        }
    }
    private fun persistLocal(current: SpaceClient) = traceLocalWork("persist-local") {
        for ((target, text) in dirtyDrafts.entries.toList()) {
            current.saveDraft(target, text)
            dirtyDrafts.remove(target, text)
        }
        for ((target, viewport) in dirtyAnchors.entries.toList()) {
            current.saveAnchor(target, viewport.anchor)
            dirtyAnchors.remove(target, viewport)
        }
        for ((space, listed) in dirtyListings.entries.toList()) {
            current.setListed(space, listed); dirtyListings.remove(space, listed)
        }
    }
    fun consumeScrollTarget(message: String) {
        mutable.update { if (it.scrollToMessage == message) it.copy(scrollToMessage = null) else it }
    }
    fun olderMessages() {
        val target = mutable.value.target() ?: return
        localAction(Problem.CONNECTION) { network.withLock {
            val current = requireNotNull(client)
            val before = current.window(target)
            val parent = current.parent(target)
            val start = if (parent == null) current.older(target.space, channel = target.channel, before = before.from)
            else current.olderThread(target.space, parent, before.from, target.channel)
            historyWindows[target] = start
            publish()
            mutable.update { if (it.target() == target) it.copy(scrollToMessage = it.messages.firstOrNull()?.id) else it }
        } }
    }
    fun latestMessages() {
        val target = mutable.value.target() ?: return
        historyWindows.remove(target)
        val snapshot = SavedViewport(null)
        dirtyAnchors[target] = snapshot
        val version = anchorVersions.getOrPut(target) { AtomicLong() }.incrementAndGet()
        mutable.update { if (it.target() == target) it.copy(anchor = null, followingLatest = true) else it }
        localAction {
            localAccess.withLock {
                if (anchorVersions[target]?.get() == version) {
                    requireNotNull(client).saveAnchor(target, null)
                    dirtyAnchors.remove(target, snapshot)
                }
            }
            publish()
            mutable.update { if (it.target() == target) it.copy(scrollToMessage = it.messages.lastOrNull()?.id) else it }
        }
    }
    fun markRead(space: String, channel: String, position: Long) {
        val target = Target(space, channel)
        scope.launch {
            try { localAccess.withLock { requireNotNull(client).markRead(space, channel, position) }; publish() }
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
    private suspend fun createAndOpen(profile: SpaceProfile, channels: List<SpaceChannelProfile>, route: Long) {
        val space = localAccess.withLock { requireNotNull(client).create(profile, channels) }
        mutable.update { it.copy(created = space) }
        selectRoute(space, expected = route); publish(); wake.trySend(Unit)
    }
    fun dismissCreation() { synchronized(navigationChanges) { navigationVersion.incrementAndGet() } }
    fun consumeCreated(space: String) { mutable.update { if (it.created == space) it.copy(created = null) else it } }
    fun invite(days: Int = 7) { mutable.value.selected?.let { invite(it, days) } }
    fun invite(space: String, days: Int = 7) {
        localAction { localAccess.withLock { requireNotNull(client).invite(space, days * 86_400_000L) }; publish(); wake.trySend(Unit) }
    }
    fun preview(link: String) {
        val token = previews.incrementAndGet()
        mutable.update { it.copy(preview = null, previewIssue = null) }
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
    fun join(link: String, acceptedRulesToken: String? = null) {
        val route = navigationVersion.get(); val review = previews.get()
        localAction(Problem.INVITATION) { network.withLock {
            val space = requireNotNull(client).join(link, acceptedRulesToken)
            if (previews.get() == review) {
                mutable.update { it.copy(preview = null, joinedInvitation = link) }
                selectRoute(space, expected = route)
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
        mutable.update { it.copy(previewIssue = null, requiredNode = null, problem = null) }
        localAction(Problem.INVITATION, isCurrent = { navigationVersion.get() == activeRoute }) {
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
    private fun markPublicReferenceOpened(link: String, route: Long) = synchronized(navigationChanges) {
        if (navigationVersion.get() == route) mutable.update { it.copy(openedPublicReference = link) }
    }
    fun dismissPublicReference() { synchronized(navigationChanges) { navigationVersion.incrementAndGet() } }
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
                requireNotNull(client).refresh(space, channel, from = historyWindows[target.copy(thread = null)])
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
    fun dismissPreview() { previews.incrementAndGet(); mutable.update { it.copy(preview = null, previewIssue = null) } }
    fun clearProblem() { mutable.update { it.copy(problem = null) } }
    private fun localAction(problem: Problem = Problem.SAVE, isCurrent: () -> Boolean = { true }, action: suspend () -> Unit) {
        if (closed.get() || navigationSaving.get() || mutable.value.working || mutable.value.loading) return
        mutable.update { it.copy(working = true, problem = null) }
        fun report(update: (State) -> State) = synchronized(navigationChanges) {
            if (isCurrent()) mutable.update(update)
        }
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (denied: SpaceClient.ReadDenied) {
                report { it.copy(accessProblems = it.accessProblems + (denied.spaceId to denied.problem),
                    previewIssue = if (problem == Problem.INVITATION) denied.problem else it.previewIssue) }
            }
            catch (different: SpaceClient.DifferentNode) { report { it.copy(requiredNode = different.node) } }
            catch (_: ChangedIdentity) { report { it.copy(availability = Availability.CHANGED_IDENTITY) } }
            catch (_: MissingService) { report { it.copy(availability = Availability.UNSUPPORTED) } }
            catch (_: Exception) { report { it.copy(problem = problem) } }
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
                        if (current == null) dirtyDrafts.isEmpty() && dirtyAnchors.isEmpty() && dirtyListings.isEmpty()
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
