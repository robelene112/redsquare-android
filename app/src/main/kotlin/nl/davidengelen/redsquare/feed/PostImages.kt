package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var selected by remember(images) { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        images.forEachIndexed { index, source ->
            PostImage(source, backend, stringResource(R.string.image_position, index + 1, images.size),
                Modifier.fillMaxWidth().height(previewHeight).clickable { selected = index })
            GifCredits(source, gifAttributions[source], onOpenSource)
            if (onRemove != null) TextButton(enabled = removalEnabled, onClick = { onRemove(index) }) {
                Text(stringResource(R.string.image_remove, index + 1))
            }
        }
    }
    selected?.let { index ->
        Dialog(onDismissRequest = { selected = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.image_position, index + 1, images.size), Modifier.weight(1f))
                    TextButton(onClick = { selected = null }) { Text(stringResource(R.string.image_close)) }
                }
                key(index) {
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }
                    val transform = rememberTransformableState { zoom, pan, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        PostImage(images[index], backend, null, Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                        }.transformable(transform), ContentScale.Fit)
                    }
                }
                GifCredits(images[index], gifAttributions[images[index]], onOpenSource)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { selected = index - 1 }, enabled = index > 0) { Text(stringResource(R.string.image_previous)) }
                    TextButton(onClick = { selected = index + 1 }, enabled = index < images.lastIndex) { Text(stringResource(R.string.image_next)) }
                }
            }
        }
    }
}

@Composable
private fun GifCredits(source: String, credit: GiphyAttribution?, onOpenSource: (() -> Unit) -> Unit) {
    if (LocalGiphyConfiguration.current.mediaUrl(source) != null) {
        Text(stringResource(R.string.giphy_brand), style = MaterialTheme.typography.labelSmall)
        if (credit != null) GiphyCredits(credit, onNavigate = onOpenSource)
    }
}

@Composable
private fun PostImage(source: String, backend: SaitoBackend, label: String?, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
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
    val ready = image
    if (ready != null) Image(ready, label, modifier, contentScale = scale)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.image_unavailable))
            TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
        }
        else Text(stringResource(R.string.image_loading))
    }
}
