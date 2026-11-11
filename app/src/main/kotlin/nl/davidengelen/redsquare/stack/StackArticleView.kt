package nl.davidengelen.redsquare.stack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.PostImage
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

/** Keep images native and bounded, rather than handing arbitrary article markup to a WebView. */
@Composable
internal fun LiveStackMarkdown(article: StackArticle, backend: SaitoBackend) {
    val markdown = article.markdown
    val pattern = Regex("!\\[([^\\]]*)]\\(([^)]+)\\)")
    var offset = 0
    for (image in pattern.findAll(markdown)) {
        if (image.range.first > offset) StackMarkdown(markdown.substring(offset, image.range.first), reader = true)
        val target = image.groupValues[2]
        val source = if (target.startsWith("stack:image:")) article.images[target.removePrefix("stack:image:")]
            else RedSquareImages.safeSource(target)
        if (source != null) PostImage(source, backend, image.groupValues[1].ifBlank { null },
            Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp)))
        else Text(stringResource(R.string.image_unavailable), style = MaterialTheme.typography.bodySmall)
        offset = image.range.last + 1
    }
    if (offset < markdown.length) StackMarkdown(markdown.substring(offset), reader = true)
}
