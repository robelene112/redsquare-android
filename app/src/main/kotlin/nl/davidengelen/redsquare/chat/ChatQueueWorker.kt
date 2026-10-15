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
            if(id in ChatQueueScheduling.pendingIds(applicationContext,wallet,backend))
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
            enqueueOriginal(context,wallet,backend,endpoint,candidate.id)
        }
        val groups=GroupChatStore(context,wallet,backend)
        for(entry in groups.entries()) {
            val candidate=entry.pendingMessage ?: continue
            groups.enableAutomatic(entry.group.id,candidate.id)
            enqueueOriginal(context,wallet,backend,endpoint,candidate.id)
        }
    }

    fun pendingIds(context:Context,wallet:SaitoWallet,backend:SaitoBackend):List<String> =
        ChatWorkStore.pendingDelivery(context,wallet,backend).mapNotNull{it.load().transaction?.id}+
            GroupChatStore(context,wallet,backend).entries().mapNotNull{it.pendingMessage?.id}

    private fun enqueueOriginal(context:Context,wallet:SaitoWallet,backend:SaitoBackend,endpoint:String,id:String) {
        val request=OneTimeWorkRequestBuilder<ChatQueueWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("transaction" to id,"owner" to wallet.publicKey,
                "backend" to backend.id,"endpoint" to endpoint)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("chat:${backend.id}:${wallet.publicKey}:$id",ExistingWorkPolicy.KEEP,request)
    }
}
