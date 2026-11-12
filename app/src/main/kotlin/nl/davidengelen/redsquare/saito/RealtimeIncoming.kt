package nl.davidengelen.redsquare.saito

import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** A feature claims its frames before a socket's ordinary Chat receiver sees them.
 * null = another feature, false = rejected, true = retained/accepted.
 * Wallet and exact endpoint are always part of ownership; there is no global catch-all.
 */
internal object RealtimeIncoming {
    private data class Address(val owner: String, val endpoint: String)
    private class Receiver(val callback: (ByteArray) -> Boolean?) {
        val active = AtomicBoolean(true)
    }
    private val receivers = ConcurrentHashMap<Address, CopyOnWriteArrayList<Receiver>>()

    fun register(owner: String, endpoint: String, callback: (ByteArray) -> Boolean?): Closeable {
        require(SaitoWallet.isPublicKey(owner))
        val address = Address(owner, endpoint)
        val receiver = Receiver(callback)
        receivers.compute(address) { _, previous ->
            (previous ?: CopyOnWriteArrayList<Receiver>()).apply { add(receiver) }
        }
        return Closeable {
            receiver.active.set(false)
            receivers.computeIfPresent(address) { _, entries ->
                entries.remove(receiver)
                entries.takeUnless { it.isEmpty() }
            }
        }
    }

    fun hasReceiver(owner: String, endpoint: String) = receivers[Address(owner, endpoint)]?.any { it.active.get() } == true

    fun receive(owner: String, endpoint: String, bytes: ByteArray): Boolean? {
        for (receiver in receivers[Address(owner, endpoint)].orEmpty()) {
            if (!receiver.active.get()) continue
            val accepted = try { receiver.callback(bytes) } catch (_: Exception) { false }
            if (accepted != null) return accepted
        }
        return null
    }
}
