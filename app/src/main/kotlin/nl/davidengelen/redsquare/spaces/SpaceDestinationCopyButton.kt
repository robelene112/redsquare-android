package nl.davidengelen.redsquare.spaces

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R

@Composable
internal fun SpaceDestinationCopyButton(@StringRes label: Int, enabled: Boolean, link: suspend () -> String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copying by remember { mutableStateOf(false) }
    var result by remember { mutableIntStateOf(label) }
    TextButton(onClick = {
        copying = true
        scope.launch {
            try {
                val clip = ClipData.newPlainText(context.getString(label), link()).apply {
                    description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                }
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                result = R.string.spaces_copied
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                result = R.string.spaces_copy_destination_failed
            } finally {
                copying = false
            }
        }
    }, enabled = enabled && !copying, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(result))
    }
}
