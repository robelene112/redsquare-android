package nl.davidengelen.redsquare.spaces

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.ProfileAvatar
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.FrameIconButton

@Composable internal fun SpaceMembersPage(account: SpacesAccount, state: SpacesAccount.State,
    modifier: Modifier = Modifier, onBack: () -> Unit, onProfile: (String) -> Unit, onMessage: (String) -> Unit) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FrameIconButton(R.drawable.ic_back, stringResource(R.string.spaces_back)) { onBack() }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(state.current?.name.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.spaces_members), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.members.isEmpty()) item { Text(stringResource(R.string.spaces_loading_members), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.members, key = { it.publicKey }) { member ->
                val identity = registeredIdentity(member.publicKey).name
                Surface(onClick = { onProfile(member.publicKey) }, shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProfileAvatar(member.publicKey, Modifier.size(40.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(identity, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(when (member.role) {
                                SpaceRole.OWNER -> R.string.spaces_role_owner
                                SpaceRole.ADMIN -> R.string.spaces_role_admin
                                SpaceRole.MEMBER -> R.string.spaces_role_member
                            }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (member.publicKey != account.ownerKey) IconButton(onClick = { onMessage(member.publicKey) }, modifier = Modifier.size(48.dp)) {
                            Icon(painterResource(R.drawable.ic_chat), stringResource(R.string.spaces_message_member, identity), Modifier.size(22.dp))
                        } else Icon(painterResource(R.drawable.ic_forward), null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
