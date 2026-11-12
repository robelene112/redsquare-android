package nl.davidengelen.redsquare.fileshare

import android.content.Context
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.*
import java.io.Closeable

/** Files lease the same authenticated connection as Talk, without enabling media. */
internal class SaitoFileChannel(context: Context, wallet: SaitoWallet, backend: SaitoBackend,
    endpoint: String, contact: String, validate: () -> Unit, signal: (SaitoTransaction) -> Unit,
    opened: () -> Unit, message: (String) -> Unit, stageData: (String) -> Boolean, failed: () -> Unit,
    otherData: (String) -> Boolean, useTraversal: Boolean = true) : Closeable {
    private val lease = SharedRtcPeers.acquire(context, wallet, backend.id, contact, endpoint, useTraversal,
        RtcCallbacks(validate = validate, signal = signal, opened = opened, failed = failed,
            stageData = { envelope ->
                Fileshare.ownsDirectFrame(envelope) && stageData(envelope)
            }, otherData = { envelope ->
                // Initial controls stay behind the queued OPEN notification.
                // Only an established encrypted inbox may bypass the IO actor.
                if (Fileshare.ownsDirectFrame(envelope)) { message(envelope); true } else otherData(envelope)
            }))
    val connected get() = lease.connected
    suspend fun offer() = lease.offer()
    suspend fun receive(request: String, body: JsonObject, signature: String? = null) = lease.receive(request, body, signature)
    fun send(text: String) = lease.send(text)
    override fun close() = lease.close()
}
