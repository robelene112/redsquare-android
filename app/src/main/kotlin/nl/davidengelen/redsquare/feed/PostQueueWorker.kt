package nl.davidengelen.redsquare.feed

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.SaitoBackend

/** Inputs identify public work; plaintext posts and wallet secrets stay out of the scheduler. */
class PostQueueWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val selected=nl.davidengelen.redsquare.QueuedDeliveryContext.selected(applicationContext,inputData)
                ?:return@withContext Result.success()
            val wallet=selected.wallet;val backend=selected.backend
            val endpoint=selected.endpoint;val id=selected.transactionId
            resumeQueuedPosts(applicationContext, wallet, backend, endpoint, id)
            if (PostDraftStore.pendingDelivery(applicationContext, wallet.publicKey, backend).any { it.load().transaction?.id == id })
                Result.retry() else Result.success()
        } catch (_: nl.davidengelen.redsquare.InvalidQueuedDelivery) { Result.failure() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}

internal object PostQueueScheduling {
    fun enqueue(context: Context, owner: String, backend: SaitoBackend, endpoint: String) {
        for (store in PostDraftStore.pendingDelivery(context, owner, backend)) {
            val saved = store.load()
            if (saved.available || (!saved.queued && !saved.automatic)) continue
            if (!saved.automatic && !store.update(saved, saved.copy(automatic = true))) continue
            val candidate = requireNotNull(saved.transaction)
            val request = OneTimeWorkRequestBuilder<PostQueueWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf("transaction" to candidate.id, "owner" to owner,
                    "backend" to backend.id, "endpoint" to endpoint))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("post:${candidate.id}", ExistingWorkPolicy.KEEP, request)
        }
    }
}
