package nl.davidengelen.redsquare

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*

/** Owned Vault adapter, anchored to independently executed actual rental builders and WASM role decisions. */
internal class OwnedStoreRentalFixture(private val reference: JsonObject) {
    enum class Time { BEFORE, AT, AFTER }
    @Volatile var time = Time.BEFORE
    @Volatile var available = true
    @Volatile var activated = false
    @Volatile var reads = 0; private set
    @Volatile var checkouts = 0; private set
    private val source = reference.getAsJsonObject("rentalAccess")
    fun reply(request: SignedSaitoMessage): String {
        check(request.author == reference.get("buyer").asString)
        reads++
        if (!available) return "{\"status\":\"err\",\"err\":\"rental_provider_unavailable\"}"
        val data = request.body.get("data")
        return when (request.body.get("request").asString) {
            "vault checkout rental" -> {
                val inner = SaitoEnvelope.decode(data.asString)
                check(inner.author == request.author && inner.body == source.getAsJsonObject("checkoutMessage"))
                checkouts++
                if (activated) "{\"status\":\"err\",\"err\":\"archive_update_denied\"}"
                else {
                    activated = true
                    JsonObject().apply { addProperty("status", "ok"); addProperty("file_id", source.getAsJsonObject("file").get("id").asString); addProperty("result", 1) }.toString()
                }
            }
            "vault access rental" -> {
                val body = data.asJsonObject
                check(body.get("access_script").asString == source.get("loanSource").asString && body.get("access_hash").asString == source.get("loanHash").asString)
                check(body.getAsJsonObject("data").get("file_id").asString == source.getAsJsonObject("file").get("id").asString)
                val permitted = source.getAsJsonArray("roles")[time.ordinal].asJsonObject.get("renter").asInt == 1
                if (!permitted) "{\"status\":\"err\",\"err\":\"access_denied_script_failed\"}"
                else JsonObject().apply {
                    addProperty("status", "success")
                    add("txs", JsonArray().apply { if (activated) add(source.getAsJsonObject("file").get("envelope")) })
                }.toString()
            }
            else -> error("Unexpected rental request")
        }
    }
}
