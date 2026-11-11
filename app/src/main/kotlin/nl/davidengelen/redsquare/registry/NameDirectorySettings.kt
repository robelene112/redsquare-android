package nl.davidengelen.redsquare.registry

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

/** Device preference for a separate read-only provider, independent of the content node. */
internal class NameDirectorySettings(context: Context) {
    private val preferences = context.getSharedPreferences("name-directory", Context.MODE_PRIVATE)
    fun enabled(): Boolean = preferences.getBoolean("enabled", true)
    fun setEnabled(enabled: Boolean) {
        check(preferences.edit().putBoolean("enabled", enabled).commit()) { "Could not save name lookup choice" }
    }
}

@Composable
internal fun NameDirectoryChoice(enabled: Boolean?, retryLoad: () -> Unit, change: suspend (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    HorizontalDivider()
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
        value = enabled == true, enabled = enabled != null && !saving, role = Role.Switch,
        onValueChange = { requested ->
            saving = true
            scope.launch {
                try { change(requested); failed = false }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
                finally { saving = false }
            }
        }), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.identity_directory_choice), Modifier.weight(1f))
        Switch(checked = enabled == true, onCheckedChange = null, enabled = enabled != null && !saving)
    }
    Text(stringResource(R.string.identity_directory_choice_detail), style = MaterialTheme.typography.bodySmall)
    InlineStatus(if (failed || enabled == null) stringResource(R.string.identity_directory_choice_error) else null,
        StatusTone.ERROR)
    if (enabled == null) TextButton(onClick = retryLoad) { Text(stringResource(R.string.retry)) }
}
