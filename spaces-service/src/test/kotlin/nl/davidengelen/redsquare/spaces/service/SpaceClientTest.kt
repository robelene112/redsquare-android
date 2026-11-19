package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceClientStore
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceClientTest {
    @get:Rule val files = TemporaryFolder()
    private val locals = mutableListOf<SqliteClientFixture>()
    private fun local(name: String) = SqliteClientFixture(files.newFile(name).absolutePath).also(locals::add)
    @After fun closeOwnedClients() { locals.asReversed().forEach { it.close() } }
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)

    @Test fun openingAnInvitationDoesNotJoinAndExplicitAdmissionRecoversHistoryBeforeBothClientsCanConverse() = runBlocking {
        val guest = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val ownerLocal = local("owner.sqlite")
        val guestLocal = local("guest.sqlite")
        SpacesServiceStore(files.newFolder("community-service").toPath(), service, clock).use { remote ->
            val ownerClient = SpaceClient(owner, "fixture-node", service.publicKey, ownerLocal, clock) { remote.exchange(it.envelope()) }
            val guestClient = SpaceClient(guest, "fixture-node", service.publicKey, guestLocal, clock) { remote.exchange(it.envelope()) }
            val space = ownerClient.create("Independent voices")
            ownerClient.flush(space)
            ownerClient.send(space, "Welcome, future community members")
            ownerClient.flush(space)
            val invitation = ownerClient.invite(space)
            ownerClient.flush(space)
            val preview = guestClient.preview(invitation)
            assertEquals("Independent voices", preview.name)
            assertEquals(space, preview.spaceId)
            assertThrows(IllegalArgumentException::class.java) { guestClient.messages(space) }
            assertEquals(space, guestClient.join(invitation))
            guestClient.flush(space)
            guestClient.refresh(space)
            assertFalse(guestClient.community(space).joined)
            assertThrows(IllegalArgumentException::class.java) { guestClient.send(space, "Not admitted yet") }
            ownerClient.admitPending(space)
            ownerClient.flush(space)
            guestClient.refresh(space)
            assertEquals(listOf("Welcome, future community members"), guestClient.messages(space))
            guestClient.send(space, "Happy to be here")
            guestClient.flush(space)
            ownerClient.refresh(space)
            assertEquals(listOf("Welcome, future community members", "Happy to be here"), ownerClient.messages(space))
        }
    }

    @Test fun anAcceptedCreationWithALostResponseRecoversItsOriginalBeforeSendingAndReadingRealText() = runBlocking {
        val received = mutableListOf<String>()
        var loseResponse = true
        val local = local("client.sqlite")
        SpacesServiceStore(files.newFolder("service").toPath(), service, clock).use { remote ->
            fun client() = SpaceClient(owner, "fixture-node", service.publicKey, local, clock) { original ->
                received += original.id
                val result = remote.exchange(original.envelope())
                if (loseResponse) { loseResponse = false; throw java.io.IOException("Original response lost") }
                result
            }
            val first = client()
            val space = first.create("A real community")
            try { first.flush(space); fail("Response should be lost") } catch (_: java.io.IOException) { }
            val reopened = client()
            reopened.flush(space)
            assertEquals("A real community", reopened.community(space).name)
            assertEquals(listOf(space), reopened.communities().map { it.id })
            assertEquals(2, received.size)
            assertEquals(received[0], received[1])
            reopened.send(space, "Hello from a durable community")
            reopened.flush(space)
            reopened.refresh(space)
            assertEquals(listOf("Hello from a durable community"), reopened.messages(space))
            assertEquals(reopened.messages(space), client().messages(space))
            assertThrows(IllegalArgumentException::class.java) {
                SpaceClient(owner, "another-node", service.publicKey, local, clock) { remote.exchange(it.envelope()) }.messages(space)
            }
        }
        Unit
    }
}

/** Isolated database adapter. Android's encrypted SQLite adapter has its own
 * native/OS contract; this does not claim Android at-rest protection. */
internal class SqliteClientFixture(path: String) : SpaceClientStore, AutoCloseable {
    override fun close() = db.close()
    private val db = DriverManager.getConnection("jdbc:sqlite:$path").apply {
        createStatement().use { it.execute("CREATE TABLE IF NOT EXISTS entries(key TEXT PRIMARY KEY,value BLOB NOT NULL)") }
    }
    @Synchronized override fun <T> transaction(action: SpaceClientStore.Transaction.() -> T): T {
        db.autoCommit = false
        try {
            val value = action(object : SpaceClientStore.Transaction {
                override fun get(key: String): ByteArray? = db.prepareStatement("SELECT value FROM entries WHERE key=?").use { sql ->
                    sql.setString(1, key); sql.executeQuery().use { if (it.next()) it.getBytes(1) else null }
                }
                override fun put(key: String, value: ByteArray) { db.prepareStatement("INSERT OR REPLACE INTO entries VALUES(?,?)").use {
                    it.setString(1, key); it.setBytes(2, value); check(it.executeUpdate() == 1)
                } }
                override fun remove(key: String) { db.prepareStatement("DELETE FROM entries WHERE key=?").use { it.setString(1, key); it.executeUpdate() } }
                override fun page(prefix: String, cursor: String, limit: Int): List<SpaceClientStore.Entry> {
                    require(limit in 1..100)
                    return db.prepareStatement("SELECT key,value FROM entries WHERE key>=? AND key<? AND key>=? ORDER BY key LIMIT ?").use { sql ->
                        sql.setString(1, prefix); sql.setString(2, prefix + '\uffff'); sql.setString(3, cursor); sql.setInt(4, limit)
                        sql.executeQuery().use { rows -> buildList { while (rows.next()) add(SpaceClientStore.Entry(rows.getString(1), rows.getBytes(2))) } }
                    }
                }
            })
            db.commit(); return value
        } catch (failure: Throwable) { db.rollback(); throw failure }
        finally { db.autoCommit = true }
    }
}
