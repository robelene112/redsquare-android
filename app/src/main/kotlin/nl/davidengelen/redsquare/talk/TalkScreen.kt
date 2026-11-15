package nl.davidengelen.redsquare.talk

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.ProfileAvatar
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.*
import org.webrtc.SurfaceViewRenderer
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack

internal data class TalkDraft(val url: String, val direct: String? = null,
    val owner: String? = null, val savedBackend: SaitoBackend? = null,
    val microphone: Boolean = true, val camera: Boolean? = null) : java.io.Serializable
internal val LocalTalkLaunch = staticCompositionLocalOf<((String, TalkMode) -> Unit)?> { null }

internal class TalkPermissionGate(val run: (Boolean, () -> Unit) -> Unit, val notice: @Composable () -> Unit)
@Composable internal fun rememberTalkPermissionGate(): TalkPermissionGate {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<Boolean, () -> Unit>?>(null) }
    var denied by remember { mutableStateOf(false) }
    fun available(camera: Boolean): Boolean = TalkCapture.permitted(context, camera) && TalkService.available(context)

    val scope = rememberCoroutineScope()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action = pending; pending = null
        if (action != null && available(action.first)) scope.launch {
            val resumed = kotlinx.coroutines.withTimeoutOrNull(5000) {
                lifecycle.currentStateFlow.first { it.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
            }
            if (resumed != null) { denied = false; action.second() } else denied = true
        } else denied = true
    }
    return TalkPermissionGate(run = { camera, action ->
        if (available(camera)) { denied = false; action() }
        else {
            pending = camera to action
            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (camera) permissions.add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            launcher.launch(permissions.toTypedArray())
        }
    }, notice = {
        InlineStatus(if (denied) stringResource(R.string.talk_permissions_needed) else null, StatusTone.INFO,
            actionLabel = stringResource(R.string.settings), onAction = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}")))
            })
    })
}

@Composable internal fun TalkIncomingDialog(account: TalkAccount?, owner: String?, backend: SaitoBackend?, onAccepted: () -> Unit) {
    val live by TalkRuntime.call.collectAsState()
    // Answer changes INCOMING to JOINING before its suspend function completes.
    // Keep navigation ownership outside the disappearing dialog's composition.
    val scope = rememberCoroutineScope()
    val latestOwner by rememberUpdatedState(owner)
    val latestBackend by rememberUpdatedState(backend)
    val latestAccount by rememberUpdatedState(account)
    val latestAccepted by rememberUpdatedState(onAccepted)
    val call = live?.takeIf { it.owner == owner && it.savedBackend == backend && it.phase == CallPhase.INCOMING } ?: return
    if (account == null) return
    val gate = rememberTalkPermissionGate()
    var accepting by remember(call.token) { mutableStateOf(false) }
    val name = registeredIdentity(requireNotNull(call.direct)).name
    fun answer(camera: Boolean) {
        accepting = true
        scope.launch {
            try {
                if (account.accept(call.token, camera)) {
                    val active = TalkRuntime.call.value
                    if (latestAccount === account && latestOwner == call.owner && latestBackend == call.savedBackend &&
                        active?.token == call.token && active.phase.started) latestAccepted()
                }
            } finally { accepting = false }
        }
    }
    AlertDialog(onDismissRequest = { TalkRuntime.end(call.token) },
        icon = { Icon(painterResource(R.drawable.ic_phone), null) },
        title = { Text(stringResource(R.string.talk_incoming)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(if (call.link.room.mode == TalkMode.VIDEO) R.string.talk_incoming_video else R.string.talk_incoming_audio))
            gate.notice()
        } }, confirmButton = {
            Column {
                Button(enabled = !accepting, modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    gate.run(call.link.room.mode == TalkMode.VIDEO) {
                        answer(call.link.room.mode == TalkMode.VIDEO)
                    }
                }) { Text(stringResource(R.string.talk_accept)) }
                if (call.link.room.mode == TalkMode.VIDEO) TextButton(enabled = !accepting,
                    modifier = Modifier.heightIn(min = 48.dp), onClick = {
                        gate.run(false) { answer(false) }
                    }) { Text(stringResource(R.string.talk_audio_only)) }
            }
        }, dismissButton = {
            TextButton(onClick = { TalkRuntime.end(call.token) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.talk_decline))
            }
        })
}

@Composable internal fun TalkActiveBar(owner: String?, backend: SaitoBackend?, onOpen: () -> Unit) {
    val live by TalkRuntime.call.collectAsState()
    val call = live?.takeIf { it.owner == owner && it.savedBackend == backend }
    AnimatedVisibility(visible = call?.phase?.started == true,
        enter = expandVertically(tween(AppSpacing.transitionMillis), expandFrom = Alignment.Top),
        exit = shrinkVertically(tween(AppSpacing.transitionMillis), shrinkTowards = Alignment.Top)) {
    if (call != null) Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = 4.dp)) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpen, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_phone), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.talk_return), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            FrameIconButton(R.drawable.ic_call_end, stringResource(R.string.talk_end), onClick = { TalkRuntime.end(call.token) })
        }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun TalkScreen(wallet: SaitoWallet, backend: SaitoBackend, account: TalkAccount,
    entry: TalkDraft?, onEntry: (TalkDraft?) -> Unit, chrome: @Composable (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val live by TalkRuntime.call.collectAsState()
    val call = live?.takeIf { it.owner == wallet.publicKey && it.savedBackend == backend }
    val requested by TalkRuntime.choices.collectAsState()
    val desired = requested?.takeIf { it.token == call?.token }
    val online by account.online.collectAsState()
    val gate = rememberTalkPermissionGate()
    val link = remember(entry?.url) { entry?.let { TalkLink.parse(it.url) } }
    val showingInvitation = link != null && entry != null
    // Choices travel with the saved invitation, including module and Activity restoration.
    val camera = entry?.camera ?: (link?.room?.mode != TalkMode.VOICE)
    val microphone = entry?.microphone ?: true
    var starting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var joinText by key(wallet.publicKey, backend.id) { rememberSaveable { mutableStateOf("") } }
    var joinError by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val shareInvitationLabel = stringResource(R.string.talk_share_invite)
    var copied by remember(call?.token) { mutableStateOf(false) }
    LaunchedEffect(call?.token, copied) {
        if (copied) { kotlinx.coroutines.delay(2000); copied = false }
    }
    val destination = entry?.url ?: if (call?.phase?.live == true) "${call.token}:active" else "calls-home"
    // A preview and the active call are different reading destinations.
    val listState = key(destination) { rememberLazyListState() }
    Column(Modifier.fillMaxSize()) {
        chrome(false)
        SingleOwnerReveal(destination, Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (call?.phase?.live != true || call.phase == CallPhase.INCOMING || showingInvitation)
                item { Text(stringResource(R.string.talk_calls), style = MaterialTheme.typography.headlineMedium) }
            if (call?.phase?.live == true && call.phase != CallPhase.INCOMING && !showingInvitation) {
                val invitationFirst = call.direct == null && call.link.room.host == wallet.publicKey && call.participants.isEmpty()
                val invitation: @Composable () -> Unit = {
                    TalkInvitationCard(copied = copied, onCopy = {
                        val room = call.link.room.copy(peers = (call.participants.map { it.key } + wallet.publicKey).distinct())
                        clipboard.setText(AnnotatedString(TalkLink(call.link.backend, room).url())); copied = true
                    }, onShare = {
                        val room = call.link.room.copy(peers = (call.participants.map { it.key } + wallet.publicKey).distinct())
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, TalkLink(call.link.backend, room).url()), shareInvitationLabel))
                    })
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        call.direct?.let { Text(registeredIdentity(it).name, style = MaterialTheme.typography.titleLarge) }
                        val roomReady = call.direct == null && call.link.room.host == wallet.publicKey &&
                            call.participants.isEmpty() && online && call.phase.started
                        Text(stringResource(if (roomReady) R.string.talk_room_ready else callPhaseLabel(call.phase)),
                            style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.talk_network, call.link.backend.domain), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (invitationFirst) item { invitation() }
                items(call.participants, key = { it.key }) { peer ->
                    TalkVideoTile(peer.key, registeredIdentity(peer.key).name, peer.video, peer.microphone, peer.camera, peer.connected,
                        modifier = Modifier.animateItem())
                }
                item { TalkVideoTile(wallet.publicKey, stringResource(R.string.talk_you), call.localVideo,
                    call.microphone, call.camera && !call.cameraPaused, connected = true, mirror = call.frontCamera,
                    previewRatio = if (call.participants.isEmpty()) 16f / 10f else 4f / 3f,
                    modifier = if (call.participants.isEmpty()) Modifier.fillMaxWidth() else Modifier.widthIn(max = 180.dp)) }
                if (call.direct == null && !invitationFirst) item { invitation() }
            } else if (link != null && entry != null) {
                item {
                    Text(stringResource(if (entry.direct != null) R.string.talk_ready_direct else R.string.talk_ready_room),
                        style = MaterialTheme.typography.titleLarge)
                    entry.direct?.let { Text(registeredIdentity(it).name, style = MaterialTheme.typography.titleMedium) }
                        ?: Text(stringResource(R.string.talk_host, registeredIdentity(link.room.host).name),
                            style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.talk_network, link.backend.domain), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(if (call?.phase?.live == true) R.string.talk_other_call_privacy else R.string.talk_prejoin_privacy),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entry.direct == null) Text(stringResource(R.string.talk_room_link_privacy),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = microphone, role = Role.Checkbox, onValueChange = { onEntry(entry.copy(microphone = it)) }),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = microphone, onCheckedChange = null)
                        Text(stringResource(R.string.talk_start_microphone))
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = camera, role = Role.Checkbox, onValueChange = { onEntry(entry.copy(camera = it)) }),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = camera, onCheckedChange = null)
                        Text(stringResource(R.string.talk_start_camera))
                    }
                    gate.notice()
                    InlineStatus(if (call?.phase?.live == true) stringResource(R.string.talk_other_call_active) else null, StatusTone.INFO)
                    InlineStatus(if (failed) stringResource(R.string.talk_start_failed) else null, StatusTone.ERROR)
                }
                item {
                    Button(enabled = !starting && call?.phase?.live != true, onClick = {
                        gate.run(camera) {
                            starting = true; failed = false
                            scope.launch {
                                if (account.start(if (entry.direct == null) link else link.copy(room = link.room.copy(
                                        mode = if (camera) TalkMode.VIDEO else TalkMode.VOICE)), entry.direct, camera, microphone)) onEntry(null) else failed = true
                                starting = false
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(if (starting) R.string.talk_starting else if (entry.direct != null) R.string.talk_call else R.string.talk_join))
                    }
                    TextButton(onClick = { onEntry(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.talk_cancel)) }
                }
            } else {
                if (call != null && !call.phase.live) item { InlineStatus(stringResource(callPhaseLabel(call.phase)), StatusTone.INFO) }
                item {
                    Text(stringResource(R.string.talk_intro), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.talk_intro_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Button(enabled = !starting, onClick = {
                        starting = true
                        scope.launch {
                            val room = withContext(Dispatchers.IO) { TalkRoom(SaitoWallet.create().publicKey, wallet.publicKey) }
                            onEntry(TalkDraft(TalkLink(backend, room).url(), owner = wallet.publicKey, savedBackend = backend)); starting = false
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(painterResource(R.drawable.ic_video), null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.talk_create_room))
                    }
                }
                item {
                    OutlinedTextField(value = joinText, onValueChange = { joinText = it; joinError = false },
                        label = { Text(stringResource(R.string.talk_invite_link)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    InlineStatus(if (joinError) stringResource(R.string.talk_invalid_link) else null, StatusTone.ERROR)
                    OutlinedButton(onClick = {
                        val parsed = TalkLink.parse(joinText.trim())
                        if (parsed == null) joinError = true else onEntry(TalkDraft(parsed.url(), owner = wallet.publicKey, savedBackend = backend))
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.talk_review_invite)) }
                }
                item {
                    Text(stringResource(if (online) R.string.talk_available else R.string.talk_checking_network),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.talk_incoming_limit), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        }
        if (call?.phase?.started == true && !showingInvitation) {
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = 16.dp)) {
                    TalkControlGrid(call, desired, gate)
                    if (call.cameraPaused) Text(stringResource(R.string.talk_camera_paused), style = MaterialTheme.typography.bodySmall)
                    InlineStatus(when {
                        call.cameraUnavailable -> stringResource(R.string.talk_camera_unavailable)
                        call.cameraSwitchUnavailable -> stringResource(R.string.talk_camera_switch_failed)
                        else -> null
                    }, StatusTone.INFO)
                    gate.notice()
                    Spacer(Modifier.height(16.dp))
                    TalkEndCallButton(call.token)
            }
            }
        }
        if (showingInvitation && call?.phase?.live == true) TextButton(onClick = { onEntry(null) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.talk_return)) }
        if (call?.phase?.live == true && (showingInvitation || !call.phase.started))
            TalkEndCallButton(call.token, Modifier.padding(horizontal = AppSpacing.screen, vertical = 8.dp))
    }
}

@Composable private fun TalkControlGrid(call: TalkCall, desired: TalkChoices?, gate: TalkPermissionGate) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val microphoneOn = desired?.microphone ?: call.microphone
    val cameraOn = desired?.camera ?: call.camera
    val microphone: @Composable RowScope.() -> Unit = {
        TalkCallControl(icon = if (microphoneOn) R.drawable.ic_microphone else R.drawable.ic_microphone_off,
            label = stringResource(if (microphoneOn) R.string.talk_mute else R.string.talk_unmute),
            state = stringResource(if (microphoneOn) R.string.talk_microphone_on else R.string.talk_muted),
            selected = !microphoneOn, horizontal = largeText, modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = { TalkRuntime.controls(call.token, microphone = !microphoneOn) })
    }
    val camera: @Composable RowScope.() -> Unit = {
        TalkCallControl(icon = if (cameraOn) R.drawable.ic_video else R.drawable.ic_video_off,
            label = stringResource(if (cameraOn) R.string.talk_camera_off else R.string.talk_camera_on),
            state = stringResource(if (cameraOn) R.string.talk_camera_enabled else R.string.talk_camera_disabled),
            selected = !cameraOn, horizontal = largeText, modifier = Modifier.weight(1f).fillMaxHeight(), onClick = {
                if (cameraOn) TalkRuntime.controls(call.token, camera = false)
                else gate.run(true) { TalkRuntime.controls(call.token, camera = true) }
            })
    }
    val devices: @Composable RowScope.() -> Unit = {
        if (call.camera && call.canSwitchCamera) TalkCallControl(icon = R.drawable.ic_camera_switch,
            label = stringResource(R.string.talk_switch_camera), horizontal = largeText,
            modifier = Modifier.weight(1f).fillMaxHeight(), onClick = { TalkRuntime.switchCamera(call.token) })
        TalkAudioRouteButton(Modifier.weight(1f).fillMaxHeight(), horizontal = largeText)
    }
    if (largeText) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            microphone(); camera()
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) { devices() }
    } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        microphone(); camera(); devices()
    }
}

@Composable private fun TalkInvitationCard(copied: Boolean, onCopy: () -> Unit, onShare: () -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val share: @Composable (Modifier) -> Unit = { modifier ->
        Button(onClick = onShare, modifier = modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.talk_share_invite), textAlign = TextAlign.Center)
        }
    }
    val copy: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedButton(onClick = onCopy, modifier = modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
            Text(stringResource(if (copied) R.string.talk_copied else R.string.talk_copy_invite), textAlign = TextAlign.Center)
        }
    }
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(AppSpacing.cardRadius),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.talk_invite_people), style = MaterialTheme.typography.titleMedium)
            if (largeText) Column(Modifier.fillMaxWidth().testTag("talk-invitation-actions"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                share(Modifier.fillMaxWidth()); copy(Modifier.fillMaxWidth())
            } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("talk-invitation-actions"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                share(Modifier.weight(1f).fillMaxHeight()); copy(Modifier.weight(1f).fillMaxHeight())
            }
            Text(stringResource(R.string.talk_room_link_privacy), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun TalkCallControl(icon: Int, label: String, modifier: Modifier = Modifier,
    selected: Boolean = false, state: String? = null, horizontal: Boolean = false, onClick: () -> Unit) {
    val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh, tween(AppSpacing.transitionMillis), label = "talk-control-color")
    Surface(onClick = onClick, modifier = modifier.heightIn(min = if (horizontal) 64.dp else 80.dp).semantics {
        role = Role.Button
        state?.let { stateDescription = it }
    }, shape = RoundedCornerShape(AppSpacing.controlRadius), color = color,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface) {
        if (horizontal) Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(icon), null, Modifier.size(24.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        } else Column(Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            Icon(painterResource(icon), null, Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable private fun TalkEndCallButton(token: String, modifier: Modifier = Modifier) {
    Button(onClick = { TalkRuntime.end(token) }, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(AppSpacing.controlRadius),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
        Icon(painterResource(R.drawable.ic_call_end), null, Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.talk_end))
    }
}

@Composable private fun TalkVideoTile(publicKey: String, name: String, video: VideoTrack?, microphone: Boolean,
    camera: Boolean, connected: Boolean, mirror: Boolean = false, previewRatio: Float = 4f / 3f, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(previewRatio).clip(RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                if (video != null && camera && connected) key(video) { TalkVideo(video, mirror) }
                else ProfileAvatar(publicKey, Modifier.size(72.dp))
            }
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!connected) Text(stringResource(R.string.talk_connecting_peer), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!microphone) Icon(painterResource(R.drawable.ic_microphone_off), stringResource(R.string.talk_muted), Modifier.size(20.dp))
            }
        }
    }
}

@Composable private fun TalkVideo(track: VideoTrack, mirror: Boolean) {
    val context = LocalContext.current
    val renderer = remember(track) { SurfaceViewRenderer(context).apply {
        init(RtcPlatform.graphics(context), null); setMirror(mirror)
        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT); setEnableHardwareScaler(true)
    } }
    DisposableEffect(track, renderer) {
        track.addSink(renderer)
        onDispose { runCatching { track.removeSink(renderer) }; renderer.release() }
    }
    AndroidView(factory = { renderer }, update = { it.setMirror(mirror) }, modifier = Modifier.fillMaxSize())
}

internal fun callPhaseLabel(phase: CallPhase): Int = when (phase) {
    CallPhase.VERIFYING -> R.string.talk_verifying
    CallPhase.INCOMING -> R.string.talk_incoming
    CallPhase.DIALING -> R.string.talk_dialing
    CallPhase.JOINING -> R.string.talk_connecting
    CallPhase.ACTIVE -> R.string.talk_in_call
    CallPhase.ENDED -> R.string.talk_ended
    CallPhase.DECLINED -> R.string.talk_declined
    CallPhase.NO_ANSWER -> R.string.talk_no_answer
    CallPhase.INTERRUPTED -> R.string.talk_interrupted
    CallPhase.UNAVAILABLE -> R.string.talk_unavailable
}
