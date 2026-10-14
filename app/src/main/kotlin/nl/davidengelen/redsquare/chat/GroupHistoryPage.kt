package nl.davidengelen.redsquare.chat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import nl.davidengelen.redsquare.saito.*

internal data class GroupHistoryPage(val incomplete:Boolean,val hasOlder:Boolean,val cursor:Long?)

/** Authentication establishes page position separately from membership/operation admission. */
internal suspend fun loadGroupHistoryPage(peer:SaitoPeer,store:GroupChatStore,id:String,before:Long):GroupHistoryPage {
    val criteria=JsonObject().apply{
        addProperty("request","load");addProperty("field3",id)
        addProperty("limit",50);addProperty("created_earlier_than",before)
    }
    val request=JsonObject().apply{addProperty("request","archive");add("data",criteria)}
    val rows=JsonParser.parseString(peer.request(request.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)).asJsonArray
    require(rows.size()<=50)
    var rejected=false
    var cursor:Long?=null
    val available=mutableListOf<String>()
    for(row in rows){
        currentCoroutineContext().ensureActive()
        try {
            val value=row.asJsonObject.get("tx")
            require(value.isJsonPrimitive&&value.asJsonPrimitive.isString)
            val signed=SaitoEnvelope.decode(value.asString)
            require(signed.body.get("module")==com.google.gson.JsonPrimitive("Chat") &&
                signed.body.get("group_id")==com.google.gson.JsonPrimitive(id))
            val operation=signed.body.get("request")
            require(operation!=null && operation.isJsonPrimitive && operation.asJsonPrimitive.isString &&
                operation.asString in setOf("chat message","chat join","chat update","chat remove"))
            require(id in signed.recipients && signed.recipients.all(SaitoWallet::isPublicKey))
            require(signed.createdAt<before)
            cursor=cursor?.let{minOf(it,signed.createdAt)} ?: signed.createdAt
            // A correctly addressed signed row can be inadmissible under current membership.
            // Keep its authenticated position so that it cannot hide earlier available records.
            available.add(value.asString)
        } catch(_:Exception){rejected=true}
    }
    val admissionFailed=store.receiveAvailable(id,available)
    return GroupHistoryPage(admissionFailed||rejected||(rows.size()==50&&cursor==null),rows.size()==50&&cursor!=null,cursor)
}
