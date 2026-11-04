package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.AppSpacing

/** The same readable writing surface is retained while a signed attempt is read-only. */
@Composable
internal fun PublishingEditor(value: String, onChange: (String) -> Unit, enabled: Boolean, maximumHeight: Dp = 280.dp) {
    val height = maximumHeight.coerceIn(1.dp, 280.dp)
    TextField(value = value, onValueChange = onChange, enabled = enabled,
        placeholder = { Text(stringResource(R.string.post_write)) }, textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent),
        shape = RoundedCornerShape(AppSpacing.gap),
        modifier = Modifier.fillMaxWidth().heightIn(min = minOf(160.dp, height), max = height))
}
