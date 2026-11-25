package nl.davidengelen.redsquare

import nl.davidengelen.redsquare.media.LocalGiphyConfiguration
import nl.davidengelen.redsquare.media.GiphyConfiguration

import nl.davidengelen.redsquare.talk.*
import nl.davidengelen.redsquare.saito.TalkMode
import nl.davidengelen.redsquare.saito.TalkRoom
import nl.davidengelen.redsquare.saito.TalkLink
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
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
import nl.davidengelen.redsquare.stack.StackScreen
import nl.davidengelen.redsquare.ui.AppHeader
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.CompactBackupNotice
import nl.davidengelen.redsquare.policy.PublishingAgreementController
import nl.davidengelen.redsquare.policy.PublishingAgreementDialog
import nl.davidengelen.redsquare.policy.LocalPublishingAgreement

/** Each composition owns exactly one acquisition, including a composition that is abandoned. */
private class ActivityTalkAccount(val account: TalkAccount) : RememberObserver {
    override fun onRemembered() = account.attach()
    override fun onForgotten() = account.detach()
    override fun onAbandoned() = account.detach()
}

private data class ProtectedInvitationRoute(val owner: String, val backend: SaitoBackend, val link: String)

class MainActivity : ComponentActivity() {
    private val routeModels by lazy {
        // This exported Activity's Intent extras are untrusted, not saved route defaults.
        // Keep registry/owner restoration support while admitting only our own route state.
        val arguments = androidx.lifecycle.viewmodel.MutableCreationExtras(defaultViewModelCreationExtras).apply {
            this[androidx.lifecycle.DEFAULT_ARGS_KEY] = Bundle()
        }
        androidx.lifecycle.ViewModelProvider(viewModelStore, defaultViewModelProviderFactory, arguments)
    }
    private val notificationRoutes by lazy {
        routeModels[nl.davidengelen.redsquare.notifications.NotificationRoutes.VIEW_MODEL_KEY,
            nl.davidengelen.redsquare.notifications.NotificationRoutes::class.java]
    }
    private val talkRoutes by lazy {
        routeModels[nl.davidengelen.redsquare.talk.TalkLaunchRoutes::class.java]
    }

    private val incomingVaultFiles by lazy {
        androidx.lifecycle.ViewModelProvider(this)[nl.davidengelen.redsquare.vault.VaultIncomingFiles::class.java]
    }
    private val spaceUris = kotlinx.coroutines.flow.MutableStateFlow<nl.davidengelen.redsquare.spaces.SpacesLaunchRoutes.Open?>(null)
    private val storeUris = kotlinx.coroutines.flow.MutableStateFlow<Uri?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Preserve the original launch configuration, including controlled peer endpoints.
        if (incomingVaultFiles.receive(this, intent)) return
        val space = nl.davidengelen.redsquare.spaces.SpacesLaunchRoutes.from(intent)
        if (space != null) spaceUris.value = space
        else if (nl.davidengelen.redsquare.saito.StoreLink.parse(intent.data?.toString().orEmpty()) != null) storeUris.value = intent.data
        else if (nl.davidengelen.redsquare.talk.TalkLaunchRoutes.accepts(intent.data)) talkRoutes.enqueue(intent.data) else notificationRoutes.enqueue(intent.data)
    }

    private var videoDiagnostics: VideoDiagnostics? = null
    internal fun videoDiagnosticsSnapshot(): String? = videoDiagnostics?.snapshot()

    private var walletExports: WalletExport? = null
    private var walletImports: WalletImport? = null
    private val createWalletDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        walletExports?.documentChosen(it)
    }
    private val createProtectedWalletDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        walletExports?.documentChosen(it, protected = true)
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
        if (savedInstanceState == null) incomingVaultFiles.receive(this, intent)
        talkRoutes.initialize(intent.data)
        val initialSpace = nl.davidengelen.redsquare.spaces.SpacesLaunchRoutes.from(intent)
        if (initialSpace != null) {
            if (savedInstanceState == null) spaceUris.value = initialSpace
        } else if (nl.davidengelen.redsquare.saito.StoreLink.parse(intent.data?.toString().orEmpty()) != null) {
            if (savedInstanceState == null) storeUris.value = intent.data
        } else if (!nl.davidengelen.redsquare.talk.TalkLaunchRoutes.accepts(intent.data)) notificationRoutes.initialize(intent.data)
        videoDiagnostics = VideoDiagnostics.fromIntent(this, intent)
        setContent {
            var openingInitialSpace by remember {
                mutableStateOf(savedInstanceState == null && initialSpace?.uri?.let {
                    nl.davidengelen.redsquare.spaces.SpacesLaunchRoutes.isDestination(it)
                } == true)
            }
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
            val nameDirectorySettings = remember { nl.davidengelen.redsquare.registry.NameDirectorySettings(applicationContext) }
            var nameDirectoryEnabled by remember { mutableStateOf<Boolean?>(null) }
            var nameDirectoryLoad by remember { mutableIntStateOf(0) }
            LaunchedEffect(nameDirectoryLoad) {
                nameDirectoryEnabled = try { withContext(Dispatchers.IO) { nameDirectorySettings.enabled() } }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { null }
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
            var receivedFilesScope by rememberSaveable { mutableStateOf<nl.davidengelen.redsquare.fileshare.ReceivedFilesScope?>(null) }
            var connectionDetailsOpen by remember { mutableStateOf(false) }
            var settingsCategory by rememberSaveable { mutableStateOf<String?>(null) }
            val settingsStates = rememberSaveableStateHolder()
            val settingsOverviewScroll = rememberScrollState()
            var module by rememberSaveable { mutableStateOf(AppModule.RED_SQUARE) }
            var spacesReturnDepth by rememberSaveable { mutableStateOf<Int?>(null) }
            var spacesReturnOwner by rememberSaveable { mutableStateOf<String?>(null) }
            var spacesReturnBackend by rememberSaveable { mutableStateOf<String?>(null) }
            fun clearSpacesReturn() { spacesReturnDepth = null; spacesReturnOwner = null; spacesReturnBackend = null }
            var talkDraft by rememberSaveable { mutableStateOf<TalkDraft?>(null) }
            var talkReturnExpired by remember { mutableStateOf(false) }
            var stackLinkEntry by rememberSaveable { mutableStateOf<nl.davidengelen.redsquare.ui.StackLinkEntry?>(null) }
            var storeLinkEntry by rememberSaveable { mutableStateOf<nl.davidengelen.redsquare.ui.StoreLinkEntry?>(null) }
            var vaultRentalEntry by rememberSaveable { mutableStateOf<nl.davidengelen.redsquare.ui.VaultRentalEntry?>(null) }
            var storeReturnOrder by rememberSaveable { mutableStateOf<String?>(null) }
            var stackScene by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
            val linkedSceneStates = rememberSaveableStateHolder()
            var retainedLinkedKeys by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
            var storeConversation by remember { mutableStateOf(false) }
            var stackLinkRequest by remember { mutableStateOf(0L) }
            var moduleSwitchFailed by remember { mutableStateOf(false) }
            var policiesOpen by rememberSaveable { mutableStateOf(false) }
            var personalControlsOpen by remember { mutableStateOf(false) }
            var usernameOpen by remember { mutableStateOf(false) }
            var savingBackend by remember { mutableStateOf(false) }
            var backendSaveError by remember { mutableStateOf(false) }
            val backendStates = rememberSaveableStateHolder()
            val spacesScreenStates = rememberSaveableStateHolder()
            val scope = rememberCoroutineScope()
            androidx.activity.compose.BackHandler(settingsOpen && settingsCategory != null) {
                settingsCategory = null
            }
            val postNavigation = remember { PostNavigation() }
            fun selectModule(selected: AppModule, onSelected: () -> Unit = {}) {
                if (selected == module && stackLinkEntry == null && storeLinkEntry == null) { clearSpacesReturn(); onSelected() }
                else scope.launch {
                    val moved = postNavigation.navigate { clearSpacesReturn(); stackLinkEntry = null; storeLinkEntry = null; vaultRentalEntry = null; module = selected; onSelected(); true }
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
            var spacesActivated by rememberSaveable { mutableStateOf(false) }
            val articleRouteSaver = remember {
                Saver<String?, String>(
                    save = { link -> link?.takeIf { nl.davidengelen.redsquare.spaces.SpacesLaunchRoutes.isArticle(android.net.Uri.parse(it)) } },
                    restore = { it },
                )
            }
            var pendingSpaceInvitation by rememberSaveable(stateSaver = articleRouteSaver) { mutableStateOf<String?>(null) }
            var pendingSpaceNotification by remember { mutableStateOf<nl.davidengelen.redsquare.spaces.SpacesLaunchRoutes.Open?>(null) }
            val scopedSpaceNotification = pendingSpaceNotification?.takeIf {
                address != null && backend != null && it.matches(address, requireNotNull(backend))
            }
            var pendingProtectedInvitation by remember { mutableStateOf<ProtectedInvitationRoute?>(null) }
            LaunchedEffect(address, backend) {
                val pending = pendingProtectedInvitation
                if (pending != null && (pending.owner != address || pending.backend != backend)) pendingProtectedInvitation = null
                if (scopedSpaceNotification == null) pendingSpaceNotification = null
            }
            val incomingSpaceUri by spaceUris.collectAsState()
            LaunchedEffect(module) { if (module == AppModule.SPACES) spacesActivated = true }
            val spacesAccount = remember(address, backend, spacesActivated) {
                val current = wallet
                val node = backend
                if (!spacesActivated || current == null || node == null) null
                else {
                    val initialRoute = scopedSpaceNotification?.uri ?: pendingSpaceInvitation?.let(Uri::parse)
                    nl.davidengelen.redsquare.spaces.SpacesAccount(applicationContext, current, node,
                        FeedEndpoints.fromIntent(intent, node), scope, prioritiseInitialDestination = initialRoute?.let {
                            nl.davidengelen.redsquare.spaces.SpacesLaunchRoutes.isDestination(it)
                        } == true)
                }
            }
            DisposableEffect(spacesAccount, module) {
                val account = spacesAccount
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) account?.visible(module == AppModule.SPACES)
                    if (event == Lifecycle.Event.ON_STOP) account?.visible(false)
                }
                lifecycle.addObserver(observer)
                account?.visible(module == AppModule.SPACES && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                onDispose { lifecycle.removeObserver(observer); account?.visible(false) }
            }
            DisposableEffect(spacesAccount) { onDispose { spacesAccount?.close() } }
            LaunchedEffect(incomingSpaceUri, address, backend) {
                val incoming = incomingSpaceUri ?: return@LaunchedEffect
                val owner = address ?: return@LaunchedEffect
                val node = backend ?: return@LaunchedEffect
                if (!incoming.matches(owner, node)) {
                    if (spaceUris.value == incoming) spaceUris.value = null
                    openingInitialSpace = false
                    return@LaunchedEffect
                }
                val moved = postNavigation.navigate {
                    if (wallet?.publicKey != owner || backend != node || spaceUris.value != incoming) false
                    else {
                        spacesActivated = true
                        stackLinkEntry = null; storeLinkEntry = null; vaultRentalEntry = null
                        clearSpacesReturn()
                        if (nl.davidengelen.redsquare.spaces.SpacesLaunchRoutes.isProtectedChat(incoming.uri)) {
                            pendingProtectedInvitation = ProtectedInvitationRoute(owner, node, incoming.uri.toString())
                            module = AppModule.RED_SQUARE
                        } else {
                            pendingSpaceInvitation = incoming.uri.toString().takeUnless { incoming.fromNotification }
                            pendingSpaceNotification = incoming.takeIf { it.fromNotification }
                            module = AppModule.SPACES
                        }
                        spaceUris.value = null
                        true
                    }
                }
                openingInitialSpace = false
                moduleSwitchFailed = !moved
            }
            LaunchedEffect(address, backend) {
                receivedFilesScope?.let { saved ->
                    if (address != null && backend != null && (saved.owner != address || saved.backend != backend)) receivedFilesScope = null
                }
            }
            var failed by remember { mutableStateOf(false) }
            var retry by remember { mutableIntStateOf(0) }
            var walletOpen by remember { mutableStateOf(false) }
            var walletBalanceOpen by remember { mutableStateOf(false) }
            var transferDraft by rememberSaveable { mutableStateOf<WalletTransferDraft?>(null) }
            val walletTransferOpen = transferDraft != null
            LaunchedEffect(address, backend) {
                val draft = transferDraft
                if (draft != null && address != null && backend != null &&
                    (draft.owner != address || draft.backend != backend)) transferDraft = null
            }
            var recoveryGuideOpen by remember { mutableStateOf(false) }
            val backupReminders = remember { getSharedPreferences("wallet-backup-reminders", MODE_PRIVATE) }
            var backupReminder by remember { mutableStateOf(false) }
            val spacesRecovery = remember {
                nl.davidengelen.redsquare.spaces.AndroidSpacesRecovery(applicationContext) { FeedEndpoints.fromIntent(intent, it) }
            }
            val walletExport = remember {
                WalletExport(this@MainActivity, { wallet }, createWalletDocument::launch,
                    createProtectedWalletDocument::launch, spacesRecovery::collect)
            }
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
                }, chooseDocument = { openWalletDocument.launch(arrayOf("*/*")) },
                retainArchive = { incoming, archives -> withContext(Dispatchers.IO) { spacesRecovery.retain(incoming, archives) } }) }
            val walletScrollState = rememberScrollState()
            LaunchedEffect(walletOpen, walletImport.preview?.publicKey) {
                // Enter each overview/review at its beginning. Later balance,
                // acknowledgement and backup updates retain the reader's position.
                if (walletOpen) walletScrollState.scrollTo(0)
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
            LaunchedEffect(address, backend, backendReady) {
                if (address != null && backendReady && backend != null && stackLinkEntry?.let {
                    it.owner != address || it.savedBackend != backend } == true) stackLinkEntry = null
            }
            val talkWallet = wallet
            val talkBackend = backend
            val talkEndpoint = talkBackend?.let { FeedEndpoints.fromIntent(intent, it) }
            val talkFallbackForTest = if (BuildConfig.DEBUG) intent.getStringExtra("talkFallbackPeerUrl")?.takeIf {
                it != talkEndpoint } else null
            val talkOwnership = remember(address, talkBackend, talkEndpoint, talkFallbackForTest) {
                if (talkWallet != null && talkBackend != null && talkEndpoint != null)
                    ActivityTalkAccount(TalkRuntime.account(applicationContext, talkWallet, talkBackend, talkEndpoint,
                        talkFallbackForTest)) else null
            }
            val talkAccount = talkOwnership?.account
            DisposableEffect(talkAccount) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) TalkRuntime.visibility(true)
                    if (event == Lifecycle.Event.ON_PAUSE) TalkRuntime.visibility(false)
                }
                lifecycle.addObserver(observer)
                TalkRuntime.visibility(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                onDispose { lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(address, talkBackend) {
                if (address != null && talkBackend != null && talkDraft?.let {
                    it.owner != address || it.savedBackend != talkBackend } == true) talkDraft = null
            }
            val pendingVaultFile by incomingVaultFiles.pending.collectAsState()
            LaunchedEffect(pendingVaultFile, address, backend, savingBackend, walletImport.busy, walletExport.busy) {
                if (pendingVaultFile == null || address == null || backend == null || savingBackend || walletImport.busy || walletExport.busy)
                    return@LaunchedEffect
                val owner = address; val node = backend
                postNavigation.awaitSettled()
                val moved = postNavigation.navigate {
                    if (wallet?.publicKey != owner || backend != node || savingBackend || walletImport.busy || walletExport.busy) false
                    else {
                        walletExport.cancel(); walletImport.cancel(); walletOpen = false
                        settingsOpen = false; settingsCategory = null; usernameOpen = false
                        stackLinkEntry = null; vaultRentalEntry = null; module = AppModule.VAULT; true
                    }
                }
                moduleSwitchFailed = !moved
            }
            val pendingTalkDelivery by talkRoutes.pending.collectAsState()
            LaunchedEffect(pendingTalkDelivery, address, talkBackend, spacesAccount, savingBackend, walletImport.busy, walletExport.busy) {
                val delivery = pendingTalkDelivery ?: return@LaunchedEffect
                if (savingBackend || walletImport.busy || walletExport.busy) return@LaunchedEffect
                val uri = talkRoutes.uri(delivery) ?: run { talkRoutes.consume(delivery); return@LaunchedEffect }
                val owner = address ?: return@LaunchedEffect
                val node = talkBackend ?: return@LaunchedEffect
                postNavigation.awaitSettled()
                if (!talkRoutes.current(delivery)) return@LaunchedEffect
                val link = TalkLink.parse(uri.toString())
                val active = TalkRuntime.call.value
                val exactCall = uri.scheme == "redsquare-talk" && uri.host == "call" &&
                    uri.pathSegments.singleOrNull() == active?.token && uri.getQueryParameter("owner") == owner &&
                    uri.getQueryParameter("backend") == node.id && active != null && active.owner == owner && active.savedBackend == node && active.phase.live
                if (link == null && !exactCall) { talkReturnExpired = true; talkRoutes.consume(delivery); return@LaunchedEffect }
                val room = active?.spaceRoom?.takeIf { exactCall }
                if (room != null && spacesAccount == null) { spacesActivated = true; return@LaunchedEffect }
                val moved = postNavigation.navigate {
                    if (!talkRoutes.current(delivery) || wallet?.publicKey != owner || backend != node || savingBackend || walletImport.busy || walletExport.busy) false
                    else {
                        talkDraft = link?.let { TalkDraft(it.url(), owner = owner, savedBackend = node) }
                        stackLinkEntry = null
                        if (room != null) {
                            spacesAccount?.select(room.space, room.channel)
                            module = AppModule.SPACES
                        } else module = AppModule.TALK
                        talkReturnExpired = false
                        talkRoutes.consume(delivery)
                        true
                    }
                }
                if (moved || talkRoutes.current(delivery)) moduleSwitchFailed = !moved
            }
            val launchTalk: (String, TalkMode) -> Unit = { peer, mode ->
                val owner = address
                val node = backend
                if (owner != null && node != null) scope.launch {
                    val draft = withContext(Dispatchers.IO) {
                        TalkDraft(TalkLink(node, TalkRoom(SaitoWallet.create().publicKey, owner, mode = mode)).url(), peer, owner, node)
                    }
                    postNavigation.awaitSettled()
                    val moved = postNavigation.navigate {
                        if (wallet?.publicKey != owner || backend != node || savingBackend || walletImport.busy || walletExport.busy) false
                        else { talkDraft = draft; stackLinkEntry = null; module = AppModule.TALK; true }
                    }
                    moduleSwitchFailed = !moved
                }
            }
            val linkedStack = stackLinkEntry?.takeIf {
                module == AppModule.STACK && it.owner == address && it.savedBackend == backend
            }
            val linkedStore = storeLinkEntry?.takeIf {
                module == AppModule.STORE && it.owner == address && it.savedBackend == backend
            }
            LaunchedEffect(address, backend) {
                if (vaultRentalEntry?.let { it.owner != address || it.savedBackend != backend } == true) { vaultRentalEntry = null; storeReturnOrder = null }
            }
            val linkedVault = vaultRentalEntry?.takeIf { module == AppModule.VAULT && it.owner == address && it.savedBackend == backend }
            val liveLinkedKeys = linkedVault?.originStore?.retainedSceneKeys() ?: linkedStore?.retainedSceneKeys() ?: linkedStack?.let {
                setOf(nl.davidengelen.redsquare.ui.linkedStackStateKey(it.owner, it.savedBackend, stackScene))
            }.orEmpty()
            LaunchedEffect(address, backend, liveLinkedKeys) {
                // Do not prune restored scenes during the initial asynchronous identity/node load.
                if (address == null || backend == null) return@LaunchedEffect
                (retainedLinkedKeys.toSet() - liveLinkedKeys).forEach(linkedSceneStates::removeState)
                retainedLinkedKeys = ArrayList(liveLinkedKeys)
            }
            val visibleBackend = if (storeConversation && module == AppModule.STORE) backend else linkedVault?.node ?: linkedStore?.target?.backend ?: linkedStack?.target?.backend ?: backend
            LaunchedEffect(address, backend) {
                if (storeLinkEntry?.let { it.owner != address || it.savedBackend != backend } == true) storeLinkEntry = null
            }
            val externalUri = androidx.compose.ui.platform.LocalUriHandler.current
            val readingUri = remember(externalUri) {
                object : androidx.compose.ui.platform.UriHandler {
                    override fun openUri(uri: String) {
                        if (TalkLink.parse(uri) != null) { talkRoutes.enqueue(Uri.parse(uri)); return }
                        val storeTarget = nl.davidengelen.redsquare.saito.StoreLink.parse(uri)
                        val target = nl.davidengelen.redsquare.saito.StackLink.parse(uri)
                        if (target == null && storeTarget == null) { externalUri.openUri(uri); return }
                        val owner = wallet?.publicKey ?: return
                        val savedNode = backend ?: return
                        val request = ++stackLinkRequest
                        scope.launch {
                            // Reading surfaces may already own the draft-save guard around openUri.
                            snapshotFlow { postNavigation.transitioning }.first { !it }
                            if (request != stackLinkRequest) return@launch
                            val moved = postNavigation.navigate {
                                if (wallet?.publicKey != owner || backend != savedNode || savingBackend ||
                                    walletImport.busy || walletExport.busy) false
                                else {
                                    if (storeTarget != null) {
                                        val previous = storeLinkEntry?.takeIf {
                                            module == AppModule.STORE && it.owner == owner && it.savedBackend == savedNode
                                        }
                                        if ((previous?.depth ?: 0) >= 32) return@navigate false
                                        storeLinkEntry = nl.davidengelen.redsquare.ui.StoreLinkEntry(storeTarget, owner, savedNode,
                                            module, stackLinkEntry, stackScene.takeIf { stackLinkEntry != null }, previous)
                                        module = AppModule.STORE
                                    } else {
                                        storeLinkEntry = null
                                        stackLinkEntry = nl.davidengelen.redsquare.ui.StackLinkEntry(requireNotNull(target), owner, savedNode, uri)
                                        stackScene = java.util.UUID.randomUUID().toString()
                                        module = AppModule.STACK
                                    }
                                    settingsOpen = false; settingsCategory = null
                                    true
                                }
                            }
                            moduleSwitchFailed = !moved
                        }
                    }
                }
            }
            val pendingStoreUri by storeUris.collectAsState()
            LaunchedEffect(pendingStoreUri, address, backend, savingBackend, walletImport.busy, walletExport.busy) {
                val uri = pendingStoreUri ?: return@LaunchedEffect
                if (address == null || backend == null || savingBackend || walletImport.busy || walletExport.busy) return@LaunchedEffect
                readingUri.openUri(uri.toString())
                storeUris.value = null
            }
            RedSquareTheme(this@MainActivity, appearance) {
                nl.davidengelen.redsquare.feed.ProfileAvatarScope(wallet, backend,
                    backend?.let { FeedEndpoints.fromIntent(intent, it) }) {
                CompositionLocalProvider(LocalTalkLaunch provides launchTalk, LocalPublishingAgreement provides publishingAgreement,
                    nl.davidengelen.redsquare.chat.LocalProtectedChats provides
                        nl.davidengelen.redsquare.chat.ProtectedChatAccess(spacesAccount) { spacesActivated = true },
                    androidx.compose.ui.platform.LocalUriHandler provides readingUri,
                    nl.davidengelen.redsquare.ui.LocalExternalUriHandler provides externalUri,
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
                val nodeBlock = rememberNodeBlocks(visibleBackend, intent)
                val keyboardVisible = nl.davidengelen.redsquare.ui.keyboardOccupiesWindow()
                val featureUnobstructed = receivedFilesScope == null && !settingsOpen && !connectionDetailsOpen && !policiesOpen &&
                    !publishingAgreement.open && !personalControlsOpen && !usernameOpen &&
                    !walletOpen && !walletBalanceOpen && !walletTransferOpen && !recoveryGuideOpen &&
                    !savingBackend && !walletImport.busy && !walletExport.busy
                val pendingModuleRoute by notificationRoutes.pending.collectAsState()
                LaunchedEffect(module, pendingModuleRoute, address, backend, featureUnobstructed) {
                    val delivery = pendingModuleRoute ?: return@LaunchedEffect
                    if (module == AppModule.RED_SQUARE || !featureUnobstructed ||
                        !NotificationRoutes.targetsScope(delivery, address, backend)) return@LaunchedEffect
                    postNavigation.awaitSettled()
                    if (!notificationRoutes.current(delivery)) return@LaunchedEffect
                    // Mount the existing destination owner. It alone resolves/verifies/consumes
                    // this delivery, preserving policy, exact-target and draft-save checks.
                    val moved = postNavigation.navigate {
                        if (module == AppModule.RED_SQUARE || !notificationRoutes.current(delivery)) false
                        else { module = AppModule.RED_SQUARE; true }
                    }
                    moduleSwitchFailed = !moved
                }
                if (featureUnobstructed && talkWallet != null && talkBackend != null && talkEndpoint != null) {
                    CompositionLocalProvider(LocalRegistryAuthority provides RegistryAuthority.fromIntent(intent, talkEndpoint)) {
                        RegisteredIdentityScope(talkWallet, talkEndpoint, talkBackend,
                            RegistryAuthority.directoryPeer(intent, talkEndpoint).takeIf { nameDirectoryEnabled == true }) {
                            TalkIncomingDialog(talkAccount, address, backend) { selectModule(AppModule.TALK) { talkDraft = null } }
                        }
                    }
                }
                Surface(Modifier.fillMaxSize()) {
                    fun closeWallet() {
                        walletExport.cancel(); walletImport.cancel(); walletOpen = false
                    }
                    val renderAppHeader: @Composable (Boolean, Boolean) -> Unit = { hideForKeyboard, compactRoom ->
                        val navigationEnabled = !postNavigation.transitioning && !savingBackend &&
                            !walletImport.busy && !walletExport.busy
                        if (!hideForKeyboard && !keyboardVisible) AppHeader(visibleBackend?.domain, address, module,
                            walletEnabled = navigationEnabled,
                            settingsEnabled = backendReady && navigationEnabled,
                            onWallet = {
                                settingsOpen = false; settingsCategory = null
                                if (!walletOpen) {
                                    walletExport.clearMessage(); walletImport.clearMessage(); walletOpen = true
                                }
                            },
                            onSettings = { scope.launch {
                                if (walletOpen) closeWallet()
                                settingsCategory = null
                                settingsOverviewScroll.scrollTo(0)
                                settingsOpen = true
                            } },
                            onModule = { selected -> selectModule(selected) {
                                if (walletOpen) closeWallet()
                                settingsOpen = false; settingsCategory = null
                            } },
                            onConnectionDetails = { connectionDetailsOpen = true }, blocks = nodeBlock, showNodeStatus = !compactRoom,
                            registryAuthority = visibleBackend?.let { RegistryAuthority.fromIntent(intent, FeedEndpoints.fromIntent(intent, it)) }
                                ?: nl.davidengelen.redsquare.saito.SaitoRegistry.REGISTRAR)
                        nl.davidengelen.redsquare.ui.InlineStatus(
                            if (moduleSwitchFailed) stringResource(R.string.navigation_save_failed) else null,
                            nl.davidengelen.redsquare.ui.StatusTone.ERROR)
                    }
                    val appHeader: @Composable () -> Unit = { renderAppHeader(false, false) }
                    val activeCall: @Composable (nl.davidengelen.redsquare.spaces.SpacesAccount.Target?, Boolean, Boolean) -> Unit = { roomInView, compact, inline ->
                        val active by TalkRuntime.call.collectAsState()
                        val room = active?.spaceRoom
                        val showingActiveRoom = roomInView != null && room != null && room.space == roomInView.space && room.channel == roomInView.channel
                        if (module != AppModule.TALK || room != null)
                            TalkActiveBar(address, backend, roomInView = showingActiveRoom, compactKeyboard = compact, inline = inline) {
                                if (room == null) selectModule(AppModule.TALK) { talkDraft = null }
                                else selectModule(AppModule.SPACES) { spacesAccount?.select(room.space, room.channel) }
                            }
                    }
                    val renderChrome: @Composable (Boolean, Boolean, nl.davidengelen.redsquare.spaces.SpacesAccount.Target?, Boolean, Boolean) -> Unit = { isHome, hideModuleHeader, roomInView, compactRoom, inlineWritingCall ->
                        renderAppHeader(hideModuleHeader, compactRoom)
                        if (!inlineWritingCall)
                            activeCall(roomInView, keyboardVisible || (module == AppModule.SPACES && hideModuleHeader), false)
                        InlineStatus(if (talkReturnExpired) stringResource(R.string.talk_return_expired) else null, StatusTone.INFO)
                        nl.davidengelen.redsquare.ui.BetaUpdateNotice(
                            updateAvailable && !keyboardVisible && !hideModuleHeader && !compactRoom) {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PreviewUpdates.REVIEW_PAGE)))
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
                    val chrome: @Composable (Boolean) -> Unit = { isHome -> renderChrome(isHome, false, null, false, false) }
                    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
                    val shortWindow = when (module) {
                        AppModule.SPACES -> keyboardVisible && screenHeight < nl.davidengelen.redsquare.spaces.SHORT_SPACES_WINDOW_DP
                        AppModule.RED_SQUARE -> nl.davidengelen.redsquare.feed.compactPostWindow()
                        else -> false
                    }
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(vertical = if (shortWindow) 0.dp else 4.dp)) {
                        if (openingInitialSpace && !failed && (!backendReady || backend != null)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.spaces_destination_opening))
                            }
                        } else if (usernameOpen && wallet != null && backend != null) {
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
                                    SingleOwnerReveal(linkedStore?.scene ?: linkedStack?.target ?: module, Modifier.weight(1f)) {
                                        val updateWalletMetadata: suspend (SaitoWallet, SaitoWallet) -> Unit = { previous, updated ->
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
                                                if (wallet === previous) { wallet = result.first; failed = result.first == null }
                                                check(result.second) { "Could not save wallet metadata" }
                                            }
                                        }
                                        fun openSpaceMember(member: String, message: Boolean) {
                                            val owner = wallet?.publicKey ?: return
                                            scope.launch {
                                                val moved = postNavigation.navigate {
                                                    val current = wallet ?: return@navigate false
                                                    if (current.publicKey != owner || backend != selected) return@navigate false
                                                    if (message) nl.davidengelen.redsquare.chat.retainChatContact(current, member, updateWalletMetadata)
                                                    if (wallet?.publicKey != owner || backend != selected) return@navigate false
                                                    val memory = androidx.lifecycle.ViewModelProvider(this@MainActivity)[
                                                        "browse:$owner:${selected.id}", nl.davidengelen.redsquare.feed.FeedNavigationMemory::class.java]
                                                    val depth = memory.destinations.size
                                                    memory.destinations.add(if (message) nl.davidengelen.redsquare.feed.BrowseDestination.PrivateConversation(member)
                                                        else nl.davidengelen.redsquare.feed.BrowseDestination.Profile(member))
                                                    spacesReturnDepth = depth; spacesReturnOwner = owner; spacesReturnBackend = selected.id
                                                    module = AppModule.RED_SQUARE
                                                    true
                                                }
                                                moduleSwitchFailed = !moved
                                            }
                                        }
                                        fun openSpaceNode(node: String, link: String) {
                                            val choice = SaitoBackend.entries.firstOrNull { it.webUrl == node } ?: return
                                            val owner = wallet?.publicKey ?: return
                                            if (savingBackend) return
                                            savingBackend = true; backendSaveError = false
                                            scope.launch {
                                                try {
                                                    val moved = postNavigation.navigate {
                                                        if (wallet?.publicKey != owner || backend != selected) return@navigate false
                                                        withContext(NonCancellable) {
                                                            val result = withContext(Dispatchers.IO) { runCatching { backendStore.save(choice) } }
                                                            if (result.isFailure) {
                                                                if (result.exceptionOrNull() is AndroidBackendStore.UncertainCommit) backend = null
                                                                backendSaveError = true; false
                                                            } else {
                                                                clearSpacesReturn(); backend = choice; spacesActivated = true
                                                                pendingSpaceInvitation = link; pendingSpaceNotification = null
                                                                module = AppModule.SPACES; true
                                                            }
                                                        }
                                                    }
                                                    moduleSwitchFailed = !moved
                                                } finally { savingBackend = false }
                                            }
                                        }
                                        val renderModule: @Composable () -> Unit = {
                                            if (module == AppModule.SPACES) {
                                                val currentWallet = wallet
                                                val account = spacesAccount
                                                if (currentWallet != null && account != null) key(currentWallet.publicKey, selected) {
                                                    val endpoint = FeedEndpoints.fromIntent(intent, selected)
                                                    nl.davidengelen.redsquare.feed.ProfileAvatarScope(currentWallet, selected, endpoint) {
                                                        CompositionLocalProvider(LocalPostNavigation provides postNavigation,
                                                            LocalRegistryAuthority provides RegistryAuthority.fromIntent(intent, endpoint)) {
                                                            RegisteredIdentityScope(currentWallet, endpoint, selected,
                                                                RegistryAuthority.directoryPeer(intent, endpoint).takeIf { nameDirectoryEnabled == true }) {
                                                                spacesScreenStates.SaveableStateProvider("${currentWallet.publicKey}:${selected.id}") {
                                                                    nl.davidengelen.redsquare.spaces.SpacesScreen(account, Modifier.fillMaxSize(),
                                                                        chrome = { keyboardInLayout, room, compactRoom -> renderChrome(false, keyboardInLayout, room, compactRoom, false) },
                                                                        onProfile = { openSpaceMember(it, false) }, onMessage = { openSpaceMember(it, true) },
                                                                        onOpenNode = ::openSpaceNode, initialInvitation = scopedSpaceNotification?.uri?.toString() ?: pendingSpaceInvitation,
                                                                        fromNotification = scopedSpaceNotification != null,
                                                                        onInvitationConsumed = { pendingSpaceInvitation = null; pendingSpaceNotification = null })
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else chrome(false)
                                            } else if (module == AppModule.STORE) {
                                                val currentWallet = wallet
                                                if (currentWallet != null) key(currentWallet.publicKey, selected) {
                                                    val storeNode = linkedStore?.target?.backend ?: selected
                                                    val storeEndpoint = FeedEndpoints.fromIntent(intent, storeNode)
                                                    CompositionLocalProvider(LocalPostNavigation provides postNavigation,
                                                        LocalRegistryAuthority provides RegistryAuthority.fromIntent(intent, storeEndpoint)) {
                                                        RegisteredIdentityScope(currentWallet, storeEndpoint, storeNode,
                                                            RegistryAuthority.directoryPeer(intent, storeEndpoint).takeIf { nameDirectoryEnabled == true }) {
                                                            nl.davidengelen.redsquare.store.StoreScreen(currentWallet, storeNode, storeEndpoint,
                                                                Modifier.fillMaxSize(), chrome, entry = linkedStore?.target,
                                                                onExitLink = if (linkedStore != null) ({
                                                                    scope.launch {
                                                                        val moved = postNavigation.navigate {
                                                                            val origin = storeLinkEntry
                                                                            if (origin == null || origin.owner != wallet?.publicKey || origin.savedBackend != backend) false
                                                                            else {
                                                                                module = origin.origin; stackLinkEntry = origin.originStack
                                                                                origin.originStackScene?.let { stackScene = it }
                                                                                storeLinkEntry = origin.originStore; true
                                                                            }
                                                                        }
                                                                        moduleSwitchFailed = !moved
                                                                    }
                                                                }) else null, chatBackend = selected,
                                                                chatEndpoint = FeedEndpoints.fromIntent(intent, selected),
                                                                onChatVisibility = { storeConversation = it }, onWalletMetadata = updateWalletMetadata,
                                                                initialOrder = storeReturnOrder, onInitialOrderConsumed = { storeReturnOrder = null },
                                                                onOpenVault = { node, file, order ->
                                                                    scope.launch {
                                                                        val moved = postNavigation.navigate {
                                                                            if (wallet?.publicKey != currentWallet.publicKey || backend != selected) false
                                                                            else {
                                                                                vaultRentalEntry = nl.davidengelen.redsquare.ui.VaultRentalEntry(currentWallet.publicKey, selected, node, file, order, linkedStore)
                                                                                module = AppModule.VAULT; true
                                                                            }
                                                                        }
                                                                        moduleSwitchFailed = !moved
                                                                    }
                                                                }) {
                                                                settingsCategory = "node"; settingsOpen = true
                                                            }
                                                        }
                                                    }
                                                } else chrome(false)
                                            } else if (module == AppModule.VAULT) {
                                                val currentWallet = wallet
                                                if (currentWallet != null) key(currentWallet.publicKey, selected) {
                                                    val vaultNode = linkedVault?.node ?: selected
                                                    val vaultEndpoint = FeedEndpoints.fromIntent(intent, vaultNode)
                                                    CompositionLocalProvider(LocalRegistryAuthority provides RegistryAuthority.fromIntent(intent, vaultEndpoint)) {
                                                        RegisteredIdentityScope(currentWallet, vaultEndpoint, vaultNode,
                                                            RegistryAuthority.directoryPeer(intent, vaultEndpoint).takeIf { nameDirectoryEnabled == true }) {
                                                    nl.davidengelen.redsquare.vault.VaultScreen(currentWallet, vaultNode,
                                                        vaultEndpoint, balances, Modifier.fillMaxSize(), chrome,
                                                        incomingFile = pendingVaultFile.takeIf { vaultNode == selected }, onIncomingConsumed = incomingVaultFiles::consumed,
                                                        selected = selected, initialRentalFile = linkedVault?.fileId,
                                                        onRentalBack = linkedVault?.let { origin -> {
                                                            scope.launch {
                                                                val moved = postNavigation.navigate {
                                                                    if (wallet?.publicKey != origin.owner || backend != origin.savedBackend) false
                                                                    else { storeLinkEntry = origin.originStore; storeReturnOrder = origin.orderId; vaultRentalEntry = null; module = AppModule.STORE; true }
                                                                }
                                                                moduleSwitchFailed = !moved
                                                            }
                                                        } }) {
                                                        settingsCategory = "node"; settingsOpen = true
                                                    }
                                                        }
                                                    }
                                                } else chrome(false)
                                            } else if (module == AppModule.TALK) {
                                                val currentWallet = wallet
                                                if (currentWallet != null && talkAccount != null) {
                                                    val endpoint = FeedEndpoints.fromIntent(intent, selected)
                                                    CompositionLocalProvider(LocalRegistryAuthority provides RegistryAuthority.fromIntent(intent, endpoint)) {
                                                        RegisteredIdentityScope(currentWallet, endpoint, selected,
                                                            RegistryAuthority.directoryPeer(intent, endpoint).takeIf { nameDirectoryEnabled == true }) {
                                                            TalkScreen(currentWallet, selected, talkAccount, talkDraft?.takeIf {
                                                                it.owner == currentWallet.publicKey && it.savedBackend == selected },
                                                                onEntry = { talkDraft = it }, chrome = chrome)
                                                        }
                                                    }
                                                } else chrome(false)
                                            } else if (module == AppModule.STACK) {
                                                if (linkedStack == null && BuildConfig.DEBUG && intent.getBooleanExtra("stackDesignFixture", false)) {
                                                    StackReviewScreen(Modifier.fillMaxSize(), chrome) {
                                                        address?.let { owner ->
                                                            nl.davidengelen.redsquare.ui.StackPreviewAuthor(owner, selected,
                                                                RegistryAuthority.fromIntent(intent, FeedEndpoints.fromIntent(intent, selected)))
                                                        }
                                                    }
                                                } else {
                                                    val stackNode = linkedStack?.target?.backend ?: selected
                                                    val stackEndpoint = FeedEndpoints.fromIntent(intent, stackNode)
                                                    nl.davidengelen.redsquare.feed.ProfileAvatarScope(wallet, stackNode, stackEndpoint) {
                                                    CompositionLocalProvider(LocalPostNavigation provides postNavigation,
                                                        LocalRegistryAuthority provides RegistryAuthority.fromIntent(intent, stackEndpoint)) {
                                                        RegisteredIdentityScope(wallet, stackEndpoint, stackNode,
                                                            RegistryAuthority.directoryPeer(intent, stackEndpoint).takeIf { nameDirectoryEnabled == true }) {
                                                            StackScreen(wallet, stackNode, stackEndpoint, Modifier.fillMaxSize(), chrome,
                                                                entry = linkedStack?.target, entryUrl = linkedStack?.url,
                                                                onExitLink = if (linkedStack != null) ({ selectModule(AppModule.RED_SQUARE) }) else null) {
                                                                settingsCategory = "node"; settingsOpen = true
                                                            }
                                                        }
                                                    }
                                                    }
                                                }
                                            } else {
                                                val feedEndpoint = FeedEndpoints.fromIntent(intent, selected)
                                                CompositionLocalProvider(LocalPostNavigation provides postNavigation,
                                                    LocalRegistryAuthority provides RegistryAuthority.fromIntent(intent, feedEndpoint)) {
                                                    RegisteredIdentityScope(wallet, feedEndpoint, selected,
                                                        RegistryAuthority.directoryPeer(intent, feedEndpoint).takeIf { nameDirectoryEnabled == true }) {
                                                        val protectedInvitation = pendingProtectedInvitation
                                                            ?.takeIf { it.owner == wallet?.publicKey && it.backend == selected }
                                                        FeedScreen(wallet, failed, feedEndpoint, selected,
                                                            lifecycle, Modifier.fillMaxSize(), notificationRoutes = notificationRoutes,
                                                            initialProtectedInvitation = protectedInvitation?.link,
                                                            onProtectedInvitationConsumed = {
                                                                if (pendingProtectedInvitation == protectedInvitation) pendingProtectedInvitation = null
                                                            },
                                                            allowPushRoute = featureUnobstructed,
                                                            externalReturnDepth = spacesReturnDepth.takeIf { spacesReturnOwner == wallet?.publicKey && spacesReturnBackend == selected.id },
                                                            onExternalBack = { clearSpacesReturn(); module = AppModule.SPACES },
                                                            onExternalNavigate = ::clearSpacesReturn,
                                                            chrome = { home, compactWriting, inlineCall -> renderChrome(home, compactWriting, null, false, inlineCall) },
                                                            inlineWritingCall = { activeCall(null, true, true) },
                                                            canReportDrawn = featureUnobstructed && !postNavigation.transitioning,
                                                            onWalletMetadata = updateWalletMetadata
                                                        ) { reportFullyDrawn() }
                                                    }
                                                }
                                            }
                                        }
                                        if (linkedStore != null) key(linkedStore.scene) {
                                            linkedSceneStates.SaveableStateProvider(linkedStore.stateKey) { renderModule() }
                                        } else if (linkedStack != null) key(linkedStack.target) {
                                            linkedSceneStates.SaveableStateProvider(nl.davidengelen.redsquare.ui.linkedStackStateKey(
                                                linkedStack.owner, linkedStack.savedBackend, stackScene)) { renderModule() }
                                        }
                                        else moduleStates.SaveableStateProvider("${wallet?.publicKey.orEmpty()}:${module.name}") { renderModule() }
                                    }
                                }
                            } else {
                                chrome(true)
                                if (!backendReady) Text(stringResource(R.string.feed_loading))
                            }
                        } else chrome(true)
                    }
                    if (connectionDetailsOpen) NodeConnectionDetails(visibleBackend?.domain, nodeBlock) { connectionDetailsOpen = false }
                    receivedFilesScope?.let { saved ->
                        if (saved.owner == address && saved.backend == backend)
                            nl.davidengelen.redsquare.fileshare.ReceivedFilesScreen(saved) {
                                receivedFilesScope = null; settingsCategory = null; settingsOpen = true
                            }
                    }
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
                        backLabel = stringResource(if (settingsCategory != null) R.string.settings_back else R.string.settings_close_task),
                        header = if (settingsCategory == null) appHeader else null,
                        dismissEnabled = !savingBackend,
                        text = {
                            SingleOwnerReveal(settingsCategory, Modifier.fillMaxSize()) {
                            settingsStates.SaveableStateProvider(settingsCategory ?: "index") {
                            val categoryScroll = if (settingsCategory == null) settingsOverviewScroll else rememberScrollState()
                            Column(Modifier.fillMaxWidth().verticalScroll(categoryScroll),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                                if (settingsCategory == null) {
                                    Text(stringResource(R.string.settings_account_section), style = MaterialTheme.typography.titleSmall)
                                    AccountActionRow(stringResource(R.string.settings_privacy_safety), R.drawable.ic_profile, summary =
                                        stringResource(R.string.settings_personal_summary)) { settingsCategory = "privacy" }
                                    AccountActionRow(stringResource(R.string.settings_notifications_privacy), R.drawable.ic_notifications, summary =
                                        stringResource(if (androidx.core.app.NotificationManagerCompat.from(applicationContext)
                                            .areNotificationsEnabled()) R.string.settings_notifications_allowed
                                            else R.string.settings_notifications_blocked)) { settingsCategory = "notifications" }
                                    Text(stringResource(R.string.settings_app_section), style = MaterialTheme.typography.titleSmall)
                                    AccountActionRow(stringResource(R.string.server_choice), R.drawable.ic_globe, summary =
                                        backend?.domain ?: stringResource(R.string.feed_loading)) { settingsCategory = "node" }
                                    AccountActionRow(stringResource(R.string.appearance), R.drawable.ic_appearance, summary =
                                        stringResource(appearance.label)) { settingsCategory = "appearance" }
                                    if (wallet != null && backend != null) AccountActionRow(stringResource(R.string.settings_storage),
                                        R.drawable.ic_file, summary = stringResource(R.string.settings_storage_summary)) {
                                        receivedFilesScope = nl.davidengelen.redsquare.fileshare.ReceivedFilesScope(wallet!!.publicKey, backend!!)
                                        settingsOpen = false; settingsCategory = null
                                    }
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
                                if (settingsCategory == "node") {
                                    nl.davidengelen.redsquare.registry.NameDirectoryChoice(nameDirectoryEnabled,
                                        retryLoad = { nameDirectoryLoad++ }) { requested ->
                                        // Closing Settings must not cancel application of an explicitly saved choice.
                                        withContext(NonCancellable) {
                                            withContext(Dispatchers.IO) { nameDirectorySettings.setEnabled(requested) }
                                            nameDirectoryEnabled = requested
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
                        confirmButton = if (settingsCategory == null) null else ({
                                TextButton(enabled = !savingBackend, onClick = { settingsOpen = false; settingsCategory = null }) {
                                Text(stringResource(R.string.done))
                            }
                        })
                    )
                    PublishingAgreementDialog(publishingAgreement)
                    if (policiesOpen) nl.davidengelen.redsquare.policy.PolicyCenter { policiesOpen = false }
                    if (personalControlsOpen) PersonalControlsDialog { personalControlsOpen = false }
                    if (walletOpen) WalletDialog(address, failed, walletExport, walletImport,
                        walletScrollState, backend, balances, overviewHeader = appHeader,
                        onClose = ::closeWallet,
                        onOpen = { destination ->
                            walletOpen = false
                            when (destination) {
                                WalletDestination.RECEIVE -> walletBalanceOpen = true
                                WalletDestination.SEND -> if (address != null && backend != null) {
                                    transferDraft = WalletTransferDraft(address, backend!!)
                                }
                                WalletDestination.RECOVERY -> recoveryGuideOpen = true
                            }
                        })
                    if (walletBalanceOpen && address != null) WalletBalanceDialog(address!!, backend, balances) {
                        walletBalanceOpen = false
                        walletOpen = true
                    }
                    val editableTransfer = transferDraft
                    if (editableTransfer != null && editableTransfer.owner == address &&
                        editableTransfer.backend == backend && wallet != null && backend != null) {
                        val transfers = remember(wallet, backend) {
                            WalletTransfers(this@MainActivity, wallet!!, backend!!, { wallet }, { backend }, balances,
                                FeedEndpoints.fromIntent(intent, backend!!))
                        }
                        WalletTransferDialog(transfers, editableTransfer, onDraftChange = { update ->
                            transferDraft?.takeIf { it.owner == address && it.backend == backend }?.let {
                                transferDraft = update(it)
                            }
                        }, onClose = {
                            transferDraft = null
                            walletOpen = true
                        })
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
