package nl.davidengelen.redsquare.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The rounded history/feed selectors from the approved native shell, with real tab semantics. */
@Composable
internal fun DesignTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit,
    trailing: (@Composable () -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                labels.forEachIndexed { index, label ->
                    var focused by remember(label) { mutableStateOf(false) }
                    FilterChip(selected = selected == index, onClick = { onSelect(index) },
                        label = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.Tab }
                            .onFocusChanged { focused = it.isFocused }
                            .border(if (focused) 2.dp else 0.dp,
                                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(8.dp)))
                }
            }
            trailing?.invoke()
        }
    }
}
