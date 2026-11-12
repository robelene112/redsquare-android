package nl.davidengelen.redsquare.saito

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.webrtc.*
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** One Saito DTLS/data/media connection per local identity, node scope and counterparty.
 * A file transfer and a call hold separate leases. Releasing one cannot dispose the other.
 * Acquire on the IO actor; no factory or EGL initialization belongs on the UI thread.
 */
internal object SharedRtcPeers {
    private val peers = ConcurrentHashMap<String, SharedRtcPeer>()

    @Synchronized fun acquire(context: Context, wallet: SaitoWallet, nodeScope: String, peer: String,
        endpoint: String, traversal: Boolean, callbacks: RtcCallbacks, additionalEndpoints: List<String> = emptyList()): RtcLease {
        require(SaitoWallet.isPublicKey(peer) && peer != wallet.publicKey)
        val key = "${context.packageName}:${wallet.publicKey}:$nodeScope:$peer"
        while (true) {
            val entry = peers.computeIfAbsent(key) {
                SharedRtcPeer(context.applicationContext, wallet, peer, traversal) { closed -> peers.remove(key, closed) }
            }
            entry.acquire(endpoint, callbacks, additionalEndpoints)?.let { return it }
            peers.remove(key, entry)
        }
    }
}

internal data class RtcCallbacks(
    val validate: () -> Unit,
    val signal: (SaitoTransaction) -> Unit,
    val opened: () -> Unit,
    val failed: () -> Unit,
    val data: (String) -> Boolean = { false },
    val otherData: (String) -> Boolean = { false },
    val track: (MediaStreamTrack) -> Unit = {},
    val media: Boolean = false
)

/** UI/media code owns its tracks; this lease owns only their attachment to this peer. */
internal class RtcLease internal constructor(internal val entry: SharedRtcPeer,
    internal val endpoint: String, internal val callbacks: RtcCallbacks) : Closeable {
    internal val active = AtomicBoolean(true)
    internal var network: Closeable? = null
    val connected get() = active.get() && entry.connected
    suspend fun offer() = entry.offer(this)
    suspend fun receive(request: String, body: com.google.gson.JsonObject, signature: String? = null) = entry.receive(this, request, body, signature)
    fun send(envelope: String) { check(active.get()); callbacks.validate(); entry.send(envelope) }
    fun media(audio: AudioTrack?, video: VideoTrack?, stream: String) {
        check(callbacks.media && active.get())
        entry.media(this, audio, video, stream)
    }
    override fun close() {
        if (active.compareAndSet(true, false)) {
            network?.close(); network = null
            entry.release(this)
        }
    }
}

/** The shared factory is initialized lazily by an actual connection, with no capture until tracks are attached. */
internal object RtcPlatform {
    private var initialized = false
    private var current: PeerConnectionFactory? = null
    private var egl: EglBase? = null
    @Synchronized fun factory(context: Context): PeerConnectionFactory {
        current?.let { return it }
        if (!initialized) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
            initialized = true
        }
        val graphics = EglBase.create().also { egl = it }
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(graphics.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(graphics.eglBaseContext))
            .createPeerConnectionFactory().also { current = it }
    }
    @Synchronized fun graphics(context: Context): EglBase.Context {
        factory(context)
        return requireNotNull(egl).eglBaseContext
    }
}

internal class SharedRtcPeer(context: Context, private val wallet: SaitoWallet, private val remote: String,
    traversal: Boolean, private val removed: (SharedRtcPeer) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<suspend () -> Unit>(128)
    private val leases = CopyOnWriteArrayList<RtcLease>()
    private val closing = AtomicBoolean(false)
    private val factory = RtcPlatform.factory(context)
    private val connection: PeerConnection
    private val channel: DataChannel
    private var makingOffer = false
    private var ignoredOffer = false
    private var started = false
    private var needsNegotiation = false
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private val seen = LinkedHashSet<String>()
    private val senders = mutableListOf<RtpSender>()
    private val remoteTracks = CopyOnWriteArrayList<MediaStreamTrack>()
    @Volatile private var authenticated = false
    @Volatile var connected = false
        private set

    init {
        val servers = (if (traversal) listOf("sf", "sg", "de") else emptyList()).map { region ->
            PeerConnection.IceServer.builder("turn:stun-$region.saito.io:3478")
                .setUsername("guest").setPassword("somepassword").createIceServer()
        }
        connection = requireNotNull(factory.createPeerConnection(PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) { post {
                signal("peer-candidate", com.google.gson.JsonObject().apply {
                    add("iceCandidate", com.google.gson.JsonObject().apply {
                        addProperty("candidate", candidate.sdp); addProperty("sdpMid", candidate.sdpMid)
                        addProperty("sdpMLineIndex", candidate.sdpMLineIndex)
                    })
                })
            } }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                if (state in setOf(PeerConnection.PeerConnectionState.FAILED, PeerConnection.PeerConnectionState.CLOSED)) post { fail() }
            }
            override fun onSignalingChange(state: PeerConnection.SignalingState) {
                if (state == PeerConnection.SignalingState.STABLE) post { negotiateIfNeeded() }
            }
            override fun onRenegotiationNeeded() { post { needsNegotiation = true; negotiateIfNeeded() } }
            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track() ?: return
                track.setEnabled(false)
                post {
                    remoteTracks.addIfAbsent(track)
                    if (authenticated) leases.filter { it.active.get() && it.callbacks.media }.forEach { lease ->
                        lease.callbacks.validate(); track.setEnabled(true); lease.callbacks.track(track)
                    }
                }
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) { channel.close(); channel.dispose() }
        }))
        channel = connection.createDataChannel("data-channel", DataChannel.Init().apply {
            negotiated = true; id = 42; ordered = true
        })
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() { when (channel.state()) {
                DataChannel.State.OPEN -> post { publishOpen() }
                DataChannel.State.CLOSED -> post { fail() }
                else -> Unit
            } }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary || buffer.data.remaining() !in 1..(4 * SaitoTransaction.MAX_MESSAGE_BYTES)) {
                    post { fail() }; return
                }
                val text = ByteArray(buffer.data.remaining()).also(buffer.data::get).toString(Charsets.UTF_8)
                if (!authenticated || closing.get()) return
                try {
                    val signal = Stun.read(text, remote, wallet.publicKey)
                    val request = signal?.body?.get("request")?.asString
                    if (signal != null && request in setOf("peer-joined", "peer-description", "peer-candidate")) {
                        post { if (remember(signal.id)) receiveSignal(requireNotNull(request), signal.body) }
                        return
                    }
                } catch (_: Exception) { post { fail() }; return }
                // File frames go straight to their encrypted disk inbox, not through the control queue.
                for (lease in leases.filter { it.active.get() }) {
                    try {
                        lease.callbacks.validate()
                        val handled = RealtimeIncoming.receiveDirect(wallet.publicKey, lease.endpoint, remote, text)
                        if (handled != null || lease.callbacks.data(text)) return
                    } catch (_: Exception) { post { fail() }; return }
                }
                leases.firstOrNull { it.active.get() }?.let { lease ->
                    try { lease.callbacks.validate(); lease.callbacks.otherData(text) } catch (_: Exception) { post { fail() } }
                }
            }
        })
        scope.launch {
            for (event in events) {
                if (closing.get()) break
                try { withTimeout(15_000) { event() } }
                catch (_: Exception) { fail() }
            }
        }
    }

    @Synchronized fun acquire(endpoint: String, callbacks: RtcCallbacks, additionalEndpoints: List<String>): RtcLease? {
        if (closing.get()) return null
        try { callbacks.validate() }
        catch (failure: Exception) {
            if (leases.isEmpty()) dispose()
            throw failure
        }
        val lease = RtcLease(this, endpoint, callbacks)
        leases.add(lease)
        val registrations = (listOf(endpoint) + additionalEndpoints).distinct().map { route ->
            RealtimeIncoming.register(wallet.publicKey, route) { bytes ->
                val message = Stun.network(bytes, remote, wallet.publicKey) ?: return@register null
                if (message.body.get("request").asString !in setOf("peer-joined", "peer-description", "peer-candidate")) return@register null
                if (!lease.active.get()) return@register null
                try { callbacks.validate() } catch (_: Exception) { lease.close(); return@register null }
                post {
                    if (leases.any { it.active.get() } && remember(message.id)) receiveSignal(message.body.get("request").asString, message.body)
                }
            }
        }
        lease.network = Closeable { registrations.forEach { it.close() } }
        post {
            if (lease.active.get() && connected) {
                callbacks.validate(); callbacks.opened()
                if (callbacks.media) remoteTracks.forEach { it.setEnabled(true); callbacks.track(it) }
            }
        }
        return lease
    }

    suspend fun offer(lease: RtcLease) = command(lease) {
        started = true; needsNegotiation = true; negotiateIfNeeded()
    }
    suspend fun receive(lease: RtcLease, request: String, body: com.google.gson.JsonObject, signature: String?) = command(lease) {
        if (signature == null || remember(signature)) receiveSignal(request, body)
    }

    private suspend fun command(lease: RtcLease, operation: suspend () -> Unit) {
        val done = CompletableDeferred<Unit>()
        check(post {
            try { check(lease.active.get()); lease.callbacks.validate(); operation(); done.complete(Unit) }
            catch (failure: Exception) { done.completeExceptionally(failure); throw failure }
        })
        withTimeout(16_000) { done.await() }
    }

    fun media(lease: RtcLease, audio: AudioTrack?, video: VideoTrack?, stream: String) { post {
        if (!lease.active.get()) return@post
        lease.callbacks.validate()
        senders.forEach { connection.removeTrack(it) }; senders.clear()
        listOfNotNull(audio, video).forEach { senders.add(connection.addTrack(it, listOf(stream))) }
        if (authenticated) remoteTracks.forEach { it.setEnabled(true); lease.callbacks.track(it) }
        needsNegotiation = true; negotiateIfNeeded()
    } }

    fun send(envelope: String) {
        val bytes = envelope.toByteArray(Charsets.UTF_8)
        check(!closing.get() && authenticated && channel.state() == DataChannel.State.OPEN)
        require(bytes.size <= SaitoTransaction.MAX_MESSAGE_BYTES * 3)
        check(channel.bufferedAmount() <= 512 * 1024 && channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false)))
    }

    fun release(lease: RtcLease) {
        if (lease.callbacks.media) remoteTracks.forEach { runCatching { it.setEnabled(false) } }
        leases.remove(lease)
        post {
            if (lease.callbacks.media) {
                senders.forEach { connection.removeTrack(it) }; senders.clear()
                needsNegotiation = true
            }
            if (leases.none { it.active.get() }) dispose() else negotiateIfNeeded()
        }
    }

    private suspend fun receiveSignal(request: String, body: com.google.gson.JsonObject) {
        when (request) {
            "peer-description" -> {
                val description = body.getAsJsonObject("description")
                val type = description.get("type").asString
                require(type in setOf("offer", "answer"))
                val sdp = description.get("sdp").asString
                require(sdp.length in 1..65_536)
                val collision = type == "offer" && (makingOffer || connection.signalingState() != PeerConnection.SignalingState.STABLE)
                ignoredOffer = collision && wallet.publicKey >= remote
                if (ignoredOffer) return
                if (collision) setDescription(SessionDescription(SessionDescription.Type.ROLLBACK, ""), true)
                started = true
                setDescription(SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp), false)
                authenticated = true // Callers admit only the exact counterparty's signed Stun description.
                pendingCandidates.forEach { connection.addIceCandidate(it) }; pendingCandidates.clear()
                if (type == "offer") {
                    val answer = createDescription(answer = true)
                    setDescription(answer, true); sendDescription(answer)
                }
                publishOpen()
            }
            "peer-candidate" -> if (!ignoredOffer) {
                val data = body.getAsJsonObject("iceCandidate") ?: return
                val candidate = data.get("candidate").asString
                val mid = data.get("sdpMid")?.takeUnless { it.isJsonNull }?.asString
                val index = data.get("sdpMLineIndex").asInt
                require(candidate.length <= 4096 && (mid?.length ?: 0) <= 256 && index in 0..31)
                val ice = IceCandidate(mid, index, candidate)
                if (connection.remoteDescription == null) { require(pendingCandidates.size < 128); pendingCandidates.add(ice) }
                else connection.addIceCandidate(ice)
            }
        }
    }

    private suspend fun negotiateIfNeeded() {
        if (!started || !needsNegotiation || makingOffer || connection.signalingState() != PeerConnection.SignalingState.STABLE) return
        needsNegotiation = false; makingOffer = true
        try {
            val description = createDescription()
            setDescription(description, true); sendDescription(description)
        } finally { makingOffer = false }
    }

    private fun sendDescription(description: SessionDescription) = signal("peer-description", com.google.gson.JsonObject().apply {
        add("description", com.google.gson.JsonObject().apply {
            addProperty("type", description.type.canonicalForm()); addProperty("sdp", description.description)
        })
    })

    private fun signal(request: String, body: com.google.gson.JsonObject) {
        // Each route carries the same signature, so upstream Stun deduplicates multiple sockets/nodes.
        val transaction = Stun.signal(wallet, remote, request, body, System.currentTimeMillis())
        var sent = false
        leases.filter { it.active.get() }.forEach { lease ->
            try { lease.callbacks.validate(); lease.callbacks.signal(transaction); sent = true } catch (_: Exception) { }
        }
        check(sent)
    }

    private fun publishOpen() {
        if (!authenticated || channel.state() != DataChannel.State.OPEN || connected) return
        connected = true
        leases.filter { it.active.get() }.forEach { lease -> lease.callbacks.validate(); lease.callbacks.opened() }
    }
    private fun remember(id: String): Boolean {
        if (!seen.add(id)) return false
        if (seen.size > 256) seen.remove(seen.first())
        return true
    }
    private fun post(operation: suspend () -> Unit): Boolean {
        if (closing.get()) return false
        if (events.trySend(operation).isSuccess) return true
        scope.launch { fail() }; return false // Disposal must never run on a WebRTC observer thread.
    }
    private fun fail() {
        if (closing.get()) return
        leases.filter { it.active.get() }.forEach { runCatching { it.callbacks.failed() } }
        dispose()
    }
    private fun dispose() {
        if (!closing.compareAndSet(false, true)) return
        connected = false
        leases.forEach { it.active.set(false); it.network?.close() }; leases.clear()
        channel.unregisterObserver(); channel.close(); channel.dispose()
        remoteTracks.forEach { runCatching { it.setEnabled(false) } }; remoteTracks.clear()
        connection.close(); connection.dispose()
        removed(this); events.close(); scope.cancel()
    }

    private suspend fun createDescription(answer: Boolean = false): SessionDescription = suspendCancellableCoroutine { next ->
        val observer = object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) { if (next.isActive) next.resumeWith(Result.success(description)) }
            override fun onCreateFailure(error: String) { if (next.isActive) next.resumeWith(Result.failure(IllegalStateException("RTC description failed"))) }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }
        if (answer) connection.createAnswer(observer, MediaConstraints()) else connection.createOffer(observer, MediaConstraints())
    }
    private suspend fun setDescription(description: SessionDescription, local: Boolean): Unit = suspendCancellableCoroutine { next ->
        val observer = object : SdpObserver {
            override fun onSetSuccess() { if (next.isActive) next.resumeWith(Result.success(Unit)) }
            override fun onSetFailure(error: String) { if (next.isActive) next.resumeWith(Result.failure(IllegalStateException("RTC description rejected"))) }
            override fun onCreateSuccess(description: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
        }
        if (local) connection.setLocalDescription(observer, description) else connection.setRemoteDescription(observer, description)
    }
}
