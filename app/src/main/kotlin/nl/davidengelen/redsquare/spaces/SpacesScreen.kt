package nl.davidengelen.redsquare.spaces

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.ProfileAvatar
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.DesignSheet
import nl.davidengelen.redsquare.ui.FrameIconButton
import java.text.DateFormat
import java.util.Date

/** Production routes use only account-owned received/durable state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpacesScreen(account: SpacesAccount, modifier: Modifier = Modifier,
    chrome: @Composable (Boolean) -> Unit, onProfile: (String) -> Unit, onMessage: (String) -> Unit,
    onOpenNode: (String, String) -> Unit, initialInvitation: String? = null, onInvitationConsumed: () -> Unit = {}) {
    val state by account.state.collectAsState()
    val scope = rememberCoroutineScope()
    val navigation = LocalPostNavigation.current
    DisposableEffect(navigation, account) {
        val save: suspend () -> Boolean = { account.guardNavigation { true } }
        val guard: suspend (suspend () -> Boolean) -> Boolean = { commit -> account.guardNavigation(commit) }
        navigation?.saveCurrent = save; navigation?.guardedCurrent = guard
        onDispose {
            if (navigation?.saveCurrent === save) navigation.saveCurrent = null
            if (navigation?.guardedCurrent === guard) navigation.guardedCurrent = null
        }
    }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val navigationTitle = stringResource(R.string.spaces_navigation_panel)
    var tab by rememberSaveable { mutableStateOf(SpacesTab.MY_SPACES) }
    var createOpen by remember { mutableStateOf(false) }
    var joinOpen by remember { mutableStateOf(false) }
    var inviteOpen by remember { mutableStateOf(false) }
    var informationOpen by remember { mutableStateOf(false) }
    var membersOpen by rememberSaveable { mutableStateOf(false) }
    var overlaySpace by remember { mutableStateOf(state.selected) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var publicRulesOpen by remember { mutableStateOf(false) }
    var publicReference by rememberSaveable { mutableStateOf<String?>(null) }
    var publicCopied by remember { mutableStateOf(false) }
    var publicShareFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var invitationText by remember { mutableStateOf("") }
    var reviewedInvitation by remember(account) { mutableStateOf<SpaceClient.InvitationPreview?>(null) }
    val invitationReview = state.preview ?: reviewedInvitation
    SideEffect { state.preview?.let { reviewedInvitation = it } }
    fun clearInvitationReview() { reviewedInvitation = null; account.dismissPreview() }
    var savedSelection by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.current
    val profile = state.selected?.let { state.profiles[it] }
    val channel = state.channels.firstOrNull { it.id == state.selectedChannel }
    LaunchedEffect(state.publicLink) { publicCopied = false; publicShareFailed = false }
    LaunchedEffect(state.openedPublicReference) {
        state.openedPublicReference?.let { opened ->
            if (opened == publicReference) publicReference = null
            account.consumePublicReference(opened)
        }
    }
    val imeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    fun choose(space: String?, channel: String? = null) {
        savedSelection = space; account.select(space, channel); tab = SpacesTab.MY_SPACES
        scope.launch { drawer.close() }
    }
    fun selectTab(target: SpacesTab) { account.select(null); savedSelection = null; tab = target }
    LaunchedEffect(account, state.ready, state.loading) {
        // A verified public visitor has local context without My Spaces membership.
        if (state.ready && !state.loading && state.selected == null && savedSelection != null) account.select(savedSelection)
    }
    LaunchedEffect(state.selected) {
        if (overlaySpace != state.selected) {
            informationOpen = false; inviteOpen = false; membersOpen = false; publicRulesOpen = false
            overlaySpace = state.selected
        }
        state.selected?.let { savedSelection = it; tab = SpacesTab.MY_SPACES }
    }
    LaunchedEffect(initialInvitation, state.ready, state.loading, state.working) {
        val link = initialInvitation ?: return@LaunchedEffect
        if (state.loading || !state.ready || state.working) return@LaunchedEffect
        clearInvitationReview()
        if (SpacesLaunchRoutes.isPublic(android.net.Uri.parse(link))) {
            joinOpen = false; informationOpen = false; inviteOpen = false; membersOpen = false
            publicReference = link; account.openPublicLink(link)
        } else { invitationText = link; joinOpen = true; account.preview(link) }
        onInvitationConsumed()
    }
    LaunchedEffect(state.joinedInvitation) {
        state.joinedInvitation?.let { joined ->
            if (joinOpen && invitationText.trim() == joined) joinOpen = false
            account.consumeJoinedInvitation(joined)
        }
    }
    LaunchedEffect(selected?.joined) { if (selected?.joined == true) publicRulesOpen = false }
    BackHandler(membersOpen) { membersOpen = false; informationOpen = true }
    BackHandler(!membersOpen && state.selected != null && drawer.isClosed && !createOpen && !joinOpen && !inviteOpen && !informationOpen && !publicRulesOpen) { choose(null) }
    ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = state.selected != null, modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(Modifier.widthIn(max = 360.dp).fillMaxHeight().semantics { paneTitle = navigationTitle }) {
                LazyColumn(contentPadding = PaddingValues(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text(stringResource(R.string.spaces_your_communities), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }) }
                    items(state.communities, key = { "space:${it.id}" }) { community ->
                        SpaceCommunityRow(state.profiles[community.id] ?: SpaceProfile(community.name), community.id, account.ownerKey, account.backend,
                            summary = stringResource(if (community.id == state.selected) R.string.spaces_current_community else R.string.spaces_open_saved_channel),
                            selected = community.id == state.selected, hasUnread = community.hasUnread) { choose(community.id) }
                    }
                    if (selected != null) {
                        item { HorizontalDivider(Modifier.padding(vertical = 12.dp)); Text(selected.name, style = MaterialTheme.typography.titleLarge) }
                        val groups = state.channels.groupBy { it.profile.category }
                        groups.forEach { (category, channels) ->
                            item(key = "category:$category") { Text(category.ifBlank { stringResource(R.string.spaces_channels) },
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { heading() }) }
                            items(channels, key = { it.id }) { entry ->
                                NavigationDrawerItem(label = { Text(entry.profile.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                    selected = entry.id == state.selectedChannel, onClick = { choose(selected.id, entry.id) },
                                    icon = { Icon(painterResource(channelKindIcon(entry.profile.kind)), null, Modifier.size(22.dp)) },
                                    badge = if (entry.hasUnread == true) ({ Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) }) else null)
                            }
                        }
                        if (selected.canInvite) item { OutlinedButton(onClick = {
                            scope.launch { drawer.close() }; inviteOpen = true
                            if (state.invitation == null) account.invite(selected.id)
                        }, enabled = !state.working && !selected.pending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.spaces_invite))
                        } }
                    }
                    item { TextButton(onClick = { choose(null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_back)) } }
                }
            }
        }) {
        Column(Modifier.fillMaxSize()) {
            chrome(imeVisible)
            if (membersOpen && selected != null) SpaceMembersPage(account, state, Modifier.weight(1f),
                onBack = { membersOpen = false; informationOpen = true }, onProfile = onProfile, onMessage = onMessage)
            else if (state.selected == null) SpacesOverview(account, state, tab, { choose(it) }, { createOpen = true },
                { clearInvitationReview(); joinOpen = true }, Modifier.weight(1f)) { AvailabilityNotice(state.availability, state.refreshing, account::refresh) }
            else {
                val title = channel?.profile?.name ?: stringResource(R.string.spaces_kind_chat)
                val menu = stringResource(R.string.spaces_switch_channel)
                Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FrameIconButton(R.drawable.ic_back, stringResource(R.string.spaces_back)) { choose(null) }
                    Surface(onClick = { focusManager.clearFocus(); keyboardController?.hide(); scope.launch { drawer.open() } }, shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f).semantics { contentDescription = menu; role = Role.Button; heading(); stateDescription = title }) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(selected?.name ?: profile?.name.orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(20.dp))
                        }
                    }
                    FrameIconButton(R.drawable.ic_info, stringResource(R.string.spaces_information)) { focusManager.clearFocus(); keyboardController?.hide(); informationOpen = true }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvailabilityNotice(state.availability, state.refreshing, account::refresh)
                    state.selected?.let { state.accessProblems[it] }?.let { AccessNotice(it) }
                    if (selected != null && !selected.joined && selected.pending) Text(stringResource(R.string.spaces_confirming_access),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (selected?.joined == true && channel?.ready == false) Text(stringResource(R.string.spaces_waiting_channel_keys),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (selected?.joined == true && channel?.profile?.kind == SpaceChannelKind.ANNOUNCEMENT && !selected.canSend)
                        Text(stringResource(R.string.spaces_announcements_read_only), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                }
                if (state.loadingChannel || selected == null) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state.problem == null) CircularProgressIndicator(Modifier.size(28.dp)) else ProblemNotice(requireNotNull(state.problem), account::clearProblem)
                } else key(selected.id, selected.channel) {
                    val conversation = channel?.profile?.kind in setOf(SpaceChannelKind.CHAT, SpaceChannelKind.ANNOUNCEMENT) || channel == null
                    if (!conversation) Box(Modifier.weight(1f).fillMaxWidth().padding(AppSpacing.screen), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(painterResource(channelKindIcon(requireNotNull(channel).profile.kind)), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(channel.profile.name, style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(R.string.spaces_channel_not_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        if (state.hasEarlier) OutlinedButton(onClick = account::olderMessages, enabled = !state.working,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen).heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.spaces_earlier_messages))
                        }
                        val anchorIndex = state.anchor?.let { anchor -> state.messages.indexOfFirst { it.id == anchor.messageId } } ?: -1
                        val initialIndex = if (anchorIndex >= 0) anchorIndex else state.messages.lastIndex.coerceAtLeast(0)
                        val list = remember { androidx.compose.foundation.lazy.LazyListState(initialIndex, if (anchorIndex >= 0) state.anchor?.offset?.coerceAtLeast(0) ?: 0 else 0) }
                        var initialPage by remember { mutableStateOf(true) }
                        var autoScrolling by remember { mutableStateOf(false) }
                        var lastRead by remember { mutableStateOf<Long?>(null) }
                        val latestMessages by rememberUpdatedState(state.messages)
                        val latestWindow by rememberUpdatedState(state.windowFrom)
                        val latestHasLater by rememberUpdatedState(state.hasLater)
                        LaunchedEffect(list, account) {
                            snapshotFlow {
                                val visible = list.layoutInfo.visibleItemsInfo
                                val first = visible.firstOrNull()?.let { item -> latestMessages.firstOrNull { it.id == item.key && it.position != null } }
                                val read = visible.mapNotNull { item -> latestMessages.firstOrNull { it.id == item.key }?.position }.maxOrNull()
                                val anchor = first?.let { SpaceClient.ReadingAnchor(it.id, requireNotNull(it.position), list.firstVisibleItemScrollOffset, latestWindow) }
                                ChannelViewport(anchor, !(list.canScrollForward || list.isScrollInProgress), autoScrolling, read)
                            }.collect { viewport ->
                                if (!viewport.automatic && viewport.anchor != null) account.readingPosition(selected.id, selected.channel,
                                    viewport.followLatest && !latestHasLater, viewport.anchor)
                                viewport.read?.let { position -> if (lastRead == null || position > requireNotNull(lastRead)) {
                                    lastRead = position; account.markRead(selected.id, selected.channel, position)
                                } }
                            }
                        }
                        LaunchedEffect(state.messages.map { it.id }, state.scrollToMessage) {
                            val requested = state.scrollToMessage
                            val requestedIndex = state.messages.indexOfFirst { it.id == requested }
                            if (requestedIndex >= 0) {
                                autoScrolling = true
                                try { list.animateScrollToItem(requestedIndex) } finally { autoScrolling = false }
                                initialPage = false; account.consumeScrollTarget(requireNotNull(requested)); return@LaunchedEffect
                            }
                            if (state.messages.isNotEmpty() && initialPage) {
                                val index = state.anchor?.let { anchor -> state.messages.indexOfFirst { it.id == anchor.messageId } } ?: -1
                                autoScrolling = true
                                try { list.scrollToItem(if (index >= 0) index else state.messages.lastIndex, if (index >= 0) state.anchor?.offset ?: 0 else 0) }
                                finally { autoScrolling = false }
                                initialPage = false
                            } else if (state.messages.isNotEmpty() && state.followingLatest) {
                                autoScrolling = true
                                try { list.animateScrollToItem(state.messages.lastIndex) } finally { autoScrolling = false }
                            }
                        }
                        if (state.messages.isEmpty() && !state.contentLoaded) Box(Modifier.weight(1f).fillMaxWidth().padding(AppSpacing.screen), contentAlignment = Alignment.Center) {
                            if (state.refreshing) CircularProgressIndicator(Modifier.size(28.dp))
                            else Text(stringResource(R.string.spaces_history_not_loaded), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (state.messages.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().padding(AppSpacing.screen), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.spaces_no_messages), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
                            contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(state.messages, key = { it.id }) { message -> MessageRow(message, state.draft.isEmpty() && !state.working) { account.restoreRejectedDraft(message.id) } }
                        }
                        if (state.hasLater) OutlinedButton(onClick = account::latestMessages,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen).heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_latest_messages)) }
                        state.problem?.let { ProblemNotice(it, account::clearProblem) }
                        if (profile?.visibility != SpaceVisibility.PUBLIC || selected.joined) {
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = 8.dp), verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(state.draft, { account.editDraft(selected.id, selected.channel, it) }, Modifier.weight(1f),
                                    enabled = !state.savingNavigation && !state.loadingChannel, placeholder = { Text(stringResource(R.string.spaces_message_channel_hint, channel?.profile?.name ?: "general")) },
                                    shape = RoundedCornerShape(18.dp), minLines = 1, maxLines = 4)
                                FilledIconButton(onClick = account::send, enabled = selected.canSend && state.draft.isNotBlank() && !state.working &&
                                    state.accessProblems[selected.id] == null, modifier = Modifier.size(48.dp)) {
                                    Icon(painterResource(R.drawable.ic_send), stringResource(R.string.spaces_send), Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
                if (selected != null && !state.loadingChannel && profile?.visibility == SpaceVisibility.PUBLIC && !selected.joined) {
                    Column(Modifier.fillMaxWidth().padding(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.spaces_public_browsing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { if (profile.houseRules.isEmpty()) account.joinPublic(state.publicRulesToken) else publicRulesOpen = true },
                            enabled = !state.working && !selected.pending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(if (selected.pending) R.string.spaces_joining_public else R.string.spaces_join_public))
                        }
                    }
                }
            }
            if (!imeVisible) SpacesBottomBar(tab) { selectTab(it) }
        }
    }
    DesignSheet(publicReference != null, stringResource(R.string.spaces_open_public_community), { publicReference = null; account.dismissPublicReference() }) { first ->
        val node = state.requiredNode
        val choice = nl.davidengelen.redsquare.saito.SaitoBackend.entries.firstOrNull { it.webUrl == node }
        when {
            state.working -> { Text(stringResource(R.string.spaces_opening_community)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            choice != null -> {
                Text(stringResource(R.string.spaces_different_node, choice.domain))
                Button(onClick = { publicReference?.let { onOpenNode(choice.webUrl, it) } }, enabled = !state.savingNavigation,
                    modifier = first.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_switch_node_open)) }
            }
            node != null -> Text(stringResource(R.string.spaces_unknown_reference_node), color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.previewIssue != null -> AccessNotice(requireNotNull(state.previewIssue))
            else -> {
                Text(stringResource(R.string.spaces_public_link_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { publicReference?.let(account::openPublicLink) },
                    modifier = first.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_retry)) }
            }
        }
    }
    SpaceCreationSheet(createOpen, account, state) { createOpen = false }
    var acceptedInvitation by remember(invitationReview?.spaceId, invitationReview?.rulesToken) { mutableStateOf(false) }
    DesignSheet(joinOpen, stringResource(R.string.spaces_join_invitation), { joinOpen = false; account.dismissPreview() },
        contentKey = invitationReview?.spaceId, footer = { first ->
            val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
            val rulesAccepted = invitationReview?.profile?.houseRules.isNullOrEmpty() || acceptedInvitation
            Button(onClick = {
                focus.clearFocus(); keyboard?.hide()
                if (invitationReview == null) account.preview(invitationText.trim()) else account.join(invitationText.trim(), invitationReview?.rulesToken)
            }, enabled = joinOpen && invitationText.isNotBlank() && !state.working && rulesAccepted && (invitationReview == null || state.preview != null),
                modifier = (if (invitationReview != null) first else Modifier).fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (invitationReview == null) R.string.spaces_preview else R.string.spaces_join))
            }
            if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
        }) { first ->
        Text(stringResource(R.string.spaces_join_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    SpaceAvatar(preview.profile, preview.spaceId, account.ownerKey, account.backend, Modifier.size(56.dp))
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
        state.previewIssue?.let { issue -> Text(stringResource(if (issue == SpaceClient.ReadProblem.EXPIRED)
            R.string.spaces_invitation_expired else R.string.spaces_expiry_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        state.problem?.let { ProblemNotice(it, account::clearProblem) }
    }
    var acceptedPublicRules by remember(state.selected, state.publicRulesToken) { mutableStateOf(false) }
    DesignSheet(publicRulesOpen && selected != null, stringResource(R.string.spaces_house_rules), { publicRulesOpen = false }, footer = { first ->
        Button(onClick = { account.joinPublic(state.publicRulesToken) }, enabled = acceptedPublicRules && !state.working && selected?.pending != true,
            modifier = first.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_join_public)) }
    }) {
        profile?.let { Text(it.name, style = MaterialTheme.typography.titleLarge); Text(it.houseRules) }
        RulesAcceptance(acceptedPublicRules, { acceptedPublicRules = it })
        state.problem?.let { ProblemNotice(it, account::clearProblem) }
    }
    InvitationSheet(inviteOpen, state.invitation, state.working, selected?.canInvite == true && !selected.pending,
        { inviteOpen = false }, account::refresh,
        { days -> selected?.let { account.invite(it.id, days) } },
        { selected?.let { account.revokeInvitation(it.id) } })
    DesignSheet(informationOpen && selected != null, stringResource(R.string.spaces_information), { informationOpen = false }) { first ->
        if (selected != null && profile != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SpaceAvatar(profile, selected.id, account.ownerKey, account.backend, Modifier.size(64.dp))
                Text(profile.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
            if (profile.description.isNotBlank()) Text(profile.description)
            Text(stringResource(if (profile.visibility == SpaceVisibility.PUBLIC) R.string.spaces_public_information else R.string.spaces_private_information),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected.joined) Text(stringResource(R.string.spaces_member_confirmed), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (profile.houseRules.isNotEmpty()) { Text(stringResource(R.string.spaces_house_rules), style = MaterialTheme.typography.titleMedium); Text(profile.houseRules) }
            state.publicLink?.let { link ->
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
            if (state.members.any { it.publicKey == account.ownerKey && it.role == SpaceRole.OWNER } && profile.visibility == SpaceVisibility.PUBLIC)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) { Text(stringResource(R.string.spaces_discoverable)); Text(stringResource(R.string.spaces_unlisted_detail), style = MaterialTheme.typography.bodySmall) }
                    Switch(state.listingIntent ?: profile.listed, { account.setListed(selected.id, it) }, enabled = !state.working && !state.savingNavigation)
                }
            if (state.listingIntent != null) Text(stringResource(R.string.spaces_saving_listing), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected.canInvite) OutlinedButton(onClick = { informationOpen = false; inviteOpen = true; if (state.invitation == null) account.invite(selected.id) },
                enabled = !state.working && !selected.pending, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_invite)) }
            OutlinedButton(onClick = {
                scope.launch {
                    val commit: suspend () -> Boolean = { informationOpen = false; membersOpen = true; true }
                    navigation?.navigate(commit) ?: account.guardNavigation(commit)
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_members)) }
            TextButton(onClick = { informationOpen = false }, modifier = first.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_close)) }
        }
    }
}

private data class ChannelViewport(val anchor: SpaceClient.ReadingAnchor?, val followLatest: Boolean, val automatic: Boolean, val read: Long?)
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

@Composable private fun MessageRow(message: SpaceClient.Message, canRestore: Boolean, restore: () -> Unit) {
    val name = registeredIdentity(message.author).name
    val time = remember(message.createdAt) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAt)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileAvatar(message.author, Modifier.size(38.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(message.text ?: stringResource(if (message.missingKeys) R.string.spaces_missing_keys else R.string.spaces_message_unavailable),
                color = if (message.text != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (message.rejected) stringResource(R.string.spaces_not_sent)
                else if (message.pending) stringResource(if (message.attempted) R.string.spaces_unconfirmed else R.string.spaces_queued) else time,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (message.rejected && message.text != null) TextButton(onClick = restore, enabled = canRestore,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_restore_draft)) }
        }
    }
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
@Composable private fun ProblemNotice(problem: SpacesAccount.Problem, dismiss: () -> Unit) {
    val message = when (problem) {
        SpacesAccount.Problem.INVITATION -> R.string.spaces_invitation_failed
        SpacesAccount.Problem.SAVE -> R.string.spaces_save_failed
        SpacesAccount.Problem.CONNECTION -> R.string.spaces_offline
    }
    Column {
        Text(stringResource(message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = dismiss) { Text(stringResource(R.string.spaces_close)) }
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
