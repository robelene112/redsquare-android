package nl.davidengelen.redsquare.chat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import nl.davidengelen.redsquare.saito.*

/** Imported IDs are hints. Only an available authenticated creation establishes authority. */
internal suspend fun loadGroupDiscoveryPage(peer:SaitoPeer,store:GroupChatStore,owner:String,
    hints:Set<String>,before:Long):GroupHistoryPage {
    val criteria=JsonObject().apply {
        addProperty("request","load");addProperty("field1","Chat");addProperty("field3",owner)
        addProperty("limit",50);addProperty("created_earlier_than",before)
    }
    val request=JsonObject().apply{addProperty("request","archive");add("data",criteria)}
    val rows=JsonParser.parseString(peer.request(request.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)).asJsonArray
    require(rows.size()<=50)
    var rejected=false
    var cursor:Long?=null
    for(row in rows){
        currentCoroutineContext().ensureActive()
        val original=try {
            val value=row.asJsonObject.get("tx")
            require(value.isJsonPrimitive&&value.asJsonPrimitive.isString)
            val signed=SaitoEnvelope.decode(value.asString)
            require(signed.body.get("module")==com.google.gson.JsonPrimitive("Chat") &&
                signed.recipients.firstOrNull()==owner && signed.createdAt<before)
            cursor=cursor?.let{minOf(it,signed.createdAt)} ?: signed.createdAt
            if(signed.body.get("request")!=com.google.gson.JsonPrimitive("chat group"))continue
            val group=GroupChat.readCreation(value.asString)
            if(group.id !in hints)continue
            value.asString
        }catch(_:Exception){rejected=true;continue}
        // Storage failure is a recovery error, never an inadmissible-row shortcut.
        store.receive(original)
    }
    return GroupHistoryPage(rejected||(rows.size()==50&&cursor==null),rows.size()==50&&cursor!=null,cursor)
}
