package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class RedSquareNotificationsTest {
    @Test fun `a signed commentary quote with a parent field is not classified as a reply`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-notification-reference.json")!!.reader()).asJsonObject
        val reply = fixture.getAsJsonObject("incomingReply")
        val quote = fixture.getAsJsonObject("parentedQuote")
        // Both signatures and parent references are valid; only the signed request differs.
        assertEquals(2, RedSquareFeed.readSearch("[$reply,$quote]").posts.size)
        val page = RedSquareFeed.readReplies("[$reply,$quote]")
        assertEquals(listOf("Reply notification reference"), page.posts.map { it.text })
        assertEquals("pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU", page.posts.single().author)
        assertEquals(1700000000055L, page.posts.single().createdAt)
        assertEquals("A verified quote is ineligible, not an unverified transaction", 0, page.ignored)
    }
}
