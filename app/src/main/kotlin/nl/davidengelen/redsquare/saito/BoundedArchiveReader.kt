package nl.davidengelen.redsquare.saito

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.Closeable

/** Read policy belongs to the reader's selected wallet/node, never a different endpoint. */
internal class ArchiveReadPolicy {
    private val limits = mutableMapOf<String, Int>()
    private val unavailableContinuations = mutableSetOf<String>()
    private fun key(criteria: JsonObject) = criteria.deepCopy().apply {
        remove("limit"); remove("created_earlier_than")
    }.toString()
    @Synchronized fun limit(criteria: JsonObject): Int = minOf(criteria.get("limit").asInt, limits[key(criteria)] ?: 100)
    @Synchronized fun reduce(criteria: JsonObject, limit: Int) { limits[key(criteria)] = limit }
    @Synchronized fun adapted(criteria: JsonObject) = limits.containsKey(key(criteria))
    @Synchronized fun canContinue(criteria: JsonObject) = key(criteria) !in unavailableContinuations
    @Synchronized fun markUnavailable(criteria: JsonObject) { unavailableContinuations.add(key(criteria)) }
    @Synchronized fun retryContinuations() { unavailableContinuations.clear() }
}

/** A tie is drained before advancing time. Old Archive has no offset or composite cursor.
 * Its exact timestamp queries order by unique archive ID; bounded exact-ID reads fill
 * the interval between its lowest/highest batches. Sparse IDs cost reads, never gaps.
 */
internal sealed interface ArchiveReadCursor {
    val timestamp: Long
    data class Before(override val timestamp: Long) : ArchiveReadCursor
    data class High(override val timestamp: Long) : ArchiveReadCursor
    data class Low(override val timestamp: Long, val lastInteriorId: Long) : ArchiveReadCursor
    data class Interior(override val timestamp: Long, val nextId: Long, val lastId: Long) : ArchiveReadCursor
}

internal class BoundedArchiveReader(private val wallet: SaitoWallet, private val endpoint: String,
    private val policy: ArchiveReadPolicy) : Closeable {
    var peer = SaitoPeer(wallet, endpoint)
        private set
    data class Page(val response: String, val next: ArchiveReadCursor?, val adapted: Boolean,
        val pagingUnavailable: Boolean = false)
    private data class Read(val response: String, val limit: Int, val single: Boolean = false)

    suspend fun next(criteria: JsonObject, cursor: ArchiveReadCursor? = null): Page {
        val position = cursor ?: ArchiveReadCursor.Before(criteria.get("created_earlier_than").asLong)
        val query = criteria.deepCopy()
        when (position) {
            is ArchiveReadCursor.Before -> query.addProperty("created_earlier_than", position.timestamp)
            else -> {
                query.remove("created_earlier_than")
                query.addProperty("created_at", position.timestamp)
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
        var unavailable = false
        val next = when (position) {
            is ArchiveReadCursor.Before -> {
                if (!read.single && rows.size() < read.limit) null
                else {
                    val oldest = rows.mapNotNull { row ->
                        try { row.asJsonObject.get("created_at").asString.toLongOrNull()?.takeIf { it >= 0 && it < position.timestamp } }
                        catch (_: Exception) { null }
                    }.minOrNull()
                    if (oldest == null) { unavailable = true; null }
                    else if (adapted && !policy.canContinue(criteria)) { unavailable = true; null }
                    else if (adapted) ArchiveReadCursor.High(oldest) else older(oldest)
                }
            }
            is ArchiveReadCursor.High -> {
                if (rows.isEmpty) older(position.timestamp)
                else ids(rows).minOrNull()?.let { ArchiveReadCursor.Low(position.timestamp, it - 1) }
                    ?: run { unavailable = true; null }
            }
            is ArchiveReadCursor.Low -> {
                if (rows.isEmpty) older(position.timestamp)
                else {
                    val last = ids(rows).maxOrNull()
                    if (last == null) { unavailable = true; null }
                    else if (last >= position.lastInteriorId) older(position.timestamp)
                    else ArchiveReadCursor.Interior(position.timestamp, last + 1, position.lastInteriorId)
                }
            }
            is ArchiveReadCursor.Interior -> {
                require(rows.size() <= 1)
                if (position.nextId == position.lastId) older(position.timestamp)
                else position.copy(nextId = position.nextId + 1)
            }
        }
        if (unavailable) policy.markUnavailable(criteria)
        return Page(read.response, next, adapted, unavailable)
    }

    private fun older(timestamp: Long) = if (timestamp > 0) ArchiveReadCursor.Before(timestamp) else null
    private fun ids(rows: JsonArray): List<Long> = rows.mapNotNull { row ->
        try { row.asJsonObject.get("id")?.asString?.toLongOrNull()?.takeIf { it >= 0 } }
        catch (_: Exception) { null }
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
                    limit = 10
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
