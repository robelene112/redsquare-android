package nl.davidengelen.redsquare.gateway

import com.google.auth.oauth2.GoogleCredentials
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** External data-only send. Acceptance is not a device receipt; caller owns durable retries.
 * The caller supplies an authorized, refreshable credential and explicit target project.
 * localOrigin is limited to owned loopback HTTP fixtures. Production always uses Google HTTPS.
 */
class FcmSender(private val project: String, private val credentials: GoogleCredentials, localOrigin: String? = null,
    private val clock: java.time.Clock = java.time.Clock.systemUTC()) : java.io.Closeable {
    sealed interface Result {
        data class Accepted(val name: String) : Result
        data object Unregistered : Result
        data class Retry(val afterMillis: Long) : Result
        data class Rejected(val status: Int) : Result
    }
    private val closed = java.util.concurrent.atomic.AtomicBoolean()
    private val active = java.util.concurrent.atomic.AtomicReference<okhttp3.Call?>()
    @Volatile private var authorizationTask: java.util.concurrent.FutureTask<Map<String, List<String>>>? = null
    private val production = URI("https://fcm.googleapis.com/v1/projects/$project/messages:send")
    private val endpoint: String
    init {
        require(project.matches(Regex("[a-z0-9-]{1,128}")))
        val origin = if (localOrigin == null) "https://fcm.googleapis.com" else URI(localOrigin).let {
            require(it.scheme == "http" && it.host == "127.0.0.1" && it.port in 1..65535 &&
                it.rawUserInfo == null && it.rawQuery == null && it.rawFragment == null && it.rawPath in setOf("", "/"))
            "http://127.0.0.1:${it.port}"
        }
        endpoint = "$origin/v1/projects/$project/messages:send"
    }

    fun send(fid: String, wake: String, expiresAt: Long): Result {
        require(fid.matches(Regex("[A-Za-z0-9_-]{1,256}")) && wake.matches(Regex("[0-9a-f]{64}")))
        if (closed.get() || expiresAt <= clock.millis()) return Result.Rejected(410)
        return try {
            // Refreshable Google credentials obtain fresh metadata for each attempt; never cache a bearer here.
            val authorization = authorization()
            val now = clock.millis()
            if (expiresAt <= now) return Result.Rejected(410)
            val ttl = minOf((expiresAt - now) / 1000, 2_419_200L)
            val message = JsonObject().apply {
                addProperty("fid", fid)
                add("data", JsonObject().apply { addProperty("version", "1"); addProperty("wake", wake) })
                add("android", JsonObject().apply { addProperty("priority", "HIGH"); addProperty("ttl", "${ttl}s") })
            }
            val body = JsonObject().apply { add("message", message) }.toString()
            val request = Request.Builder().url(endpoint).header("Authorization", authorization)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            val call = client.newCall(request)
            if (!active.compareAndSet(null, call)) return Result.Retry(10_000)
            try {
                if (closed.get()) { call.cancel(); return Result.Rejected(410) }
                call.execute().use { response ->
                // Status and retry headers are usable even when an error body is absent or unreadable.
                if (response.code == 429 || response.code >= 500) return Result.Retry(retryAfter(response.header("Retry-After"),
                    if (response.code == 429) 60_000L else 10_000L))
                if (response.code != 200 && response.code != 404) return Result.Rejected(response.code)
                val bytes = try { response.body.byteStream().readNBytes(65_537) }
                    catch (failure: IOException) {
                        if (response.code == 404) return Result.Rejected(404)
                        throw failure
                    }
                if (bytes.size > 65_536) {
                    if (response.code == 404) return Result.Rejected(404)
                    throw IOException("FCM response too large")
                }
                val parsed = try {
                    val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                    GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(text, JsonObject::class.java)
                } catch (_: Exception) { null }
                when {
                    response.code == 200 -> {
                        val name = parsed?.get("name")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                        if (name == null || !name.matches(Regex("projects/[^/\\s]{1,128}/messages/[^/\\s]{1,1024}"))) {
                            throw IOException("FCM acceptance unavailable")
                        }
                        Result.Accepted(name)
                    }
                    response.code == 404 && unregistered(parsed) -> Result.Unregistered
                    else -> Result.Rejected(response.code)
                }
                }
            } finally { active.compareAndSet(call, null) }
        } catch (_: IOException) { Result.Retry(10_000) }
    }

    /** A timed-out provider is drained, never multiplied. It never mutates gateway state or sends. */
    @Synchronized private fun authorization(): String {
        if (closed.get()) throw IOException("FCM sender closed")
        val task = authorizationTask ?: java.util.concurrent.FutureTask<Map<String, List<String>>> {
            credentials.getRequestMetadata(production)
        }.also {
            authorizationTask = it
            Thread(it, "gateway-fcm-authorization").apply { isDaemon = true }.start()
        }
        val metadata = try { task.get(12, TimeUnit.SECONDS) }
        catch (failure: java.util.concurrent.TimeoutException) { throw IOException("FCM authorization pending", failure) }
        catch (failure: InterruptedException) { Thread.currentThread().interrupt(); throw IOException("FCM authorization interrupted", failure) }
        catch (failure: java.util.concurrent.ExecutionException) {
            authorizationTask = null; throw IOException("FCM authorization unavailable", failure)
        }
        authorizationTask = null
        if (closed.get()) throw IOException("FCM sender closed")
        return metadata.entries.firstOrNull { it.key.equals("Authorization", ignoreCase = true) }?.value?.singleOrNull()
            ?: throw IOException("FCM authorization unavailable")
    }

    override fun close() {
        closed.set(true)
        active.get()?.cancel()
        authorizationTask?.cancel(true)
    }

    private fun unregistered(body: JsonObject?): Boolean {
        val error = body?.get("error")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
        val details = error.get("details")?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
        return details.any { value ->
            if (!value.isJsonObject) false else value.asJsonObject.let { detail ->
                detail.get("@type")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ==
                    "type.googleapis.com/google.firebase.fcm.v1.FcmError" &&
                    detail.get("errorCode")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString == "UNREGISTERED"
            }
        }
    }

    private fun retryAfter(value: String?, minimum: Long): Long {
        if (value == null) return minimum
        val seconds = value.toLongOrNull()
        val delay = if (seconds != null && seconds >= 0) {
            if (seconds > Long.MAX_VALUE / 1000) Long.MAX_VALUE else seconds * 1000
        } else try {
            maxOf(0, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - clock.millis())
        } catch (_: Exception) { minimum }
        return maxOf(minimum, delay)
    }

    companion object {
        /** Called only when the operator explicitly enables the sender with an authorized target project. */
        fun applicationDefault(project: String): FcmSender = FcmSender(project,
            GoogleCredentials.getApplicationDefault().createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging")))
        private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
    }
}
