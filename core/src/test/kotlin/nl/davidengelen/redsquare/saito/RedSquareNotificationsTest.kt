package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class RedSquareNotificationsTest {
    @Test fun `an ordinary signed repost retains its original while commentary remains a quote`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-notification-reference.json")!!.reader()).asJsonObject
        val repost = fixture.getAsJsonObject("incomingRepost")
        val quote = fixture.getAsJsonObject("parentedQuote")
        val page = RedSquareActions.readIncomingReposts("[$repost,$quote]", "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR")
        assertEquals(listOf(repost.get("sig").asString), page.actions.map { it.id })
        assertEquals(fixture.getAsJsonObject("root").get("sig").asString, page.actions.single().target)
        assertEquals("pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU", page.actions.single().author)
        assertEquals(1700000000070L, page.actions.single().createdAt)
        assertEquals(0, page.ignored)
    }

    @Test fun `a username mention qualifies only with a registrar proof for the active owner`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-notification-reference.json")!!.reader()).asJsonObject
        val registry = JsonParser.parseReader(javaClass.getResourceAsStream("/native-registry-reference.json")!!.reader()).asJsonObject
        val authority = registry.get("registrar").asString
        val known = SaitoRegistry.readNamecheck(registry.get("rows").toString(), "NativeCase7@saito", authority)!!
        val row = fixture.getAsJsonObject("usernameMention")
        val owner = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        assertTrue(RedSquareFeed.readMentions("[$row]", owner).posts.isEmpty())
        val page = RedSquareFeed.readMentions("[$row]", owner, known, authority)
        assertEquals(listOf("Hello @NativeCase7@saito, username mention reference"), page.posts.map { it.text })
        assertEquals(1700000000063L, page.posts.single().createdAt)
        assertTrue(RedSquareFeed.readMentions("[$row]", owner, known).posts.isEmpty())
        assertTrue(RedSquareFeed.readMentions("[$row]", owner, known.copy(publicKey = authority), authority).posts.isEmpty())
        assertTrue(RedSquareFeed.readMentions("[$row]", owner, known.copy(proof = "{}"), authority).posts.isEmpty())
    }

    @Test fun `a signed mention list qualifies without a public key token in the text`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-notification-reference.json")!!.reader()).asJsonObject
        val row = fixture.getAsJsonObject("listedMention")
        val page = RedSquareFeed.readMentions("[$row]", "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR")
        assertEquals(listOf("A signed mention list"), page.posts.map { it.text })
        assertEquals("pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU", page.posts.single().author)
        assertEquals(1700000000062L, page.posts.single().createdAt)
        assertEquals(0, page.ignored)
    }

    @Test fun `an exact public key text mention counts while ordinary addressing does not`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-notification-reference.json")!!.reader()).asJsonObject
        val owner = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        val mentioned = fixture.getAsJsonObject("incomingMention")
        val addressed = fixture.getAsJsonObject("addressedPost")
        val page = RedSquareFeed.readMentions("[$mentioned,$addressed]", owner)
        assertEquals(listOf("Hello @$owner, mention reference"), page.posts.map { it.text })
        assertEquals("pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU", page.posts.single().author)
        assertEquals(1700000000060L, page.posts.single().createdAt)
        assertEquals(0, page.ignored)
    }

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
