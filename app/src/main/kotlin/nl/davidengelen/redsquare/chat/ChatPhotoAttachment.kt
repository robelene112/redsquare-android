package nl.davidengelen.redsquare.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.media.GiphyAttribution
import nl.davidengelen.redsquare.media.GiphyItem
import nl.davidengelen.redsquare.media.GiphyPicker
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.PostImagePreparation
import nl.davidengelen.redsquare.feed.PostImages
import nl.davidengelen.redsquare.saito.SaitoBackend

/** URI grants are consumed immediately. Only the normalized composed message enters encrypted drafts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatPhotoAttachment(session: String, body: String, backend: SaitoBackend, enabled: Boolean,
    beforePicker: suspend () -> Boolean, onPreparing: (Boolean) -> Unit, onChange: (String) -> Unit, onSelectGif: (suspend (GiphyItem) -> Boolean)? = null, gifAttribution: GiphyAttribution? = null,
    showPickerActions: Boolean = true,
    content: @Composable (preview: @Composable () -> Unit, actions: @Composable () -> Unit) -> Unit) = key(session) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickingFor by rememberSaveable(session) { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Uri?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var gifOpen by remember { mutableStateOf(false) }
    var selectingGif by remember { mutableStateOf(false) }
    val selectGif by rememberUpdatedState(onSelectGif)
    var failed by remember { mutableStateOf(false) }
    val currentBody by rememberUpdatedState(body)
    val currentEnabled by rememberUpdatedState(enabled)
    val change by rememberUpdatedState(onChange)
    val preparation by rememberUpdatedState(onPreparing)
    // Restored pickers and returned URIs also keep navigation waiting.
    SideEffect { preparation(preparing || pickingFor != null || selected != null || selectingGif) }
    val photo = remember(body) { chatPhoto(body) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (pickingFor == session) { pickingFor = null; selected = uri; preparation(uri != null) }
    }
    LaunchedEffect(selected) {
        val uri = selected ?: return@LaunchedEffect
        preparing = true; preparation(true); failed = false
        try {
            val source = PostImagePreparation.normalize(context.contentResolver, uri, 500_000)
            if (currentEnabled) change(composeChatPhoto(source, chatPhoto(currentBody)?.text ?: currentBody))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { selected = null; preparing = false; preparation(false) }
    }
    val preview: @Composable () -> Unit = {
        Column {
            photo?.let { attachment ->
                PostImages(listOf(attachment.source), backend, onRemove = { change(attachment.text) },
                    removalEnabled = enabled && !preparing && !selectingGif,
                    previewHeight = if (WindowInsets.isImeVisible) 72.dp else 100.dp,
                    gifAttributions = gifAttribution?.let { mapOf(attachment.source to it) }.orEmpty(),
                    onOpenSource = { open -> scope.launch { if (beforePicker() && currentEnabled) open() } })
            }
            if (preparing) Text(stringResource(R.string.image_preparing))
            if (failed) Text(stringResource(R.string.image_prepare_failed))
        }
    }
    val actions: @Composable () -> Unit = {
        if (photo == null && showPickerActions) Box {
            var expanded by remember { mutableStateOf(false) }
            IconButton(enabled = enabled && !preparing && pickingFor == null && !selectingGif,
                onClick = { expanded = true }) {
                Icon(painterResource(R.drawable.ic_image), stringResource(R.string.chat_attach_media))
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_add_photo)) },
                    enabled = enabled && !preparing && pickingFor == null, onClick = {
                    expanded = false
                    scope.launch {
                        try {
                            if (beforePicker() && currentEnabled) {
                                pickingFor = session
                                preparation(true)
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { pickingFor = null; preparation(false); failed = true }
                    }
                })
                if (onSelectGif != null) DropdownMenuItem(text = { Text(stringResource(R.string.giphy_add)) },
                    enabled = enabled && !preparing && pickingFor == null && !selectingGif,
                    onClick = {
                        expanded = false
                        scope.launch {
                            try { if (beforePicker() && currentEnabled) { failed = false; gifOpen = true } }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { failed = true }
                        }
                    })
            }
        }
    }
    content(preview, actions)
    if (gifOpen) GiphyPicker(onClose = { if (!selectingGif) gifOpen = false },
            selectionEnabled = enabled && !selectingGif, selectionError = failed, onSelect = { item ->
                if (!selectingGif && currentEnabled) {
                    selectingGif = true; preparation(true)
                    scope.launch {
                        try {
                            val accepted = selectGif?.invoke(item) == true
                            failed = !accepted
                            if (accepted) gifOpen = false
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true }
                        finally { selectingGif = false; preparation(false) }
                    }
                }
            })
}
