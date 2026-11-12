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
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == END) {
            val call = TalkRuntime.call.value
            if (call != null && intent.data?.scheme == "talk-end" && intent.data?.authority == packageName &&
                intent.data?.lastPathSegment == call?.token && intent.getStringExtra("owner") == call.owner &&
                intent.getStringExtra("backend") == call.savedBackend.id) TalkRuntime.end(call.token)
            return START_NOT_STICKY
        }
        val call = TalkRuntime.call.value
        if (call == null || !call.phase.started || intent?.getStringExtra("token") != call.token) {
            stopSelf(startId); return START_NOT_STICKY
        }
        if (token == call.token) return START_NOT_STICKY
        token = call.token; normal = false
        val alert = notification(call)
        if (Build.VERSION.SDK_INT >= 30) startForeground(ID, alert, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
            if (call.camera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0)
        else startForeground(ID, alert)
        try {
            val manager = getSystemService(AudioManager::class.java)
            oldMode = manager.mode; oldSpeaker = manager.isSpeakerphoneOn
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { change ->
                    if (change in setOf(AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
                        TalkRuntime.interrupt(call.token)
                }.build()
            check(manager.requestAudioFocus(requireNotNull(focus)) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            manager.mode = AudioManager.MODE_IN_COMMUNICATION
            // Keep a connected headset/system route. Video defaults to speaker only without one.
            if (manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none { it.type in setOf(
                    android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO, android.media.AudioDeviceInfo.TYPE_USB_HEADSET) })
                manager.isSpeakerphoneOn = call.camera
            wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:talk")
                .apply { setReferenceCounted(false); acquire(10 * 60_000L) }
            ready[call.token]?.complete(Unit)
            scope.launch {
                while (isActive) {
                    delay(60_000); if (TalkRuntime.call.value?.token == token && TalkRuntime.call.value?.phase?.started == true)
                        wake?.acquire(10 * 60_000L)
                }
            }
            scope.launch {
                TalkRuntime.call.collectLatest { updated ->
                    if (updated == null || updated.token != token || !updated.phase.started) {
                        normal = true; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                    } else getSystemService(NotificationManager::class.java).notify(ID, notification(updated))
                }
            }
        } catch (failure: Exception) {
            TalkRuntime.record("call-audio", failure)
            ready[call.token]?.completeExceptionally(IllegalStateException("Call audio unavailable"))
            TalkRuntime.interrupt(call.token); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId)
        }
        return START_NOT_STICKY
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
        val manager = getSystemService(AudioManager::class.java)
        focus?.let { manager.abandonAudioFocusRequest(it) }
        if (token != null) {
            if (Build.VERSION.SDK_INT >= 31) manager.clearCommunicationDevice()
            manager.isSpeakerphoneOn = oldSpeaker; manager.mode = oldMode
        }
        wake?.takeIf { it.isHeld }?.release(); scope.cancel()
        token?.let { ready.remove(it)?.cancel() }
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
