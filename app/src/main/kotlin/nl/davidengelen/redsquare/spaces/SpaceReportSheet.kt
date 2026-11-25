package nl.davidengelen.redsquare.spaces

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.DesignSheet
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

@Composable internal fun SpaceReportSheet(message: String?, submit: (suspend (String) -> Unit)?, onClose: () -> Unit) {
    key(message) {
        val scope = rememberCoroutineScope()
        var reason by remember { mutableStateOf("") }
        var pending by remember { mutableStateOf(false) }
        var submitted by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        DesignSheet(true, stringResource(if (submitted) R.string.spaces_report_sent else R.string.spaces_report_message),
            onDismiss = { if (!pending) onClose() }, contentKey = message,
            footer = { first ->
                if (submitted) Button(onClick = onClose, modifier = first.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.spaces_close))
                } else {
                    Button(onClick = {
                        if (!pending && submit != null) {
                            pending = true
                            failed = false
                            scope.launch {
                                try {
                                    submit(reason)
                                    submitted = true
                                    reason = ""
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { failed = true }
                                finally { pending = false }
                            }
                        }
                    }, enabled = submit != null && reason.isNotBlank() && !pending,
                        modifier = first.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(if (pending) R.string.spaces_report_sending else R.string.spaces_report_send))
                    }
                    TextButton(onClick = onClose, enabled = !pending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.spaces_cancel))
                    }
                }
            }) { first ->
            if (submitted) Text(stringResource(R.string.spaces_report_sent_detail), style = MaterialTheme.typography.bodyMedium)
            else {
                Text(stringResource(R.string.spaces_report_private_detail), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(reason, { next ->
                    if (next.codePointCount(0, next.length) <= SpaceMessageReport.MAX_REASON) reason = next
                }, first.fillMaxWidth(), enabled = submit != null && !pending,
                    label = { Text(stringResource(R.string.spaces_report_reason)) }, minLines = 2, maxLines = 5)
                if (failed || submit == null) InlineStatus(stringResource(R.string.spaces_report_unavailable), StatusTone.WARNING)
            }
        }
    }
}
