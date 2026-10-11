package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.FeedPost

@Composable
internal fun ReportAction(post: FeedPost, state: ReportState?) {
    if (state == null || !state.canReport(post)) return
    val status = state.statuses[post.id]
    var confirming by rememberSaveable(post.id) { mutableStateOf(false) }
    var showingResult by rememberSaveable(post.id) { mutableStateOf(false) }
    TextButton(enabled = status?.busy != true, onClick = {
        if (status?.acknowledged == true) showingResult = true else confirming = true
    }) { Text(stringResource(when {
        status?.acknowledged == true -> R.string.report_details
        status?.candidate != null -> R.string.report_retry
        else -> R.string.report_action
    })) }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.report_confirm, state.backend.domain)) },
        text = { Column {
            if (status?.failed == true) Text(stringResource(R.string.report_uncertain, state.backend.domain))
            Text(stringResource(R.string.report_explanation))
        } },
        confirmButton = { TextButton(onClick = {
            confirming = false; showingResult = true; state.send(post)
        }) { Text(stringResource(R.string.report_send)) } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.report_cancel)) } })
    if (showingResult) AlertDialog(onDismissRequest = { showingResult = false },
        title = { Text(stringResource(R.string.report_result_title, state.backend.domain)) },
        text = { Text(stringResource(when {
            status?.acknowledged == true -> R.string.report_acknowledged
            status?.failed == true -> R.string.report_uncertain
            else -> R.string.report_sending
        }, state.backend.domain)) },
        confirmButton = { TextButton(enabled = status?.busy != true,
            onClick = { showingResult = false }) { Text(stringResource(R.string.done)) } })
}
