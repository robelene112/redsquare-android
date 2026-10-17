package nl.davidengelen.redsquare.notifications

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.tasks.Tasks
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoLocalState
import java.io.File
import java.util.concurrent.TimeUnit

private val registrationExecution = Mutex()

/** Shared installation transport. Chat must join its consent calculation before using this SDK. */
internal object PushRegistration {
    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun request(context: Context, observedRegistration: Boolean? = null) {
        if (!BuildConfig.PUSH_CONFIGURED) return
        try {
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniqueWork("push-registration", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<PushRegistrationWorker>().setConstraints(connected)
                    .setInputData(workDataOf("observed" to observedRegistration?.toString()))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build())
            manager.enqueueUniquePeriodicWork("push-registration-refresh", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PushRegistrationWorker>(24, TimeUnit.HOURS)
                    .setConstraints(connected).build())
        } catch (_: Exception) {
            // A later startup/resume/choice retries; never roll back consent or block the feed.
            Log.w("PushTransport", "Push registration scheduling unavailable")
        }
    }
}

/** Only this worker invokes register/unregister; callbacks request reconciliation without mutating state. */
class PushRegistrationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        registrationExecution.withLock {
            if (!BuildConfig.PUSH_CONFIGURED) return@withLock Result.success()
            if (!PushTransport.initialize(applicationContext)) return@withLock Result.retry()
            try {
                currentCoroutineContext().ensureActive()
                val store = RegistrationStore(applicationContext)
                val saved = store.load()
                val desired = desired()
                val messaging = FirebaseMessaging.getInstance()
                if (desired == null) {
                    if ((saved == null || saved.phase == Phase.UNREGISTERED) &&
                        inputData.getString("observed") != "true") return@withLock Result.success()
                    store.save(RegistrationRecord(Phase.UNREGISTERING))
                    Tasks.await(messaging.unregister(), 20, TimeUnit.SECONDS)
                    currentCoroutineContext().ensureActive()
                    store.save(RegistrationRecord(Phase.UNREGISTERED))
                } else {
                    val now = System.currentTimeMillis()
                    val current = Tasks.await(FirebaseInstallations.getInstance().id, 20, TimeUnit.SECONDS)
                    val fresh = saved?.phase == Phase.REGISTERED && saved.fid == current &&
                        now >= saved.registeredAt && now - saved.registeredAt < DAY &&
                        inputData.getString("observed") != "false"
                    if (!fresh) {
                        // An interrupted request must still be unregistered after a later opt-out.
                        store.save(RegistrationRecord(Phase.REGISTERING))
                        Tasks.await(messaging.register(), 20, TimeUnit.SECONDS)
                        currentCoroutineContext().ensureActive()
                        val fid = Tasks.await(FirebaseInstallations.getInstance().id, 20, TimeUnit.SECONDS)
                        currentCoroutineContext().ensureActive()
                        store.save(RegistrationRecord(Phase.REGISTERED, fid, System.currentTimeMillis()))
                    }
                }
                // Import, server/permission changes and opt-out can race an asynchronous SDK task.
                if (desired() == desired) Result.success() else Result.retry()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Result.retry() }
        }
    }

    private data class Demand(val owner: String, val backend: SaitoBackend)

    private fun desired(): Demand? {
        if (!applicationContext.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) return null
        return synchronized(SaitoLocalState.lock) {
            val wallet = AndroidWalletStore(applicationContext).loadExisting()
            val backend = AndroidBackendStore(applicationContext).load()
            if (SocialPushSettings(applicationContext, wallet.publicKey).enabled()) Demand(wallet.publicKey, backend) else null
        }
    }

    private companion object { const val DAY = 24 * 60 * 60 * 1000L }
}

private enum class Phase { REGISTERING, REGISTERED, UNREGISTERING, UNREGISTERED }
private data class RegistrationRecord(val phase: Phase, val fid: String = "", val registeredAt: Long = 0)

/** Installation state survives wallet replacement without retaining any wallet secret. */
private class RegistrationStore(context: Context) {
    private val pending = File(context.noBackupFilesDir, "push.registration.v1.new")
    private val file = EncryptedLocalFile(context, "push.registration.v1", "redsquare.push.registration", "push.registration.")

    fun load(): RegistrationRecord? = synchronized(SaitoLocalState.lock) {
        val contents = file.load() ?: run { check(!pending.exists()); return@synchronized null }
        require(contents.version == 1.toByte())
        val parts = contents.text.split('\n')
        require(parts.size == 3)
        val record = RegistrationRecord(Phase.valueOf(parts[0]), parts[1], parts[2].toLong())
        require(record.registeredAt >= 0)
        if (record.phase == Phase.REGISTERED) {
            require(record.fid.matches(Regex("[A-Za-z0-9_-]{1,256}")) && record.registeredAt > 0)
        } else require(record.fid.isEmpty() && record.registeredAt == 0L)
        record
    }

    fun save(record: RegistrationRecord) = synchronized(SaitoLocalState.lock) {
        load() // Preserve an unreadable existing record for recovery.
        file.save(EncryptedLocalFile.Contents(1, "${record.phase}\n${record.fid}\n${record.registeredAt}"))
        check(load() == record)
    }
}
