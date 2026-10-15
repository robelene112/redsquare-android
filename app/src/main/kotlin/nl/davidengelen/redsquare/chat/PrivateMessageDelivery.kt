package nl.davidengelen.redsquare.chat

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import nl.davidengelen.redsquare.saito.*

/** Foreground and resumed messages share guarded originals and durable progress. */
internal suspend fun submitPrivateMessage(context:Context,wallet:SaitoWallet,backend:SaitoBackend,
    endpoint:String,contact:String,candidate:SaitoTransaction,
    onProgress:suspend(ChatWorkStore.Saved,List<ChatHistoryStore.Entry>)->Unit={_,_->}) {
    DeliveryAttempts.run("chat:${backend.id}:${candidate.id}") {
        val work=ChatWorkStore(context,wallet,backend,contact)
        val history=ChatHistoryStore(context,wallet,backend,contact)
        val previous=work.load()
        check(previous.transaction?.id==candidate.id)
        if(previous.automatic&&previous.status!=ChatWorkStore.Status.QUEUED) {
            val found=SaitoPeer(wallet,endpoint).use{peer->
                require("archive" in peer.services())
                val request=JsonObject().apply {
                    addProperty("request","archive")
                    add("data",JsonObject().apply {
                        addProperty("request","load");addProperty("sig",candidate.id)
                        addProperty("field3",PrivateChat.conversationId(wallet.publicKey,contact));addProperty("limit",1)
                    })
                }
                val rows=JsonParser.parseString(peer.request(request.toString().toByteArray(Charsets.UTF_8)){work.assertSelected()}
                    .toString(Charsets.UTF_8)).asJsonArray
                require(rows.size()<=1)
                if(rows.isEmpty())false else {
                    val value=rows.single().asJsonObject.get("tx")
                    require(value.isJsonPrimitive&&value.asJsonPrimitive.isString)
                    require(SaitoEnvelope.decodePayload(value.asString)==SaitoEnvelope.decodePayload(candidate.envelope()))
                    true
                }
            }
            if(found) {
                val retained=history.save(listOf(candidate.envelope())).entries
                onProgress(work.confirmAvailable(candidate.id),retained)
                return@run
            }
        }
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
            if(!saved.automatic||saved.deliveryComplete)continue
            val candidate=requireNotNull(saved.transaction)
            if(onlyId!=null&&candidate.id!=onlyId)continue
            submitPrivateMessage(context,wallet,backend,endpoint,work.contact,candidate)
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){/* Explicit signed intent remains durable and scoped. */}
    }
}
