package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import org.junit.Assert.assertEquals
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** A compatible external node counts distinct actual signed frames, including repeat authors. */
internal class PostLikePeer {
    private val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
    val postAuthor: String get() = root.get("field2").asString
    private val indexed = ConcurrentHashMap<String, JsonObject>()
    private fun countedRoot(): JsonObject = root.deepCopy().apply {
            val envelope = JsonParser.parseString(get("tx").asString).asJsonObject
            val count = 4 + indexed.size
            envelope.addProperty("opt", Base64.getEncoder().encodeToString(
                "{\"num_likes\":$count,\"num_retweets\":2}".toByteArray()))
            addProperty("tx", envelope.toString())
        }
    val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val tx = postingTransaction(frame)
            val action = SaitoEnvelope.decode(tx.envelope())
            assertEquals("like tweet", action.body.get("request").asString)
            assertEquals(setOf("signature"), action.body.getAsJsonObject("data").keySet())
            assertEquals(root.get("sig").asString, action.body.getAsJsonObject("data").get("signature").asString)
            indexed.putIfAbsent(action.id, JsonObject().apply {
                addProperty("sig", action.id); addProperty("field1", "RedSquareLike")
                addProperty("field2", action.author); addProperty("field4", root.get("sig").asString)
                addProperty("created_at", action.createdAt); addProperty("tx", tx.envelope())
            })
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareLike")
                indexed[criteria.get("sig").asString]?.let { "[$it]" } ?: "[]"
            else "[${countedRoot()}]"
        })
}
