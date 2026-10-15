package nl.davidengelen.redsquare.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.PostImagePreparation
import nl.davidengelen.redsquare.feed.PostImages
import nl.davidengelen.redsquare.saito.SaitoBackend

/** URI grants are consumed immediately. Only the normalized composed message enters encrypted drafts. */
@Composable
internal fun ChatPhotoAttachment(session: String, body: String, backend: SaitoBackend, enabled: Boolean,
    beforePicker: suspend () -> Boolean, onPreparing: (Boolean) -> Unit, onChange: (String) -> Unit) = key(session) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickingFor by rememberSaveable(session) { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Uri?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val currentBody by rememberUpdatedState(body)
    val currentEnabled by rememberUpdatedState(enabled)
    val change by rememberUpdatedState(onChange)
    val preparation by rememberUpdatedState(onPreparing)
    val photo = remember(body) { chatPhoto(body) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (pickingFor == session) { pickingFor = null; selected = uri }
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
    Column {
        photo?.let { attachment ->
            PostImages(listOf(attachment.source), backend, onRemove = { change(attachment.text) }, removalEnabled = enabled && !preparing)
        }
        TextButton(enabled = enabled && !preparing && pickingFor == null && photo == null, onClick = {
            scope.launch {
                try {
                    if (beforePicker() && currentEnabled) {
                        pickingFor = session
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { pickingFor = null; failed = true }
            }
        }) { Text(stringResource(R.string.chat_add_photo)) }
        if (preparing) Text(stringResource(R.string.image_preparing))
        if (failed) Text(stringResource(R.string.image_prepare_failed))
    }
}
