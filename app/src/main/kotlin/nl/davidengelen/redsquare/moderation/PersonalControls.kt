package nl.davidengelen.redsquare.moderation

import android.content.Context
import android.system.Os
import java.io.FileDescriptor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Shared semantics for social content/notifications and the later incoming Chat flow. */
internal data class PersonalPolicy(val muted: Set<String> = emptySet(), val blocked: Set<String> = emptySet()) {
    fun hidesSocial(author: String) = author in muted || author in blocked
    fun hidesIncomingChat(author: String) = author in blocked
}

/** Device-local personal choices for one public identity, shared by both servers. */
internal class PersonalControls(private val context: Context, private val owner: String?, private val scope: CoroutineScope,
    commitDirectorySync: (FileDescriptor) -> Unit = Os::fsync) {
    private val storage = owner?.let { AndroidPersonalControls(context, it, commitDirectorySync) }
    val ownerAvailable get() = owner != null
    var policy by mutableStateOf(PersonalPolicy())
        private set
    val muted get() = policy.muted
    val blocked get() = policy.blocked
    var ready by mutableStateOf(false)
        private set
    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf(false)
        private set

    suspend fun load() {
        loading = true
        try {
            policy = withContext(Dispatchers.IO) {
                if (owner == null) {
                    // With no identity, do not guess whose saved choices apply.
                    // Anonymous public reading remains available on a device without them.
                    require(context.noBackupFilesDir.listFiles()?.none { it.name.startsWith("personal.") } == true)
                    PersonalPolicy()
                } else requireNotNull(storage).load()
            }
            ready = true
            error = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { ready = false; error = true }
        finally { loading = false }
    }

    fun retry() {
        if (loading || saving) return
        loading = true
        scope.launch { load() }
    }

    fun setMuted(author: String, mute: Boolean) {
        save(author, policy.copy(muted = if (mute) muted + author else muted - author))
    }

    fun setBlocked(author: String, block: Boolean) {
        save(author, policy.copy(blocked = if (block) blocked + author else blocked - author))
    }

    private fun save(author: String, updated: PersonalPolicy) {
        if (owner == null || !ready || saving) return
        val previous = policy
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    require(SaitoWallet.isPublicKey(author))
                    requireNotNull(storage).save(previous, updated)
                }
                policy = updated
                error = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: AndroidPersonalControls.RecoveryRequired) { ready = false; error = true }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }

    fun hidesPosts(author: String) = !ready || policy.hidesSocial(author)


}

internal val LocalPersonalControls = staticCompositionLocalOf<PersonalControls?> { null }

@Composable
internal fun PersonalControlsScope(owner: String?, commitDirectorySync: (FileDescriptor) -> Unit = Os::fsync,
    content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val controls = remember(owner) { PersonalControls(context, owner, scope, commitDirectorySync) }
    LaunchedEffect(controls) { controls.load() }
    CompositionLocalProvider(LocalPersonalControls provides controls, content = content)
}

@Composable
internal fun PersonalControlsStatus(controls: PersonalControls?) {
    if (controls == null || controls.loading) Text(stringResource(R.string.personal_loading))
    else if (!controls.ready && !controls.ownerAvailable) Text(stringResource(R.string.personal_owner_unavailable))
    else if (!controls.ready) {
        Text(stringResource(R.string.personal_load_error))
        TextButton(onClick = controls::retry) { Text(stringResource(R.string.retry)) }
    } else if (controls.error) Text(stringResource(R.string.personal_save_error))
}

@Composable
internal fun AccountControls(author: String) {
    val controls = LocalPersonalControls.current ?: return
    PersonalControlsStatus(controls)
    AccountControlButtons(controls, author)
}

private enum class Control(val activeLabel: Int, val inactiveLabel: Int, val detail: Int, val confirmation: Int) {
    MUTE(R.string.personal_unmute_account, R.string.personal_mute_account, R.string.personal_mute_detail, R.string.personal_mute),
    BLOCK(R.string.personal_unblock_account, R.string.personal_block_account, R.string.personal_block_detail, R.string.personal_block)
}

@Composable
private fun AccountControlButtons(controls: PersonalControls, author: String) {
    var confirming by remember(author) { mutableStateOf<Control?>(null) }
    fun change(control: Control, enabled: Boolean) {
        when (control) {
            Control.MUTE -> controls.setMuted(author, enabled)
            Control.BLOCK -> controls.setBlocked(author, enabled)
        }
    }
    Row {
        Control.entries.forEach { control ->
            val selected = author in if (control == Control.MUTE) controls.muted else controls.blocked
            TextButton(enabled = controls.ownerAvailable && controls.ready && !controls.saving, onClick = {
                if (selected) change(control, false) else confirming = control
            }) { Text(stringResource(if (selected) control.activeLabel else control.inactiveLabel)) }
        }
    }
    confirming?.let { control -> AlertDialog(
        onDismissRequest = { confirming = null },
        title = { Text(stringResource(control.inactiveLabel)) },
        text = { Text(stringResource(control.detail)) },
        confirmButton = {
            TextButton(enabled = controls.ready && !controls.saving, onClick = {
                confirming = null
                change(control, true)
            }) { Text(stringResource(control.confirmation)) }
        },
        dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.personal_cancel)) } }
    ) }
}

@Composable
internal fun PersonalControlsDialog(onDone: () -> Unit) {
    val controls = LocalPersonalControls.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.personal_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.personal_mute_detail))
                Text(stringResource(R.string.personal_block_detail))
                Text(stringResource(R.string.personal_local_detail))
                PersonalControlsStatus(controls)
                if (controls?.ready == true) {
                    val accounts = controls.muted + controls.blocked
                    if (accounts.isEmpty()) Text(stringResource(R.string.personal_empty))
                    accounts.sorted().forEach { author ->
                        Text(author)
                        AccountControlButtons(controls, author)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.done)) } }
    )
}
