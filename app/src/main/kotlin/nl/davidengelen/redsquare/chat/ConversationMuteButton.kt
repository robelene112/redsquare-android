package nl.davidengelen.redsquare.chat

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.moderation.LocalPersonalControls

@Composable
internal fun ConversationMuteButton(conversation: String) {
    val controls = LocalPersonalControls.current ?: return
    val muted = conversation in controls.policy.mutedConversations
    var confirming by remember(controls, conversation) { mutableStateOf(false) }
    IconButton(enabled = controls.ready && !controls.saving, onClick = { confirming = true }) {
        Icon(painterResource(if (muted) R.drawable.ic_notifications_off else R.drawable.ic_notifications),
            stringResource(if (muted) R.string.chat_unmute_conversation else R.string.chat_mute_conversation))
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.chat_conversation_notifications)) },
        text = { Text(stringResource(R.string.chat_conversation_mute_detail)) },
        confirmButton = {
            TextButton(enabled = controls.ready && !controls.saving, onClick = {
                controls.setConversationMuted(conversation, !muted)
                confirming = false
            }) { Text(stringResource(if (muted) R.string.chat_conversation_unmute else R.string.personal_mute)) }
        },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.personal_cancel)) } })
}
