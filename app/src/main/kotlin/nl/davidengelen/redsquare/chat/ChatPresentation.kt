package nl.davidengelen.redsquare.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.feed.ProfileAvatar
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.SaitoBackend
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** One accessible sender header for a visual sequence of individually signed messages. */
@Composable
internal fun ChatSenderHeader(author: String, onIdentity: (String) -> Unit) {
    val identity = registeredIdentity(author).name
    val label = stringResource(R.string.chat_message_identity_for, identity)
    Row(Modifier.fillMaxWidth(0.94f).heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClickLabel = label) { onIdentity(author) }
        .semantics { contentDescription = label }.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileAvatar(author, Modifier.size(32.dp).clip(CircleShape))
        Text(identity, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
    }
}

/** The same readable message surface for private and unencrypted group conversations. */
@Composable
internal fun ChatMessageBubble(author: String, text: String, createdAt: Long, outgoing: Boolean,
    backend: SaitoBackend, selectable: Boolean = false, actions: (@Composable () -> Unit)? = null,
    onQuote: (String) -> Unit, onIdentity: ((String) -> Unit)? = null, showIdentity: Boolean = true) {
    val identity = registeredIdentity(author).name
    val senderDescription = stringResource(R.string.chat_message_from, identity)
    val timestamp = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.ENGLISH).format(Date(createdAt))
    val messageContent: @Composable () -> Unit = {
        if (selectable) SelectionContainer { PrivateMessageText(text, backend, onQuote) }
        else PrivateMessageText(text, backend, onQuote)
    }
    val time: @Composable (Modifier) -> Unit = { modifier ->
        Text(timestamp, modifier, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val media = remember(text) { chatPhoto(privateMessageBody(text)) != null }
    Surface(Modifier.widthIn(max = 290.dp).fillMaxWidth().then(if (showIdentity) Modifier else
        Modifier.semantics { contentDescription = senderDescription }), shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (outgoing) 18.dp else 5.dp,
        bottomEnd = if (outgoing) 5.dp else 18.dp),
        color = if (outgoing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (outgoing) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (showIdentity) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = stringResource(R.string.chat_message_identity_for, identity)
                    val target = if (onIdentity == null) Modifier else Modifier.heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClickLabel = label) { onIdentity(author) }
                        .semantics { contentDescription = label }
                    Box(Modifier.weight(1f).then(target), contentAlignment = Alignment.CenterStart) {
                        Text(identity, style = MaterialTheme.typography.labelMedium)
                    }
                    actions?.invoke()
                }
                messageContent()
                time(Modifier)
            } else if (media) {
                // Keep images and their attribution at the complete bubble width.
                messageContent()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    time(Modifier.weight(1f))
                    actions?.invoke()
                }
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        messageContent()
                        time(Modifier)
                    }
                    actions?.invoke()
                }
            }
        }
    }
}

/** Contextual actions share the bubble's identity; callbacks retain each conversation's permissions. */
@Composable
internal fun ChatMessageActions(liked: Boolean, canLike: Boolean, canReply: Boolean,
    onLike: () -> Unit, onReply: () -> Unit, onCopy: () -> Unit, onIdentity: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more), stringResource(R.string.chat_message_actions))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            if (onIdentity != null) DropdownMenuItem(text = { Text(stringResource(R.string.profile_identity_details)) },
                onClick = { expanded = false; onIdentity() })
            DropdownMenuItem(text = { Text(stringResource(if (liked) R.string.chat_unlike else R.string.chat_like)) },
                enabled = canLike, onClick = { expanded = false; onLike() })
            DropdownMenuItem(text = { Text(stringResource(R.string.post_reply)) }, enabled = canReply,
                onClick = { expanded = false; onReply() })
            DropdownMenuItem(text = { Text(stringResource(R.string.chat_copy)) },
                onClick = { expanded = false; onCopy() })
        }
    }
}

@Composable
internal fun ConversationDetails(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_conversation_details)) },
        text = {
            val label = stringResource(R.string.chat_details_content)
            Column(Modifier.verticalScroll(rememberScrollState()).semantics { contentDescription = label },
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } })
}

@Composable
internal fun ChatIdentityRow(key: String, title: String, subtitle: String, titleMaxLines: Int = Int.MAX_VALUE) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatar(key, Modifier.size(40.dp).clip(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
