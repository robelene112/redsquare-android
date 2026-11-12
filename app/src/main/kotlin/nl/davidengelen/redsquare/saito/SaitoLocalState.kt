package nl.davidengelen.redsquare.saito

/** Serializes durable identity/selection changes with local delivery claims. */
internal object SaitoLocalState {
    val lock = Any()
    // Live transfers can revalidate the durable authority when it changes, rather than decrypting a wallet per chunk.
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun observeAuthority(listener: () -> Unit): java.io.Closeable = synchronized(lock) {
        listeners.add(listener)
        java.io.Closeable { listeners.remove(listener) }
    }
    var authorityRevision: Long = 0
        private set
    fun invalidateAuthority() {
        check(Thread.holdsLock(lock))
        authorityRevision = Math.addExact(authorityRevision, 1)
        // Silence live media before the durable identity/policy mutation begins.
        // Observers defer their reads until this write lock is released.
        listeners.forEach { listener -> runCatching { listener() } }
    }
}
