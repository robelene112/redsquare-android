package nl.davidengelen.redsquare.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The reference's40dp paint with one explicit48dp keyboard, pointer and accessibility target. */
@Composable
internal fun DesignButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    outlined: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val shape = ButtonDefaults.shape
    val color = if (outlined) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
    val foreground = if (outlined) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
    Box(modifier.defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = 48.dp)
        .onFocusChanged { focused = it.isFocused }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled,
            role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        contentAlignment = Alignment.Center, propagateMinConstraints = true) {
        Surface(modifier = Modifier.clip(shape).indication(interaction, ripple()), shape = shape,
            color = if (enabled) color else if (outlined) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = .12f),
            contentColor = if (enabled) foreground else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
            border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                else if (outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null) {
            Row(Modifier.defaultMinSize(minHeight = 40.dp).padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
            }
        }
    }
}
