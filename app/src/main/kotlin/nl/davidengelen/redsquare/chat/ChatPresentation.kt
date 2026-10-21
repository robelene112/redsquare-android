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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.feed.GeneratedProfilePicture
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.SaitoBackend
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** The same readable message surface for private and unencrypted group conversations. */
@Composable
internal fun ChatMessageBubble(author: String, text: String, createdAt: Long, outgoing: Boolean,
    backend: SaitoBackend, selectable: Boolean = false, actions: (@Composable () -> Unit)? = null,
    onQuote: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth(0.94f), shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (outgoing) 16.dp else 4.dp,
        bottomEnd = if (outgoing) 4.dp else 16.dp),
        color = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(registeredIdentity(author).name, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                actions?.invoke()
            }
            if (selectable) SelectionContainer { PrivateMessageText(text, backend, onQuote) }
            else PrivateMessageText(text, backend, onQuote)
            Text(DateFormat.getTimeInstance(DateFormat.SHORT, Locale.ENGLISH).format(Date(createdAt)),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Contextual actions share the bubble's identity; callbacks retain each conversation's permissions. */
@Composable
internal fun ChatMessageActions(liked: Boolean, canLike: Boolean, canReply: Boolean,
    onLike: () -> Unit, onReply: () -> Unit, onCopy: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more), stringResource(R.string.chat_message_actions))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
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
internal fun ChatIdentityRow(key: String, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GeneratedProfilePicture(key, Modifier.size(40.dp).clip(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
