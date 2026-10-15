package nl.davidengelen.redsquare.chat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*

/** An exact authenticated original can establish server availability, never recipient delivery. */
internal suspend fun archivedChatOriginal(wallet:SaitoWallet,endpoint:String,candidate:SaitoTransaction,conversation:String,
    assertSelected:()->Unit):Boolean = SaitoPeer(wallet,endpoint).use { peer ->
    require("archive" in peer.services())
    val request=JsonObject().apply {
        addProperty("request","archive")
        add("data",JsonObject().apply {
            addProperty("request","load");addProperty("sig",candidate.id)
            addProperty("field3",conversation);addProperty("limit",1)
        })
    }
    val rows=JsonParser.parseString(peer.request(request.toString().toByteArray(Charsets.UTF_8),assertSelected)
        .toString(Charsets.UTF_8)).asJsonArray
    require(rows.size()<=1)
    if(rows.isEmpty())false else {
        val value=rows.single().asJsonObject.get("tx")
        require(value.isJsonPrimitive&&value.asJsonPrimitive.isString)
        require(SaitoEnvelope.decodePayload(value.asString)==SaitoEnvelope.decodePayload(candidate.envelope()))
        true
    }
}
