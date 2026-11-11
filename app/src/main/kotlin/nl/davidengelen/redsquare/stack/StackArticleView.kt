package nl.davidengelen.redsquare.stack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.ImageData
import com.mohamedrejeb.richeditor.model.ImageLoader
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import kotlinx.coroutines.CancellationException
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.PostImage
import nl.davidengelen.redsquare.feed.PostImageLoader
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.*
import java.text.DateFormat
import java.util.Date

@Composable
internal fun LiveStackCard(article: StackArticle, backend: SaitoBackend, onClick: () -> Unit) {
    val name = registeredIdentity(article.author).name
    Card(onClick, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        article.cover?.let { source ->
            PostImage(source, backend, null, Modifier.fillMaxWidth().height(164.dp))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.stack_card_label), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            Text(article.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (article.excerpt.isNotBlank()) Text(article.excerpt, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stackArticleDate(article), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun stackArticleDate(article: StackArticle): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(article.createdAt))

/** The existing Markdown parser owns code/formatting; only actual image nodes ask this loader. */
@OptIn(ExperimentalRichTextApi::class)
@Composable
internal fun LiveStackMarkdown(article: StackArticle, backend: SaitoBackend) {
    val rich = remember(article.revision, article.markdown) {
        RichTextState().setMarkdown(article.markdown)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val bounds = with(density) { Size(maxWidth.toPx(), 220.dp.toPx()) }
        val loader = remember(article.revision, backend, bounds) {
            StackArticleImageLoader(article.images, backend, bounds)
        }
        SelectionContainer {
            RichText(rich,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif,
                    fontSize = 18.sp, lineHeight = 29.7.sp),
                imageLoader = loader)
        }
    }
}

@OptIn(ExperimentalRichTextApi::class)
private class StackArticleImageLoader(private val images: Map<String, String>, private val backend: SaitoBackend,
    private val bounds: Size) : ImageLoader {
    @Composable
    override fun load(model: Any): ImageData {
        val target = model as? String
        val source = if (target?.startsWith("stack:image:") == true) images[target.removePrefix("stack:image:")]
            else target?.let(RedSquareImages::safeSource)
        var retry by remember(source) { mutableIntStateOf(0) }
        var bitmap by remember(source, backend) {
            mutableStateOf<ImageBitmap?>(null)
        }
        var failed by remember(source, backend) { mutableStateOf(false) }
        LaunchedEffect(source, backend, retry) {
            failed = false
            try {
                requireNotNull(source)
                bitmap = PostImageLoader.load(source, backend).asImageBitmap()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
        val fallback = painterResource(R.drawable.ic_image)
        val delegate = bitmap?.let { BitmapPainter(it) } ?: fallback
        val painter = remember(delegate, bounds) { StackReaderPainter(delegate, bounds) }
        return ImageData(painter,
            contentDescription = if (bitmap != null) null else stringResource(if (failed) R.string.image_unavailable else R.string.image_loading),
            modifier = if (failed) Modifier.clickable(onClickLabel = stringResource(R.string.retry)) { retry++ } else Modifier)
    }
}

/** Stable bounded geometry for loading, error and raster content; all painting preserves aspect ratio. */
private class StackReaderPainter(private val delegate: Painter,
    override val intrinsicSize: Size) : Painter() {
    override fun DrawScope.onDraw() {
        val child = delegate.intrinsicSize
        if (child.width <= 0 || child.height <= 0 || !child.width.isFinite() || !child.height.isFinite()) return
        val scale = minOf(size.width / child.width, size.height / child.height)
        val fitted = Size(child.width * scale, child.height * scale)
        translate((size.width - fitted.width) / 2, (size.height - fitted.height) / 2) {
            with(delegate) { draw(fitted) }
        }
    }
}
