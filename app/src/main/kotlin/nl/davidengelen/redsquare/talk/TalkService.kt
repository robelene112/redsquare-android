package nl.davidengelen.redsquare.talk

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import nl.davidengelen.redsquare.MainActivity
import nl.davidengelen.redsquare.R

/** User-started microphone/camera foreground ownership. No sticky restart or killed-app ringing promise. */
class TalkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var token: String? = null
    private var normal = false
    private var focus: AudioFocusRequest? = null
    private var oldMode = AudioManager.MODE_NORMAL
    private var oldSpeaker = false
    private var wake: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        channel(this)
    }
    private var ownership: Job? = null
    private var audioOwned = false
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == END) {
            val call = TalkRuntime.call.value
            if (call != null && intent.data?.scheme == "talk-end" && intent.data?.authority == packageName &&
                intent.data?.lastPathSegment == call.token && intent.getStringExtra("owner") == call.owner &&
                intent.getStringExtra("backend") == call.savedBackend.id) TalkRuntime.end(call.token)
            if (token == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        val call = TalkRuntime.call.value
        if (call == null || !call.phase.started || intent?.getStringExtra("token") != call.token) {
            stopSelf(startId); return START_NOT_STICKY
        }
        if (token == call.token) return START_NOT_STICKY
        releaseOwnership()
        token = call.token; normal = false
        val ownedToken = call.token
        try {
            val alert = notification(call)
            if (Build.VERSION.SDK_INT >= 30) startForeground(ID, alert, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                if (call.camera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0)
            else startForeground(ID, alert)
            val manager = getSystemService(AudioManager::class.java)
            oldMode = manager.mode; oldSpeaker = manager.isSpeakerphoneOn; audioOwned = true
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { change ->
                    if (change in setOf(AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
                        TalkRuntime.interrupt(ownedToken)
                }.build()
            check(manager.requestAudioFocus(requireNotNull(focus)) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            manager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none { it.type in setOf(
                    android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO, android.media.AudioDeviceInfo.TYPE_USB_HEADSET) })
                manager.isSpeakerphoneOn = call.camera
            wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:talk")
                .apply { setReferenceCounted(false); acquire(10 * 60_000L) }
            ready[ownedToken]?.complete(Unit)
            ownership = scope.launch {
                launch {
                    while (isActive) {
                        delay(60_000)
                        if (token == ownedToken && TalkRuntime.call.value?.token == ownedToken &&
                            TalkRuntime.call.value?.phase?.started == true) wake?.acquire(10 * 60_000L)
                    }
                }
                TalkRuntime.call.collectLatest { updated ->
                    // A newer call can be claimed before its start command arrives.
                    // An older observer must never stop that newer service ownership.
                    if (token != ownedToken || updated?.token != ownedToken) return@collectLatest
                    if (!updated.phase.started) {
                        normal = true; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId)
                    } else try {
                        val notification = notification(updated)
                        if (Build.VERSION.SDK_INT >= 30 && updated.camera && TalkRuntime.visible &&
                            getForegroundServiceType() and ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA == 0) {
                            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
                        } else getSystemService(NotificationManager::class.java).notify(ID, notification)
                    } catch (failure: Exception) {
                        TalkRuntime.record("call-service", failure)
                        TalkRuntime.interrupt(ownedToken); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId)
                    }
                }
            }
        } catch (failure: Exception) {
            TalkRuntime.record("call-audio", failure)
            ready[ownedToken]?.completeExceptionally(IllegalStateException("Call audio unavailable"))
            TalkRuntime.interrupt(ownedToken); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId)
        }
        return START_NOT_STICKY
    }
    private fun releaseOwnership() {
        ownership?.cancel(); ownership = null
        val manager = getSystemService(AudioManager::class.java)
        focus?.let { manager.abandonAudioFocusRequest(it) }; focus = null
        if (audioOwned) {
            if (Build.VERSION.SDK_INT >= 31) manager.clearCommunicationDevice()
            manager.isSpeakerphoneOn = oldSpeaker; manager.mode = oldMode; audioOwned = false
        }
        wake?.takeIf { it.isHeld }?.release(); wake = null
        token?.let { ready.remove(it)?.cancel() }
    }
    private fun notification(call: TalkCall): Notification {
        val returnToCall = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setData(Uri.Builder().scheme("redsquare-talk").authority("call").appendPath(call.token)
                .appendQueryParameter("owner", call.owner).appendQueryParameter("backend", call.savedBackend.id).build())
        val open = PendingIntent.getActivity(this, call.token.hashCode(), returnToCall,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val end = Intent(this, TalkService::class.java).setAction(END)
            .setData(Uri.Builder().scheme("talk-end").authority(packageName).appendPath(call.token).build())
            .putExtra("owner", call.owner).putExtra("backend", call.savedBackend.id)
        val hangup = PendingIntent.getService(this, call.token.hashCode(), end,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_phone)
            .setGroup(CHANNEL).setContentTitle(getString(R.string.talk_title))
            .setContentText(getString(if (call.phase == CallPhase.ACTIVE) R.string.talk_in_call else R.string.talk_connecting))
            .setCategory(Notification.CATEGORY_CALL).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.talk_end), hangup).build()).build()
    }
    override fun onDestroy() {
        if (!normal) token?.let { TalkRuntime.interrupt(it) }
        releaseOwnership(); token = null; scope.cancel()
        super.onDestroy()
    }
    companion object {
        const val CHANNEL = "talk.calls"
        const val ID = 9104
        private const val END = "nl.davidengelen.redsquare.talk.END"
        private val ready = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<Unit>>()
        fun available(context: android.content.Context): Boolean {
            channel(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager.areNotificationsEnabled() &&
                manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE &&
                (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED)
        }
        fun prepare(token: String) { ready[token] = CompletableDeferred() }
        suspend fun awaitReady(token: String) { withTimeout(8_000) { requireNotNull(ready[token]).await() } }
        fun channel(context: android.content.Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.talk_channel), NotificationManager.IMPORTANCE_LOW))
        }
    }
}
