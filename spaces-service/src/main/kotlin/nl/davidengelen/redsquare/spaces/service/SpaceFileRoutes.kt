package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SignedSaitoMessage
import nl.davidengelen.redsquare.spaces.SpaceFileCipher
import nl.davidengelen.redsquare.spaces.SpaceFileGrant
import nl.davidengelen.redsquare.spaces.SpaceFileLease
import nl.davidengelen.redsquare.spaces.SpaceFileStage
import nl.davidengelen.redsquare.spaces.SpaceJournal
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
    }

    fun ready() = database.createStatement().use { sql ->
        sql.executeQuery("SELECT object_id, actor, control_id, accepted_id FROM file_uploads LIMIT 0").close()
    }

    fun permit(reply: JsonObject, scope: JsonObject, actor: String, journal: SpaceJournal, now: Long) {
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
                val prior = upload(space, objectId)
                if (prior == null) {
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
                    require(prior == Upload(actor, channel, prior.control, lease.id, sha256, bytes, null))
                    journal.verifyFileUpload(actor, channel, prior.control, now)
                }
                reply.addProperty("permission", grant(lease, objectId, sha256, bytes, SpaceFileGrant.Action.PUT, now))
            }
            "download", "confirm" -> {
                val file = scope.fileText("file").also { require(it.matches(SIGNATURE)) }
                val upload = database.prepareStatement("""SELECT object_id, sha256, bytes FROM file_uploads
                    WHERE space_id=? AND channel_id=? AND accepted_id=? AND NOT EXISTS (
                    SELECT 1 FROM records WHERE space_id=? AND channel_id=? AND kind='message-delete' AND target_message_id=?)""").use { sql ->
                    sql.setString(1, space)
                    sql.setString(2, channel)
                    sql.setString(3, file)
                    sql.setString(4, space)
                    sql.setString(5, channel)
                    sql.setString(6, file)
                    sql.executeQuery().use { row ->
                        require(row.next()) { "The shared file is unavailable" }
                        Triple(row.getString(1), row.getString(2), row.getLong(3))
                    }
                }
                if (scope.fileText("access") == "download") {
                    reply.addProperty("permission", grant(lease, upload.first, upload.second, upload.third,
                        SpaceFileGrant.Action.GET, now))
                }
                reply.addProperty("access_term", listOf(lease.id, record.generation, record.epoch,
                    journal.snapshot.membershipEndedAt[actor] ?: 0, record.accessStartedAt[actor] ?: 0).joinToString(":"))
            }
        }
    }

    fun acceptPublication(event: SignedSaitoMessage, journal: SpaceJournal, now: Long) {
        val body = event.body
        val storage = requireNotNull(journal.snapshot.fileStorage)
        val stage = SpaceFileStage.read(body.fileText("storage_stage"), wallet.publicKey, storage.offer.provider)
        val prior = requireNotNull(upload(stage.space, stage.objectId))
        val channel = body.fileText("channel_id")
        require(prior == Upload(event.author, channel, prior.control, stage.lease, stage.sha256, stage.bytes, null))
        journal.verifyFileUpload(event.author, channel, prior.control, now)
        database.prepareStatement("UPDATE file_uploads SET accepted_id=? WHERE space_id=? AND object_id=?").use { sql ->
            sql.setString(1, event.id)
            sql.setString(2, stage.space)
            sql.setString(3, stage.objectId)
            check(sql.executeUpdate() == 1)
        }
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
        require(it in 16..SpaceFileCipher.MAX_CIPHERTEXT_BYTES)
    } ?: throw IllegalArgumentException("Encrypted file length is invalid")

    private companion object {
        val ID = Regex("[0-9a-f]{64}")
        val SIGNATURE = Regex("[0-9a-f]{128}")
    }
}
