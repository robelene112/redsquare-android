package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.spaces.SpaceAdminCursor
import nl.davidengelen.redsquare.spaces.SpaceAdminEvent
import nl.davidengelen.redsquare.spaces.SpaceJournal
import nl.davidengelen.redsquare.spaces.SpaceMessageReport
import nl.davidengelen.redsquare.spaces.SpaceRole
import java.sql.Connection
import java.sql.ResultSet

internal class SpaceModerationRoutes(private val database: Connection) {
    fun createSchema() {
        database.createStatement().use { sql ->
            sql.execute("""CREATE TABLE space_reports (
                position INTEGER PRIMARY KEY AUTOINCREMENT, space_id TEXT NOT NULL,
                report_id TEXT NOT NULL UNIQUE, channel_id TEXT NOT NULL, message_id TEXT NOT NULL,
                reporter TEXT NOT NULL, reason TEXT NOT NULL, received_at INTEGER NOT NULL CHECK(received_at>=0),
                UNIQUE(space_id,channel_id,message_id,reporter))""")
            sql.execute("CREATE INDEX space_reports_page ON space_reports(space_id,position)")
            sql.execute("CREATE INDEX controls_admin_page ON controls(space_id,accepted_at,event_id)")
            sql.execute("CREATE INDEX joins_admin_page ON joins(space_id,accepted_at,event_id)")
            sql.execute("CREATE INDEX records_admin_page ON records(space_id,accepted_at,event_id) WHERE kind IN ('message-delete','message-pin')")
        }
    }

    fun ready() {
        database.createStatement().use { sql ->
            sql.executeQuery("SELECT position,reporter,reason FROM space_reports LIMIT 0").close()
        }
    }

    fun report(space: String, report: SpaceMessageReport, journal: SpaceJournal): SpaceMessageReport? {
        val state = journal.snapshot
        if (state.dissolved || state.protectedChat != null || report.reporter !in state.members || !journal.canAccessChannel(report.reporter, report.channel))
            return null
        val readable = database.prepareStatement(
            "SELECT 1 FROM records WHERE space_id=? AND channel_id=? AND event_id=? AND kind IN ('message','thread-reply')"
        ).use { sql ->
            sql.setString(1, space)
            sql.setString(2, report.channel)
            sql.setString(3, report.message)
            sql.executeQuery().use(ResultSet::next)
        }
        if (!readable) return null
        database.prepareStatement("""INSERT INTO space_reports(space_id,report_id,channel_id,message_id,reporter,reason,received_at)
            VALUES(?,?,?,?,?,?,?) ON CONFLICT(space_id,channel_id,message_id,reporter) DO NOTHING""").use { sql ->
            sql.setString(1, space)
            sql.setString(2, report.id)
            sql.setString(3, report.channel)
            sql.setString(4, report.message)
            sql.setString(5, report.reporter)
            sql.setString(6, report.reason)
            sql.setLong(7, report.receivedAt)
            sql.executeUpdate()
        }
        return database.prepareStatement(
            "SELECT * FROM space_reports WHERE space_id=? AND channel_id=? AND message_id=? AND reporter=?"
        ).use { sql ->
            sql.setString(1, space)
            sql.setString(2, report.channel)
            sql.setString(3, report.message)
            sql.setString(4, report.reporter)
            sql.executeQuery().use { rows -> check(rows.next()); readReport(rows) }
        }
    }

    fun reportPage(reply: JsonObject, journal: SpaceJournal, space: String, actor: String, before: Long): Boolean {
        if (!canReview(journal, actor)) return false
        val items = JsonArray()
        var cursor = 0L
        var more = false
        database.prepareStatement(
            "SELECT * FROM space_reports WHERE space_id=? AND position<? ORDER BY position DESC LIMIT ?"
        ).use { sql ->
            sql.setString(1, space)
            sql.setLong(2, before)
            sql.setInt(3, PAGE_SIZE + 1)
            sql.executeQuery().use { rows ->
                while (rows.next()) {
                    if (items.size() == PAGE_SIZE) {
                        more = true
                        break
                    }
                    items.add(readReport(rows).json())
                    cursor = rows.getLong("position")
                }
            }
        }
        reply.addProperty("request", "page")
        reply.add("items", items)
        reply.addProperty("next_cursor", if (more) cursor else 0L)
        return true
    }

    fun auditPage(reply: JsonObject, journal: SpaceJournal, space: String, actor: String, before: SpaceAdminCursor): Boolean {
        if (!canReview(journal, actor)) return false
        val rows = database.prepareStatement("""
            SELECT original,actor,accepted_at,event_id FROM controls WHERE space_id=? AND (accepted_at,event_id)<(?,?)
            UNION ALL
            SELECT original,actor,accepted_at,event_id FROM joins WHERE space_id=? AND (accepted_at,event_id)<(?,?)
            UNION ALL
            SELECT event.original,event.actor,event.accepted_at,event.event_id FROM records event
            WHERE event.space_id=? AND (event.accepted_at,event.event_id)<(?,?) AND event.kind IN ('message-delete','message-pin')
            AND (event.kind='message-pin' OR EXISTS (
                SELECT 1 FROM records target WHERE target.space_id=event.space_id AND target.event_id=event.target_message_id AND target.actor<>event.actor))
            ORDER BY accepted_at DESC,event_id DESC LIMIT ?
        """.trimIndent()).use { sql ->
            for (offset in listOf(1, 4, 7)) {
                sql.setString(offset, space)
                sql.setLong(offset + 1, before.at)
                sql.setString(offset + 2, before.event)
            }
            sql.setInt(10, PAGE_SIZE + 1)
            sql.executeQuery().use { result -> buildList {
                while (result.next()) {
                    val original = SaitoEnvelope.decode(result.getString("original"))
                    val cursor = SpaceAdminCursor(result.getLong("accepted_at"), result.getString("event_id"))
                    check(cursor.event == original.id && original.author == result.getString("actor"))
                    val body = original.body
                    fun metadata(field: String) = body.get(field)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                    val action = when (val kind = metadata("content_kind") ?: requireNotNull(metadata("kind"))) {
                        "archive-channel" -> if (body.get("archived").asBoolean) "archived-channel" else "reopened-channel"
                        "set-role" -> if (metadata("role") == "admin") "made-admin" else "removed-admin"
                        "set-write-restriction" -> if (body.get("expires_at").asLong > 0) "paused-writing" else "resumed-writing"
                        else -> kind
                    }
                    add(SpaceAdminEvent(cursor, original.author, action, metadata("channel_id"), metadata("member")))
                }
            } }
        }
        val page = rows.take(PAGE_SIZE)
        val next = page.lastOrNull()?.cursor?.takeIf { rows.size > PAGE_SIZE }
        reply.addProperty("request", "page")
        reply.add("items", JsonArray().apply { page.forEach { add(it.json()) } })
        reply.addProperty("next_cursor", next?.at ?: 0)
        next?.let { reply.addProperty("next_event", it.event) }
        return true
    }

    private fun canReview(journal: SpaceJournal, actor: String): Boolean =
        !journal.snapshot.dissolved && journal.snapshot.protectedChat == null &&
            journal.snapshot.members[actor] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN)

    private fun readReport(rows: ResultSet) = SpaceMessageReport(rows.getString("report_id"),
        rows.getString("channel_id"), rows.getString("message_id"), rows.getString("reporter"),
        rows.getString("reason"), rows.getLong("received_at"))

    private companion object { const val PAGE_SIZE = 20 }
}
