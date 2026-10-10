package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class RedSquareAuthorActionsTest {
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
}
