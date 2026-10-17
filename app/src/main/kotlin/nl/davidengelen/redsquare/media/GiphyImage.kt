package nl.davidengelen.redsquare.media

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.nio.ByteBuffer

/** Visible-view lifetime only. Provider assets never enter the raster LRU or a disk cache. */
@Composable
internal fun GiphyImage(source: String, label: String?, modifier: Modifier, fit: Boolean = false) {
    val configuration = LocalGiphyConfiguration.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var drawable by remember(source, configuration) { mutableStateOf<Drawable?>(null) }
    var failed by remember(source, configuration) { mutableStateOf(false) }
    var attempt by remember(source, configuration) { mutableIntStateOf(0) }
    LaunchedEffect(source, configuration, attempt) {
        failed = false
        try { drawable = GiphyImages.load(requireNotNull(configuration.mediaUrl(source))) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val ready = drawable
    val description = if (label == null) Modifier else Modifier.semantics { contentDescription = label }
    Box(modifier.then(description).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (ready != null) {
            DisposableEffect(ready, lifecycle) {
                val animation = ready as? Animatable
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> animation?.start()
                        Lifecycle.Event.ON_PAUSE -> animation?.stop()
                        else -> Unit
                    }
                }
                lifecycle.addObserver(observer)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) animation?.start()
                onDispose { lifecycle.removeObserver(observer); animation?.stop() }
            }
            key(ready) {
                AndroidView(factory = { context -> ImageView(context).apply { cropToPadding = true } }, modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        view.scaleType = if (fit) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
                        if (view.drawable !== ready) view.setImageDrawable(ready)
                    }, onRelease = { view -> (view.drawable as? Animatable)?.stop(); view.setImageDrawable(null) })
            }
        } else if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.image_unavailable))
            TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry)) }
        } else Text(stringResource(R.string.image_loading))
    }
}

private object GiphyImages {
    private val slots = Semaphore(2)

    suspend fun load(source: String): Drawable = withContext(Dispatchers.IO) {
        slots.withPermit {
            val bytes = GiphyHttp.read(source.toHttpUrl(), 8 * 1024 * 1024)
            currentCoroutineContext().ensureActive()
            // API28 ByteBuffer source remains immutable for AnimatedImageDrawable's lifetime.
            val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                require(width in 1..16_384 && height in 1..16_384 && width.toLong() * height <= 100_000_000)
                val scale = minOf(1.0, 512.0 / maxOf(width, height))
                decoder.setTargetSize(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            currentCoroutineContext().ensureActive()
            drawable
        }
    }
}
