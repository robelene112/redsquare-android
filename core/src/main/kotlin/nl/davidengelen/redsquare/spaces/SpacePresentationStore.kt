package nl.davidengelen.redsquare.spaces

import java.util.concurrent.CancellationException

/** One synchronous presentation borrows one actual store transaction. Verified
 * journals are reused only inside that read, never across commits or failures.
 * Readers may maintain disposable indexes, but do not receive control events. */
internal class SpacePresentationStore(private val backing: SpaceClientStore) : SpaceClientStore {
    private data class Read(val transaction: SnapshotRows,
        val journals: MutableMap<Triple<String, String, String?>, SpaceJournal> = mutableMapOf())
    private val active = ThreadLocal<Read?>()

    override fun <T> transaction(action: SpaceClientStore.Transaction.() -> T): T {
        val read = active.get()
        return if (read != null) action(read.transaction) else backing.transaction(action)
    }

    fun <T> project(action: () -> T): T {
        if (active.get() != null) return action()
        return backing.transaction {
            active.set(Read(SnapshotRows(this)))
            try { action() } finally { active.remove() }
        }
    }

    fun verifiedJournal(service: String, space: String, through: String?, load: () -> SpaceJournal): SpaceJournal {
        val read = active.get() ?: return load()
        return read.journals.getOrPut(Triple(service, space, through), load)
    }

    fun prefetch(keys: List<String>) { active.get()?.transaction?.prefetch(keys) }

    fun readRows(keys: List<String>): List<ByteArray?> =
        active.get()?.transaction?.readRows(keys) ?: transaction { keys.map(::get) }

    /** Repeated identity/state reads share this one atomic snapshot, including
     * disposable index writes. Never retain plaintext or authority between transactions. */
    private class SnapshotRows(private val backing: SpaceClientStore.Transaction) : SpaceClientStore.Transaction {
        private val values = LinkedHashMap<String, ByteArray?>(16, .75f, true)
        private var bytes = 0

        override fun get(key: String): ByteArray? {
            if (values.containsKey(key)) return values[key]?.copyOf()
            return backing.get(key).also { remember(key, it) }
        }

        fun prefetch(keys: List<String>) {
            val missing = keys.distinct().filterNot(values::containsKey)
            if (missing.isEmpty()) return
            // A hint can encounter an old edit on a deleted message. Only the
            // ordinary reads determine which failures the caller observes.
            val found = readBatch(missing) ?: return
            for (key in missing) remember(key, found[key])
        }

        fun readRows(keys: List<String>): List<ByteArray?> {
            val missing = keys.distinct().filterNot(values::containsKey)
            val found = if (missing.isEmpty()) emptyMap() else readBatch(missing) ?: return keys.map(::get)
            // Deliver the result directly. A valid page can exceed the memo budget.
            return keys.map { key -> (if (values.containsKey(key)) values[key] else found[key])?.copyOf() }
        }

        private fun readBatch(keys: List<String>): Map<String, ByteArray>? = try { backing.getAll(keys) }
        catch (failure: Exception) {
            if (failure is CancellationException || failure is InterruptedException) throw failure
            null
        }

        override fun put(key: String, value: ByteArray) {
            forget(key)
            backing.put(key, value)
            remember(key, value)
        }

        override fun remove(key: String) {
            forget(key)
            backing.remove(key)
            remember(key, null)
        }

        override fun page(prefix: String, cursor: String, limit: Int): List<SpaceClientStore.Entry> =
            backing.page(prefix, cursor, limit).also { rows -> rows.forEach { remember(it.key, it.value) } }

        override fun pageBefore(prefix: String, before: String, limit: Int): List<SpaceClientStore.Entry> =
            backing.pageBefore(prefix, before, limit).also { rows -> rows.forEach { remember(it.key, it.value) } }

        private fun forget(key: String) { bytes -= values.remove(key)?.size ?: 0 }

        private fun remember(key: String, value: ByteArray?) {
            forget(key)
            if ((value?.size ?: 0) > MAX_BYTES) return
            values[key] = value?.copyOf()
            bytes += value?.size ?: 0
            while (values.size > MAX_ROWS || bytes > MAX_BYTES) forget(values.keys.first())
        }

        companion object {
            private const val MAX_ROWS = 128
            private const val MAX_BYTES = 1024 * 1024
        }
    }
}
