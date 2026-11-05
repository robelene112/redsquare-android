package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
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
    val receivedAtMillis: Long, val receivedAtElapsedMillis: Long = SystemClock.elapsedRealtime())

internal enum class NodeBlockFailure { INFORMATION, CONNECTION }
internal sealed interface NodeBlockRead {
    data class Reported(val block: SaitoNodeBlock) : NodeBlockRead
    data object Unavailable : NodeBlockRead
    data object Disconnected : NodeBlockRead
}

/** Observable report state and one interruptible check action, scoped to one selected node. */
@Stable
internal class NodeBlockState(val backend: SaitoBackend?) {
    var observation by mutableStateOf<NodeBlockObservation?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var failure by mutableStateOf<NodeBlockFailure?>(null)
        private set
    internal var generation by mutableIntStateOf(0)
        private set
    private var manual by mutableStateOf(false)
    private var nowMillis by mutableLongStateOf(System.currentTimeMillis())
    private var nowElapsedMillis by mutableLongStateOf(SystemClock.elapsedRealtime())

    val olderBlock: Boolean get() = observation?.let { nowMillis - it.block.timestamp > 5 * 60_000L } == true
    val lastSeen: Boolean get() = observation?.let {
        failure != null || nowElapsedMillis - it.receivedAtElapsedMillis > 90_000L
    } == true
    val showProgress: Boolean get() = checking && (manual || observation == null)

    fun checkAgain() {
        if (checking || backend == null) return
        checking = true
        manual = true
        generation++
    }

    internal fun begin() {
        checking = true
        nowMillis = System.currentTimeMillis()
        nowElapsedMillis = SystemClock.elapsedRealtime()
    }

    internal fun finish(result: NodeBlockRead) {
        nowMillis = System.currentTimeMillis()
        nowElapsedMillis = SystemClock.elapsedRealtime()
        when (result) {
            is NodeBlockRead.Reported -> {
                backend?.let { observation = NodeBlockObservation(it, result.block, nowMillis, nowElapsedMillis) }
                failure = null
            }
            NodeBlockRead.Unavailable -> failure = NodeBlockFailure.INFORMATION
            NodeBlockRead.Disconnected -> failure = NodeBlockFailure.CONNECTION
        }
        checking = false
        manual = false
    }
}

/** Each selected-node lifetime owns its results; old requests cannot populate another node. */
@Composable
internal fun rememberNodeBlocks(backend: SaitoBackend?, intent: Intent): NodeBlockState {
    val reader = remember(intent) { NodeBlockReader.fromIntent(intent) }
    val status = remember(backend) { NodeBlockState(backend) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(status, status.generation, reader, lifecycle) {
        val selected = backend ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (currentCoroutineContext().isActive) {
                status.begin()
                status.finish(reader.read(selected))
                delay(30_000)
            }
        }
    }
    return status
}

/** Small public node-bootstrap reads; wallet funding, peer selection and feed refresh are independent. */
private class NodeBlockReader(private val endpoints: Map<SaitoBackend, String?>) {
    suspend fun read(backend: SaitoBackend): NodeBlockRead {
        val endpoint = endpoints[backend] ?: return NodeBlockRead.Unavailable
        return suspendCancellableCoroutine { result ->
            val call = client.newCall(Request.Builder().url(endpoint)
                .header("Cache-Control", "no-cache, no-store").build())
            result.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, failure: IOException) {
                    if (result.isActive) result.resume(NodeBlockRead.Disconnected)
                }
                override fun onResponse(call: Call, response: Response) {
                    val block = try {
                        response.use {
                            if (!it.isSuccessful || it.body.contentLength() > SaitoNodeBlock.MAX_BYTES) NodeBlockRead.Unavailable
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
                                try { NodeBlockRead.Reported(SaitoNodeBlock.parse(bytes.toByteArray(), System.currentTimeMillis())) }
                                catch (_: Exception) { NodeBlockRead.Unavailable }
                            }
                        }
                    } catch (_: IOException) { NodeBlockRead.Disconnected }
                    catch (_: Exception) { NodeBlockRead.Unavailable }
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
