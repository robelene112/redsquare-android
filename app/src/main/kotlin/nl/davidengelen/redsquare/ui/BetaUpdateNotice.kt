package nl.davidengelen.redsquare.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import nl.davidengelen.redsquare.R
import androidx.compose.ui.unit.dp

/** One accessible action, with no separate heading/button competing with the feed. */
@Composable
internal fun BetaUpdateNotice(visible: Boolean, onOpen: () -> Unit) {
    AnimatedVisibility(visible,
        enter = expandVertically(tween(AppSpacing.transitionMillis), expandFrom = Alignment.Top) +
            fadeIn(tween(AppSpacing.transitionMillis)),
        exit = shrinkVertically(tween(AppSpacing.transitionMillis), shrinkTowards = Alignment.Top) +
            fadeOut(tween(AppSpacing.transitionMillis))) {
        Surface(onClick = onOpen,
            modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }
                .padding(horizontal = AppSpacing.screen)
                .padding(bottom = AppSpacing.small),
            shape = RoundedCornerShape(AppSpacing.cardRadius),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.preview_update_available),
                        style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.view_update),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
                Icon(painterResource(R.drawable.ic_forward), contentDescription = null,
                    modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
