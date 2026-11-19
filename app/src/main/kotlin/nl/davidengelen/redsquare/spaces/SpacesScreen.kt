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
    chrome: @Composable (Boolean) -> Unit, initialInvitation: String? = null, onInvitationConsumed: () -> Unit = {}) {
    val state by account.state.collectAsState()
    val scope = rememberCoroutineScope()
    val channelStates = rememberSaveableStateHolder()
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
    var createOpen by remember { mutableStateOf(false) }
    var joinOpen by remember { mutableStateOf(false) }
    var inviteOpen by remember { mutableStateOf(false) }
    var informationOpen by remember { mutableStateOf(false) }
    var communityName by remember { mutableStateOf("") }
    var invitationText by remember { mutableStateOf("") }
    // The account clears its preview when Join completes. Keep the verified
    // presentation until this dialog finishes exiting, without re-enabling it.
    var reviewedInvitation by remember(account) { mutableStateOf<SpaceClient.InvitationPreview?>(null) }
    val invitationReview = state.preview ?: reviewedInvitation
    SideEffect { state.preview?.let { reviewedInvitation = it } }
    fun clearInvitationReview() { reviewedInvitation = null; account.dismissPreview() }
    var savedSelection by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.communities.firstOrNull { it.id == state.selected }
    fun select(id: String?) {
        savedSelection = id
        account.select(id)
        scope.launch { drawer.close() }
    }
    LaunchedEffect(state.ready, state.loading, state.communities.map { it.id }) {
        if (state.ready && !state.loading && state.selected == null && savedSelection != null &&
            state.communities.any { it.id == savedSelection }) account.select(savedSelection)
    }
    LaunchedEffect(state.selected) { state.selected?.let { savedSelection = it } }
    LaunchedEffect(initialInvitation, state.ready, state.loading, state.working) {
        val link = initialInvitation ?: return@LaunchedEffect
        if (state.loading || !state.ready || state.working) return@LaunchedEffect
        clearInvitationReview(); invitationText = link; joinOpen = true; account.preview(link); onInvitationConsumed()
    }
    LaunchedEffect(state.working, state.selected) {
        if (!state.working && state.selected != null && state.problem == null) createOpen = false
        if (!state.working && state.selected != null && state.preview == null && state.problem == null && joinOpen) joinOpen = false
    }
    BackHandler(selected != null && drawer.isClosed && !createOpen && !joinOpen && !inviteOpen && !informationOpen) { select(null) }

    ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = selected != null, modifier = modifier,
        drawerContent = {
            ModalDrawerSheet(Modifier.widthIn(max = 360.dp).fillMaxHeight()) {
                LazyColumn(contentPadding = PaddingValues(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text(stringResource(R.string.spaces_your_communities), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }) }
                    items(state.communities, key = { it.id }) { community ->
                        CommunityRow(community, community.id == selected?.id) { select(community.id) }
                    }
                    if (selected != null) {
                        item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
                        item {
                            Text(selected.name, style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.spaces_conversations), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                        }
                        item { NavigationDrawerItem(label = { Text(stringResource(R.string.spaces_general)) }, selected = true,
                            onClick = { scope.launch { drawer.close() } }, icon = { Icon(painterResource(R.drawable.ic_chat), null) }) }
                        if (selected.canInvite) item {
                            OutlinedButton(onClick = { scope.launch { drawer.close() }; inviteOpen = true; if (state.invitation == null) account.invite() },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.working && selected.canSend) {
                                Text(stringResource(R.string.spaces_invite))
                            }
                        }
                    }
                    item { TextButton(onClick = { select(null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.spaces_back))
                    } }
                }
            }
        }) {
        Column(Modifier.fillMaxSize()) {
            chrome(false)
            if (selected == null) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(AppSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Text(stringResource(R.string.spaces_your_communities), style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() })
                    }
                    item { AvailabilityNotice(state.availability, state.refreshing, account::refresh) }
                    if (state.loading) item {
                        Text(stringResource(R.string.spaces_loading), style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (state.communities.isEmpty() && !state.loading) item {
                        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Icon(painterResource(R.drawable.ic_spaces), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.spaces_intro), style = MaterialTheme.typography.headlineMedium)
                                Text(stringResource(R.string.spaces_intro_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { createOpen = true }, enabled = state.ready && !state.loading && !state.working,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                                Text(stringResource(R.string.spaces_create))
                            }
                            OutlinedButton(onClick = { clearInvitationReview(); joinOpen = true }, enabled = state.ready && !state.working,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                                Text(stringResource(R.string.spaces_join_invitation))
                            }
                        }
                    }
                    items(state.communities, key = { it.id }) { CommunityRow(it, false) { select(it.id) } }
                    state.problem?.let { problem -> item { ProblemNotice(problem, account::clearProblem) } }
                }
            } else channelStates.SaveableStateProvider(selected.id) { key(selected.id) {
                val menu = stringResource(R.string.spaces_switch_channel)
                Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.small, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    FrameIconButton(R.drawable.ic_back, stringResource(R.string.spaces_back)) { select(null) }
                    Surface(onClick = { scope.launch { drawer.open() } }, shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f)
                            .semantics { contentDescription = menu; role = Role.Button; heading(); stateDescription = selected.name }) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(selected.name, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.spaces_general), style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(20.dp))
                        }
                    }
                    FrameIconButton(R.drawable.ic_info, stringResource(R.string.spaces_information)) { informationOpen = true }
                }
                if (state.loadingChannel) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                } else {
                val list = rememberLazyListState(initialFirstVisibleItemIndex =
                    (state.messages.lastIndex + 1 + if (state.hasEarlier) 1 else 0).coerceAtLeast(0))
                var initialPage by rememberSaveable { mutableStateOf(true) }
                var autoScrolling by remember { mutableStateOf(false) }
                LaunchedEffect(list, account) {
                    snapshotFlow {
                        if (list.layoutInfo.visibleItemsInfo.isEmpty()) null else (list.canScrollForward || list.isScrollInProgress) to autoScrolling
                    }.collect { position ->
                        position?.let { (forward, automatic) ->
                            if (!automatic) account.readingPosition(selected.id, followLatest = !forward)
                        }
                    }
                }
                LaunchedEffect(state.messages.map { it.id }, state.scrollToMessage) {
                    val requested = state.scrollToMessage
                    val requestedIndex = state.messages.indexOfFirst { it.id == requested }
                    if (requested != null && requestedIndex >= 0) {
                        autoScrolling = true
                        try { list.animateScrollToItem(requestedIndex + 1 + if (state.hasEarlier) 1 else 0) }
                        finally { autoScrolling = false }
                        initialPage = false
                        account.consumeScrollTarget(requested)
                        return@LaunchedEffect
                    }
                    if (state.messages.isNotEmpty() && (initialPage || state.followingLatest)) {
                        autoScrolling = true
                        try { list.animateScrollToItem(state.messages.lastIndex + 1 + if (state.hasEarlier) 1 else 0) }
                        finally { autoScrolling = false }
                        initialPage = false
                    }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
                    contentPadding = PaddingValues(horizontal = AppSpacing.screen, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item(key = "status") {
                        AvailabilityNotice(state.availability, state.refreshing, account::refresh)
                        if (!selected.joined) {
                            Text(stringResource(if (selected.membershipRequested) R.string.spaces_waiting_admission else R.string.spaces_creating),
                                style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(if (selected.membershipRequested) R.string.spaces_waiting_detail else R.string.spaces_creating_detail),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (!selected.canSend) Text(stringResource(R.string.spaces_control_pending), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.hasEarlier) item(key = "earlier") {
                        OutlinedButton(onClick = account::olderMessages, enabled = !state.working,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_earlier_messages)) }
                    }
                    if (state.messages.isEmpty() && selected.joined) item {
                        Text(stringResource(R.string.spaces_no_messages), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp))
                    }
                    items(state.messages, key = { it.id }) { message ->
                        MessageRow(message, state.draft.isEmpty() && !state.working) { account.restoreRejectedDraft(message.id) }
                    }
                    state.problem?.let { problem -> item(key = "problem") { ProblemNotice(problem, account::clearProblem) } }
                }
                }
                AnimatedVisibility(state.hasLater,
                    enter = fadeIn(tween(AppSpacing.transitionMillis)) + expandVertically(tween(AppSpacing.transitionMillis)),
                    exit = fadeOut(tween(AppSpacing.transitionMillis)) + shrinkVertically(tween(AppSpacing.transitionMillis))) {
                    OutlinedButton(onClick = account::latestMessages,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.spaces_latest_messages))
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(state.draft, { account.editDraft(selected.id, it) }, Modifier.weight(1f), enabled = !state.savingNavigation && !state.loadingChannel,
                        placeholder = { Text(stringResource(R.string.spaces_message_hint)) }, shape = RoundedCornerShape(18.dp),
                        minLines = 1, maxLines = 4)
                    FilledIconButton(onClick = account::send, enabled = selected.canSend && state.draft.isNotBlank() && !state.working && !state.loadingChannel,
                        modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_send), stringResource(R.string.spaces_send), Modifier.size(22.dp))
                    }
                }
            } }
        }
    }

    DesignSheet(createOpen, stringResource(R.string.spaces_create), { createOpen = false }) { first ->
        Text(stringResource(R.string.spaces_create_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
        val valid = communityName.isNotBlank() && communityName.codePointCount(0, communityName.length) <= 64
        OutlinedTextField(communityName, { communityName = it }, first.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.spaces_name)) }, shape = RoundedCornerShape(14.dp),
            supportingText = { Text(stringResource(R.string.spaces_name_limit)) })
        state.problem?.let { ProblemNotice(it, account::clearProblem) }
        Button(onClick = { account.create(communityName.trim()) }, enabled = valid && !state.working,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_create)) }
        if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    DesignSheet(joinOpen, stringResource(R.string.spaces_join_invitation), { joinOpen = false; account.dismissPreview() },
        contentKey = invitationReview?.spaceId,
        footer = { first ->
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            Button(onClick = {
                focus.clearFocus(); keyboard?.hide()
                if (invitationReview == null) account.preview(invitationText.trim()) else account.join(invitationText.trim())
            }, enabled = joinOpen && invitationText.isNotBlank() && !state.working &&
                (invitationReview == null || state.preview != null),
                modifier = (if (invitationReview != null) first else Modifier).fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (invitationReview == null) R.string.spaces_preview else R.string.spaces_join))
            }
            if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
        }) { first ->
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        Text(stringResource(R.string.spaces_join_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.spaces_join_as, registeredIdentity(account.ownerKey).name),
            style = MaterialTheme.typography.labelLarge)
        AnimatedContent(invitationReview, label = "spaces-invitation-review", transitionSpec = {
            fadeIn(tween(AppSpacing.transitionMillis)) togetherWith fadeOut(tween(AppSpacing.transitionMillis))
        }) { preview ->
            if (preview == null) OutlinedTextField(invitationText, { invitationText = it.take(8192); clearInvitationReview() },
                (if (invitationReview == null) first else Modifier).fillMaxWidth(),
                label = { Text(stringResource(R.string.spaces_invitation_link)) }, maxLines = 4, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (invitationText.isNotBlank() && !state.working) {
                        focus.clearFocus(); keyboard?.hide(); account.preview(invitationText.trim())
                    }
                }))
            else Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(R.drawable.ic_spaces), null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.spaces_join_preview, preview.name), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.spaces_admission_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = ::clearInvitationReview, enabled = joinOpen && !state.working && state.preview != null) {
                        Text(stringResource(R.string.spaces_change_invitation))
                    }
                }
            }
        }
        state.problem?.let { ProblemNotice(it, account::clearProblem) }
    }
    InvitationSheet(inviteOpen, state.invitation, state.working, selected?.canSend == true,
        { inviteOpen = false }, account::refresh, account::invite)
    DesignSheet(informationOpen && selected != null, stringResource(R.string.spaces_information), { informationOpen = false }) { first ->
        selected?.let { community ->
            Text(community.name, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.spaces_general), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(if (community.joined) R.string.spaces_member_confirmed
                else if (community.membershipRequested) R.string.spaces_waiting_admission else R.string.spaces_creating))
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text(stringResource(R.string.spaces_channel_privacy), Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (community.canInvite) OutlinedButton(onClick = {
                informationOpen = false; inviteOpen = true
                if (state.invitation == null) account.invite()
            }, enabled = !state.working && community.canSend,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.spaces_invite))
            }
            TextButton(onClick = { informationOpen = false },
                modifier = first.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_close)) }
        }
    }
}

@Composable private fun CommunityRow(community: SpaceClient.Community, selected: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.spaces_open_community, community.name)
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = CircleShape, color = Color(0xffa35b43)) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Text(community.name.codePoints().limit(1).toArray().let { String(it, 0, it.size) }, color = Color.White,
                        style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(community.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (community.joined) R.string.spaces_general else R.string.spaces_unconfirmed),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_forward), null, Modifier.size(20.dp))
        }
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
    dismiss: () -> Unit, retry: () -> Unit, generate: (Int) -> Unit) {
    val context = LocalContext.current
    var copied by remember(open) { mutableStateOf(false) }
    var failed by remember(open) { mutableStateOf(false) }
    val copyLabel = stringResource(R.string.spaces_invitation_link)
    var newLink by remember(open) { mutableStateOf(false) }
    var days by remember(open) { mutableIntStateOf(7) }
    LaunchedEffect(invitation?.link) { copied = false; newLink = false }
    DesignSheet(open, stringResource(R.string.spaces_invite), dismiss) { first ->
        if (invitation?.expired == true || newLink) {
            Text(stringResource(if (invitation?.expired == true) R.string.spaces_invitation_expired else R.string.spaces_new_invitation_detail))
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
            }, modifier = first.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (copied) R.string.spaces_copied else R.string.spaces_copy_invitation))
            }
            OutlinedButton(onClick = {
                try {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, invitation.link)
                    }, null))
                    failed = false
                } catch (_: Exception) { failed = true }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_share_invitation)) }
            TextButton(onClick = { newLink = true }, enabled = canGenerate && !working,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.spaces_new_invitation)) }
            if (failed) Text(stringResource(R.string.spaces_share_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}
