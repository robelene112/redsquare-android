package nl.davidengelen.redsquare.saito

import org.junit.Assert.*
import org.junit.Test

class StackLinkTest {
    private val author = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
    @Test fun `approved public article author and module URLs identify native destinations`() {
        assertEquals(StackLink(SaitoBackend.OFFICIAL, author, "a".repeat(128)),
            StackLink.parse("https://saito.io/stack/$author/${"a".repeat(128)}"))
        assertEquals(StackLink(SaitoBackend.OWNER, author), StackLink.parse("https://saito.davidengelen.nl/stack/$author/"))
        assertEquals(StackLink(SaitoBackend.OFFICIAL), StackLink.parse("https://saito.io/stack"))
    }
    @Test fun `article lookup keeps the exact signed revision rather than substituting a newer story`() {
        val fixture = com.google.gson.JsonParser.parseReader(javaClass.getResourceAsStream("/native-stack-reference.json")!!.reader()).asJsonObject
        val signature = fixture.getAsJsonObject("article").get("sig").asString
        val response = com.google.gson.JsonArray().apply {
            listOf("article", "revision", "foreign", "restricted").forEach { add(fixture.get(it)) }
        }.toString()
        val result = SaitoStack.readArticle(response, author, signature)
        assertNotNull(result)
        assertEquals("Open networks, real writing", result!!.title)
        assertEquals(signature, result.revision)
        assertNull(SaitoStack.readArticle(response, author, "a".repeat(128)))
        for (name in listOf("foreign", "restricted")) {
            assertNull(name, SaitoStack.readArticle(response, author, fixture.getAsJsonObject(name).get("sig").asString))
        }
        val request = com.google.gson.JsonParser.parseString(SaitoStack.articleRequest(author, signature)).asJsonObject
        assertEquals("archive", request.get("request").asString)
        val data = request.getAsJsonObject("data")
        assertEquals(setOf("request", "field1", "field2", "field4", "sig", "limit"), data.keySet())
        assertEquals("load", data.get("request").asString)
        assertEquals("Stack", data.get("field1").asString)
        assertEquals(author, data.get("field2").asString)
        assertEquals("stack:post", data.get("field4").asString)
        assertEquals(signature, data.get("sig").asString)
        assertEquals(1, data.get("limit").asInt)
    }
    @Test fun `foreign malformed and explicit website action URLs remain external`() {
        for (url in listOf("https://saito.io.evil.test/stack/$author", "https://saito.io@evil.test/stack/$author",
            "https://saito.io:8443/stack/$author", "https://saito.io/stack/not-a-key", "https://saito.io/stack/$author/not-a-signature",
            "https://saito.io/%73tack/$author", "https://saito.io/stack//", "https://saito.io/stack?publish=1", "https://example.org/stack")) {
            assertNull(url, StackLink.parse(url))
        }
    }
}
