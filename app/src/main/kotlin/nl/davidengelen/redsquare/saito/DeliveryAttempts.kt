package nl.davidengelen.redsquare.saito

/** A foreground retry and resumed delivery cannot transmit the same item concurrently. */
internal object DeliveryAttempts {
    private val active = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    suspend fun run(id: String, operation: suspend () -> Unit) {
        if (!active.add(id)) return
        try { operation() } finally { active.remove(id) }
    }
}
