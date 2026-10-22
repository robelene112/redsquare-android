package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.SaitoBackend

internal enum class ProfileImageKind(val field: String, val ratio: Int, val label: Int, val changeLabel: Int, val removeLabel: Int) {
    PICTURE("image", 1, R.string.profile_picture, R.string.profile_picture_change, R.string.profile_picture_remove),
    BANNER("banner", 3, R.string.profile_banner, R.string.profile_banner_change, R.string.profile_banner_remove)
}

/** Profile and editor share the bounded raster loader; signed fields never execute HTML/SVG. */
@Composable
internal fun ProfileImage(source: String?, kind: ProfileImageKind, author: String, backend: SaitoBackend, modifier: Modifier) {
    var image by remember(source, backend) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(source, backend) { mutableStateOf(false) }
    var retry by remember(source, backend) { mutableIntStateOf(0) }
    LaunchedEffect(source, backend, retry) {
        if (source == null) return@LaunchedEffect
        failed = false
        try { image = PostImageLoader.load(source, backend).asImageBitmap() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val ready = image
        if (ready != null) Image(ready, stringResource(kind.label), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (kind == ProfileImageKind.PICTURE) GeneratedProfilePicture(author, Modifier.fillMaxSize())
        else if (source != null) Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (failed) R.string.image_unavailable else R.string.image_loading),
                Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            if (failed) ProfileImageRetry(kind) { retry++ }
        }
        if (failed && kind == ProfileImageKind.PICTURE) ProfileImageRetry(kind) { retry++ }
    }
}

/** A full-size retry control never paints over the unavailable-banner explanation. */
@Composable
private fun ProfileImageRetry(kind: ProfileImageKind, onRetry: () -> Unit) {
    FilledTonalIconButton(onClick = onRetry, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(R.drawable.ic_refresh),
            contentDescription = stringResource(if (kind == ProfileImageKind.PICTURE)
                R.string.profile_picture_retry else R.string.profile_banner_retry))
    }
}
