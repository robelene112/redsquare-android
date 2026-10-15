package nl.davidengelen.redsquare.chat

import android.content.Context
import android.content.Intent
import androidx.work.*
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.feed.FeedEndpoints
import nl.davidengelen.redsquare.saito.*

/** Scheduler inputs are public routing metadata, never message text or wallet keys. */
class ChatQueueWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        try {
            val backend=SaitoBackend.entries.firstOrNull{it.id==inputData.getString("backend")}
                ?:return@withContext Result.failure()
            val id=inputData.getString("transaction") ?:return@withContext Result.failure()
            val wallet=synchronized(SaitoLocalState.lock) {
                if(AndroidBackendStore(applicationContext).load()!=backend)return@withContext Result.success()
                AndroidWalletStore(applicationContext).loadExisting()
            }
            if(wallet.publicKey!=inputData.getString("owner"))return@withContext Result.success()
            val requested=requireNotNull(inputData.getString("endpoint"))
            val endpoint=if(!BuildConfig.DEBUG||requested==backend.peerUrl)backend.peerUrl else {
                val extra=if(backend==SaitoBackend.OWNER)"feedPeerUrl" else "feedOfficialPeerUrl"
                FeedEndpoints.fromIntent(Intent().putExtra(extra,requested),backend)
            }
            resumeQueuedChats(applicationContext,wallet,backend,endpoint,id)
            if(ChatWorkStore.pendingDelivery(applicationContext,wallet,backend).any{it.load().let{saved->
                saved.automatic&&saved.status!=ChatWorkStore.Status.FORWARDED&&saved.transaction?.id==id}})
                Result.retry() else Result.success()
        }catch(cancelled:CancellationException){throw cancelled}
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
