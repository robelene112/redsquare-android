package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceJournal
import nl.davidengelen.redsquare.spaces.SpaceRole
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Clock
import java.util.Properties
import java.security.SecureRandom

/** Server-owned signed ordering. No private community keys are accepted. */
class SpacesServiceStore(directory: Path, private val wallet: SaitoWallet, private val clock: Clock) : AutoCloseable {
    private val database: Connection
    private data class Challenge(val caller: String, val scope: JsonObject, val expiresAt: Long)
    private val challenges = mutableMapOf<String, Challenge>()

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
        val stream = scope.text("stream").also { require(it in setOf("control", "join", "content")) }
        require(scope.keySet() == setOf("space_id", "stream", "cursor") + if (stream == "content") setOf("channel_id") else emptySet())
        val channel = if (stream == "content") scope.text("channel_id").also { require(it.matches(ID)) } else null
        val cursor = scope.number("cursor")
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
                    val journal = journal(space)
                    if (stream == "join") {
                        require(journal.snapshot.members[request.author] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                    } else require(request.author in journal.snapshot.members) { "Spaces private history is not available to this identity" }
                    if (channel != null) require(requireNotNull(journal.snapshot.channels[channel]).devices.keys.any {
                        it.split(':')[1] == request.author
                    }) { "Spaces channel is not available to this identity" }
                    val items = JsonArray()
                    var bytes = 0
                    var next = cursor
                    val table = when (stream) { "join" -> "joins"; "content" -> "records"; else -> "controls" }
                    database.prepareStatement("SELECT position,receipt FROM $table WHERE space_id=? AND position>=?" +
                        (if (channel != null) " AND channel_id=?" else "") + " ORDER BY position LIMIT 50").use { sql ->
                        sql.setString(1, space); sql.setLong(2, cursor)
                        if (channel != null) sql.setString(3, channel)
                        sql.executeQuery().use { rows ->
                            while (rows.next()) {
                                val value = rows.getString(2)
                                if (stream == "join") {
                                    val original = SaitoEnvelope.decode(SaitoEnvelope.decode(value).body.text("event"))
                                    val invitation = requireNotNull(journal.snapshot.invitations[original.body.text("invite_id")])
                                    if (original.author in journal.snapshot.members || invitation.expiresAt <= now) {
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
                }
            }
            else -> throw IllegalArgumentException("Unsupported Spaces read request")
        }
        return SaitoTransaction.createSpaceReply(wallet, reply, now, request.author).envelope()
    }

    private fun originalEnvelope(receipt: String): String = SaitoEnvelope.decode(receipt).body.text("event")

    init {
        Files.createDirectories(directory)
        database = DriverManager.getConnection("jdbc:sqlite:${directory.resolve("spaces.sqlite").toAbsolutePath()}",
            Properties().apply { setProperty("transaction_mode", "IMMEDIATE") })
        try {
            // Reject incompatible/foreign state before journal_mode can change
            // an existing database header. Migration must preserve that state.
            database.createStatement().use { sql ->
                val existingVersion = sql.executeQuery("PRAGMA user_version").use { rows ->
                    check(rows.next()); rows.getInt(1)
                }
                check(existingVersion in 0..2) { "Spaces storage version needs a compatible service" }
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
                check(version in 0..2) { "Spaces storage version needs a compatible service" }
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
            }
        } catch (failure: Throwable) {
            try { database.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    /** Original accepted results survive lost responses and service restart.
     * IMMEDIATE transactions reserve the write lock before reading the head. */
    @Synchronized fun submit(envelope: String): String {
        val event = SaitoEnvelope.decode(envelope)
        val body = event.body
        require(body.text("module") == "Spaces" && body.text("request") == "event" && body.get("version").toString() == "1")
        require(event.recipients == listOf(event.author, wallet.publicKey).distinct())
        val space = body.text("space_id").also { require(it.matches(ID)) }
        val operation = body.text("operation_id").also { require(it.matches(ID)) }
        return atomic {
            val prior = database.prepareStatement(listOf("controls", "joins", "records").joinToString(" UNION ALL ") {
                "SELECT event_id,receipt FROM $it WHERE space_id=? AND operation_id=?"
            }).use { sql ->
                for (index in 0..2) { sql.setString(index * 2 + 1, space); sql.setString(index * 2 + 2, operation) }
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
                val joining = body.text("kind") == "join"
                val content = body.text("kind") in setOf("content", "history")
                val position = if (joining) {
                    journal.verifyJoin(envelope, acceptedAt)
                    database.prepareStatement("SELECT COALESCE(MAX(position)+1,0) FROM joins WHERE space_id=?").use { sql ->
                        sql.setString(1, space)
                        sql.executeQuery().use { rows -> check(rows.next()); rows.getLong(1) }
                    }
                } else if (content) {
                    journal.verifyContent(envelope)
                    database.prepareStatement("SELECT COALESCE(MAX(position)+1,0) FROM records WHERE space_id=? AND channel_id=?").use { sql ->
                        sql.setString(1, space); sql.setString(2, body.text("channel_id"))
                        sql.executeQuery().use { rows -> check(rows.next()); rows.getLong(1) }
                    }
                } else state.nextPosition
                val receipt = SaitoTransaction.create(wallet, JsonObject().apply {
                    addProperty("module", "Spaces"); addProperty("request", "receipt"); addProperty("version", 1)
                    addProperty("space_id", space); addProperty("stream", if (joining) "join" else if (content) "content" else "control")
                    if (content) addProperty("channel_id", body.text("channel_id"))
                    addProperty("position", position); addProperty("event", envelope)
                }, acceptedAt, listOf(event.author)).envelope()
                if (!joining && !content) journal.receive(receipt)
                if (content) {
                    database.prepareStatement("INSERT INTO records(space_id,channel_id,position,event_id,operation_id,actor,control_id,epoch,generation,original,receipt,accepted_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)").use { sql ->
                        sql.setString(1, space); sql.setString(2, body.text("channel_id")); sql.setLong(3, position)
                        sql.setString(4, event.id); sql.setString(5, operation); sql.setString(6, event.author)
                        sql.setString(7, body.text("control_id")); sql.setLong(8, body.number("epoch"))
                        sql.setString(9, body.text("key_generation")); sql.setString(10, envelope)
                        sql.setString(11, receipt); sql.setLong(12, acceptedAt)
                        check(sql.executeUpdate() == 1)
                    }
                } else {
                val table = if (joining) "joins" else "controls" // Internal constants only, never client SQL.
                database.prepareStatement("INSERT INTO $table(space_id,position,event_id,operation_id,actor,original,receipt,accepted_at) VALUES(?,?,?,?,?,?,?,?)").use { sql ->
                    sql.setString(1, space); sql.setLong(2, position); sql.setString(3, event.id)
                    sql.setString(4, operation); sql.setString(5, event.author); sql.setString(6, envelope)
                    sql.setString(7, receipt); sql.setLong(8, acceptedAt)
                    check(sql.executeUpdate() == 1)
                }
                }
                receipt
            }
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
