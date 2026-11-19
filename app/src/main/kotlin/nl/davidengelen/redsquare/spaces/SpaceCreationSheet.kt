package nl.davidengelen.redsquare.spaces

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.PostImagePreparation
import nl.davidengelen.redsquare.ui.DesignSheet

/** Editable view data permits temporarily blank fields; validated domain values
 * are constructed on the account's IO worker only after Create. */
internal data class SpaceCreation(val name: String, val description: String, val visibility: SpaceVisibility,
    val listed: Boolean, val houseRules: String, val image: String?, val channels: List<StarterChannel>)
internal data class StarterChannel(val id: Int, val name: String, val kind: SpaceChannelKind, val category: String = "")

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SpaceCreationSheet(open: Boolean, account: SpacesAccount, state: SpacesAccount.State, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var privateSpace by rememberSaveable { mutableStateOf(true) }
    var listed by rememberSaveable { mutableStateOf(true) }
    var rules by rememberSaveable { mutableStateOf("") }
    var image by rememberSaveable { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var imageFailed by remember { mutableStateOf(false) }
    var nextId by rememberSaveable { mutableIntStateOf(5) }
    var channels by rememberSaveable(stateSaver = StarterChannelsSaver) { mutableStateOf(defaultStarterChannels()) }
    LaunchedEffect(state.created) {
        val created = state.created ?: return@LaunchedEffect
        name = ""; description = ""; privateSpace = true; listed = true; rules = ""; image = null
        channels = defaultStarterChannels(); nextId = 5
        account.consumeCreated(created); onDismiss()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            preparing = true; imageFailed = false
            try { image = PostImagePreparation.normalize(context.contentResolver, uri, SpaceProfile.IMAGE_CHARACTER_LIMIT, cropRatio = 1) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { imageFailed = true }
            finally { preparing = false }
        }
    }
    val valid = name.isNotBlank() && name.toByteArray().size <= 256 && description.toByteArray().size <= 4096 &&
        rules.toByteArray().size <= 4096 && (rules.isEmpty() || rules.isNotBlank()) && channels.isNotEmpty() && channels.all {
            it.name.isNotBlank() && it.name.toByteArray().size <= 128 && it.category.toByteArray().size <= 128
        } && channels.map { it.category.lowercase(java.util.Locale.ROOT) to it.name.lowercase(java.util.Locale.ROOT) }.distinct().size == channels.size
    DesignSheet(open, stringResource(R.string.spaces_create), { account.dismissCreation(); onDismiss() }, footer = {
        Button(onClick = { account.create(SpaceCreation(name.trim(), description.trim(), if (privateSpace) SpaceVisibility.PRIVATE else SpaceVisibility.PUBLIC,
            !privateSpace && listed, rules.trim(), image, channels)) }, enabled = valid && !preparing && !state.working,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_create)) }
        if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
    }) { first ->
        Text(stringResource(R.string.spaces_create_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val fallbackName = stringResource(R.string.spaces_community)
            val previewName = name.trim().takeIf { it.isNotBlank() && it.toByteArray().size <= 256 } ?: fallbackName
            val preview = remember(previewName, image) { SpaceProfile(previewName, image = image) }
            SpaceAvatar(preview, "new-community", account.ownerKey, account.backend, Modifier.size(64.dp))
            Column {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !preparing && !state.working, modifier = first.heightIn(min = 48.dp)) {
                    Text(stringResource(if (image == null) R.string.spaces_add_image else R.string.spaces_change_image))
                }
                if (image != null) TextButton(onClick = { image = null }, enabled = !preparing && !state.working,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_remove_image)) }
            }
        }
        if (preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (imageFailed) Text(stringResource(R.string.spaces_image_failed), color = MaterialTheme.colorScheme.error)
        OutlinedTextField(name, { name = it.take(256) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.spaces_name)) }, shape = RoundedCornerShape(14.dp), enabled = !state.working)
        OutlinedTextField(description, { description = it.take(4096) }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
            label = { Text(stringResource(R.string.spaces_description)) }, shape = RoundedCornerShape(14.dp), enabled = !state.working)
        Text(stringResource(R.string.spaces_visibility), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = privateSpace, onClick = { privateSpace = true }, label = { Text(stringResource(R.string.spaces_private)) }, enabled = !state.working,
                leadingIcon = { Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(selected = !privateSpace, onClick = { privateSpace = false }, label = { Text(stringResource(R.string.spaces_public)) }, enabled = !state.working,
                leadingIcon = { Icon(painterResource(R.drawable.ic_globe), null, Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
        Text(stringResource(if (privateSpace) R.string.spaces_private_creation_detail else R.string.spaces_public_creation_detail),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!privateSpace) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.spaces_discoverable), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.spaces_discoverable_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(listed, { listed = it }, enabled = !state.working)
        }
        OutlinedTextField(rules, { rules = it.take(4096) }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 6,
            label = { Text(stringResource(R.string.spaces_house_rules_optional)) }, shape = RoundedCornerShape(14.dp), enabled = !state.working)
        Text(stringResource(R.string.spaces_starting_channels), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.spaces_starting_channels_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        channels.forEach { draft -> key(draft.id) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(draft.name, { name -> channels = channels.map { if (it.id == draft.id) it.copy(name = name.take(128)) else it } },
                            Modifier.weight(1f), label = { Text(stringResource(R.string.spaces_channel_name)) }, singleLine = true, enabled = !state.working)
                        IconButton(onClick = { channels = channels.filterNot { it.id == draft.id } }, enabled = channels.size > 1 && !state.working,
                            modifier = Modifier.size(48.dp)) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.spaces_remove_channel, draft.name)) }
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { menu = true }, enabled = !state.working, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(channelKindLabel(draft.kind)))
                            Spacer(Modifier.width(8.dp)); Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(18.dp))
                        }
                        DropdownMenu(menu, { menu = false }) {
                            SpaceChannelKind.entries.forEach { kind -> DropdownMenuItem(text = { Text(stringResource(channelKindLabel(kind))) },
                                onClick = { channels = channels.map { if (it.id == draft.id) it.copy(kind = kind) else it }; menu = false }) }
                        }
                    }
                    OutlinedTextField(draft.category, { category -> channels = channels.map { if (it.id == draft.id) it.copy(category = category.take(128)) else it } },
                        Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.spaces_category_optional)) }, singleLine = true, enabled = !state.working)
                }
            }
        } }
        OutlinedButton(onClick = { channels = channels + StarterChannel(nextId++, "", SpaceChannelKind.CHAT) },
            enabled = channels.size < 32 && !state.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.spaces_add_channel))
        }
        if (!valid && name.isNotBlank()) Text(stringResource(R.string.spaces_creation_validation), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.problem == SpacesAccount.Problem.SAVE) Text(stringResource(R.string.spaces_save_failed), color = MaterialTheme.colorScheme.error)
    }
}

internal fun channelKindLabel(kind: SpaceChannelKind): Int = when (kind) {
    SpaceChannelKind.CHAT -> R.string.spaces_kind_chat
    SpaceChannelKind.ANNOUNCEMENT -> R.string.spaces_kind_announcement
    SpaceChannelKind.VOICE -> R.string.spaces_kind_voice
    SpaceChannelKind.ARTICLE -> R.string.spaces_kind_article
    SpaceChannelKind.FILE -> R.string.spaces_kind_file
}
internal fun channelKindIcon(kind: SpaceChannelKind): Int = when (kind) {
    SpaceChannelKind.CHAT -> R.drawable.ic_chat
    SpaceChannelKind.ANNOUNCEMENT -> R.drawable.ic_notifications
    SpaceChannelKind.VOICE -> R.drawable.ic_video
    SpaceChannelKind.ARTICLE -> R.drawable.ic_article
    SpaceChannelKind.FILE -> R.drawable.ic_file
}

private fun defaultStarterChannels() = listOf(StarterChannel(0, "general", SpaceChannelKind.CHAT),
    StarterChannel(1, "announcements", SpaceChannelKind.ANNOUNCEMENT), StarterChannel(2, "the lounge", SpaceChannelKind.VOICE),
    StarterChannel(3, "reading room", SpaceChannelKind.ARTICLE), StarterChannel(4, "resources", SpaceChannelKind.FILE))
private val StarterChannelsSaver = listSaver<List<StarterChannel>, Any>(
    save = { channels -> channels.flatMap { listOf(it.id, it.name, it.kind.name, it.category) } },
    restore = { fields -> fields.chunked(4).map { StarterChannel(it[0] as Int, it[1] as String, SpaceChannelKind.valueOf(it[2] as String), it[3] as String) } })
