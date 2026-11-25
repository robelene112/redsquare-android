package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SignedSaitoMessage
import nl.davidengelen.redsquare.spaces.SpaceFileCipher
import nl.davidengelen.redsquare.spaces.SpaceFileAccessCheck
import nl.davidengelen.redsquare.spaces.SpaceFileGrant
import nl.davidengelen.redsquare.spaces.SpaceFileLease
import nl.davidengelen.redsquare.spaces.SpaceFileStage
import nl.davidengelen.redsquare.spaces.SpaceJournal
import nl.davidengelen.redsquare.spaces.SpaceRole
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import java.sql.Connection

/** Provider grants follow the accepted journal and the ledger's current content ordering. */
internal class SpaceFileRoutes(private val database: Connection, private val wallet: SaitoWallet) {
    private data class Upload(val actor: String, val channel: String, val control: String, val lease: String,
        val sha256: String, val bytes: Long, val accepted: String?)

    fun createSchema() = database.createStatement().use { sql ->
        sql.execute("""CREATE TABLE IF NOT EXISTS file_uploads (
            space_id TEXT NOT NULL, object_id TEXT NOT NULL, actor TEXT NOT NULL, channel_id TEXT NOT NULL,
            control_id TEXT NOT NULL, lease_id TEXT NOT NULL, sha256 TEXT NOT NULL, bytes INTEGER NOT NULL,
            accepted_id TEXT, PRIMARY KEY(space_id, object_id), UNIQUE(space_id, accepted_id))""")
        sql.execute("""CREATE TABLE IF NOT EXISTS file_upload_cancellations (
            space_id TEXT NOT NULL, object_id TEXT NOT NULL, PRIMARY KEY(space_id, object_id))""")
        sql.execute("""CREATE TABLE IF NOT EXISTS file_permissions (
            id TEXT PRIMARY KEY, actor TEXT NOT NULL, scope TEXT NOT NULL, access_term TEXT,
            expires_at INTEGER NOT NULL)""")
        sql.execute("CREATE INDEX IF NOT EXISTS file_permissions_expiry ON file_permissions(expires_at)")
    }

    fun ready() = database.createStatement().use { sql ->
        sql.executeQuery("SELECT object_id, actor, control_id, accepted_id FROM file_uploads LIMIT 0").close()
        sql.executeQuery("SELECT id, actor, scope, access_term FROM file_permissions LIMIT 0").close()
    }

    fun permit(reply: JsonObject, scope: JsonObject, actor: String, journal: SpaceJournal, now: Long,
        checkOnly: Boolean = false) {
        val space = scope.fileText("space_id")
        val channel = scope.fileText("channel_id")
        val record = requireNotNull(journal.snapshot.channels[channel])
        require(record.rootContentKind == "file" && !journal.snapshot.dissolved)
        require(actor in journal.snapshot.members && journal.canAccessChannel(actor, channel))
        if (record.visibility == SpaceVisibility.PRIVATE)
            require(record.retiredDevices.isEmpty() && record.devices.keys.any { it.split(':')[1] == actor })
        val lease = requireNotNull(journal.snapshot.fileStorage)
        require(now < lease.offer.expiresAt) { "Storage term has expired" }
        when (scope.fileText("access")) {
            "upload" -> {
                val control = scope.fileText("control").also { require(it.matches(SIGNATURE)) }
                val objectId = scope.fileText("object").also { require(it.matches(ID)) }
                val sha256 = scope.fileText("sha256").also { require(it.matches(ID)) }
                val bytes = scope.requireFileBytes()
                journal.verifyFileUpload(actor, channel, control, now)
                require(bytes <= lease.offer.capacityBytes)
                require(!cancelled(space, objectId))
                val prior = upload(space, objectId)
                if (prior == null) {
                    require(!checkOnly)
                    database.prepareStatement("INSERT INTO file_uploads VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)").use { sql ->
                        sql.setString(1, space)
                        sql.setString(2, objectId)
                        sql.setString(3, actor)
                        sql.setString(4, channel)
                        sql.setString(5, control)
                        sql.setString(6, lease.id)
                        sql.setString(7, sha256)
                        sql.setLong(8, bytes)
                        check(sql.executeUpdate() == 1)
                    }
                } else {
                    require(prior == Upload(actor, channel, prior.control, prior.lease, sha256, bytes, null))
                    journal.verifyFileUpload(actor, channel, prior.control, now)
                }
                if (!checkOnly) reply.addProperty("permission", grant(lease, objectId, sha256, bytes, SpaceFileGrant.Action.PUT, now))
            }
            "usage" -> reply.addProperty("permission", grant(lease, "", "", 0, SpaceFileGrant.Action.STATUS, now))
            "abandon" -> {
                val objectId = scope.fileText("object").also { require(it.matches(ID)) }
                val upload = upload(space, objectId) ?: return
                require(upload.channel == channel && upload.accepted == null &&
                    (upload.actor == actor || journal.snapshot.members[actor] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN)))
                database.prepareStatement("INSERT OR IGNORE INTO file_upload_cancellations VALUES (?, ?)").use { sql ->
                    sql.setString(1, space)
                    sql.setString(2, objectId)
                    sql.executeUpdate()
                }
                reply.addProperty("permission", grant(lease, objectId, upload.sha256, upload.bytes, SpaceFileGrant.Action.DELETE, now))
            }
            "download", "confirm", "remove" -> {
                val file = scope.fileText("file").also { require(it.matches(SIGNATURE)) }
                val remove = scope.fileText("access") == "remove"
                val deleted = if (remove) "EXISTS" else "NOT EXISTS"
                val upload = database.prepareStatement("""SELECT object_id, sha256, bytes, actor FROM file_uploads
                    WHERE space_id=? AND channel_id=? AND accepted_id=? AND $deleted (
                    SELECT 1 FROM records WHERE space_id=? AND channel_id=? AND kind='message-delete' AND target_message_id=?)""").use { sql ->
                    sql.setString(1, space)
                    sql.setString(2, channel)
                    sql.setString(3, file)
                    sql.setString(4, space)
                    sql.setString(5, channel)
                    sql.setString(6, file)
                    sql.executeQuery().use { row ->
                        require(row.next()) { "The shared file is unavailable" }
                        if (remove) require(row.getString(4) == actor ||
                            journal.snapshot.members[actor] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
                        Triple(row.getString(1), row.getString(2), row.getLong(3))
                    }
                }
                if (!checkOnly && scope.fileText("access") != "confirm") {
                    reply.addProperty("permission", grant(lease, upload.first, upload.second, upload.third,
                        if (remove) SpaceFileGrant.Action.DELETE else SpaceFileGrant.Action.GET, now))
                }
                reply.addProperty("access_term", listOf(lease.id, record.generation, record.epoch,
                    journal.snapshot.membershipEndedAt[actor] ?: 0, record.accessStartedAt[actor] ?: 0).joinToString(":"))
            }
        }
    }

    fun recordPermission(reply: JsonObject, scope: JsonObject, actor: String, journal: SpaceJournal, now: Long) {
        if (scope.fileText("access") !in setOf("upload", "download")) return
        val lease = requireNotNull(journal.snapshot.fileStorage)
        val grant = SpaceFileGrant.read(reply.fileText("permission"), wallet.publicKey, lease.offer.provider)
        database.prepareStatement("DELETE FROM file_permissions WHERE expires_at<=?").use { sql ->
            sql.setLong(1, now); sql.executeUpdate()
        }
        database.prepareStatement("INSERT INTO file_permissions VALUES (?, ?, ?, ?, ?)").use { sql ->
            sql.setString(1, grant.id)
            sql.setString(2, actor)
            sql.setString(3, scope.toString())
            sql.setString(4, reply.get("access_term")?.asString)
            sql.setLong(5, grant.expiresAt)
            check(sql.executeUpdate() == 1)
        }
    }

    fun stillAllowed(check: SpaceFileAccessCheck, journal: SpaceJournal, now: Long): Boolean {
        val grant = check.permission
        if (now < grant.issuedAt || now >= grant.expiresAt) return false
        val active = journal.snapshot.fileStorage ?: return false
        if (active.offer.provider != check.provider || active.id != grant.lease || now >= active.offer.expiresAt) return false
        val (actor, scope, term) = database.prepareStatement("SELECT actor, scope, access_term FROM file_permissions WHERE id=?").use { sql ->
            sql.setString(1, grant.id)
            sql.executeQuery().use { row ->
                if (!row.next()) return false
                Triple(row.getString(1), JsonParser.parseString(row.getString(2)).asJsonObject, row.getString(3))
            }
        }
        val observed = JsonObject()
        return try {
            permit(observed, scope, actor, journal, now, checkOnly = true)
            term == observed.get("access_term")?.asString
        } catch (_: IllegalArgumentException) { false }
    }

    fun acceptPublication(event: SignedSaitoMessage, journal: SpaceJournal, now: Long) {
        val body = event.body
        val storage = requireNotNull(journal.snapshot.fileStorage)
        val stage = SpaceFileStage.read(body.fileText("storage_stage"), wallet.publicKey, storage.offer.provider)
        val prior = requireNotNull(upload(stage.space, stage.objectId))
        require(!cancelled(stage.space, stage.objectId))
        val channel = body.fileText("channel_id")
        require(prior == Upload(event.author, channel, prior.control, prior.lease, stage.sha256, stage.bytes, null))
        journal.verifyFileUpload(event.author, channel, prior.control, now)
        database.prepareStatement("UPDATE file_uploads SET accepted_id=? WHERE space_id=? AND object_id=?").use { sql ->
            sql.setString(1, event.id)
            sql.setString(2, stage.space)
            sql.setString(3, stage.objectId)
            check(sql.executeUpdate() == 1)
        }
    }

    private fun cancelled(space: String, id: String): Boolean =
        database.prepareStatement("SELECT 1 FROM file_upload_cancellations WHERE space_id=? AND object_id=?").use { sql ->
            sql.setString(1, space); sql.setString(2, id)
            sql.executeQuery().use { it.next() }
        }

    private fun grant(lease: SpaceFileLease, id: String, digest: String, bytes: Long,
        action: SpaceFileGrant.Action, now: Long): String = SpaceFileGrant.create(wallet, lease.offer.provider,
        lease.offer.space, lease.id, action, id, digest, bytes, now,
        minOf(lease.offer.expiresAt, Math.addExact(now, SpaceFileGrant.MAX_GRANT_MILLIS))).envelope

    private fun upload(space: String, id: String): Upload? = database.prepareStatement("""SELECT actor, channel_id,
        control_id, lease_id, sha256, bytes, accepted_id FROM file_uploads WHERE space_id=? AND object_id=?""").use { sql ->
        sql.setString(1, space)
        sql.setString(2, id)
        sql.executeQuery().use { row ->
            if (row.next()) Upload(row.getString(1), row.getString(2), row.getString(3), row.getString(4),
                row.getString(5), row.getLong(6), row.getString(7)) else null
        }
    }

    private fun JsonObject.fileText(name: String): String = get(name).also {
        require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isString)
    }.asString

    private fun JsonObject.requireFileBytes(): Long = get("bytes").also {
        require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isNumber)
    }.asString.toLongOrNull()?.also {
        require(it in SpaceFileCipher.MIN_CIPHERTEXT_BYTES..SpaceFileCipher.MAX_CIPHERTEXT_BYTES)
    } ?: throw IllegalArgumentException("Encrypted file length is invalid")

    private companion object {
        val ID = Regex("[0-9a-f]{64}")
        val SIGNATURE = Regex("[0-9a-f]{128}")
    }
}
