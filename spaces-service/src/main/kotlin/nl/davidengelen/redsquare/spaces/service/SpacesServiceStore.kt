package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonNull
import com.google.gson.JsonArray
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SignedSaitoMessage
import nl.davidengelen.redsquare.spaces.SpaceArticleContent
import nl.davidengelen.redsquare.spaces.SpaceFileAccessCheck
import nl.davidengelen.redsquare.spaces.SpaceJournal
import nl.davidengelen.redsquare.spaces.SpaceRole
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import nl.davidengelen.redsquare.spaces.SpaceSnapshot
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Clock
import java.util.Properties
import java.security.SecureRandom
import java.util.Locale

/** Server-owned signed ordering. No private community keys are accepted. */
class SpacesServiceStore(directory: Path, private val wallet: SaitoWallet, private val clock: Clock) : AutoCloseable {
    private val database: Connection
    private val fileRoutes: SpaceFileRoutes
    private data class Challenge(val caller: String, val scope: JsonObject, val expiresAt: Long)
    private val challenges = mutableMapOf<String, Challenge>()
    private data class FileJournal(val head: String?, val journal: SpaceJournal)
    private val recentFileJournals = object : LinkedHashMap<String, FileJournal>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FileJournal>?) = size > 16
    }

    /** Loopback operational metadata, not a signed write acknowledgment or a
     * promise of available disk space. The node also checks its own identity. */
    @Synchronized fun readiness(): String {
        val version = database.createStatement().use { sql ->
            sql.executeQuery("PRAGMA user_version").use { rows -> check(rows.next()); rows.getInt(1) }
        }
        check(version == 5)
        database.createStatement().use { sql ->
            sql.executeQuery("SELECT service_key FROM service_metadata WHERE id=1").use { rows ->
                check(rows.next() && rows.getString(1) == wallet.publicKey)
            }
        }
        database.createStatement().use { it.executeQuery("SELECT kind,recipient_credential,target_message_id FROM records LIMIT 0").close() }
        database.createStatement().use { it.executeQuery("SELECT space_id,listed,listing_receipt FROM public_spaces LIMIT 0").close() }
        fileRoutes.ready()
        return JsonObject().apply {
            addProperty("capability", "spaces-v1"); addProperty("service_key", wallet.publicKey)
            addProperty("schema", version); addProperty("state", "ready"); addProperty("file_hosting", false)
            add("features", JsonArray().apply { add("message-history-v1"); add("unread-v1"); add("channel-archive-v1"); add("role-management-v1"); add("public-channel-edit-v1"); add("private-channel-access-v1"); add("private-channel-edit-v1"); add("membership-management-v1"); add("ownership-management-v1"); add("category-management-v1"); add("visibility-management-v1"); add("dissolution-v1") })
        }.toString()
    }

    /** Read authority comes from the actual signer and a fresh bound challenge. */
    @Synchronized fun exchange(envelope: String): String {
        val request = SaitoEnvelope.decode(envelope)
        val body = request.body
        require(body.text("module") == "Spaces" && body.get("version").toString() == "1" &&
            request.recipients == listOf(request.author, wallet.publicKey).distinct())
        if (body.text("request") == "event") return submit(envelope)
        val requestId = body.text("request_id").also { require(it.matches(ID)) }
        val scope = body.get("scope").also { require(it != null && it.isJsonObject) }.asJsonObject.deepCopy()
        val space = scope.text("space_id").also { require(it.matches(ID)) }
        val stream = scope.text("stream").also { require(it in setOf("control", "join", "content", "keys", "invitation", "directory", "heads", "messages", "pins", "files")) }
        require(scope.keySet() == setOf("space_id", "stream", "cursor") + when (stream) {
            "messages" -> setOf("channel_id", "direction", "limit") + (if (scope.has("thread_id")) setOf("thread_id") else emptySet()) +
                (if (scope.has("pin_history")) setOf("pin_history") else emptySet()) +
                (if (scope.has("reaction_history")) setOf("reaction_history") else emptySet()) +
                (if (scope.has("reaction_cursors")) setOf("reaction_cursors") else emptySet()) +
                (if (scope.has("article_id")) setOf("article_id") else emptySet())
            "pins" -> setOf("channel_id") + (if (scope.has("through")) setOf("through") else emptySet())
            "files" -> setOf("channel_id", "access") + when (scope.text("access")) {
                "upload" -> setOf("control", "object", "sha256", "bytes")
                "download", "confirm", "remove" -> setOf("file")
                "abandon" -> setOf("object")
                "usage" -> emptySet()
                else -> throw IllegalArgumentException("Unsupported file access")
            }
            "content" -> setOf("channel_id"); "keys" -> setOf("channel_id", "recipient_credential")
            "invitation" -> setOf("invite_id"); "directory" -> setOf("query"); "heads" -> setOf("positions"); else -> emptySet()
        })
        if (stream == "directory") require(space == "0".repeat(64) && scope.text("query").let { it.codePointCount(0, it.length) <= 128 })
        val channel = if (stream in setOf("content", "keys", "messages", "pins", "files")) scope.text("channel_id").also { require(it.matches(ID)) } else null
        val recipient = if (stream == "keys") scope.text("recipient_credential") else null
        val cursor = scope.number("cursor")
        if (stream == "files") require(cursor == 0L)
        if (stream == "messages") require(scope.text("direction") in setOf("before", "after") && scope.number("limit") in 1..50)
        val article = if (stream == "messages" && scope.has("article_id")) scope.text("article_id").also {
            require(it.matches(Regex("[0-9a-f]{128}")) && cursor == Long.MAX_VALUE && scope.text("direction") == "before" &&
                scope.number("limit") == 1L && !scope.has("thread_id") && !scope.has("reaction_history") && !scope.has("pin_history"))
        } else null
        if (stream == "messages" && scope.has("pin_history")) require(scope.get("pin_history").let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean })
        if (stream == "messages" && scope.has("reaction_history")) require(scope.get("reaction_history").let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean })
        if (stream == "messages" && scope.has("reaction_cursors")) {
            require(scope.get("reaction_history")?.asBoolean == true)
            val cursors = scope.getAsJsonObject("reaction_cursors"); require(cursors.size() <= 50)
            for ((message, value) in cursors.entrySet()) {
                require(message.matches(Regex("[0-9a-f]{128}")) && value.isJsonObject)
                val progress = value.asJsonObject
                require(progress.keySet() == setOf("after") + if (progress.has("through")) setOf("through") else emptySet())
                require(progress.number("after") >= 0)
                if (progress.has("through")) require(progress.number("through") >= progress.number("after"))
            }
        }
        if (stream == "messages" && scope.has("thread_id")) require(scope.text("thread_id").matches(Regex("[0-9a-f]{128}")))
        if (stream == "heads") {
            require(cursor == 0L)
            val positions = scope.getAsJsonObject("positions")
            require(positions.size() in 1..50)
            positions.keySet().forEach { require(it.matches(ID)); positions.number(it) }
        }
        val now = clock.millis().also { require(it >= 0) }
        challenges.entries.removeIf { it.value.expiresAt <= now }
        val reply = JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("version", 1); addProperty("request_id", requestId)
            addProperty("caller", request.author); add("scope", scope.deepCopy())
        }
        when (body.text("request")) {
            "challenge" -> {
                check(challenges.size < 2048) { "Spaces challenge capacity reached" }
                val nonce = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                val expiry = Math.addExact(now, 60_000)
                challenges[nonce] = Challenge(request.author, scope, expiry)
                reply.addProperty("request", "challenge"); reply.addProperty("nonce", nonce)
                reply.addProperty("expires_at", expiry)
            }
            "read" -> {
                val nonce = body.text("nonce")
                val challenge = requireNotNull(challenges[nonce]) { "Spaces read needs a fresh challenge" }
                require(challenge.caller == request.author && challenge.scope == scope)
                challenges.remove(nonce)
                atomic {
                    if (stream == "directory") directoryPage(reply, scope.text("query"), cursor)
                    else {
                    val journal = journal(space)
                    // A former member may authenticate their own departure.
                    // This does not authorize later private controls or content,
                    // and banning a never-member grants no read authority.
                    val departureEnd = journal.snapshot.membershipReadThrough[request.author]?.takeIf {
                        stream == "control" && request.author !in journal.snapshot.members && journal.snapshot.visibility == SpaceVisibility.PRIVATE
                    }
                    fun denied(reason: String) {
                        reply.addProperty("request", "denied"); reply.addProperty("reason", reason)
                    }
                    if (stream == "invitation") {
                        val invitationId = scope.text("invite_id")
                        val invitation = journal.snapshot.invitations[invitationId]
                        if (invitation == null || !invitation.accepts(request.author)) { denied("unavailable"); return@atomic }
                        val proof = "SaitoSpacesPreview/v1:${wallet.publicKey}:$space:$invitationId:${request.author}:$nonce"
                        val validProof = runCatching {
                            SaitoWallet.verify(proof.toByteArray(Charsets.UTF_8), body.text("invite_proof"), invitation.key)
                        }.getOrDefault(false)
                        if (!validProof) { denied("unavailable"); return@atomic }
                        if (invitation.expiresAt <= now) { denied("expired"); return@atomic }
                    } else if (stream == "join") {
                        if (!journal.canApproveDeviceRequests(request.author)) {
                            denied("unavailable"); return@atomic
                        }
                    } else if (!(journal.snapshot.visibility == SpaceVisibility.PUBLIC && stream == "control") &&
                        !(stream in setOf("content", "messages", "pins") && journal.snapshot.channels[channel]?.visibility == SpaceVisibility.PUBLIC) &&
                        request.author !in journal.snapshot.members && departureEnd == null) { denied("unavailable"); return@atomic }
                    if (channel != null) {
                        val target = journal.snapshot.channels[channel]
                        if (target == null || target.visibility != SpaceVisibility.PUBLIC && (!journal.canAccessChannel(request.author, channel) ||
                            target.devices.keys.none { it.split(':')[1] == request.author })) {
                            denied("unavailable"); return@atomic
                        }
                        if (recipient != null && (recipient !in target.devices || recipient.split(':')[1] != request.author)) {
                            denied("unavailable"); return@atomic
                        }
                    }
                    if (stream == "heads") {
                        val positions = scope.getAsJsonObject("positions")
                        val allowed = positions.keySet().all { id -> journal.snapshot.channels[id]?.let { record ->
                            record.visibility == SpaceVisibility.PUBLIC || journal.canAccessChannel(request.author, id) && record.devices.keys.any { it.split(':')[1] == request.author }
                        } == true }
                        if (!allowed) { denied("unavailable"); return@atomic }
                        headPage(reply, space, request.author, positions)
                        return@atomic
                    }
                    if (stream == "pins") {
                        pinPage(reply, space, requireNotNull(channel), request.author, cursor, scope.get("through")?.let { scope.number("through") })
                        return@atomic
                    }
                    if (stream == "files") {
                        try {
                            fileRoutes.permit(reply, scope, request.author, journal, now)
                            fileRoutes.recordPermission(reply, scope, request.author, journal, now)
                            reply.add("items", JsonArray())
                            reply.addProperty("next_cursor", 0)
                        } catch (_: IllegalArgumentException) {
                            denied("unavailable")
                        }
                        return@atomic
                    }
                    if (stream == "messages") {
                        val thread = scope.get("thread_id")?.asString
                        if (thread != null) {
                            val known = database.prepareStatement("SELECT 1 FROM records WHERE space_id=? AND channel_id=? AND kind='message' AND event_id=?").use { sql ->
                                sql.setString(1, space); sql.setString(2, channel); sql.setString(3, thread)
                                sql.executeQuery().use { it.next() }
                            }
                            if (!known) { denied("unavailable"); return@atomic }
                        }
                        val record = journal.snapshot.channels.getValue(requireNotNull(channel))
                        if (article != null && record.rootContentKind != "article") {
                            denied("unavailable"); return@atomic
                        }
                        messagePage(reply, space, channel, request.author, cursor, scope.text("direction"), scope.number("limit").toInt(), thread,
                            scope.get("reaction_history")?.asBoolean == true, scope.getAsJsonObject("reaction_cursors"),
                            scope.get("pin_history")?.asBoolean == true, record.rootContentKind, article)
                        return@atomic
                    }
                    val items = JsonArray()
                    var bytes = 0
                    var next = cursor
                    val table = when (stream) { "join" -> "joins"; "content", "keys" -> "records"; else -> "controls" }
                    database.prepareStatement("SELECT position,receipt FROM $table WHERE space_id=? AND position>=?" +
                        (if (channel != null) " AND channel_id=?" else "") +
                        (if (recipient != null) " AND recipient_credential=? AND kind IN ('history','history-keys')" else "") +
                        " ORDER BY position LIMIT 50").use { sql ->
                        sql.setString(1, space); sql.setLong(2, cursor)
                        if (channel != null) sql.setString(3, channel)
                        if (recipient != null) sql.setString(4, recipient)
                        sql.executeQuery().use { rows ->
                            while (rows.next()) {
                                if (departureEnd != null && rows.getLong(1) > departureEnd) break
                                val value = rows.getString(2)
                                if (stream == "join") {
                                    val original = SaitoEnvelope.decode(SaitoEnvelope.decode(value).body.text("event"))
                                    val allowed = journal.snapshot.members[request.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN) ||
                                        original.body.text("kind") == "replace-device"
                                    if (!allowed || !journal.pendingJoinIsCurrent(original, now)) {
                                        next = Math.addExact(rows.getLong(1), 1)
                                        continue
                                    }
                                    journal.verifyJoin(originalEnvelope(value), now)
                                }
                                val size = value.toByteArray(Charsets.UTF_8).size
                                check(size <= PAGE_BYTES) { "Spaces receipt exceeds the page capacity" }
                                if (bytes + size > PAGE_BYTES) break
                                items.add(value); bytes += size
                                next = Math.addExact(rows.getLong(1), 1)
                            }
                        }
                    }
                    reply.addProperty("request", "page"); reply.addProperty("nonce", nonce)
                    reply.add("items", items); reply.addProperty("next_cursor", next)
                    if (stream in setOf("keys", "content")) reply.addProperty("content_end", nextPosition("records", space, requireNotNull(channel)))
                    }
                }
                if (!reply.has("request")) reply.addProperty("request", "page")
                reply.addProperty("nonce", nonce)
                reply.addProperty("message_history", true)
                reply.addProperty("message_reactions", true)
                reply.addProperty("message_pins", true)
                reply.addProperty("addressed_invitations", true)
                reply.addProperty("device_replacement_v1", true)
                reply.addProperty("space_articles_v1", true)
                reply.addProperty("space_write_restrictions_v1", true)
                reply.addProperty("space_file_storage_v1", true)
                reply.addProperty("protected_chat_v1", true)
            }
            else -> throw IllegalArgumentException("Unsupported Spaces read request")
        }
        return SaitoTransaction.createSpaceReply(wallet, reply, now, request.author).envelope()
    }

    @Synchronized
    fun checkFileAccess(request: String): String {
        val now = clock.millis()
        val check = SpaceFileAccessCheck.read(request, wallet.publicKey, now)
        return atomic {
            check.reply(wallet, fileRoutes.stillAllowed(check, fileJournal(check.permission.space), now), now)
        }
    }

    /** Message slots, independent of the number of immutable mutations. The
     * original receipt and its latest edit remain independently authenticated. */
    private fun messagePage(reply: JsonObject, space: String, channel: String, reader: String, cursor: Long, direction: String, limit: Int, thread: String?, reactions: Boolean, reactionCursors: JsonObject?, pins: Boolean, rootContentKind: String = "message", article: String? = null) {
        val before = direction == "before"
        val items = mutableListOf<JsonObject>()
        var size = 0
        var more = false
        database.prepareStatement("SELECT position,event_id,receipt FROM records WHERE space_id=? AND channel_id=? AND kind=?" +
            (if (thread == null) "" else " AND target_message_id=?") +
            (if (article == null) "" else " AND event_id=?") + " AND position" +
            (if (before) "<? ORDER BY position DESC" else ">=? ORDER BY position") + " LIMIT ?").use { sql ->
            sql.setString(1, space); sql.setString(2, channel); sql.setString(3, if (thread == null) rootContentKind else "thread-reply")
            var parameter = 4
            if (thread != null) sql.setString(parameter++, thread)
            if (article != null) sql.setString(parameter++, article)
            sql.setLong(parameter++, cursor); sql.setInt(parameter, limit + 1)
            sql.executeQuery().use { rows ->
                while (rows.next()) {
                    val item = JsonObject().apply { addProperty("message", rows.getString(3)) }
                    if (thread == null && rootContentKind == "message") database.prepareStatement("SELECT COUNT(*) FROM records WHERE space_id=? AND channel_id=? AND kind='thread-reply' AND target_message_id=?").use { count ->
                        count.setString(1, space); count.setString(2, channel); count.setString(3, rows.getString(2))
                        count.executeQuery().use { summary -> check(summary.next()); item.addProperty("reply_count", summary.getLong(1)) }
                    }
                    database.prepareStatement("SELECT receipt FROM records WHERE space_id=? AND channel_id=? AND kind='message-edit' AND target_message_id=? ORDER BY position DESC LIMIT 1").use { edit ->
                        edit.setString(1, space); edit.setString(2, channel); edit.setString(3, rows.getString(2))
                        edit.executeQuery().use { versions -> if (versions.next()) item.addProperty("edit", versions.getString(1)) }
                    }
                    database.prepareStatement("SELECT receipt FROM records WHERE space_id=? AND channel_id=? AND kind='message-delete' AND target_message_id=? ORDER BY position DESC LIMIT 1").use { deletion ->
                        deletion.setString(1, space); deletion.setString(2, channel); deletion.setString(3, rows.getString(2))
                        deletion.executeQuery().use { versions -> if (versions.next()) item.addProperty("deletion", versions.getString(1)) }
                    }
                    if (pins) database.prepareStatement("SELECT receipt FROM records WHERE space_id=? AND channel_id=? AND kind='message-pin' AND target_message_id=? ORDER BY position DESC LIMIT 1").use { pin ->
                        pin.setString(1, space); pin.setString(2, channel); pin.setString(3, rows.getString(2))
                        pin.executeQuery().use { versions -> if (versions.next()) item.addProperty("pin", versions.getString(1)) }
                    }
                    if (reactions) {
                        val progress = reactionCursors?.getAsJsonObject(rows.getString(2))
                        val after = progress?.number("after") ?: 0L
                        val latest = database.prepareStatement("SELECT COALESCE(MAX(position)+1,0) FROM records WHERE space_id=? AND channel_id=? AND kind='message-reaction' AND target_message_id=?").use { query ->
                            query.setString(1, space); query.setString(2, channel); query.setString(3, rows.getString(2))
                            query.executeQuery().use { values -> check(values.next()); values.getLong(1) }
                        }
                        val through = progress?.get("through")?.asLong ?: latest
                        require(after <= through && through <= latest)
                        val choices = JsonArray()
                        var complete = true
                        var next = after
                        database.prepareStatement("SELECT r.receipt,r.position FROM records r WHERE r.space_id=? AND r.channel_id=? AND r.kind='message-reaction' AND r.target_message_id=? AND r.position>=? AND r.position<? " +
                            "AND NOT EXISTS (SELECT 1 FROM records n WHERE n.space_id=r.space_id AND n.channel_id=r.channel_id AND n.kind=r.kind AND n.target_message_id=r.target_message_id AND n.actor=r.actor AND n.position>r.position AND n.position<?) ORDER BY r.position LIMIT 51").use { query ->
                            query.setString(1, space); query.setString(2, channel); query.setString(3, rows.getString(2))
                            query.setLong(4, after); query.setLong(5, through); query.setLong(6, through)
                            query.executeQuery().use { actors -> while (actors.next()) {
                                if (choices.size() == 50) { complete = false; break }
                                choices.add(actors.getString(1)); next = Math.addExact(actors.getLong(2), 1)
                            } }
                        }
                        // The caller's complete desired selection is independent of
                        // group pagination, so a cold client cannot overwrite choices
                        // that happen to fall beyond this bounded actor page.
                        val own = database.prepareStatement("SELECT receipt FROM records WHERE space_id=? AND channel_id=? AND kind='message-reaction' AND target_message_id=? AND actor=? AND position<? ORDER BY position DESC LIMIT 1").use { query ->
                            query.setString(1, space); query.setString(2, channel); query.setString(3, rows.getString(2))
                            query.setString(4, reader); query.setLong(5, through)
                            query.executeQuery().use { actor -> if (actor.next()) actor.getString(1) else null }
                        }
                        item.add("own_reaction", own?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
                        item.add("reactions", choices); item.addProperty("reactions_complete", complete)
                        item.addProperty("reaction_version", through); item.addProperty("reaction_next_cursor", if (complete) through else next)
                    }
                    val bytes = item.toString().toByteArray(Charsets.UTF_8).size
                    check(bytes <= PAGE_BYTES) { "Spaces message exceeds the page capacity" }
                    if (items.size == limit || size + bytes > PAGE_BYTES) { more = true; break }
                    items.add(item); size += bytes
                }
            }
        }
        if (before) items.reverse()
        reply.add("items", JsonArray().apply { items.forEach(::add) })
        reply.addProperty("next_cursor", items.lastOrNull()?.let { Math.addExact(SaitoEnvelope.decode(it.text("message")).body.number("position"), 1) } ?: cursor)
        reply.addProperty("has_more", more)
        reply.addProperty("content_end", nextPosition("records", space, channel))
    }

    /** Latest target states within a fixed signed observation, independent of
     * the currently visible message window. Private pin intent stays encrypted. */
    private fun pinPage(reply: JsonObject, space: String, channel: String, reader: String, after: Long, observedThrough: Long?) {
        val latest = database.prepareStatement("SELECT COALESCE(MAX(position)+1,0) FROM records WHERE space_id=? AND channel_id=? AND kind='message-pin'").use { query ->
            query.setString(1, space); query.setString(2, channel)
            query.executeQuery().use { rows -> check(rows.next()); rows.getLong(1) }
        }
        val through = observedThrough ?: latest
        require(after <= through && through <= latest)
        val items = JsonArray(); var size = 0; var next = after; var more = false
        database.prepareStatement("SELECT r.receipt,r.position,r.target_message_id,r.original FROM records r WHERE r.space_id=? AND r.channel_id=? AND r.kind='message-pin' AND r.position>=? AND r.position<? " +
            "AND NOT EXISTS (SELECT 1 FROM records n WHERE n.space_id=r.space_id AND n.channel_id=r.channel_id AND n.kind=r.kind AND n.target_message_id=r.target_message_id AND n.position>r.position AND n.position<?) ORDER BY r.position LIMIT 51").use { query ->
            query.setString(1, space); query.setString(2, channel); query.setLong(3, after); query.setLong(4, through); query.setLong(5, through)
            query.executeQuery().use { rows -> while (rows.next()) {
                val pin = SaitoEnvelope.decode(rows.getString(4))
                val page = JsonObject()
                messagePage(page, space, channel, reader, Math.addExact(pin.body.number("target_position"), 1), "before", 1,
                    pin.body.get("thread_root_id")?.asString, false, null, true)
                val item = page.getAsJsonArray("items").single().asJsonObject.deepCopy()
                require(SaitoEnvelope.decode(item.text("message")).body.text("event").let(SaitoEnvelope::decode).id == rows.getString(3))
                item.addProperty("pin", rows.getString(1))
                pin.body.get("thread_root_id")?.asString?.let { parent ->
                    val base = SaitoEnvelope.decode(SaitoEnvelope.decode(item.text("message")).body.text("event"))
                    val root = JsonObject()
                    messagePage(root, space, channel, reader, Math.addExact(base.body.number("target_position"), 1), "before", 1, null, false, null, false)
                    val parentItem = root.getAsJsonArray("items").single().asJsonObject
                    require(SaitoEnvelope.decode(SaitoEnvelope.decode(parentItem.text("message")).body.text("event")).id == parent)
                    item.add("parent", parentItem)
                }
                val bytes = item.toString().toByteArray(Charsets.UTF_8).size
                check(bytes <= PAGE_BYTES)
                if (items.size() == 50 || size + bytes > PAGE_BYTES) { more = true; break }
                items.add(item); size += bytes; next = Math.addExact(rows.getLong(2), 1)
            } }
        }
        reply.add("items", items); reply.addProperty("next_cursor", if (more) next else through)
        reply.addProperty("has_more", more); reply.addProperty("pin_version", through); reply.addProperty("pin_end", latest)
        reply.addProperty("content_end", nextPosition("records", space, channel))
    }

    private fun originalEnvelope(receipt: String): String = SaitoEnvelope.decode(receipt).body.text("event")

    private fun directoryPage(reply: JsonObject, query: String, cursor: Long) {
        val items = JsonArray()
        var next = cursor
        var bytes = 0
        var more = false
        database.prepareStatement("SELECT id,creation_receipt,listing_receipt FROM public_spaces WHERE listed=1 AND id>? AND instr(search_text,?)>0 ORDER BY id LIMIT 51").use { sql ->
            sql.setLong(1, cursor); sql.setString(2, query.trim().lowercase(Locale.ROOT))
            sql.executeQuery().use { rows ->
                while (rows.next()) {
                    val receipt = rows.getString(2)
                    val listing = rows.getString(3)
                    val size = receipt.toByteArray(Charsets.UTF_8).size +
                        if (listing == receipt) 0 else listing.toByteArray(Charsets.UTF_8).size
                    check(size <= PAGE_BYTES)
                    if (items.size() >= 50 || bytes + size > PAGE_BYTES) { more = true; break }
                    items.add(JsonObject().apply {
                        addProperty("creation", receipt)
                        if (listing != receipt) addProperty("listing", listing)
                    })
                    bytes += size; next = rows.getLong(1)
                }
            }
        }
        reply.add("items", items); reply.addProperty("next_cursor", next); reply.addProperty("has_more", more)
    }

    private fun indexPublicCreation(space: String, receipt: String) {
        val signed = SaitoEnvelope.decode(receipt)
        require(signed.author == wallet.publicKey && signed.body.text("space_id") == space)
        val original = SaitoEnvelope.decode(signed.body.text("event"))
        if (original.body.text("visibility") != "public" || !original.body.has("public_profile")) return
        val profile = SpaceProfile.fromJson(original.body.getAsJsonObject("public_profile"))
        require(profile.visibility == SpaceVisibility.PUBLIC)
        database.prepareStatement("INSERT INTO public_spaces(space_id,profile,search_text,listed,creation_receipt,listing_receipt) VALUES(?,?,?,?,?,?) ON CONFLICT(space_id) DO NOTHING").use { sql ->
            sql.setString(1, space); sql.setString(2, profile.json().toString())
            sql.setString(3, (profile.name + "\n" + profile.description).lowercase(Locale.ROOT))
            sql.setInt(4, if (profile.listed) 1 else 0); sql.setString(5, receipt); sql.setString(6, receipt)
            check(sql.executeUpdate() in 0..1)
        }
    }

    private fun indexVisibilityChange(space: String, receipt: String, snapshot: SpaceSnapshot) {
        if (snapshot.visibility == SpaceVisibility.PRIVATE || snapshot.dissolved) {
            database.prepareStatement("UPDATE public_spaces SET listed=0,listing_receipt=? WHERE space_id=?").use { sql ->
                sql.setString(1, receipt); sql.setString(2, space); check(sql.executeUpdate() in 0..1)
            }
            return
        }
        val profile = requireNotNull(snapshot.publicProfile)
        require(snapshot.visibility == SpaceVisibility.PUBLIC && profile.visibility == SpaceVisibility.PUBLIC)
        val creation = database.prepareStatement("SELECT receipt FROM controls WHERE space_id=? AND position=0").use { sql ->
            sql.setString(1, space)
            sql.executeQuery().use { rows -> check(rows.next()); rows.getString(1) }
        }
        database.prepareStatement("INSERT INTO public_spaces(space_id,profile,search_text,listed,creation_receipt,listing_receipt) VALUES(?,?,?,?,?,?) ON CONFLICT(space_id) DO UPDATE SET profile=excluded.profile,search_text=excluded.search_text,listed=excluded.listed,listing_receipt=excluded.listing_receipt").use { sql ->
            sql.setString(1, space); sql.setString(2, profile.json().toString())
            sql.setString(3, (profile.name + "\n" + profile.description).lowercase(Locale.ROOT))
            sql.setInt(4, if (profile.listed) 1 else 0); sql.setString(5, creation); sql.setString(6, receipt)
            check(sql.executeUpdate() == 1)
        }
    }

    init {
        Files.createDirectories(directory)
        database = DriverManager.getConnection("jdbc:sqlite:${directory.resolve("spaces.sqlite").toAbsolutePath()}",
            Properties().apply { setProperty("transaction_mode", "IMMEDIATE") })
        fileRoutes = SpaceFileRoutes(database, wallet)
        try {
            // Reject incompatible/foreign state before journal_mode can change
            // an existing database header. Migration must preserve that state.
            database.createStatement().use { sql ->
                val existingVersion = sql.executeQuery("PRAGMA user_version").use { rows ->
                    check(rows.next()); rows.getInt(1)
                }
                check(existingVersion in 0..5) { "Spaces storage version needs a compatible service" }
                if (existingVersion == 0) sql.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use { rows ->
                    check(!rows.next()) { "Existing unversioned Spaces storage needs recovery" }
                }
                if (existingVersion != 0) sql.executeQuery("SELECT service_key FROM service_metadata WHERE id=1").use { rows ->
                    check(rows.next() && rows.getString(1) == wallet.publicKey) { "Spaces storage belongs to a different service identity" }
                }
            }
            database.createStatement().use { sql ->
                sql.execute("PRAGMA journal_mode=WAL")
                sql.execute("PRAGMA synchronous=FULL")
                sql.execute("PRAGMA busy_timeout=5000")
                sql.execute("PRAGMA foreign_keys=ON")
            }
            atomic {
                val version = database.createStatement().use { sql ->
                    sql.executeQuery("PRAGMA user_version").use { rows -> check(rows.next()); rows.getInt(1) }
                }
                check(version in 0..5) { "Spaces storage version needs a compatible service" }
                if (version == 0) {
                    database.createStatement().use { sql ->
                        sql.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use {
                            check(!it.next()) { "Existing unversioned Spaces storage needs recovery" }
                        }
                        sql.execute("CREATE TABLE service_metadata (id INTEGER PRIMARY KEY CHECK(id=1), service_key TEXT NOT NULL)")
                        sql.execute("""CREATE TABLE controls (
                            space_id TEXT NOT NULL, position INTEGER NOT NULL CHECK(position>=0),
                            event_id TEXT NOT NULL, operation_id TEXT NOT NULL, actor TEXT NOT NULL,
                            original TEXT NOT NULL, receipt TEXT NOT NULL, accepted_at INTEGER NOT NULL,
                            PRIMARY KEY(space_id,position), UNIQUE(space_id,event_id), UNIQUE(space_id,operation_id))""")
                        sql.execute("""CREATE TABLE joins (
                            space_id TEXT NOT NULL, position INTEGER NOT NULL CHECK(position>=0),
                            event_id TEXT NOT NULL, operation_id TEXT NOT NULL, actor TEXT NOT NULL,
                            original TEXT NOT NULL, receipt TEXT NOT NULL, accepted_at INTEGER NOT NULL,
                            PRIMARY KEY(space_id,position), UNIQUE(space_id,event_id), UNIQUE(space_id,operation_id))""")
                        sql.execute("PRAGMA user_version=1")
                    }
                    database.prepareStatement("INSERT INTO service_metadata(id,service_key) VALUES(1,?)").use {
                        it.setString(1, wallet.publicKey); check(it.executeUpdate() == 1)
                    }
                }
                database.createStatement().use { sql ->
                    sql.executeQuery("SELECT service_key FROM service_metadata WHERE id=1").use { rows ->
                        check(rows.next() && rows.getString(1) == wallet.publicKey) { "Spaces storage belongs to a different service identity" }
                    }
                }
                if (version < 2) database.createStatement().use { sql ->
                    sql.execute("""CREATE TABLE records (
                        space_id TEXT NOT NULL, channel_id TEXT NOT NULL, position INTEGER NOT NULL CHECK(position>=0),
                        event_id TEXT NOT NULL, operation_id TEXT NOT NULL, actor TEXT NOT NULL,
                        control_id TEXT NOT NULL, epoch INTEGER NOT NULL CHECK(epoch>=0), generation TEXT NOT NULL,
                        original TEXT NOT NULL, receipt TEXT NOT NULL, accepted_at INTEGER NOT NULL,
                        PRIMARY KEY(space_id,channel_id,position), UNIQUE(space_id,event_id), UNIQUE(space_id,operation_id),
                        FOREIGN KEY(space_id,control_id) REFERENCES controls(space_id,event_id))""")
                    sql.execute("PRAGMA user_version=2")
                }
                if (version < 3) database.createStatement().use { sql ->
                    sql.execute("""CREATE TABLE rejections (
                        space_id TEXT NOT NULL, position INTEGER NOT NULL CHECK(position>=0),
                        event_id TEXT NOT NULL, operation_id TEXT NOT NULL, actor TEXT NOT NULL,
                        original TEXT NOT NULL, receipt TEXT NOT NULL, accepted_at INTEGER NOT NULL,
                        PRIMARY KEY(space_id,position), UNIQUE(space_id,event_id), UNIQUE(space_id,operation_id))""")
                    sql.execute("ALTER TABLE records ADD COLUMN kind TEXT NOT NULL DEFAULT 'unclassified'")
                    sql.execute("ALTER TABLE records ADD COLUMN recipient_credential TEXT")
                    // Upgrade public routing metadata in bounded batches. The
                    // accepted original and its signed receipt stay byte-exact.
                    var after = 0L
                    while (true) {
                        val batch = database.prepareStatement("SELECT rowid,original FROM records WHERE rowid>? ORDER BY rowid LIMIT 100").use { select ->
                            select.setLong(1, after)
                            select.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1) to rows.getString(2)) } }
                        }
                        for ((row, original) in batch) {
                            val message = SaitoEnvelope.decode(original).body
                            database.prepareStatement("UPDATE records SET kind=?,recipient_credential=? WHERE rowid=?").use { update ->
                                update.setString(1, if (message.text("kind") == "content") message.text("content_kind") else message.text("kind"))
                                update.setString(2, message.get("recipient_credential")?.asString); update.setLong(3, row)
                                check(update.executeUpdate() == 1)
                            }
                            after = row
                        }
                        if (batch.size < 100) break
                    }
                    sql.execute("CREATE INDEX records_recipient ON records(space_id,channel_id,recipient_credential,position)")
                    sql.execute("PRAGMA user_version=3")
                }
                if (version < 4) {
                    database.createStatement().use { sql ->
                        sql.execute("CREATE TABLE public_spaces(id INTEGER PRIMARY KEY AUTOINCREMENT,space_id TEXT NOT NULL UNIQUE,profile TEXT NOT NULL,search_text TEXT NOT NULL,listed INTEGER NOT NULL CHECK(listed IN (0,1)),creation_receipt TEXT NOT NULL,listing_receipt TEXT NOT NULL)")
                        sql.execute("CREATE INDEX public_spaces_listed ON public_spaces(listed,id)")
                    }
                    var after = 0L
                    while (true) {
                        val rows = database.prepareStatement("SELECT rowid,space_id,receipt FROM controls WHERE position=0 AND rowid>? ORDER BY rowid LIMIT 100").use { sql ->
                            sql.setLong(1, after)
                            sql.executeQuery().use { records -> buildList {
                                while (records.next()) add(Triple(records.getLong(1), records.getString(2), records.getString(3)))
                            } }
                        }
                        rows.forEach { (row, space, receipt) -> indexPublicCreation(space, receipt); after = row }
                        if (rows.size < 100) break
                    }
                    database.createStatement().use { it.execute("PRAGMA user_version=4") }
                }
                if (version < 5) {
                    database.createStatement().use { it.execute("ALTER TABLE records ADD COLUMN target_message_id TEXT") }
                    var after = 0L
                    while (true) {
                        val batch = database.prepareStatement("SELECT rowid,original FROM records WHERE rowid>? ORDER BY rowid LIMIT 100").use { select ->
                            select.setLong(1, after)
                            select.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getLong(1) to rows.getString(2)) } }
                        }
                        for ((row, original) in batch) {
                            val body = SaitoEnvelope.decode(original).body
                            database.prepareStatement("UPDATE records SET kind=?,target_message_id=? WHERE rowid=?").use { update ->
                                update.setString(1, if (body.text("kind") in setOf("content", "public-content")) body.text("content_kind") else body.text("kind"))
                                update.setString(2, body.get("target_message_id")?.asString); update.setLong(3, row)
                                check(update.executeUpdate() == 1)
                            }
                            after = row
                        }
                        if (batch.size < 100) break
                    }
                    database.createStatement().use { sql ->
                        sql.execute("CREATE INDEX records_messages ON records(space_id,channel_id,kind,position)")
                        sql.execute("CREATE INDEX records_message_versions ON records(space_id,channel_id,kind,target_message_id,position)")
                        sql.execute("PRAGMA user_version=5")
                    }
                }
                fileRoutes.createSchema()
            }
        } catch (failure: Throwable) {
            try { database.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    /** Original accepted results survive lost responses and service restart.
     * IMMEDIATE transactions reserve the write lock before reading the head. */
    private fun headPage(reply: JsonObject, space: String, caller: String, positions: JsonObject) {
        val items = JsonArray()
        for (channel in positions.keySet()) {
            val cursor = positions.number(channel)
            val end = nextPosition("records", space, channel)
            require(cursor <= end) { "Read position is ahead of this node's history" }
            val (count, messageEnd) = database.prepareStatement("SELECT COUNT(*),MAX(position) FROM records WHERE space_id=? AND channel_id=? AND position>=? AND kind='message' AND actor<>?").use { sql ->
                sql.setString(1, space); sql.setString(2, channel); sql.setLong(3, cursor); sql.setString(4, caller)
                sql.executeQuery().use { rows ->
                    check(rows.next()); val count = rows.getLong(1)
                    count to if (count == 0L) 0L else Math.addExact(rows.getLong(2), 1)
                }
            }
            items.add(JsonObject().apply {
                addProperty("channel_id", channel); addProperty("end_cursor", end); addProperty("unread_count", count)
                addProperty("message_end", messageEnd)
            })
        }
        reply.addProperty("request", "page"); reply.add("items", items); reply.addProperty("next_cursor", 0)
    }

    @Synchronized fun submit(envelope: String): String {
        val event = SaitoEnvelope.decode(envelope)
        val body = event.body
        require(body.text("module") == "Spaces" && body.text("request") == "event" && body.get("version").toString() == "1")
        require(event.recipients == listOf(event.author, wallet.publicKey).distinct())
        val space = body.text("space_id").also { require(it.matches(ID)) }
        val operation = body.text("operation_id").also { require(it.matches(ID)) }
        return atomic {
            val prior = database.prepareStatement(listOf("controls", "joins", "records", "rejections").joinToString(" UNION ALL ") {
                "SELECT event_id,receipt FROM $it WHERE space_id=? AND operation_id=?"
            }).use { sql ->
                for (index in 0..3) { sql.setString(index * 2 + 1, space); sql.setString(index * 2 + 2, operation) }
                sql.executeQuery().use { rows ->
                    if (rows.next()) {
                        require(rows.getString(1) == event.id) { "Spaces operation already has a different original" }
                        rows.getString(2)
                    } else null
                }
            }
            if (prior != null) prior else {
                val journal = journal(space)
                val state = journal.snapshot
                val acceptedAt = clock.millis().also { require(it >= 0) }
                val joining = body.text("kind") in setOf("join", "join-channel", "replace-device")
                val content = body.text("kind") in setOf("content", "history", "public-content")
                val table = if (joining) "joins" else if (content) "records" else "controls"
                val position = if (joining || content) nextPosition(table, space, if (content) body.text("channel_id") else null)
                    else state.nextPosition
                fun receipt(stream: String, at: Long) = SaitoTransaction.createSpaceReply(wallet, JsonObject().apply {
                    addProperty("module", "Spaces"); addProperty("request", "receipt"); addProperty("version", 1)
                    addProperty("space_id", space); addProperty("stream", stream)
                    addProperty("position", at); addProperty("event", envelope)
                    if (stream == "content") addProperty("channel_id", body.text("channel_id"))
                    if (stream in setOf("content", "rejection")) state.controlId?.let { addProperty("authorized_control", it) }
                    if (stream == "rejection") addProperty("reason", "not-accepted")
                }, acceptedAt, event.author).envelope()
                val receipt = receipt(if (joining) "join" else if (content) "content" else "control", position)
                try {
                    when {
                        joining -> journal.verifyJoin(envelope, acceptedAt)
                        content -> {
                            journal.verifyContent(envelope, acceptedAt)
                            verifyMessageMutation(event, journal)
                            if (body.get("content_kind")?.asString == "file") fileRoutes.acceptPublication(event, journal, acceptedAt)
                        }
                        else -> journal.receive(receipt)
                    }
                } catch (_: IllegalArgumentException) {
                    // Persist a terminal disposition before answering. An old
                    // rejected original can never become accepted on retry.
                    val rejectedPosition = nextPosition("rejections", space)
                    val rejected = receipt("rejection", rejectedPosition)
                    insertSimple("rejections", space, rejectedPosition, event.id, operation, event.author, envelope, rejected, acceptedAt)
                    return@atomic rejected
                }
                if (content) {
                    database.prepareStatement("INSERT INTO records(space_id,channel_id,position,event_id,operation_id,actor,control_id,epoch,generation,original,receipt,accepted_at,kind,recipient_credential,target_message_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)").use { sql ->
                        sql.setString(1, space); sql.setString(2, body.text("channel_id")); sql.setLong(3, position)
                        sql.setString(4, event.id); sql.setString(5, operation); sql.setString(6, event.author)
                        sql.setString(7, requireNotNull(state.controlId)); sql.setLong(8, body.number("epoch"))
                        sql.setString(9, body.text("key_generation")); sql.setString(10, envelope)
                        sql.setString(11, receipt); sql.setLong(12, acceptedAt)
                        sql.setString(13, if (body.text("kind") in setOf("content", "public-content")) body.text("content_kind") else body.text("kind"))
                        sql.setString(14, body.get("recipient_credential")?.asString)
                        sql.setString(15, body.get("target_message_id")?.asString)
                        check(sql.executeUpdate() == 1)
                    }
                } else insertSimple(table, space, position, event.id, operation, event.author, envelope, receipt, acceptedAt)
                if (body.text("kind") == "create") indexPublicCreation(space, receipt)
                if (body.text("kind") in setOf("set-visibility", "dissolve")) indexVisibilityChange(space, receipt, journal.snapshot)
                if (body.text("kind") == "set-listed") {
                    val profile = requireNotNull(journal.snapshot.publicProfile)
                    database.prepareStatement("UPDATE public_spaces SET listed=?,profile=?,listing_receipt=? WHERE space_id=?").use { sql ->
                        sql.setInt(1, if (profile.listed) 1 else 0); sql.setString(2, profile.json().toString())
                        sql.setString(3, receipt); sql.setString(4, space); check(sql.executeUpdate() == 1)
                    }
                }
                receipt
            }
        }
    }

    private fun verifyMessageMutation(event: SignedSaitoMessage, journal: SpaceJournal) {
        val body = event.body
        val kind = body.get("content_kind")?.asString
        if (kind !in setOf("message-edit", "message-delete", "thread-reply", "message-reaction", "message-pin")) return
        val target = body.text("target_message_id")
        val position = body.number("target_position")
        require(target.matches(Regex("[0-9a-f]{128}")) && position >= 0)
        val original = database.prepareStatement("SELECT original FROM records WHERE space_id=? AND channel_id=? AND position=? AND event_id=? AND kind IN ('message','thread-reply','article','file')").use { sql ->
            sql.setString(1, body.text("space_id")); sql.setString(2, body.text("channel_id"))
            sql.setLong(3, position); sql.setString(4, target)
            sql.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }
        val accepted = SaitoEnvelope.decode(requireNotNull(original))
        val acceptedKind = accepted.body.text("content_kind")
        if (acceptedKind == "article") require(kind == "message-edit" || kind == "message-delete")
        if (acceptedKind == "file") require(kind == "message-delete")
        if (acceptedKind == "article" && kind == "message-edit" && body.text("kind") == "public-content") {
            require(SpaceArticleContent.decode(accepted.body.text("text")) is SpaceArticleContent.Original)
        }
        if (kind == "thread-reply") {
            require(acceptedKind == "message") { "Replies belong to a top-level message" }
            return
        }
        val thread = if (acceptedKind == "thread-reply") accepted.body.text("target_message_id") else null
        require(body.get("thread_root_id")?.asString == thread) { "A change belongs to its actual conversation" }
        val author = accepted.author
        require(if (kind == "message-pin") journal.hasCurrentAdministratorIntent(event.author, body.text("control_id"))
            else kind == "message-reaction" || author == event.author || kind == "message-delete" && journal.hasCurrentAdministratorIntent(event.author, body.text("control_id"))) {
            "Only the author can edit; moderation needs current staff authority"
        }
        val deleted = database.prepareStatement("SELECT 1 FROM records WHERE space_id=? AND channel_id=? AND kind='message-delete' AND target_message_id=? LIMIT 1").use { sql ->
            sql.setString(1, body.text("space_id")); sql.setString(2, body.text("channel_id")); sql.setString(3, target)
            sql.executeQuery().use { it.next() }
        }
        require(!deleted) { "The message has already been removed" }
    }

    private fun nextPosition(table: String, space: String, channel: String? = null): Long =
        database.prepareStatement("SELECT COALESCE(MAX(position)+1,0) FROM $table WHERE space_id=?" +
            if (channel == null) "" else " AND channel_id=?").use { sql ->
            sql.setString(1, space); if (channel != null) sql.setString(2, channel)
            sql.executeQuery().use { rows -> check(rows.next()); rows.getLong(1) }
        }

    private fun insertSimple(table: String, space: String, position: Long, event: String, operation: String,
        actor: String, original: String, receipt: String, time: Long) {
        database.prepareStatement("INSERT INTO $table(space_id,position,event_id,operation_id,actor,original,receipt,accepted_at) VALUES(?,?,?,?,?,?,?,?)").use { sql ->
            sql.setString(1, space); sql.setLong(2, position); sql.setString(3, event); sql.setString(4, operation)
            sql.setString(5, actor); sql.setString(6, original); sql.setString(7, receipt); sql.setLong(8, time)
            check(sql.executeUpdate() == 1)
        }
    }

    private fun journal(space: String): SpaceJournal {
        val journal = SpaceJournal(space, wallet.publicKey)
        database.prepareStatement("SELECT receipt FROM controls WHERE space_id=? ORDER BY position").use { sql ->
            sql.setString(1, space)
            sql.executeQuery().use { rows -> while (rows.next()) journal.receive(rows.getString(1)) }
        }
        return journal
    }

    /** This projection is only used for read-only checks on a committed control head. */
    private fun fileJournal(space: String): SpaceJournal {
        val head = database.prepareStatement("SELECT event_id FROM controls WHERE space_id=? ORDER BY position DESC LIMIT 1").use { sql ->
            sql.setString(1, space)
            sql.executeQuery().use { row -> if (row.next()) row.getString(1) else null }
        }
        val cached = recentFileJournals[space]
        if (cached != null && cached.head == head) return cached.journal
        return journal(space).also { recentFileJournals[space] = FileJournal(head, it) }
    }

    private fun <T> atomic(action: () -> T): T {
        database.autoCommit = false
        var failure: Throwable? = null
        try {
            val result = action()
            database.commit()
            return result
        } catch (original: Throwable) {
            failure = original
            try { database.rollback() } catch (cleanup: Throwable) { original.addSuppressed(cleanup) }
            throw original
        } finally {
            try { database.autoCommit = true } catch (cleanup: Throwable) {
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    @Synchronized override fun close() = database.close()

    private fun JsonObject.text(name: String): String {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun JsonObject.number(name: String): Long {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber &&
            value.asString.matches(Regex("0|[1-9][0-9]{0,18}")))
        return value.asString.toLong().also { require(it >= 0) }
    }

    private companion object {
        val ID = Regex("[0-9a-f]{64}")
        const val PAGE_BYTES = 2 * 1024 * 1024
    }
}
