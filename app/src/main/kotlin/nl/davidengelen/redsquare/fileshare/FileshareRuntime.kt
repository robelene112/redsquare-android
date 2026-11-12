package nl.davidengelen.redsquare.fileshare

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.chat.receiveChat
import nl.davidengelen.redsquare.moderation.AndroidPersonalControls
import nl.davidengelen.redsquare.saito.*
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Process-owned sessions survive screen disposal. Only live work starts the foreground service. */
internal object FileshareRuntime {
    private val sessions = ConcurrentHashMap<String, FileSession>()
    private val changes = MutableStateFlow<List<FileTransfer>>(emptyList())
    val transfers: StateFlow<List<FileTransfer>> = changes.asStateFlow()
    private val preparingChanges = MutableStateFlow<List<FilePreparation>>(emptyList())
    val preparations = preparingChanges.asStateFlow()
    val chatChanges = MutableSharedFlow<Triple<String, SaitoBackend, String?>>(extraBufferCapacity = 16)
    fun session(context: Context, wallet: SaitoWallet, backend: SaitoBackend, endpoint: String, contact: String): FileSession {
        require(SaitoWallet.isPublicKey(contact) && contact != wallet.publicKey)
        return sessions.computeIfAbsent("${wallet.publicKey}:${backend.id}:$endpoint:$contact") {
            FileSession(context.applicationContext, wallet, backend, endpoint, contact, ::publish)
        }
    }
    @Synchronized private fun publish() {
        changes.value = sessions.values.flatMap { it.transfers.value }.sortedByDescending { it.createdAt }
        preparingChanges.value = sessions.values.mapNotNull { it.preparation.value }
    }
    /** Initial file signalling can land on Talk's or a temporary Archive socket too. */
    fun receive(owner: String, backend: SaitoBackend, endpoint: String, bytes: ByteArray): Boolean {
        return sessions.values.filter { it.owner == owner && it.backend == backend && it.endpoint == endpoint && it.ownsDelivery }
            .any { session -> runCatching { session.receive(bytes) }.getOrDefault(false) }
    }
    /** Bootstrap a known visible conversation on a DTLS peer already owned by Talk. */
    fun receiveDirect(owner: String, backend: SaitoBackend, endpoint: String, contact: String, envelope: String): Boolean? =
        sessions.values.firstOrNull { it.owner == owner && it.backend == backend && it.endpoint == endpoint &&
            it.contact == contact && it.ownsDelivery }?.receiveDirect(envelope)
    fun cancel(token: String) { sessions.values.forEach { it.cancel(token) } }
    fun stopAll() { sessions.values.forEach { it.interrupt() } }
    @Synchronized fun hasLiveWork() = sessions.values.any { session ->
        session.foregroundOwned || session.transfers.value.any { it.phase.active } || session.preparation.value?.phase == PreparationPhase.PREPARING
    }
}

internal class FileSession(private val context: Context, private val wallet: SaitoWallet,
    val backend: SaitoBackend, val endpoint: String, val contact: String, private val changed: () -> Unit) {
    val owner get() = wallet.publicKey
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<suspend () -> Unit>(32)
    private val state = MutableStateFlow<List<FileTransfer>>(emptyList())
    val transfers = state.asStateFlow()
    private val preparationState = MutableStateFlow<FilePreparation?>(null)
    val preparation = preparationState.asStateFlow()
    private class Preparing(val token: String) {
        val cancellation = android.os.CancellationSignal()
        @Volatile var input: InputStream? = null
    }
    private var preparingWork: Preparing? = null
    private var preparationJob: Job? = null
    private val store = FileTransferStore(context, owner, backend, contact)
    private var current: FileTransfer? = null
    @Volatile private var rtc: SaitoFileChannel? = null
    private var peer: SaitoPeer? = null
    private var open = false
    private var source: InputStream? = null
    private var spool: FileTransferStore.Spool? = null
    private var receiver: Fileshare.Receiver? = null
    @Volatile private var incomingFrames: FileTransferStore.FrameInbox? = null
    private var sendingIndex = 0L
    private var sendingBytes = 0
    @Volatile private var activityAt = System.currentTimeMillis()
    private var lastPublication = 0L
    private var checkedRevision = -1L
    private var checkedAt = 0L
    @Volatile private var chatWallet = wallet
    @Volatile private var serviceStarted = false
    val foregroundOwned get() = serviceStarted
    @Volatile private var observers = 0
    val ownsDelivery get() = observers > 0 || serviceStarted
    private var loaded = false
    private var watchdog: Job? = null
    @Volatile private var operation: Job? = null
    private var readJob: Job? = null
    @Volatile private var channelGeneration = 0L
    private val seenSignals = LinkedHashSet<String>()
    @Volatile private var overflow = false
    val ready = MutableStateFlow(false)
    val error = MutableStateFlow(false)

    init {
        scope.launch {
            for (event in events) {
                val task = scope.launch(start = CoroutineStart.LAZY) {
                    try { event() }
                    catch (_: CancellationException) { /* An explicit Cancel preempts connection/request waits. */ }
                    catch (failure: Exception) {
                        if (nl.davidengelen.redsquare.BuildConfig.DEBUG && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
                            android.util.Log.w("File104Probe", "File event failed", failure)
                        error.value = true
                        try {
                            if (preparationState.value?.phase == PreparationPhase.PREPARING) finishPreparation(PreparationPhase.FAILED)
                            else end(FilePhase.FAILED)
                        } catch (_: Exception) { cleanup() }
                    }
                }
                operation = task; task.start(); task.join(); operation = null
                if (overflow) {
                    overflow = false; error.value = true
                    if (preparationState.value?.phase == PreparationPhase.PREPARING) finishPreparation(PreparationPhase.FAILED)
                    else end(FilePhase.FAILED)
                }
            }
        }
        post {
            val retained = store.load().map { value ->
                store.discardInbox(value.token)
                if (value.phase.active || value.phase == FilePhase.OFFERED) {
                    store.discard(value.token)
                    value.copy(phase = FilePhase.INTERRUPTED).also(store::save)
                } else value
            }
            state.value = retained
            store.loadPreparation()?.let { previous ->
                store.discard(previous.token)
                val restored = if (previous.phase == PreparationPhase.PREPARING) previous.copy(phase = PreparationPhase.INTERRUPTED) else previous
                preparationState.value = restored; store.savePreparation(restored)
            }
            loaded = true; ready.value = true; changed()
        }
    }

    private fun startWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            while (isActive) {
                delay(1000)
                post {
                    val pending = preparationState.value
                    if (pending?.phase == PreparationPhase.PREPARING) {
                        assertSelected()
                        if (!FileshareNotifications.available(context) || System.currentTimeMillis() - activityAt > 60_000)
                            finishPreparation(PreparationPhase.INTERRUPTED)
                    }
                    val value = current
                    if (value != null && (value.phase.active || value.phase == FilePhase.OFFERED)) {
                        assertSelected()
                        if (value.phase.active && !FileshareNotifications.available(context)) end(FilePhase.INTERRUPTED)
                        val bound = if (value.phase in setOf(FilePhase.WAITING, FilePhase.OFFERED)) 120_000 else 60_000
                        if (System.currentTimeMillis() - activityAt > bound) end(FilePhase.INTERRUPTED)
                    } else if (rtc != null && System.currentTimeMillis() - activityAt > 30_000) cleanup()
                    if (observers == 0 && current?.phase == FilePhase.OFFERED) end(FilePhase.DECLINED)
                }
            }
        }
    }

    fun attach() { post { observers++ } }
    fun detach() { post { observers = maxOf(0, observers - 1) } }
    @Synchronized fun send(uri: Uri) {
        if (!ready.value || state.value.any { it.phase.active || it.phase == FilePhase.OFFERED } ||
            preparationState.value?.phase == PreparationPhase.PREPARING) return
        if (!FileshareNotifications.available(context)) { error.value = true; return }
        require(uri.scheme == "content" && uri.toString().length <= 8192)
        val pending = FilePreparation(UUID.randomUUID().toString(), owner, backend, contact, endpoint, uri.toString())
        preparationState.value = pending; error.value = false; activityAt = System.currentTimeMillis(); changed()
        try { context.startForegroundService(Intent(context, FileTransferService::class.java)) }
        catch (_: Exception) { preparationState.value = pending.copy(phase = PreparationPhase.FAILED); error.value = true; changed(); return }
        post { startPreparation(uri, pending) }
    }
    fun retry(token: String) { post {
        val previous = state.value.singleOrNull { it.token == token } ?: return@post
        if (previous.incoming || previous.phase.active || previous.source == null) return@post
        send(Uri.parse(previous.source))
    } }

    /** Called by the existing Chat receiver, or the foreground connection, never by an arbitrary URI. */
    @Synchronized fun receive(bytes: ByteArray): Boolean {
        val message = Fileshare.network(bytes, contact, owner) ?: return false
        if (message.sender == owner) return true // A Relay may echo the signed change/self output.
        if (message.id in seenSignals) return true
        check(post { handle(message) })
        message.id?.let { seenSignals.add(it) }
        if (seenSignals.size > 256) seenSignals.remove(seenSignals.first())
        return true
    }

    /** Only initial controls bootstrap here. Active file frames keep their encrypted streaming inbox. */
    fun receiveDirect(envelope: String): Boolean? {
        if (rtc != null || !ownsDelivery || !Fileshare.ownsDirectFrame(envelope)) return null
        val message = Fileshare.direct(envelope, contact, owner)
        if (message.body.get("module")?.asString != "Relay") return null
        return post {
            assertSelected()
            if (rtc == null) connect(initiator = false)
            handle(message)
        }
    }

    fun accept(token: String) {
        if (state.value.none { it.token == token && it.phase == FilePhase.OFFERED }) return
        if (!FileshareNotifications.available(context)) { error.value = true; return }
        // The platform foreground-service launch stays directly attached to the visible user action.
        try { context.startForegroundService(Intent(context, FileTransferService::class.java)) }
        catch (_: Exception) { error.value = true; return }
        post {
            val value = current ?: return@post
            if (value.token != token || value.phase != FilePhase.OFFERED || !open) return@post
            assertSelected(); nl.davidengelen.redsquare.policy.PublishingPolicies.requireAllowed(context)
            serviceStarted = true
            update(value.copy(phase = FilePhase.RECEIVING))
            spool = store.begin(value); receiver = Fileshare.Receiver(value.offer)
            incomingFrames = store.inbox(requireNotNull(current))
            sendControl("grant file permission", value.offer.id)
        }
    }

    fun cancel(token: String) {
        if (state.value.any { it.token == token && (it.phase.active || it.phase == FilePhase.OFFERED) } ||
            preparationState.value?.let { it.token == token && it.phase == PreparationPhase.PREPARING } == true) operation?.cancel()
        post {
        if (preparationState.value?.let { it.token == token && it.phase == PreparationPhase.PREPARING } == true) {
            finishPreparation(PreparationPhase.CANCELLED); return@post
        }
        val value = current ?: return@post
        if (value.token != token || !(value.phase.active || value.phase == FilePhase.OFFERED)) return@post
        try {
            if (open) sendControl(if (value.phase == FilePhase.OFFERED) "deny file permission" else "stop file transfer", value.offer.id)
        } catch (_: Exception) { /* Local cancellation does not depend on the other peer. */ }
        end(if (value.phase == FilePhase.OFFERED) FilePhase.DECLINED else FilePhase.CANCELLED)
    } }
    fun interrupt() {
        operation?.cancel()
        post {
            if (preparationState.value?.phase == PreparationPhase.PREPARING) finishPreparation(PreparationPhase.INTERRUPTED)
            else if (current?.phase?.active == true) end(FilePhase.INTERRUPTED) else cleanup()
        }
    }

    suspend fun export(token: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            assertSelected()
            val value = state.value.single { it.token == token }
            store.export(value, uri)
            assertSelected()
            val updated = value.copy(phase = FilePhase.SAVED)
            store.save(updated)
            post { publish(updated) }
            true
        } catch (_: Exception) {
            try { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) } catch (_: Exception) { }
            false
        }
    }

    private fun startPreparation(uri: Uri, pending: FilePreparation) {
        assertSelected(); nl.davidengelen.redsquare.policy.PublishingPolicies.requireAllowed(context)
        if (preparationState.value?.token != pending.token || preparationState.value?.phase != PreparationPhase.PREPARING) return
        cleanup(); serviceStarted = true
        store.savePreparation(pending)
        val work = Preparing(pending.token); preparingWork = work
        startWatchdog()
        preparationJob = scope.launch(PREPARATION_IO) {
            var retainedSource: InputStream? = null
            try {
                var name: String? = null; var size: Long? = null
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
                    work.cancellation)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0)
                        if (!cursor.isNull(1)) size = cursor.getLong(1)
                    }
                }
                val cleaned = (name ?: "Shared file").replace('/', '_').replace('\\', '_').filter { it.code >= 32 }.take(255)
                val filename = (if (cleaned.lastOrNull()?.isHighSurrogate() == true) cleaned.dropLast(1) else cleaned).ifBlank { "Shared file" }
                val worker = currentCoroutineContext()
                fun progress(bytes: Long) {
                    worker.ensureActive(); assertSelected()
                    check(preparationState.value?.token == pending.token && preparationState.value?.phase == PreparationPhase.PREPARING)
                    activityAt = System.currentTimeMillis()
                    if (activityAt - lastPublication >= 100) {
                        lastPublication = activityAt
                        preparationState.update { current ->
                            if (current?.token == pending.token && current.phase == PreparationPhase.PREPARING)
                                current.copy(name = filename, bytes = bytes) else current
                        }
                        changed()
                    }
                }
                progress(0)
                val staged = size == null
                val input = requireNotNull(context.contentResolver.openInputStream(uri)); work.input = input
                retainedSource = input
                if (staged) input.use { size = store.prepare(pending.token, it, ::progress) }
                val offer = Fileshare.Offer(UUID.randomUUID().toString().replace("-", "").take(12), filename,
                    requireNotNull(size), context.contentResolver.getType(uri).orEmpty())
                val value = FileTransfer(pending.token, owner, backend, contact, endpoint, offer, false,
                    FilePhase.CONNECTING, source = uri.toString())
                val prepared = if (staged) store.openPrepared(value) else input
                retainedSource = prepared
                worker.ensureActive()
                if (!post {
                    if (preparationState.value?.token != pending.token || preparationState.value?.phase != PreparationPhase.PREPARING) {
                        prepared.close(); store.discard(pending.token); return@post
                    }
                    assertSelected(); check(FileshareNotifications.available(context))
                    source = prepared; update(value)
                    preparationState.value = null; store.savePreparation(null); changed()
                    preparingWork = null; preparationJob = null
                    connect(initiator = true)
                }) throw IllegalStateException("Transfer queue full")
                retainedSource = null // The serialized controller now owns the opened source.
            } catch (_: CancellationException) { retainedSource?.close() }
            catch (_: Exception) {
                retainedSource?.close()
                post { if (preparationState.value?.token == pending.token) finishPreparation(PreparationPhase.FAILED) }
            } finally { work.input = null }
        }
    }

    private fun finishPreparation(phase: PreparationPhase) {
        val pending = preparationState.value ?: return
        if (pending.phase != PreparationPhase.PREPARING) return
        preparationState.update { current ->
            if (current?.token == pending.token && current.phase == PreparationPhase.PREPARING) current.copy(phase = phase) else current
        }
        preparationJob?.cancel(); preparationJob = null
        preparingWork?.let { work ->
            work.cancellation.cancel()
            scope.launch(Dispatchers.IO) { try { work.input?.close() } catch (_: Exception) { } }
        }
        preparingWork = null
        store.discard(pending.token)
        store.savePreparation(pending.copy(phase = phase)); changed()
        if (phase == PreparationPhase.FAILED) error.value = true
        cleanup()
    }

    private fun assertSelected() = synchronized(SaitoLocalState.lock) {
        if (checkedRevision == SaitoLocalState.authorityRevision && System.currentTimeMillis() - checkedAt < 1000) return@synchronized
        val selected = AndroidWalletStore(context).loadExisting()
        check(selected.publicKey == owner && AndroidBackendStore(context).load() == backend)
        check(!AndroidPersonalControls(context, owner).load().hidesIncomingChat(contact))
        chatWallet = selected // Same signer, current imported/updated Chat key metadata.
        checkedRevision = SaitoLocalState.authorityRevision; checkedAt = System.currentTimeMillis()
    }

    private suspend fun connect(initiator: Boolean) {
        if (rtc != null) return
        assertSelected()
        val connection = SaitoPeer(wallet, endpoint, onIncoming = { bytes ->
            try {
                if (!receive(bytes)) {
                    assertSelected()
                    val savedContact = receiveChat(context, chatWallet, backend, bytes, endpoint)
                    FileshareRuntime.chatChanges.tryEmit(Triple(owner, backend, savedContact))
                }
                true
            } catch (_: Exception) { false }
        })
        peer = connection
        require("relay" in connection.services())
        scope.launch {
            try { connection.awaitDisconnection() } catch (_: Exception) { }
            post { if (peer === connection) end(FilePhase.INTERRUPTED) }
        }
        val generation = ++channelGeneration
        rtc = SaitoFileChannel(context, wallet, backend, endpoint, contact, validate = ::assertSelected,
            signal = { transaction -> post {
                if (generation == channelGeneration) {
                    assertSelected()
                    requireNotNull(peer).request(Fileshare.relay(transaction), beforeSend = { assertSelected() })
                }
            } },
            opened = { post {
                if (generation != channelGeneration) return@post
                open = true; activityAt = System.currentTimeMillis()
                current?.takeIf { !it.incoming && it.phase == FilePhase.CONNECTING }?.let { value ->
                    assertSelected()
                    sendChannel(Fileshare.offer(wallet, contact, value.offer, System.currentTimeMillis()))
                    update(value.copy(phase = FilePhase.WAITING))
                }
            } }, message = message@{ text ->
                val inbox = incomingFrames
                if (generation != channelGeneration) return@message
                if (inbox != null) {
                    if (inbox.append(text)) post { drainInbox(inbox, generation) }
                } else post { if (generation == channelGeneration) handle(Fileshare.direct(text, contact, owner)) }
            }, failed = { post { if (generation == channelGeneration && rtc != null) end(FilePhase.INTERRUPTED) } },
            otherData = { envelope ->
                assertSelected()
                nl.davidengelen.redsquare.chat.receiveBoundChat(context, chatWallet, backend, endpoint, contact, envelope)
            }, useTraversal = !nl.davidengelen.redsquare.BuildConfig.DEBUG || endpoint == backend.peerUrl)
        // A lease on an existing Talk peer is authenticated/open before its queued
        // opened callback executes. Initial file permission controls can use it now.
        open = requireNotNull(rtc).connected
        startWatchdog()
        if (initiator) {
            signal("peer-joined", JsonObject())
            requireNotNull(rtc).offer()
        }
    }

    private suspend fun drainInbox(inbox: FileTransferStore.FrameInbox, generation: Long) {
        repeat(4) {
            currentCoroutineContext().ensureActive()
            if (generation != channelGeneration || incomingFrames !== inbox) return
            val frame = inbox.next() ?: return
            handle(Fileshare.direct(frame, contact, owner))
        }
        // Let queued controls and the watchdog run even while the browser keeps sending.
        post { drainInbox(inbox, generation) }
    }

    private suspend fun signal(request: String, data: JsonObject) {
        assertSelected()
        requireNotNull(peer).request(Fileshare.relay(Fileshare.signal(wallet, contact, request, data, System.currentTimeMillis())),
            beforeSend = { assertSelected() })
    }

    private suspend fun handle(message: Fileshare.Message) {
        if (nl.davidengelen.redsquare.BuildConfig.DEBUG && android.os.Build.HARDWARE in listOf("ranchu", "goldfish") &&
            message.request in setOf("peer-joined", "peer-description", "query file permission"))
            android.util.Log.d("File104Probe", "handle:${message.request} open=$open rtc=${rtc?.connected} observers=$observers")
        assertSelected()
        when (message.request) {
            "peer-joined" -> { if (observers > 0 || serviceStarted) connect(initiator = false) }
            "peer-description", "peer-candidate" -> {
                if (rtc == null && (observers > 0 || serviceStarted)) connect(initiator = false)
                rtc?.receive(message.request, message.body, message.id)
            }
            "peer-left" -> end(FilePhase.INTERRUPTED)
            "query file permission" -> {
                if (rtc == null && ownsDelivery) connect(initiator = false)
                check(open)
                val offer = Fileshare.readOffer(message.data)
                if (current?.offer?.id == offer.id) return
                if (observers == 0 || current?.phase?.active == true || current?.phase == FilePhase.OFFERED || preparationState.value?.phase == PreparationPhase.PREPARING) {
                    sendControl("deny file permission", offer.id); return
                }
                // A new offer can arrive during the completed transfer's ACK grace period.
                // Its token replaces current, so the old delayed cleanup will no longer run.
                val previousInbox = incomingFrames; incomingFrames = null; previousInbox?.close()
                receiver = null; serviceStarted = false
                update(FileTransfer(UUID.randomUUID().toString(), owner, backend, contact, endpoint,
                    offer, true, FilePhase.OFFERED))
            }
            "grant file permission" -> {
                val value = current ?: return
                if (value.incoming || value.phase != FilePhase.WAITING || message.data.get("id").asString != value.offer.id) return
                update(value.copy(phase = FilePhase.SENDING)); sendNext()
            }
            "deny file permission", "stop file transfer" -> {
                if (message.data.get("id").asString == current?.offer?.id)
                    end(if (message.request == "deny file permission") FilePhase.DECLINED else FilePhase.CANCELLED)
            }
            "share file" -> {
                val value = current ?: return
                if (!value.incoming || value.phase !in setOf(FilePhase.RECEIVING, FilePhase.COMPLETE)) return
                val chunk = Fileshare.readChunk(message.data)
                val target = requireNotNull(receiver)
                val complete = target.append(chunk) { requireNotNull(spool).append(it) }
                activityAt = System.currentTimeMillis()
                if (complete && value.phase != FilePhase.COMPLETE) {
                    requireNotNull(spool).finish(); spool = null
                    update(value.copy(phase = FilePhase.COMPLETE, bytes = target.received))
                } else publishProgress(value.copy(bytes = target.received))
                sendChannel(Fileshare.receipt(wallet, contact, value.offer.id, chunk.index, System.currentTimeMillis()))
                if (complete) finishLater(value.token)
            }
            "read receipt" -> {
                val value = current ?: return
                if (value.incoming || value.phase != FilePhase.SENDING || message.data.get("id").asString != value.offer.id) return
                val index = Fileshare.number(message.data, "chunk_id")
                if (sendingBytes == 0) return
                if (index < sendingIndex) return
                require(index == sendingIndex)
                activityAt = System.currentTimeMillis()
                val updated = value.copy(bytes = value.bytes + sendingBytes)
                sendingBytes = 0
                if (updated.bytes == updated.offer.size) {
                    update(updated.copy(phase = FilePhase.COMPLETE)); cleanup()
                } else { publishProgress(updated); sendNext() }
            }
        }
    }

    private fun sendControl(request: String, id: String) {
        assertSelected()
        sendChannel(Fileshare.control(wallet, contact, request,
            JsonObject().apply { addProperty("id", id) }, System.currentTimeMillis()))
    }
    private fun sendNext() {
        assertSelected()
        val value = requireNotNull(current)
        val input = requireNotNull(source)
        val generation = channelGeneration
        readJob = scope.launch(Dispatchers.IO) {
            try {
                // A legacy browser can advertise a 64 KiB SCTP message limit. Base64 inside
                // the signed JSON envelope expands twice, so 32 KiB leaves room for metadata.
                val bytes = ByteArray(minOf(32_768L, value.offer.size - value.bytes).toInt())
                var count = 0
                while (count < bytes.size) {
                    ensureActive()
                    val read = input.read(bytes, count, bytes.size - count)
                    check(read > 0); count += read
                }
                if (value.bytes + count == value.offer.size) check(input.read() == -1)
                ensureActive()
                post {
                    if (generation != channelGeneration || current?.token != value.token || current?.phase != FilePhase.SENDING) return@post
                    assertSelected(); sendingIndex++; sendingBytes = count
                    sendChannel(Fileshare.chunk(wallet, contact,
                        Fileshare.Chunk(value.offer, value.bytes, sendingIndex, bytes), System.currentTimeMillis()))
                }
            } catch (_: CancellationException) { }
            catch (_: Exception) {
                post { if (generation == channelGeneration) end(FilePhase.FAILED) }
            }
        }
    }
    private fun update(value: FileTransfer) {
        store.save(value); publish(value); activityAt = System.currentTimeMillis()
    }
    private fun sendChannel(transaction: SaitoTransaction) {
        val frame = transaction.envelope()
        synchronized(SaitoLocalState.lock) {
            assertSelected()
            requireNotNull(rtc).send(frame)
        }
    }
    private fun publish(value: FileTransfer) {
        if (current?.token == value.token || value.phase.active || value.phase == FilePhase.OFFERED) current = value
        state.value = (listOf(value) + state.value.filter { it.token != value.token }).sortedByDescending { it.createdAt }
        changed()
    }
    private fun publishProgress(value: FileTransfer) {
        current = value
        val now = System.currentTimeMillis()
        if (now - lastPublication >= 100) { lastPublication = now; publish(value) }
    }
    private fun end(phase: FilePhase) {
        val value = current
        if (value != null && (value.phase.active || value.phase == FilePhase.OFFERED)) {
            try { update(value.copy(phase = phase)) } finally { store.discard(value.token) }
        }
        cleanup()
    }
    private fun cleanup() {
        current?.takeIf { !it.incoming }?.let { store.discard(it.token) }
        channelGeneration++; watchdog?.cancel(); watchdog = null
        readJob?.cancel(); readJob = null
        val oldInbox = incomingFrames; incomingFrames = null; oldInbox?.close()
        open = false; serviceStarted = false
        val oldRtc = rtc; rtc = null
        val oldPeer = peer; peer = null
        source?.close(); source = null; spool?.close(); spool = null; receiver = null
        sendingIndex = 0; sendingBytes = 0
        oldRtc?.close(); oldPeer?.close()
    }
    private fun finishLater(token: String) { scope.launch {
        delay(1500)
        post { if (current?.token == token && current?.phase == FilePhase.COMPLETE) cleanup() }
    } }
    companion object { private val PREPARATION_IO = Dispatchers.IO.limitedParallelism(2) }
    private fun post(event: suspend () -> Unit): Boolean = events.trySend(event).isSuccess.also {
        if (!it) overflow = true
    }
}
