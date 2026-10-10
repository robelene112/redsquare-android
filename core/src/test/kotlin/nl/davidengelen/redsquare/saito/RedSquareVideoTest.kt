package nl.davidengelen.redsquare.saito

import org.junit.Assert.assertEquals
import org.junit.Test

class RedSquareVideoTest {
    @Test fun `a compatible first YouTube link yields a canonical tap target`() {
        val video = RedSquareVideo.youtube("Watch https://www.youtube.com/watch?v=M7lc1UVf-VE&t=20 now")!!
        assertEquals("M7lc1UVf-VE", video.id)
        assertEquals("https://www.youtube.com/watch?v=M7lc1UVf-VE", video.url)
    }

    @Test fun `a verified explicit video link survives archive decoding`() {
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture.
        val message = RedSquarePosts.message("Signed video link")
        message.getAsJsonObject("data").addProperty("link", "https://youtu.be/M7lc1UVf-VE")
        val post = RedSquareFeed.decode(SaitoTransaction.create(wallet, message, 1700000900001).envelope())
        assertEquals("Signed video link", post.text)
        assertEquals("M7lc1UVf-VE", RedSquareVideo.youtube(post.text, post.link)?.id)
    }

    @Test fun `real provider paths work but misleading hosts and executable links stay text`() {
        for (url in listOf("https://youtu.be/M7lc1UVf-VE?si=public", "https://youtube.com/shorts/M7lc1UVf-VE",
            "https://m.youtube.com/live/M7lc1UVf-VE#t=2", "https://music.youtube.com/watch?v=M7lc1UVf-VE")) {
            assertEquals(url, "M7lc1UVf-VE", RedSquareVideo.youtube("See $url.")?.id)
        }
        for (url in listOf("https://youtube.com.attacker.test/watch?v=M7lc1UVf-VE", "https://notyoutube.com/watch?v=M7lc1UVf-VE",
            "https://youtube.com@attacker.test/watch?v=M7lc1UVf-VE", "javascript:alert(1)", "file:///M7lc1UVf-VE",
            "https://youtu.be:444/M7lc1UVf-VE", "https://youtu.be/M7lc1UVf-VE/extra", "https://youtube.com/watch?v=short")) {
            assertEquals(url, null, RedSquareVideo.youtube("", url))
        }
        assertEquals(null, RedSquareVideo.youtube("https://example.test/first https://youtu.be/M7lc1UVf-VE"))
        assertEquals(null, RedSquareVideo.youtube("https://youtu.be/M7lc1UVf-VE", "https://example.test/signed"))
    }
}
