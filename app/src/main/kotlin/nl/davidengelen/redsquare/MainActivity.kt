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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.system.Os
import java.io.File
import java.io.IOException
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoBackend
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
import nl.davidengelen.redsquare.ui.AccountDialog
import nl.davidengelen.redsquare.ui.AccountActionRow
import nl.davidengelen.redsquare.ui.AccountChoiceRow
import nl.davidengelen.redsquare.ui.SingleOwnerReveal
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone
import nl.davidengelen.redsquare.ui.RedSquareTheme
import nl.davidengelen.redsquare.notifications.NotificationRoutes
import nl.davidengelen.redsquare.ui.NodeConnectionDetails
import nl.davidengelen.redsquare.ui.SingleOwnerReveal
import nl.davidengelen.redsquare.ui.AppModule
import nl.davidengelen.redsquare.stack.StackReviewScreen
import nl.davidengelen.redsquare.ui.AppHeader
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.CompactBackupNotice
import nl.davidengelen.redsquare.policy.PublishingAgreementController
import nl.davidengelen.redsquare.policy.PublishingAgreementDialog
import nl.davidengelen.redsquare.policy.LocalPublishingAgreement

class MainActivity : ComponentActivity() {
    private val notificationRoutes by lazy {
        // This exported Activity's Intent extras are untrusted, not saved route defaults.
        // Keep registry/owner restoration support while admitting only our own route state.
        val arguments = androidx.lifecycle.viewmodel.MutableCreationExtras(defaultViewModelCreationExtras).apply {
            this[androidx.lifecycle.DEFAULT_ARGS_KEY] = Bundle()
        }
        val provider = androidx.lifecycle.ViewModelProvider(viewModelStore, defaultViewModelProviderFactory, arguments)
        provider[nl.davidengelen.redsquare.notifications.NotificationRoutes.VIEW_MODEL_KEY,
            nl.davidengelen.redsquare.notifications.NotificationRoutes::class.java]
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Preserve the original launch configuration, including controlled peer endpoints.
        notificationRoutes.enqueue(intent.data)
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
        notificationRoutes.initialize(intent.data)
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
            var connectionDetailsOpen by remember { mutableStateOf(false) }
            var settingsCategory by rememberSaveable { mutableStateOf<String?>(null) }
            val settingsStates = rememberSaveableStateHolder()
            var module by rememberSaveable { mutableStateOf(AppModule.RED_SQUARE) }
            var moduleSwitchFailed by remember { mutableStateOf(false) }
            var policiesOpen by rememberSaveable { mutableStateOf(false) }
            var personalControlsOpen by remember { mutableStateOf(false) }
            var usernameOpen by remember { mutableStateOf(false) }
            var savingBackend by remember { mutableStateOf(false) }
            var backendSaveError by remember { mutableStateOf(false) }
            val backendStates = rememberSaveableStateHolder()
            val scope = rememberCoroutineScope()
            androidx.activity.compose.BackHandler(settingsOpen && settingsCategory != null) {
                settingsCategory = null
            }
            val postNavigation = remember { PostNavigation() }
            fun selectModule(selected: AppModule) {
                if (selected != module) scope.launch {
                    val moved = postNavigation.navigate { module = selected; true }
                    moduleSwitchFailed = !moved
                }
            }
            androidx.activity.compose.BackHandler(module != AppModule.RED_SQUARE) {
                selectModule(AppModule.RED_SQUARE)
            }
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
            val walletScrollState = rememberScrollState()
            LaunchedEffect(walletOpen, walletImport.preview) {
                if (walletOpen && walletImport.preview == null) walletScrollState.scrollTo(0)
            }
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
            val publishingAgreement = remember { PublishingAgreementController(applicationContext) { descriptor ->
                if (BuildConfig.DEBUG && packageName == "nl.davidengelen.redsquare.dev" &&
                    android.os.Build.HARDWARE in listOf("ranchu", "goldfish") &&
                    intent.getBooleanExtra("publishingCommitSyncUnavailable", false)) {
                    throw IOException("Controlled publishing agreement filesystem sync failure")
                }
                Os.fsync(descriptor)
            } }
            RedSquareTheme(this@MainActivity, appearance) {
                nl.davidengelen.redsquare.feed.ProfileAvatarScope(wallet, backend,
                    backend?.let { FeedEndpoints.fromIntent(intent, it) }) {
                CompositionLocalProvider(LocalPublishingAgreement provides publishingAgreement,
                    LocalVideoFixture provides VideoFixture.fromIntent(intent),
                    LocalVideoDiagnostics provides videoDiagnostics,
                    LocalGiphyConfiguration provides GiphyConfiguration.fromIntent(intent),
                    nl.davidengelen.redsquare.media.LocalChatGiphyConfiguration provides GiphyConfiguration.chatFromIntent(intent),
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
                val nodeBlock = rememberNodeBlockObservation(backend, intent)
                val keyboardVisible = WindowInsets.isImeVisible
                val featureUnobstructed = !settingsOpen && !connectionDetailsOpen && !policiesOpen &&
                    !publishingAgreement.open && !personalControlsOpen && !usernameOpen &&
                    !walletOpen && !walletBalanceOpen && !walletTransferOpen && !recoveryGuideOpen &&
                    !savingBackend && !walletImport.busy && !walletExport.busy
                val pendingModuleRoute by notificationRoutes.pending.collectAsState()
                LaunchedEffect(module, pendingModuleRoute, address, backend, featureUnobstructed) {
                    val delivery = pendingModuleRoute ?: return@LaunchedEffect
                    if (module != AppModule.STACK || !featureUnobstructed ||
                        !NotificationRoutes.targetsScope(delivery, address, backend)) return@LaunchedEffect
                    snapshotFlow { postNavigation.transitioning }.first { !it }
                    if (!notificationRoutes.current(delivery)) return@LaunchedEffect
                    // Mount the existing destination owner. It alone resolves/verifies/consumes
                    // this delivery, preserving policy, exact-target and draft-save checks.
                    val moved = postNavigation.navigate {
                        if (module != AppModule.STACK || !notificationRoutes.current(delivery)) false
                        else { module = AppModule.RED_SQUARE; true }
                    }
                    moduleSwitchFailed = !moved
                }
                Surface(Modifier.fillMaxSize()) {
                    val chrome: @Composable (Boolean) -> Unit = { isHome ->
                        if (!keyboardVisible) AppHeader(backend?.domain, address, module,
                            walletEnabled = !postNavigation.transitioning,
                            settingsEnabled = backendReady && !savingBackend && !postNavigation.transitioning,
                            onWallet = { walletExport.clearMessage(); walletImport.clearMessage(); walletOpen = true },
                            onSettings = { settingsOpen = true },
                            onModule = ::selectModule,
                            onConnectionDetails = { connectionDetailsOpen = true }, observation = nodeBlock)
                        nl.davidengelen.redsquare.ui.InlineStatus(
                            if (moduleSwitchFailed) stringResource(R.string.navigation_save_failed) else null,
                            nl.davidengelen.redsquare.ui.StatusTone.ERROR)
                        if (updateAvailable && !keyboardVisible) {
                            Text(stringResource(R.string.preview_update_available))
                            TextButton(onClick = {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PreviewUpdates.REVIEW_PAGE)))
                            }) { Text(stringResource(R.string.view_update)) }
                        }
                        if (publishingAgreement.savedNotice) Text(stringResource(R.string.publishing_saved),
                            Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
                        if (module == AppModule.RED_SQUARE && isHome && !usernameOpen && !keyboardVisible) {
                            CompactBackupNotice(visible = backupReminder, enabled = backupReminder && wallet != null && !walletExport.busy && !walletImport.busy && !postNavigation.transitioning,
                                onBackup = { walletExport.clearMessage(); walletImport.clearMessage(); walletOpen = true },
                                onLater = { dismissBackupReminder() },
                                onImport = { walletExport.clearMessage(); walletOpen = true; walletImport.start() })
                        }
                        if (backendReady && backend == null) Text(stringResource(R.string.backend_load_error))
                        if (failed) {
                            Text(stringResource(R.string.wallet_recovery_error))
                            Button(onClick = { failed = false; retry++ }) { Text(stringResource(R.string.retry)) }
                        }
                    }
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(vertical = 4.dp)) {
                        if (usernameOpen && wallet != null && backend != null) {
                            chrome(false)
                            key(wallet!!.publicKey, backend) {
                                CompositionLocalProvider(LocalPostNavigation provides postNavigation) {
                                val usernameEndpoint = FeedEndpoints.fromIntent(intent, backend!!)
                                UsernameScreen(wallet!!, backend!!, usernameEndpoint,
                                    RegistryAuthority.fromIntent(intent, usernameEndpoint), Modifier.weight(1f).padding(horizontal = AppSpacing.screen)) {
                                    usernameOpen = false
                                }
                                }
                            }
                        } else if (!(BuildConfig.DEBUG && intent.getBooleanExtra("feedDisabled", false))) {
                            val selected = backend
                            if (selected != null) backendStates.SaveableStateProvider(selected.id) {
                                key(selected) {
                                    val moduleStates = rememberSaveableStateHolder()
                                    SingleOwnerReveal(module, Modifier.weight(1f)) {
                                        moduleStates.SaveableStateProvider("${wallet?.publicKey.orEmpty()}:${module.name}") {
                                            if (module == AppModule.STACK) {
                                                StackReviewScreen(Modifier.fillMaxSize(), chrome)
                                            } else {
                                                val feedEndpoint = FeedEndpoints.fromIntent(intent, selected)
                                                CompositionLocalProvider(LocalPostNavigation provides postNavigation,
                                                    LocalRegistryAuthority provides RegistryAuthority.fromIntent(intent, feedEndpoint)) {
                                                    RegisteredIdentityScope(wallet, feedEndpoint, selected) {
                                                        FeedScreen(wallet, failed, feedEndpoint, selected,
                                                            lifecycle, Modifier.fillMaxSize(), notificationRoutes = notificationRoutes,
                                                            allowPushRoute = featureUnobstructed,
                                                            chrome = chrome,
                                                            canReportDrawn = featureUnobstructed && !postNavigation.transitioning,
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
                                        }
                                    }
                                }
                            } else {
                                chrome(true)
                                if (!backendReady) Text(stringResource(R.string.feed_loading))
                            }
                        } else chrome(true)
                    }
                    if (connectionDetailsOpen) NodeConnectionDetails(backend?.domain, nodeBlock) { connectionDetailsOpen = false }
                    if (settingsOpen) AccountDialog(
                        onDismissRequest = {
                            if (!savingBackend) {
                                if (settingsCategory != null) settingsCategory = null
                                else { settingsOpen = false; settingsCategory = null }
                            }
                        },
                        title = stringResource(when (settingsCategory) {
                            "privacy" -> R.string.settings_privacy_safety
                            "notifications" -> R.string.settings_notifications_privacy
                            "node" -> R.string.server_choice
                            "appearance" -> R.string.appearance
                            "about" -> R.string.settings_about
                            else -> R.string.settings
                        }),
                        closeLabel = stringResource(R.string.settings_close_task),
                        backLabel = if (settingsCategory != null) stringResource(R.string.settings_back) else null,
                        dismissEnabled = !savingBackend,
                        text = {
                            SingleOwnerReveal(settingsCategory, Modifier.fillMaxSize()) {
                            settingsStates.SaveableStateProvider(settingsCategory ?: "index") {
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                                if (settingsCategory == null) {
                                    Text(stringResource(R.string.settings_preferences_summary), style = MaterialTheme.typography.bodySmall)
                                    AccountActionRow(stringResource(R.string.settings_privacy_safety), R.drawable.ic_profile, summary =
                                        stringResource(R.string.settings_personal_summary)) { settingsCategory = "privacy" }
                                    AccountActionRow(stringResource(R.string.settings_notifications_privacy), R.drawable.ic_notifications, summary =
                                        stringResource(if (androidx.core.app.NotificationManagerCompat.from(applicationContext)
                                            .areNotificationsEnabled()) R.string.settings_notifications_allowed
                                            else R.string.settings_notifications_blocked)) { settingsCategory = "notifications" }
                                    AccountActionRow(stringResource(R.string.server_choice), R.drawable.ic_globe, summary =
                                        backend?.domain ?: stringResource(R.string.feed_loading)) { settingsCategory = "node" }
                                    AccountActionRow(stringResource(R.string.appearance), R.drawable.ic_appearance, summary =
                                        stringResource(appearance.label)) { settingsCategory = "appearance" }
                                    AccountActionRow(stringResource(R.string.settings_about), R.drawable.ic_info, summary =
                                        stringResource(R.string.settings_about_summary)) { settingsCategory = "about" }
                                }
                                if (settingsCategory == "privacy") {
                                TextButton(enabled = !savingBackend, onClick = {
                                    settingsCategory = null
                                    settingsOpen = false
                                    policiesOpen = true
                                }) { Text(stringResource(R.string.policy_center)) }
                                TextButton(enabled = wallet != null, onClick = {
                                    settingsCategory = null
                                    settingsOpen = false
                                    personalControlsOpen = true
                                }) { Text(stringResource(R.string.personal_title)) }
                                TextButton(enabled = wallet != null && backend != null && !savingBackend, onClick = {
                                    settingsCategory = null
                                    savingBackend = true
                                    scope.launch {
                                        try {
                                            settingsOpen = false
                                            if (postNavigation.save()) usernameOpen = true
                                        } finally { savingBackend = false }
                                    }
                                }) { Text(stringResource(R.string.username_settings)) }
                                }
                                if (settingsCategory == "notifications") {
                                Spacer(Modifier.height(12.dp))
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
                                    nl.davidengelen.redsquare.notifications.ChatPushChoice(owner, backend)
                                    nl.davidengelen.redsquare.chat.ChatAlertPreviewChoice(owner)
                                }
                                }
                                if (settingsCategory == "node") {
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.server_history_hint), style = MaterialTheme.typography.bodySmall)
                                InlineStatus(if (backendSaveError) stringResource(R.string.backend_save_error) else null, StatusTone.ERROR)
                                Column(Modifier.selectableGroup()) {
                                SaitoBackend.entries.forEach { choice ->
                                    AccountChoiceRow(title = choice.domain,
                                        selected = backend == choice,
                                        summary = if (backend == choice) stringResource(R.string.settings_selected) else null,
                                        enabled = !savingBackend, onClick = {
                                            savingBackend = true
                                            backendSaveError = false
                                            scope.launch {
                                                try {
                                                    if (!postNavigation.save()) {
                                                        settingsOpen = false
                                                        settingsCategory = null
                                                        return@launch
                                                    }
                                                    val outcome = withContext(Dispatchers.IO) {
                                                        try { backendStore.save(choice); Result.success(Unit) }
                                                        catch (failure: Exception) { Result.failure(failure) }
                                                    }
                                                    if (outcome.isSuccess) { backend = choice; settingsOpen = false; settingsCategory = null }
                                                    else {
                                                        if (outcome.exceptionOrNull() is AndroidBackendStore.UncertainCommit) backend = null
                                                        backendSaveError = true
                                                    }
                                                } finally { savingBackend = false }
                                            }
                                        })
                                }
                                }
                                }
                                if (settingsCategory == "appearance") {
                                Spacer(Modifier.height(12.dp))
                                InlineStatus(if (appearanceSaveError) stringResource(R.string.appearance_save_error) else null, StatusTone.ERROR)
                                Column(Modifier.selectableGroup()) {
                                Appearance.entries.forEach { choice ->
                                    AccountChoiceRow(title = stringResource(choice.label),
                                        selected = appearance == choice,
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
                                        })
                                }
                                }
                                }
                                if (settingsCategory == "about") {
                                Spacer(Modifier.height(12.dp))
                                nl.davidengelen.redsquare.ui.AboutApp()
                                }
                            }
                            }
                            }
                        },
                        confirmButton = {
                                TextButton(enabled = !savingBackend, onClick = { settingsOpen = false; settingsCategory = null }) {
                                Text(stringResource(R.string.done))
                            }
                        }
                    )
                    PublishingAgreementDialog(publishingAgreement)
                    if (policiesOpen) nl.davidengelen.redsquare.policy.PolicyCenter { policiesOpen = false }
                    if (personalControlsOpen) PersonalControlsDialog { personalControlsOpen = false }
                    if (walletOpen) WalletDialog(address, failed, walletExport, walletImport,
                        walletScrollState, backend != null,
                        onClose = { walletExport.cancel(); walletImport.cancel(); walletOpen = false },
                        onOpen = { destination ->
                            walletOpen = false
                            when (destination) {
                                WalletDestination.RECEIVE -> walletBalanceOpen = true
                                WalletDestination.SEND -> walletTransferOpen = true
                                WalletDestination.RECOVERY -> recoveryGuideOpen = true
                            }
                        })
                    if (walletBalanceOpen && address != null) WalletBalanceDialog(address!!, backend, balances) {
                        walletBalanceOpen = false
                        walletOpen = true
                    }
                    if (walletTransferOpen && wallet != null && backend != null) {
                        val transfers = remember(wallet, backend) {
                            WalletTransfers(this@MainActivity, wallet!!, backend!!, { wallet }, { backend }, balances,
                                FeedEndpoints.fromIntent(intent, backend!!))
                        }
                        WalletTransferDialog(transfers) {
                            walletTransferOpen = false
                            walletOpen = true
                        }
                    }
                    if (recoveryGuideOpen) AccountDialog(
                        onDismissRequest = { recoveryGuideOpen = false; walletOpen = true },
                        title = stringResource(R.string.wallet_recovery_guide),
                        closeLabel = stringResource(R.string.wallet_recovery_close_task),
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                Text(stringResource(R.string.wallet_recovery_private))
                                Text(stringResource(R.string.wallet_recovery_restore), Modifier.padding(top = 12.dp))
                                Text(stringResource(R.string.wallet_recovery_history), Modifier.padding(top = 12.dp))
                            }
                        },
                        confirmButton = { TextButton(onClick = { recoveryGuideOpen = false; walletOpen = true }) {
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
}
