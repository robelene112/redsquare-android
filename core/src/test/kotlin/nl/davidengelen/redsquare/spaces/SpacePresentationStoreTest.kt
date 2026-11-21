package nl.davidengelen.redsquare.spaces

import org.junit.Assert.*
import org.junit.Test

class SpacePresentationStoreTest {
    @Test fun snapshotReadsAndPagesKeepCallerBytesIndependentAndObserveOwnWrites() {
        val disk = AtomicRows()
        disk.transaction { put("row", byteArrayOf(1)) }
        val store = SpacePresentationStore(disk)
        store.project { store.transaction {
            get("row")!![0] = 9
            assertArrayEquals(byteArrayOf(1), get("row"))
            val replacement = byteArrayOf(2)
            put("row", replacement); replacement[0] = 8
            assertArrayEquals(byteArrayOf(2), get("row"))
            page("row").single().value[0] = 7
            get("row")!![0] = 6
            assertArrayEquals(byteArrayOf(2), get("row"))
            remove("row"); assertNull(get("row"))
            put("row", byteArrayOf(3)); assertArrayEquals(byteArrayOf(3), get("row"))
        } }
        assertArrayEquals(byteArrayOf(3), disk.transaction { get("row") })
        disk.transaction { put("row", byteArrayOf(4)) }
        store.project { store.transaction { assertArrayEquals(byteArrayOf(4), get("row")) } }
    }

    @Test fun aFailedWriteAndProjectionCannotRetainAChangedOrRemovedRow() {
        val disk = AtomicRows()
        disk.transaction { put("row", byteArrayOf(1)) }
        val store = SpacePresentationStore(disk)
        for (deleting in listOf(false, true)) {
            assertThrows(IllegalStateException::class.java) {
                store.project { store.transaction {
                    assertArrayEquals(byteArrayOf(1), get("row"))
                    disk.failAfterRemoval = true
                    try {
                        if (deleting) remove("row") else put("row", byteArrayOf(2))
                        fail("Injected storage failure must be observable")
                    } catch (_: StorageFailure) { }
                    assertNull("Failed mutations cannot leave a stale cached row", get("row"))
                    throw IllegalStateException("Abort the actual transaction")
                } }
            }
            store.project { store.transaction { assertArrayEquals(byteArrayOf(1), get("row")) } }
        }
    }

    @Test fun snapshotRetentionIsBoundedForManyLargeAndOversizedRows() {
        val disk = AtomicRows()
        disk.transaction {
            put("large-a", ByteArray(512 * 1024)); put("large-b", ByteArray(512 * 1024))
            put("small", byteArrayOf(1)); put("oversized", ByteArray(1024 * 1024 + 1))
            repeat(129) { put("item-$it", byteArrayOf(1)) }
        }
        val store = SpacePresentationStore(disk)
        store.project { store.transaction {
            get("large-a"); get("large-b"); get("small"); get("large-a")
            assertEquals("Byte budget evicts the oldest retained row", 2, disk.reads["large-a"])
            get("oversized"); get("oversized")
            assertEquals("An oversized row is read without being retained", 2, disk.reads["oversized"])
        } }
        store.project { store.transaction {
            repeat(129) { get("item-$it") }; get("item-0")
            assertEquals("Entry budget also bounds small rows", 2, disk.reads["item-0"])
        } }
    }

    private class StorageFailure : IllegalStateException("Injected row write failure")
    /** Transaction-interface fixture: committed rows survive failed callbacks;
     * mutable results are independent, as with real encrypted/SQLite adapters. */
    private class AtomicRows : SpaceClientStore {
        private var committed = mutableMapOf<String, ByteArray>()
        val reads = mutableMapOf<String, Int>()
        var failAfterRemoval = false
        override fun <T> transaction(action: SpaceClientStore.Transaction.() -> T): T {
            val working = committed.mapValues { it.value.copyOf() }.toMutableMap()
            val result = action(object : SpaceClientStore.Transaction {
                override fun get(key: String): ByteArray? {
                    reads[key] = (reads[key] ?: 0) + 1
                    return working[key]?.copyOf()
                }
                override fun put(key: String, value: ByteArray) {
                    remove(key); working[key] = value.copyOf()
                }
                override fun remove(key: String) {
                    working.remove(key)
                    if (failAfterRemoval) { failAfterRemoval = false; throw StorageFailure() }
                }
                override fun page(prefix: String, cursor: String, limit: Int) = working.entries
                    .filter { it.key.startsWith(prefix) && it.key >= cursor }.sortedBy { it.key }.take(limit)
                    .map { SpaceClientStore.Entry(it.key, it.value.copyOf()) }
            })
            committed = working
            return result
        }
    }
}
