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
    val busy: Boolean = false, val acknowledged: Boolean = false, val failed: Boolean = false,
    val ready: Boolean = false, val saveFailed: Boolean = false, val attempted: Boolean = false,
    val backendError: Boolean = false)

/** One wallet and selected backend's reports, shared by every post view. */
internal class ReportState(private val context: Context, private val wallet: SaitoWallet,
    val backend: SaitoBackend, private val endpoint: String, private val scope: CoroutineScope) {
    val statuses = mutableStateMapOf<String, ReportStatus>()
    fun canReport(post: FeedPost): Boolean = post.author != wallet.publicKey

    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    private fun store(target: String) = PublicStringStore(context,
        "report.${backend.id}.${wallet.publicKey}.$target.v1", 1)

    fun load(post: FeedPost) {
        if (statuses.containsKey(post.id)) return
        statuses[post.id] = ReportStatus()
        // Navigation can replace a post row while this shared state is still loading.
        scope.launch { loadSaved(post) }
    }

    private suspend fun loadSaved(post: FeedPost) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                val saved = store(post.id).load()
                require(!saved.unreadable)
                saved.values.singleOrNull()?.let { value ->
                    val record = gson.fromJson(value, JsonObject::class.java)
                    require(record.keySet() == setOf("transaction", "acknowledged") ||
                        record.keySet() == setOf("transaction", "acknowledged", "attempted") ||
                        record.keySet() == setOf("transaction", "acknowledged", "attempted", "backendError"))
                    val candidate = SaitoTransaction.restore(record.get("transaction").asString)
                    val signed = SaitoEnvelope.decode(candidate.envelope())
                    require(signed.author == wallet.publicKey && signed.recipients == listOf(wallet.publicKey))
                    require(signed.body.keySet() == setOf("module", "request", "data"))
                    require(signed.body.get("module").asString == "RedSquare" && signed.body.get("request").asString == "flag tweet")
                    val data = signed.body.getAsJsonObject("data")
                    require(data.keySet() == setOf("signature") && data.get("signature").asString == post.id)
                    val acknowledgement = record.get("acknowledged").asJsonPrimitive
                    require(acknowledgement.isBoolean)
                    // Older records cannot prove that no transmission was attempted.
                    val attempted = record.get("attempted")?.asJsonPrimitive?.let {
                        require(it.isBoolean); it.asBoolean
                    } ?: true
                    require(!acknowledgement.asBoolean || attempted)
                    val backendError = record.get("backendError")?.asJsonPrimitive?.let {
                        require(it.isBoolean); it.asBoolean
                    } ?: false
                    require(!backendError || (attempted && !acknowledgement.asBoolean))
                    ReportStatus(candidate, acknowledged = acknowledgement.asBoolean,
                        failed = !acknowledgement.asBoolean, ready = true, attempted = attempted, backendError = backendError)
                } ?: ReportStatus(ready = true)
            }
            statuses[post.id] = loaded
        } catch (cancelled: CancellationException) { statuses.remove(post.id); throw cancelled }
        catch (_: Exception) { statuses[post.id] = ReportStatus(failed = true) }
    }

    fun retryLoad(post: FeedPost) {
        if (statuses[post.id]?.busy == true) return
        statuses.remove(post.id)
        load(post)
    }

    private fun save(target: String, candidate: SaitoTransaction, acknowledged: Boolean, attempted: Boolean,
        backendError: Boolean) {
        val record = JsonObject().apply {
            addProperty("transaction", candidate.encoded()); addProperty("acknowledged", acknowledged)
            addProperty("attempted", attempted)
            addProperty("backendError", backendError)
        }.toString()
        val storage = store(target)
        storage.save(listOf(record))
        check(storage.load().let { !it.unreadable && it.values == listOf(record) })
    }

    fun send(post: FeedPost) {
        if (!canReport(post)) return
        val status = statuses[post.id] ?: return
        if (!status.ready || status.busy || status.acknowledged) return
        statuses[post.id] = status.copy(busy = true, failed = false, saveFailed = false)
        scope.launch {
            var candidate = status.candidate
            var acknowledged = false
            var savingDetails = false
            var attempted = status.attempted
            var backendError = status.backendError
            try {
                if (candidate == null) candidate = withContext(Dispatchers.IO) {
                    val body = JsonObject().apply {
                        addProperty("module", "RedSquare"); addProperty("request", "flag tweet")
                        add("data", JsonObject().apply { addProperty("signature", post.id) })
                    }
                    SaitoTransaction.create(wallet, body, System.currentTimeMillis())
                }
                val original = requireNotNull(candidate)
                // Retain the exact signed intent before any possible transmission.
                savingDetails = true
                withContext(NonCancellable + Dispatchers.IO) { save(post.id, original, false, attempted, backendError) }
                savingDetails = false
                statuses[post.id] = ReportStatus(original, busy = true, ready = true, attempted = attempted, backendError = backendError)
                withContext(Dispatchers.IO) {
                    SaitoPeer(wallet, endpoint).use { peer ->
                        peer.authenticate()
                        val reply = try { peer.request(original) {
                            check(AndroidBackendStore(context).load() == backend &&
                                AndroidWalletStore(context).loadExisting().publicKey == wallet.publicKey)
                            savingDetails = true
                            save(post.id, original, false, true, false)
                            savingDetails = false
                            attempted = true
                            backendError = false
                        }.toString(Charsets.UTF_8) }
                        catch (_: SaitoPeer.RequestError) { backendError = true; null }
                        if (reply != null) {
                            val response = gson.fromJson(reply, JsonObject::class.java)
                            require(response != null)
                            backendError = response.get("err")?.let {
                                it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank()
                            } == true
                            require(backendError || response.size() == 0)
                            acknowledged = !backendError
                        }
                        savingDetails = true
                        withContext(NonCancellable) { save(post.id, original, acknowledged, true, backendError) }
                        savingDetails = false
                    }
                }
                statuses[post.id] = ReportStatus(original, acknowledged = acknowledged, failed = !acknowledged,
                    ready = true, attempted = true, backendError = backendError)
            } catch (failure: Exception) {
                if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                statuses[post.id] = ReportStatus(candidate,
                    acknowledged = acknowledged, failed = !acknowledged, ready = true,
                    saveFailed = savingDetails, attempted = attempted, backendError = backendError)
            }
            finally { statuses[post.id]?.let { statuses[post.id] = it.copy(busy = false) } }
        }
    }
}
