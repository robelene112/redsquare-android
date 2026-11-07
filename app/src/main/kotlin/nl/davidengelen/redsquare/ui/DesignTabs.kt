package nl.davidengelen.redsquare.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Reference chips in full48dp targets; the strip scrolls without crowding out reading content. */
@Composable
internal fun DesignTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit,
    trailing: (@Composable () -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                labels.forEachIndexed { index, label ->
                    var focused by remember(label) { mutableStateOf(false) }
                    val interaction = remember(label) { MutableInteractionSource() }
                    val chosen = selected == index
                    val color by animateColorAsState(if (chosen) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface, tween(AppSpacing.transitionMillis), label = "History selection")
                    val shape = RoundedCornerShape(8.dp)
                    Box(Modifier.heightIn(min = 48.dp).onFocusChanged { focused = it.isFocused }
                        .selectable(chosen, interactionSource = interaction, indication = null, role = Role.Tab,
                            onClick = { onSelect(index) }),
                        contentAlignment = Alignment.Center) {
                        Surface(Modifier.clip(shape).indication(interaction, ripple()), shape = shape, color = color,
                            contentColor = if (chosen) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                else if (!chosen) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null) {
                            Box(Modifier.heightIn(min = 32.dp).padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
            }
            trailing?.invoke()
        }
    }
}
