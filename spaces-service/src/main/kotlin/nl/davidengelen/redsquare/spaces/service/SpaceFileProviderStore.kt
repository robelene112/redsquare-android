package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceStorageApproval
import nl.davidengelen.redsquare.spaces.SpaceStorageOffer
import nl.davidengelen.redsquare.spaces.verifySpaceStorageOrigin
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Clock

/** Independent provider ledger. It does not run as part of the Spaces node. */
class SpaceFileProviderStore(root: Path, private val wallet: SaitoWallet, private val origin: String,
    private val authority: String, private val clock: Clock = Clock.systemUTC()) : AutoCloseable {
    data class Lease(val id: String, val capacityBytes: Long, val expiresAt: Long, val usedBytes: Long, val envelope: String) {
        override fun toString() = "Space file storage lease"
    }

    private val database = DriverManager.getConnection("jdbc:sqlite:${Files.createDirectories(root).resolve("space-file-provider.sqlite")}")

    init {
        try {
            verifySpaceStorageOrigin(origin)
            require(SaitoWallet.isPublicKey(authority) && authority != wallet.publicKey)
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
                    envelope TEXT NOT NULL, capacity_bytes INTEGER NOT NULL, used_bytes INTEGER NOT NULL DEFAULT 0)""")
                sql.execute("CREATE INDEX IF NOT EXISTS leases_space ON leases(space_id, expires_at)")
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
        } catch (failure: Exception) {
            runCatching { database.close() }.onFailure(failure::addSuppressed)
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
            val existing = database.prepareStatement("SELECT approval, lease_id, capacity_bytes, expires_at, used_bytes, envelope FROM leases WHERE approval_id=?").use { sql ->
                sql.setString(1, approval.operation)
                sql.executeQuery().use { row ->
                    if (!row.next()) null else {
                        require(row.getString("approval") == approvalEnvelope)
                        Lease(row.getString("lease_id"), row.getLong("capacity_bytes"), row.getLong("expires_at"),
                            row.getLong("used_bytes"), row.getString("envelope"))
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
        offer.verifyScope(offer.space, authority, offer.owner, now)
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
        val lease = Lease(signed.id, offer.capacityBytes, offer.expiresAt, 0, signed.envelope())
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
        return database.prepareStatement("SELECT lease_id, capacity_bytes, expires_at, used_bytes, envelope FROM leases WHERE space_id=? ORDER BY expires_at DESC, rowid DESC LIMIT 1").use { sql ->
            sql.setString(1, space)
            sql.executeQuery().use { row ->
                if (!row.next()) null else Lease(row.getString("lease_id"), row.getLong("capacity_bytes"), row.getLong("expires_at"),
                    row.getLong("used_bytes"), row.getString("envelope"))
            }
        }
    }

    @Synchronized override fun close() = database.close()
}
