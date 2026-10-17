package nl.davidengelen.redsquare.saito

/** Canonical supported subscription categories shared by native client and gateway. */
object PushCategories {
    fun requireCanonical(value: String) {
        val parts = value.split(',')
        require(parts.all { it in setOf("mentions", "private-chats", "replies") } &&
            parts.distinct().sorted().joinToString(",") == value)
    }
}
