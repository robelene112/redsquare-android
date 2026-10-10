package nl.davidengelen.redsquare.saito

import org.junit.Assert.assertEquals
import org.junit.Test

class RedSquareVideoTest {
    @Test fun `a compatible first YouTube link yields a canonical tap target`() {
        val video = RedSquareVideo.youtube("Watch https://www.youtube.com/watch?v=M7lc1UVf-VE&t=20 now")!!
        assertEquals("M7lc1UVf-VE", video.id)
        assertEquals("https://www.youtube.com/watch?v=M7lc1UVf-VE", video.url)
    }
}
