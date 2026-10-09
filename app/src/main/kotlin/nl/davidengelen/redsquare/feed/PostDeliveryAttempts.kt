package nl.davidengelen.redsquare.feed

/** A foreground retry and resumed delivery cannot transmit the same item concurrently. */
internal object PostDeliveryAttempts {
    private val active = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    suspend fun run(id: String, operation: suspend () -> Unit) {
        if (!active.add(id)) return
        try { operation() } finally { active.remove(id) }
    }
}
