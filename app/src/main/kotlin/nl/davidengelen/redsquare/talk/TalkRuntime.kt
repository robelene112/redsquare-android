package nl.davidengelen.redsquare.talk

import android.content.Context
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.chat.receiveChat
import nl.davidengelen.redsquare.chat.receiveChatEnvelope
import nl.davidengelen.redsquare.fileshare.FileshareRuntime
import nl.davidengelen.redsquare.moderation.AndroidPersonalControls
import nl.davidengelen.redsquare.saito.*
import org.webrtc.MediaStreamTrack
import org.webrtc.VideoTrack
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal enum class CallPhase {
    VERIFYING, INCOMING, DIALING, JOINING, ACTIVE, ENDED, DECLINED, NO_ANSWER, INTERRUPTED, UNAVAILABLE;
    val live get() = this in setOf(VERIFYING, INCOMING, DIALING, JOINING, ACTIVE)
    val started get() = this in setOf(DIALING, JOINING, ACTIVE)
}
internal data class CallParticipant(val key: String, val connected: Boolean = false,
    val microphone: Boolean = true, val camera: Boolean = true, val video: VideoTrack? = null)
internal data class TalkCall(val token: String, val owner: String, val savedBackend: SaitoBackend,
    val link: TalkLink, val direct: String?, val incoming: Boolean, val phase: CallPhase,
    val participants: List<CallParticipant> = emptyList(), val microphone: Boolean = true,
    val camera: Boolean = false, val cameraPaused: Boolean = false, val localVideo: VideoTrack? = null,
    val since: Long = 0)

/** Process ownership is independent of a screen. Only a user-started call holds background ownership. */
internal object TalkRuntime {
    private val accounts = ConcurrentHashMap<String, TalkAccount>()
    val call = MutableStateFlow<TalkCall?>(null)
    @Volatile var visible = false
        private set
    fun account(context: Context, wallet: SaitoWallet, backend: SaitoBackend, endpoint: String): TalkAccount =
        accounts.computeIfAbsent("${wallet.publicKey}:${backend.id}:$endpoint") {
            TalkAccount(context.applicationContext, wallet, backend, endpoint)
        }
    fun end(token: String) { accounts.values.forEach { it.end(token) } }
    fun visibility(value: Boolean) { visible = value; accounts.values.forEach { it.visibility() } }
    fun interrupt() { accounts.values.forEach { it.interrupt() } }
    fun controls(token: String, microphone: Boolean? = null, camera: Boolean? = null) {
        accounts.values.forEach { it.controls(token, microphone, camera) }
    }
    fun switchCamera(token: String) { accounts.values.forEach { it.switchCamera(token) } }
    internal fun remove(account: TalkAccount) { accounts.entries.removeIf { it.value === account } }
}

/** One serial actor owns invitations, membership and media for the selected wallet/node. */
internal class TalkAccount(private val context: Context, val wallet: SaitoWallet,
    val backend: SaitoBackend, val endpoint: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<suspend () -> Unit>(128)
    private val sockets = ConcurrentHashMap<String, SaitoPeer>()
    private val hooks = mutableListOf<Closeable>()
    private val leases = mutableMapOf<String, RtcLease>()
    private val seen = LinkedHashSet<String>()
    private val retired = LinkedHashSet<String>()
    private var observers = 0
    @Volatile private var closed = false
    @Volatile private var current: TalkCall? = null
    private var capture: TalkCapture? = null
    private var accepted = false
    private var connectingMedia = false
    private var changedAt = System.currentTimeMillis()
    private var latestWallet = wallet
    private val routes = if (BuildConfig.DEBUG && endpoint != backend.peerUrl) listOf(endpoint)
        else listOf(endpoint, SaitoBackend.OFFICIAL.peerUrl).distinct()
    val online = MutableStateFlow(false)
    val failed = MutableStateFlow(false)

    init {
        scope.launch {
            for (operation in events) {
                try { withTimeout(20_000) { operation() } }
                catch (_: CancellationException) { if (!closed) finish(CallPhase.INTERRUPTED) }
                catch (_: Exception) { failed.value = true; finish(CallPhase.UNAVAILABLE) }
            }
        }
        scope.launch {
            while (isActive) {
                delay(1000)
                post {
                    if (current?.phase?.live == true) {
                        guard()
                        val elapsed = System.currentTimeMillis() - changedAt
                        when {
                            current?.phase in setOf(CallPhase.VERIFYING, CallPhase.INCOMING, CallPhase.DIALING) && elapsed > 45_000 -> finish(CallPhase.NO_ANSWER)
                            current?.phase == CallPhase.JOINING && elapsed > 40_000 && leases.isNotEmpty() -> finish(CallPhase.UNAVAILABLE)
                            accepted && !TalkCapture.permitted(context, camera = false) -> finish(CallPhase.INTERRUPTED)
                        }
                    }
                }
            }
        }
    }

    fun attach() { post { observers++; connect() } }
    fun detach() { post { observers = (observers - 1).coerceAtLeast(0); closeIfIdle() } }
    fun visibility() { post {
        capture?.visible(TalkRuntime.visible)
        current?.takeIf { it.phase.started }?.let { publish(it.copy(cameraPaused = it.camera && !TalkRuntime.visible)) }
        if (!TalkRuntime.visible && current?.phase?.started != true) {
            if (current?.phase?.live == true) finish(CallPhase.INTERRUPTED)
            closeNetwork()
        } else if (TalkRuntime.visible || current?.phase?.started == true) connect()
    } }

    private fun guard(peer: String? = null) = synchronized(SaitoLocalState.lock) {
        val selected = AndroidWalletStore(context).loadExisting()
        check(selected.publicKey == wallet.publicKey && AndroidBackendStore(context).load() == backend)
        val policy = AndroidPersonalControls(context, wallet.publicKey).load()
        check(peer == null || !policy.hidesIncomingChat(peer))
        current?.participants?.forEach { check(!policy.hidesIncomingChat(it.key)) }
        latestWallet = selected
    }

    private suspend fun connect() {
        if (closed || (!TalkRuntime.visible && current?.phase?.started != true)) return
        guard()
        for (route in routes) {
            if (sockets.containsKey(route)) continue
            val hook = RealtimeIncoming.register(wallet.publicKey, route) { bytes -> receive(bytes, null) }
            val direct = RealtimeIncoming.registerDirect(wallet.publicKey, route) { peer, envelope -> receiveEnvelope(envelope, peer) }
            hooks.add(hook); hooks.add(direct)
            val socket = SaitoPeer(wallet, route, onIncoming = { bytes ->
                try {
                    guard()
                    val peer = receiveChat(context, latestWallet, backend, bytes, endpoint)
                    FileshareRuntime.chatChanges.tryEmit(Triple(wallet.publicKey, backend, peer)); true
                } catch (_: Exception) { false }
            })
            sockets[route] = socket
            scope.launch {
                try {
                    check("relay" in socket.services()); online.value = true
                    socket.awaitDisconnection()
                } catch (_: Exception) { }
                post {
                    if (sockets.remove(route, socket)) {
                        socket.close(); online.value = sockets.isNotEmpty()
                        if (!online.value && current?.phase?.live == true) finish(CallPhase.INTERRUPTED)
                    }
                }
            }
        }
    }

    private fun receive(bytes: ByteArray, boundPeer: String?): Boolean? =
        receiveMessage(TalkProtocol.network(bytes, wallet.publicKey), boundPeer)
    private fun receiveEnvelope(envelope: String, boundPeer: String): Boolean? =
        receiveMessage(TalkProtocol.read(envelope, wallet.publicKey, boundPeer), boundPeer)
    private fun receiveMessage(message: TalkMessage?, boundPeer: String?): Boolean? {
        message ?: return null
        if (message.sender == wallet.publicKey) return true
        // The shared peer's own hook owns SDP/ICE. A pending invitation establishes its lease first.
        if (message.module == "Stun" && message.request in setOf("peer-description", "peer-candidate", "peer-joined")) return null
        guard(message.sender)
        val now = System.currentTimeMillis()
        if (message.createdAt < now - 120_000 || message.createdAt > now + 30_000) return false
        return post {
            guard(message.sender)
            val id = message.signature ?: "${message.sender}:${message.request}:${message.roomId}:${message.createdAt}"
            if (!seen.add(id)) return@post
            if (seen.size > 512) seen.remove(seen.first())
            handle(message, boundPeer)
        }
    }

    private suspend fun handle(message: TalkMessage, boundPeer: String?) {
        val request = message.request
        val call = current
        if (request == TalkDial.REQUEST.wire) {
            val room = requireNotNull(message.room)
            if (room.id in retired) return
            if (TalkRuntime.call.value?.phase?.live == true || call?.phase?.live == true || !TalkRuntime.visible) {
                if (message.identityVerified) send(TalkProtocol.invite(wallet, message.sender, room, TalkDial.REJECT, now()))
                return
            }
            val pending = TalkCall(UUID.randomUUID().toString(), wallet.publicKey, backend, TalkLink(backend, room),
                message.sender, true, if (message.identityVerified) CallPhase.INCOMING else CallPhase.VERIFYING,
                listOf(CallParticipant(message.sender, camera = room.mode == TalkMode.VIDEO)))
            accepted = false; publish(pending); changedAt = now()
            // Legacy unsigned Relay claims must prove a signed SDP and DTLS connection before ringing.
            if (!message.identityVerified) acquire(message.sender, media = false, offer = true)
            else send(TalkProtocol.invite(wallet, message.sender, room, TalkDial.PING, now()))
            return
        }
        if (call == null || !call.phase.live || message.roomId != call.link.room.id) return
        val peer = message.sender
        val member = peer in call.participants.map { it.key }
        val authenticated = message.identityVerified || boundPeer == peer || leases[peer]?.connected == true
        if (message.module == "Relay") {
            if (peer != call.direct || !authenticated) return
            when (request) {
                TalkDial.CANCEL.wire -> finish(CallPhase.ENDED)
                TalkDial.REJECT.wire -> finish(CallPhase.DECLINED)
                TalkDial.ACCEPT.wire -> if (!call.incoming && call.phase == CallPhase.DIALING) {
                    accepted = true; publish(call.copy(phase = CallPhase.JOINING)); changedAt = now(); startMedia()
                }
            }
            return
        }
        if (!accepted || !authenticated) return
        when (request) {
            "call-list-request" -> {
                // Possession of a deliberate public room invitation permits requesting entry.
                // Every admitted participant still proves its own signed SDP/DTLS identity.
                if (call.direct != null && !member) return
                send(TalkProtocol.participants(wallet, call.link.room, peer,
                    (call.participants.map { it.key } + wallet.publicKey).distinct(), now()))
            }
            "call-list-response" -> {
                if (call.direct != null && peer != call.direct) return
                if (call.direct == null && peer != call.link.room.host && !member && peer !in call.link.room.peers) return
                message.body.getAsJsonArray("call_list").map { it.asString }.filter { it != wallet.publicKey }.forEach {
                    addParticipant(it); acquire(it, media = true, offer = true)
                }
            }
            "peer-joined" -> {
                if (call.direct != null && peer != call.direct) return
                addParticipant(peer); acquire(peer, media = true, offer = false)
            }
            "peer-left" -> if (member) {
                leases.remove(peer)?.close()
                publish(requireNotNull(current).copy(participants = requireNotNull(current).participants.filter { it.key != peer }))
                if (call.direct != null) finish(CallPhase.ENDED)
            }
            "peer-kicked" -> if (peer == call.link.room.host) finish(CallPhase.ENDED)
            "peer-kick-broadcast" -> if (peer == call.link.room.host) {
                val kicked = message.body.getAsJsonObject("data")?.get("kicked_peer")?.asString
                    ?: message.body.get("kicked_peer")?.asString
                if (kicked == wallet.publicKey) finish(CallPhase.ENDED)
                else if (kicked != null) {
                    leases.remove(kicked)?.close()
                    publish(requireNotNull(current).copy(participants = requireNotNull(current).participants.filter { it.key != kicked }))
                }
            }
            "toggle-audio", "toggle-video" -> if (member) {
                val data = message.body.getAsJsonObject("data")
                require(data.get("public_key").asString == peer && data.get("enabled").asJsonPrimitive.isBoolean)
                val enabled = data.get("enabled").asBoolean
                publish(requireNotNull(current).copy(participants = requireNotNull(current).participants.map {
                    if (it.key != peer) it else if (request == "toggle-audio") it.copy(microphone = enabled) else it.copy(camera = enabled)
                }))
            }
        }
    }

    private fun addParticipant(peer: String) {
        guard(peer)
        val call = requireNotNull(current)
        if (call.participants.any { it.key == peer }) return
        check(call.participants.size < TalkRoom.MAX_PEERS - 1)
        publish(call.copy(participants = call.participants + CallParticipant(peer, camera = call.link.room.mode == TalkMode.VIDEO)))
    }

    private suspend fun acquire(peer: String, media: Boolean, offer: Boolean) {
        guard(peer)
        val token = requireNotNull(current).token
        val previous = leases[peer]
        // A verified invitation probe becomes a media lease only after explicit acceptance.
        if (previous != null && (!media || previous.callbacks.media)) return
        val route = routes.firstOrNull { sockets.containsKey(it) } ?: error("Call network unavailable")
        val lease = SharedRtcPeers.acquire(context, wallet, backend.id, peer, route,
            !BuildConfig.DEBUG || endpoint == backend.peerUrl, RtcCallbacks(validate = {
                guard(peer); check(current?.token == token && current?.phase?.live == true)
            }, signal = { transaction -> check(post { if (current?.token == token) send(transaction) }) },
                opened = { post {
                    if (current?.token != token) return@post
                    val call = requireNotNull(current)
                    if (call.phase == CallPhase.VERIFYING && call.direct == peer) {
                        publish(call.copy(phase = CallPhase.INCOMING)); changedAt = now()
                        send(TalkProtocol.invite(wallet, peer, call.link.room, TalkDial.PING, now()))
                    } else if (accepted) publish(call.copy(phase = CallPhase.ACTIVE, since = call.since.takeIf { it > 0 } ?: now(),
                        participants = call.participants.map { if (it.key == peer) it.copy(connected = true) else it }))
                } }, failed = { post {
                    if (current?.token == token && current?.phase?.live == true) {
                        leases.remove(peer)?.close()
                        if (current?.direct != null) finish(CallPhase.INTERRUPTED)
                        else publish(requireNotNull(current).copy(participants = requireNotNull(current).participants.filter { it.key != peer }))
                    }
                } }, otherData = { envelope ->
                    guard(peer)
                    val signed = SaitoEnvelope.decodePayload(envelope)
                    check(signed.author == peer && wallet.publicKey in signed.recipients)
                    val saved = receiveChatEnvelope(context, latestWallet, backend, envelope, endpoint)
                    FileshareRuntime.chatChanges.tryEmit(Triple(wallet.publicKey, backend, saved)); true
                }, track = { track -> if (track is VideoTrack) post {
                    if (current?.token == token && accepted) publish(requireNotNull(current).copy(participants =
                        requireNotNull(current).participants.map { if (it.key == peer) it.copy(video = track) else it }))
                } }, media = media))
        leases[peer] = lease
        previous?.close()
        if (media) {
            send(TalkProtocol.event(wallet, requireNotNull(current).link.room, "peer-joined", listOf(peer), now()))
            capture?.let { lease.media(it.audio, it.video, requireNotNull(current).link.room.id) }
        }
        if (offer) {
            send(Stun.signal(wallet, peer, "peer-joined", JsonObject(), now()))
            lease.offer()
        }
    }

    /** Called only after the visible permission/notification gate, with a user-confirmed prejoin. */
    suspend fun start(link: TalkLink, direct: String?, camera: Boolean, microphone: Boolean): Boolean {
        val result = CompletableDeferred<Boolean>()
        if (!post {
            guard(direct)
            if (TalkRuntime.call.value?.phase?.live == true || !TalkRuntime.visible || !TalkCapture.permitted(context, camera)) {
                result.complete(false); return@post
            }
            val call = TalkCall(UUID.randomUUID().toString(), wallet.publicKey, backend, link, direct, false,
                if (direct == null) CallPhase.JOINING else CallPhase.DIALING,
                direct?.let { listOf(CallParticipant(it, camera = link.room.mode == TalkMode.VIDEO)) }.orEmpty(), microphone, camera)
            publish(call); changedAt = now(); accepted = direct == null
            try {
                startService(call); connect()
                if (direct != null) send(TalkProtocol.invite(wallet, direct, link.room, TalkDial.REQUEST, now()))
                else startMedia()
                result.complete(true)
            } catch (failure: Exception) { finish(CallPhase.UNAVAILABLE); result.complete(false) }
        }) return false
        return withTimeoutOrNull(25_000) { result.await() } ?: false
    }

    suspend fun accept(token: String, camera: Boolean): Boolean {
        val result = CompletableDeferred<Boolean>()
        if (!post {
            val call = current
            if (call?.token != token || call.phase != CallPhase.INCOMING || !TalkRuntime.visible || !TalkCapture.permitted(context, camera)) {
                result.complete(false); return@post
            }
            try {
                guard(call.direct); accepted = true
                publish(call.copy(phase = CallPhase.JOINING, camera = camera)); changedAt = now()
                startService(requireNotNull(current))
                send(TalkProtocol.invite(wallet, requireNotNull(call.direct), call.link.room, TalkDial.ACCEPT, now()))
                startMedia(); result.complete(true)
            } catch (_: Exception) { finish(CallPhase.UNAVAILABLE); result.complete(false) }
        }) return false
        return withTimeoutOrNull(25_000) { result.await() } ?: false
    }

    private suspend fun startService(call: TalkCall) {
        TalkService.prepare(call.token)
        withContext(Dispatchers.Main.immediate) {
            check(TalkRuntime.visible)
            context.startForegroundService(android.content.Intent(context, TalkService::class.java).putExtra("token", call.token))
        }
        TalkService.awaitReady(call.token)
    }
    private suspend fun startMedia() {
        if (connectingMedia || !accepted) return
        connectingMedia = true
        try {
            val call = requireNotNull(current); guard()
            capture = TalkCapture(context, call.microphone, call.camera)
            capture?.visible(TalkRuntime.visible)
            publish(call.copy(localVideo = capture?.video, cameraPaused = call.camera && !TalkRuntime.visible))
            if (call.direct != null) acquire(call.direct, media = true, offer = true)
            send(TalkProtocol.enter(wallet, call.link.room, now()))
            call.link.room.peers.filter { it != wallet.publicKey }.forEach { addParticipant(it); acquire(it, media = true, offer = true) }
            if (call.direct == null && call.link.room.host != wallet.publicKey) {
                addParticipant(call.link.room.host); acquire(call.link.room.host, media = true, offer = true)
            }
        } finally { connectingMedia = false }
    }

    fun controls(token: String, microphone: Boolean?, camera: Boolean?) { post {
        val call = current ?: return@post
        if (call.token != token || !call.phase.started || !accepted) return@post
        guard()
        if (camera == true && (!TalkRuntime.visible || !TalkCapture.permitted(context, true))) return@post
        capture?.controls(microphone ?: call.microphone, camera ?: call.camera)
        publish(call.copy(microphone = microphone ?: call.microphone, camera = camera ?: call.camera,
            localVideo = capture?.video, cameraPaused = (camera ?: call.camera) && !TalkRuntime.visible))
        leases.values.filter { it.callbacks.media }.forEach { lease -> capture?.let { lease.media(it.audio, it.video, call.link.room.id) } }
        microphone?.let { send(TalkProtocol.event(wallet, call.link.room, "toggle-audio", call.participants.map { it.key }, now(), it)) }
        camera?.let { send(TalkProtocol.event(wallet, call.link.room, "toggle-video", call.participants.map { it.key }, now(), it)) }
    } }
    fun switchCamera(token: String) { post { if (current?.token == token && accepted && TalkRuntime.visible) capture?.switchCamera() } }
    fun end(token: String) { post {
        val call = current ?: return@post
        if (call.token != token || !call.phase.live) return@post
        val phase = if (call.incoming && !accepted) CallPhase.DECLINED else CallPhase.ENDED
        try {
            guard()
            if (call.direct != null && call.phase in setOf(CallPhase.INCOMING, CallPhase.DIALING))
                send(TalkProtocol.invite(wallet, call.direct, call.link.room, if (call.incoming) TalkDial.REJECT else TalkDial.CANCEL, now()))
            else if (accepted) send(TalkProtocol.event(wallet, call.link.room, "peer-left", call.participants.map { it.key }, now()))
        } catch (_: Exception) { }
        finish(phase)
    } }
    fun interrupt() { post { finish(CallPhase.INTERRUPTED) } }

    private suspend fun send(transaction: SaitoTransaction) {
        guard()
        val outcomes = sockets.values.map { socket -> scope.async {
            runCatching { socket.request(Fileshare.relay(transaction), beforeSend = { guard() }) }
        } }.awaitAll()
        check(outcomes.any { it.isSuccess }) { "No Talk relay accepted the signal" }
    }
    private fun publish(call: TalkCall) { current = call; TalkRuntime.call.value = call }
    private fun finish(phase: CallPhase) {
        val call = current
        accepted = false
        leases.values.forEach { it.close() }; leases.clear()
        capture?.close(); capture = null
        if (call != null && call.phase.live) {
            retired.add(call.link.room.id); if (retired.size > 128) retired.remove(retired.first())
            publish(call.copy(phase = phase, localVideo = null, participants = call.participants.map { it.copy(video = null, connected = false) }))
        }
        closeIfIdle()
    }
    private fun closeNetwork() {
        hooks.forEach { it.close() }; hooks.clear()
        sockets.values.forEach { it.close() }; sockets.clear(); online.value = false
    }
    private fun closeIfIdle() {
        if (current?.phase?.started == true) return
        if (observers == 0) {
            closeNetwork(); closed = true; events.close(); TalkRuntime.remove(this); scope.cancel()
        }
    }
    private fun post(operation: suspend () -> Unit): Boolean {
        if (closed) return false
        if (events.trySend(operation).isSuccess) return true
        scope.launch { finish(CallPhase.INTERRUPTED); closeNetwork() }; return false
    }
    private fun now() = System.currentTimeMillis()
}
