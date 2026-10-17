package nl.davidengelen.redsquare.notifications

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.PushSubscriptionClient
import nl.davidengelen.redsquare.saito.SaitoLocalState
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit

/** Device binding adapter, supplied through app configuration rather than push payloads. */
internal object PushGatewayBinding {
    private val execution = Mutex()
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()

    fun configuredOrigin(): String = URI(BuildConfig.PUSH_RESOLVER).let {
        require(it.scheme == "https" && !it.host.isNullOrBlank() && it.rawUserInfo == null &&
            it.rawQuery == null && it.rawFragment == null && it.rawPath == "/v1/wakes/resolve")
        URI("https", null, it.host, it.port, null, null, null).toASCIIString()
    }

    suspend fun reconcile(context: Context, fid: String?, origin: String, localEndpoint: String? = null) =
        withContext(Dispatchers.IO) {
            execution.withLock {
                if (localEndpoint != null) {
                    val fixture = URI(localEndpoint)
                    require(BuildConfig.DEBUG && Build.HARDWARE in listOf("ranchu", "goldfish") &&
                        fixture.scheme == "http" && fixture.host == "127.0.0.1" && fixture.port in 1..65535 &&
                        fixture.path in listOf("", "/") && fixture.rawUserInfo == null &&
                        fixture.rawQuery == null && fixture.rawFragment == null)
                } else require(origin == configuredOrigin())
                val executionContext = currentCoroutineContext()
                val exchange = PushSubscriptionClient.Http { publicOrigin, path, body, signature ->
                    executionContext.ensureActive()
                    val request = Request.Builder().url("${localEndpoint?.trimEnd('/') ?: publicOrigin}$path")
                        .post(body.toRequestBody("application/json".toMediaType()))
                    if (signature != null) request.header("X-Saito-Signature", signature)
                    http.newCall(request.build()).execute().use { response ->
                        val bytes = ByteArray(4097)
                        var size = 0
                        response.body.byteStream().use { input ->
                            while (size < bytes.size) {
                                val count = input.read(bytes, size, bytes.size - size)
                                if (count < 0) break
                                check(count > 0); size += count
                            }
                        }
                        require(size <= 4096)
                        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes, 0, size)).toString()
                        executionContext.ensureActive()
                        PushSubscriptionClient.Reply(response.code, text)
                    }
                }
                PushSubscriptionClient(origin, DeviceJournal(context), exchange).reconcile {
                    executionContext.ensureActive()
                    if (fid == null) null else pushIdentity(context)?.let { (wallet, backend) ->
                        PushSubscriptionClient.Demand(wallet, backend, fid)
                    }
                }
            }
        }

    /** Installation authority survives wallet replacement, separate from per-wallet feature files. */
    private class DeviceJournal(context: Context) : PushSubscriptionClient.Journal {
        private val pending = File(context.noBackupFilesDir, "push.binding.v1.new")
        private val file = EncryptedLocalFile(context, "push.binding.v1", "redsquare.push.binding", "push.binding.")
        override fun load(): String? = synchronized(SaitoLocalState.lock) {
            val contents = file.load() ?: run { check(!pending.exists()); return@synchronized null }
            require(contents.version == 1.toByte())
            contents.text
        }
        override fun save(value: String) = synchronized(SaitoLocalState.lock) {
            load() // Preserve unreadable prior authority for recovery.
            file.save(EncryptedLocalFile.Contents(1, value))
        }
    }
}
