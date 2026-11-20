package nl.davidengelen.redsquare.spaces

/** One synchronous presentation borrows one actual store transaction. Verified
 * journals are reused only inside that read, never across commits or failures.
 * Readers may maintain disposable indexes, but do not receive control events. */
internal class SpacePresentationStore(private val backing: SpaceClientStore) : SpaceClientStore {
    private data class Read(val transaction: SpaceClientStore.Transaction,
        val journals: MutableMap<Triple<String, String, String?>, SpaceJournal> = mutableMapOf())
    private val active = ThreadLocal<Read?>()

    override fun <T> transaction(action: SpaceClientStore.Transaction.() -> T): T {
        val read = active.get()
        return if (read != null) action(read.transaction) else backing.transaction(action)
    }

    fun <T> project(action: () -> T): T {
        if (active.get() != null) return action()
        return backing.transaction {
            active.set(Read(this))
            try { action() } finally { active.remove() }
        }
    }

    fun verifiedJournal(service: String, space: String, through: String?, load: () -> SpaceJournal): SpaceJournal {
        val read = active.get() ?: return load()
        return read.journals.getOrPut(Triple(service, space, through), load)
    }
}
