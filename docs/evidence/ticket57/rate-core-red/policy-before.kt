package nl.davidengelen.redsquare.feed

/** Local deliberate-input policy, independent of Saito transaction/retry rules. */
object PostLikeInputPolicy {
    fun reserve(acceptedAt: List<Long>, nowMillis: Long): List<Long>? = acceptedAt + nowMillis
}
