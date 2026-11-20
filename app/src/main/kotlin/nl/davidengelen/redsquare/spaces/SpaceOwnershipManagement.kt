package nl.davidengelen.redsquare.spaces

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity

@Composable internal fun SpaceOfferOwnershipButton(account: SpacesAccount, state: SpacesAccount.State,
    member: SpaceClient.Member, active: Boolean) {
    val space = state.selected ?: return
    val owner = state.members.any { it.publicKey == account.ownerKey && it.role == SpaceRole.OWNER }
    if (!state.ownershipSupported || !owner || member.publicKey == account.ownerKey) return
    var confirming by rememberSaveable(space, member.publicKey, account.ownerKey, account.backend.webUrl) { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) confirming = false }
    val enabled = active && state.current?.canManage == true && !state.working && state.ownershipOffer == null
    ManagementChangeNotice(state.ownershipChange?.takeIf { it.memberKey == member.publicKey }?.status, state.availability, account::refresh)
    OutlinedButton(onClick = { confirming = true }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_offer_ownership)) }
    if (confirming && active) AlertDialog(onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.spaces_transfer_ownership)) },
        text = { OwnershipConfirmationBody {
            Text(registeredIdentity(member.publicKey).name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.spaces_ownership_offer_detail))
            Text(stringResource(R.string.spaces_ownership_private_access_detail))
        } },
        confirmButton = { TextButton(onClick = {
            confirming = false
            account.changeOwnership(space, SpaceClient.OwnershipAction.OFFER, member = member.publicKey)
        }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_offer_ownership)) } },
        dismissButton = { TextButton(onClick = { confirming = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_cancel)) } })
}

@Composable internal fun SpaceOwnershipOfferControls(account: SpacesAccount, state: SpacesAccount.State) {
    val offer = state.ownershipOffer ?: return
    val current = state.current ?: return
    val owner = state.members.firstOrNull { it.role == SpaceRole.OWNER } ?: return
    val recipient = offer.member == account.ownerKey
    if (!state.ownershipSupported || !current.joined || !recipient && owner.publicKey != account.ownerKey) return
    var confirming by rememberSaveable(current.id, offer.id, account.ownerKey, account.backend.webUrl) { mutableStateOf<SpaceClient.OwnershipAction?>(null) }
    LaunchedEffect(offer.acceptedId) {
        if (offer.acceptedId != null && confirming == SpaceClient.OwnershipAction.ACCEPT) confirming = null
    }
    val enabled = !state.working && !current.pending
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.spaces_transfer_ownership), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(if (recipient) R.string.spaces_ownership_offered_by else R.string.spaces_ownership_offered_to,
                registeredIdentity(if (recipient) owner.publicKey else offer.member).name))
            AnimatedContent(offer.acceptedId != null, label = "ownership-preparation") { preparing ->
                Text(stringResource(if (preparing) R.string.spaces_ownership_preparing else R.string.spaces_ownership_awaiting_acceptance),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ManagementChangeNotice(state.ownershipChange?.status, state.availability, account::refresh)
            if (recipient && offer.acceptedId == null) Button(onClick = { confirming = SpaceClient.OwnershipAction.ACCEPT },
                enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_accept_ownership)) }
            OutlinedButton(onClick = { confirming = if (recipient) SpaceClient.OwnershipAction.DECLINE else SpaceClient.OwnershipAction.CANCEL },
                enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (recipient) R.string.spaces_decline_ownership else R.string.spaces_cancel_ownership_offer))
            }
        }
    }
    confirming?.let { action ->
        val accepting = action == SpaceClient.OwnershipAction.ACCEPT
        val label = when (action) {
            SpaceClient.OwnershipAction.ACCEPT -> R.string.spaces_accept_ownership
            SpaceClient.OwnershipAction.DECLINE -> R.string.spaces_decline_ownership
            else -> R.string.spaces_cancel_ownership_offer
        }
        AlertDialog(onDismissRequest = { confirming = null },
            title = { Text(stringResource(label)) },
            text = { OwnershipConfirmationBody {
                Text(stringResource(if (accepting) R.string.spaces_ownership_accept_detail else R.string.spaces_ownership_cancel_detail))
                if (accepting || offer.acceptedId != null) Text(stringResource(R.string.spaces_ownership_received_copies_detail))
            } },
            confirmButton = { TextButton(onClick = {
                confirming = null
                account.changeOwnership(current.id, action, proposalId = offer.id)
            }, enabled = enabled && (!accepting || offer.acceptedId == null), modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(label)) } },
            dismissButton = { TextButton(onClick = { confirming = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_back)) } })
    }
}

@Composable private fun OwnershipConfirmationBody(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
