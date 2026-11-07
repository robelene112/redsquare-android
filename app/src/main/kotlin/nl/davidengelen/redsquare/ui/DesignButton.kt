package nl.davidengelen.redsquare.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.Layout
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
    val colors = if (outlined) ButtonDefaults.outlinedButtonColors() else ButtonDefaults.buttonColors()
    Layout(modifier = modifier.defaultMinSize(minWidth = ButtonDefaults.MinWidth, minHeight = 48.dp)
        .onFocusChanged { focused = it.isFocused }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled,
            role = Role.Button, onClick = onClick), content = {
        Surface(modifier = Modifier.clip(shape).indication(interaction, ripple()), shape = shape,
            color = if (enabled) colors.containerColor else colors.disabledContainerColor,
            contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
            border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                else if (outlined) ButtonDefaults.outlinedButtonBorder(enabled) else null) {
            Row(Modifier.defaultMinSize(minHeight = 40.dp).padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
            }
        }
    }) { children, constraints ->
        // Preserve a weighted button's width, but do not force its painted height
        // to equal the touch target or add spare padding when large text grows.
        val paint = children.single().measure(constraints.copy(minHeight = 0))
        val height = maxOf(constraints.minHeight, paint.height)
        layout(paint.width, height) { paint.placeRelative(0, (height - paint.height) / 2) }
    }
}
