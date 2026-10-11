package nl.davidengelen.redsquare.feed

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.saito.*

internal data class ReportStatus(val candidate: SaitoTransaction? = null,
    val busy: Boolean = false, val acknowledged: Boolean = false, val failed: Boolean = false)

/** One wallet and selected backend's reports, shared by every post view. */
internal class ReportState(private val context: Context, private val wallet: SaitoWallet,
    val backend: SaitoBackend, private val endpoint: String, private val scope: CoroutineScope) {
    val statuses = mutableStateMapOf<String, ReportStatus>()
    fun canReport(post: FeedPost): Boolean = post.author != wallet.publicKey

    fun send(post: FeedPost) {
        if (!canReport(post)) return
        val status = statuses[post.id] ?: ReportStatus()
        if (status.busy || status.acknowledged) return
        statuses[post.id] = status.copy(busy = true, failed = false)
        scope.launch {
            var candidate = status.candidate
            try {
                withContext(Dispatchers.IO) {
                    if (candidate == null) {
                        val body = JsonObject().apply {
                            addProperty("module", "RedSquare"); addProperty("request", "flag tweet")
                            add("data", JsonObject().apply { addProperty("signature", post.id) })
                        }
                        candidate = SaitoTransaction.create(wallet, body, System.currentTimeMillis())
                    }
                    SaitoPeer(wallet, endpoint).use { peer ->
                        peer.authenticate()
                        val permitted = synchronized(SaitoLocalState.lock) {
                            AndroidBackendStore(context).load() == backend &&
                                AndroidWalletStore(context).loadExisting().publicKey == wallet.publicKey
                        }
                        check(permitted)
                        val reply = peer.request(requireNotNull(candidate)).toString(Charsets.UTF_8)
                        val acknowledgement = GsonBuilder().setStrictness(Strictness.STRICT).create()
                            .fromJson(reply, JsonObject::class.java)
                        require(acknowledgement != null && acknowledgement.size() == 0)
                    }
                }
                statuses[post.id] = ReportStatus(candidate, acknowledged = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { statuses[post.id] = ReportStatus(candidate, failed = true) }
            finally { statuses[post.id]?.let { statuses[post.id] = it.copy(busy = false) } }
        }
    }
}
