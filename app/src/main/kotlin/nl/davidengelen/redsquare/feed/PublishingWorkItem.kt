package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import nl.davidengelen.redsquare.ui.AppSpacing

/** Saved publishing work keeps its originating node visible beside its content and actions. */
@Composable
internal fun PublishingWorkItem(node: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(AppSpacing.gap),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(AppSpacing.gap), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(node, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
