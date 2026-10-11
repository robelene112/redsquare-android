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
    LaunchedEffect(state, post.id) { state.load(post) }
    val status = state.statuses[post.id]
    var confirming by rememberSaveable(post.id) { mutableStateOf(false) }
    var showingResult by rememberSaveable(post.id) { mutableStateOf(false) }
    TextButton(enabled = (status?.ready == true || status?.failed == true) && status?.busy != true, onClick = {
        if (status?.acknowledged == true || status?.ready != true) showingResult = true else confirming = true
    }) { Text(stringResource(when {
        status?.acknowledged == true -> R.string.report_details
        status?.candidate != null -> R.string.report_retry
        else -> R.string.report_action
    })) }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.report_confirm, state.backend.domain)) },
        text = { Column {
            if (status?.failed == true) Text(stringResource(
                when {
                    status.backendError -> R.string.report_backend_error
                    status.saveFailed -> R.string.report_save_error
                    !status.attempted -> R.string.report_not_sent
                    else -> R.string.report_uncertain
                }, state.backend.domain))
            Text(stringResource(R.string.report_explanation))
        } },
        confirmButton = { TextButton(onClick = {
            confirming = false; showingResult = true; state.send(post)
        }) { Text(stringResource(R.string.report_send)) } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.report_cancel)) } })
    if (showingResult) AlertDialog(onDismissRequest = { showingResult = false },
        title = { Text(stringResource(R.string.report_result_title, state.backend.domain)) },
        text = { Column { Text(stringResource(when {
            status?.ready != true && status?.failed == true -> R.string.report_load_error
            status?.ready != true -> R.string.report_loading
            status?.acknowledged == true -> R.string.report_acknowledged
            status?.backendError == true -> R.string.report_backend_error
            status?.saveFailed == true -> R.string.report_save_error
            status?.failed == true && !status.attempted -> R.string.report_not_sent
            status?.failed == true -> R.string.report_uncertain
            else -> R.string.report_sending
        }, state.backend.domain))
            if ((status?.acknowledged == true || status?.backendError == true) && status.saveFailed)
                Text(stringResource(R.string.report_receipt_save_error))
        } },
        confirmButton = { Column {
            if (status?.ready != true && status?.failed == true) TextButton(onClick = { state.retryLoad(post) }) {
                Text(stringResource(R.string.retry))
            }
            TextButton(enabled = status?.busy != true,
                onClick = { showingResult = false }) { Text(stringResource(R.string.done)) }
        } })
}
