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
    @Volatile var activeLoan: JsonObject? = null
    @Volatile var reads = 0; private set
    @Volatile var checkouts = 0; private set
    private val source = reference.getAsJsonObject("rentalAccess") ?: androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        .open("native-store-rental-purchase-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("rentalAccess") }
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
                val candidate = listOfNotNull(source, activeLoan).distinctBy { it.get("loanHash").asString }
                    .single { it.get("loanHash").asString == body.get("access_hash").asString }
                check(body.get("access_script").asString == candidate.get("loanSource").asString)
                check(body.getAsJsonObject("data").get("file_id").asString == candidate.getAsJsonObject("file").get("id").asString)
                val permitted = candidate.getAsJsonArray("roles")[time.ordinal].asJsonObject.get("renter").asInt == 1
                if (!permitted) "{\"status\":\"err\",\"err\":\"access_denied_script_failed\"}"
                else JsonObject().apply {
                    addProperty("status", "success")
                    add("txs", JsonArray().apply { if (activated && (activeLoan ?: source).get("loanHash").asString == candidate.get("loanHash").asString) add(candidate.getAsJsonObject("file").get("envelope")) })
                }.toString()
            }
            else -> error("Unexpected rental request")
        }
    }
}
