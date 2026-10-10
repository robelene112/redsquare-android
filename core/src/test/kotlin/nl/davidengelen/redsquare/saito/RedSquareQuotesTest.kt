package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class RedSquareQuotesTest {
    @Test fun `authenticated commentary survives an original with an invalid signature`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-quote-reference.json")!!.reader()).asJsonObject
        val post = RedSquareFeed.decode(fixture.getAsJsonObject("invalidOriginal").get("tx").asString)
        assertEquals("Commentary survives an invalid original", post.text)
        assertEquals(fixture.getAsJsonObject("original").get("sig").asString, post.quoteTarget)
        assertEquals(null, post.quoted)
    }

    @Test fun `current signed commentary retweet preserves its own identity and author`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-quote-reference.json")!!.reader()).asJsonObject
        val current = fixture.getAsJsonObject("current")
        val post = RedSquareFeed.decode(current.get("tx").asString)
        assertEquals(current.get("sig").asString, post.id)
        assertEquals(current.get("field2").asString, post.author)
        assertEquals(1700000060002L, post.createdAt)
        assertEquals("Current commentary retweet", post.text)
        assertEquals(post.id, post.threadId)
    }
}
