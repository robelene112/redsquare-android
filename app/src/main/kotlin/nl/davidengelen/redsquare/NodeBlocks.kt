package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoNodeBlock
import okhttp3.*

internal data class NodeBlockObservation(val backend: SaitoBackend, val block: SaitoNodeBlock,
    val receivedAtMillis: Long)

/** Each selected-node lifetime owns its results; old requests cannot populate another node. */
@Composable
internal fun rememberNodeBlockObservation(backend: SaitoBackend?, intent: Intent): NodeBlockObservation? {
    val reader = remember(intent) { NodeBlockReader.fromIntent(intent) }
    val observed = remember(backend) { mutableStateOf<NodeBlockObservation?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(backend, reader, lifecycle) {
        val selected = backend ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (currentCoroutineContext().isActive) {
                reader.read(selected)?.let { observed.value = NodeBlockObservation(selected, it, System.currentTimeMillis()) }
                delay(30_000)
            }
        }
    }
    return observed.value
}

/** Small public node-bootstrap reads; wallet funding, peer selection and feed refresh are independent. */
private class NodeBlockReader(private val endpoints: Map<SaitoBackend, String?>) {
    suspend fun read(backend: SaitoBackend): SaitoNodeBlock? {
        val endpoint = endpoints[backend] ?: return null
        return suspendCancellableCoroutine { result ->
            val call = client.newCall(Request.Builder().url(endpoint)
                .header("Cache-Control", "no-cache, no-store").build())
            result.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, failure: IOException) {
                    if (result.isActive) result.resume(null)
                }
                override fun onResponse(call: Call, response: Response) {
                    val block = try {
                        response.use {
                            if (!it.isSuccessful || it.body.contentLength() > SaitoNodeBlock.MAX_BYTES) null
                            else {
                                val bytes = ByteArrayOutputStream(4096)
                                it.body.byteStream().use { stream ->
                                    val buffer = ByteArray(4096)
                                    while (true) {
                                        val read = stream.read(buffer, 0, minOf(buffer.size, SaitoNodeBlock.MAX_BYTES + 1 - bytes.size()))
                                        if (read < 0) break
                                        require(read > 0 && bytes.size() + read <= SaitoNodeBlock.MAX_BYTES)
                                        bytes.write(buffer, 0, read)
                                    }
                                }
                                SaitoNodeBlock.parse(bytes.toByteArray(), System.currentTimeMillis())
                            }
                        }
                    } catch (_: Exception) { null }
                    if (result.isActive) result.resume(block)
                }
            })
        }
    }

    companion object {
        private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()

        fun fromIntent(intent: Intent): NodeBlockReader {
            val isolated = BuildConfig.DEBUG && (intent.hasExtra("feedPeerUrl") ||
                intent.getBooleanExtra("feedDisabled", false) || intent.hasExtra("balanceApiUrl") ||
                intent.hasExtra("nodeBlockApiUrl") || intent.hasExtra("nodeBlockOfficialApiUrl"))
            return NodeBlockReader(SaitoBackend.entries.associateWith { backend ->
                if (!isolated) "${backend.webUrl}/options"
                else fixtureUrl(intent.getStringExtra(if (backend == SaitoBackend.OWNER)
                    "nodeBlockApiUrl" else "nodeBlockOfficialApiUrl"))
            })
        }

        private fun fixtureUrl(value: String?): String? {
            if (value == null) return null
            return try {
                val uri = URI(value)
                value.takeIf { uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
                    uri.path == "/options" && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null }
            } catch (_: Exception) { null }
        }
    }
}
