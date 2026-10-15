package nl.davidengelen.redsquare.chat

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.saito.*

/** Scheduler inputs are public routing metadata, never message text or wallet keys. */
class ChatQueueWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        try {
            val selected=nl.davidengelen.redsquare.QueuedDeliveryContext.selected(applicationContext,inputData)
                ?:return@withContext Result.success()
            val wallet=selected.wallet;val backend=selected.backend
            val endpoint=selected.endpoint;val id=selected.transactionId
            resumeQueuedChats(applicationContext,wallet,backend,endpoint,id)
            if(ChatWorkStore.pendingDelivery(applicationContext,wallet,backend).any{it.load().let{saved->
                saved.automatic&&saved.status!=ChatWorkStore.Status.FORWARDED&&saved.transaction?.id==id}})
                Result.retry() else Result.success()
        }catch(_:nl.davidengelen.redsquare.InvalidQueuedDelivery){Result.failure()}
        catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){Result.retry()}
    }
}

internal object ChatQueueScheduling {
    fun enqueue(context:Context,wallet:SaitoWallet,backend:SaitoBackend,endpoint:String) {
        for(work in ChatWorkStore.pendingDelivery(context,wallet,backend)) {
            val saved=work.load()
            val candidate=requireNotNull(saved.transaction)
            if(saved.status!=ChatWorkStore.Status.QUEUED&&!saved.automatic)continue
            work.enableAutomatic(candidate.id)
            val request=OneTimeWorkRequestBuilder<ChatQueueWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf("transaction" to candidate.id,"owner" to wallet.publicKey,
                    "backend" to backend.id,"endpoint" to endpoint)).build()
            WorkManager.getInstance(context).enqueueUniqueWork("chat:${backend.id}:${wallet.publicKey}:${candidate.id}",ExistingWorkPolicy.KEEP,request)
        }
    }
}
