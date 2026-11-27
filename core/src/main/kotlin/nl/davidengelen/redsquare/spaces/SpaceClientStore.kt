package nl.davidengelen.redsquare.spaces

/** One account/node namespace. Implementations atomically commit all writes,
 * protect private bytes at rest and preserve prior data on failure. Off main. */
interface SpaceClientStore {
    fun <T> transaction(action: Transaction.() -> T): T

    interface Transaction {
        fun get(key: String): ByteArray?
        /** One bounded set of existing rows. Missing keys are omitted. */
        fun getAll(keys: List<String>): Map<String, ByteArray> {
            require(keys.size <= 100)
            return buildMap { for (key in keys) get(key)?.let { put(key, it) } }
        }
        fun put(key: String, value: ByteArray)
        fun remove(key: String)
        /** Lexicographic, inclusive cursor and bounded result. */
        fun page(prefix: String, cursor: String = prefix, limit: Int = 50): List<Entry>
        /** Reverse lexicographic order, with an exclusive upper cursor. */
        fun pageBefore(prefix: String, before: String = prefix + '\uffff', limit: Int = 50): List<Entry>
    }
    data class Entry(val key: String, val value: ByteArray)
}
