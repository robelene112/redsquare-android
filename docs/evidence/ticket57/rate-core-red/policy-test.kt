package nl.davidengelen.redsquare.feed

import org.junit.Assert.*
import org.junit.Test

class PostLikeInputPolicyTest {
    @Test fun twoNewIntentsFitARollingSecondAndTheOldestExpiresAtTheBoundary() {
        val first = requireNotNull(PostLikeInputPolicy.reserve(emptyList(), 10_000))
        val second = requireNotNull(PostLikeInputPolicy.reserve(first, 10_500))
        assertNull(PostLikeInputPolicy.reserve(second, 10_999))
        assertEquals(listOf(10_500L, 11_000L), PostLikeInputPolicy.reserve(second, 11_000))
    }
}
