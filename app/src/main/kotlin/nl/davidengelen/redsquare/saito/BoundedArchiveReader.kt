package nl.davidengelen.redsquare.saito

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.Closeable
import java.io.IOException

/** Read policy belongs to the reader's selected wallet/node, never a different endpoint. */
internal class ArchiveReadPolicy {
    private val limits = mutableMapOf<String, Int>()
    private fun key(criteria: JsonObject) = criteria.deepCopy().apply {
        remove("limit"); remove("created_earlier_than")
    }.toString()
    @Synchronized fun limit(criteria: JsonObject): Int = minOf(criteria.get("limit").asInt, limits[key(criteria)] ?: 100)
    @Synchronized fun reduce(criteria: JsonObject, limit: Int) { limits[key(criteria)] = limit }
    @Synchronized fun adapted(criteria: JsonObject) = limits.containsKey(key(criteria))
}

/** A tie is drained before advancing time. Old Archive has no offset or composite cursor.
 * Its exact timestamp queries order by unique archive ID; bounded exact-ID reads fill
 * the interval between its lowest/highest batches. Sparse IDs cost reads, never gaps.
 */
internal sealed interface ArchiveReadCursor {
    data class Before(val timestamp: Long) : ArchiveReadCursor
    data class High(val timestamp: Long) : ArchiveReadCursor
    data class Low(val timestamp: Long, val lastInteriorId: Long) : ArchiveReadCursor
    data class Interior(val timestamp: Long, val nextId: Long, val lastId: Long) : ArchiveReadCursor
}

internal class BoundedArchiveReader(private val wallet: SaitoWallet, private val endpoint: String,
    private val policy: ArchiveReadPolicy) : Closeable {
    var peer = SaitoPeer(wallet, endpoint)
        private set
    data class Page(val response: String, val next: ArchiveReadCursor?, val adapted: Boolean)
    private data class Read(val response: String, val limit: Int, val single: Boolean = false)

    suspend fun next(criteria: JsonObject, cursor: ArchiveReadCursor? = null): Page {
        val position = cursor ?: ArchiveReadCursor.Before(criteria.get("created_earlier_than").asLong)
        val query = criteria.deepCopy()
        when (position) {
            is ArchiveReadCursor.Before -> query.addProperty("created_earlier_than", position.timestamp)
            else -> {
                query.remove("created_earlier_than")
                val timestamp = when (position) {
                    is ArchiveReadCursor.High -> position.timestamp
                    is ArchiveReadCursor.Low -> position.timestamp
                    is ArchiveReadCursor.Interior -> position.timestamp
                    else -> error("Unreachable cursor")
                }
                query.addProperty("created_at", timestamp)
                when (position) {
                    is ArchiveReadCursor.Low -> query.addProperty("ascending", 1)
                    is ArchiveReadCursor.Interior -> query.addProperty("id", position.nextId)
                    else -> Unit
                }
                query.addProperty("limit", 10)
            }
        }
        val read = read(query, criteria)
        val rows = JsonParser.parseString(read.response).asJsonArray
        require(rows.size() <= 100)
        val adapted = policy.adapted(criteria)
        val next = when (position) {
            is ArchiveReadCursor.Before -> {
                if (!read.single && rows.size() < read.limit) null
                else {
                    val oldest = rows.mapNotNull { row ->
                        try { row.asJsonObject.get("created_at").asLong.takeIf { it >= 0 && it < position.timestamp } }
                        catch (_: Exception) { null }
                    }.minOrNull() ?: throw IOException("Archive continuation is unavailable")
                    if (adapted) ArchiveReadCursor.High(oldest) else older(oldest)
                }
            }
            is ArchiveReadCursor.High -> {
                if (rows.isEmpty) older(position.timestamp)
                else ArchiveReadCursor.Low(position.timestamp, ids(rows).min() - 1)
            }
            is ArchiveReadCursor.Low -> {
                if (rows.isEmpty) older(position.timestamp)
                else {
                    val last = ids(rows).max()
                    if (last >= position.lastInteriorId) older(position.timestamp)
                    else ArchiveReadCursor.Interior(position.timestamp, last + 1, position.lastInteriorId)
                }
            }
            is ArchiveReadCursor.Interior -> {
                require(rows.size() <= 1)
                if (position.nextId == position.lastId) older(position.timestamp)
                else position.copy(nextId = position.nextId + 1)
            }
        }
        return Page(read.response, next, adapted)
    }

    private fun older(timestamp: Long) = if (timestamp > 0) ArchiveReadCursor.Before(timestamp) else null
    private fun ids(rows: JsonArray): List<Long> = rows.map { row ->
        try { row.asJsonObject.get("id").asLong.also { require(it >= 0) } }
        catch (_: Exception) { throw IOException("Archive continuation is unavailable") }
    }

    private suspend fun read(query: JsonObject, base: JsonObject): Read {
        var limit = minOf(query.get("limit").asInt, policy.limit(base)).coerceAtLeast(10)
        while (true) {
            query.addProperty("limit", limit)
            try { return Read(request(query), limit) }
            catch (failure: SaitoPeer.ResponseTooLarge) {
                peer.close()
                peer = SaitoPeer(wallet, endpoint)
                if (limit > 10) {
                    limit = maxOf(10, limit / 2)
                    policy.reduce(base, limit)
                } else {
                    policy.reduce(base, 10)
                    // Old Archive clamps even limit1 to ten. An exact ID still selects
                    // one row. Routing hints cannot authorize any displayed content.
                    val hint = failure.archiveHint ?: throw failure
                    val exact = query.deepCopy().apply { addProperty("id", hint.id) }
                    val response = request(exact)
                    val rows = JsonParser.parseString(response).asJsonArray
                    require(rows.size() <= 1)
                    require(rows.all { it.asJsonObject.get("id").asLong == hint.id })
                    return Read(response, 10, single = true)
                }
            }
        }
    }

    private suspend fun request(criteria: JsonObject): String {
        val request = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
        return peer.requestArchive(request.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
    }
    override fun close() = peer.close()
}
