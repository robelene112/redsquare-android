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
    @Test fun `web posts with signed null fields retain compatible empty defaults`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-feed-null-reference.json")!!.reader()).asJsonObject
        val post = RedSquareFeed.decode(fixture.getAsJsonObject("archiveRow").get("tx").asString)
        assertEquals("", post.text)
        assertEquals("", post.parentId)
        assertEquals(fixture.getAsJsonObject("expected").get("threadId").asString, post.threadId)
    }
    @Test fun `altered and malformed archive rows do not conceal authentic posts`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-feed-reference.json")!!.reader()).asJsonObject
        val valid = fixture.getAsJsonObject("archiveRow")
        val changedEnvelope = fixture.getAsJsonObject("webEnvelope").deepCopy().apply {
            addProperty("m", java.util.Base64.getEncoder().encodeToString("Changed post".toByteArray()))
        }
        val changed = valid.deepCopy().apply {
            addProperty("created_at", 1699999999999L)
            addProperty("tx", changedEnvelope.toString())
        }
        val page = RedSquareFeed.readArchive("[$changed,{},$valid]")
        assertEquals(listOf("Native Red Square reference post"), page.posts.map { it.text })
        assertEquals(1699999999999L, page.oldestCreatedAt)
        assertEquals(3, page.rowCount)
        assertEquals(2, page.ignored)
    }
}
