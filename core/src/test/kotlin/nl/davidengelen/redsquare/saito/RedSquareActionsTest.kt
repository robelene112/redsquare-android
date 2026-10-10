package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class RedSquareActionsTest {
    private fun reference() = JsonParser.parseReader(javaClass.getResourceAsStream("/native-social-reference.json")!!.reader()).asJsonObject

    @Test fun `native social actions match independent WASM signed bytes and targets`() {
        val fixture = reference()
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe test key only.
        for ((name, kind) in listOf("like" to RedSquareActionKind.LIKE, "repost" to RedSquareActionKind.REPOST)) {
            val vector = fixture.getAsJsonObject(name)
            val candidate = SaitoTransaction.create(wallet, vector.getAsJsonObject("body"), vector.get("timestamp").asLong,
                vector.getAsJsonArray("recipients").map { it.asString })
            assertEquals(vector.get("networkBase64").asString, candidate.encoded())
            assertEquals(vector.get("sig").asString, candidate.id)
            val action = RedSquareActions.decode(vector.get("tx").asString)
            assertEquals(kind, action.kind)
            assertEquals(fixture.get("target").asString, action.target)
            assertEquals(vector.get("field2").asString, action.author)
        }
    }

    @Test fun `altered social messages and duplicate rows cannot invent verified actions`() {
        val fixture = reference()
        val like = fixture.getAsJsonObject("like")
        val repost = fixture.getAsJsonObject("repost")
        val altered = like.deepCopy()
        val outer = JsonParser.parseString(altered.get("tx").asString).asJsonObject
        outer.addProperty("m", Base64.getEncoder().encodeToString("{\"module\":\"RedSquare\",\"request\":\"retweet\"}".toByteArray()))
        altered.addProperty("tx", outer.toString())
        val actions = RedSquareActions.readArchive("[$altered,$repost,$like,$like]", like.get("field2").asString, RedSquareActionKind.LIKE)
        assertEquals(listOf(like.get("sig").asString), actions.map { it.id })
    }
}
