package nl.davidengelen.redsquare.fileshare

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import nl.davidengelen.redsquare.MainActivity
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.BuildConfig

/** User-started live data transfer. Android may still stop it on force-stop or its dataSync timeout. */
class FileTransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wake: PowerManager.WakeLock
    private var lastToken: String? = null
    private var lastProgress = -1
    private var lastPhase: FilePhase? = null
    private var stoppingNormally = false
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.fileshare_channel), NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION_ID, Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_file).setContentTitle(getString(R.string.fileshare_connecting))
            .setOngoing(true).setProgress(0, 0, true).build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:fileshare")
            .apply { setReferenceCounted(false); acquire(10 * 60_000L) }
        scope.launch {
            while (isActive) {
                delay(60_000)
                if (FileshareRuntime.transfers.value.any { it.phase.active }) wake.acquire(10 * 60_000L)
            }
        }
        scope.launch {
            FileshareRuntime.transfers.collectLatest { transfers ->
                val value = transfers.firstOrNull { it.phase.active }
                if (value == null) {
                    // Allow the user action to publish its session, and the final receipt to drain.
                    delay(2000)
                    val last = transfers.firstOrNull { it.token == lastToken }
                    if (last != null) notifications.notify(NOTIFICATION_ID, notification(last))
                    stoppingNormally = true
                    stopForeground(if (last == null) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
                    stopSelf()
                } else {
                    val progress = (100L * value.bytes / value.offer.size).toInt()
                    if (value.token != lastToken || value.phase != lastPhase || progress != lastProgress) {
                        lastToken = value.token; lastPhase = value.phase; lastProgress = progress
                        notifications.notify(NOTIFICATION_ID, notification(value))
                    }
                }
            }
        }
    }

    private fun notification(value: FileTransfer): Notification {
        val destination = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setData(FileTransferReference(value.owner, value.backend, value.contact, value.token).uri())
        if (BuildConfig.DEBUG && android.os.Build.HARDWARE in listOf("ranchu", "goldfish") && value.endpoint != value.backend.peerUrl)
            destination.putExtra(if (value.backend == nl.davidengelen.redsquare.saito.SaitoBackend.OWNER) "feedPeerUrl" else "feedOfficialPeerUrl", value.endpoint)
        val open = PendingIntent.getActivity(this, value.token.hashCode(), destination,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancelIntent = Intent(this, FileTransferService::class.java).setAction(CANCEL)
            .setData(Uri.Builder().scheme("fileshare-cancel").authority(packageName).appendPath(value.token).build())
        val cancel = PendingIntent.getService(this, value.token.hashCode(), cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_file)
            .setContentTitle(getString(filePhaseLabel(value.phase, value.incoming)))
            .setContentText(getString(R.string.fileshare_notification_detail)) // Keep filenames out of private notifications.
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(value.phase.active).setAutoCancel(!value.phase.active)
            .apply {
                if (value.phase.active) {
                    setProgress(100, (100L * value.bytes / value.offer.size).toInt(), value.phase in setOf(FilePhase.CONNECTING, FilePhase.WAITING))
                    addAction(Notification.Action.Builder(null, getString(R.string.fileshare_cancel), cancel).build())
                }
            }.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL && intent.data?.scheme == "fileshare-cancel" && intent.data?.authority == packageName)
            intent.data?.lastPathSegment?.let(FileshareRuntime::cancel)
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        FileshareRuntime.stopAll(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() {
        if (!stoppingNormally) FileshareRuntime.stopAll()
        scope.cancel()
        if (::wake.isInitialized && wake.isHeld) wake.release()
        super.onDestroy()
    }
    companion object {
        const val CHANNEL = "fileshare.transfers"
        const val NOTIFICATION_ID = 9103
        private const val CANCEL = "nl.davidengelen.redsquare.fileshare.CANCEL"
    }
}

internal fun filePhaseLabel(phase: FilePhase, incoming: Boolean) = when (phase) {
    FilePhase.CONNECTING -> R.string.fileshare_connecting
    FilePhase.WAITING -> R.string.fileshare_waiting
    FilePhase.OFFERED -> R.string.fileshare_offered
    FilePhase.SENDING -> R.string.fileshare_sending
    FilePhase.RECEIVING -> R.string.fileshare_receiving
    FilePhase.COMPLETE -> if (incoming) R.string.fileshare_received else R.string.fileshare_sent
    FilePhase.SAVED -> R.string.fileshare_saved
    FilePhase.DECLINED -> R.string.fileshare_declined
    FilePhase.CANCELLED -> R.string.fileshare_cancelled
    FilePhase.INTERRUPTED -> R.string.fileshare_interrupted
    FilePhase.FAILED -> R.string.fileshare_failed
}
