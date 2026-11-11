package nl.davidengelen.redsquare.saito

/** Serializes durable identity/selection changes with local delivery claims. */
internal object SaitoLocalState {
    val lock = Any()
    // Live transfers can revalidate the durable authority when it changes, rather than decrypting a wallet per chunk.
    var authorityRevision: Long = 0
        private set
    fun invalidateAuthority() {
        check(Thread.holdsLock(lock))
        authorityRevision = Math.addExact(authorityRevision, 1)
    }
}
