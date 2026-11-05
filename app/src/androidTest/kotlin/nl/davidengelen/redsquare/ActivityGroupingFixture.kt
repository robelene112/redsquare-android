package nl.davidengelen.redsquare

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Public scalar2 actor signs actual independent events for the owned binary-peer fixture. */
internal fun signedActivityLike(reference: JsonObject, time: Long): JsonObject {
    val target = reference.getAsJsonObject("root").get("sig").asString
    val owner = reference.getAsJsonObject("incomingLike").get("field3").asString
    val actor = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    val tx = SaitoTransaction.create(actor, JsonObject().apply {
        addProperty("module", "RedSquare"); addProperty("request", "like tweet")
        add("data", JsonObject().apply { addProperty("signature", target) })
    }, time, listOf(owner))
    return JsonObject().apply {
        addProperty("tx", tx.envelope()); addProperty("sig", tx.id)
        addProperty("field1", "RedSquareLike"); addProperty("field2", actor.publicKey)
        addProperty("field3", owner); addProperty("field4", target); addProperty("created_at", time)
    }
}
