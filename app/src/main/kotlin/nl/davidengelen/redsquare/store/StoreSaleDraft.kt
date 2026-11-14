package nl.davidengelen.redsquare.store

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*

internal data class StoreSaleDraft(val title: String = "", val description: String = "", val price: String = "",
    val quantity: String = "1", val item: String = "", val creating: Boolean = false, val image: Boolean = false)
internal class StoreSaleDraftStore(context: Context, owner: String, backend: SaitoBackend) {
    private val file = EncryptedLocalFile(context, "store-sale-draft.$owner.${backend.id}", "redsquare-store-sale-drafts-v1", "store-sale-draft.")
    fun read(): StoreSaleDraft {
        val saved = file.load() ?: return StoreSaleDraft()
        require(saved.version == 1.toByte())
        val row = JsonParser.parseString(saved.text).asJsonObject
        require(row.keySet().containsAll(setOf("title", "description", "price", "quantity", "item", "creating")) && (row.keySet() - setOf("title", "description", "price", "quantity", "item", "creating")).all { it == "image" })
        return StoreSaleDraft(row.get("title").asString, row.get("description").asString, row.get("price").asString,
            row.get("quantity").asString, row.get("item").asString, row.get("creating").asBoolean, row.get("image")?.asBoolean ?: false).also(::validate)
    }
    fun save(draft: StoreSaleDraft) {
        validate(draft)
        file.save(EncryptedLocalFile.Contents(1, JsonObject().apply {
            addProperty("title", draft.title); addProperty("description", draft.description); addProperty("price", draft.price)
            addProperty("quantity", draft.quantity); addProperty("item", draft.item); addProperty("creating", draft.creating); addProperty("image", draft.image)
        }.toString()))
        check(read() == draft)
    }
    private fun validate(draft: StoreSaleDraft) {
        require(draft.title.length <= 10_000 && draft.description.length <= 200_000 && draft.price.length <= 80 && draft.quantity.length <= 20 && draft.item.length <= 300)
    }
}
