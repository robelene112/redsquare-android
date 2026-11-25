package nl.davidengelen.redsquare.spaces

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.ProfileAvatar
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.talk.CallParticipant
import nl.davidengelen.redsquare.talk.CallPhase
import nl.davidengelen.redsquare.talk.TalkRuntime
import nl.davidengelen.redsquare.talk.TalkService
import nl.davidengelen.redsquare.talk.TalkVideo
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone
import org.webrtc.VideoTrack

@Composable
internal fun SpaceRoomChannel(account: SpacesAccount, state: SpacesAccount.State, channel: SpaceClient.Channel,
    onBrowse: () -> Unit, modifier: Modifier = Modifier) {
    val target = SpacesAccount.Target(requireNotNull(state.selected), channel.id)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permission = rememberRoomPermission()
    val global by TalkRuntime.call.collectAsState()
    val current = global?.takeIf { it.owner == account.ownerKey && it.savedBackend == account.backend && it.phase.started }
    val call = current?.takeIf { item -> item.spaceRoom?.let { it.space == target.space && it.channel == target.channel } == true }
    val joining = call?.phase == CallPhase.JOINING
    var room by remember(target) { mutableStateOf<SpaceVoiceRoom?>(null) }
    var loading by remember(target) { mutableStateOf(true) }
    var failed by remember(target) { mutableStateOf(false) }
    var activity by remember(target) { mutableStateOf(false) }
    var failureMessage by remember(target) { mutableIntStateOf(0) }
    val online = state.availability == SpacesAccount.Availability.ONLINE
    LaunchedEffect(account, target, online, channel.archived, call?.token) {
        if (!online || channel.archived || call != null) return@LaunchedEffect
        while (true) {
            try {
                room = account.observeVoiceRoom(target)
                failed = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { room = null; failed = true }
            finally { loading = false }
            delay(2_500)
        }
    }

    val roomStatus = when {
        channel.archived -> R.string.spaces_room_archived
        joining -> R.string.spaces_room_joining
        call != null -> R.string.spaces_room_joined
        !online -> R.string.spaces_room_offline
        failureMessage != 0 -> failureMessage
        failed -> R.string.spaces_room_unavailable
        room == null -> R.string.spaces_room_loading
        else -> roomStatus(requireNotNull(room).status)
    }
    val prejoin = room?.participants.orEmpty().map {
        CallParticipant(it.wallet, connected = true, microphone = false, camera = false,
            roomSession = it.session, roomMaySpeak = it.canSpeak)
    }
    val participants = if (!channel.archived && online) (call?.participants ?: prejoin) else call?.participants.orEmpty()
    val participantCount = participants.size + if (call?.phase == CallPhase.ACTIVE) 1 else 0
    val role = state.members.firstOrNull { it.publicKey == account.ownerKey }?.role
    var remove by remember(target) { mutableStateOf<CallParticipant?>(null) }
    fun moderate(participant: CallParticipant, removeFromRoom: Boolean) {
        val id = participant.roomSession ?: return
        if (activity) return
        activity = true
        failureMessage = 0
        scope.launch {
            try { room = account.moderateVoiceRoom(target, id, removeFromRoom) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failureMessage = R.string.spaces_room_moderation_unavailable }
            finally { activity = false }
        }
    }

    LazyColumn(modifier.fillMaxWidth().testTag("spaces-room-content"), contentPadding = PaddingValues(AppSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_video), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(channel.profile.name, style = MaterialTheme.typography.titleLarge)
                        Text(if (roomStatus != 0) stringResource(roomStatus)
                            else pluralStringResource(R.plurals.spaces_room_participants, participantCount, participantCount),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (call?.phase == CallPhase.ACTIVE) item {
            RoomParticipant(account.ownerKey, stringResource(R.string.talk_you), call.localVideo,
                call.microphone, call.camera, mirror = call.frontCamera)
        }
        if (call != null && failureMessage != 0) item {
            InlineStatus(stringResource(failureMessage), StatusTone.WARNING)
        }
        items(participants, key = { it.key }) { participant ->
            val name = registeredIdentity(participant.key).name
            val theirRole = state.members.firstOrNull { it.publicKey == participant.key }?.role
            val canModerate = participant.key != account.ownerKey && (role == SpaceRole.OWNER && theirRole in setOf(SpaceRole.ADMIN, SpaceRole.MEMBER) ||
                role == SpaceRole.ADMIN && theirRole == SpaceRole.MEMBER)
            RoomParticipant(participant.key, name, participant.video, participant.microphone, participant.camera,
                showConnecting = call != null && !participant.connected,
                actions = if (!canModerate) null else ({
                    var open by remember(participant.key) { mutableStateOf(false) }
                    Box {
                        FrameIconButton(R.drawable.ic_more, stringResource(R.string.spaces_room_moderate, name), enabled = !activity) { open = true }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            if (participant.roomMaySpeak) DropdownMenuItem(text = { Text(stringResource(R.string.talk_mute)) }, onClick = {
                                open = false; moderate(participant, false)
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.spaces_room_remove)) }, onClick = {
                                open = false; remove = participant
                            })
                        }
                    }
                }))
        }
        if (call == null) {
            item {
                Text(stringResource(R.string.spaces_room_capture_off), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                val available = online && !channel.archived && !loading && room?.status == SpaceVoiceRoom.Status.AVAILABLE && current == null
                Button(enabled = available && !activity, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
                    fun join() {
                        if (!TalkService.available(context)) { failureMessage = R.string.spaces_room_notification_permission; return }
                        activity = true
                        failureMessage = 0
                        scope.launch {
                            try {
                                val authority = account.roomAuthority(target)
                                val result = SpaceRoomCallRuntime.join(context, authority,
                                    SpaceRoomDestination(target.space, target.channel, channel.profile.name))
                                if (result != SpaceVoiceRoom.Status.AVAILABLE) failureMessage =
                                    result?.let(::roomStatus)?.takeIf { it != 0 } ?: R.string.spaces_room_unavailable
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { failureMessage = R.string.spaces_room_unavailable }
                            finally { activity = false }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 33) permission.request(Manifest.permission.POST_NOTIFICATIONS, ::join) else join()
                }) { Text(stringResource(R.string.spaces_room_join)) }
            }
            if (current != null) item { Text(stringResource(R.string.spaces_room_another_call)) }
        } else if (!joining) {
            item {
                if (!call.microphoneAllowed) InlineStatus(stringResource(R.string.spaces_room_speaking_paused), StatusTone.INFO)
                RoomCaptureControls(call.token, call.microphone, call.camera, call.microphoneAllowed, permission)
            }
            if (call.camera && call.canSwitchCamera) item {
                OutlinedButton(onClick = { TalkRuntime.switchCamera(call.token) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.talk_switch_camera))
                }
            }
            item { OutlinedButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.spaces_room_browse))
            } }
            item {
                Button(onClick = { TalkRuntime.end(call.token) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                    Icon(painterResource(R.drawable.ic_call_end), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.spaces_room_leave))
                }
            }
        }
        if (permission.denied) item {
            InlineStatus(stringResource(R.string.spaces_room_permission_denied), StatusTone.INFO,
                actionLabel = stringResource(R.string.settings), onAction = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                })
        }
    }
    remove?.let { participant ->
        val name = registeredIdentity(participant.key).name
        AlertDialog(onDismissRequest = { remove = null }, title = { Text(stringResource(R.string.spaces_room_remove_title, name)) },
            text = { Text(stringResource(R.string.spaces_room_remove_description)) },
            confirmButton = { TextButton(onClick = { remove = null; moderate(participant, true) }) { Text(stringResource(R.string.spaces_room_remove)) } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun RoomCaptureControls(token: String, microphone: Boolean, camera: Boolean, maySpeak: Boolean, permission: RoomPermission) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = maySpeak, onClick = {
            if (microphone) TalkRuntime.controls(token, microphone = false)
            else permission.request(Manifest.permission.RECORD_AUDIO) { TalkRuntime.controls(token, microphone = true) }
        }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(painterResource(if (microphone) R.drawable.ic_microphone else R.drawable.ic_microphone_off), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (microphone) R.string.talk_mute else R.string.talk_unmute))
        }
        OutlinedButton(onClick = {
            if (camera) TalkRuntime.controls(token, camera = false)
            else permission.request(Manifest.permission.CAMERA) { TalkRuntime.controls(token, camera = true) }
        }, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(painterResource(if (camera) R.drawable.ic_video else R.drawable.ic_video_off), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (camera) R.string.talk_camera_off else R.string.talk_camera_on))
        }
    }
}

@Composable private fun RoomParticipant(wallet: String, name: String, video: VideoTrack?, microphone: Boolean, camera: Boolean,
    mirror: Boolean = false, showConnecting: Boolean = false, actions: (@Composable () -> Unit)? = null) {
    val shape = RoundedCornerShape(16.dp)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column {
            if (camera && video != null) Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(shape)) { key(video) { TalkVideo(video, mirror) } }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileAvatar(wallet, Modifier.size(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    if (showConnecting) Text(stringResource(R.string.talk_connecting_peer), style = MaterialTheme.typography.bodySmall)
                }
                if (!microphone && actions == null) Icon(painterResource(R.drawable.ic_microphone_off),
                    stringResource(R.string.talk_muted), Modifier.size(18.dp))
                actions?.invoke()
            }
        }
    }
}

private fun roomStatus(status: SpaceVoiceRoom.Status): Int = when (status) {
    SpaceVoiceRoom.Status.AVAILABLE -> 0
    SpaceVoiceRoom.Status.FULL -> R.string.spaces_room_full
    SpaceVoiceRoom.Status.ARCHIVED -> R.string.spaces_room_archived
    SpaceVoiceRoom.Status.MEMBERS_ONLY -> R.string.spaces_room_members_only
    SpaceVoiceRoom.Status.SESSION_ENDED -> R.string.spaces_room_ended
    SpaceVoiceRoom.Status.ALREADY_JOINED -> R.string.spaces_room_other_device
}

internal class RoomPermission(val denied: Boolean, val request: (String, () -> Unit) -> Unit)

@Composable internal fun rememberRoomPermission(): RoomPermission {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        pending = null
        denied = !granted
        if (granted && action != null) scope.launch {
            val resumed = withTimeoutOrNull(5000) { lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) } }
            if (resumed != null) action() else denied = true
        }
    }
    return RoomPermission(denied) { permission, action ->
        if (pending == null) {
            denied = false
            if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) action()
            else { pending = action; launcher.launch(permission) }
        }
    }
}
