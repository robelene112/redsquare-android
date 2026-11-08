package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.AppSpacing

/** The same readable writing surface is retained while a signed attempt is read-only. */
@Composable
internal fun PublishingEditor(value: String, onChange: (String) -> Unit, enabled: Boolean, maximumHeight: Dp = 280.dp) {
    val height = maximumHeight.coerceIn(1.dp, 280.dp)
    OutlinedTextField(value = value, onValueChange = onChange, enabled = enabled,
        label = { Text(stringResource(R.string.post_write)) }, textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface),
        shape = RoundedCornerShape(AppSpacing.controlRadius),
        modifier = Modifier.fillMaxWidth().heightIn(min = minOf(160.dp, height), max = height))
}
