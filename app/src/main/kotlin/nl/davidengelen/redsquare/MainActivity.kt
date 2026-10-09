package nl.davidengelen.redsquare

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.system.Os
import java.io.File
import java.io.IOException
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.ImportBalance
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.feed.FeedScreen
import nl.davidengelen.redsquare.feed.FeedEndpoints

class MainActivity : ComponentActivity() {
    private var walletExports: WalletExport? = null
    private var walletImports: WalletImport? = null
    private val createWalletDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        walletExports?.documentChosen(it)
    }
    private val openWalletDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        walletImports?.documentChosen(it)
    }

    override fun onDestroy() {
        walletExports?.cancel()
        walletExports = null
        walletImports?.cancel()
        walletImports = null
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var wallet by remember { mutableStateOf<SaitoWallet?>(null) }
            val backendStore = remember {
                AndroidBackendStore(applicationContext) { descriptor ->
                    if (BuildConfig.DEBUG && intent.getBooleanExtra("backendCommitSyncUnavailable", false)) {
                        throw IOException("Controlled selection commit sync failure")
                    }
                    Os.fsync(descriptor)
                }
            }
            var backend by remember { mutableStateOf<SaitoBackend?>(null) }
            var backendReady by remember { mutableStateOf(false) }
            var settingsOpen by remember { mutableStateOf(false) }
            var savingBackend by remember { mutableStateOf(false) }
            var backendSaveError by remember { mutableStateOf(false) }
            val backendStates = rememberSaveableStateHolder()
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                backend = withContext(Dispatchers.IO) {
                    try { backendStore.load() } catch (_: Exception) { null }
                }
                backendReady = true
            }
            val address = wallet?.publicKey
            var failed by remember { mutableStateOf(false) }
            var retry by remember { mutableIntStateOf(0) }
            var walletOpen by remember { mutableStateOf(false) }
            var recoveryGuideOpen by remember { mutableStateOf(false) }
            val backupReminders = remember { getSharedPreferences("wallet-backup-reminders", MODE_PRIVATE) }
            var backupReminder by remember { mutableStateOf(false) }
            val walletExport = remember { WalletExport(this@MainActivity, { wallet }, createWalletDocument::launch) }
            val balances = remember { WalletBalances.fromIntent(intent) }
            val walletImport = remember { WalletImport(this@MainActivity, { wallet }, { backend }, balances,
                activate = { previous, incoming ->
                    wallet = null
                    val result = withContext(Dispatchers.IO) {
                        val store = AndroidWalletStore(applicationContext)
                        try { WalletImport.Activation(store.replace(previous, incoming), true) }
                        catch (_: Exception) {
                            val restored = try { store.loadExisting().takeIf { it.exportJson() == previous.exportJson() } }
                                catch (_: Exception) { null }
                            WalletImport.Activation(restored, false)
                        }
                    }
                    // An outgoing export cannot claim that a newly imported
                    // wallet is backed up or dismiss that identity's reminder.
                    if (result.successful) walletExport.clearMessage()
                    wallet = result.wallet
                    failed = result.wallet == null
                    result
                }, chooseDocument = { openWalletDocument.launch(arrayOf("*/*")) }) }
            DisposableEffect(walletExport) {
                walletExports = walletExport
                onDispose { walletExport.cancel(); if (walletExports === walletExport) walletExports = null }
            }
            DisposableEffect(walletImport) {
                walletImports = walletImport
                onDispose { walletImport.cancel(); if (walletImports === walletImport) walletImports = null }
            }
            val dismissBackupReminder = {
                address?.let { backupReminders.edit().putBoolean("hidden:$it", true).apply() }
                backupReminder = false
            }
            LaunchedEffect(address) {
                backupReminder = false
                address?.let { identity ->
                    backupReminder = withContext(Dispatchers.IO) {
                        try { !backupReminders.getBoolean("hidden:$identity", false) }
                        catch (_: Exception) { true }
                    }
                }
            }
            LaunchedEffect(address, walletExport.message) {
                if (address != null && walletExport.message == R.string.wallet_export_saved) dismissBackupReminder()
            }
            var updateAvailable by remember { mutableStateOf(false) }
            var updateCheck by remember { mutableIntStateOf(0) }
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) updateCheck++
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(updateCheck) {
                val fixtureEndpoint = if (BuildConfig.DEBUG) intent.getStringExtra("reviewManifestUrl") else null
                if (updateCheck > 0 && (BuildConfig.REVIEW_BUILD || fixtureEndpoint != null)) {
                    val endpoint = fixtureEndpoint ?: PreviewUpdates.MANIFEST
                    updateAvailable = withContext(Dispatchers.IO) {
                        val version = packageManager.getPackageInfo(packageName, 0).longVersionCode
                        PreviewUpdates.hasNewerBuild(endpoint, version)
                    }
                }
            }
            LaunchedEffect(retry) {
                failed = false
                wallet = withContext(Dispatchers.IO) {
                    try {
                        val store = if (BuildConfig.DEBUG && intent.getBooleanExtra("walletCommitSyncUnavailable", false)) {
                            AndroidWalletStore(applicationContext) { descriptor ->
                                if (!File(noBackupFilesDir, "wallet.initializing").exists()) {
                                    throw IOException("Controlled filesystem sync failure")
                                }
                                Os.fsync(descriptor)
                            }
                        } else AndroidWalletStore(applicationContext)
                        store.loadOrCreate()
                    }
                    catch (_: Exception) { null }
                }
                failed = wallet == null
            }
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                        if (updateAvailable) {
                            Text(stringResource(R.string.preview_update_available))
                            TextButton(onClick = {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PreviewUpdates.REVIEW_PAGE)))
                            }) { Text(stringResource(R.string.view_update)) }
                        }
                        Row {
                            TextButton(onClick = { walletExport.clearMessage(); walletImport.clearMessage(); walletOpen = true }) {
                                Text(stringResource(R.string.wallet))
                            }
                            TextButton(onClick = { settingsOpen = true }, enabled = backendReady && !savingBackend) {
                                Text(stringResource(R.string.settings))
                            }
                        }
                        backend?.let { Text(stringResource(R.string.selected_server, it.domain), style = MaterialTheme.typography.labelSmall) }
                        if (backupReminder) {
                            Text(stringResource(R.string.wallet_backup_reminder), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.wallet_backup_reminder_detail), style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = { walletExport.clearMessage(); walletImport.clearMessage(); walletOpen = true }) {
                                    Text(stringResource(R.string.wallet_back_up))
                                }
                                TextButton(onClick = dismissBackupReminder) { Text(stringResource(R.string.later)) }
                            }
                            TextButton(enabled = wallet != null && !walletExport.busy && !walletImport.busy,
                                onClick = { walletExport.clearMessage(); walletOpen = true; walletImport.start() }) {
                                Text(stringResource(R.string.wallet_import_existing))
                            }
                        }
                        if (backendReady && backend == null) Text(stringResource(R.string.backend_load_error))
                        if (failed) {
                            Text(stringResource(R.string.wallet_recovery_error))
                            Button(onClick = { failed = false; retry++ }) { Text(stringResource(R.string.retry)) }
                        }
                        if (!(BuildConfig.DEBUG && intent.getBooleanExtra("feedDisabled", false))) {
                            val selected = backend
                            if (selected != null) backendStates.SaveableStateProvider(selected.id) {
                                key(selected) {
                                    FeedScreen(wallet, failed, FeedEndpoints.fromIntent(intent, selected), selected,
                                        lifecycle, Modifier.weight(1f)) { reportFullyDrawn() }
                                }
                            } else if (!backendReady) Text(stringResource(R.string.feed_loading))
                        }
                    }
                    if (settingsOpen) AlertDialog(
                        onDismissRequest = { if (!savingBackend) settingsOpen = false },
                        title = { Text(stringResource(R.string.settings)) },
                        text = {
                            Column {
                                Text(stringResource(R.string.server_choice))
                                Text(stringResource(R.string.server_history_hint), style = MaterialTheme.typography.bodySmall)
                                if (backendSaveError) Text(stringResource(R.string.backend_save_error))
                                SaitoBackend.entries.forEach { choice ->
                                    Row {
                                        RadioButton(selected = backend == choice, onClick = null)
                                        TextButton(enabled = !savingBackend, onClick = {
                                            savingBackend = true
                                            backendSaveError = false
                                            scope.launch {
                                                try {
                                                    val outcome = withContext(Dispatchers.IO) {
                                                        try { backendStore.save(choice); Result.success(Unit) }
                                                        catch (failure: Exception) { Result.failure(failure) }
                                                    }
                                                    if (outcome.isSuccess) { backend = choice; settingsOpen = false }
                                                    else {
                                                        if (outcome.exceptionOrNull() is AndroidBackendStore.UncertainCommit) backend = null
                                                        backendSaveError = true
                                                    }
                                                } finally { savingBackend = false }
                                            }
                                        }) { Text(choice.domain) }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(enabled = !savingBackend, onClick = { settingsOpen = false }) {
                                Text(stringResource(R.string.done))
                            }
                        }
                    )
                    if (walletOpen) AlertDialog(
                        onDismissRequest = { if (!walletImport.activating) {
                            walletExport.cancel(); walletImport.cancel(); walletOpen = false
                        } },
                        title = { Text(stringResource(R.string.your_wallet)) },
                        text = {
                            val addressLabel = stringResource(R.string.wallet_address)
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                when {
                                    address != null -> Text(address!!, Modifier.semantics { contentDescription = addressLabel })
                                    failed -> Text(stringResource(R.string.wallet_unavailable))
                                    else -> Text(stringResource(R.string.wallet_opening))
                                }
                                walletExport.message?.let { Text(stringResource(it)) }
                                walletImport.message?.let { Text(stringResource(it)) }
                                walletImport.preview?.let { preview ->
                                    Text(stringResource(if (preview.sameIdentity) R.string.wallet_import_restore else R.string.wallet_import_replace),
                                        style = MaterialTheme.typography.titleMedium)
                                    val selectedAddressLabel = stringResource(R.string.wallet_import_address)
                                    Text(preview.publicKey, Modifier.semantics { contentDescription = selectedAddressLabel })
                                    Text(stringResource(if (preview.sameIdentity) R.string.wallet_import_restore_detail else R.string.wallet_import_replace_detail))
                                    if (!preview.sameIdentity) {
                                        if (preview.risks?.otherAssetsUnknown == true) Text(stringResource(R.string.wallet_import_other_assets))
                                        preview.risks?.savedName?.let { Text(stringResource(R.string.wallet_import_saved_name, it)) }
                                        if (preview.risks?.pendingWork == true) Text(stringResource(R.string.wallet_import_pending_work))
                                        Text(stringResource(when (preview.balance) {
                                            null -> R.string.wallet_import_balance_checking
                                            ImportBalance.ZERO -> R.string.wallet_import_balance_zero
                                            ImportBalance.POSITIVE -> R.string.wallet_import_balance_positive
                                            ImportBalance.UNKNOWN -> R.string.wallet_import_balance_unknown
                                        }))
                                        Text(stringResource(when {
                                            preview.backupVerified -> R.string.wallet_import_backup_verified
                                            preview.backupSkipped -> R.string.wallet_import_backup_skipped
                                            preview.balance == ImportBalance.ZERO -> R.string.wallet_import_backup_recommended
                                            else -> R.string.wallet_import_backup_required
                                        }))
                                        if (preview.balance == ImportBalance.ZERO && !preview.backupVerified && !preview.backupSkipped) {
                                            TextButton(enabled = !walletExport.busy && !walletImport.busy, onClick = walletImport::skipBackup) {
                                                Text(stringResource(R.string.wallet_import_skip_backup))
                                            }
                                        }
                                        TextButton(enabled = !walletExport.busy && !walletImport.busy,
                                            onClick = { walletImport.backUpCurrentWallet { verified -> walletExport.start(verified) } }) {
                                            Text(stringResource(R.string.wallet_import_backup))
                                        }
                                    }
                                    Text(stringResource(if (preview.sameIdentity) R.string.wallet_import_restore_warning else R.string.wallet_import_work_warning))
                                    val riskLabel = stringResource(R.string.wallet_import_ack_label)
                                    Row {
                                        Checkbox(checked = preview.acknowledged, onCheckedChange = walletImport::acknowledge,
                                            enabled = !walletImport.busy && !walletExport.busy,
                                            modifier = Modifier.semantics { contentDescription = riskLabel })
                                        Text(stringResource(R.string.wallet_import_acknowledge))
                                    }
                                    TextButton(enabled = !walletExport.busy && !walletImport.busy && preview.acknowledged &&
                                        (preview.sameIdentity || preview.backupVerified ||
                                            (preview.balance == ImportBalance.ZERO && preview.backupSkipped)), onClick = walletImport::confirm) {
                                        Text(stringResource(if (preview.sameIdentity) R.string.wallet_import_confirm_restore else R.string.wallet_import_confirm_replace))
                                    }
                                }
                                TextButton(enabled = wallet != null && !walletExport.busy && !walletImport.busy,
                                    onClick = { walletExport.start() }) { Text(stringResource(R.string.wallet_export)) }
                                TextButton(enabled = wallet != null && !walletExport.busy && !walletImport.busy,
                                    onClick = { walletExport.clearMessage(); walletImport.start() }) {
                                    Text(stringResource(R.string.wallet_import))
                                }
                                Text(stringResource(R.string.wallet_backup_private), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { recoveryGuideOpen = true }) { Text(stringResource(R.string.wallet_recovery_guide)) }
                            }
                        },
                        confirmButton = { TextButton(enabled = !walletImport.activating,
                            onClick = { walletExport.cancel(); walletImport.cancel(); walletOpen = false }) {
                            Text(stringResource(R.string.done))
                        } }
                    )
                    if (recoveryGuideOpen) AlertDialog(
                        onDismissRequest = { recoveryGuideOpen = false },
                        title = { Text(stringResource(R.string.wallet_recovery_guide)) },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                Text(stringResource(R.string.wallet_recovery_private))
                                Text(stringResource(R.string.wallet_recovery_restore), Modifier.padding(top = 12.dp))
                                Text(stringResource(R.string.wallet_recovery_history), Modifier.padding(top = 12.dp))
                            }
                        },
                        confirmButton = { TextButton(onClick = { recoveryGuideOpen = false }) {
                            Text(stringResource(R.string.wallet_close_guide))
                        } }
                    )
                }
            }
        }
    }
}
