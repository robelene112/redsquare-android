package nl.davidengelen.redsquare.spaces

import org.junit.Assert.*
import org.junit.Test

class SpacePresentationStoreTest {
    @Test fun failedPrefetchLeavesOptionalRowsUnreadAndDemandedFailuresIntact() {
        val disk = AtomicRows()
        disk.transaction { put("message", byteArrayOf(1)); put("old-edit", byteArrayOf(2)) }
        disk.unreadableKey = "old-edit"
        val store = SpacePresentationStore(disk)
        store.project { store.transaction {
            store.prefetch(listOf("message", "old-edit"))
            assertArrayEquals(byteArrayOf(1), get("message"))
            assertThrows(StorageFailure::class.java) { get("old-edit") }
            assertThrows(StorageFailure::class.java) { store.readRows(this, listOf("message", "old-edit")) }
        } }
    }

    @Test fun preloadedRowsShareTheSnapshotIncludingMissingRowsAndWrites() {
        val disk = AtomicRows()
        disk.transaction { put("present", byteArrayOf(1)) }
        val store = SpacePresentationStore(disk)
        store.project { store.transaction {
            store.prefetch(listOf("present", "missing"))
            assertEquals(mapOf("present" to 1, "missing" to 1), disk.reads)
            get("present")!![0] = 9
            assertArrayEquals(byteArrayOf(1), get("present"))
            assertNull(get("missing"))
            assertEquals(mapOf("present" to 1, "missing" to 1), disk.reads)
            remove("present")
            put("missing", byteArrayOf(2))
            assertNull(get("present"))
            assertArrayEquals(byteArrayOf(2), get("missing"))
        } }
        disk.transaction { put("present", byteArrayOf(3)); remove("missing") }
        store.project { store.transaction {
            assertArrayEquals(byteArrayOf(3), get("present"))
            assertNull(get("missing"))
        } }
    }

    @Test fun demandedBatchReturnsLargeRowsInRequestedOrderWithNoSecondDiskPass() {
        val disk = AtomicRows()
        val keys = (0 until 8).map { "receipt-$it" }
        disk.transaction { keys.forEachIndexed { index, key -> put(key, ByteArray(180 * 1024) { index.toByte() }) } }
        val store = SpacePresentationStore(disk)
        val checkRows = { store.transaction {
            val rows = store.readRows(this, keys.reversed() + listOf("missing", keys.last()))
            assertEquals((7 downTo 0).map { it.toByte() } + listOf(null, 7.toByte()), rows.map { it?.first() })
            rows.first()!![0] = 9
            assertEquals(7.toByte(), rows.last()?.first())
            assertEquals((keys + "missing").associateWith { 1 }, disk.reads)
            assertEquals(1, disk.batchReads)
        } }
        checkRows()
        disk.reads.clear(); disk.batchReads = 0
        store.project(checkRows)
    }

    @Test fun demandedBatchFallsBackWhenOnlyTheBulkReadFails() {
        val disk = AtomicRows()
        disk.transaction { put("row", byteArrayOf(7)) }
        disk.failBatchRead = true
        val store = SpacePresentationStore(disk)
        store.transaction {
            assertArrayEquals(byteArrayOf(7), store.readRows(this, listOf("row")).single())
            assertEquals(1, disk.batchReads)
            assertEquals(mapOf("row" to 1), disk.reads)
        }
    }

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
        var batchReads = 0
        var failBatchRead = false
        var failAfterRemoval = false
        var unreadableKey: String? = null
        override fun <T> transaction(action: SpaceClientStore.Transaction.() -> T): T {
            val working = committed.mapValues { it.value.copyOf() }.toMutableMap()
            val result = action(object : SpaceClientStore.Transaction {
                override fun get(key: String): ByteArray? {
                    reads[key] = (reads[key] ?: 0) + 1
                    if (key == unreadableKey) throw StorageFailure()
                    return working[key]?.copyOf()
                }
                override fun getAll(keys: List<String>): Map<String, ByteArray> {
                    batchReads++
                    if (failBatchRead) throw StorageFailure()
                    return super.getAll(keys)
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
                override fun pageBefore(prefix: String, before: String, limit: Int) = working.entries
                    .filter { it.key.startsWith(prefix) && it.key < before }.sortedByDescending { it.key }.take(limit)
                    .map { SpaceClientStore.Entry(it.key, it.value.copyOf()) }
            })
            committed = working
            return result
        }
    }
}
