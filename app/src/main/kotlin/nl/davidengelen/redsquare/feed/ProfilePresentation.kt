package nl.davidengelen.redsquare.feed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

/** Public or unsent appearance, rendered with the same identity hierarchy. */
internal data class ProfileAppearance(val picture: String?, val banner: String?, val bio: String, val verified: nl.davidengelen.redsquare.saito.SaitoProfile? = null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileIdentityHeader(author: String, backend: SaitoBackend, appearance: ProfileAppearance,
    onDetails: () -> Unit, primaryAction: @Composable () -> Unit = {}) {
    val identity = registeredIdentity(author)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val banner = Modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(16.dp)).testTag("profile-cover")
            ProfileImage(appearance.banner, ProfileImageKind.BANNER, author, backend, banner)
            Row(Modifier.fillMaxWidth().padding(top = maxWidth / 3 - 24.dp),
                verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                val picture = Modifier.size(80.dp).testTag("profile-avatar").border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(4.dp).clip(CircleShape)
                if (appearance.verified != null) ProfileAvatar(author, picture, appearance.verified, showRetry = true)
                else ProfileImage(appearance.picture, ProfileImageKind.PICTURE, author, backend, picture)
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(top = 24.dp)) { primaryAction() }
            }
        }
        Text(identity.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        InlineStatus(if (identity.saveError) stringResource(R.string.identity_name_save_error) else null, StatusTone.WARNING)
        FlowRow(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text("${author.take(8)}…${author.takeLast(6)}", Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onDetails, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.profile_identity_details))
            }
        }
        if (appearance.bio.isNotEmpty()) Text(appearance.bio, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun ProfileHistoryTabs(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val scroll = rememberScrollState()
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().let { if (largeText) it.horizontalScroll(scroll) else it }.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ProfileTab.entries.forEach { tab ->
                var focused by remember(tab) { mutableStateOf(false) }
                val active = tab == selected
                val indicator by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                    tween(AppSpacing.transitionMillis), label = "Profile history selection")
                val width = if (largeText) Modifier.widthIn(min = 96.dp) else Modifier.weight(1f)
                Column(width.onFocusChanged { focused = it.isFocused }
                    .clip(RoundedCornerShape(8.dp))
                    .border(if (focused) 2.dp else 0.dp,
                        if (focused) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(8.dp))
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(tab) })
                    .heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(tab.label), style = MaterialTheme.typography.labelLarge,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.width(24.dp).height(3.dp).clip(CircleShape).background(indicator))
                }
            }
        }
    }
}
