package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class SaitoStackTest {
    @Test fun `signed article exposes the original title author content cover and revision identity`() {
        val rows = JsonParser.parseReader(javaClass.getResourceAsStream("/native-stack-reference.json")!!.reader()).asJsonObject
        val article = SaitoStack.decode(rows.getAsJsonObject("article").get("tx").asString)
        assertEquals("Open networks, real writing", article.title)
        assertEquals("jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR", article.author)
        assertEquals("A short introduction", article.excerpt)
        assertEquals("# A real article\n\nOriginal **Markdown**.", article.markdown)
        assertEquals("https://images.example.test/stack.jpg", article.cover)
        assertEquals(rows.getAsJsonObject("article").get("sig").asString, article.id)
        assertEquals(article.id, article.revision)
    }

    @Test fun `archive revisions stay with their signer and restricted rows do not become public content`() {
        val reference = JsonParser.parseReader(javaClass.getResourceAsStream("/native-stack-reference.json")!!.reader()).asJsonObject
        val rows = com.google.gson.JsonArray().apply {
            listOf("article", "revision", "foreign", "restricted").forEach { add(reference.get(it)) }
        }
        val page = SaitoStack.readArchive(rows.toString())
        assertEquals(4, page.rowCount)
        assertEquals(1, page.ignored)
        assertEquals(2, page.articles.size)
        assertEquals("Revised writing", page.articles.single { it.author == "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR" }.title)
        assertEquals("Different author", page.articles.single { it.author != "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR" }.title)
        assertEquals(1700001000000L, page.before)
        assertEquals("https://saito.io/stack/jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR/" +
            reference.getAsJsonObject("revision").get("sig").asString,
            SaitoStack.articleUrl(page.articles.single { it.title == "Revised writing" }, SaitoBackend.OFFICIAL))
    }
}
