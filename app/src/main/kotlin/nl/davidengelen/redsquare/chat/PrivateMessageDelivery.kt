package nl.davidengelen.redsquare.chat

import android.content.Context
import kotlinx.coroutines.CancellationException
import nl.davidengelen.redsquare.saito.*

/** Foreground and resumed messages share guarded originals and durable progress. */
internal suspend fun submitPrivateMessage(context:Context,wallet:SaitoWallet,backend:SaitoBackend,
    endpoint:String,contact:String,candidate:SaitoTransaction,
    onProgress:suspend(ChatWorkStore.Saved,List<ChatHistoryStore.Entry>)->Unit={_,_->}) {
    DeliveryAttempts.run("chat:${backend.id}:${candidate.id}") {
        val work=ChatWorkStore(context,wallet,backend,contact)
        val history=ChatHistoryStore(context,wallet,backend,contact)
        submitChatTransaction(wallet,endpoint,contact,candidate,ChatSubmissionCallbacks(
            claim={work.claim(candidate.id)},assertSelected={work.assertSelected()},
            submitted={
                val saved=work.record(candidate.id,ChatWorkStore.Status.SUBMITTED)
                onProgress(saved,history.save(listOf(candidate.envelope())).entries)
            },
            forwarded={
                val saved=work.record(candidate.id,ChatWorkStore.Status.FORWARDED)
                onProgress(saved,history.load().entries)
            }))
    }
}

internal suspend fun resumeQueuedChats(context:Context,wallet:SaitoWallet,backend:SaitoBackend,endpoint:String,
    onlyId:String?=null) {
    for(work in ChatWorkStore.pendingDelivery(context,wallet,backend)) {
        try {
            val saved=work.load()
            if(!saved.automatic||saved.status==ChatWorkStore.Status.FORWARDED)continue
            val candidate=requireNotNull(saved.transaction)
            if(onlyId!=null&&candidate.id!=onlyId)continue
            submitPrivateMessage(context,wallet,backend,endpoint,work.contact,candidate)
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){/* Explicit signed intent remains durable and scoped. */}
    }
}
