package nl.davidengelen.redsquare.stack

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.DesignButton

@Composable
internal fun StackIntro(title: String, body: String, eyebrow: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (eyebrow != null) Text(eyebrow, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary, letterSpacing = 1.8.sp)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StackSection(title: String) { Text(title, style = MaterialTheme.typography.titleMedium) }

@Composable
internal fun StackCover(editorial: Boolean, height: Dp) {
    Image(painterResource(if (editorial) R.drawable.stack_editorial else R.drawable.stack_network),
        contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(18.dp)))
}

private data class SampleArticle(val title: Int, val summary: Int, val author: Int, val byline: Int)
private val samples = listOf(
    SampleArticle(R.string.stack_sample_title, R.string.stack_sample_summary, R.string.stack_author_name, R.string.stack_sample_byline),
    SampleArticle(R.string.stack_scripts_title, R.string.stack_scripts_summary, R.string.stack_alex_name, R.string.stack_scripts_byline),
    SampleArticle(R.string.stack_quiet_title, R.string.stack_quiet_summary, R.string.stack_author_name, R.string.stack_sample_byline))

@Composable
internal fun sampleAuthor(index: Int): String = stringResource(samples[index].author)

@Composable
internal fun StackArticleCard(index: Int, access: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            StackCover(editorial = index != 0, height = 164.dp)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(painterResource(if (index != 2) R.drawable.ic_article else if (access) R.drawable.ic_check else R.drawable.ic_lock),
                        null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(if (index != 2) R.string.stack_card_label else if (access)
                        R.string.stack_access_active else R.string.stack_subscribers_label), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                }
                Text(stringResource(samples[index].title), style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(stringResource(samples[index].summary), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(stringResource(samples[index].byline),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun StackAuthorRow(followed: Boolean, authorIndex: Int = 0, onFollow: () -> Unit, onAuthor: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(onClick = onAuthor, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Text(if (authorIndex == 1) "AM" else "MC", style = MaterialTheme.typography.titleSmall)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(samples[authorIndex].author), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.stack_author_detail), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        DesignButton(onFollow, outlined = true) {
            Text(stringResource(if (followed) R.string.stack_following_preview else R.string.stack_follow_preview))
        }
    }
}

@Composable
internal fun StackMenuRow(title: String, detail: String, icon: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(painterResource(icon), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_forward), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun LazyListScope.articleContent(index: Int, access: Boolean, followed: Boolean,
    onFollow: () -> Unit, onAuthor: () -> Unit, onAccess: () -> Unit) {
    item { StackIntro(stringResource(samples[index].title), stringResource(R.string.stack_article_subtitle), stringResource(R.string.stack_reader_byline, stringResource(samples[index].author).uppercase())) }
    item { StackAuthorRow(followed, index, onFollow, onAuthor) }
    item { StackCover(editorial = index != 0, height = 200.dp) }
    item { StackMarkdown(stringResource(if (index == 2 && !access) R.string.stack_article_excerpt else R.string.stack_reading_body), reader = true) }
    if (index == 2 && !access) item {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.stack_keep_reading), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.stack_support_writer), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                StackBlockButton(onAccess, Modifier.fillMaxWidth()) { Text(stringResource(R.string.stack_access_options)) }
            }
        }
    } else item { StackArticleActions() }
}

@Composable
private fun StackArticleActions() {
    var discussion by remember { mutableStateOf<Boolean?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StackSection(stringResource(R.string.stack_continue_conversation))
        StackBlockButton({ discussion = true }, Modifier.fillMaxWidth(), outlined = true) {
            Text(stringResource(R.string.stack_discuss))
        }
        StackMenuRow(stringResource(R.string.stack_share_article), stringResource(R.string.stack_share_article_detail),
            R.drawable.ic_share) { discussion = false }
        Text(stringResource(R.string.stack_reading_sample_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    discussion?.let { discussing ->
        AlertDialog(onDismissRequest = { discussion = null },
            title = { Text(stringResource(if (discussing) R.string.stack_discuss else R.string.stack_share_article)) },
            text = { Text(stringResource(R.string.stack_article_actions_preview)) },
            confirmButton = { TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { discussion = null }) { Text(stringResource(R.string.done)) } })
    }
}

@Composable
internal fun StackAuthorProfile(authorIndex: Int, followed: Boolean, onFollow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StackCover(editorial = true, height = 128.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Text(if (authorIndex == 1) "AM" else "MC", style = MaterialTheme.typography.headlineSmall)
                }
            }
            Spacer(Modifier.weight(1f))
            DesignButton(onFollow, outlined = true) { Text(stringResource(if (followed)
                R.string.stack_following_preview else R.string.stack_follow_preview)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(samples[authorIndex].author), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(if (authorIndex == 1) R.string.stack_alex_handle else R.string.stack_maya_handle),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.stack_author_bio), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.stack_sample_author), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
    }
}
