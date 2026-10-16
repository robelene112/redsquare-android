package nl.davidengelen.redsquare.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.TimeUnit

// A cancellation acknowledgement alone does not mean a running receipt has stopped.
private val receiptExecution = Mutex()

/** Durable receipt boundary. Only public routing references enter WorkManager. */
internal object SocialPushScheduling {
    suspend fun enqueue(context: Context, payload: String, endpoint: String? = null): UUID = withContext(Dispatchers.IO) {
        val reference = SocialPushReference.parse(payload)
        val request = OneTimeWorkRequestBuilder<SocialPushWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(workDataOf("reference" to payload, "endpoint" to (endpoint ?: reference.backend.peerUrl),
                "receivedAt" to System.currentTimeMillis()))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(reference), ExistingWorkPolicy.KEEP, request)
            .result.get(5, TimeUnit.SECONDS)
        request.id
    }

    /** Cancel this reference, then wait for any executing receipt to leave its effect boundary. */
    suspend fun cancelAndAwait(context: Context, payload: String) = withContext(Dispatchers.IO) {
        val name = workName(SocialPushReference.parse(payload))
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(name).result.get(5, TimeUnit.SECONDS)
        withTimeout(20_000) {
            receiptExecution.withLock {
                check(manager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS).all { it.state.isFinished })
            }
        }
    }

    private fun workName(reference: SocialPushReference) =
        "social:${reference.backend.id}:${reference.owner}:${reference.signature}"
}

/** Reuse the receipt verifier with fresh local choices each time durable work executes. */
class SocialPushWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        receiptExecution.withLock {
            currentCoroutineContext().ensureActive()
            if (isStopped) return@withLock Result.failure()
            val payload = inputData.getString("reference") ?: return@withLock Result.failure()
            val reference = try { SocialPushReference.parse(payload) }
            catch (_: IllegalArgumentException) { return@withLock Result.failure() }
            val receivedAt = inputData.getLong("receivedAt", -1)
            if (receivedAt < 0) return@withLock Result.failure()
            val now = System.currentTimeMillis()
            // A clock rollback conservatively retains the receipt; the signed event has its own age check.
            if (now >= receivedAt && now - receivedAt > RECEIPT_LIFETIME) return@withLock Result.success()
            val endpoint = inputData.getString("endpoint") ?: reference.backend.peerUrl
            when (SocialPush.receive(applicationContext, payload, endpoint, now)) {
                SocialPushResult.PUBLISHED, SocialPushResult.SUPPRESSED -> Result.success()
                SocialPushResult.RETRY -> Result.retry()
            }
        }
    }

    private companion object { const val RECEIPT_LIFETIME = 7 * 24 * 60 * 60 * 1000L }
}
