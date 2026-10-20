package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.ui.AppSpacing
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** One post hierarchy, with secondary actions owned outside the transient popup. */
@Composable
internal fun PostCard(post: FeedPost, backend: SaitoBackend, onIdentity: (String) -> Unit,
    onPost: (FeedPost) -> Unit, social: SocialState?, quotes: QuoteState?, onQuote: ((FeedPost) -> Unit)? = null) {
    val author = registeredIdentity(post.author).name
    val timestamp = remember(post.createdAt) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.ENGLISH).format(Date(post.createdAt))
    }
    val profileLabel = stringResource(R.string.post_profile_for, author)
    val threadLabel = stringResource(R.string.post_thread_for, author)
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = threadLabel) { onPost(post) }
        .padding(vertical = AppSpacing.gap)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = profileLabel) { onIdentity(post.author) }
                .semantics { contentDescription = profileLabel },
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                GeneratedProfilePicture(post.author, Modifier.size(40.dp).clip(CircleShape).clearAndSetSemantics { })
                Column(Modifier.weight(1f)) {
                    Text(author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(timestamp, maxLines = 2, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PostMoreActions(post, author, timestamp, social?.reports)
        }
        Text(post.text, Modifier.padding(vertical = AppSpacing.small), style = MaterialTheme.typography.bodyLarge)
        PostImages(post.images, backend)
        PostVideo(post, backend)
        QuoteContent(post, quotes, backend)
        SocialActions(post, social, onQuote, showReport = false)
    }
}

@Composable
private fun PostMoreActions(post: FeedPost, author: String, timestamp: String, reports: ReportState?) {
    var expanded by remember { mutableStateOf(false) }
    var identity by rememberSaveable(post.id, post.author) { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.post_actions_for, author)
    // ReportAction stays composed after the popup closes, retaining confirmation,
    // exact pending candidates and the existing result/retry behavior.
    ReportAction(post, reports) { reportLabel, reportEnabled, report ->
        Box {
            IconButton(onClick = { expanded = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = menuLabel }) {
                Icon(painterResource(R.drawable.ic_more), contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.post_public_identity)) },
                    onClick = { expanded = false; identity = true })
                if (reportLabel != null) DropdownMenuItem(text = { Text(reportLabel) }, enabled = reportEnabled,
                    onClick = { expanded = false; report() })
            }
        }
    }
    if (identity) AlertDialog(onDismissRequest = { identity = false },
        title = { Text(stringResource(R.string.post_public_identity)) },
        text = { SelectionContainer {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text(author, style = MaterialTheme.typography.titleMedium)
                Text(post.author, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.post_published_at, timestamp), style = MaterialTheme.typography.bodySmall)
            }
        } }, confirmButton = { TextButton(onClick = { identity = false }) { Text(stringResource(R.string.done)) } })
}
