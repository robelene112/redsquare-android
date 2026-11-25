package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceFileGrant
import nl.davidengelen.redsquare.spaces.SpaceStorageApproval
import nl.davidengelen.redsquare.spaces.SpaceStorageOffer
import nl.davidengelen.redsquare.spaces.SpaceStorageUsage
import nl.davidengelen.redsquare.spaces.verifySpaceStorageOrigin
import java.math.BigInteger
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.time.Clock

/** Independent provider ledger. It does not run as part of the Spaces node. */
class SpaceFileProviderStore(root: Path, private val wallet: SaitoWallet, private val origin: String,
    private val authority: String, private val clock: Clock = Clock.systemUTC()) : AutoCloseable {
    data class Lease(val id: String, val capacityBytes: Long, val expiresAt: Long, val usedBytes: Long, val envelope: String) {
        override fun toString() = "Space file storage lease"
    }

    private val objects = Files.createDirectories(root.resolve("objects"))
    private val writer = FileChannel.open(root.resolve("provider.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    private val database: Connection

    init {
        var opened: Connection? = null
        try {
            verifySpaceStorageOrigin(origin)
            require(SaitoWallet.isPublicKey(authority) && authority != wallet.publicKey)
            check(writer.tryLock() != null) { "Storage directory already has a running provider" }
            database = DriverManager.getConnection("jdbc:sqlite:${root.resolve("space-file-provider.sqlite")}")
            opened = database
            database.createStatement().use { sql ->
                sql.execute("PRAGMA busy_timeout=5000")
                sql.execute("PRAGMA journal_mode=WAL")
                sql.execute("PRAGMA synchronous=FULL")
                sql.execute("""CREATE TABLE IF NOT EXISTS provider_identity (
                    singleton INTEGER PRIMARY KEY CHECK(singleton=1), provider TEXT NOT NULL,
                    authority TEXT NOT NULL, origin TEXT NOT NULL)""")
                sql.execute("""CREATE TABLE IF NOT EXISTS leases (
                    approval_id TEXT PRIMARY KEY, approval TEXT NOT NULL, offer_id TEXT NOT NULL UNIQUE,
                    space_id TEXT NOT NULL, lease_id TEXT NOT NULL UNIQUE, expires_at INTEGER NOT NULL,
                    envelope TEXT NOT NULL, capacity_bytes INTEGER NOT NULL)""")
                sql.execute("CREATE INDEX IF NOT EXISTS leases_space ON leases(space_id, expires_at)")
                sql.execute("""CREATE TABLE IF NOT EXISTS file_objects (
                    object_id TEXT PRIMARY KEY, lease_id TEXT NOT NULL, sha256 TEXT NOT NULL,
                    bytes INTEGER NOT NULL, stage TEXT NOT NULL)""")
                sql.execute("""CREATE TABLE IF NOT EXISTS removed_files (
                    object_id TEXT PRIMARY KEY, space_id TEXT NOT NULL,
                    sha256 TEXT NOT NULL, bytes INTEGER NOT NULL)""")
            }
            database.prepareStatement("INSERT OR IGNORE INTO provider_identity VALUES (1, ?, ?, ?)").use { sql ->
                sql.setString(1, wallet.publicKey)
                sql.setString(2, authority)
                sql.setString(3, origin)
                sql.executeUpdate()
            }
            database.createStatement().use { statement ->
                statement.executeQuery("SELECT provider, authority, origin FROM provider_identity WHERE singleton=1").use { row ->
                    require(row.next() && row.getString(1) == wallet.publicKey && row.getString(2) == authority && row.getString(3) == origin) {
                        "Storage directory belongs to a different provider or authority"
                    }
                }
            }
            removeUncommittedObjects()
        } catch (failure: Exception) {
            runCatching { opened?.close() }.onFailure(failure::addSuppressed)
            runCatching { writer.close() }.onFailure(failure::addSuppressed)
            throw failure
        }
    }

    /** The operator supplies the available plan before the owner reviews it. */
    fun offer(space: String, owner: String, capacityBytes: Long, expiresAt: Long): SpaceStorageOffer {
        val now = clock.millis()
        return SpaceStorageOffer.create(wallet, origin, authority, space, owner, capacityBytes, expiresAt,
            minOf(expiresAt, Math.addExact(now, 10 * 60_000L)), BigInteger.ZERO, now)
    }

    @Synchronized
    fun commit(approvalEnvelope: String): Lease {
        val approval = SpaceStorageApproval.read(approvalEnvelope, authority)
        val offer = approval.offer
        require(offer.provider == wallet.publicKey && offer.origin == origin && offer.costNolan.signum() == 0)
        database.autoCommit = false
        try {
            val existing = database.prepareStatement("SELECT approval, lease_id, capacity_bytes, expires_at, envelope FROM leases WHERE approval_id=?").use { sql ->
                sql.setString(1, approval.operation)
                sql.executeQuery().use { row ->
                    if (!row.next()) null else {
                        require(row.getString("approval") == approvalEnvelope)
                        Lease(row.getString("lease_id"), row.getLong("capacity_bytes"), row.getLong("expires_at"),
                            usedInSpace(offer.space), row.getString("envelope"))
                    }
                }
            }
            val accepted = existing ?: createLease(approvalEnvelope, approval)
            database.commit()
            return accepted
        } catch (failure: Exception) {
            runCatching { database.rollback() }.onFailure(failure::addSuppressed)
            throw failure
        } finally {
            database.autoCommit = true
        }
    }

    private fun createLease(approvalEnvelope: String, approval: SpaceStorageApproval): Lease {
        val offer = approval.offer
        val now = clock.millis()
        require(now < offer.expiresAt) { "The approved storage term expired" }
        val used = usedInSpace(offer.space)
        val signed = SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "SpacesStorage")
            addProperty("version", 1)
            addProperty("request", "lease")
            addProperty("approval", approval.operation)
            addProperty("offer", offer.id)
            addProperty("space", offer.space)
            addProperty("capacity_bytes", offer.capacityBytes)
            addProperty("expires_at", offer.expiresAt)
        }, now, listOf(offer.owner, authority))
        val lease = Lease(signed.id, offer.capacityBytes, offer.expiresAt, used, signed.envelope())
        database.prepareStatement("INSERT INTO leases (approval_id, approval, offer_id, space_id, lease_id, expires_at, envelope, capacity_bytes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)").use { sql ->
            sql.setString(1, approval.operation)
            sql.setString(2, approvalEnvelope)
            sql.setString(3, offer.id)
            sql.setString(4, offer.space)
            sql.setString(5, lease.id)
            sql.setLong(6, offer.expiresAt)
            sql.setString(7, lease.envelope)
            sql.setLong(8, offer.capacityBytes)
            check(sql.executeUpdate() == 1)
        }
        return lease
    }

    @Synchronized
    fun leaseFor(space: String): Lease? {
        require(space.matches(Regex("[0-9a-f]{64}")))
        return database.prepareStatement("SELECT lease_id, capacity_bytes, expires_at, envelope FROM leases WHERE space_id=? ORDER BY expires_at DESC, rowid DESC LIMIT 1").use { sql ->
            sql.setString(1, space)
            sql.executeQuery().use { row ->
                if (!row.next()) null else Lease(row.getString("lease_id"), row.getLong("capacity_bytes"), row.getLong("expires_at"),
                    usedInSpace(space), row.getString("envelope"))
            }
        }
    }

    /**
     * Buffer to an opaque file without holding the ledger while the sender streams.
     * Receipt creation, reservation and retries all use the original object identity.
     */
    fun put(grantEnvelope: String, source: InputStream, beforeCommit: () -> Unit = {}): String {
        val grant = grant(grantEnvelope, SpaceFileGrant.Action.PUT)
        synchronized(this) { checkPut(grant)?.let { return it } }
        val pending = Files.createTempFile(objects, "incoming-", ".part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newOutputStream(pending).use { sink ->
                val hashing = DigestOutputStream(sink, digest)
                val buffer = ByteArray(64 * 1024)
                var remaining = grant.bytes
                while (remaining > 0) {
                    val count = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    require(count > 0) { "Incomplete encrypted file" }
                    hashing.write(buffer, 0, count)
                    remaining -= count
                }
                require(source.read() == -1) { "Encrypted file exceeds its approved length" }
            }
            require(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == grant.sha256) {
                "Encrypted file does not match its approved digest"
            }
            FileChannel.open(pending, StandardOpenOption.WRITE).use { it.force(true) }
            beforeCommit()
            return synchronized(this) { checkPut(grant) ?: storeObject(grant, pending) }
        } finally {
            Files.deleteIfExists(pending)
        }
    }

    @Synchronized
    fun open(grantEnvelope: String): InputStream {
        val grant = grant(grantEnvelope, SpaceFileGrant.Action.GET)
        requireActiveLease(grant)
        requireNotNull(existingObject(grant)) { "Encrypted file is unavailable" }
        return Files.newInputStream(objects.resolve(grant.objectId))
    }

    @Synchronized
    fun usage(grantEnvelope: String): String {
        val grant = grant(grantEnvelope, SpaceFileGrant.Action.STATUS)
        val lease = requireActiveLease(grant)
        return SpaceStorageUsage.sign(wallet, authority, grant, lease.usedBytes, lease.capacityBytes,
            lease.expiresAt, clock.millis())
    }

    @Synchronized
    fun remove(grantEnvelope: String) {
        val grant = grant(grantEnvelope, SpaceFileGrant.Action.DELETE)
        requireActiveLease(grant)
        val existing = existingObject(grant)
        if (existing == null) {
            // A valid cancellation may reach storage before the first upload.
            // Recording its identity also prevents that in-flight PUT landing later.
            recordRemoval(grant)
        } else {
            database.autoCommit = false
            try {
                recordRemoval(grant)
                database.prepareStatement("DELETE FROM file_objects WHERE object_id=?").use { sql ->
                    sql.setString(1, grant.objectId)
                    check(sql.executeUpdate() == 1)
                }
                database.commit()
            } catch (failure: Exception) {
                runCatching { database.rollback() }.onFailure(failure::addSuppressed)
                throw failure
            } finally { database.autoCommit = true }
        }
        Files.deleteIfExists(objects.resolve(grant.objectId))
    }

    private fun recordRemoval(grant: SpaceFileGrant) {
        if (wasRemoved(grant)) return
        database.prepareStatement("INSERT INTO removed_files VALUES (?, ?, ?, ?)").use { sql ->
            sql.setString(1, grant.objectId); sql.setString(2, grant.space)
            sql.setString(3, grant.sha256); sql.setLong(4, grant.bytes)
            check(sql.executeUpdate() == 1)
        }
    }

    private fun wasRemoved(grant: SpaceFileGrant): Boolean =
        database.prepareStatement("SELECT space_id,sha256,bytes FROM removed_files WHERE object_id=?").use { sql ->
            sql.setString(1, grant.objectId)
            sql.executeQuery().use { row ->
                if (!row.next()) false else {
                    require(row.getString(1) == grant.space && row.getString(2) == grant.sha256 && row.getLong(3) == grant.bytes)
                    true
                }
            }
        }

    private fun grant(envelope: String, action: SpaceFileGrant.Action): SpaceFileGrant =
        SpaceFileGrant.read(envelope, authority, wallet.publicKey).also {
            require(it.action == action)
            it.requireCurrent(clock.millis())
        }

    private fun requireActiveLease(grant: SpaceFileGrant): Lease {
        val now = clock.millis()
        grant.requireCurrent(now)
        return database.prepareStatement("SELECT capacity_bytes, expires_at, envelope FROM leases WHERE lease_id=? AND space_id=? AND expires_at>?").use { sql ->
            sql.setString(1, grant.lease)
            sql.setString(2, grant.space)
            sql.setLong(3, now)
            sql.executeQuery().use { row ->
                require(row.next()) { "Space storage has expired or is unavailable" }
                Lease(grant.lease, row.getLong(1), row.getLong(2), usedInSpace(grant.space), row.getString(3))
            }
        }
    }

    private fun usedInSpace(space: String): Long =
        database.prepareStatement("SELECT COALESCE(SUM(o.bytes),0) FROM file_objects o JOIN leases l ON l.lease_id=o.lease_id WHERE l.space_id=?").use { sql ->
            sql.setString(1, space)
            sql.executeQuery().use { row -> check(row.next()); row.getLong(1) }
        }

    private fun existingObject(grant: SpaceFileGrant): String? =
        database.prepareStatement("SELECT l.space_id, o.lease_id, o.sha256, o.bytes, o.stage FROM file_objects o JOIN leases l ON l.lease_id=o.lease_id WHERE o.object_id=?").use { sql ->
            sql.setString(1, grant.objectId)
            sql.executeQuery().use { row ->
                if (!row.next()) null else {
                    require(row.getString(1) == grant.space && row.getString(3) == grant.sha256 && row.getLong(4) == grant.bytes)
                    row.getString(5)
                }
            }
        }

    private fun checkPut(grant: SpaceFileGrant): String? {
        val lease = requireActiveLease(grant)
        require(!wasRemoved(grant)) { "The shared object was removed" }
        val existing = existingObject(grant)
        require(existing != null || lease.capacityBytes - lease.usedBytes >= grant.bytes) { "Space storage is full" }
        return existing
    }

    private fun storeObject(grant: SpaceFileGrant, pending: Path): String {
        val receipt = SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "SpacesStorage")
            addProperty("version", 1)
            addProperty("request", "staged")
            addProperty("space", grant.space)
            addProperty("lease", grant.lease)
            addProperty("object", grant.objectId)
            addProperty("sha256", grant.sha256)
            addProperty("bytes", grant.bytes)
        }, clock.millis(), listOf(authority)).envelope()
        val destination = objects.resolve(grant.objectId)
        database.autoCommit = false
        try {
            Files.move(pending, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            FileChannel.open(objects, StandardOpenOption.READ).use { it.force(true) }
            database.prepareStatement("INSERT INTO file_objects VALUES (?, ?, ?, ?, ?)").use { sql ->
                sql.setString(1, grant.objectId)
                sql.setString(2, grant.lease)
                sql.setString(3, grant.sha256)
                sql.setLong(4, grant.bytes)
                sql.setString(5, receipt)
                check(sql.executeUpdate() == 1)
            }
            database.commit()
            return receipt
        } catch (failure: Exception) {
            runCatching { database.rollback() }.onFailure(failure::addSuppressed)
            // Startup reconciles any unindexed ciphertext. A commit error can be
            // ambiguous, so never delete a possibly committed destination here.
            throw failure
        } finally {
            database.autoCommit = true
        }
    }

    private fun removeUncommittedObjects() {
        Files.newDirectoryStream(objects).use { paths ->
            database.prepareStatement("SELECT 1 FROM file_objects WHERE object_id=?").use { sql ->
                for (path in paths) {
                    val name = path.fileName.toString()
                    if (INCOMING.matches(name)) Files.delete(path)
                    if (OBJECT_ID.matches(name)) {
                        sql.setString(1, name)
                        if (sql.executeQuery().use { !it.next() }) Files.delete(path)
                    }
                }
            }
        }
    }

    @Synchronized override fun close() {
        try { database.close() } finally { writer.close() }
    }

    private companion object {
        val INCOMING = Regex("incoming-[0-9]+\\.part")
        val OBJECT_ID = Regex("[0-9a-f]{64}")
    }
}
