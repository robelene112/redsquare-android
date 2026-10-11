package nl.davidengelen.redsquare.moderation

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Device-local personal choices for one public identity, shared by both servers. */
internal class PersonalControls(private val context: Context, private val owner: String?, private val scope: CoroutineScope) {
    private val storage = owner?.let { EncryptedLocalFile(context, "personal.$it.v1", "personal.$it", "personal.$it.") }
    val ownerAvailable get() = owner != null
    var muted by mutableStateOf<Set<String>>(emptySet())
        private set
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
            muted = withContext(Dispatchers.IO) {
                if (owner == null) {
                    // With no identity, do not guess whose saved choices apply.
                    // Anonymous public reading remains available on a device without them.
                    require(context.noBackupFilesDir.listFiles()?.none { it.name.startsWith("personal.") } == true)
                    emptySet()
                } else requireNotNull(storage).load()?.let { saved ->
                    require(saved.version == 1.toByte())
                    val record = JsonParser.parseString(saved.text).asJsonObject
                    require(record.get("version").asInt == 1 && record.get("owner").asString == owner)
                    val accounts = record.getAsJsonArray("muted").map { it.asString }
                    require(accounts.size <= MAX_ACCOUNTS && accounts.distinct().size == accounts.size)
                    require(accounts.all(SaitoWallet::isPublicKey))
                    accounts.toSet()
                } ?: emptySet()
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
        if (owner == null || !ready || saving) return
        val updated = if (mute) muted + author else muted - author
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    require(SaitoWallet.isPublicKey(author) && updated.size <= MAX_ACCOUNTS)
                    val record = JsonObject().apply {
                        addProperty("version", 1)
                        addProperty("owner", owner)
                        add("muted", com.google.gson.JsonArray().apply { updated.sorted().forEach(::add) })
                    }
                    requireNotNull(storage).save(EncryptedLocalFile.Contents(1, record.toString()))
                }
                muted = updated
                error = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }

    fun hidesPosts(author: String) = !ready || author in muted

    private companion object { const val MAX_ACCOUNTS = 1000 }
}

internal val LocalPersonalControls = staticCompositionLocalOf<PersonalControls?> { null }

@Composable
internal fun PersonalControlsScope(owner: String?, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val controls = remember(owner) { PersonalControls(context, owner, scope) }
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
    var confirming by remember(author) { mutableStateOf(false) }
    PersonalControlsStatus(controls)
    val muted = author in controls.muted
    TextButton(enabled = controls.ownerAvailable && controls.ready && !controls.saving, onClick = {
        if (muted) controls.setMuted(author, false) else confirming = true
    }) { Text(stringResource(if (muted) R.string.personal_unmute_account else R.string.personal_mute_account)) }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.personal_mute_account)) },
        text = { Text(stringResource(R.string.personal_mute_detail)) },
        confirmButton = {
            TextButton(enabled = controls.ready && !controls.saving, onClick = {
                confirming = false
                controls.setMuted(author, true)
            }) { Text(stringResource(R.string.personal_mute)) }
        },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.personal_cancel)) } }
    )
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
                Text(stringResource(R.string.personal_local_detail))
                PersonalControlsStatus(controls)
                if (controls?.ready == true) {
                    if (controls.muted.isEmpty()) Text(stringResource(R.string.personal_empty))
                    controls.muted.sorted().forEach { author ->
                        Text(author)
                        TextButton(enabled = !controls.saving, onClick = { controls.setMuted(author, false) }) {
                            Text(stringResource(R.string.personal_unmute_account))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.done)) } }
    )
}
