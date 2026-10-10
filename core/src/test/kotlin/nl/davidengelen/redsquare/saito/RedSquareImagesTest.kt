package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class RedSquareImagesTest {
    @Test fun `signed native image order matches the independent web reference`() {
        val reference = JsonParser.parseReader(javaClass.getResourceAsStream("/native-images-reference.json")!!.reader()).asJsonObject
        val post = RedSquareFeed.decode(reference.getAsJsonObject("ordinary").get("tx").asString)
        assertEquals("Signed image reference", post.text)
        val images = reference.getAsJsonObject("images")
        assertEquals(listOf(images.get("red").asString, images.get("blue").asString), post.images)
    }
    @Test fun `unsafe media does not discard authenticated text or safe images`() {
        val reference = JsonParser.parseReader(javaClass.getResourceAsStream("/native-images-reference.json")!!.reader()).asJsonObject
        val post = RedSquareFeed.decode(reference.getAsJsonObject("malformed").get("tx").asString)
        assertEquals("Text survives unsafe image entries", post.text)
        assertEquals(listOf("https://example.test/image.png", "/media/reference.png",
            reference.getAsJsonObject("images").get("red").asString), post.images)
    }
    @Test fun `a quote cannot attribute conflicting image order to the original author`() {
        val reference = JsonParser.parseReader(javaClass.getResourceAsStream("/native-images-reference.json")!!.reader()).asJsonObject
        val post = RedSquareFeed.decode(reference.getAsJsonObject("conflicting").get("tx").asString)
        assertEquals("Image quote commentary", post.text)
        assertEquals(listOf(reference.getAsJsonObject("images").get("green").asString), post.images)
        assertEquals(null, post.quoted)
    }
    @Test fun `outgoing quote images match independent wire bytes and preserve original media`() {
        val reference = JsonParser.parseReader(javaClass.getResourceAsStream("/native-images-reference.json")!!.reader()).asJsonObject
        val original = RedSquareFeed.decode(reference.getAsJsonObject("ordinary").get("tx").asString)
        val ownImages = listOf(reference.getAsJsonObject("images").get("green").asString)
        val message = RedSquarePosts.message("Image quote commentary", ownImages, quote = original)
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe reference, never fund.
        val candidate = SaitoTransaction.create(wallet, message, 1700000080001, listOf(original.author))
        assertEquals(reference.getAsJsonObject("dual").get("sig").asString, candidate.id)
        val decoded = RedSquareFeed.decode(candidate.envelope())
        assertEquals(ownImages, decoded.images)
        assertEquals(original.images, decoded.quoted!!.images)
        assertEquals(original.envelope, decoded.quoted!!.envelope)
    }
}
