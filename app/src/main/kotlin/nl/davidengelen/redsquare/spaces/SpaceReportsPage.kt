package nl.davidengelen.redsquare.spaces

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.DesignSheet
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable internal fun SpaceReportsPage(account: SpacesAccount, state: SpacesAccount.State,
    modifier: Modifier = Modifier, onBack: () -> Unit) {
    val space = state.selected ?: return
    val staff = account.canReviewModeration(space, state)
    var denied by remember(account, space, staff) { mutableStateOf(false) }
    var logOpen by remember(account, space, staff) { mutableStateOf(false) }
    var review by remember(account, space, staff) { mutableStateOf<SpaceMessageReport?>(null) }
    val dropStaffHistory = { review = null; denied = true }
    BackHandler(logOpen) { logOpen = false }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FrameIconButton(R.drawable.ic_back, stringResource(R.string.spaces_back)) {
                if (logOpen) logOpen = false else onBack()
            }
            Text(stringResource(if (logOpen) R.string.spaces_admin_log else R.string.spaces_reports), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() })
        }
        if (staff && !denied) key(account, space, logOpen) {
            if (logOpen) StaffHistory(
                loadPage = { before: SpaceAdminCursor? -> account.adminLog(space, before).let { it.events to it.next } },
                onDenied = dropStaffHistory, rowKey = { it.cursor.event }, empty = R.string.spaces_admin_log_empty,
                failure = R.string.spaces_admin_log_failed, more = R.string.spaces_admin_log_more,
            ) { event ->
                val actor = registeredIdentity(event.actor).name
                val member = event.member?.let { registeredIdentity(it).name }
                val channel = state.channels.firstOrNull { it.id == event.channel }?.profile?.name
                val time = moderationDate(event.cursor.at)
                HistoryCard(stringResource(adminAction(event.action)), listOfNotNull(actor, channel, time).joinToString(" · "), member)
            } else StaffHistory(
                loadPage = { before: Long? -> account.messageReports(space, before ?: Long.MAX_VALUE).let { it.reports to it.next } },
                onDenied = dropStaffHistory, rowKey = { it.id }, empty = R.string.spaces_reports_empty,
                failure = R.string.spaces_reports_load_failed, more = R.string.spaces_reports_more,
                header = { OutlinedButton(onClick = { logOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.spaces_view_admin_log))
                } },
            ) { report ->
                val name = registeredIdentity(report.reporter).name
                val channel = state.channels.firstOrNull { it.id == report.channel }?.profile?.name
                HistoryCard(stringResource(R.string.spaces_report_author, name),
                    listOfNotNull(channel, moderationDate(report.receivedAt)).joinToString(" · "), report.reason,
                    onReview = { review = report })
            }
        }
        else Box(Modifier.padding(AppSpacing.screen)) {
            InlineStatus(stringResource(R.string.spaces_reports_staff_only), StatusTone.INFO)
        }
    }
    if (staff && !denied) review?.let { item ->
        key(item.id) {
            ReviewReportSheet(item, load = { account.reviewReportedMessage(space, item) },
                onDenied = dropStaffHistory, onClose = { review = null })
        }
    }
}

@Composable private fun ReviewReportSheet(report: SpaceMessageReport, load: suspend () -> SpaceReportedMessage?,
    onDenied: () -> Unit, onClose: () -> Unit) {
    var message by remember { mutableStateOf<SpaceReportedMessage?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(report.id, attempt) {
        loaded = false
        failed = false
        try {
            message = load()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SpaceClient.ReadDenied) { onDenied() }
        catch (_: Exception) { failed = true }
        finally { loaded = true }
    }
    DesignSheet(true, stringResource(R.string.spaces_review_report), onDismiss = onClose, contentKey = report.id,
        footer = { first ->
            Button(onClick = onClose, modifier = first.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.spaces_close))
            }
        }) { first ->
        HistoryCard(stringResource(R.string.spaces_report_reason), moderationDate(report.receivedAt), report.reason)
        Text(stringResource(R.string.spaces_reported_message), first, style = MaterialTheme.typography.titleMedium)
        val entry = message
        when {
            !loaded -> LinearProgressIndicator(Modifier.fillMaxWidth())
            failed -> {
                InlineStatus(stringResource(R.string.spaces_report_message_load_failed), StatusTone.WARNING)
                TextButton(onClick = { attempt++ }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.spaces_report_retry))
                }
            }
            entry != null -> {
                Text(entry.original ?: stringResource(R.string.spaces_report_message_unavailable))
                if (entry.current.deleted) Text(stringResource(R.string.spaces_report_message_removed))
                else if (entry.current.edited) HistoryCard(stringResource(R.string.spaces_report_message_edited),
                    moderationDate(entry.current.editedAt ?: entry.current.createdAt),
                    entry.current.text ?: stringResource(R.string.spaces_report_message_unavailable))
            }
            else -> Text(stringResource(R.string.spaces_report_message_unavailable))
        }
    }
}

@Composable private fun <T, Cursor> StaffHistory(loadPage: suspend (Cursor?) -> Pair<List<T>, Cursor?>,
    onDenied: () -> Unit, rowKey: (T) -> String, empty: Int, failure: Int, more: Int, header: (@Composable () -> Unit)? = null,
    row: @Composable (T) -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<T>?>(null) }
    var cursor by remember { mutableStateOf<Cursor?>(null) }
    var currentPage by remember { mutableStateOf<Cursor?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var failedPage by remember { mutableStateOf<Cursor?>(null) }
    suspend fun load(before: Cursor? = null) {
        if (loading) return
        loading = true
        failed = false
        try {
            val (page, next) = loadPage(before)
            rows = page
            cursor = next
            currentPage = before
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SpaceClient.ReadDenied) {
            rows = null
            cursor = null
            currentPage = null
            failedPage = null
            onDenied()
        }
        catch (_: Exception) {
            failedPage = before
            failed = true
        }
        finally { loading = false }
    }
    LaunchedEffect(Unit) { load() }
    LazyColumn(contentPadding = PaddingValues(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        header?.let { content -> item { content() } }
        if (loading && rows == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (failed) item {
            InlineStatus(stringResource(failure), StatusTone.WARNING)
            TextButton(onClick = { scope.launch { load(failedPage) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.spaces_report_retry))
            }
        }
        if (rows?.isEmpty() == true) item { Text(stringResource(empty)) }
        if (currentPage != null) item {
            OutlinedButton(onClick = { scope.launch { load() } }, enabled = !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_moderation_latest)) }
        }
        items(rows.orEmpty(), key = rowKey) { row(it) }
        cursor?.let { next -> item {
            OutlinedButton(onClick = { scope.launch { load(next) } }, enabled = !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(more)) }
        } }
    }
}

@Composable private fun HistoryCard(title: String, metadata: String, detail: String?, onReview: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            onReview?.let {
                TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.spaces_review_report))
                }
            }
        }
    }
}

@Composable internal fun moderationDate(time: Long): String = remember(time) {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
}

private fun adminAction(action: String): Int = when (action) {
    "made-admin" -> R.string.spaces_admin_made_admin
    "removed-admin" -> R.string.spaces_admin_removed_admin
    "remove-member" -> R.string.spaces_admin_removed_member
    "ban-member" -> R.string.spaces_admin_banned_member
    "unban-member" -> R.string.spaces_admin_unbanned_member
    "paused-writing" -> R.string.spaces_admin_paused_writing
    "resumed-writing" -> R.string.spaces_admin_resumed_writing
    "create-channel" -> R.string.spaces_admin_created_channel
    "edit-channel" -> R.string.spaces_admin_edited_channel
    "archived-channel" -> R.string.spaces_admin_archived_channel
    "reopened-channel" -> R.string.spaces_admin_reopened_channel
    "grant-channel-access", "revoke-channel-access", "rotate-access" -> R.string.spaces_admin_changed_access
    "message-delete" -> R.string.spaces_admin_removed_content
    "message-pin" -> R.string.spaces_admin_changed_pin
    "approved-channel-access" -> R.string.spaces_admin_approved_channel_access
    "approved-device-replacement" -> R.string.spaces_admin_approved_device_replacement
    "join-public" -> R.string.spaces_admin_joined_public
    "muted-in-room" -> R.string.spaces_admin_muted_in_room
    "removed-from-room" -> R.string.spaces_admin_removed_from_room
    "join", "join-channel" -> R.string.spaces_admin_join_requested
    else -> R.string.spaces_admin_updated_space
}
