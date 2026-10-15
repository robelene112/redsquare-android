package nl.davidengelen.redsquare.chat

import android.content.Context
import kotlinx.coroutines.CancellationException
import nl.davidengelen.redsquare.saito.*

/** UI, foreground recovery and WorkManager share the same signed group attempt. */
internal suspend fun submitGroupMessage(context:Context,wallet:SaitoWallet,backend:SaitoBackend,
    endpoint:String,group:String,candidate:SaitoTransaction) {
    DeliveryAttempts.run("chat:${backend.id}:${candidate.id}") {
        val store=GroupChatStore(context,wallet,backend)
        val previous=requireNotNull(store.load(group))
        check(previous.pendingMessage?.id==candidate.id)
        if(previous.automaticMessage&&previous.queuedMessage==null&&
            archivedChatOriginal(wallet,endpoint,candidate,group){store.assertSelected()}) {
            store.confirmAvailable(group,candidate.id)
            return@run
        }
        submitChatTransaction(wallet,endpoint,wallet.publicKey,candidate,ChatSubmissionCallbacks(
            claim={store.claim(group,candidate.id)},assertSelected={store.assertSelected()},
            submitted={store.record(group,candidate.id,GroupChatStore.Status.SUBMITTED)},
            forwarded={store.record(group,candidate.id,GroupChatStore.Status.FORWARDED)},
            relayRecipients={store.relayRecipients(group,candidate.id)}))
    }
}

internal suspend fun resumeQueuedGroupMessages(context:Context,wallet:SaitoWallet,backend:SaitoBackend,endpoint:String,onlyId:String?=null) {
    val store=GroupChatStore(context,wallet,backend)
    for(entry in store.entries()) {
        val candidate=entry.pendingMessage ?: continue
        if(!entry.automaticMessage||(onlyId!=null&&candidate.id!=onlyId))continue
        try { submitGroupMessage(context,wallet,backend,endpoint,entry.group.id,candidate) }
        catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){/* Retain the original, including when current membership prohibits sending. */}
    }
}
