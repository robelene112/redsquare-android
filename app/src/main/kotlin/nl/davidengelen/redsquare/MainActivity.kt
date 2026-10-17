package nl.davidengelen.redsquare

import nl.davidengelen.redsquare.media.LocalGiphyConfiguration
import nl.davidengelen.redsquare.media.GiphyConfiguration

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
import nl.davidengelen.redsquare.feed.LocalPostNavigation
import nl.davidengelen.redsquare.feed.PostNavigation
import nl.davidengelen.redsquare.feed.LocalVideoFixture
import nl.davidengelen.redsquare.feed.LocalVideoDiagnostics
import nl.davidengelen.redsquare.feed.VideoDiagnostics
import nl.davidengelen.redsquare.feed.VideoFixture
import nl.davidengelen.redsquare.feed.VideoPlaybackHost
import nl.davidengelen.redsquare.registry.UsernameScreen
import nl.davidengelen.redsquare.registry.RegistryAuthority
import nl.davidengelen.redsquare.registry.RegisteredIdentityScope
import nl.davidengelen.redsquare.registry.LocalRegistryAuthority
import nl.davidengelen.redsquare.moderation.PersonalControlsScope
import nl.davidengelen.redsquare.moderation.PersonalControlsDialog
import nl.davidengelen.redsquare.ui.AndroidAppearanceStore
import nl.davidengelen.redsquare.ui.Appearance
import nl.davidengelen.redsquare.ui.RedSquareTheme

class MainActivity : ComponentActivity() {
    private val socialRoutes by lazy {
        // This exported Activity's Intent extras are untrusted, not saved route defaults.
        // Keep registry/owner restoration support while admitting only our own route state.
        val arguments = androidx.lifecycle.viewmodel.MutableCreationExtras(defaultViewModelCreationExtras).apply {
            this[androidx.lifecycle.DEFAULT_ARGS_KEY] = Bundle()
        }
        val provider = androidx.lifecycle.ViewModelProvider(viewModelStore, defaultViewModelProviderFactory, arguments)
        provider[nl.davidengelen.redsquare.notifications.SocialPushRoutes::class.java]
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Preserve the original launch configuration, including controlled peer endpoints.
        socialRoutes.enqueue(intent.data)
    }

    private var videoDiagnostics: VideoDiagnostics? = null
    internal fun videoDiagnosticsSnapshot(): String? = videoDiagnostics?.snapshot()

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

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        socialRoutes.initialize(intent.data)
        videoDiagnostics = VideoDiagnostics.fromIntent(this, intent)
        setContent {
            val appearanceStore = remember { AndroidAppearanceStore(applicationContext) }
            var appearance by remember { mutableStateOf(Appearance.SYSTEM) }
            var appearanceReady by remember { mutableStateOf(false) }
            var savingAppearance by remember { mutableStateOf(false) }
            var appearanceSaveError by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                appearance = withContext(Dispatchers.IO) {
                    try { appearanceStore.load() } catch (_: Exception) { Appearance.SYSTEM }
                }
                appearanceReady = true
            }
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
            var personalControlsOpen by remember { mutableStateOf(false) }
            var usernameOpen by remember { mutableStateOf(false) }
            var savingBackend by remember { mutableStateOf(false) }
            var backendSaveError by remember { mutableStateOf(false) }
            val backendStates = rememberSaveableStateHolder()
            val scope = rememberCoroutineScope()
            val postNavigation = remember { PostNavigation() }
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
            var walletBalanceOpen by remember { mutableStateOf(false) }
            var walletTransferOpen by remember { mutableStateOf(false) }
            var recoveryGuideOpen by remember { mutableStateOf(false) }
            val backupReminders = remember { getSharedPreferences("wallet-backup-reminders", MODE_PRIVATE) }
            var backupReminder by remember { mutableStateOf(false) }
            val walletExport = remember { WalletExport(this@MainActivity, { wallet }, createWalletDocument::launch) }
            val balances = remember { WalletBalances.fromIntent(intent) }
            val walletImport = remember { WalletImport(this@MainActivity, { wallet }, { backend }, balances,
                beforeSelection = { postNavigation.save() },
                activate = activate@ { previous, incoming ->
                    if (!postNavigation.save()) return@activate WalletImport.Activation(previous, false)
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
                    nl.davidengelen.redsquare.notifications.PushRegistration.request(applicationContext)
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
                    if (event == Lifecycle.Event.ON_RESUME) {
                        updateCheck++
                        nl.davidengelen.redsquare.notifications.PushRegistration.request(applicationContext)
                    }
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
            LaunchedEffect(address, backend) {
                if (address != null && backend != null) {
                    nl.davidengelen.redsquare.notifications.PushRegistration.request(applicationContext)
                }
            }
            val giphyAttributions = remember(address) {
                address?.let { nl.davidengelen.redsquare.media.GiphyAttributionStore(applicationContext, it) }
            }
            RedSquareTheme(this@MainActivity, appearance) {
                CompositionLocalProvider(LocalVideoFixture provides VideoFixture.fromIntent(intent),
                    LocalVideoDiagnostics provides videoDiagnostics,
                    LocalGiphyConfiguration provides GiphyConfiguration.fromIntent(intent),
                    nl.davidengelen.redsquare.media.LocalGiphyAttributions provides giphyAttributions) {
                VideoPlaybackHost(address, backend) {
                PersonalControlsScope(address, commitDirectorySync = { descriptor ->
                    if (BuildConfig.DEBUG && intent.getBooleanExtra("personalCommitTerminate", false)) {
                        android.os.Process.killProcess(android.os.Process.myPid())
                        error("Expected controlled process termination")
                    }
                    if (BuildConfig.DEBUG && intent.getBooleanExtra("personalCommitSyncUnavailable", false)) {
                        throw IOException("Controlled personal filesystem sync failure")
                    }
                    Os.fsync(descriptor)
                }, recoveryDirectorySync = { descriptor ->
                    if (BuildConfig.DEBUG && intent.getBooleanExtra("personalRecoverySyncUnavailable", false)) {
                        val obstruction = File(noBackupFilesDir, "personal.${requireNotNull(address)}.recovery.v1")
                        check(!obstruction.exists() && obstruction.mkdir())
                        check(File(obstruction, "controlled-unavailable").createNewFile())
                        throw IOException("Controlled personal recovery filesystem obstruction")
                    }
                    Os.fsync(descriptor)
                }) {
                // safeDrawing remains the sole IME/system-inset owner. Secondary
                // chrome returns when typing ends without dismissing any reminders.
                val keyboardVisible = WindowInsets.isImeVisible
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        if (!keyboardVisible) Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Box(Modifier.size(18.dp).background(androidx.compose.ui.graphics.Color(0xffda243d)))
                            Text(stringResource(R.string.app_name), Modifier.weight(1f).padding(start = 8.dp),
                                style = MaterialTheme.typography.titleLarge, maxLines = 1)
                            TextButton(contentPadding = PaddingValues(horizontal = 6.dp),
                                onClick = { if (!postNavigation.transitioning) { walletExport.clearMessage(); walletImport.clearMessage(); walletOpen = true } }) {
                                Text(stringResource(R.string.wallet), style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(contentPadding = PaddingValues(horizontal = 6.dp),
                                onClick = { if (!postNavigation.transitioning) settingsOpen = true },
                                enabled = backendReady && !savingBackend && !postNavigation.transitioning) {
                                Text(stringResource(R.string.settings), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (updateAvailable && !keyboardVisible) {
                            Text(stringResource(R.string.preview_update_available))
                            TextButton(onClick = {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PreviewUpdates.REVIEW_PAGE)))
                            }) { Text(stringResource(R.string.view_update)) }
                        }
                        if (!keyboardVisible) backend?.let { Text(stringResource(R.string.selected_server, it.domain),
                            Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (backupReminder && !keyboardVisible) {
                            OutlinedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                            Text(stringResource(R.string.wallet_backup_reminder), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.wallet_backup_reminder_detail), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth()) {
                                TextButton(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp),
                                    onClick = { if (!postNavigation.transitioning) { walletExport.clearMessage(); walletImport.clearMessage(); walletOpen = true } }) {
                                    Text(stringResource(R.string.wallet_back_up), style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp),
                                    onClick = { if (!postNavigation.transitioning) dismissBackupReminder() }) {
                                    Text(stringResource(R.string.later), style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(modifier = Modifier.weight(2f), contentPadding = PaddingValues(horizontal = 2.dp),
                                    enabled = wallet != null && !walletExport.busy && !walletImport.busy && !postNavigation.transitioning,
                                    onClick = { if (!postNavigation.transitioning) { walletExport.clearMessage(); walletOpen = true; walletImport.start() } }) {
                                    Text(stringResource(R.string.wallet_import_existing), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            }
                            }
                        }
                        if (backendReady && backend == null) Text(stringResource(R.string.backend_load_error))
                        if (failed) {
                            Text(stringResource(R.string.wallet_recovery_error))
                            Button(onClick = { failed = false; retry++ }) { Text(stringResource(R.string.retry)) }
                        }
                        if (usernameOpen && wallet != null && backend != null) {
                            key(wallet!!.publicKey, backend) {
                                CompositionLocalProvider(LocalPostNavigation provides postNavigation) {
                                val usernameEndpoint = FeedEndpoints.fromIntent(intent, backend!!)
                                UsernameScreen(wallet!!, backend!!, usernameEndpoint,
                                    RegistryAuthority.fromIntent(intent, usernameEndpoint), Modifier.weight(1f)) {
                                    usernameOpen = false
                                }
                                }
                            }
                        } else if (!(BuildConfig.DEBUG && intent.getBooleanExtra("feedDisabled", false))) {
                            val selected = backend
                            if (selected != null) backendStates.SaveableStateProvider(selected.id) {
                                key(selected) {
                                    val feedEndpoint = FeedEndpoints.fromIntent(intent, selected)
                                    CompositionLocalProvider(LocalPostNavigation provides postNavigation,
                                        LocalRegistryAuthority provides RegistryAuthority.fromIntent(intent, feedEndpoint)) {
                                    RegisteredIdentityScope(wallet, feedEndpoint, selected) {
                                        FeedScreen(wallet, failed, feedEndpoint, selected,
                                            lifecycle, Modifier.weight(1f), socialRoutes = socialRoutes,
                                            allowPushRoute = !settingsOpen && !personalControlsOpen && !usernameOpen &&
                                                !walletOpen && !walletBalanceOpen && !walletTransferOpen && !recoveryGuideOpen &&
                                                !savingBackend && !walletImport.busy && !walletExport.busy,
                                            onWalletMetadata = { previous, updated ->
                                                check(wallet === previous && updated.publicKey == previous.publicKey)
                                                withContext(NonCancellable) {
                                                    val result = withContext(Dispatchers.IO) {
                                                        val store = AndroidWalletStore(applicationContext)
                                                        try { store.replace(previous, updated) to true }
                                                        catch (_: Exception) {
                                                            val restored = try { store.loadExisting() } catch (_: Exception) { null }
                                                            restored to false
                                                        }
                                                    }
                                                    if (wallet === previous) {
                                                        wallet = result.first
                                                        failed = result.first == null
                                                    }
                                                    check(result.second) { "Could not save wallet metadata" }
                                                }
                                            }) { reportFullyDrawn() }
                                    }
                                    }
                                }
                            } else if (!backendReady) Text(stringResource(R.string.feed_loading))
                        }
                    }
                    if (settingsOpen) AlertDialog(
                        onDismissRequest = { if (!savingBackend) settingsOpen = false },
                        title = { Text(stringResource(R.string.settings)) },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                TextButton(enabled = wallet != null, onClick = {
                                    settingsOpen = false
                                    personalControlsOpen = true
                                }) { Text(stringResource(R.string.personal_title)) }
                                TextButton(enabled = wallet != null && backend != null && !savingBackend, onClick = {
                                    savingBackend = true
                                    scope.launch {
                                        try {
                                            settingsOpen = false
                                            if (postNavigation.save()) usernameOpen = true
                                        } finally { savingBackend = false }
                                    }
                                }) { Text(stringResource(R.string.username_settings)) }
                                wallet?.publicKey?.let { owner ->
                                    nl.davidengelen.redsquare.notifications.SocialAlertChoice(owner) {
                                        postNavigation.navigate {
                                            withContext(Dispatchers.IO) {
                                                nl.davidengelen.redsquare.notifications.SocialPush.ensureChannels(applicationContext)
                                            }
                                            startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
                                            true
                                        }
                                    }
                                }
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
                                                    if (!postNavigation.save()) {
                                                        settingsOpen = false
                                                        return@launch
                                                    }
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
                                Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleSmall)
                                if (appearanceSaveError) Text(stringResource(R.string.appearance_save_error))
                                Column(Modifier.selectableGroup()) {
                                Appearance.entries.forEach { choice ->
                                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                        selected = appearance == choice, role = Role.RadioButton,
                                        enabled = appearanceReady && !savingAppearance, onClick = {
                                            savingAppearance = true
                                            appearanceSaveError = false
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) { appearanceStore.save(choice) }
                                                    appearance = choice
                                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                                catch (_: Exception) { appearanceSaveError = true }
                                                finally { savingAppearance = false }
                                            }
                                        }), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        RadioButton(selected = appearance == choice, onClick = null,
                                            enabled = appearanceReady && !savingAppearance)
                                        Text(stringResource(choice.label), Modifier.padding(start = 8.dp))
                                    }
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
                    if (personalControlsOpen) PersonalControlsDialog { personalControlsOpen = false }
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
                                        Text(stringResource(R.string.wallet_import_local_posts))
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
                                TextButton(enabled = address != null && !walletExport.busy && !walletImport.busy,
                                    onClick = { walletBalanceOpen = true }) {
                                    Text(stringResource(R.string.wallet_balance_receive))
                                }
                                TextButton(enabled = wallet != null && backend != null && !walletExport.busy && !walletImport.busy,
                                    onClick = { walletTransferOpen = true }) { Text(stringResource(R.string.transfer_send)) }
                                TextButton(onClick = { recoveryGuideOpen = true }) { Text(stringResource(R.string.wallet_recovery_guide)) }
                            }
                        },
                        confirmButton = { TextButton(enabled = !walletImport.activating,
                            onClick = { walletExport.cancel(); walletImport.cancel(); walletOpen = false }) {
                            Text(stringResource(R.string.done))
                        } }
                    )
                    if (walletBalanceOpen && address != null) WalletBalanceDialog(address!!, backend, balances) {
                        walletBalanceOpen = false
                    }
                    if (walletTransferOpen && wallet != null && backend != null) {
                        val transfers = remember(wallet, backend) {
                            WalletTransfers(this@MainActivity, wallet!!, backend!!, { wallet }, { backend }, balances,
                                FeedEndpoints.fromIntent(intent, backend!!))
                        }
                        WalletTransferDialog(transfers) { walletTransferOpen = false }
                    }
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
        }
    }
}
