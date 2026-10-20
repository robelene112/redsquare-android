package nl.davidengelen.redsquare.feed

/** Local deliberate-input policy, independent of Saito transaction/retry rules. */
object PostLikeInputPolicy {
    fun reserve(acceptedAt: List<Long>, nowMillis: Long): List<Long>? {
        require(nowMillis >= 0 && acceptedAt.size <= 2 && acceptedAt.all { it >= 0 })
        require(acceptedAt.zipWithNext().all { (before, after) -> before <= after })
        // A backwards clock cannot reopen a previously consumed window.
        if (acceptedAt.any { it > nowMillis }) return null
        val recent = acceptedAt.filter { nowMillis - it < 1000 }
        return if (recent.size == 2) null else recent + nowMillis
    }
}
