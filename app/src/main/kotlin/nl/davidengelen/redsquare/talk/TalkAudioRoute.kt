package nl.davidengelen.redsquare.talk

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.*

/** Choose among actual communication devices. Android owns device availability and routing. */
@Composable internal fun TalkAudioRouteButton() {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    var open by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val live by TalkRuntime.call.collectAsState()
    var pending by remember { mutableStateOf<Pair<String, Int>?>(null) }
    fun select(device: AudioDeviceInfo): Boolean = Build.VERSION.SDK_INT >= 31 &&
        runCatching { audio.setCommunicationDevice(device) }.getOrDefault(false)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val choice = pending; pending = null
        val call = TalkRuntime.call.value
        if (choice != null && call?.token == choice.first && call.phase.started && Build.VERSION.SDK_INT >= 31) {
            val device = runCatching { audio.availableCommunicationDevices.firstOrNull { it.id == choice.second } }.getOrNull()
            if (granted && device != null && select(device)) open = false else failed = true
            revision++
        }
    }
    LaunchedEffect(live?.token, live?.phase?.started) {
        if (live?.phase?.started != true) { open = false; pending = null }
        if (pending?.first != live?.token) pending = null
    }
    DisposableEffect(audio) {
        val callback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { revision++ }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { revision++ }
        }
        audio.registerAudioDeviceCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
        onDispose { audio.unregisterAudioDeviceCallback(callback) }
    }
    OutlinedButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
        failed = false
        open = true; revision++
    }) { Text(stringResource(R.string.talk_audio_output)) }
    DesignSheet(open, stringResource(R.string.talk_audio_output), { open = false }) { firstAction ->
        InlineStatus(if (failed) stringResource(R.string.talk_audio_output_failed) else null, StatusTone.INFO)
        if (Build.VERSION.SDK_INT >= 31) {
            val devices = remember(open, revision) { runCatching { audio.availableCommunicationDevices }.getOrDefault(emptyList()) }
            val selected = runCatching { audio.communicationDevice?.id }.getOrNull()
            devices.forEachIndexed { index, device ->
                val name = when (device.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> stringResource(R.string.talk_phone)
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> stringResource(R.string.talk_speaker)
                    else -> device.productName.toString()
                }
                AudioRouteChoice(label = name, selected = device.id == selected,
                    modifier = if (index == 0) firstAction else Modifier, onClick = {
                        val call = TalkRuntime.call.value
                        if (call?.phase?.started == true) {
                            val bluetooth = device.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                                AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
                            if (bluetooth && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                pending = call.token to device.id
                                permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            } else if (select(device)) { open = false; revision++ }
                            else failed = true
                        } else open = false
                    })
            }
            if (devices.isEmpty()) Text(stringResource(R.string.talk_system_output))
        } else {
            AudioRouteChoice(label = stringResource(R.string.talk_system_output), selected = !audio.isSpeakerphoneOn,
                modifier = firstAction, onClick = { audio.isSpeakerphoneOn = false; open = false })
            AudioRouteChoice(label = stringResource(R.string.talk_speaker), selected = audio.isSpeakerphoneOn,
                onClick = { audio.isSpeakerphoneOn = true; open = false })
        }
    }
}

@Composable private fun AudioRouteChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
    }
}
