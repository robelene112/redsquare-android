package nl.davidengelen.redsquare.feed

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
    TextButton(enabled = status?.busy != true && status?.acknowledged != true,
        onClick = { confirming = true }) { Text(stringResource(R.string.report_action)) }
    if (status?.busy == true) Text(stringResource(R.string.report_sending, state.backend.domain))
    if (status?.acknowledged == true) Text(stringResource(R.string.report_acknowledged, state.backend.domain))
    if (status?.failed == true) Text(stringResource(R.string.report_uncertain, state.backend.domain))
    if (confirming) AlertDialog(onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.report_confirm, state.backend.domain)) },
        text = { Text(stringResource(R.string.report_explanation)) },
        confirmButton = { TextButton(onClick = { confirming = false; state.send(post) }) {
            Text(stringResource(R.string.report_send))
        } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.report_cancel)) } })
}
