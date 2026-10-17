package nl.davidengelen.redsquare.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.TimeUnit

private val wakeExecution = Mutex()

/** The push transport supplies only an opaque wake ID; endpoints come from app configuration. */
internal object SocialPushWakeScheduling {
    suspend fun enqueue(context: Context, wake: String, resolverEndpoint: String,
        archiveEndpoint: String? = null): UUID = withContext(Dispatchers.IO) {
        require(wake.matches(Regex("[0-9a-f]{64}")))
        val manager = WorkManager.getInstance(context)
        val name = "social-wake:$wake"
        val request = OneTimeWorkRequestBuilder<SocialPushWakeWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(workDataOf("wake" to wake, "resolver" to resolverEndpoint,
                "archive" to archiveEndpoint, "receivedAt" to System.currentTimeMillis()))
            .build()
        manager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request).result.get(5, TimeUnit.SECONDS)
        manager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS).single().id
    }

    suspend fun cancelAndAwait(context: Context, wake: String) = withContext(Dispatchers.IO) {
        require(wake.matches(Regex("[0-9a-f]{64}")))
        val name = "social-wake:$wake"
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(name).result.get(5, TimeUnit.SECONDS)
        withTimeout(20_000) {
            wakeExecution.withLock {
                check(manager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS).all { it.state.isFinished })
            }
        }
    }
}

/** Tracer: processing the opaque gateway wake is the next missing behavior. */
class SocialPushWakeWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        wakeExecution.withLock { Result.success() }
    }
}
