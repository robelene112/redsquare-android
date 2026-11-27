package nl.davidengelen.redsquare.spaces

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness

/** Disposable protected index. Call only inside the receiving account's store
 * transaction, after authenticating the actual message and its current version.
 * Read cursors are separate for the channel and every thread. No plaintext is kept. */
internal class SpaceAttentionIndex(private val store: SpaceClientStore.Transaction, private val namespace: String) {
    data class Counts(val ordinary: Long?, val personal: Long?)
    private data class Record(val id: String, val position: Long, val scope: String, val version: Long,
        val personal: Boolean?, val removed: Boolean, val own: Boolean, val unread: Boolean)
    private val prefix = "$namespace/attention/"
    private fun scope(thread: String?) = thread ?: "root"
    private fun row(id: String) = "${prefix}records/$id"
    private fun missing(id: String) = "${prefix}missing/$id"
    private fun cursor(scope: String) = "${prefix}read/$scope"
    private fun pending(scope: String) = "${prefix}pending-read/$scope"
    private fun unreadPrefix(scope: String) = "${prefix}unread/$scope/"
    private fun unread(record: Record) = unreadPrefix(record.scope) + record.position.toString().padStart(20, '0') + "/" + record.id
    private fun readPosition(scope: String) = store.get(cursor(scope))?.toString(Charsets.UTF_8)?.toLong() ?: -1L

    fun observe(id: String, position: Long, thread: String? = null, personal: Boolean?, version: Long = position,
        removed: Boolean = false, ownMessage: Boolean = false) {
        require(id.matches(Regex("[A-Za-z0-9-]{1,128}")) && position >= 0 && version >= position)
        require(thread == null || thread != "root" && thread.matches(Regex("[A-Za-z0-9-]{1,128}")))
        val target = scope(thread)
        val previous = store.get(row(id))?.let(::record)
        if (previous != null) {
            require(previous.position == position && previous.scope == target && previous.own == ownMessage)
            if (previous.version > version || previous.version == version && previous.personal != null && personal == null) return
            if (previous.version == version && previous.personal != null && personal != null)
                require(previous.personal == personal && previous.removed == removed)
        }
        val current = Record(id, position, target, version, personal, removed, ownMessage,
            !removed && !ownMessage && position > readPosition(target))
        previous?.let { adjust(it, -1); store.remove(unread(it)) }
        adjust(current, 1)
        put(current)
        if (current.unread) store.put(unread(current), id.toByteArray(Charsets.UTF_8))
        if (current.unread && personal == null) store.put(missing(id), byteArrayOf(1)) else store.remove(missing(id))
    }

    /** The caller has actually displayed this accepted position. Return true
     * when the cursor advances or a pending batch of read records is cleaned. */
    fun markRead(thread: String?, through: Long): Boolean {
        require(through >= 0)
        val target = scope(thread)
        if (through > readPosition(target)) store.put(cursor(target), through.toString().toByteArray(Charsets.UTF_8))
        else if (store.get(pending(target)) == null) return false
        clean(target)
        return true
    }
    fun counts(): Counts {
        store.page("${prefix}pending-read/", limit = 1).firstOrNull()?.let { clean(it.key.substringAfterLast('/')) }
        val unknown = store.page("${prefix}pending-read/", limit = 1).isNotEmpty()
        val totals = totals()
        return Counts(if (unknown) null else totals[0], if (unknown || totals[2] > 0) null else totals[1])
    }
    fun unavailable(limit: Int = 16, after: String? = null): List<String> {
        require(limit in 1..50)
        val start = "${prefix}missing/"
        var rows = store.page(start, if (after == null) start else start + after + '\u0000', limit)
        if (rows.isEmpty() && after != null) rows = store.page(start, limit = limit)
        return rows.map { it.key.substringAfterLast('/') }
    }
    private fun clean(target: String) {
        val through = readPosition(target)
        for (entry in store.page(unreadPrefix(target), limit = 50)) {
            val saved = record(requireNotNull(store.get(row(entry.value.toString(Charsets.UTF_8)))))
            if (saved.position > through) break
            if (saved.unread) { adjust(saved, -1); put(saved.copy(unread = false)) }
            store.remove(entry.key); store.remove(missing(saved.id))
        }
        val remaining = store.page(unreadPrefix(target), limit = 1).firstOrNull()?.let {
            record(requireNotNull(store.get(row(it.value.toString(Charsets.UTF_8))))).position <= through
        } == true
        if (remaining) store.put(pending(target), byteArrayOf(1)) else store.remove(pending(target))
    }
    private fun totals(): LongArray = store.get("${prefix}totals")?.toString(Charsets.UTF_8)?.split(',')?.let { values ->
        require(values.size == 3); values.map { it.toLong().also { count -> require(count >= 0) } }.toLongArray()
    } ?: longArrayOf(0, 0, 0)
    private fun adjust(value: Record, delta: Long) {
        if (!value.unread) return
        val totals = totals()
        totals[0] = Math.addExact(totals[0], delta)
        if (value.personal == true) totals[1] = Math.addExact(totals[1], delta)
        if (value.personal == null) totals[2] = Math.addExact(totals[2], delta)
        require(totals.all { it >= 0 })
        store.put("${prefix}totals", totals.joinToString(",").toByteArray(Charsets.UTF_8))
    }
    private fun put(value: Record) {
        store.put(row(value.id), JsonObject().apply {
            addProperty("id", value.id); addProperty("position", value.position); addProperty("scope", value.scope)
            addProperty("version", value.version); addProperty("personal", value.personal)
            addProperty("removed", value.removed); addProperty("own", value.own); addProperty("unread", value.unread)
        }.toString().toByteArray(Charsets.UTF_8))
    }
    private fun record(bytes: ByteArray): Record {
        val data = GSON.fromJson(bytes.toString(Charsets.UTF_8), JsonObject::class.java)
        return Record(data.get("id").asString, data.get("position").asLong, data.get("scope").asString, data.get("version").asLong,
            data.get("personal")?.takeUnless { it.isJsonNull }?.asBoolean, data.get("removed").asBoolean, data.get("own").asBoolean, data.get("unread").asBoolean)
    }
    private companion object { val GSON = GsonBuilder().setStrictness(Strictness.STRICT).create() }
}
