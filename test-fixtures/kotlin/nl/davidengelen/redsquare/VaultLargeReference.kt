package nl.davidengelen.redsquare

import com.google.gson.JsonObject
import java.util.Base64

/** Builds the bounded payload; detached header, signature and digest come from independent pinned WASM. */
internal fun vaultLargeReference(base: JsonObject): JsonObject {
    val reference = base.getAsJsonObject("large")
    val bytes = ByteArray(reference.get("size").asInt) { ((it * 31 + 7) and 255).toByte() }
    val body = JsonObject().apply {
        addProperty("request", "vault add file")
        addProperty("access_script", reference.get("accessScript").asString)
        addProperty("access_hash", reference.get("accessHash").asString)
        add("data", JsonObject().apply {
            addProperty("name", reference.get("name").asString)
            addProperty("file", "data:${reference.get("mime").asString};base64," + Base64.getEncoder().encodeToString(bytes))
        })
    }
    val envelope = JsonObject().apply {
        addProperty("t", reference.get("detached").asString)
        addProperty("m", Base64.getEncoder().encodeToString(body.toString().toByteArray(Charsets.UTF_8)))
        addProperty("opt", reference.get("opt").asString)
    }
    return base.deepCopy().apply {
        add("key", reference.getAsJsonObject("key").deepCopy())
        add("file", JsonObject().apply {
            addProperty("id", reference.get("fileSignature").asString)
            addProperty("envelope", envelope.toString())
        })
    }
}
