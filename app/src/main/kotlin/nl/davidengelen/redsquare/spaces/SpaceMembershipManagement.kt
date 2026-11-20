package nl.davidengelen.redsquare.spaces

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity

@Composable internal fun SpaceMemberRemovalControls(account: SpacesAccount, state: SpacesAccount.State, member: SpaceClient.Member, active: Boolean) {
    val space = state.selected ?: return
    val ownRole = state.members.firstOrNull { it.publicKey == account.ownerKey }?.role
    val eligible = state.membershipSupported && member.publicKey != account.ownerKey && member.role != SpaceRole.OWNER &&
        (ownRole == SpaceRole.OWNER || ownRole == SpaceRole.ADMIN && member.role == SpaceRole.MEMBER)
    if (!eligible) return
    var confirming by rememberSaveable(space, member.publicKey) { mutableStateOf<SpaceClient.MembershipAction?>(null) }
    LaunchedEffect(active) { if (!active) confirming = null }
    val change = state.membershipChange?.takeIf { it.memberKey == member.publicKey }
    val pending = change?.status in setOf(SpaceClient.ChangeStatus.QUEUED, SpaceClient.ChangeStatus.AWAITING_CONFIRMATION)
    ManagementChangeNotice(change?.status, state.availability, account::refresh)
    val enabled = active && state.current?.canManage == true && !state.working && !pending
    OutlinedButton(onClick = { confirming = SpaceClient.MembershipAction.REMOVE }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_remove_member)) }
    TextButton(onClick = { confirming = SpaceClient.MembershipAction.BAN }, enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_ban_member)) }
    confirming?.let { action ->
        val banning = action == SpaceClient.MembershipAction.BAN
        AlertDialog(onDismissRequest = { confirming = null },
            title = { Text(stringResource(if (banning) R.string.spaces_ban_member_title else R.string.spaces_remove_member_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(registeredIdentity(member.publicKey).name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (banning) R.string.spaces_ban_member_detail else R.string.spaces_remove_member_detail))
            } },
            confirmButton = { TextButton(onClick = {
                confirming = null
                account.changeMembership(space, member.publicKey, action)
            }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (banning) R.string.spaces_ban_member else R.string.spaces_remove_member))
            } }, dismissButton = { TextButton(onClick = { confirming = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.spaces_cancel))
            } })
    }
}

@Composable internal fun SpaceLeaveControls(account: SpacesAccount, state: SpacesAccount.State) {
    val current = state.current ?: return
    if (!current.joined || current.dissolved || !state.membershipSupported) return
    val owner = state.members.any { it.publicKey == account.ownerKey && it.role == SpaceRole.OWNER }
    var confirming by rememberSaveable(current.id) { mutableStateOf(false) }
    HorizontalDivider()
    if (owner) Text(stringResource(R.string.spaces_owner_transfer_before_leave), color = MaterialTheme.colorScheme.onSurfaceVariant)
    else {
        ManagementChangeNotice(state.membershipChange?.takeIf { it.action == SpaceClient.MembershipAction.LEAVE }?.status,
            state.availability, account::refresh)
        OutlinedButton(onClick = { confirming = true }, enabled = !current.pending && !state.working,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_leave)) }
    }
    if (confirming && !owner) AlertDialog(onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.spaces_leave_title)) },
        text = { Text(stringResource(R.string.spaces_leave_detail)) },
        confirmButton = { TextButton(onClick = {
            confirming = false
            account.changeMembership(current.id, account.ownerKey, SpaceClient.MembershipAction.LEAVE)
        }, enabled = !current.pending && !state.working, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_leave)) } },
        dismissButton = { TextButton(onClick = { confirming = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_cancel)) } })
}

@Composable internal fun SpaceBanRow(account: SpacesAccount, state: SpacesAccount.State, member: String) {
    val space = state.selected ?: return
    val name = registeredIdentity(member).name
    val liftDescription = stringResource(R.string.spaces_lift_ban_for, name)
    val change = state.membershipChange?.takeIf { it.memberKey == member }
    val pending = change?.status in setOf(SpaceClient.ChangeStatus.QUEUED, SpaceClient.ChangeStatus.AWAITING_CONFIRMATION)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            ManagementChangeNotice(change?.status, state.availability, account::refresh)
            OutlinedButton(onClick = { account.changeMembership(space, member, SpaceClient.MembershipAction.UNBAN) },
                enabled = state.current?.canManage == true && !state.working && !pending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = liftDescription }) { Text(stringResource(R.string.spaces_lift_ban)) }
        }
    }
}
