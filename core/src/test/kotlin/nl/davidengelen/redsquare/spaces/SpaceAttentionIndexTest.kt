package nl.davidengelen.redsquare.spaces

import org.junit.Assert.*
import org.junit.Test

class SpaceAttentionIndexTest {
    @Test fun ordinaryActivityAndPersonalCountsAreSeparateAndThreadReadsDoNotEraseOtherScopes() {
        val rows = Rows()
        rows.transaction {
            val index = SpaceAttentionIndex(this, "channel-a")
            index.observe("ordinary", 1, personal = false)
            index.observe("mention", 2, personal = true)
            index.observe("reply", 3, thread = "parent-a", personal = true)
            index.observe("other-reply", 4, thread = "parent-b", personal = false)
            assertEquals(4L, index.counts().ordinary); assertEquals(2L, index.counts().personal)
            index.markRead(null, 100)
            assertEquals(2L, index.counts().ordinary); assertEquals(1L, index.counts().personal)
            index.markRead("parent-b", 100)
            assertEquals(1L, index.counts().ordinary); assertEquals(1L, index.counts().personal)
            index.markRead("parent-a", 3)
            assertEquals(0L, index.counts().ordinary); assertEquals(0L, index.counts().personal)
        }
    }
    @Test fun lateKeysAndAuthenticOldEditsCannotResurrectAlreadyReadOrRemovedMentions() {
        val rows = Rows()
        rows.transaction {
            val index = SpaceAttentionIndex(this, "channel-a")
            index.observe("encrypted", 1, personal = null)
            assertEquals(1L, index.counts().ordinary); assertNull(index.counts().personal)
            index.observe("encrypted", 1, personal = true)
            assertEquals(1L, index.counts().personal)
            index.observe("encrypted", 1, personal = null)
            assertEquals(1L, index.counts().personal)
            index.observe("encrypted", 1, personal = false, version = 10)
            assertEquals(0L, index.counts().personal)
            index.observe("encrypted", 1, personal = true, version = 9)
            assertEquals(0L, index.counts().personal)
            index.observe("encrypted", 1, personal = true, version = 11)
            index.markRead(null, 1)
            index.observe("encrypted", 1, personal = true, version = 12)
            assertEquals(0L, index.counts().personal)
            index.observe("removed", 20, personal = true)
            index.observe("removed", 20, personal = false, version = 21, removed = true)
            index.observe("removed", 20, personal = true, version = 20)
            assertEquals(0L, index.counts().ordinary); assertEquals(0L, index.counts().personal)
        }
    }
    @Test fun largeReadProgressRemainsBoundedAndUnknownUntilItsPendingCleanupCompletes() {
        val rows = Rows()
        rows.transaction {
            val index = SpaceAttentionIndex(this, "channel-a")
            repeat(230) { index.observe("message-$it", it.toLong(), personal = it % 2 == 0) }
            val before = rows.readRows
            index.markRead(null, 229)
            assertTrue("A read must not scan the whole conversation", rows.readRows - before <= 100)
            var counts = index.counts()
            repeat(10) { if (counts.personal == null) counts = index.counts() }
            assertEquals(0L, counts.ordinary); assertEquals(0L, counts.personal)
            index.observe("later", 230, personal = true)
            assertEquals(1L, index.counts().personal)
            index.markRead(null, 1)
            assertEquals(1L, index.counts().personal)
            val other = SpaceAttentionIndex(this, "channel-b")
            assertEquals(0L, other.counts().personal)
            other.observe("later", 230, personal = false)
            assertEquals(1L, other.counts().ordinary); assertEquals(0L, other.counts().personal)
            assertEquals(1L, index.counts().personal)
        }
    }
    private class Rows : SpaceClientStore {
        var readRows = 0
        private var data = sortedMapOf<String, ByteArray>()
        override fun <T> transaction(action: SpaceClientStore.Transaction.() -> T): T {
            val candidate = data.mapValues { it.value.copyOf() }.toSortedMap()
            val result = action(object : SpaceClientStore.Transaction {
                override fun get(key: String) = candidate[key]?.copyOf()
                override fun put(key: String, value: ByteArray) { candidate[key] = value.copyOf() }
                override fun remove(key: String) { candidate.remove(key) }
                override fun page(prefix: String, cursor: String, limit: Int): List<SpaceClientStore.Entry> =
                    candidate.entries.asSequence().filter { it.key.startsWith(prefix) && it.key >= cursor }.take(limit)
                        .map { SpaceClientStore.Entry(it.key, it.value.copyOf()) }.toList().also { readRows += it.size }
            })
            data = candidate
            return result
        }
    }
}
