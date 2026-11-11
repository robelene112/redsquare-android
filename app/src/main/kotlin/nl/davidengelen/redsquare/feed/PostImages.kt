package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.VisualResultReveal
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CancellationException
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.media.GiphyImage
import nl.davidengelen.redsquare.media.GiphyAttribution
import nl.davidengelen.redsquare.media.GiphyCredits
import nl.davidengelen.redsquare.media.LocalGiphyConfiguration
import nl.davidengelen.redsquare.saito.SaitoBackend

@Composable
internal fun PostImages(images: List<String>, backend: SaitoBackend, onRemove: ((Int) -> Unit)? = null, removalEnabled: Boolean = true,
    previewHeight: Dp = 180.dp, gifAttributions: Map<String, GiphyAttribution> = emptyMap(),
    onOpenSource: (() -> Unit) -> Unit = { it() }) {
    if (images.isEmpty()) return
    val configuration = LocalGiphyConfiguration.current
    val retained = nl.davidengelen.redsquare.media.retainedGiphyAttributions(images.filter { configuration.mediaUrl(it) != null })
    val credits = retained + gifAttributions
    var selected by rememberSaveable(images) { mutableStateOf<Int?>(null) }
    val focus = remember(images) { images.map { FocusRequester() } }
    val scope = rememberCoroutineScope()
    val window = LocalWindowInfo.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        images.forEachIndexed { index, source ->
            PostImage(source, backend, stringResource(R.string.image_position, index + 1, images.size),
                Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(AppSpacing.cardRadius)).focusRequester(focus[index]).focusProperties { canFocus = true }.clickable { selected = index })
            GifCredits(source, credits[source], onOpenSource)
            if (onRemove != null) TextButton(enabled = removalEnabled, onClick = { onRemove(index) }) {
                Text(stringResource(R.string.image_remove, index + 1))
            }
        }
    }
    selected?.let { index ->
        ImageViewer(images, index, backend, credits, onOpenSource) {
            selected = null
            scope.launch {
                withFrameNanos { }
                snapshotFlow { window.isWindowFocused }.first { it }
                focus[index].requestFocus()
            }
        }
    }
}

@Composable
internal fun GifCredits(source: String, credit: GiphyAttribution?, onOpenSource: (() -> Unit) -> Unit) {
    if (LocalGiphyConfiguration.current.mediaUrl(source) != null) {
        Text(stringResource(R.string.giphy_brand), style = MaterialTheme.typography.labelSmall)
        if (credit != null) GiphyCredits(credit, onNavigate = onOpenSource)
    }
}

@Composable
internal fun PostImage(source: String, backend: SaitoBackend, label: String?, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    if (LocalGiphyConfiguration.current.mediaUrl(source) != null) {
        GiphyImage(source, label, modifier, fit = scale == ContentScale.Fit)
        return
    }
    var image by remember(source, backend) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(source, backend) { mutableStateOf(false) }
    var retry by remember(source, backend) { mutableIntStateOf(0) }
    LaunchedEffect(source, backend, retry) {
        failed = false
        try { image = PostImageLoader.load(source, backend).asImageBitmap() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    // Keep blank/loading image taps from falling through to the containing link or viewer.
    // Child Retry owns its gesture; a scroll can still take over at normal touch slop.
    val loadingInput = if (image == null) Modifier.pointerInput(source, backend) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!down.isConsumed) {
                down.consume()
                waitForUpOrCancellation()?.consume()
            }
        }
    } else Modifier
    key(source, backend) {
        VisualResultReveal(image to failed, modifier.then(loadingInput)) { (ready, unavailable), current ->
            if (ready != null) Image(ready, label, Modifier.fillMaxSize(), contentScale = scale)
            else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                if (unavailable) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.image_unavailable))
                    TextButton(enabled = current && image == null && failed,
                        onClick = { if (image == null && failed) { failed = false; retry++ } }) {
                        Text(stringResource(R.string.retry))
                    }
                }
                else Text(stringResource(R.string.image_loading))
            }
        }
    }
}
