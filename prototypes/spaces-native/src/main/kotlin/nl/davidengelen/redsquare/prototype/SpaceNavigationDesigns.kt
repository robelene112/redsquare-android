package nl.davidengelen.redsquare.prototype

// Five native alternatives for the same navigation question. Community content is shared.
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R

internal enum class NavigationDesign(val letter: String, val title: String, val detail: String) {
    SPACE_SWITCHER("A", "Space switcher", "Tap the community name in the header to browse its channels."),
    CHANNEL_TITLE("B", "Channel title", "The current channel becomes the menu, with the Space above it."),
    BOTTOM_DOCK("C", "Bottom dock", "A wide Channels control sits just above the bottom navigation."),
    FLOATING_BUTTON("D", "Floating button", "A floating Channels button stays within reach above the composer."),
    SPACE_RAIL("E", "Space rail", "A slim left rail keeps communities and the channel menu beside the conversation.");

    val choice get() = "$letter · $title"
}

private fun menuModifier() = Modifier.testTag("channels-menu").semantics {
    contentDescription = "Switch Space or channel"
    role = Role.Button
}

@Composable
internal fun NavigationDesignPicker(selected: NavigationDesign, onChoose: (NavigationDesign) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Compare navigation") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Five alternatives. Your conversation and drafts stay in place.", style = MaterialTheme.typography.bodySmall)
                NavigationDesign.entries.forEach { design ->
                    Surface(shape = RoundedCornerShape(14.dp),
                        color = if (selected == design) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                        Column(Modifier.fillMaxWidth().selectable(selected == design, role = Role.RadioButton,
                            onClick = { onChoose(design) }).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(design.choice, style = MaterialTheme.typography.titleSmall)
                            Text(design.detail, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
internal fun SpaceSwitcherDesign(space: String, onOpen: () -> Unit) {
    FilledTonalButton(onClick = onOpen, modifier = menuModifier().fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(12.dp)) {
        Avatar(space.split(" ").take(2).map { it.firstOrNull() ?: 'S' }.joinToString(""), Color(0xffa35b43), 32)
        Spacer(Modifier.width(12.dp))
        Text(space, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Glyph(R.drawable.ic_chevron_down, 20)
    }
}

@Composable
internal fun ChannelTitleDesign(space: String, channel: String, onOpen: () -> Unit) {
    Surface(onClick = onOpen, modifier = menuModifier().fillMaxWidth(),
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(space, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(channel, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Glyph(R.drawable.ic_chevron_down, 20)
        }
    }
}

@Composable
internal fun BottomDockDesign(space: String, onOpen: () -> Unit) {
    FilledTonalButton(onClick = onOpen,
        modifier = menuModifier().fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        Glyph(R.drawable.ic_spaces_channels)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Channels", style = MaterialTheme.typography.labelLarge)
            Text(space, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Glyph(R.drawable.ic_chevron_down, 18)
    }
}

@Composable
internal fun FloatingButtonDesign(onOpen: () -> Unit) {
    ExtendedFloatingActionButton(onClick = onOpen, modifier = menuModifier(),
        icon = { Glyph(R.drawable.ic_spaces_channels) }, text = { Text("Channels") },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
}

@Composable
internal fun SpaceRailDesign(names: List<String>, selected: String, onOpen: () -> Unit, onSpace: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.width(64.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onOpen, modifier = menuModifier().size(48.dp)) { Glyph(R.drawable.ic_spaces_channels) }
            HorizontalDivider(Modifier.width(32.dp))
            names.forEach { name ->
                Surface(onClick = { onSpace(name) }, shape = CircleShape,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Open $name"; role = Role.Button },
                    color = Color.Transparent,
                    border = if (name == selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                    Box(contentAlignment = Alignment.Center) {
                        Avatar(name.split(" ").take(2).map { it.firstOrNull() ?: 'S' }.joinToString(""),
                            if (name == "Saito Builders") Color(0xff487e82) else Color(0xffa35b43), 36)
                    }
                }
            }
        }
    }
}
