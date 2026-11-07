package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone
import nl.davidengelen.redsquare.ui.DesignTabs
import nl.davidengelen.redsquare.ui.FrameIconButton

/** Public or unsent appearance, rendered with the same identity hierarchy. */
internal data class ProfileAppearance(val picture: String?, val banner: String?, val bio: String, val verified: nl.davidengelen.redsquare.saito.SaitoProfile? = null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileIdentityHeader(author: String, backend: SaitoBackend, appearance: ProfileAppearance,
    onDetails: () -> Unit, primaryAction: @Composable () -> Unit = {}) {
    val identity = registeredIdentity(author)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val banner = Modifier.fillMaxWidth().height(128.dp)
            .clip(RoundedCornerShape(AppSpacing.cardRadius)).testTag("profile-cover")
        ProfileImage(appearance.banner, ProfileImageKind.BANNER, author, backend, banner)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val picture = Modifier.size(64.dp).testTag("profile-avatar").clip(CircleShape)
            if (appearance.verified != null) ProfileAvatar(author, picture, appearance.verified, showRetry = true)
            else ProfileImage(appearance.picture, ProfileImageKind.PICTURE, author, backend, picture)
            Spacer(Modifier.weight(1f))
            primaryAction()
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(identity.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                Text("${author.take(8)}…${author.takeLast(6)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FrameIconButton(R.drawable.ic_info, stringResource(R.string.profile_identity_details), onClick = onDetails)
        }
        InlineStatus(if (identity.saveError) stringResource(R.string.identity_name_save_error) else null, StatusTone.WARNING)
        if (appearance.bio.isNotEmpty()) Text(appearance.bio, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ProfileHistoryTabs(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    DesignTabs(ProfileTab.entries.map { stringResource(it.label) }, selected.ordinal,
        onSelect = { onSelect(ProfileTab.entries[it]) })
}
