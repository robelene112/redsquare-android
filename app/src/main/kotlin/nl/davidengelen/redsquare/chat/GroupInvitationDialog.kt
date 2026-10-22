package nl.davidengelen.redsquare.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.AppDialogWindow
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

/** Deliberate device actions use the original compatible link without adding invitation claims. */
@Composable
internal fun GroupInvitationDialog(invitation: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = requireNotNull(LocalActivity.current)
    val invitationLabel = stringResource(R.string.group_invitation_link)
    val shareLabel = stringResource(R.string.group_invitation_share)
    var feedback by remember(invitation) { mutableIntStateOf(0) }
    val copied = feedback == R.string.group_invitation_copied
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        AppDialogWindow(styleSystemBars = true)
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.group_cancel))
                    }
                    Text(stringResource(R.string.group_invitation_link),
                        Modifier.weight(1f).padding(start = AppSpacing.small).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                    Text(stringResource(R.string.group_invitation_hint), style = MaterialTheme.typography.bodyMedium)
                    val linkLabel = stringResource(R.string.group_invitation_accessibility)
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        SelectionContainer {
                            Text(invitation, Modifier.fillMaxWidth().padding(AppSpacing.gap)
                                .semantics { contentDescription = linkLabel }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(stringResource(R.string.group_privacy), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = AppSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    InlineStatus(if (feedback == 0) null else stringResource(feedback),
                        if (copied) StatusTone.SUCCESS else StatusTone.ERROR)
                    Button(onClick = {
                        feedback = try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(invitationLabel, invitation))
                            R.string.group_invitation_copied
                        } catch (_: Exception) { R.string.group_invitation_copy_error }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_copy), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(AppSpacing.small))
                        Text(stringResource(R.string.group_invitation_copy))
                    }
                    OutlinedButton(onClick = {
                        feedback = try {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, invitation)
                            }
                            activity.startActivity(Intent.createChooser(send, shareLabel))
                            0
                        } catch (_: Exception) { R.string.group_invitation_share_error }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_share), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(AppSpacing.small))
                        Text(stringResource(R.string.group_invitation_share))
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.group_done))
                    }
                }
            }
        }
    }
}
