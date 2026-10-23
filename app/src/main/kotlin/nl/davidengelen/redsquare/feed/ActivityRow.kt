package nl.davidengelen.redsquare.feed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.ui.AppSpacing
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Distinct author and event targets; read feedback only follows a verified local save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActivityRow(author: String, createdAt: Long, caption: String, unread: Boolean?,
    canMark: Boolean, onMark: () -> Unit, onIdentity: () -> Unit, onOpen: () -> Unit) {
    val name = registeredIdentity(author).name
    val timestamp = remember(createdAt) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.ENGLISH).format(Date(createdAt))
    }
    val profileLabel = stringResource(R.string.post_profile_for, name)
    val optionsLabel = stringResource(R.string.activity_options_for, name)
    val openLabel = stringResource(R.string.activity_open_for, name)
    val readLabel = stringResource(when (unread) {
        true -> R.string.activity_unread
        false -> R.string.activity_read
        null -> R.string.activity_read_unknown
    })
    var options by remember { mutableStateOf(false) }
    var identity by rememberSaveable(author, createdAt) { mutableStateOf(false) }
    val background by animateColorAsState(if (unread == true) MaterialTheme.colorScheme.surfaceContainerLow
        else MaterialTheme.colorScheme.surface, tween(AppSpacing.transitionMillis), label = "Activity read status")
    Surface(color = background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = profileLabel, onClick = onIdentity)
                    .semantics { contentDescription = profileLabel },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    GeneratedProfilePicture(author, Modifier.size(32.dp).clip(CircleShape).clearAndSetSemantics { })
                    Text(name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall)
                }
                Box {
                    IconButton(onClick = { options = true }, modifier = Modifier.size(48.dp)
                        .semantics { contentDescription = optionsLabel }) {
                        Icon(painterResource(R.drawable.ic_more), contentDescription = null)
                    }
                    DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.activity_mark_read)) },
                            enabled = canMark, onClick = { options = false; onMark() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.profile_identity_details)) },
                            onClick = { options = false; identity = true })
                    }
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
                .padding(bottom = AppSpacing.small)) {
                Text(caption, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(timestamp, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(readLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    if (identity) ProfileIdentityDialog(author) { identity = false }
}
