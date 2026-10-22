package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.AppSpacing

@Composable
internal fun SearchSectionTitle(title: String) {
    Text(title, Modifier.fillMaxWidth().padding(top = AppSpacing.gap, bottom = AppSpacing.small).semantics { heading() },
        style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun SearchEmptyResult(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(vertical = AppSpacing.small),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Profile navigation and exact identity have separate, non-overlapping targets. */
@Composable
internal fun SearchPersonResult(key: String, name: String, cachedName: Boolean,
    onProfile: () -> Unit, onDetails: () -> Unit) {
    val profileLabel = stringResource(R.string.post_profile_for, name)
    Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.small)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Row(Modifier.weight(1f).heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = profileLabel, onClick = onProfile)
                .semantics { contentDescription = profileLabel },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                GeneratedProfilePicture(key, Modifier.size(40.dp).clip(CircleShape).clearAndSetSemantics { })
                Column(Modifier.weight(1f)) {
                    Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text("${key.take(8)}…${key.takeLast(6)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDetails, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_info), stringResource(R.string.search_identity_details, name))
            }
        }
        if (cachedName) Text(stringResource(R.string.search_cached_name), Modifier.padding(top = AppSpacing.small),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SearchCoverageDialog(onDone: () -> Unit) {
    AlertDialog(onDismissRequest = onDone, title = { Text(stringResource(R.string.status_search_coverage)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
            Text(stringResource(R.string.search_coverage))
            Text(stringResource(R.string.search_people_coverage))
        } }, confirmButton = { TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.done))
        } })
}
