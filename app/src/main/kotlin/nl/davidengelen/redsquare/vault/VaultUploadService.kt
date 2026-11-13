package nl.davidengelen.redsquare.vault

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.R

/** Explicit user-started work, never resumed automatically after process loss. */
class VaultUploadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wake: PowerManager.WakeLock? = null
    private var stopping = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.vault_upload_channel), NotificationManager.IMPORTANCE_LOW))
        val cancel = PendingIntent.getService(this, 107, Intent(this, VaultUploadService::class.java).setAction(CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_file)
            .setContentTitle(getString(R.string.vault_upload_sending)).setContentText(getString(R.string.vault_upload_notification))
            .setOngoing(true).setOnlyAlertOnce(true).setProgress(0, 0, true)
            .addAction(Notification.Action.Builder(null, getString(R.string.vault_stop_upload), cancel).build()).build()
        startForeground(9107, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:vault")
            .apply { setReferenceCounted(false); acquire(180_000) }
        scope.launch {
            delay(2000)
            while (VaultUploads.active().isNotEmpty()) delay(500)
            stopping = true; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) VaultUploads.active().forEach { it.cancelSending() }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        VaultUploads.active().forEach { it.cancelSending() }; stopping = true
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() {
        if (!stopping) VaultUploads.active().forEach { it.cancelSending() }
        scope.cancel(); wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "vault.uploads"
        private const val CANCEL = "nl.davidengelen.redsquare.vault.CANCEL"
    }
}
