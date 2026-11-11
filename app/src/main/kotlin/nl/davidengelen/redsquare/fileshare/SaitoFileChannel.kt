package nl.davidengelen.redsquare.fileshare

import android.content.Context
import com.google.gson.JsonObject
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.*
import java.io.Closeable
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import nl.davidengelen.redsquare.saito.Fileshare

/** Data-only WebRTC. Signed Stun descriptions bind DTLS to the selected Saito counterparty. */
internal class SaitoFileChannel(context: Context, private val polite: Boolean,
    private val signal: (String, JsonObject) -> Unit, private val opened: () -> Unit,
    private val message: (String) -> Unit, private val failed: () -> Unit,
    useTraversal: Boolean = true) : Closeable {
    private val factory: PeerConnectionFactory
    private val connection: PeerConnection
    private val channel: DataChannel
    private var ignoredOffer = false
    private var makingOffer = false
    private val candidates = mutableListOf<IceCandidate>()

    init {
        initialize(context)
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        val servers = (if (useTraversal) listOf("sf", "sg", "de") else emptyList()).map { region ->
            // Public Saito traversal configuration from the pinned upstream Stun module.
            PeerConnection.IceServer.builder("turn:stun-$region.saito.io:3478")
                .setUsername("guest").setPassword("somepassword").createIceServer()
        }
        connection = requireNotNull(factory.createPeerConnection(PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                signal("peer-candidate", JsonObject().apply {
                    add("iceCandidate", JsonObject().apply {
                        addProperty("candidate", candidate.sdp); addProperty("sdpMid", candidate.sdpMid)
                        addProperty("sdpMLineIndex", candidate.sdpMLineIndex)
                    })
                })
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                if (state == PeerConnection.PeerConnectionState.FAILED || state == PeerConnection.PeerConnectionState.CLOSED) failed()
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) { channel.close(); channel.dispose() }
            override fun onRenegotiationNeeded() {}
        }))
        channel = connection.createDataChannel("data-channel", DataChannel.Init().apply {
            negotiated = true; id = 42; ordered = true
        })
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                when (channel.state()) {
                    DataChannel.State.OPEN -> opened()
                    DataChannel.State.CLOSED -> failed()
                    else -> Unit
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary || buffer.data.remaining() !in 1..Fileshare.MAX_FRAME_BYTES) { failed(); return }
                val bytes = ByteArray(buffer.data.remaining()).also(buffer.data::get)
                message(bytes.toString(Charsets.UTF_8))
            }
        })
    }

    suspend fun offer() {
        makingOffer = true
        try {
            val offer = createDescription()
            setDescription(offer, local = true)
            sendDescription(offer)
        } finally { makingOffer = false }
    }

    suspend fun receive(request: String, body: JsonObject) {
        if (request == "peer-description") {
            val value = body.getAsJsonObject("description")
            val kind = value.get("type").asString
            require(kind in setOf("offer", "answer"))
            val text = value.get("sdp").asString
            require(text.length in 1..65_536)
            val collision = kind == "offer" && (makingOffer || connection.signalingState() != PeerConnection.SignalingState.STABLE)
            ignoredOffer = collision && !polite
            if (ignoredOffer) return
            if (collision) setDescription(SessionDescription(SessionDescription.Type.ROLLBACK, ""), local = true)
            setDescription(SessionDescription(SessionDescription.Type.fromCanonicalForm(kind), text), local = false)
            candidates.forEach { connection.addIceCandidate(it) }; candidates.clear()
            if (kind == "offer") {
                val answer = createDescription(answer = true)
                setDescription(answer, local = true); sendDescription(answer)
            }
        } else if (request == "peer-candidate" && !ignoredOffer) {
            val value = body.getAsJsonObject("iceCandidate")
            if (value == null || value.isJsonNull) return
            val candidate = value.get("candidate").asString
            val mid = value.get("sdpMid")?.takeUnless { it.isJsonNull }?.asString
            val index = value.get("sdpMLineIndex").asInt
            require(candidate.length <= 4096 && (mid?.length ?: 0) <= 256 && index in 0..31)
            val ice = IceCandidate(mid, index, candidate)
            if (connection.remoteDescription == null) {
                require(candidates.size < 128); candidates.add(ice)
            } else connection.addIceCandidate(ice)
        }
    }

    fun send(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        check(bytes.size <= Fileshare.MAX_FRAME_BYTES && channel.state() == DataChannel.State.OPEN)
        check(channel.bufferedAmount() <= 512 * 1024 && channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false)))
    }
    private fun sendDescription(description: SessionDescription) = signal("peer-description", JsonObject().apply {
        add("description", JsonObject().apply {
            addProperty("type", description.type.canonicalForm()); addProperty("sdp", description.description)
        })
    })
    private suspend fun createDescription(answer: Boolean = false): SessionDescription = suspendCancellableCoroutine { next ->
        val observer = object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) { if (next.isActive) next.resume(description) }
            override fun onCreateFailure(error: String) { if (next.isActive) next.resumeWithException(IllegalStateException("WebRTC description failed")) }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) {}
        }
        if (answer) connection.createAnswer(observer, MediaConstraints()) else connection.createOffer(observer, MediaConstraints())
    }
    private suspend fun setDescription(description: SessionDescription, local: Boolean): Unit = suspendCancellableCoroutine { next ->
        val observer = object : SdpObserver {
            override fun onSetSuccess() { if (next.isActive) next.resume(Unit) }
            override fun onSetFailure(error: String) { if (next.isActive) next.resumeWithException(IllegalStateException("WebRTC description rejected")) }
            override fun onCreateSuccess(description: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
        }
        if (local) connection.setLocalDescription(observer, description) else connection.setRemoteDescription(observer, description)
    }
    override fun close() {
        channel.unregisterObserver(); channel.close(); connection.close()
        channel.dispose(); connection.dispose(); factory.dispose()
    }
    companion object {
        @Volatile private var initialized = false
        @Synchronized private fun initialize(context: Context) {
            if (!initialized) {
                PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .setEnableInternalTracer(false).createInitializationOptions())
                initialized = true
            }
        }
    }
}
