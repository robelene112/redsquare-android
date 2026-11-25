package nl.davidengelen.redsquare.spaces

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.Closeable
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean

internal data class SpaceRoomDestination(val space: String, val channel: String, val name: String)

/** The call owns a separate authenticated peer and a scoped handle to the encrypted account store. */
internal class SpaceRoomAuthority(
    context: Context,
    private val wallet: SaitoWallet,
    val backend: SaitoBackend,
    endpoint: String,
    val address: SpaceVoiceSignalKey.Address,
) : Closeable {
    private val closed = AtomicBoolean()
    private val exchange = Mutex()
    private val store = AndroidSpaceClientStore(context, wallet.publicKey, "$backend:$endpoint", address.authority)
    private val peer = SaitoPeer(wallet, endpoint)
    val traversalAvailable = endpoint == backend.peerUrl
    val owner = wallet.publicKey

    private val client = SpaceClient(wallet, backend.webUrl, address.authority, store, Clock.systemUTC()) { request ->
        check(!closed.get())
        require(peer.servicePublicKey("spaces-v1") == address.authority) { "Room authority changed" }
        peer.request(request) { check(!closed.get()) }.toString(Charsets.UTF_8)
    }

    fun newSession(): SpaceVoiceSignalKey = SpaceVoiceSignalKey.create(address, wallet)

    suspend fun join(keys: SpaceVoiceSignalKey): SpaceVoiceRoom = exchange.withLock {
        client.joinVoiceRoom(address.space, address.channel, keys.contact)
    }

    suspend fun renew(session: String, through: Long): SpaceVoiceRoom = exchange.withLock {
        client.renewVoiceRoom(address.space, address.channel, session, through)
    }

    suspend fun signal(session: String, packet: SpaceVoiceSignalKey.Packet) = exchange.withLock {
        client.relayVoiceSignal(address.space, address.channel, session, packet)
    }

    suspend fun leave(session: String) = exchange.withLock {
        client.leaveVoiceRoom(address.space, address.channel, session)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        peer.close()
        store.close()
    }
}
