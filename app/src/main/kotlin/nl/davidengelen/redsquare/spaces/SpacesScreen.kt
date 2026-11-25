package nl.davidengelen.redsquare.spaces

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import nl.davidengelen.redsquare.feed.LocalPostNavigation
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.ProfileAvatar
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.CompactWritingField
import nl.davidengelen.redsquare.ui.DesignSheet
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.keyboardOccupiesWindow
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

internal const val SHORT_SPACES_WINDOW_DP = 420

/** Production routes use only account-owned received/durable state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpacesScreen(account: SpacesAccount, modifier: Modifier = Modifier,
    chrome: @Composable (Boolean, SpacesAccount.Target?, Boolean) -> Unit, onProfile: (String) -> Unit, onMessage: (String) -> Unit,
    onOpenNode: (String, String) -> Unit, initialInvitation: String? = null, fromNotification: Boolean = false, onInvitationConsumed: () -> Unit = {}) {
    val sharedState by account.state.collectAsState()
    val communities = remember(sharedState.communities) { sharedState.communities.filter { it.protectedChat == null } }
    val state = sharedState.copy(communities = communities)
    val scope = rememberCoroutineScope()
    val publishing = nl.davidengelen.redsquare.policy.LocalPublishingAgreement.current
    val navigation = LocalPostNavigation.current
    val articleEditorGuard = remember(account) { AtomicReference<(() -> Boolean)?>(null) }
    DisposableEffect(navigation, account) {
        val save: suspend () -> Boolean = { articleEditorGuard.get()?.invoke() != false && account.guardNavigation { true } }
        val guard: suspend (suspend () -> Boolean) -> Boolean = { commit ->
            articleEditorGuard.get()?.invoke() != false && account.guardNavigation(commit)
        }
        navigation?.saveCurrent = save; navigation?.guardedCurrent = guard
        onDispose {
            if (navigation?.saveCurrent === save) navigation.saveCurrent = null
            if (navigation?.guardedCurrent === guard) navigation.guardedCurrent = null
        }
    }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    DisposableEffect(account, drawer.isOpen) {
        account.channelDrawerVisible(drawer.isOpen)
        onDispose { account.channelDrawerVisible(false) }
    }
    var closingDrawerContent by remember(account) { mutableStateOf<SpacesAccount.State?>(null) }
    LaunchedEffect(drawer.currentValue, drawer.targetValue, drawer.isAnimationRunning) {
        if (drawer.targetValue == DrawerValue.Open || (drawer.currentValue == DrawerValue.Closed &&
                drawer.targetValue == DrawerValue.Closed && !drawer.isAnimationRunning))
            closingDrawerContent = null
    }
    val navigationTitle = stringResource(R.string.spaces_navigation_panel)
    var tab by rememberSaveable { mutableStateOf(SpacesTab.MY_SPACES) }
    var createOpen by remember { mutableStateOf(false) }
    var joinOpen by remember { mutableStateOf(false) }
    var invitationSpace by remember(account) { mutableStateOf<String?>(null) }
    var invitationAfterInformation by remember(account) { mutableStateOf<String?>(null) }
    var informationOpen by remember { mutableStateOf(false) }
    var informationHidden by remember { mutableStateOf(true) }
    var channelEditorPending by rememberSaveable { mutableStateOf(false) }
    var categoryEditorPending by rememberSaveable { mutableStateOf(false) }
    val categoryEditor = rememberSaveable(saver = SpaceCategoryEditorState.Saver) { SpaceCategoryEditorState() }
    val channelEditor = rememberSaveable(saver = SpaceChannelEditorState.Saver) { SpaceChannelEditorState() }
    val channelCreator = rememberSaveable(saver = SpaceChannelCreatorState.Saver) { SpaceChannelCreatorState() }
    var membersOpen by rememberSaveable { mutableStateOf(false) }
    var reportsSpace by rememberSaveable(account.ownerKey, account.backend.webUrl) { mutableStateOf<String?>(null) }
    val reportsOpen = reportsSpace != null && reportsSpace == state.selected
    var searchSpace by rememberSaveable(account.ownerKey, account.backend.webUrl) { mutableStateOf<String?>(null) }
    var searchResultOpen by rememberSaveable(account.ownerKey, account.backend.webUrl) { mutableStateOf(false) }
    var searchLink by remember { mutableStateOf<String?>(null) }
    var activityLink by remember(account) { mutableStateOf<String?>(null) }
    var activityGroup by remember(account) { mutableStateOf<SpaceActivityGroup?>(null) }
    var activityReturn by rememberSaveable(account.ownerKey, account.backend.webUrl) { mutableStateOf(false) }
    val routeStates = rememberSaveableStateHolder()
    val routeScope = "${account.ownerKey}/${account.backend.id}"
    val searchOpen = searchSpace != null && searchSpace == state.selected && !searchResultOpen
    var channelAccessRoute by rememberSaveable(account.ownerKey, account.backend.webUrl) { mutableStateOf<String?>(null) }
    val channelAccessOpen = channelAccessRoute == "${state.selected}:${state.selectedChannel}"
    LaunchedEffect(state.selected, state.selectedChannel, state.loadingChannel) {
        if (channelAccessRoute != null && state.selected != null && state.selectedChannel != null && !state.loadingChannel && !channelAccessOpen)
            channelAccessRoute = null
    }
    var overlaySpace by remember { mutableStateOf(state.selected) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var publicRulesOpen by remember { mutableStateOf(false) }
    var publicReference by rememberSaveable { mutableStateOf<String?>(null) }
    var publicCopied by remember { mutableStateOf(false) }
    var publicShareFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val compactConfiguration = screenHeight <= 480.dp ||
        screenHeight <= 700.dp && LocalDensity.current.fontScale >= 1.5f
    var invitationText by remember { mutableStateOf("") }
    var replacingDevice by remember { mutableStateOf(false) }
    var archivePickerOpen by remember { mutableStateOf(false) }
    var archiveActivationOpen by remember { mutableStateOf(false) }
    var reviewedInvitation by remember(account) { mutableStateOf<SpaceClient.InvitationPreview?>(null) }
    val invitationReview = state.preview ?: reviewedInvitation
    SideEffect { state.preview?.let { reviewedInvitation = it } }
    fun clearInvitationReview() { reviewedInvitation = null; account.dismissPreview() }
    var savedSelection by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.current
    val inviteOpen = invitationSpace != null && invitationSpace == state.selected
    fun showInvitation(space: String) {
        val latest = account.state.value
        if (latest.selected != space || latest.current?.canInvite != true) return
        invitationSpace = space
        if (latest.invitation == null && latest.current.canManage) account.invite(space)
    }
    var pinsRoute by rememberSaveable(account.ownerKey, account.backend) { mutableStateOf<String?>(null) }
    val pinConversationRoute = selected?.takeUnless { state.loadingChannel }?.let { "${it.id}:${it.channel}:${state.threadParent?.id.orEmpty()}" }
    val pinsOpen = pinConversationRoute != null && pinsRoute == pinConversationRoute
    LaunchedEffect(pinConversationRoute, state.ready, state.loading, savedSelection) {
        // Account restoration initially has no selected conversation. Keep the
        // saved route until a real scope, or the communities page, is restored.
        if (pinConversationRoute != null && pinsRoute != pinConversationRoute ||
            state.ready && !state.loading && state.selected == null && savedSelection == null) pinsRoute = null
    }
    val profile = state.selected?.let { state.profiles[it] }
    val channel = state.channels.firstOrNull { it.id == state.selectedChannel }
    LaunchedEffect(state.publicLink) { publicCopied = false; publicShareFailed = false }
    LaunchedEffect(state.openedPublicReference) {
        state.openedPublicReference?.let { opened ->
            if (opened == publicReference) publicReference = null
            if (opened == searchLink) {
                searchResultOpen = true
                searchLink = null
            }
            if (opened == activityLink) {
                activityReturn = true
                activityLink = null; activityGroup = null
            }
            account.consumePublicReference(opened)
        }
    }
    val imeVisible = keyboardOccupiesWindow()
    fun choose(space: String?, channel: String? = null) {
        if (articleEditorGuard.get()?.invoke() == false) return
        // Selection is immediate; the panel keeps its rows until its exit ends.
        if (drawer.currentValue != DrawerValue.Closed || drawer.targetValue != DrawerValue.Closed)
            closingDrawerContent = closingDrawerContent ?: state
        categoryEditorPending = false; categoryEditor.open = false; channelEditorPending = false; channelEditor.open = false; channelCreator.open = false; channelAccessRoute = null
        savedSelection = space; account.select(space, channel); tab = SpacesTab.MY_SPACES
        scope.launch { drawer.close() }
    }
    fun selectTab(target: SpacesTab) {
        if (articleEditorGuard.get()?.invoke() == false) return
        activityReturn = false
        categoryEditorPending = false; categoryEditor.open = false; channelEditorPending = false; channelEditor.open = false
        channelCreator.open = false; channelAccessRoute = null; account.select(null); savedSelection = null; tab = target
    }
    LaunchedEffect(account, state.ready, state.loading) {
        // A verified public visitor has local context without My Spaces membership.
        if (initialInvitation == null && state.ready && !state.loading && !state.working &&
            !state.projectingDestination && state.selected == null &&
            savedSelection != null) account.select(savedSelection)
    }
    LaunchedEffect(state.selected) {
        if (overlaySpace != state.selected) {
            informationOpen = false; invitationSpace = null; invitationAfterInformation = null
            membersOpen = false; reportsSpace = null; publicRulesOpen = false
            searchSpace = null; searchResultOpen = false; searchLink = null
            overlaySpace = state.selected
        }
        state.selected?.let { savedSelection = it; tab = SpacesTab.MY_SPACES }
    }
    LaunchedEffect(initialInvitation, fromNotification, state.ready, state.loading, state.working, state.savingNavigation) {
        val link = initialInvitation ?: return@LaunchedEffect
        val uri = android.net.Uri.parse(link)
        val destination = SpacesLaunchRoutes.isDestination(uri)
        if ((state.loading && !destination) || !state.ready || state.working || state.savingNavigation) return@LaunchedEffect
        categoryEditorPending = false; categoryEditor.open = false; channelEditorPending = false; channelEditor.open = false
        channelCreator.open = false; channelAccessRoute = null
        informationOpen = false; invitationSpace = null; invitationAfterInformation = null
        membersOpen = false; reportsSpace = null; publicRulesOpen = false
        searchSpace = null; searchResultOpen = false; searchLink = null
        activityReturn = false; activityLink = null; activityGroup = null
        clearInvitationReview()
        replacingDevice = false
        if (SpacesLaunchRoutes.isArticle(uri) || SpacesLaunchRoutes.isPublic(uri) || destination) {
            joinOpen = false; channelAccessRoute = null
            publicReference = link
            when {
                destination -> {
                    if (fromNotification) activityLink = link
                    if (!account.openDestinationLink(link, fromNotification = fromNotification)) return@LaunchedEffect
                }
                SpacesLaunchRoutes.isArticle(uri) -> account.openArticleLink(link)
                else -> account.openPublicLink(link)
            }
        } else { invitationText = link; joinOpen = true; account.preview(link) }
        onInvitationConsumed()
    }
    LaunchedEffect(state.joinedInvitation) {
        state.joinedInvitation?.let { joined ->
            if (joinOpen && invitationText.trim() == joined) joinOpen = false
            account.consumeJoinedInvitation(joined)
        }
    }
    LaunchedEffect(state.openedRecovery, archivePickerOpen) {
        val archive = state.openedRecovery ?: return@LaunchedEffect
        if (archivePickerOpen) {
            archivePickerOpen = false
            account.consumeRecoveredHistory(archive)
        }
    }
    LaunchedEffect(joinOpen) { if (!joinOpen) replacingDevice = false }
    LaunchedEffect(selected?.joined) { if (selected?.joined == true) publicRulesOpen = false }
    fun backFromConversation() {
        when {
            searchResultOpen && searchSpace == state.selected -> {
                account.returnFromDestination()
                searchResultOpen = false
            }
            activityReturn -> selectTab(SpacesTab.ACTIVITY)
            account.returnFromDestination() -> Unit
            state.threadParent != null -> account.closeThread()
            else -> choose(null)
        }
    }
    BackHandler(channelAccessRoute != null) { channelAccessRoute = null; informationOpen = selected != null }
    BackHandler(membersOpen) { membersOpen = false; informationOpen = true }
    BackHandler(reportsOpen) { reportsSpace = null; informationOpen = true }
    BackHandler(searchOpen) { searchSpace = null; informationOpen = true }
    BackHandler(!membersOpen && !reportsOpen && !searchOpen && !channelAccessOpen && state.selected != null && drawer.isClosed &&
        !createOpen && !joinOpen && !inviteOpen && !informationOpen && !channelEditor.open && !channelCreator.open && !publicRulesOpen) {
        backFromConversation()
    }
    BackHandler(drawer.currentValue != DrawerValue.Closed || drawer.targetValue != DrawerValue.Closed || drawer.isAnimationRunning) {
        scope.launch { drawer.close() }
    }
    ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = state.selected != null, modifier = modifier,
        drawerContent = {
            val menuState = closingDrawerContent ?: state
            val menuCommunity = menuState.current
            ModalDrawerSheet(Modifier.widthIn(max = 360.dp).fillMaxHeight().semantics { paneTitle = navigationTitle }) {
                LazyColumn(contentPadding = PaddingValues(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text(stringResource(R.string.spaces_your_communities), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }) }
                    if (menuState.ready && !menuState.communityListLoaded) item { LoadingCommunities() }
                    items(menuState.communities, key = { "space:${it.id}" }) { community ->
                        SpaceCommunityRow(menuState.profiles[community.id] ?: SpaceProfile(community.name), community.id, account.avatarImages,
                            summary = stringResource(if (community.id == menuState.selected) R.string.spaces_current_community else R.string.spaces_open_saved_channel),
                            selected = community.id == menuState.selected, hasUnread = community.hasUnread, personalUnread = community.personalUnread) { choose(community.id) }
                    }
                    if (menuCommunity != null) {
                        item { HorizontalDivider(Modifier.padding(vertical = 12.dp)); Text(menuCommunity.name, style = MaterialTheme.typography.titleLarge) }
                        val groups = menuState.channels.groupBy { it.categoryId?.let { id -> "id:$id" } ?: "label:${it.profile.category}" }
                        groups.forEach { (category, channels) ->
                            item(key = "category:$category") { Text(channels.first().profile.category.ifBlank { stringResource(R.string.spaces_channels) },
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { heading() }) }
                            items(channels, key = { it.id }) { entry ->
                                NavigationDrawerItem(label = { Text(entry.profile.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                    selected = entry.id == menuState.selectedChannel, onClick = { choose(menuCommunity.id, entry.id) },
                                    icon = { Icon(painterResource(channelKindIcon(entry.profile.kind)), null, Modifier.size(22.dp)) },
                                    badge = if (entry.hasUnread == true || (entry.personalUnread ?: 0) > 0) ({ SpaceUnreadBadge(entry.personalUnread, entry.hasUnread) }) else null)
                            }
                        }
                        if (menuCommunity.canInvite) item { OutlinedButton(onClick = {
                            scope.launch {
                                drawer.close()
                                showInvitation(menuCommunity.id)
                            }
                        }, enabled = !state.working && menuCommunity.canManage, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.spaces_invite))
                        } }
                    }
                    item { TextButton(onClick = { choose(null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_back)) } }
                }
            }
        }) {
        SpacesViewport { height ->
            val shortWindow = height < SHORT_SPACES_WINDOW_DP.dp
            // During the first IME frames the resized viewport can arrive
            // ahead of the inset signal. Compare to the actual configuration
            // so a short landscape or split-screen window keeps navigation.
            val keyboardInLayout = imeVisible || (shortWindow && screenHeight - height >= 96.dp)
            val compactReading = compactConfiguration || shortWindow
            val compactTyping = state.selected != null && keyboardInLayout && height < 290.dp
            val visibleRoom = if (!channelAccessOpen && !membersOpen && !reportsOpen && !searchOpen && !state.loadingChannel && selected != null &&
                channel?.profile?.kind == SpaceChannelKind.VOICE) SpacesAccount.Target(selected.id, channel.id) else null
            val compactRoom = visibleRoom != null && shortWindow
            chrome(keyboardInLayout, visibleRoom, compactRoom)
            if (channelAccessOpen && selected != null) Box(Modifier.weight(1f)) {
                SpaceChannelAccessPage(account, state) { channelAccessRoute = null; informationOpen = true }
            }
            else if (membersOpen && selected != null) SpaceMembersPage(account, state, Modifier.weight(1f),
                onBack = { membersOpen = false; informationOpen = true }, onProfile = onProfile, onMessage = onMessage)
            else if (reportsOpen && selected != null) SpaceReportsPage(account, state, Modifier.weight(1f),
                onBack = { reportsSpace = null; informationOpen = true })
            else if (searchOpen && selected != null) routeStates.SaveableStateProvider("$routeScope/search/${selected.id}") {
                SpaceSearchPage(account, state, selected.id, Modifier.weight(1f),
                    onBack = { searchSpace = null; informationOpen = true }, onOpen = { destination ->
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        val link = destination.link()
                        searchLink = link
                        publicReference = link
                        account.openDestinationLink(link)
                    })
            }
            else if (state.selected == null) routeStates.SaveableStateProvider("$routeScope/overview/${tab.name}") {
                SpacesOverview(account, state, tab, { choose(it) }, { createOpen = true },
                    { clearInvitationReview(); joinOpen = true }, onActivity = { group ->
                        val link = group.target.link()
                        activityLink = link; activityGroup = group
                        publicReference = link
                        account.openDestinationLink(link, group)
                    }, modifier = Modifier.weight(1f)) { AvailabilityNotice(state.availability, state.refreshing, account::refresh) }
            } else {
                val title = channel?.profile?.name ?: stringResource(
                    if (selected?.articleReadDenied == true) R.string.spaces_kind_article else R.string.spaces_kind_chat)
                val menu = stringResource(R.string.spaces_switch_channel)
                if (!compactTyping) Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small,
                    vertical = if (shortWindow) 4.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FrameIconButton(R.drawable.ic_back, stringResource(when {
                        activityReturn -> R.string.spaces_activity_back
                        searchResultOpen -> R.string.spaces_search_back
                        else -> R.string.spaces_back
                    })) {
                        backFromConversation()
                    }
                    Surface(onClick = { focusManager.clearFocus(); keyboardController?.hide(); scope.launch { drawer.open() } }, shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f).semantics { contentDescription = menu; role = Role.Button; heading(); stateDescription = title }) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp,
                            vertical = if (shortWindow) 6.dp else 12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                if (!compactRoom) Text(selected?.name ?: profile?.name.orEmpty(), style = MaterialTheme.typography.labelSmall,
                                    maxLines = if (compactReading) 1 else 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = if (compactReading) 1 else 2, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(20.dp))
                        }
                    }
                    if (!state.loadingChannel && state.pinStatus?.supported == true && channel?.profile?.kind in setOf(SpaceChannelKind.CHAT, SpaceChannelKind.ANNOUNCEMENT))
                        FrameIconButton(R.drawable.ic_pin, stringResource(R.string.spaces_pinned_messages)) { focusManager.clearFocus(); keyboardController?.hide(); pinsRoute = pinConversationRoute }
                    FrameIconButton(R.drawable.ic_info, stringResource(R.string.spaces_information), enabled = !state.destinationReadDenied) { focusManager.clearFocus(); keyboardController?.hide(); informationOpen = true }
                }
                if (!compactTyping) Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvailabilityNotice(state.availability, state.refreshing, account::refresh)
                    if (state.savedDestination || state.availability == SpacesAccount.Availability.OFFLINE && state.contentLoaded) {
                        val checked = state.savedHistoryCheckedAt
                        val detail = if (checked == null) stringResource(R.string.spaces_destination_saved) else
                            stringResource(R.string.spaces_saved_last_checked,
                                android.text.format.DateUtils.formatDateTime(context, checked,
                                    android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_TIME))
                        InlineStatus(detail, StatusTone.INFO)
                    }
                    if (!state.destinationReadDenied) state.selected?.let { state.accessProblems[it] }?.let { AccessNotice(it) }
                    if (state.ownershipSupported && state.ownershipOffer?.member == account.ownerKey) {
                        TextButton(onClick = { focusManager.clearFocus(); keyboardController?.hide(); informationOpen = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_review_ownership_offer)) }
                    }
                    if (selected != null && !selected.joined && (selected.pending || selected.membershipRequested) &&
                        state.accessProblems[selected.id] == null) Text(stringResource(R.string.spaces_confirming_access),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (selected?.joined == true && !selected.dissolved && channel?.ready == false) Text(stringResource(R.string.spaces_waiting_channel_keys),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    key(state.selected, state.selectedChannel) {
                        val archived = remember { MutableTransitionState(false) }
                        archived.targetState = selected?.dissolved == true || channel?.archived == true
                        AnimatedVisibility(archived,
                            enter = fadeIn(tween(AppSpacing.transitionMillis)) + expandVertically(tween(AppSpacing.transitionMillis)),
                            exit = fadeOut(tween(AppSpacing.transitionMillis)) + shrinkVertically(tween(AppSpacing.transitionMillis))) {
                            Text(stringResource(if (selected?.dissolved == true) R.string.spaces_dissolved else R.string.spaces_channel_archived), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (selected?.joined == true && channel?.profile?.kind == SpaceChannelKind.ANNOUNCEMENT && state.threadParent == null && !channel.archived &&
                        state.members.firstOrNull { it.publicKey == account.ownerKey }?.role !in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                        Text(stringResource(R.string.spaces_announcements_read_only), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    selected?.writeRestrictedUntil?.let { end ->
                        val time = remember(end) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(end)) }
                        val label = if (selected.writeRestrictedSpaceWide) R.string.spaces_writing_restricted_space else R.string.spaces_writing_restricted_channel
                        InlineStatus(stringResource(label, time), StatusTone.INFO)
                    }
                }
                if (state.destinationReadDenied) Box(Modifier.weight(1f).fillMaxWidth().padding(AppSpacing.screen),
                    contentAlignment = Alignment.Center) { AccessNotice(SpaceClient.ReadProblem.UNAVAILABLE) }
                else if (state.loadingChannel || selected == null) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state.problem == null) CircularProgressIndicator(Modifier.size(28.dp)) else ProblemNotice(requireNotNull(state.problem), account::clearProblem)
                } else key(selected.id, selected.channel, state.threadParent?.id) {
                    when {
                        selected.deviceReplaced -> InlineStatus(stringResource(R.string.recovery_device_replaced), StatusTone.WARNING)
                        selected.replacingDevice -> InlineStatus(stringResource(R.string.recovery_device_waiting), StatusTone.INFO)
                        selected.replacementRejected -> InlineStatus(stringResource(R.string.recovery_device_rejected), StatusTone.WARNING)
                        selected.archiveScope != null -> InlineStatus(stringResource(R.string.recovery_archive_read_only), StatusTone.INFO)
                    }
                    if (selected.archiveScope != null && !selected.replacingDevice) {
                        TextButton(onClick = { archiveActivationOpen = true }, enabled = state.ready && !state.working) {
                            Text(stringResource(R.string.recovery_replace_device))
                        }
                    }
                    val conversation = channel?.profile?.kind in setOf(SpaceChannelKind.CHAT, SpaceChannelKind.ANNOUNCEMENT) || channel == null
                    if (selected.articleReadDenied) {
                        Box(Modifier.weight(1f).fillMaxWidth().padding(AppSpacing.screen), contentAlignment = Alignment.Center) {
                            AccessNotice(SpaceClient.ReadProblem.UNAVAILABLE)
                        }
                    } else if (channel?.profile?.kind == SpaceChannelKind.ARTICLE) {
                        SpaceArticleChannel(account, state, channel, Modifier.weight(1f), articleEditorGuard::set)
                    } else if (channel?.profile?.kind == SpaceChannelKind.FILE) {
                        SpaceFileChannel(account, state, channel, Modifier.weight(1f))
                    } else if (channel?.profile?.kind == SpaceChannelKind.VOICE) {
                        SpaceRoomChannel(account, state, channel, onBrowse = { account.select(null) }, modifier = Modifier.weight(1f))
                    } else if (state.messages.any { it.isArticle }) {
                        Box(Modifier.weight(1f).fillMaxWidth().padding(AppSpacing.screen), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.spaces_article_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (!conversation) Box(Modifier.weight(1f).fillMaxWidth().padding(AppSpacing.screen), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(painterResource(channelKindIcon(requireNotNull(channel).profile.kind)), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(channel.profile.name, style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(R.string.spaces_channel_not_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val threadParent = state.threadParent
                        val target = SpacesAccount.Target(selected.id, selected.channel, threadParent?.id)
                        val canWrite = if (threadParent == null) selected.canSend else selected.canReply
                        val pinsModal by rememberUpdatedState(pinsOpen)
                        PinnedMessagesSheet(pinsOpen, state.availability, { pinsRoute = null },
                            { after -> account.loadPinnedPage(target, after) }, { reference ->
                                scope.launch { account.guardNavigation {
                                    account.openPinned(target, reference); pinsRoute = null; true
                                } }
                            }, account::refresh)
                        var overlayId by rememberSaveable { mutableStateOf<String?>(null) }
                        var overlayMode by rememberSaveable { mutableStateOf<MessageOverlay?>(null) }
                        var overlayText by rememberSaveable { mutableStateOf("") }
                        var retainedMessage by remember { mutableStateOf<SpaceClient.Message?>(null) }
                        var checkedOverlayId by remember { mutableStateOf<String?>(null) }
                        val overlayMessage = state.messages.firstOrNull { it.id == overlayId } ?: retainedMessage?.takeIf { it.id == overlayId }
                        LaunchedEffect(overlayId, overlayMode, state.messages) {
                            val id = overlayId ?: return@LaunchedEffect
                            if (overlayMode == null) return@LaunchedEffect
                            val resolved = state.messages.firstOrNull { it.id == id } ?: account.loadMessage(target, id)
                            if (resolved != null) retainedMessage = resolved
                            checkedOverlayId = id
                        }
                        if (!compactTyping) threadParent?.let { parent ->
                            val text = parent.text ?: stringResource(
                                if (parent.deleted) R.string.spaces_message_deleted else R.string.spaces_message_unavailable)
                            val back = stringResource(R.string.spaces_back_to_channel)
                            Surface(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = 8.dp),
                                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                if (compactReading) {
                                    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        FrameIconButton(R.drawable.ic_back, back, onClick = account::closeThread)
                                        Column(Modifier.weight(1f).padding(end = 8.dp, top = 4.dp, bottom = 4.dp)) {
                                            Text(registeredIdentity(parent.author).name,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                                            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                } else {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FrameIconButton(R.drawable.ic_back, back, onClick = account::closeThread)
                                            Text(stringResource(R.string.spaces_thread), style = MaterialTheme.typography.titleSmall)
                                        }
                                        Text(registeredIdentity(parent.author).name, style = MaterialTheme.typography.labelMedium)
                                        Text(text, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        val showEarlier = state.hasEarlier && !compactTyping
                        val earlierInList = showEarlier && compactReading && state.exactDestination && state.messages.isNotEmpty()
                        if (showEarlier && !earlierInList) {
                            EarlierSpaceMessages(account::olderMessages, !state.working,
                                Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen))
                        }
                        val leadingItems = if (earlierInList) 1 else 0
                        val anchorIndex = state.anchor?.let { anchor -> state.messages.indexOfFirst { it.id == anchor.messageId } } ?: -1
                        val initialIndex = leadingItems + if (anchorIndex >= 0) anchorIndex else state.messages.size
                        val list = remember { androidx.compose.foundation.lazy.LazyListState(initialIndex, if (anchorIndex >= 0) state.anchor?.offset?.coerceAtLeast(0) ?: 0 else 0) }
                        var initialPage by remember { mutableStateOf(true) }
                        var autoScrolling by remember { mutableStateOf(false) }
                        var lastRead by remember { mutableStateOf<Long?>(null) }
                        val latestMessages by rememberUpdatedState(remember(state.messages) { state.messages.associateBy { it.id } })
                        val latestWindow by rememberUpdatedState(state.windowFrom)
                        val latestHasLater by rememberUpdatedState(state.hasLater)
                        val latestFollowing by rememberUpdatedState(state.followingLatest)
                        val exactDestination by rememberUpdatedState(state.exactDestination)
                        val keyboardBottom = WindowInsets.ime.getBottom(LocalDensity.current)
                        val viewportSize by remember(list) { derivedStateOf { list.layoutInfo.viewportSize } }
                        val messageBody = remember { BringIntoViewRequester() }
                        LaunchedEffect(list, account) {
                            var userScrolling = false
                            snapshotFlow {
                                val visible = list.layoutInfo.visibleItemsInfo
                                // visibleItemsInfo may also include a prior item inside content padding.
                                // The saved offset belongs to firstVisibleItemIndex, so pair its actual key.
                                val first = visible.firstOrNull { it.index == list.firstVisibleItemIndex }
                                    ?.let { latestMessages[it.key as? String] }
                                val read = visible.mapNotNull { item -> latestMessages[item.key as? String]?.position }.maxOrNull()
                                val anchor = first?.position?.let { position ->
                                    SpaceClient.ReadingAnchor(first.id, position, list.firstVisibleItemScrollOffset, latestWindow)
                                }
                                ChannelViewport(anchor, !(list.canScrollForward || list.isScrollInProgress), autoScrolling || initialPage || overlayMode != null || pinsModal, list.isScrollInProgress, read)
                            }.collect { viewport ->
                                if (viewport.automatic) userScrolling = false else {
                                    if (viewport.scrolling) userScrolling = true
                                    // Row acceptance, reactions and IME resizing can move
                                    // the viewport without an intentional reading gesture.
                                    // Queued rows lack a durable position. A drag
                                    // through them still stops automatic following.
                                    if (userScrolling || (!exactDestination && viewport.anchor != null && !latestFollowing))
                                        account.readingPosition(selected.id, selected.channel,
                                            viewport.followLatest && !latestHasLater, viewport.anchor, target.thread, moving = viewport.scrolling)
                                    if (!viewport.scrolling) userScrolling = false
                                    viewport.read?.let { position -> if (lastRead == null || position > requireNotNull(lastRead)) {
                                        lastRead = position; account.markRead(selected.id, selected.channel, position, target.thread)
                                    } }
                                }
                            }
                        }
                        LaunchedEffect(state.messages.map { it.id }, state.messages.lastOrNull(), state.scrollToMessage, state.followingLatest, keyboardBottom, viewportSize, overlayMode, pinsOpen, leadingItems) {
                            if (overlayMode != null || pinsOpen) return@LaunchedEffect
                            val requested = state.scrollToMessage
                            val requestedIndex = state.messages.indexOfFirst { it.id == requested?.id }
                            if (requestedIndex >= 0) {
                                autoScrolling = true
                                val destinationIndex = leadingItems +
                                    if (state.followingLatest && requestedIndex == state.messages.lastIndex) state.messages.size else requestedIndex
                                try {
                                    list.animateScrollToItem(destinationIndex)
                                    if (requested?.showBody == true) messageBody.bringIntoView()
                                } finally { autoScrolling = false }
                                initialPage = false
                                account.consumeScrollTarget(requireNotNull(requested))
                                return@LaunchedEffect
                            }
                            if (state.messages.isNotEmpty() && initialPage) {
                                val index = state.anchor?.let { anchor -> state.messages.indexOfFirst { it.id == anchor.messageId } } ?: -1
                                autoScrolling = true
                                try { list.scrollToItem(leadingItems + if (index >= 0) index else state.messages.size,
                                    if (index >= 0) state.anchor?.offset ?: 0 else 0) }
                                finally { autoScrolling = false }
                                initialPage = false
                            } else if (state.messages.isNotEmpty() && state.followingLatest) {
                                // A replaced automatic animation may still be unwinding.
                                // Keep the follow request until idle; a deliberate scroll
                                // changes followingLatest and cancels this keyed effect.
                                snapshotFlow { list.isScrollInProgress }.first { !it }
                                if (!latestFollowing || overlayMode != null || pinsModal) return@LaunchedEffect
                                autoScrolling = true
                                try { list.animateScrollToItem(leadingItems + state.messages.size) } finally { autoScrolling = false }
                            }
                            if (state.messages.isNotEmpty() && latestFollowing) {
                                // Reaction chips can still be expanding after a message
                                // update has settled. Follow the rendered bottom as its
                                // geometry changes, even when the message ID is unchanged.
                                snapshotFlow {
                                    if (!list.isScrollInProgress && list.canScrollForward)
                                        list.layoutInfo.visibleItemsInfo.lastOrNull()?.let { Triple(it.index, it.offset, it.size) }
                                    else null
                                }.filterNotNull().collect {
                                    if (latestFollowing && overlayMode == null && !pinsModal && !list.isScrollInProgress) {
                                        autoScrolling = true
                                        try { list.scrollToItem(leadingItems + state.messages.size) } finally { autoScrolling = false }
                                    }
                                }
                            }
                        }
                        val reportingAvailable = selected.joined && !selected.dissolved && account.moderationReportsSupported()
                        if (state.messages.isEmpty() && !state.contentLoaded) Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().padding(AppSpacing.screen), contentAlignment = Alignment.Center) {
                            if (state.refreshing) CircularProgressIndicator(Modifier.size(28.dp))
                            else Text(stringResource(R.string.spaces_history_not_loaded), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (state.messages.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().padding(AppSpacing.screen), contentAlignment = Alignment.Center) {
                            Text(stringResource(if (state.hasEarlier) R.string.spaces_no_recent_messages else R.string.spaces_no_messages),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
                            contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (earlierInList) item(key = "spaces-earlier-messages") {
                                EarlierSpaceMessages(account::olderMessages, !state.working, Modifier.fillMaxWidth())
                            }
                            items(state.messages, key = { it.id }) { message ->
                                val accepted = !message.pending && !message.rejected && !message.cancelled
                                val own = message.author == account.ownerKey
                                val showBody = state.scrollToMessage?.let { it.id == message.id && it.showBody } == true
                                MessageRow(message, state.draft.isEmpty() && !state.working,
                                    bodyModifier = if (showBody) Modifier.bringIntoViewRequester(messageBody) else Modifier,
                                    restore = { account.restoreUnsentDraft(message.id) },
                                    reply = if (accepted && threadParent == null && (!message.deleted || message.replyCount > 0))
                                        ({ account.openThread(message) }) else null,
                                    canEdit = accepted && !message.deleted && own && canWrite,
                                    canRemove = accepted && !message.deleted && canWrite && (own || selected.canManage),
                                    canReport = accepted && !message.deleted && reportingAvailable,
                                    canCopyLink = accepted && !message.deleted && message.position != null,
                                    canCancel = message.pending && !message.attempted,
                                    openOverlay = { mode ->
                                        retainedMessage = message; overlayId = message.id
                                        overlayText = message.change?.text ?: message.text.orEmpty()
                                        overlayMode = mode
                                    },
                                    react = if (accepted && !message.deleted && selected.canReact && message.ownReactionsAvailable) ({ emoji -> account.toggleReaction(target, message, emoji) }) else null,
                                    cancelChange = { id, done -> account.cancelChange(target, id, done) },
                                    working = state.working)
                            }
                            // A short final item gives following an actual bottom target,
                            // including when the last message is taller than the viewport.
                            item(key = "spaces-conversation-end") { Spacer(Modifier.height(1.dp)) }
                        }
                        val acceptedOverlay = overlayMessage?.let { !it.pending && !it.rejected && !it.cancelled } == true
                        val editableOverlay = acceptedOverlay && overlayMessage?.deleted == false && overlayMessage.author == account.ownerKey && canWrite
                        MessageOverlays(overlayMessage, overlayMode, overlayText, { overlayText = it }, { overlayMode = null },
                            loadingTarget = overlayMessage == null && overlayId != null && overlayId != checkedOverlayId,
                            selectMode = { mode ->
                                if (mode == MessageOverlay.EDIT) overlayText = overlayMessage?.change?.text ?: overlayMessage?.text.orEmpty()
                                overlayMode = mode
                            },
                            edit = if (editableOverlay) ({ text, done -> account.editMessage(target, requireNotNull(overlayMessage), text, done) }) else null,
                            remove = if (acceptedOverlay && overlayMessage?.deleted == false && canWrite && (overlayMessage.author == account.ownerKey || selected.canManage))
                                ({ done -> account.deleteMessage(target, overlayMessage, done) }) else null,
                            report = if (acceptedOverlay && overlayMessage?.deleted == false && reportingAvailable)
                                ({ reason -> account.reportMessage(target, overlayMessage.id, reason) }) else null,
                            copyLink = if (acceptedOverlay && overlayMessage?.deleted == false && overlayMessage.position != null)
                                ({ account.destinationLink(target, SpaceDestination.Kind.MESSAGE, overlayMessage.id) }) else null,
                            cancel = if (overlayMessage?.pending == true && !overlayMessage.attempted)
                                ({ done -> account.cancelSend(target, overlayMessage.id, done) }) else null,
                            react = if (acceptedOverlay && overlayMessage?.deleted == false && selected.canReact && overlayMessage.ownReactionsAvailable)
                                ({ emoji, onSaved -> account.toggleReaction(target, overlayMessage, emoji, onSaved) }) else null,
                            pin = if (acceptedOverlay && overlayMessage?.deleted == false && overlayMessage.pinAvailable && selected.canPin && !overlayMessage.pinPending)
                                ({ done -> account.pinMessage(target, overlayMessage, !overlayMessage.pinned, done) }) else null,
                            cancelPin = overlayMessage?.pinChange?.takeIf { !it.attempted && !it.rejected && !it.cancelled }?.let { change ->
                                { done -> account.cancelChange(target, change.operationId, done) }
                            },
                            loadReactions = overlayMessage?.let { message -> { after -> account.loadReactionPage(target, message, after) } },
                            working = state.working)
                        if ((state.hasLater || !state.followingLatest && !state.exactDestination) && !compactTyping) {
                            val newerActivity = stringResource(R.string.spaces_newer_messages_available)
                            OutlinedButton(onClick = account::latestMessages,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen).heightIn(min = 48.dp)
                                    .semantics { if (state.hasLater) stateDescription = newerActivity }) {
                                Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                                    if (state.hasLater) Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.spaces_latest_messages))
                            }
                        }
                        if (!compactTyping) state.problem?.let { ProblemNotice(it, account::clearProblem) }
                        if (selected.dissolved) {
                            if (state.draft.isNotEmpty()) OutlinedTextField(state.draft, {}, readOnly = true,
                                label = { Text(stringResource(R.string.spaces_saved_draft)) }, maxLines = if (compactReading) 1 else 4,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = 8.dp))
                        } else if (profile?.visibility != SpaceVisibility.PUBLIC || selected.joined) {
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen,
                                vertical = if (compactTyping) 0.dp else 8.dp), verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val edit = { draft: String -> account.editDraft(selected.id, selected.channel, draft, target.thread) }
                                val enabled = !state.savingNavigation && !state.loadingChannel
                                val hint = stringResource(if (threadParent == null) R.string.spaces_message_channel_hint
                                    else R.string.spaces_reply_thread_hint, channel?.profile?.name ?: "general")
                                // Keep the same field while the keyboard changes the available height.
                                if (screenHeight < SHORT_SPACES_WINDOW_DP.dp)
                                    CompactWritingField(state.draft, edit, enabled, hint, Modifier.weight(1f), maxLines = 1)
                                else OutlinedTextField(state.draft, edit, Modifier.weight(1f),
                                    enabled = enabled, placeholder = {
                                        Text(hint, maxLines = if (compactReading) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                                    }, shape = RoundedCornerShape(18.dp), minLines = 1, maxLines = if (compactConfiguration) 2 else 4)
                                FilledIconButton(onClick = account::send, enabled = canWrite && state.draft.isNotBlank() && !state.working &&
                                    state.accessProblems[selected.id] == null, modifier = Modifier.size(48.dp)) {
                                    Icon(painterResource(R.drawable.ic_send), stringResource(R.string.spaces_send), Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
                if (selected != null && !state.loadingChannel && profile?.visibility == SpaceVisibility.PUBLIC && !selected.joined && !selected.dissolved) {
                    Column(Modifier.fillMaxWidth().padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.spaces_public_browsing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val banned = account.ownerKey in state.bannedMembers
                        if (banned) Text(stringResource(R.string.spaces_banned_join_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { if (profile.houseRules.isEmpty()) account.joinPublic(state.publicRulesToken) else publicRulesOpen = true },
                            enabled = !banned && !state.working && !selected.pending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(if (selected.pending) R.string.spaces_joining_public else R.string.spaces_join_public))
                        }
                    }
                }
            }
            if (!keyboardInLayout && !compactRoom) SpacesBottomBar(tab, compactHeight = shortWindow) { selectTab(it) }
        }
    }
    val referenceUri = publicReference?.let(android.net.Uri::parse)
    val openingArticle = SpacesLaunchRoutes.isArticle(referenceUri)
    val openingDestination = SpacesLaunchRoutes.isDestination(referenceUri)
    val referenceTitle = when {
        openingDestination -> R.string.spaces_destination_open
        openingArticle -> R.string.spaces_article_open
        else -> R.string.spaces_open_public_community
    }
    DesignSheet(publicReference != null, stringResource(referenceTitle),
        { publicReference = null; account.dismissPublicReference() }) { first ->
        val node = state.requiredNode
        val choice = nl.davidengelen.redsquare.saito.SaitoBackend.entries.firstOrNull { it.webUrl == node }
        when {
            state.working || openingDestination && state.projectingDestination -> {
                Text(stringResource(when {
                    openingDestination -> R.string.spaces_destination_opening
                    openingArticle -> R.string.spaces_article_opening
                    else -> R.string.spaces_opening_community
                }))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            choice != null -> {
                Text(stringResource(R.string.spaces_different_node, choice.domain))
                Button(onClick = { publicReference?.let { onOpenNode(choice.webUrl, it) } }, enabled = !state.savingNavigation,
                    modifier = first.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_switch_node_open)) }
            }
            node != null -> Text(stringResource(R.string.spaces_unknown_reference_node), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.previewIssue != null -> AccessNotice(requireNotNull(state.previewIssue))
            openingArticle && state.problem == SpacesAccount.Problem.ARTICLE_MISSING ->
                Text(stringResource(R.string.spaces_article_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            openingDestination && state.problem == SpacesAccount.Problem.DESTINATION_MISSING ->
                Text(stringResource(R.string.spaces_destination_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                Text(stringResource(when {
                    openingDestination -> R.string.spaces_destination_failed
                    openingArticle -> R.string.spaces_article_link_failed
                    else -> R.string.spaces_public_link_failed
                }), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { publicReference?.let { link ->
                    when {
                        openingDestination -> account.openDestinationLink(link, activityGroup?.takeIf { activityLink == link },
                            fromNotification = activityLink == link && activityGroup == null)
                        openingArticle -> account.openArticleLink(link)
                        else -> account.openPublicLink(link)
                    }
                } },
                    modifier = first.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_retry)) }
            }
        }
    }
    SpaceCreationSheet(createOpen, account, state) { createOpen = false }
    var acceptedInvitation by remember(invitationReview?.spaceId, invitationReview?.rulesToken) { mutableStateOf(false) }
    val expiresAt = invitationReview?.expiresAt ?: 0
    var invitationExpired by remember(expiresAt) { mutableStateOf(expiresAt in 1L..System.currentTimeMillis()) }
    LaunchedEffect(joinOpen, expiresAt) {
        while (joinOpen && expiresAt > 0 && !invitationExpired) {
            val remaining = expiresAt - System.currentTimeMillis()
            if (remaining <= 0) invitationExpired = true else delay(minOf(remaining, 60_000L))
        }
    }
    val invitationIssue = if (invitationExpired) SpaceClient.ReadProblem.EXPIRED else state.previewIssue
    DesignSheet(joinOpen, stringResource(if (replacingDevice) R.string.recovery_replace_device else R.string.spaces_join_invitation),
        { joinOpen = false; clearInvitationReview() },
        contentKey = invitationReview?.spaceId, footer = { first ->
            val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
            val rulesAccepted = invitationReview?.profile?.houseRules.isNullOrEmpty() || acceptedInvitation
            Button(onClick = {
                focus.clearFocus(); keyboard?.hide()
                if (replacingDevice) scope.launch {
                    if (publishing.beforePublishing()) {
                        account.replaceDevice(invitationText.trim(), expectProtectedChat = false)
                    }
                }
                else if (invitationReview == null) account.preview(invitationText.trim()) else account.join(invitationText.trim(), invitationReview?.rulesToken)
            }, enabled = joinOpen && invitationText.isNotBlank() && !state.working && rulesAccepted &&
                (invitationReview == null || state.preview != null && invitationIssue == null),
                modifier = (if (invitationReview != null) first else Modifier).fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(when {
                    replacingDevice -> R.string.recovery_replace_confirm
                    invitationReview == null -> R.string.spaces_preview
                    else -> R.string.spaces_join
                }))
            }
            if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
        }) { first ->
        Text(stringResource(if (replacingDevice) R.string.recovery_replace_device_detail else R.string.spaces_join_detail),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.spaces_join_as, registeredIdentity(account.ownerKey).name), style = MaterialTheme.typography.labelLarge)
        AnimatedContent(invitationReview, label = "spaces-invitation-review", transitionSpec = {
            fadeIn(tween(AppSpacing.transitionMillis)) togetherWith fadeOut(tween(AppSpacing.transitionMillis))
        }) { preview ->
            if (preview == null) OutlinedTextField(invitationText, { invitationText = it.take(8192); clearInvitationReview() }, first.fillMaxWidth(),
                label = { Text(stringResource(R.string.spaces_invitation_link)) }, maxLines = 4, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (invitationText.isNotBlank() && !state.working) account.preview(invitationText.trim()) }))
            else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SpaceAvatar(preview.profile, preview.spaceId, account.avatarImages, Modifier.size(56.dp))
                    Text(preview.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                if (preview.profile.description.isNotBlank()) Text(preview.profile.description)
                if (preview.expiresAt > 0) Text(stringResource(R.string.spaces_invitation_expires,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(preview.expiresAt))),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(if (preview.profile.visibility == SpaceVisibility.PUBLIC) R.string.spaces_invitation_public_detail else R.string.spaces_invitation_bearer_detail),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (preview.profile.houseRules.isNotEmpty()) {
                    Text(stringResource(R.string.spaces_house_rules), style = MaterialTheme.typography.titleMedium)
                    Text(preview.profile.houseRules)
                    RulesAcceptance(acceptedInvitation, { acceptedInvitation = it })
                }
                TextButton(onClick = ::clearInvitationReview, enabled = !state.working) { Text(stringResource(R.string.spaces_change_invitation)) }
            }
        }
        invitationIssue?.let { issue -> Text(stringResource(if (issue == SpaceClient.ReadProblem.EXPIRED)
            R.string.spaces_invitation_expired else R.string.spaces_expiry_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (invitationReview == null && !replacingDevice) TextButton(onClick = {
            clearInvitationReview()
            replacingDevice = true
        }, enabled = !state.working) { Text(stringResource(R.string.recovery_replace_device)) }
        if (invitationReview == null && !replacingDevice) TextButton(onClick = {
            clearInvitationReview()
            joinOpen = false
            archivePickerOpen = true
        }, enabled = !state.working) { Text(stringResource(R.string.recovery_archive_title)) }
        state.problem?.let { ProblemNotice(it, account::clearProblem) }
    }
    if (archivePickerOpen) RecoveredHistoryPicker(account, state, protectedChat = false) { archivePickerOpen = false }
    if (archiveActivationOpen) selected?.archiveScope?.let { archive ->
        ReplaceArchivedDevicePrompt(dismiss = { archiveActivationOpen = false }, confirm = {
            scope.launch {
                if (publishing.beforePublishing()) {
                    archiveActivationOpen = false
                    account.replaceDevice(archive, expectProtectedChat = false)
                }
            }
        })
    }
    var acceptedPublicRules by remember(state.selected, state.publicRulesToken) { mutableStateOf(false) }
    DesignSheet(publicRulesOpen && selected != null, stringResource(R.string.spaces_house_rules), { publicRulesOpen = false }, footer = { first ->
        Button(onClick = { account.joinPublic(state.publicRulesToken) }, enabled = account.ownerKey !in state.bannedMembers && acceptedPublicRules && !state.working && selected?.pending != true,
            modifier = first.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_join_public)) }
    }) {
        profile?.let { Text(it.name, style = MaterialTheme.typography.titleLarge); Text(it.houseRules) }
        RulesAcceptance(acceptedPublicRules, { acceptedPublicRules = it })
        state.problem?.let { ProblemNotice(it, account::clearProblem) }
    }
    InvitationSheet(inviteOpen, state.invitation, state.working, selected?.canManage == true,
        { invitationSpace = null }, account::refresh,
        { days -> selected?.let { account.invite(it.id, days) } },
        { selected?.let { account.revokeInvitation(it.id) } })
    LaunchedEffect(informationOpen, selected?.id) { if (informationOpen && selected != null) informationHidden = false }
    LaunchedEffect(channelEditorPending, informationHidden, state.ready, state.loading, state.loadingChannel,
        state.selected, state.selectedChannel, account) {
        if (channelEditorPending && informationHidden) {
            if (channelEditor.owner != account.ownerKey || channelEditor.node != account.backend.webUrl) channelEditorPending = false
            else if (state.ready && !state.loading && !state.loadingChannel && state.selected != null && state.selectedChannel != null) {
                channelEditorPending = false
                if (channelEditor.matches(account, state)) channelEditor.open = true
            }
        }
    }
    LaunchedEffect(categoryEditorPending, informationHidden, state.ready, state.loading, state.loadingChannel, state.selected, account) {
        if (categoryEditorPending && informationHidden) {
            if (categoryEditor.owner != account.ownerKey || categoryEditor.node != account.backend.webUrl) categoryEditorPending = false
            else if (state.ready && !state.loading && !state.loadingChannel && state.selected != null) {
                categoryEditorPending = false
                if (categoryEditor.matches(account, state)) categoryEditor.open = true
            }
        }
    }
    DesignSheet(informationOpen && selected != null && !state.destinationReadDenied, stringResource(R.string.spaces_information), { informationOpen = false },
        footer = { first ->
            TextButton(onClick = { informationOpen = false }, modifier = first.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.spaces_close))
            }
        }, onHidden = {
            informationHidden = true
            invitationAfterInformation?.let { space ->
                invitationAfterInformation = null
                showInvitation(space)
            }
        }) {
        if (selected != null && profile != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SpaceAvatar(profile, selected.id, account.avatarImages, Modifier.size(64.dp))
                Text(profile.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
            if (profile.description.isNotBlank()) Text(profile.description)
            Text(stringResource(when {
                selected.dissolved -> R.string.spaces_dissolved_detail
                profile.visibility == SpaceVisibility.PUBLIC -> R.string.spaces_public_information
                state.channels.any { it.visibility == SpaceVisibility.PUBLIC } -> R.string.spaces_private_public_archives_information
                else -> R.string.spaces_private_information
            }),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected.joined) Text(stringResource(R.string.spaces_member_confirmed), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.notificationPolicy?.let { policy ->
                val target = SpacesAccount.Target(selected.id, selected.channel, state.threadParent?.id)
                ChannelNotificationPreferences(channel?.profile?.name ?: "general", policy,
                    enabled = !state.working && !state.savingNavigation,
                    chooseMode = { account.setNotificationPolicy(target, mode = it) },
                    allowEveryone = { account.setNotificationPolicy(target, allowEveryone = it) })
            }
            OutlinedButton(onClick = {
                informationOpen = false
                searchSpace = selected.id
                searchResultOpen = false
            }, enabled = state.ready && !state.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_search), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.spaces_search_title))
            }
            if (channel != null && !state.loadingChannel) {
                val target = SpacesAccount.Target(selected.id, selected.channel, state.threadParent?.id)
                val kind = if (channel.profile.kind == SpaceChannelKind.VOICE) SpaceDestination.Kind.ROOM else SpaceDestination.Kind.CHANNEL
                SpaceDestinationCopyButton(
                    if (kind == SpaceDestination.Kind.ROOM) R.string.spaces_copy_room_link else R.string.spaces_copy_channel_link,
                    enabled = !state.working && !state.savingNavigation) { account.destinationLink(target, kind) }
            }
            if (profile.houseRules.isNotEmpty()) { Text(stringResource(R.string.spaces_house_rules), style = MaterialTheme.typography.titleMedium); Text(profile.houseRules) }
            state.publicLink?.takeUnless { selected.dissolved }?.let { link ->
                OutlinedButton(onClick = {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(profile.name, link)); publicCopied = true; publicShareFailed = false
                    } catch (_: Exception) { publicShareFailed = true }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (publicCopied) R.string.spaces_copied else R.string.spaces_copy_public_link))
                }
                OutlinedButton(onClick = {
                    try {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link) }, null))
                        publicShareFailed = false
                    } catch (_: Exception) { publicShareFailed = true }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_share_public_link)) }
                if (publicShareFailed) Text(stringResource(R.string.spaces_share_failed), color = MaterialTheme.colorScheme.error)
            }
            if (state.members.any { it.publicKey == account.ownerKey && it.role == SpaceRole.OWNER } &&
                profile.visibility == SpaceVisibility.PUBLIC && !selected.dissolved) {
                val discoverable = stringResource(R.string.spaces_discoverable)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(discoverable)
                        Text(stringResource(R.string.spaces_unlisted_detail), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(state.listingIntent ?: profile.listed, { account.setListed(selected.id, it) },
                        modifier = Modifier.semantics { contentDescription = discoverable },
                        enabled = selected.canManage && !state.working && !state.savingNavigation)
                }
            }
            if (state.listingIntent != null) Text(stringResource(R.string.spaces_saving_listing), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected.canInvite) OutlinedButton(onClick = {
                invitationAfterInformation = selected.id
                informationOpen = false
            },
                enabled = !state.working && selected.canManage, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_invite)) }
            OutlinedButton(onClick = {
                scope.launch {
                    val commit: suspend () -> Boolean = { informationOpen = false; membersOpen = true; true }
                    navigation?.navigate(commit) ?: account.guardNavigation(commit)
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_members)) }
            if (account.moderationReportsSupported() && selected.joined && account.canReviewModeration(selected.id, state)) OutlinedButton(onClick = {
                scope.launch {
                    val commit: suspend () -> Boolean = { informationOpen = false; reportsSpace = selected.id; true }
                    navigation?.navigate(commit) ?: account.guardNavigation(commit)
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_reports_and_log)) }
            if (selected.joined && !selected.dissolved && state.discoverySupported == true &&
                state.members.any { it.publicKey == account.ownerKey && it.role in setOf(SpaceRole.OWNER, SpaceRole.ADMIN) })
                OutlinedButton(onClick = {
                    focusManager.clearFocus(); keyboardController?.hide()
                    channelCreator.show(account, selected.id, state.channelCreation); informationOpen = false
                }, enabled = !state.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_add_channel)) }
            if (selected.joined && !selected.dissolved && state.categoriesSupported && state.members.any {
                it.publicKey == account.ownerKey && it.role in setOf(SpaceRole.OWNER, SpaceRole.ADMIN)
            }) OutlinedButton(onClick = {
                focusManager.clearFocus(); keyboardController?.hide()
                categoryEditor.show(account, state); categoryEditor.open = false; categoryEditorPending = true
                informationOpen = false
            }, enabled = !state.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.spaces_categories))
            }
            channel?.let { chosen -> SpaceChannelManagement(account, state, chosen, onAccess = {
                focusManager.clearFocus(); keyboardController?.hide()
                informationOpen = false; channelAccessRoute = "${selected.id}:${chosen.id}"
            }) {
                focusManager.clearFocus(); keyboardController?.hide()
                channelEditor.show(account, selected.id, chosen, state.channelEdit)
                channelEditor.open = false; channelEditorPending = true
                informationOpen = false
            } }
            SpaceVisibilityControls(account, state)
            SpaceOwnershipOfferControls(account, state)
            SpaceLeaveControls(account, state)
            SpaceDissolutionControls(account, state)
            SpaceManagementProblem(state.problem, account::clearProblem, scrollIntoView = true)
        }
    }
    SpaceCategorySheet(categoryEditor, account, state) { informationOpen = true }
    SpaceChannelEditSheet(channelEditor, account, state) { informationOpen = true }
    SpaceChannelCreationSheet(channelCreator, account, state, { informationOpen = true }, { space, channelId ->
        val commit: suspend () -> Boolean = {
            if (!channelCreator.open || !channelCreator.matches(account, account.state.value)) false
            else { choose(space, channelId); true }
        }
        navigation?.navigate(commit) ?: account.guardNavigation(commit)
    })
}

private data class ChannelViewport(val anchor: SpaceClient.ReadingAnchor?, val followLatest: Boolean, val automatic: Boolean, val scrolling: Boolean, val read: Long?)
@Composable private fun RulesAcceptance(accepted: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(accepted, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Checkbox(accepted, onCheckedChange = null)
        Text(stringResource(R.string.spaces_accept_rules), modifier = Modifier.weight(1f))
    }
}
@Composable private fun AccessNotice(problem: SpaceClient.ReadProblem) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(stringResource(if (problem == SpaceClient.ReadProblem.EXPIRED) R.string.spaces_invitation_expired else R.string.spaces_access_unavailable),
            Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Read the adjusted viewport before reserving space for the bottom bar.
 * During IME entry the window can shrink before Compose receives visibility. */
@Composable
private fun SpacesViewport(content: @Composable ColumnScope.(height: Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        Column(Modifier.fillMaxSize()) { content(height) }
    }
}

private enum class MessageOverlay { ACTIONS, REACTION_PICKER, EDIT, DELETE, CANCEL, REACTIONS, REPORT }

@Composable
private fun EarlierSpaceMessages(onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.spaces_earlier_messages))
    }
}

@Composable private fun MessageRow(message: SpaceClient.Message, canRestore: Boolean, bodyModifier: Modifier, restore: () -> Unit,
    reply: (() -> Unit)?, canEdit: Boolean, canRemove: Boolean, canReport: Boolean, canCopyLink: Boolean, canCancel: Boolean, openOverlay: (MessageOverlay) -> Unit,
    cancelChange: ((String, (Boolean) -> Unit) -> Unit)?, react: ((String) -> Unit)?, working: Boolean) {
    val savedEdit = message.change?.takeIf { it.kind == SpaceClient.MessageChangeKind.EDIT }?.text
    val name = registeredIdentity(message.author).name
    val time = remember(message.createdAt) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAt)) }
    val measuredBody = if (nl.davidengelen.redsquare.BuildConfig.SPACES_SCALE_FIXTURE) {
        val hostView = LocalView.current
        bodyModifier.onGloballyPositioned { position ->
            SpacesScaleMeasurement.rowGeometry(hostView, message.text, position.boundsInWindow())
        }
    } else bodyModifier
    var changeCancelTooLate by remember(message.change?.operationId) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().testTag("spaces-message"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileAvatar(message.author, Modifier.size(38.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (canEdit || canRemove || canReport || canCopyLink || savedEdit != null || message.pinChange != null)
                    FrameIconButton(R.drawable.ic_more, stringResource(R.string.spaces_message_actions)) { openOverlay(MessageOverlay.ACTIONS) }
            }
            Text(message.text ?: stringResource(if (message.deleted) R.string.spaces_message_deleted else if (message.missingKeys) R.string.spaces_missing_keys else R.string.spaces_message_unavailable),
                modifier = if (message.deleted) measuredBody.testTag("spaces-deleted-${message.id}") else measuredBody,
                style = if (message.deleted) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
                color = if (message.text != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (message.cancelled) stringResource(R.string.spaces_send_cancelled)
                    else if (message.rejected) stringResource(R.string.spaces_not_sent)
                    else if (message.pending) stringResource(if (message.attempted) R.string.spaces_unconfirmed else R.string.spaces_message_waiting_connection)
                    else if (message.edited && !message.deleted) stringResource(R.string.spaces_edited_at, time) else time,
                    style = MaterialTheme.typography.labelSmall)
                if (!message.deleted && (message.pinned || message.pinChange != null)) {
                    val status = message.pinChange?.let(::pinChangeLabel) ?: R.string.spaces_pinned
                    Icon(painterResource(R.drawable.ic_pin), stringResource(status), Modifier.size(14.dp),
                        tint = if (message.pinChange?.rejected == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            message.change?.let { change ->
                val status = when {
                    change.cancelled -> if (change.kind == SpaceClient.MessageChangeKind.EDIT) R.string.spaces_edit_cancelled else R.string.spaces_deletion_cancelled
                    change.rejected -> if (change.kind == SpaceClient.MessageChangeKind.EDIT) R.string.spaces_edit_not_accepted else R.string.spaces_delete_not_accepted
                    change.attempted -> R.string.spaces_change_awaiting_confirmation
                    change.kind == SpaceClient.MessageChangeKind.EDIT -> R.string.spaces_edit_waiting_connection
                    else -> R.string.spaces_delete_waiting_connection
                }
                Text(stringResource(status), style = MaterialTheme.typography.labelSmall,
                    color = if (change.rejected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (cancelChange != null && !change.cancelled && !change.rejected && !change.attempted)
                    TextButton(onClick = { cancelChange(change.operationId) { stopped -> changeCancelTooLate = !stopped } },
                        enabled = !working, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_cancel_change)) }
                if (changeCancelTooLate && change.attempted && !change.rejected && !change.cancelled)
                    Text(stringResource(R.string.spaces_change_cancel_too_late), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (message.deleted) {
                if (reply != null) ThreadReplyButton(message.replyCount, reply)
            } else if (reply != null || react != null || message.reactions.isNotEmpty() || message.reactionsHaveMore)
                MessageReactions(message, react, reply,
                    openPicker = { openOverlay(MessageOverlay.REACTION_PICKER) }, openDetails = { openOverlay(MessageOverlay.REACTIONS) })
            if (canCancel) TextButton(onClick = { openOverlay(MessageOverlay.CANCEL) }, enabled = !working,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_cancel_send)) }
            if ((message.rejected || message.cancelled) && message.text != null) TextButton(onClick = restore, enabled = canRestore,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_restore_draft)) }
        }
    }
}

/** Conversation-owned modal host. Neither IME resizing nor lazy row disposal
 * owns this input session or the paged reaction sheet. */
@Composable private fun MessageOverlays(message: SpaceClient.Message?, mode: MessageOverlay?, editedText: String,
    onText: (String) -> Unit, dismiss: () -> Unit, loadingTarget: Boolean, selectMode: (MessageOverlay) -> Unit, edit: ((String, () -> Unit) -> Unit)?,
    remove: ((() -> Unit) -> Unit)?, report: (suspend (String) -> Unit)?, copyLink: (suspend () -> String)?, cancel: (((Boolean) -> Unit) -> Unit)?,
    react: ((String, () -> Unit) -> Unit)?, pin: ((() -> Unit) -> Unit)?, cancelPin: (((Boolean) -> Unit) -> Unit)?,
    loadReactions: (suspend (String?) -> SpaceClient.ReactionPage)?, working: Boolean) {
    val context = LocalContext.current
    var editCopied by remember(message?.id) { mutableStateOf(false) }
    var editCopyFailed by remember(message?.id) { mutableStateOf(false) }
    var cancelTooLate by remember(message?.id) { mutableStateOf(false) }
    var pinCancelTooLate by remember(message?.pinChange?.operationId) { mutableStateOf(false) }
    val savedEditLabel = stringResource(R.string.spaces_saved_edit)
    LaunchedEffect(editedText) { editCopied = false; editCopyFailed = false }
    LaunchedEffect(mode) { if (mode != MessageOverlay.CANCEL) cancelTooLate = false }
    if (mode == MessageOverlay.REPORT) SpaceReportSheet(message?.id, report, dismiss)
    if (mode == MessageOverlay.CANCEL) AlertDialog(onDismissRequest = { if (!working) dismiss() },
        title = { Text(stringResource(R.string.spaces_cancel_send)) },
        text = { Text(stringResource(if (cancelTooLate || cancel == null) {
            if (message?.pending == true) R.string.spaces_cancel_send_too_late else R.string.spaces_cancel_send_already_sent
        } else R.string.spaces_cancel_send_detail)) },
        confirmButton = { if (cancel != null && !cancelTooLate) TextButton(onClick = {
            cancel { stopped -> if (stopped) dismiss() else cancelTooLate = true }
        }, enabled = !working) { Text(stringResource(R.string.spaces_cancel_send)) } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !working) {
            Text(stringResource(if (cancelTooLate || cancel == null) R.string.spaces_close else R.string.spaces_keep_queued)) } })
    val sheetMode = mode?.takeIf { it == MessageOverlay.EDIT || it == MessageOverlay.ACTIONS || it == MessageOverlay.REACTION_PICKER }
    var lastSheetMode by remember { mutableStateOf<MessageOverlay?>(null) }
    SideEffect { if (sheetMode != null) lastSheetMode = sheetMode }
    val shownSheet = sheetMode ?: lastSheetMode
    val editor = shownSheet == MessageOverlay.EDIT
    val restoringEdit = editor && loadingTarget
    val sheetTitle = when (shownSheet) {
        MessageOverlay.ACTIONS -> R.string.spaces_message_actions
        MessageOverlay.REACTION_PICKER -> R.string.spaces_add_reaction
        else -> if (edit != null || restoringEdit) R.string.spaces_edit_message else R.string.spaces_saved_edit
    }
    DesignSheet(sheetMode != null, stringResource(sheetTitle),
        { if (!restoringEdit && (!working || !editor || edit == null)) dismiss() }, contentKey = shownSheet,
        footer = { first ->
            if (editor) {
                if (edit != null || restoringEdit) Button(onClick = { edit?.invoke(editedText, dismiss) },
                    enabled = edit != null && !working && editedText.isNotBlank() && editedText != (message?.change?.takeUnless { it.rejected || it.cancelled }?.text ?: message?.text),
                    modifier = first.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_save_channel)) }
                OutlinedButton(onClick = {
                    try {
                        val clip = ClipData.newPlainText(savedEditLabel, editedText).apply {
                            description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                        }
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                        editCopied = true; editCopyFailed = false
                    } catch (_: Exception) { editCopyFailed = true }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (editCopyFailed) R.string.spaces_copy_edit_failed else if (editCopied) R.string.spaces_copied else R.string.spaces_copy_edit))
                }
            }
            TextButton(onClick = dismiss, enabled = !restoringEdit && (edit == null || !working), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (edit != null || restoringEdit) R.string.spaces_cancel else R.string.spaces_close)) }
        }) { first ->
        when (shownSheet) {
            MessageOverlay.EDIT -> {
                if (restoringEdit) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedTextField(editedText, { if (edit != null) onText(it) }, first.fillMaxWidth(), enabled = edit == null || !working,
                    readOnly = edit == null, label = { Text(stringResource(R.string.spaces_message_text)) }, maxLines = 8)
            }
            MessageOverlay.ACTIONS -> {
                PinChangeNotice(message?.pinChange)
                if (cancelPin != null) TextButton(onClick = {
                    cancelPin { stopped -> pinCancelTooLate = !stopped }
                }, enabled = !working, modifier = first.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.spaces_cancel_change))
                }
                if (pinCancelTooLate && message?.pinChange?.attempted == true)
                    Text(stringResource(R.string.spaces_change_cancel_too_late), style = MaterialTheme.typography.labelSmall)
                if (pin != null) TextButton(onClick = { pin(dismiss) }, enabled = !working,
                    modifier = first.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (message?.pinned == true) R.string.spaces_unpin_message else R.string.spaces_pin_message))
                }
                if (copyLink != null) SpaceDestinationCopyButton(R.string.spaces_copy_message_link, enabled = !working, link = copyLink)
                val savedEdit = message?.change?.takeIf { it.kind == SpaceClient.MessageChangeKind.EDIT }?.text
                if (edit != null || savedEdit != null) TextButton(onClick = { selectMode(MessageOverlay.EDIT) }, enabled = !working,
                    modifier = first.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (edit == null) R.string.spaces_view_saved_edit else R.string.spaces_edit_message)) }
                if (remove != null) TextButton(onClick = { selectMode(MessageOverlay.DELETE) }, enabled = !working,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_delete_message)) }
                if (report != null) TextButton(onClick = { selectMode(MessageOverlay.REPORT) }, enabled = !working,
                    modifier = first.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_report_message)) }
            }
            MessageOverlay.REACTION_PICKER -> {
                spaceReactionChoices.chunked(if (LocalDensity.current.fontScale >= 1.5f) 2 else 4).forEach { choices ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        choices.forEach { emoji ->
                            val input = if (nl.davidengelen.redsquare.BuildConfig.SPACES_SCALE_FIXTURE && message != null)
                                SpacesScaleMeasurement.inputObserver(message.id, emoji) else Modifier
                            TextButton(onClick = { react?.invoke(emoji) {}; dismiss() }, enabled = react != null,
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).then(input)) {
                                Text(emoji, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
            }
            else -> Unit
        }
    }
    if (mode == MessageOverlay.DELETE) AlertDialog(onDismissRequest = { if (!working) dismiss() },
        title = { Text(stringResource(R.string.spaces_delete_message)) },
        text = { Text(stringResource(R.string.spaces_delete_message_detail)) },
        confirmButton = { if (remove != null) TextButton(onClick = { remove(dismiss) }, enabled = !working) {
            Text(stringResource(R.string.spaces_delete_message), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !working) { Text(stringResource(R.string.spaces_cancel)) } })
    key(message?.id) { MessageReactionDetails(message, mode == MessageOverlay.REACTIONS, dismiss, react, loadReactions) }
}

@Composable private fun ChannelNotificationPreferences(channel: String, policy: SpaceNotificationPolicy, enabled: Boolean,
    chooseMode: (SpaceNotificationMode) -> Unit, allowEveryone: (Boolean) -> Unit) {
    HorizontalDivider()
    Text(stringResource(R.string.spaces_notification_preferences, channel), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.spaces_notification_device_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        SpaceNotificationMode.entries.forEach { mode ->
            val title = when (mode) {
                SpaceNotificationMode.PERSONAL -> R.string.spaces_notifications_personal
                SpaceNotificationMode.ALL -> R.string.spaces_notifications_all
                SpaceNotificationMode.SILENT -> R.string.spaces_notifications_silent
            }
            val detail = when (mode) {
                SpaceNotificationMode.PERSONAL -> R.string.spaces_notifications_personal_detail
                SpaceNotificationMode.ALL -> R.string.spaces_notifications_all_detail
                SpaceNotificationMode.SILENT -> R.string.spaces_notifications_silent_detail
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).selectable(policy.mode == mode, enabled = enabled,
                role = Role.RadioButton, onClick = { chooseMode(mode) }).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(policy.mode == mode, onClick = null, enabled = enabled)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title)); Text(stringResource(detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(policy.allowEveryone,
        enabled = enabled, role = Role.Switch, onValueChange = allowEveryone), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.spaces_notifications_everyone))
            Text(stringResource(R.string.spaces_notifications_everyone_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(policy.allowEveryone, onCheckedChange = null, enabled = enabled)
    }
    HorizontalDivider()
}

private fun pinChangeLabel(change: SpaceClient.PinChange): Int = when {
    change.cancelled -> R.string.spaces_pin_cancelled
    change.rejected -> R.string.spaces_pin_refused
    change.attempted -> R.string.spaces_pin_uncertain
    else -> R.string.spaces_pin_waiting
}

@Composable private fun PinChangeNotice(change: SpaceClient.PinChange?) {
    if (change == null) return
    Text(stringResource(pinChangeLabel(change)), style = MaterialTheme.typography.labelSmall,
        color = if (change.rejected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun PinnedMessagesSheet(open: Boolean, availability: SpacesAccount.Availability, dismiss: () -> Unit,
    load: suspend (String?) -> SpaceClient.PinnedPage, navigate: (SpaceClient.PinnedMessage) -> Unit, refresh: () -> Unit) {
    var cursor by remember { mutableStateOf<String?>(null) }
    var previous by remember { mutableStateOf<List<String?>>(emptyList()) }
    var page by remember { mutableStateOf<SpaceClient.PinnedPage?>(null) }
    var pageCursor by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val latestLoad by rememberUpdatedState(load)
    LaunchedEffect(open) { if (open) { cursor = null; previous = emptyList(); page = null; failed = false } }
    LaunchedEffect(open, cursor, retry) {
        if (!open) return@LaunchedEffect
        val requested = cursor
        while (currentCoroutineContext().isActive) {
            try {
                val received = latestLoad(requested)
                page = received; pageCursor = requested; failed = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            delay(5_000)
        }
    }
    val ready = page != null && pageCursor == cursor
    DesignSheet(open, stringResource(R.string.spaces_pinned_messages), dismiss, contentKey = cursor,
        footer = { first ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (previous.isNotEmpty()) OutlinedButton(onClick = { cursor = previous.last(); previous = previous.dropLast(1) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_previous_reactions)) }
                if (ready) page?.nextMessage?.let { next -> OutlinedButton(onClick = { previous = previous + cursor; cursor = next },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_next_reactions)) } }
            }
            TextButton(onClick = { refresh(); retry++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.spaces_pins_retry)) }
            TextButton(onClick = dismiss, modifier = first.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_close)) }
        }) { _ ->
        if (availability == SpacesAccount.Availability.OFFLINE) Text(stringResource(R.string.spaces_pins_offline), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!ready && !failed) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp)); Text(stringResource(R.string.spaces_pins_loading))
        }
        if (failed) Text(stringResource(R.string.spaces_pins_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ready) page?.let { received ->
            if (!received.complete) Text(stringResource(R.string.spaces_pins_partial), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (received.items.isEmpty() && received.complete) Text(stringResource(R.string.spaces_no_pins), color = MaterialTheme.colorScheme.onSurfaceVariant)
            received.items.forEach { reference -> key(reference.message.id) {
                val message = reference.message
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(registeredIdentity(message.author).name, style = MaterialTheme.typography.titleSmall)
                        Text(message.text ?: stringResource(R.string.spaces_missing_keys), maxLines = 5, overflow = TextOverflow.Ellipsis)
                        PinChangeNotice(message.pinChange)
                        TextButton(onClick = { navigate(reference) }, enabled = !message.missingKeys && !message.deleted &&
                            (reference.threadId == null || reference.parent?.missingKeys == false),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(if (reference.threadId == null) R.string.spaces_open_pinned else R.string.spaces_open_pinned_reply)) }
                    }
                }
            } }
        }
    }
}

private val spaceReactionChoices = listOf("👍", "❤️", "🔥", "🎉", "🚀", "😂", "🤔", "👀")

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun MessageReactions(message: SpaceClient.Message, react: ((String) -> Unit)?, reply: (() -> Unit)?,
    openPicker: () -> Unit, openDetails: () -> Unit) {
    if (nl.davidengelen.redsquare.BuildConfig.SPACES_SCALE_FIXTURE) {
        val view = LocalView.current
        SideEffect { SpacesScaleMeasurement.reactionDrawn(message, view) }
    }
    var knownOrder by remember(message.id) { mutableStateOf<List<String>>(emptyList()) }
    var lastChoice by remember(message.id) { mutableStateOf<String?>(null) }
    val available = message.reactions.associateBy { it.emoji }
    val ordered = (knownOrder.filter { it in available } + available.keys.filter { it !in knownOrder }).distinct()
    SideEffect { knownOrder = ordered }
    val shown = (ordered.take(6) + ordered.filter { available[it]?.selected == true || it == lastChoice }).distinct().mapNotNull(available::get)
    val hasMore = message.reactionsHaveMore || shown.size < message.reactions.size
    val choose: ((String) -> Unit)? = react?.let { action -> { emoji -> lastChoice = emoji; action(emoji) } }
    FlowRow(Modifier.fillMaxWidth().animateContentSize(tween(AppSpacing.transitionMillis)),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { reaction -> key(reaction.emoji) { ReactionChip(reaction, choose) } }
        if (react != null) FrameIconButton(R.drawable.ic_reaction_add, stringResource(R.string.spaces_add_reaction), onClick = openPicker)
        if (reply != null) ThreadReplyButton(message.replyCount, reply)
        if (hasMore) TextButton(onClick = openDetails,
            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_all_reactions)) }
    }
    AnimatedVisibility(!message.reactionsComplete,
        enter = fadeIn(tween(AppSpacing.transitionMillis)) + expandVertically(tween(AppSpacing.transitionMillis)),
        exit = fadeOut(tween(AppSpacing.transitionMillis)) + shrinkVertically(tween(AppSpacing.transitionMillis))) {
        Text(stringResource(R.string.spaces_reactions_not_complete), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun ThreadReplyButton(count: Long, reply: () -> Unit) {
    TextButton(onClick = reply, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(painterResource(R.drawable.ic_chat), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (count == 0L) stringResource(R.string.spaces_reply_in_thread)
            else pluralStringResource(R.plurals.social_reply_count, count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), count))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun MessageReactionDetails(message: SpaceClient.Message?, allOpen: Boolean, dismiss: () -> Unit,
    react: ((String, () -> Unit) -> Unit)?, loadPage: (suspend (String?) -> SpaceClient.ReactionPage)?) {
    var cursor by rememberSaveable { mutableStateOf<String?>(null) }
    var previousCursors by rememberSaveable { mutableStateOf<List<String?>>(emptyList()) }
    var page by remember { mutableStateOf<SpaceClient.ReactionPage?>(null) }
    var pageCursor by remember { mutableStateOf<String?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var pageFailed by remember { mutableStateOf(false) }
    var refreshVersion by remember { mutableIntStateOf(0) }
    val reactAndRefresh = react?.let { send -> { emoji: String -> send(emoji) { refreshVersion++ } } }
    LaunchedEffect(allOpen) { if (allOpen) { cursor = null; previousCursors = emptyList(); page = null; pageReady = false } }
    val pageLoading = (!pageReady || pageCursor != cursor) && !pageFailed
    LaunchedEffect(allOpen, cursor, message?.reactions, message?.reactionsComplete, refreshVersion) {
        if (!allOpen || loadPage == null) return@LaunchedEffect
        val requestedCursor = cursor
        pageFailed = false
        while (currentCoroutineContext().isActive) {
            try {
                val received = loadPage(requestedCursor)
                if (cursor == requestedCursor && allOpen) {
                    page = received; pageCursor = requestedCursor; pageReady = true; pageFailed = false
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (cursor == requestedCursor) pageFailed = true }
            delay(5_000)
        }
    }
    DesignSheet(allOpen, stringResource(R.string.spaces_reactions), dismiss, contentKey = message?.id to cursor,
        footer = { first ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (previousCursors.isNotEmpty()) OutlinedButton(onClick = {
                    cursor = previousCursors.last(); previousCursors = previousCursors.dropLast(1)
                }, enabled = true, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_previous_reactions)) }
                page?.nextEmoji?.let { next -> OutlinedButton(onClick = {
                    if (pageReady && pageCursor == cursor) { previousCursors = previousCursors + cursor; cursor = next }
                }, enabled = pageReady && pageCursor == cursor, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_next_reactions)) } }
            }
            TextButton(onClick = dismiss, modifier = first.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_close)) }
        }) { _ ->
        if (pageLoading) CircularProgressIndicator(Modifier.size(24.dp))
        page?.let { received ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                received.choices.forEach { reaction -> key(reaction.emoji) { ReactionChip(reaction, reactAndRefresh) } }
            }
            if (received.choices.isEmpty()) Text(stringResource(R.string.spaces_no_reactions_page), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!received.complete) Text(stringResource(R.string.spaces_reactions_not_complete), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (pageFailed) TextButton(onClick = { refreshVersion++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_retry_reactions)) }
    }
}

@Composable private fun ReactionChip(reaction: SpaceClient.Reaction, react: ((String) -> Unit)?) {
    FilterChip(selected = reaction.selected, onClick = { react?.invoke(reaction.emoji) }, enabled = react != null,
        modifier = Modifier.heightIn(min = 48.dp),
        leadingIcon = {
            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                AnimatedVisibility(reaction.selected, enter = fadeIn(tween(AppSpacing.transitionMillis)), exit = fadeOut(tween(AppSpacing.transitionMillis))) {
                    Icon(painterResource(R.drawable.ic_check), null, Modifier.size(16.dp))
                }
            }
        }, label = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(reaction.emoji)
                reaction.count?.let { Text(it.toString(), style = MaterialTheme.typography.labelMedium) }
            }
        })
}

@Composable private fun AvailabilityNotice(availability: SpacesAccount.Availability, refreshing: Boolean, retry: () -> Unit) {
    val label = when (availability) {
        SpacesAccount.Availability.ONLINE -> null
        SpacesAccount.Availability.CHECKING -> R.string.spaces_checking
        SpacesAccount.Availability.OFFLINE -> R.string.spaces_offline
        SpacesAccount.Availability.UNSUPPORTED -> R.string.spaces_unsupported_detail
        SpacesAccount.Availability.CHANGED_IDENTITY -> R.string.spaces_changed_identity
        SpacesAccount.Availability.STORAGE_UNAVAILABLE -> R.string.spaces_storage_unavailable
    }
    AnimatedVisibility(label != null,
        enter = fadeIn(tween(AppSpacing.transitionMillis)) + expandVertically(tween(AppSpacing.transitionMillis)),
        exit = fadeOut(tween(AppSpacing.transitionMillis)) + shrinkVertically(tween(AppSpacing.transitionMillis))) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                label?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (availability != SpacesAccount.Availability.CHECKING && availability != SpacesAccount.Availability.STORAGE_UNAVAILABLE)
                    TextButton(onClick = retry, enabled = !refreshing) { Text(stringResource(R.string.spaces_retry)) }
            }
        }
    }
}
@Composable internal fun ProblemNotice(problem: SpacesAccount.Problem, dismiss: () -> Unit) {
    val message = when (problem) {
        SpacesAccount.Problem.INVITATION -> R.string.spaces_invitation_failed
        SpacesAccount.Problem.SAVE -> R.string.spaces_save_failed
        SpacesAccount.Problem.CONNECTION -> R.string.spaces_offline
        SpacesAccount.Problem.PROTECTED_CHAT_UNSUPPORTED -> R.string.protected_chat_unsupported
        SpacesAccount.Problem.DEVICE_RECOVERY_UNSUPPORTED -> R.string.recovery_device_unsupported
        SpacesAccount.Problem.DEVICE_RECOVERY_NO_APPROVER -> R.string.recovery_device_no_approver
        SpacesAccount.Problem.DEVICE_ALREADY_ACTIVE -> R.string.recovery_device_already_active
        SpacesAccount.Problem.RECOVERY_OTHER_MODULE -> R.string.recovery_archive_in_chat
        SpacesAccount.Problem.ARTICLE_MISSING -> R.string.spaces_article_missing
        SpacesAccount.Problem.DESTINATION_MISSING -> R.string.spaces_destination_missing
    }
    Column {
        Text(stringResource(message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = dismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_close)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun InvitationSheet(open: Boolean, invitation: SpaceClient.Invitation?, working: Boolean, canGenerate: Boolean,
    dismiss: () -> Unit, retry: () -> Unit, generate: (Int) -> Unit, revoke: () -> Unit) {
    val context = LocalContext.current
    var copied by remember(open) { mutableStateOf(false) }
    var failed by remember(open) { mutableStateOf(false) }
    val copyLabel = stringResource(R.string.spaces_invitation_link)
    var newLink by remember(open) { mutableStateOf(false) }
    var days by remember(open) { mutableIntStateOf(7) }
    LaunchedEffect(invitation?.link) { copied = false; newLink = false }
    DesignSheet(open, stringResource(R.string.spaces_invite), dismiss) { first ->
        if (invitation?.revoked == true || invitation?.expired == true || newLink) {
            Text(stringResource(when {
                invitation?.revoked == true -> R.string.spaces_invitation_revoked
                invitation?.expired == true -> R.string.spaces_invitation_expired
                else -> R.string.spaces_new_invitation_detail
            }))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 7, 30).forEach { duration ->
                    FilterChip(selected = days == duration, onClick = { days = duration }, enabled = !working,
                        label = { Text(stringResource(R.string.spaces_invitation_days, duration)) })
                }
            }
            Button(onClick = { generate(days) }, enabled = canGenerate && !working,
                modifier = first.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_new_invitation)) }
        } else if (invitation?.confirmed != true) {
            Text(stringResource(R.string.spaces_invitation_preparing))
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = retry, modifier = first.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_retry)) }
        } else {
            Text(stringResource(R.string.spaces_invitation_share_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (invitation.revocationPending) Text(stringResource(R.string.spaces_revocation_pending), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (invitation.revocationFailed) Text(stringResource(R.string.spaces_revocation_failed), color = MaterialTheme.colorScheme.error)
            val expiry = remember(invitation.expiresAt) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(invitation.expiresAt)) }
            Text(stringResource(R.string.spaces_invitation_expiry, expiry), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(copyLabel, invitation.link).apply {
                        description.extras = android.os.PersistableBundle().apply {
                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                        }
                    }
                    clipboard.setPrimaryClip(clip)
                    copied = true; failed = false
                } catch (_: Exception) { failed = true }
            }, enabled = !invitation.revocationPending, modifier = first.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (copied) R.string.spaces_copied else R.string.spaces_copy_invitation))
            }
            OutlinedButton(onClick = {
                try {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, invitation.link)
                    }, null))
                    failed = false
                } catch (_: Exception) { failed = true }
            }, enabled = !invitation.revocationPending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_share_invitation)) }
            OutlinedButton(onClick = revoke, enabled = canGenerate && !working && !invitation.revocationPending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_revoke_invitation)) }
            TextButton(onClick = { newLink = true }, enabled = canGenerate && !working,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_new_invitation)) }
            if (failed) Text(stringResource(R.string.spaces_share_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}
