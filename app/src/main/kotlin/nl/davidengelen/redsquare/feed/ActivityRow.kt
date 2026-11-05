package nl.davidengelen.redsquare.feed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.identity.IdentityDetailsDialog
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.saito.FeedPost
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Distinct author and event targets; read feedback only follows a verified local save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActivityRow(authors: List<String>, createdAt: Long, caption: Int, post: FeedPost?, previewLoading: Boolean, likeCount: Int?, unread: Boolean?,
    canMark: Boolean, onMark: () -> Unit, onIdentity: (String) -> Unit, onOpen: () -> Unit) {
    val author = authors.first()
    val name = registeredIdentity(author).name
    val timestamp = remember(createdAt) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.ENGLISH).format(Date(createdAt))
    }
    val profileLabel = stringResource(R.string.post_profile_for, name)
    val optionsLabel = stringResource(R.string.activity_options_for, name)
    val openLabel = stringResource(R.string.activity_open_for, name)
    val readLabel = stringResource(when (unread) {
        true -> R.string.activity_unread
        false -> R.string.activity_read
        null -> R.string.activity_read_unknown
    })
    val preview = post?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
        val end = text.offsetByCodePoints(0, minOf(160, text.codePointCount(0, text.length)))
        text.substring(0, end).replace(Regex("\\s+"), " ") + if (end < text.length) "…" else ""
    } ?: stringResource(when {
        previewLoading -> R.string.activity_preview_loading
        post == null -> R.string.activity_preview_unavailable
        post.images.isNotEmpty() -> R.string.activity_photo_post
        post.link.isNotEmpty() -> R.string.activity_link_post
        else -> R.string.activity_no_text
    })
    val icon = when (caption) {
        R.string.notifications_like, R.string.notifications_like_own -> R.drawable.ic_like
        R.string.notifications_reply, R.string.notifications_reply_own -> R.drawable.ic_chat
        R.string.notifications_repost, R.string.notifications_repost_own -> R.drawable.ic_repost
        R.string.notifications_quote, R.string.notifications_quote_own -> R.drawable.ic_quote
        R.string.notifications_mention -> R.drawable.ic_notifications
        else -> R.drawable.ic_compose
    }
    var options by remember { mutableStateOf(false) }
    var people by remember { mutableStateOf(false) }
    var identity by rememberSaveable(author, createdAt) { mutableStateOf(false) }
    val background by animateColorAsState(if (unread == true) MaterialTheme.colorScheme.surfaceContainerLow
        else MaterialTheme.colorScheme.surface, tween(AppSpacing.transitionMillis), label = "Activity read status")
    Surface(color = background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small)) {
            Row(verticalAlignment = Alignment.Top) {
                if (authors.size == 1) {
                Row(Modifier.weight(1f).heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = profileLabel, onClick = { onIdentity(author) })
                    .semantics { contentDescription = profileLabel },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    ProfileAvatar(author, Modifier.size(32.dp).clip(CircleShape).clearAndSetSemantics { })
                    Text(name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall)
                }
                } else Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                    Row {
                        authors.take(3).forEach { actor ->
                            val actorName = registeredIdentity(actor).name
                            val actorLabel = stringResource(R.string.post_profile_for, actorName)
                            IconButton(onClick = { onIdentity(actor) }, modifier = Modifier.size(48.dp)
                                .semantics { contentDescription = actorLabel }) {
                                ProfileAvatar(actor, Modifier.size(32.dp).clip(CircleShape).clearAndSetSemantics { })
                            }
                        }
                    }
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall)
                    Text(pluralStringResource(R.plurals.activity_other_people, authors.size - 1, authors.size - 1),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { options = true }, modifier = Modifier.size(48.dp)
                        .semantics { contentDescription = optionsLabel }) {
                        Icon(painterResource(R.drawable.ic_more), contentDescription = null)
                    }
                    DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.activity_mark_read)) },
                            enabled = canMark, onClick = { options = false; onMark() })
                        if (likeCount != null) DropdownMenuItem(text = { Text(stringResource(R.string.activity_like_people)) },
                            onClick = { options = false; people = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.profile_identity_details)) },
                            onClick = { options = false; identity = true })
                    }
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
                .semantics { contentDescription = openLabel }
                .padding(bottom = AppSpacing.small)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Icon(painterResource(icon), contentDescription = null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(caption), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (unread == true) FontWeight.SemiBold else FontWeight.Normal)
                }
                Text(preview, Modifier.padding(vertical = 4.dp), maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(timestamp, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(readLabel, style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (unread == true) FontWeight.Bold else FontWeight.Normal)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    if (identity) IdentityDetailsDialog(author) { identity = false }
    if (people && likeCount != null) AlertDialog(onDismissRequest = { people = false },
        title = { Text(stringResource(R.string.activity_like_people)) },
        text = { Column {
            Text(stringResource(R.string.activity_like_totals,
                pluralStringResource(R.plurals.activity_likes, likeCount, likeCount),
                pluralStringResource(R.plurals.activity_people, authors.size, authors.size)),
                style = MaterialTheme.typography.bodyMedium)
            val openPerson: (String) -> Unit = { people = false; onIdentity(it) }
            if (authors.size <= 6) Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                authors.forEach { ActivityPerson(it, openPerson) }
            } else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(authors, key = { it }) { ActivityPerson(it, openPerson) }
            }
        } }, confirmButton = { TextButton(onClick = { people = false }) { Text(stringResource(R.string.done)) } })
}

/** Small groups wrap naturally; larger lists reuse the same actual 48dp profile target lazily. */
@Composable
private fun ActivityPerson(author: String, onOpen: (String) -> Unit) {
    val name = registeredIdentity(author).name
    val label = stringResource(R.string.post_profile_for, name)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .clickable(role = Role.Button) { onOpen(author) }
        .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        ProfileAvatar(author, Modifier.size(32.dp).clip(CircleShape).clearAndSetSemantics { })
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}
