package nl.davidengelen.redsquare.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R

internal enum class StatusTone { INFO, WORKING, SUCCESS, WARNING, ERROR }

/** Only the stable summary is live. Details and progress never repeat polling announcements. */
@Composable
internal fun InlineStatus(summary: String?, tone: StatusTone = StatusTone.INFO,
    details: List<String> = emptyList(), modifier: Modifier = Modifier,
    actionLabel: String? = null, actionEnabled: Boolean = true, onAction: () -> Unit = {}) {
    var openedDetails by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var previousSummary by remember { mutableStateOf(summary.orEmpty()) }
    SideEffect { if (summary != null) previousSummary = summary }
    val renderedSummary = summary ?: previousSummary
    val stackedActions = LocalDensity.current.fontScale > 1.3f
    val actions: @Composable RowScope.() -> Unit = {
        if (details.isNotEmpty()) TextButton(enabled = summary != null,
            onClick = { openedDetails = renderedSummary to details.toList() }) {
            Text(stringResource(R.string.status_details), style = MaterialTheme.typography.labelMedium)
        }
        if (actionLabel != null) TextButton(enabled = summary != null && actionEnabled, onClick = onAction) {
            Text(actionLabel, style = MaterialTheme.typography.labelMedium)
        }
    }
    val color = when (tone) {
        StatusTone.ERROR -> MaterialTheme.colorScheme.error
        StatusTone.SUCCESS -> androidx.compose.ui.graphics.Color(0xff168039)
        StatusTone.WARNING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    AnimatedVisibility(visible = summary != null,
        enter = expandVertically(tween(AppSpacing.transitionMillis), expandFrom = Alignment.Top),
        exit = shrinkVertically(tween(AppSpacing.transitionMillis), shrinkTowards = Alignment.Top)) {
        Surface(modifier.fillMaxWidth().padding(vertical = 3.dp)
            .animateContentSize(tween(AppSpacing.transitionMillis))
            .semantics { if (summary == null) hideFromAccessibility() },
            shape = RoundedCornerShape(AppSpacing.small), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                Row(Modifier.padding(start = AppSpacing.small, end = 4.dp).heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (tone == StatusTone.WORKING) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    else Box(Modifier.size(8.dp).background(color, CircleShape))
                    Text(renderedSummary, Modifier.weight(1f).padding(horizontal = AppSpacing.small, vertical = 6.dp)
                        .semantics { if (summary != null) liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.labelMedium)
                    if (!stackedActions) actions()
                }
                if (stackedActions && (details.isNotEmpty() || actionLabel != null))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.End,
                        content = actions)
            }
        }
    }
    openedDetails?.let { (title, explanations) ->
        AlertDialog(onDismissRequest = { openedDetails = null },
            title = { Text(title) }, text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    explanations.distinct().forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }, confirmButton = { TextButton(onClick = { openedDetails = null }) { Text(stringResource(R.string.done)) } })
    }
}
