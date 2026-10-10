package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class RedSquareAuthorActionsTest {
    @Test fun `native edit and delete signatures match independently signed vectors`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-author-actions-reference.json")!!.reader()).asJsonObject
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture key only.
        for (name in listOf("edit", "deleted")) {
            val vector = fixture.getAsJsonObject(name)
            val signed = SaitoEnvelope.decode(vector.get("tx").asString)
            val candidate = SaitoTransaction.create(wallet, signed.body, signed.createdAt, signed.recipients)
            assertEquals(vector.get("sig").asString, candidate.id)
            assertEquals(JsonParser.parseString(vector.get("tx").asString), JsonParser.parseString(candidate.envelope()))
        }
    }
    @Test fun `independently signed authorized edit changes display without replacing original identity`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-author-actions-reference.json")!!.reader()).asJsonObject
        val original = fixture.getAsJsonObject("original")
        val post = RedSquareFeed.decode(fixture.getAsJsonObject("edited").get("tx").asString)
        assertEquals("Independently edited original text", post.text)
        assertEquals(original.get("sig").asString, post.id)
        assertEquals(original.get("field2").asString, post.author)
        assertEquals(1700000000000L, post.createdAt)
    }
    @Test fun `unrelated wrong target and damaged edits preserve the authenticated original`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-author-actions-reference.json")!!.reader()).asJsonObject
        for (name in listOf("unrelatedEdited", "wrongTargetEdited", "brokenEdited")) {
            val post = RedSquareFeed.decode(fixture.getAsJsonObject(name).get("tx").asString)
            assertEquals("Native Red Square reference post", post.text)
            assertEquals(null, post.edit)
        }
    }

    @Test fun `unsigned edit timestamp cannot change signed edit ordering`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-author-actions-reference.json")!!.reader()).asJsonObject
        val outer = JsonParser.parseString(fixture.getAsJsonObject("edited").get("tx").asString).asJsonObject
        val options = JsonParser.parseString(String(java.util.Base64.getDecoder().decode(outer.get("opt").asString))).asJsonObject
        options.addProperty("edit_ts", Long.MAX_VALUE)
        outer.addProperty("opt", java.util.Base64.getEncoder().encodeToString(options.toString().toByteArray()))
        assertEquals(1700000070000L, RedSquareFeed.decode(outer.toString()).edit!!.createdAt)
    }

    @Test fun `stale and unauthenticated updates cannot replace the newest verified edit`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-author-actions-reference.json")!!.reader()).asJsonObject
        val history = listOf("original", "newerEdited", "edited", "unrelatedEdited", "brokenEdited")
            .map { RedSquareFeed.decode(fixture.getAsJsonObject(it).get("tx").asString) }
        val retained = RedSquareFeed.newest(history).single()
        assertEquals("A newer verified edit", retained.text)
        assertEquals(fixture.getAsJsonObject("newer").get("sig").asString, retained.edit!!.id)
        assertEquals(fixture.getAsJsonObject("original").get("sig").asString, retained.id)
    }

    @Test fun `archived delete commands do not become unreadable posts or prove target removal`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-author-actions-reference.json")!!.reader()).asJsonObject
        val response = listOf("original", "deleted", "unrelatedDelete").joinToString(prefix = "[", postfix = "]") {
            fixture.getAsJsonObject(it).toString()
        }
        val page = RedSquareFeed.readArchive(response)
        assertEquals(0, page.ignored)
        assertEquals(listOf("Native Red Square reference post"), page.posts.map { it.text })
        assertEquals(setOf(fixture.getAsJsonObject("deleted").get("sig").asString,
            fixture.getAsJsonObject("unrelatedDelete").get("sig").asString), page.deletions.map { it.id }.toSet())
    }

    @Test fun `deletion requires the verified original author and exact target`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-author-actions-reference.json")!!.reader()).asJsonObject
        val original = fixture.getAsJsonObject("original").get("tx").asString
        assertEquals(fixture.getAsJsonObject("deleted").get("sig").asString,
            RedSquareAuthorActions.deletion(original, fixture.getAsJsonObject("deleted").get("tx").asString)!!.id)
        assertEquals(null, RedSquareAuthorActions.deletion(original, fixture.getAsJsonObject("unrelatedDelete").get("tx").asString))
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture key.
        val body = SaitoEnvelope.decode(fixture.getAsJsonObject("deleted").get("tx").asString).body.deepCopy()
        body.getAsJsonObject("data").addProperty("tweet_id", "f".repeat(128))
        val wrongTarget = SaitoTransaction.create(wallet, body, 1700000070005L).envelope()
        assertEquals(null, RedSquareAuthorActions.deletion(original, wrongTarget))
        assertEquals(null, RedSquareAuthorActions.deletion(original, fixture.getAsJsonObject("edit").get("tx").asString))
    }
}
