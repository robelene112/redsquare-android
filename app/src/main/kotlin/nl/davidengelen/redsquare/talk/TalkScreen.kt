package nl.davidengelen.redsquare.talk

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
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
    val owner: String? = null, val savedBackend: SaitoBackend? = null) : java.io.Serializable
internal val LocalTalkLaunch = staticCompositionLocalOf<((String, TalkMode) -> Unit)?> { null }

internal class TalkPermissionGate(val run: (Boolean, () -> Unit) -> Unit, val notice: @Composable () -> Unit)
@Composable internal fun rememberTalkPermissionGate(): TalkPermissionGate {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<Boolean, () -> Unit>?>(null) }
    var denied by remember { mutableStateOf(false) }
    fun available(camera: Boolean): Boolean {
        TalkService.channel(context)
        val notifications = context.getSystemService(NotificationManager::class.java)
        return TalkCapture.permitted(context, camera) && notifications.areNotificationsEnabled() &&
            notifications.getNotificationChannel(TalkService.CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE &&
            (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
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
    val call = live?.takeIf { it.owner == owner && it.savedBackend == backend && it.phase == CallPhase.INCOMING } ?: return
    if (account == null) return
    val scope = rememberCoroutineScope()
    val gate = rememberTalkPermissionGate()
    var accepting by remember(call.token) { mutableStateOf(false) }
    val name = registeredIdentity(requireNotNull(call.direct)).name
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
                        accepting = true
                        scope.launch { if (account.accept(call.token, call.link.room.mode == TalkMode.VIDEO)) onAccepted(); accepting = false }
                    }
                }) { Text(stringResource(R.string.talk_accept)) }
                if (call.link.room.mode == TalkMode.VIDEO) TextButton(enabled = !accepting,
                    modifier = Modifier.heightIn(min = 48.dp), onClick = {
                        gate.run(false) { accepting = true; scope.launch { if (account.accept(call.token, false)) onAccepted(); accepting = false } }
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
    val call = live?.takeIf { it.owner == owner && it.savedBackend == backend && it.phase.started } ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun TalkScreen(wallet: SaitoWallet, backend: SaitoBackend, account: TalkAccount,
    entry: TalkDraft?, onEntry: (TalkDraft?) -> Unit, chrome: @Composable (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val live by TalkRuntime.call.collectAsState()
    val call = live?.takeIf { it.owner == wallet.publicKey && it.savedBackend == backend }
    val online by account.online.collectAsState()
    val gate = rememberTalkPermissionGate()
    val link = remember(entry?.url) { entry?.let { TalkLink.parse(it.url) } }
    var camera by remember(entry?.url) { mutableStateOf(link?.room?.mode != TalkMode.VOICE) }
    var microphone by remember(entry?.url) { mutableStateOf(true) }
    var starting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var joinText by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    var copied by remember(call?.token) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        chrome(false)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(AppSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { Text(stringResource(R.string.talk_calls), style = MaterialTheme.typography.headlineMedium) }
            if (call?.phase?.live == true && call.phase !in setOf(CallPhase.INCOMING, CallPhase.VERIFYING)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(callPhaseLabel(call.phase)), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.talk_network, call.link.backend.domain), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { TalkVideoTile(wallet.publicKey, stringResource(R.string.talk_you), call.localVideo,
                    call.microphone, call.camera && !call.cameraPaused, connected = true, mirror = true) }
                items(call.participants, key = { it.key }) { peer ->
                    TalkVideoTile(peer.key, registeredIdentity(peer.key).name, peer.video, peer.microphone, peer.camera, peer.connected)
                }
                if (call.participants.isEmpty()) item {
                    Text(stringResource(R.string.talk_waiting), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (call.direct == null) item {
                    OutlinedButton(onClick = {
                        val room = call.link.room.copy(peers = (call.participants.map { it.key } + wallet.publicKey).distinct())
                        clipboard.setText(AnnotatedString(TalkLink(call.link.backend, room).url())); copied = true
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(if (copied) R.string.talk_copied else R.string.talk_copy_invite))
                    }
                    TextButton(onClick = {
                        val room = call.link.room.copy(peers = (call.participants.map { it.key } + wallet.publicKey).distinct())
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, TalkLink(call.link.backend, room).url()), context.getString(R.string.talk_share_invite)))
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.talk_share_invite)) }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !call.microphone, onClick = { TalkRuntime.controls(call.token, microphone = !call.microphone) },
                            label = { Text(stringResource(if (call.microphone) R.string.talk_mute else R.string.talk_unmute)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_microphone), null, Modifier.size(20.dp)) },
                            modifier = Modifier.heightIn(min = 48.dp))
                        FilterChip(selected = call.camera, onClick = {
                            if (call.camera) TalkRuntime.controls(call.token, camera = false)
                            else gate.run(true) { TalkRuntime.controls(call.token, camera = true) }
                        }, label = { Text(stringResource(if (call.camera) R.string.talk_camera_off else R.string.talk_camera_on)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_video), null, Modifier.size(20.dp)) },
                            modifier = Modifier.heightIn(min = 48.dp))
                        if (call.camera) OutlinedButton(onClick = { TalkRuntime.switchCamera(call.token) },
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.talk_switch_camera)) }
                        TalkAudioRouteButton()
                    }
                    if (call.cameraPaused) Text(stringResource(R.string.talk_camera_paused), style = MaterialTheme.typography.bodySmall)
                    gate.notice()
                }
            } else if (link != null && entry != null) {
                item {
                    Text(stringResource(if (entry.direct != null) R.string.talk_ready_direct else R.string.talk_ready_room),
                        style = MaterialTheme.typography.titleLarge)
                    entry.direct?.let { Text(registeredIdentity(it).name, style = MaterialTheme.typography.titleMedium) }
                    Text(stringResource(R.string.talk_network, link.backend.domain), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.talk_prejoin_privacy), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = microphone, onCheckedChange = { microphone = it })
                        Text(stringResource(R.string.talk_start_microphone))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = camera, onCheckedChange = { camera = it })
                        Text(stringResource(R.string.talk_start_camera))
                    }
                    gate.notice()
                    InlineStatus(if (failed) stringResource(R.string.talk_start_failed) else null, StatusTone.ERROR)
                }
                item {
                    Button(enabled = !starting, onClick = {
                        gate.run(camera) {
                            starting = true; failed = false
                            scope.launch {
                                if (account.start(link, entry.direct, camera, microphone)) onEntry(null) else failed = true
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
        if (call?.phase?.started == true) Button(onClick = { TalkRuntime.end(call.token) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen, vertical = 8.dp).heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
            Icon(painterResource(R.drawable.ic_call_end), null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.talk_end))
        }
    }
}

@Composable private fun TalkVideoTile(publicKey: String, name: String, video: VideoTrack?, microphone: Boolean,
    camera: Boolean, connected: Boolean, mirror: Boolean = false) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
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
    AndroidView(factory = { renderer }, modifier = Modifier.fillMaxSize())
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
