package nl.davidengelen.redsquare.fileshare

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
    private var lastStartId = 0
    private var lastCommandAt = android.os.SystemClock.elapsedRealtime()
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
                if (FileshareRuntime.transfers.value.any { it.phase.active } ||
                    FileshareRuntime.preparations.value.any { it.phase == PreparationPhase.PREPARING }) wake.acquire(10 * 60_000L)
            }
        }
        scope.launch {
            while (isActive) {
                delay(1000)
                if (!FileshareRuntime.hasLiveWork() && android.os.SystemClock.elapsedRealtime() - lastCommandAt >= 2000) {
                    stoppingNormally = true
                    // Detach before requesting service destruction. Stopping first lets Android
                    // cancel the still-foreground notification before the detach reaches it.
                    stopForeground(if (lastToken == null) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
                    if (stopSelfResult(lastStartId)) {
                        return@launch
                    }
                    stoppingNormally = false
                    val latest = FileshareRuntime.transfers.value.firstOrNull { it.token == lastToken }
                    val preparing = FileshareRuntime.preparations.value.firstOrNull { it.token == lastToken }
                    val alert = latest?.let(::notification) ?: preparing?.let(::notification)
                    if (alert != null) startForeground(NOTIFICATION_ID, alert, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                }
            }
        }
        scope.launch {
            combine(FileshareRuntime.transfers, FileshareRuntime.preparations) { transfers, preparing -> transfers to preparing }
                .collectLatest { (transfers, preparing) ->
                val value = transfers.firstOrNull { it.phase.active }
                val preparation = preparing.firstOrNull { it.phase == PreparationPhase.PREPARING }
                if (value == null && preparation == null) {
                    val last = transfers.firstOrNull { it.token == lastToken }
                    val lastPreparation = preparing.firstOrNull { it.token == lastToken }
                    if (last != null) notifications.notify(NOTIFICATION_ID, notification(last))
                    else if (lastPreparation != null) notifications.notify(NOTIFICATION_ID, notification(lastPreparation))
                } else if (value != null) {
                    val progress = (100L * value.bytes / value.offer.size).toInt()
                    if (value.token != lastToken || value.phase != lastPhase || progress != lastProgress) {
                        lastToken = value.token; lastPhase = value.phase; lastProgress = progress
                        notifications.notify(NOTIFICATION_ID, notification(value))
                    }
                } else if (preparation != null && preparation.token != lastToken) {
                    lastToken = preparation.token; lastPhase = null; lastProgress = -1
                    notifications.notify(NOTIFICATION_ID, notification(preparation))
                }
            }
        }
    }

    private fun notification(value: FileTransfer): Notification {
        return builder(FileTransferReference(value.owner, value.backend, value.contact, value.token), value.endpoint,
            getString(filePhaseLabel(value.phase, value.incoming)), value.phase.active).apply {
            if (value.phase.active) setProgress(100, (100L * value.bytes / value.offer.size).toInt(),
                value.phase in setOf(FilePhase.CONNECTING, FilePhase.WAITING))
        }.build()
    }
    private fun notification(value: FilePreparation): Notification = builder(
        FileTransferReference(value.owner, value.backend, value.contact, value.token), value.endpoint,
        getString(preparationLabel(value.phase)), value.phase == PreparationPhase.PREPARING)
        .apply { if (value.phase == PreparationPhase.PREPARING) setProgress(0, 0, true) }.build()

    private fun builder(reference: FileTransferReference, endpoint: String, title: String, active: Boolean): Notification.Builder {
        val destination = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setData(reference.uri())
        if (BuildConfig.DEBUG && android.os.Build.HARDWARE in listOf("ranchu", "goldfish") && endpoint != reference.backend.peerUrl)
            destination.putExtra(if (reference.backend == nl.davidengelen.redsquare.saito.SaitoBackend.OWNER) "feedPeerUrl" else "feedOfficialPeerUrl", endpoint)
        val open = PendingIntent.getActivity(this, reference.token.hashCode(), destination,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancelIntent = Intent(this, FileTransferService::class.java).setAction(CANCEL)
            .setData(Uri.Builder().scheme("fileshare-cancel").authority(packageName).appendPath(reference.token).build())
        val cancel = PendingIntent.getService(this, reference.token.hashCode(), cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_file)
            .setContentTitle(title)
            .setContentText(getString(R.string.fileshare_notification_detail)) // Keep filenames out of private notifications.
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(active).setAutoCancel(!active)
            .apply {
                if (active) {
                    addAction(Notification.Action.Builder(null, getString(R.string.fileshare_cancel), cancel).build())
                }
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId; lastCommandAt = android.os.SystemClock.elapsedRealtime()
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

internal fun preparationLabel(phase: PreparationPhase) = when (phase) {
    PreparationPhase.PREPARING -> R.string.fileshare_preparing
    PreparationPhase.CANCELLED -> R.string.fileshare_cancelled
    PreparationPhase.INTERRUPTED -> R.string.fileshare_interrupted
    PreparationPhase.FAILED -> R.string.fileshare_prepare_failed
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
