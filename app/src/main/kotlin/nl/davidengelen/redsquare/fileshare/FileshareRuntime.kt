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
    val chatChanges = MutableSharedFlow<Pair<String, SaitoBackend>>(extraBufferCapacity = 16)
    fun session(context: Context, wallet: SaitoWallet, backend: SaitoBackend, endpoint: String, contact: String): FileSession {
        require(SaitoWallet.isPublicKey(contact) && contact != wallet.publicKey)
        return sessions.computeIfAbsent("${wallet.publicKey}:${backend.id}:$endpoint:$contact") {
            FileSession(context.applicationContext, wallet, backend, endpoint, contact, ::publish)
        }
    }
    @Synchronized private fun publish() {
        changes.value = sessions.values.flatMap { it.transfers.value }.sortedByDescending { it.createdAt }
    }
    fun cancel(token: String) { sessions.values.forEach { it.cancel(token) } }
    fun stopAll() { sessions.values.forEach { it.interrupt() } }
}

internal class FileSession(private val context: Context, private val wallet: SaitoWallet,
    val backend: SaitoBackend, val endpoint: String, val contact: String, private val changed: () -> Unit) {
    val owner get() = wallet.publicKey
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<suspend () -> Unit>(32)
    private val state = MutableStateFlow<List<FileTransfer>>(emptyList())
    val transfers = state.asStateFlow()
    private val store = FileTransferStore(context, owner, backend, contact)
    private var current: FileTransfer? = null
    private var rtc: SaitoFileChannel? = null
    private var peer: SaitoPeer? = null
    private var open = false
    private var source: InputStream? = null
    private var spool: FileTransferStore.Spool? = null
    private var receiver: Fileshare.Receiver? = null
    private var sendingIndex = 0L
    private var sendingBytes = 0
    private var activityAt = System.currentTimeMillis()
    private var lastPublication = 0L
    private var checkedRevision = -1L
    private var checkedAt = 0L
    private var serviceStarted = false
    private var observers = 0
    private var loaded = false
    private var watchdog: Job? = null
    @Volatile private var operation: Job? = null
    private var readJob: Job? = null
    private var channelGeneration = 0L
    private val seenSignals = LinkedHashSet<String>()
    @Volatile private var overflow = false
    val ready = MutableStateFlow(false)
    val error = MutableStateFlow(false)

    init {
        scope.launch {
            for (event in events) {
                val task = scope.launch {
                    try { event() }
                    catch (_: CancellationException) { /* An explicit Cancel preempts connection/request waits. */ }
                    catch (_: Exception) {
                        error.value = true
                        try { end(FilePhase.FAILED) } catch (_: Exception) { cleanup() }
                    }
                }
                operation = task; task.join(); operation = null
                if (overflow) { overflow = false; error.value = true; end(FilePhase.FAILED) }
            }
        }
        post {
            val retained = store.load().map { value ->
                if (value.phase.active || value.phase == FilePhase.OFFERED) {
                    store.discard(value.token)
                    value.copy(phase = FilePhase.INTERRUPTED).also(store::save)
                } else value
            }
            state.value = retained; loaded = true; ready.value = true; changed()
        }
    }

    private fun startWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            while (isActive) {
                delay(1000)
                post {
                    val value = current
                    if (value != null && (value.phase.active || value.phase == FilePhase.OFFERED)) {
                        assertSelected()
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
    fun send(uri: Uri) { post { start(uri) } }
    fun retry(token: String) { post {
        val previous = state.value.single { it.token == token }
        require(!previous.incoming && !previous.phase.active && previous.source != null)
        start(Uri.parse(previous.source))
    } }

    /** Called by the existing Chat receiver, or the foreground connection, never by an arbitrary URI. */
    @Synchronized fun receive(bytes: ByteArray): Boolean {
        val message = Fileshare.network(bytes, contact, owner) ?: return false
        if (message.id in seenSignals) return true
        check(post { handle(message) })
        message.id?.let { seenSignals.add(it) }
        if (seenSignals.size > 256) seenSignals.remove(seenSignals.first())
        return true
    }

    fun accept(token: String) {
        // The platform foreground-service launch stays directly attached to the visible user action.
        try { context.startForegroundService(Intent(context, FileTransferService::class.java)) }
        catch (_: Exception) { error.value = true; return }
        post {
            val value = requireNotNull(current)
            require(value.token == token && value.phase == FilePhase.OFFERED && open)
            assertSelected(); nl.davidengelen.redsquare.policy.PublishingPolicies.requireAllowed(context)
            serviceStarted = true
            update(value.copy(phase = FilePhase.RECEIVING))
            spool = store.begin(value); receiver = Fileshare.Receiver(value.offer)
            sendControl("grant file permission", value.offer.id)
        }
    }

    fun cancel(token: String) {
        if (state.value.any { it.token == token && (it.phase.active || it.phase == FilePhase.OFFERED) }) operation?.cancel()
        post {
        val value = current ?: return@post
        if (value.token != token || !(value.phase.active || value.phase == FilePhase.OFFERED)) return@post
        try {
            if (open) sendControl(if (value.phase == FilePhase.OFFERED) "deny file permission" else "stop file transfer", value.offer.id)
        } catch (_: Exception) { /* Local cancellation does not depend on the other peer. */ }
        end(if (value.phase == FilePhase.OFFERED) FilePhase.DECLINED else FilePhase.CANCELLED)
    } }
    fun interrupt() {
        operation?.cancel()
        post { if (current?.phase?.active == true) end(FilePhase.INTERRUPTED) else cleanup() }
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

    private suspend fun start(uri: Uri) {
        check(loaded && current?.phase?.active != true && current?.phase != FilePhase.OFFERED)
        assertSelected(); nl.davidengelen.redsquare.policy.PublishingPolicies.requireAllowed(context)
        error.value = false
        var name: String? = null; var size: Long? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0)
                if (!cursor.isNull(1)) size = cursor.getLong(1)
            }
        }
        val filename = requireNotNull(name).replace('/', '_').replace('\\', '_').filter { it.code >= 32 }.take(255)
        val offer = Fileshare.Offer(UUID.randomUUID().toString().replace("-", "").take(12), filename,
            requireNotNull(size), context.contentResolver.getType(uri).orEmpty())
        cleanup()
        val value = FileTransfer(UUID.randomUUID().toString(), owner, backend, contact, endpoint, offer, false,
            FilePhase.CONNECTING, source = uri.toString())
        update(value)
        withContext(Dispatchers.Main.immediate) {
            context.startForegroundService(Intent(context, FileTransferService::class.java))
        }
        serviceStarted = true
        source = requireNotNull(context.contentResolver.openInputStream(uri))
        connect(initiator = true)
    }

    private fun assertSelected() = synchronized(SaitoLocalState.lock) {
        if (checkedRevision == SaitoLocalState.authorityRevision && System.currentTimeMillis() - checkedAt < 1000) return@synchronized
        check(AndroidWalletStore(context).loadExisting().publicKey == owner && AndroidBackendStore(context).load() == backend)
        check(!AndroidPersonalControls(context, owner).load().hidesIncomingChat(contact))
        checkedRevision = SaitoLocalState.authorityRevision; checkedAt = System.currentTimeMillis()
    }

    private suspend fun connect(initiator: Boolean) {
        if (rtc != null) return
        assertSelected()
        val connection = SaitoPeer(wallet, endpoint, onIncoming = { bytes ->
            try {
                if (!receive(bytes)) {
                    receiveChat(context, wallet, backend, bytes, endpoint)
                    FileshareRuntime.chatChanges.tryEmit(owner to backend)
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
        rtc = SaitoFileChannel(context, polite = owner < contact,
            signal = { request, data -> post { if (generation == channelGeneration) signal(request, data) } },
            opened = { post {
                if (generation != channelGeneration) return@post
                open = true; activityAt = System.currentTimeMillis()
                current?.takeIf { !it.incoming && it.phase == FilePhase.CONNECTING }?.let { value ->
                    assertSelected()
                    sendChannel(Fileshare.offer(wallet, contact, value.offer, System.currentTimeMillis()))
                    update(value.copy(phase = FilePhase.WAITING))
                }
            } }, message = { text ->
                if (!post { if (generation == channelGeneration) handle(Fileshare.direct(text, contact, owner)) }) interrupt()
            }, failed = { post { if (generation == channelGeneration && rtc != null) end(FilePhase.INTERRUPTED) } },
            useTraversal = !nl.davidengelen.redsquare.BuildConfig.DEBUG || endpoint == backend.peerUrl)
        startWatchdog()
        if (initiator) {
            signal("peer-joined", JsonObject())
            requireNotNull(rtc).offer()
        }
    }

    private suspend fun signal(request: String, data: JsonObject) {
        assertSelected()
        requireNotNull(peer).request(Fileshare.relay(Fileshare.signal(wallet, contact, request, data, System.currentTimeMillis())),
            beforeSend = { assertSelected() })
    }

    private suspend fun handle(message: Fileshare.Message) {
        assertSelected()
        when (message.request) {
            "peer-joined" -> { if (observers > 0 || serviceStarted) connect(initiator = false) }
            "peer-description", "peer-candidate" -> {
                if (rtc == null && (observers > 0 || serviceStarted)) connect(initiator = false)
                rtc?.receive(message.request, message.body)
            }
            "peer-left" -> end(FilePhase.INTERRUPTED)
            "query file permission" -> {
                check(open)
                val offer = Fileshare.readOffer(message.data)
                if (current?.offer?.id == offer.id) return
                if (observers == 0 || current?.phase?.active == true || current?.phase == FilePhase.OFFERED) {
                    sendControl("deny file permission", offer.id); return
                }
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
                val bytes = ByteArray(minOf(Fileshare.CHUNK_BYTES.toLong(), value.offer.size - value.bytes).toInt())
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
            catch (_: Exception) { post { if (generation == channelGeneration) end(FilePhase.FAILED) } }
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
        channelGeneration++; watchdog?.cancel(); watchdog = null
        readJob?.cancel(); readJob = null
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
    private fun post(event: suspend () -> Unit): Boolean = events.trySend(event).isSuccess.also { if (!it) overflow = true }
}
