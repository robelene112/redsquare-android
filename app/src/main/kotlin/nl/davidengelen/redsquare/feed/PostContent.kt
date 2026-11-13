package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

/** Shared real content for browse surfaces and independently verified quote/compose targets. */
@Composable
internal fun PostContent(post: FeedPost, backend: SaitoBackend, style: TextStyle = MaterialTheme.typography.bodyLarge,
    verticalPadding: Dp = AppSpacing.small, tagged: Boolean = true, onOpenSource: (() -> Unit) -> Unit = { it() }) {
    val link = remember(post.text, post.link, post.envelope) {
        if (RedSquareVideo.youtube(post.text, post.link) == null) RedSquareLinks.preview(post) else null
    }
    val caption = link?.caption ?: post.text
    if (caption.isNotEmpty()) Text(caption,
        Modifier.padding(vertical = verticalPadding).let { if (tagged) it.testTag("post-body-text") else it }, style = style)
    PostImages(post.images, backend, onOpenSource = onOpenSource)
    PostVideo(post, backend)
    if (link != null) PostLinkCard(link, backend, onOpenSource)
}

@Composable
private fun PostLinkCard(link: RedSquareLink, backend: SaitoBackend, onOpenSource: (() -> Unit) -> Unit) {
    val uri = LocalUriHandler.current
    val nativeStack = remember(link.url) { StackLink.parse(link.url) != null }
    val nativeStore = remember(link.url) { StoreLink.parse(link.url) != null }
    var unavailable by remember(link.url) { mutableStateOf(false) }
    OutlinedCard(onClick = { onOpenSource {
        try { uri.openUri(link.url); unavailable = false }
        catch (_: android.content.ActivityNotFoundException) { unavailable = true }
        catch (_: SecurityException) { unavailable = true }
    } }, modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.small),
        shape = RoundedCornerShape(AppSpacing.cardRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        link.image?.let { source ->
            PostImage(source, backend, stringResource(R.string.post_link_image), Modifier.fillMaxWidth().height(160.dp))
            val credits = nl.davidengelen.redsquare.media.retainedGiphyAttributions(listOf(source))
            GifCredits(source, credits[source], onOpenSource)
        }
        Column(Modifier.padding(AppSpacing.gap), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (link.stack) stringResource(R.string.post_stack_link) else link.host,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            link.title?.let { Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            link.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Text(if (nativeStore) stringResource(R.string.post_open_store) else if (nativeStack) stringResource(R.string.post_open_stack) else stringResource(R.string.post_open_link, link.host), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    InlineStatus(if (unavailable) stringResource(R.string.post_link_unavailable) else null, StatusTone.WARNING)
}
