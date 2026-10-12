package nl.davidengelen.redsquare.feed

import android.content.Context
import nl.davidengelen.redsquare.saito.*

/** Bounded public activity, independently verified again when read; no private Chat or wallet data. */
internal class NotificationHistoryStore(context: Context, backend: SaitoBackend, owner: String) {
    init { require(SaitoWallet.isPublicKey(owner)) }
    private val storage = PublicStringStore(context, "notifications.${backend.id}.$owner.v1", MAX_EVENTS)
    data class Saved(val envelopes: List<String>, val unreadable: Boolean)

    fun load(): Saved {
        val loaded = storage.load()
        var unreadable = loaded.unreadable
        val entries = loaded.values.mapNotNull { envelope ->
            try { verify(envelope); envelope } catch (_: Exception) { unreadable = true; null }
        }
        return Saved(entries, unreadable)
    }

    fun save(envelopes: List<String>) {
        storage.update { previous ->
            check(!previous.unreadable) { "Unreadable notification history" }
            (envelopes + previous.values).map { it to verify(it) }.distinctBy { it.second.id }
                .sortedByDescending { it.second.createdAt }.map { it.first }
        }
    }

    companion object { const val MAX_EVENTS = 300 }

    private fun verify(envelope: String): SignedSaitoMessage {
        val signed = SaitoEnvelope.decode(envelope)
        require(signed.body.get("module").asString == "RedSquare")
        when (signed.body.get("request").asString) {
            "like tweet" -> RedSquareLikes.decode(envelope)
            "create tweet" -> RedSquareFeed.decode(envelope)
            "retweet" -> {
                val row = com.google.gson.JsonObject().apply { addProperty("tx", envelope) }
                require(RedSquareActions.readIncomingReposts("[$row]", signed.author).ignored == 0)
                // Owner exclusion belongs to the screen; use the valid signed actor for validation here.
                require(RedSquareFeed.readIncomingQuotes("[$row]", signed.author).ignored == 0)
            }
            else -> throw IllegalArgumentException("Unsupported notification activity")
        }
        return signed
    }
}
