package nl.davidengelen.redsquare.spaces

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

@Composable internal fun SpaceRestrictionControls(account: SpacesAccount, state: SpacesAccount.State,
    member: SpaceClient.Member, active: Boolean) {
    val space = state.selected ?: return
    val role = state.members.firstOrNull { it.publicKey == account.ownerKey }?.role
    if (member.publicKey == account.ownerKey || member.role == SpaceRole.OWNER || !account.writeRestrictionsSupported() ||
        !(role == SpaceRole.OWNER || role == SpaceRole.ADMIN && member.role == SpaceRole.MEMBER)) return
    key(account, space, member.publicKey) {
        val scope = rememberCoroutineScope()
        var choices by remember { mutableStateOf<SpaceRestrictionOptions?>(null) }
        var working by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var scopeMenu by remember { mutableStateOf(false) }
        var durationMenu by remember { mutableStateOf(false) }
        var channel by remember { mutableStateOf<String?>(null) }
        var until by remember { mutableStateOf<Long?>(null) }
        val whole = stringResource(R.string.spaces_restriction_whole)
        val unavailableChannel = stringResource(R.string.spaces_restriction_unavailable_channel)
        val names = state.channels.associate { it.id to it.profile.name }
        fun scopeName(channelId: String?) = if (channelId == null) whole else names[channelId] ?: unavailableChannel

        suspend fun update(operation: suspend () -> SpaceRestrictionOptions) {
            if (working) return
            working = true
            failed = false
            try { choices = operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { working = false }
        }

        LaunchedEffect(active) {
            if (active) update { account.restrictionOptions(space, member.publicKey) }
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.spaces_restriction_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.spaces_restriction_reading), style = MaterialTheme.typography.bodySmall)
            if (failed) {
                InlineStatus(stringResource(R.string.spaces_restriction_failed), StatusTone.WARNING)
                if (choices == null) TextButton(onClick = { scope.launch { update { account.restrictionOptions(space, member.publicKey) } } },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_report_retry)) }
            }
            if (choices == null && working) LinearProgressIndicator(Modifier.fillMaxWidth())
            choices?.let { options ->
                options.active.forEach { existing ->
                    val target = scopeName(existing.channel)
                    Text(stringResource(R.string.spaces_restriction_active, target, moderationDate(existing.expiresAt)),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { scope.launch {
                        update { account.setMemberWritingRestriction(space, member.publicKey, existing.channel, null) }
                    } }, enabled = active && !working, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.spaces_restriction_lift, target))
                    }
                }
                Box {
                    OutlinedButton(onClick = { scopeMenu = true }, enabled = active && !working,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.spaces_restriction_scope, scopeName(channel)))
                    }
                    DropdownMenu(expanded = scopeMenu, onDismissRequest = { scopeMenu = false }) {
                        DropdownMenuItem(text = { Text(whole) }, onClick = { channel = null; scopeMenu = false })
                        state.channels.filter { it.id in options.channels }.forEach { choice ->
                            DropdownMenuItem(text = { Text(choice.profile.name) }, onClick = { channel = choice.id; scopeMenu = false })
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { durationMenu = true }, enabled = active && !working,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(until?.let { stringResource(R.string.spaces_restriction_end, moderationDate(it)) }
                            ?: stringResource(R.string.spaces_restriction_choose_duration))
                    }
                    DropdownMenu(expanded = durationMenu, onDismissRequest = { durationMenu = false }) {
                        listOf(R.string.spaces_restriction_five_minutes to 300_000L,
                            R.string.spaces_restriction_one_hour to 3_600_000L,
                            R.string.spaces_restriction_one_day to 86_400_000L).forEach { (label, duration) ->
                            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                until = System.currentTimeMillis() + duration
                                durationMenu = false
                            })
                        }
                    }
                }
                Button(onClick = {
                    val end = until ?: return@Button
                    val selectedChannel = channel
                    scope.launch { update { account.setMemberWritingRestriction(space, member.publicKey, selectedChannel, end) } }
                }, enabled = active && !working && until != null && channel?.let { it in options.channels } != false,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.spaces_restriction_confirm))
                }
            }
        }
    }
}
