package nl.davidengelen.redsquare.spaces

/** One account/node namespace. Implementations atomically commit all writes,
 * protect private bytes at rest and preserve prior data on failure. Off main. */
interface SpaceClientStore {
    fun <T> transaction(action: Transaction.() -> T): T

    interface Transaction {
        fun get(key: String): ByteArray?
        fun put(key: String, value: ByteArray)
        fun remove(key: String)
        /** Lexicographic, inclusive cursor and bounded result. */
        fun page(prefix: String, cursor: String = prefix, limit: Int = 50): List<Entry>
    }
    data class Entry(val key: String, val value: ByteArray)
}
