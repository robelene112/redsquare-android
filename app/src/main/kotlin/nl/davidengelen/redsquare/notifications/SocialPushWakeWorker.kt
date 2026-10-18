package nl.davidengelen.redsquare.notifications

import android.content.Context
import android.app.NotificationManager
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.io.IOException
import java.net.URI
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoLocalState
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.chat.ChatNotificationResult

private val wakeExecution = Mutex()

/** The push transport supplies only an opaque wake ID; endpoints come from app configuration. */
internal object SocialPushWakeScheduling {
    suspend fun enqueue(context: Context, wake: String, resolverEndpoint: String,
        archiveEndpoint: String? = null): UUID = withContext(Dispatchers.IO) {
        require(wake.matches(Regex("[0-9a-f]{64}")))
        val manager = WorkManager.getInstance(context)
        val name = "social-wake:$wake"
        val request = OneTimeWorkRequestBuilder<SocialPushWakeWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(workDataOf("wake" to wake, "resolver" to resolverEndpoint,
                "archive" to archiveEndpoint, "receivedAt" to System.currentTimeMillis()))
            .build()
        manager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request).result.get(5, TimeUnit.SECONDS)
        manager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS).single().id
    }

    suspend fun cancelAndAwait(context: Context, wake: String) = withContext(Dispatchers.IO) {
        require(wake.matches(Regex("[0-9a-f]{64}")))
        val name = "social-wake:$wake"
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(name).result.get(5, TimeUnit.SECONDS)
        withTimeout(20_000) {
            wakeExecution.withLock {
                check(manager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS).all { it.state.isFinished })
            }
        }
    }
}

/** Resolve only for the current consenting identity, then reuse the signed selected-Archive receiver. */
class SocialPushWakeWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        wakeExecution.withLock work@{
            currentCoroutineContext().ensureActive()
            if (isStopped) return@work Result.failure()
            val wake = inputData.getString("wake") ?: return@work Result.failure()
            if (!wake.matches(Regex("[0-9a-f]{64}"))) return@work Result.failure()
            val receivedAt = inputData.getLong("receivedAt", -1)
            if (receivedAt < 0) return@work Result.failure()
            val now = System.currentTimeMillis()
            if (now >= receivedAt && now - receivedAt > LIFETIME) return@work Result.success()
            val endpoint = inputData.getString("resolver") ?: return@work Result.failure()
            val resolver = try { URI(endpoint) } catch (_: Exception) { return@work Result.failure() }
            val fixture = BuildConfig.DEBUG && Build.HARDWARE in listOf("ranchu", "goldfish") &&
                resolver.scheme == "http" && resolver.host == "127.0.0.1" && resolver.port in 1..65535
            if ((!fixture && (resolver.scheme != "https" || resolver.host.isNullOrBlank())) ||
                resolver.rawUserInfo != null || resolver.rawQuery != null || resolver.rawFragment != null ||
                resolver.path != "/v1/wakes/resolve") return@work Result.failure()
            val resumed = receiptExecution.withLock {
                currentCoroutineContext().ensureActive()
                if (isStopped) return@work Result.failure()
                val archive = if (fixture) inputData.getString("archive") else null
                PrivateChatPush.resume(applicationContext, wake, archive) ?: GroupChatPush.resume(applicationContext, wake, archive)
            }
            if (resumed != null) return@work chatResult(resumed)
            val selected = try {
                synchronized(SaitoLocalState.lock) {
                    pushIdentity(applicationContext)?.let { it.wallet to it.backend }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return@work Result.retry() }
            if (selected == null) return@work Result.success()
            val (wallet, backend) = selected
            val url = try { endpoint.toHttpUrl() } catch (_: IllegalArgumentException) { return@work Result.failure() }
            // Read-only recipient proof. The gateway must match this signer to the wake's owner.
            // An opaque ID by itself must not disclose its public wallet/backend reference.
            val intent = "Saito push resolve v1\n$url\n$wake"
            val body = JsonObject().apply { addProperty("version", 1); addProperty("wake", wake) }
            val request = Request.Builder().url(url)
                .header("X-Saito-Owner", wallet.publicKey)
                .header("X-Saito-Signature", wallet.sign(intent.toByteArray(Charsets.UTF_8)))
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            val payload = try {
                client.newCall(request).execute().use { response ->
                    if (response.code in listOf(404, 410)) return@work Result.success()
                    if (response.code != 200) return@work Result.retry()
                    response.body.byteStream().use { input ->
                        val bytes = ByteArray(1025)
                        var size = 0
                        while (size < bytes.size) {
                            val count = input.read(bytes, size, bytes.size - size)
                            if (count < 0) break
                            check(count > 0)
                            size += count
                        }
                        if (size !in 1..1024) return@work Result.failure()
                        Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                            .decode(java.nio.ByteBuffer.wrap(bytes, 0, size)).toString()
                    }
                }
            } catch (_: IOException) {
                currentCoroutineContext().ensureActive()
                return@work Result.retry()
            }
            currentCoroutineContext().ensureActive()
            val chatReference = try { ChatPushReference.parse(payload) }
            catch (_: Exception) { return@work Result.failure() }
            val reference = try { chatReference?.reference ?: SocialPushReference.parse(payload) }
            catch (_: Exception) { return@work Result.failure() }
            if (reference.owner != wallet.publicKey || reference.backend != backend) return@work Result.success()
            val archive = if (fixture) inputData.getString("archive") ?: backend.peerUrl else backend.peerUrl
            if (chatReference != null) {
                val current = try { pushIdentity(applicationContext) }
                catch (_: Exception) { return@work Result.retry() }
                if (current == null || current.wallet.publicKey != reference.owner || current.backend != reference.backend ||
                    chatReference.kind.category !in current.categories.split(',')) return@work Result.success()
                val envelope = try { original(current.wallet, wake, url) ?: return@work Result.success() }
                catch (_: IOException) { currentCoroutineContext().ensureActive(); return@work Result.retry() }
                catch (_: Exception) { return@work Result.failure() }
                currentCoroutineContext().ensureActive()
                return@work receiptExecution.withLock {
                    currentCoroutineContext().ensureActive()
                    if (isStopped) return@withLock Result.failure()
                    chatResult(when (chatReference.kind) {
                        ChatPushReference.Kind.PRIVATE -> PrivateChatPush.receive(applicationContext, reference, envelope, archive, wake)
                        ChatPushReference.Kind.GROUP -> GroupChatPush.receive(applicationContext, reference, envelope, archive, wake)
                    })
                }
            }
            receiptExecution.withLock receipt@{
                currentCoroutineContext().ensureActive()
                if (isStopped) return@receipt Result.failure()
                val result = try { SocialPush.receive(applicationContext, payload, archive) }
                catch (_: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    SocialPushResult.RETRY
                }
                when (result) {
                    SocialPushResult.PUBLISHED, SocialPushResult.SUPPRESSED -> Result.success()
                    SocialPushResult.RETRY -> Result.retry()
                }
            }
        }
    }

    private fun chatResult(outcome: ChatNotificationResult): Result = when (outcome) {
        ChatNotificationResult.PUBLISHED, ChatNotificationResult.SUPPRESSED -> Result.success()
        ChatNotificationResult.RETRY -> Result.retry()
    }

    private fun original(wallet: nl.davidengelen.redsquare.saito.SaitoWallet, wake: String, resolver: okhttp3.HttpUrl): String? {
        val url = resolver.newBuilder().encodedPath("/v1/wakes/original").build()
        val intent = "Saito push original v1\n$url\n$wake"
        val body = JsonObject().apply { addProperty("version", 1); addProperty("wake", wake) }
        val request = Request.Builder().url(url).header("X-Saito-Owner", wallet.publicKey)
            .header("X-Saito-Signature", wallet.sign(intent.toByteArray(Charsets.UTF_8)))
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return client.newCall(request).execute().use { response ->
            if (response.code in listOf(404, 410)) return null
            if (response.code != 200) throw IOException("Chat original unavailable")
            val limit = 8 * 1024 * 1024 + 512
            val bytes = ByteArray(limit + 1)
            var size = 0
            response.body.byteStream().use { input ->
                while (size < bytes.size) {
                    val count = input.read(bytes, size, bytes.size - size)
                    if (count < 0) break
                    check(count > 0); size += count
                }
            }
            require(size in 1..limit)
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes, 0, size)).toString()
            val retained = requireNotNull(com.google.gson.GsonBuilder().setStrictness(com.google.gson.Strictness.STRICT)
                .create().fromJson(text, JsonObject::class.java))
            require(retained.keySet() == setOf("version", "envelope"))
            val version = retained.get("version")
            require(version.isJsonPrimitive && version.asJsonPrimitive.isNumber && version.asBigDecimal.intValueExact() == 1)
            val envelope = retained.get("envelope")
            require(envelope.isJsonPrimitive && envelope.asJsonPrimitive.isString)
            envelope.asString.also { require(it.toByteArray(Charsets.UTF_8).size <= 8 * 1024 * 1024) }
        }
    }

    private companion object {
        const val LIFETIME = 7 * 24 * 60 * 60 * 1000L
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(12, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS).build()
    }
}
