package nl.davidengelen.redsquare.stack

import nl.davidengelen.redsquare.ui.AppSpacing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Approved Stack block controls; compact Follow and source-code controls keep their own treatment. */
internal val StackControlShape = RoundedCornerShape(AppSpacing.controlRadius)

@Composable
internal fun StackBlockButton(onClick: () -> Unit, modifier: Modifier = Modifier, outlined: Boolean = false, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    val target = modifier.fillMaxWidth().heightIn(min = 48.dp)
    val padded: @Composable RowScope.() -> Unit = {
        Box(Modifier.padding(vertical = 6.dp)) { Row(content = content) }
    }
    if (outlined) OutlinedButton(onClick, target, enabled = enabled, shape = StackControlShape, content = padded)
    else Button(onClick, target, enabled = enabled, shape = StackControlShape, content = padded)
}
