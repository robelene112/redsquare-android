package nl.davidengelen.redsquare.feed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

/** Public or unsent appearance, rendered with the same identity hierarchy. */
internal data class ProfileAppearance(val picture: String?, val banner: String?, val bio: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileIdentityHeader(author: String, backend: SaitoBackend, appearance: ProfileAppearance,
    onDetails: () -> Unit, primaryAction: @Composable () -> Unit = {}) {
    val identity = registeredIdentity(author)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val banner = Modifier.fillMaxWidth().aspectRatio(3f).clip(RoundedCornerShape(16.dp))
            if (appearance.banner != null) ProfileImage(appearance.banner, ProfileImageKind.BANNER, author, backend, banner)
            else Box(banner.background(Brush.linearGradient(listOf(
                MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHigh))))
            Row(Modifier.fillMaxWidth().padding(top = maxWidth / 3 - 24.dp),
                verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                ProfileImage(appearance.picture, ProfileImageKind.PICTURE, author, backend,
                    Modifier.size(80.dp).border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(4.dp).clip(CircleShape))
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(top = 24.dp)) { primaryAction() }
            }
        }
        Text(identity.name, style = MaterialTheme.typography.headlineSmall)
        InlineStatus(if (identity.saveError) stringResource(R.string.identity_name_save_error) else null, StatusTone.WARNING)
        FlowRow(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text("${author.take(8)}…${author.takeLast(6)}", Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onDetails, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.profile_identity_details))
            }
        }
        if (appearance.bio.isNotEmpty()) Text(appearance.bio, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun ProfileIdentityDialog(author: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val name = registeredIdentity(author).name
    var copied by remember(author) { mutableStateOf<Boolean?>(null) }
    AlertDialog(onDismissRequest = onDone,
        title = { Text(stringResource(R.string.profile_identity_details)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(author, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                InlineStatus(when (copied) {
                    true -> stringResource(R.string.profile_identity_copied)
                    false -> stringResource(R.string.profile_identity_copy_failed)
                    null -> null
                }, if (copied == false) StatusTone.ERROR else StatusTone.SUCCESS)
            }
        },
        confirmButton = { TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.done))
        } },
        dismissButton = { TextButton(onClick = {
            copied = runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Saito public key", author))
            }.isSuccess
        }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(painterResource(R.drawable.ic_copy), contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpacing.small))
            Text(stringResource(R.string.profile_identity_copy))
        } })
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
