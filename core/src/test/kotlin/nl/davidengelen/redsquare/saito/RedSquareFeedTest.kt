package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class RedSquareFeedTest {
    @Test fun `independent WASM archive transaction yields verified original post and author`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-feed-reference.json")!!.reader()).asJsonObject
        val expected = fixture.getAsJsonObject("expected")
        val post = RedSquareFeed.decode(fixture.getAsJsonObject("archiveRow").get("tx").asString)
        assertEquals(expected.get("signature").asString, post.id)
        assertEquals(expected.get("author").asString, post.author)
        assertEquals("Native Red Square reference post", post.text)
        assertEquals(1700000000000L, post.createdAt)
        assertEquals("", post.parentId)
    }
}
