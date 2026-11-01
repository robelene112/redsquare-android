package nl.davidengelen.redsquare.identity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

@Composable
internal fun IdentityDetailsDialog(author: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val name = registeredIdentity(author).name
    var copied by remember(author) { mutableStateOf<Boolean?>(null) }
    AlertDialog(onDismissRequest = onDone,
        title = { Text(stringResource(R.string.profile_identity_details)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(author, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                InlineStatus(when (copied) {
                    true -> stringResource(R.string.profile_identity_copied)
                    false -> stringResource(R.string.profile_identity_copy_failed)
                    null -> null
                }, if (copied == false) StatusTone.ERROR else StatusTone.SUCCESS)
            }
        },
        confirmButton = { TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.done))
        } },
        dismissButton = { TextButton(onClick = {
            copied = runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Saito public key", author))
            }.isSuccess
        }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(painterResource(R.drawable.ic_copy), contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpacing.small))
            Text(stringResource(R.string.profile_identity_copy))
        } })
}
