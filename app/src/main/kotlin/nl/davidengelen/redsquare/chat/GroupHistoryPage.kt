package nl.davidengelen.redsquare.chat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import nl.davidengelen.redsquare.saito.*

internal data class GroupHistoryPage(val incomplete:Boolean,val hasOlder:Boolean,val cursor:Long?)

/** Only a successfully authenticated, retained row can advance the network-page cursor. */
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
    for(row in rows){
        currentCoroutineContext().ensureActive()
        try {
            val value=row.asJsonObject.get("tx")
            require(value.isJsonPrimitive&&value.asJsonPrimitive.isString)
            val signed=SaitoEnvelope.decode(value.asString)
            require(signed.body.get("module").asString=="Chat"&&signed.body.get("group_id").asString==id)
            require(signed.createdAt<before)
            store.receive(value.asString)
            cursor=cursor?.let{minOf(it,signed.createdAt)} ?: signed.createdAt
        } catch(_:Exception){rejected=true}
    }
    return GroupHistoryPage(rejected||(rows.size()==50&&cursor==null),rows.size()==50&&cursor!=null,cursor)
}
