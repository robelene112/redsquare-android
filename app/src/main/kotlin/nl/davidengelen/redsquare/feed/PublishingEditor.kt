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
import nl.davidengelen.redsquare.ui.CompactWritingField

/** The same readable writing surface is retained while a signed attempt is read-only. */
@Composable
internal fun PublishingEditor(value: String, onChange: (String) -> Unit, enabled: Boolean, maximumHeight: Dp = 280.dp,
    modifier: Modifier = Modifier, compact: Boolean = false) {
    val height = maximumHeight.coerceIn(1.dp, 280.dp)
    val field = modifier.fillMaxWidth().heightIn(min = minOf(160.dp, height), max = height)
    val label = stringResource(R.string.post_write)
    if (compact) CompactWritingField(value, onChange, enabled, label, field, maxLines = Int.MAX_VALUE)
    else OutlinedTextField(value = value, onValueChange = onChange, enabled = enabled,
        label = { Text(label) }, textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface),
        shape = RoundedCornerShape(AppSpacing.controlRadius), modifier = field)
}
