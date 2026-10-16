package nl.davidengelen.redsquare.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Durable receipt boundary. Only public routing references enter WorkManager. */
internal object SocialPushScheduling {
    suspend fun enqueue(context: Context, payload: String, endpoint: String? = null): UUID = withContext(Dispatchers.IO) {
        val reference = SocialPushReference.parse(payload)
        val request = OneTimeWorkRequestBuilder<SocialPushWorker>()
            .setInputData(workDataOf("reference" to payload, "endpoint" to (endpoint ?: reference.backend.peerUrl),
                "receivedAt" to System.currentTimeMillis()))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "social:${reference.backend.id}:${reference.owner}:${reference.signature}", ExistingWorkPolicy.KEEP, request)
            .result.get(5, TimeUnit.SECONDS)
        request.id
    }
}

/** Tracer: scheduled receipt exists; actual verification/publication follows its native RED. */
class SocialPushWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = Result.success()
}
