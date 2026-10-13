package nl.davidengelen.redsquare.chat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*

/** Each feature owns durable preparation, identity guards and publication of transport progress. */
internal data class ChatSubmissionCallbacks(val claim: () -> Unit, val assertSelected: () -> Unit,
    val submitted: suspend () -> Unit, val forwarded: suspend () -> Unit)

internal suspend fun submitChatTransaction(wallet: SaitoWallet, endpoint: String, contact: String,
    candidate: SaitoTransaction, callbacks: ChatSubmissionCallbacks) {
    SaitoPeer(wallet, endpoint).use { peer ->
        require("relay" in peer.services())
        peer.submit(candidate) { callbacks.claim() }
        callbacks.submitted()
        val relayBody = JsonObject().apply {
            addProperty("module", "Relay"); addProperty("request", "chat relay")
            add("data", candidate.signedJson())
        }
        val relay = SaitoTransaction.create(wallet, relayBody, System.currentTimeMillis(), listOf(contact))
            .signedJson().apply { addProperty("signature", "0".repeat(128)) }
        val request = JsonObject().apply { addProperty("request", "relay peer message"); add("data", relay) }
        val response = peer.request(request.toString().toByteArray(Charsets.UTF_8)) { callbacks.assertSelected() }
        val result = JsonParser.parseString(response.toString(Charsets.UTF_8)).asJsonObject
        val success = result.get("success")
        require(result.get("err")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isEmpty() } == true)
        require(success != null && success.isJsonPrimitive && success.asJsonPrimitive.isNumber && success.asString == "1")
        callbacks.forwarded()
        peer.finishBroadcast()
    }
}
