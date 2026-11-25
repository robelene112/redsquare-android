package nl.davidengelen.redsquare

import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceVoiceRoom
import nl.davidengelen.redsquare.spaces.SpaceVoiceSignalKey
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Independent wallet, signalling session and actual host Chromium codecs. */
internal class SpaceRoomBrowserFixture(address: SpaceVoiceSignalKey.Address, private val peer: Int,
    nativeContact: SpaceVoiceSignalKey.Contact, private val request: (String, JsonObject) -> JsonObject) : Closeable {
    private val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + (4 + peer))
    private val keys = SpaceVoiceSignalKey.create(address, wallet)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outgoing = Channel<SpaceVoiceSignalKey.Packet>(64)
    private val contacts = ConcurrentHashMap<String, SpaceVoiceSignalKey.Contact>()
    private var joined = false
    private var run: Job? = null
    val errors = CopyOnWriteArrayList<String>()
    val contact = keys.contact
    val remoteCapture = ConcurrentHashMap<String, SpaceVoiceSignalKey.CaptureState>()
    @Volatile private var localCapture = SpaceVoiceSignalKey.CaptureState(1, microphone = true, camera = true)
    @Volatile var expectRemoval = false
    @Volatile var removed = false
        private set
    val browser = TalkBrowserPeer(keys.rtcWallet, nativeContact.key, if (peer == 1) "#ef2020" else "#2050ef",
        if (peer == 1) 660 else 880, debugPort = 9221 + peer) { envelope ->
        val message = SaitoEnvelope.decode(envelope)
        check(message.author == contact.key && message.body.get("module").asString == "Stun")
        val recipient = contacts.values.singleOrNull { it.key in message.recipients } ?: return@TalkBrowserPeer
        check(outgoing.trySend(keys.seal(recipient, envelope)).isSuccess)
    }

    fun start() {
        check(!joined)
        val first = SpaceVoiceRoom.read(call("room-join") { add("contact", contact.json()) })
        check(first.admitted)
        joined = true
        run = scope.launch {
            try {
                var cursor = 0L
                lease@ while (isActive) {
                    val room = SpaceVoiceRoom.read(call("room-renew") { addProperty("receivedThrough", cursor) })
                    if (!room.admitted && expectRemoval) { removed = true; break }
                    check(room.admitted)
                    val peers = room.participants.map { it.contact }.filter { it.session != contact.session }
                    contacts.values.filter { old -> peers.none { it.session == old.session } }.forEach { lost ->
                        contacts.remove(lost.session); browser.removeSpacePeer(lost.key)
                    }
                    peers.forEach { seen ->
                        if (contacts.putIfAbsent(seen.session, seen) == null) {
                            browser.connectSpacePeer(seen.key)
                            sendCapture(seen)
                        }
                    }
                    room.signals.forEach { delivery ->
                        val sender = requireNotNull(contacts[delivery.packet.from])
                        val signed = keys.open(sender, delivery.packet)
                        val capture = SpaceVoiceSignalKey.captureState(signed)
                        if (capture != null) remoteCapture[sender.session] = capture
                        else browser.spaceSignal(sender.key, signed.body)
                        cursor = delivery.sequence
                    }
                    for (attempt in 1..12) {
                        val packet = outgoing.tryReceive().getOrNull() ?: break
                        if (!contacts.containsKey(packet.to)) continue
                        when (relay(packet)) {
                            "relayed", "recipient-retired" -> Unit // The next signed roster drops the retired recipient.
                            "sender-retired" -> {
                                check(expectRemoval) { "Chromium room admission ended unexpectedly" }
                                removed = true
                                break@lease
                            }
                            else -> error("Unknown owned fixture relay outcome")
                        }
                    }
                    delay(350)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { errors += failure.stackTraceToString() }
        }
    }

    fun setCamera(enabled: Boolean) {
        browser.setSpaceCamera(enabled)
        localCapture = localCapture.copy(sequence = localCapture.sequence + 1, camera = enabled)
        contacts.values.forEach(::sendCapture)
    }

    private fun sendCapture(recipient: SpaceVoiceSignalKey.Contact) {
        check(outgoing.trySend(keys.sealCapture(recipient, localCapture, System.currentTimeMillis())).isSuccess)
    }

    fun assertRelayRejected(recipient: SpaceVoiceSignalKey.Contact, outcome: String) {
        val packet = keys.sealCapture(recipient, localCapture, System.currentTimeMillis())
        check(relay(packet) == outcome)
    }

    private fun relay(packet: SpaceVoiceSignalKey.Packet): String =
        call("room-signal") { add("packet", packet.json()) }.get("outcome").asString

    private fun call(action: String, fields: JsonObject.() -> Unit = {}) = request(action, JsonObject().apply {
        addProperty("peer", peer)
        addProperty("session", contact.session)
        fields()
    })

    override fun close() {
        scope.cancel()
        runBlocking { withTimeoutOrNull(4_000) { run?.join() } }
        try { if (joined) call("room-leave") } finally { browser.close() }
    }
}
