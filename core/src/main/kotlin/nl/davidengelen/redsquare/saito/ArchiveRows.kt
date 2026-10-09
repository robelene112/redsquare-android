package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.Strictness

internal data class ArchiveRows<T>(val entries: List<T>, val oldestCreatedAt: Long?, val rowCount: Int, val ignored: Int)

/** Archive cursors describe available rows, including rows rejected by signed-content readers. */
internal fun <T> readArchiveRows(response: String, decode: (String) -> T,
    accepts: (T) -> Boolean, identity: (T) -> String): ArchiveRows<T> {
    require(response.toByteArray(Charsets.UTF_8).size <= 16 * 1024 * 1024)
    val rows = archiveGson.fromJson(response, JsonArray::class.java)
        ?: throw IllegalArgumentException("Missing archive response")
    require(rows.size() <= 100)
    var oldest: Long? = null
    var ignored = 0
    val entries = rows.mapNotNull { row ->
        try {
            val item = row.asJsonObject
            val cursor = item.get("created_at")?.asString?.toLongOrNull()?.takeIf { it >= 0 }
            if (cursor != null) oldest = minOf(oldest ?: cursor, cursor)
            val envelope = item.get("tx")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            require(envelope?.isString == true)
            decode(envelope.asString).takeIf(accepts).also { if (it == null) ignored++ }
        } catch (_: Exception) { ignored++; null }
    }
    return ArchiveRows(entries.distinctBy(identity), oldest, rows.size(), ignored)
}

private val archiveGson = GsonBuilder().setStrictness(Strictness.STRICT).create()
