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

    @Test fun anUnadmittedExpiredRequestCanUseANewInvitationWithoutLosingItsDraft() = runBlocking {
        val milliseconds = java.util.concurrent.atomic.AtomicLong(clock.millis())
        val moving = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(milliseconds.get())
        }
        val guest = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        SpacesServiceStore(files.newFolder("expired-unadmitted").toPath(), service, moving).use { remote ->
            val inviter = SpaceClient(owner, "fixture-node", service.publicKey, local("retry-inviter.sqlite"), moving) { remote.exchange(it.envelope()) }
            val guestClient = SpaceClient(guest, "fixture-node", service.publicKey, local("retry-invitee.sqlite"), moving) { remote.exchange(it.envelope()) }
            val space = inviter.create("A renewed invitation")
            inviter.flush(space)
            val first = inviter.invite(space)
            inviter.flush(space)
            guestClient.join(first); guestClient.flush(space)
            guestClient.saveDraft(space, "Writing while waiting for admission")
            milliseconds.addAndGet(8 * 86_400_000L)
            val replacement = inviter.invite(space)
            inviter.flush(space)
            assertEquals(space, guestClient.join(replacement))
            guestClient.flush(space); inviter.admitPending(space); inviter.flush(space)
            guestClient.refresh(space)
            assertTrue(guestClient.community(space).joined)
            assertEquals("Writing while waiting for admission", guestClient.draft(space))
            guestClient.send(space, guestClient.draft(space))
            guestClient.flush(space); inviter.refresh(space)
            assertEquals(listOf("Writing while waiting for admission"), inviter.messages(space))
        }
    }

    @Test fun acceptedMembershipRecoversWhenItsInvitationHasExpiredBeforeTheDeviceReturns() = runBlocking {
        val milliseconds = java.util.concurrent.atomic.AtomicLong(clock.millis())
        val moving = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(milliseconds.get())
        }
        val guest = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val ownerStore = local("expiry-owner.sqlite")
        val guestStore = local("expiry-guest.sqlite")
        SpacesServiceStore(files.newFolder("expiry-before-return").toPath(), service, moving).use { remote ->
            val inviter = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, moving) { remote.exchange(it.envelope()) }
            fun guestClient() = SpaceClient(guest, "fixture-node", service.publicKey, guestStore, moving) { remote.exchange(it.envelope()) }
            val space = inviter.create("An accepted invitation")
            inviter.flush(space)
            inviter.send(space, "Earlier history remains available")
            inviter.flush(space)
            val link = inviter.invite(space)
            inviter.flush(space)
            guestClient().join(link); guestClient().flush(space)
            inviter.admitPending(space); inviter.flush(space)
            // The member was accepted while the link was valid. It did not
            // download its Welcome before going offline for eight days.
            milliseconds.addAndGet(8 * 86_400_000L)
            val reopened = guestClient()
            reopened.refresh(space)
            assertTrue(reopened.community(space).joined)
            assertEquals(listOf("Earlier history remains available"), reopened.messages(space))
            reopened.send(space, "Returned with the original admitted identity")
            reopened.flush(space); inviter.refresh(space)
            assertTrue(inviter.messages(space).contains("Returned with the original admitted identity"))
        }
    }

    @Test fun versionTwoAcceptedContentMigratesInBatchesWithoutChangingOriginalsOrReceipts() = runBlocking {
        val originalDirectory = files.newFolder("migration-original").toPath()
        val migratedDirectory = files.newFolder("migration-version-two").toPath()
        val storage = local("migration-client.sqlite")
        val originals = mutableListOf<Pair<String, String>>()
        var space = ""
        SpacesServiceStore(originalDirectory, service, clock).use { remote ->
            val client = SpaceClient(owner, "fixture-node", service.publicKey, storage, clock) { original ->
                remote.exchange(original.envelope()).also { response ->
                    if (SaitoEnvelope.decode(original.envelope()).body.get("request").asString == "event")
                        originals += original.envelope() to response
                }
            }
            space = client.create("Preserved older storage")
            client.flush(space)
            for (index in 0..104) client.send(space, "Migration message $index")
            repeat(3) { client.flush(space) }
        }
        // Independently construct the published version-two columns, with
        // genuine accepted data. This is not an empty or downgraded live DB.
        DriverManager.getConnection("jdbc:sqlite:" + migratedDirectory.resolve("spaces.sqlite")).use { db ->
            db.prepareStatement("ATTACH DATABASE ? AS previous").use {
                it.setString(1, originalDirectory.resolve("spaces.sqlite").toString()); it.execute()
            }
            db.createStatement().use { sql ->
                sql.execute("CREATE TABLE service_metadata(id INTEGER PRIMARY KEY CHECK(id=1),service_key TEXT NOT NULL)")
                sql.execute("INSERT INTO service_metadata SELECT * FROM previous.service_metadata")
                for (table in listOf("controls", "joins")) {
                    sql.execute("""CREATE TABLE $table (
                        space_id TEXT NOT NULL, position INTEGER NOT NULL CHECK(position>=0),
                        event_id TEXT NOT NULL, operation_id TEXT NOT NULL, actor TEXT NOT NULL,
                        original TEXT NOT NULL, receipt TEXT NOT NULL, accepted_at INTEGER NOT NULL,
                        PRIMARY KEY(space_id,position), UNIQUE(space_id,event_id), UNIQUE(space_id,operation_id))""")
                    sql.execute("INSERT INTO $table SELECT * FROM previous.$table")
                }
                sql.execute("""CREATE TABLE records (
                    space_id TEXT NOT NULL, channel_id TEXT NOT NULL, position INTEGER NOT NULL CHECK(position>=0),
                    event_id TEXT NOT NULL, operation_id TEXT NOT NULL, actor TEXT NOT NULL,
                    control_id TEXT NOT NULL, epoch INTEGER NOT NULL CHECK(epoch>=0), generation TEXT NOT NULL,
                    original TEXT NOT NULL, receipt TEXT NOT NULL, accepted_at INTEGER NOT NULL,
                    PRIMARY KEY(space_id,channel_id,position), UNIQUE(space_id,event_id), UNIQUE(space_id,operation_id),
                    FOREIGN KEY(space_id,control_id) REFERENCES controls(space_id,event_id))""")
                sql.execute("""INSERT INTO records SELECT space_id,channel_id,position,event_id,operation_id,actor,
                    control_id,epoch,generation,original,receipt,accepted_at FROM previous.records""")
                sql.execute("PRAGMA user_version=2")
                sql.execute("DETACH DATABASE previous")
            }
        }
        repeat(2) {
            SpacesServiceStore(migratedDirectory, service, clock).use { remote ->
                for ((original, receipt) in originals) assertEquals(receipt, remote.submit(original))
                val reopened = SpaceClient(owner, "fixture-node", service.publicKey, storage, clock) { remote.exchange(it.envelope()) }
                repeat(3) { reopened.refresh(space) }
                assertEquals("Migration message 104", reopened.messages(space).last())
                var from = reopened.older(space)
                while (from > 0) from = reopened.older(space)
                assertEquals("Migration message 0", reopened.timeline(space, from).first().text)
            }
        }
    }

    @Test fun anExpiredInvitationIsReportedAndCanBeReplacedWithANewChosenLifetime() = runBlocking {
        val milliseconds = java.util.concurrent.atomic.AtomicLong(clock.millis())
        val movingClock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(milliseconds.get())
        }
        val storage = local("expiring-invitation.sqlite")
        SpacesServiceStore(files.newFolder("expiring-service").toPath(), service, movingClock).use { remote ->
            val client = SpaceClient(owner, "fixture-node", service.publicKey, storage, movingClock) { remote.exchange(it.envelope()) }
            val space = client.create("An invitation that expires")
            client.flush(space)
            val old = client.invite(space)
            client.flush(space)
            assertTrue(requireNotNull(client.invitation(space)).confirmed)
            milliseconds.addAndGet(8 * 86_400_000L)
            assertTrue(requireNotNull(client.invitation(space)).expired)
            val replacement = client.invite(space, expiresAfterMillis = 86_400_000)
            client.flush(space)
            assertNotEquals(old, replacement)
            val active = requireNotNull(client.invitation(space))
            assertTrue(active.confirmed); assertFalse(active.expired)
            assertEquals(milliseconds.get() + 86_400_000, active.expiresAt)
        }
    }

    @Test fun everyCommunityRemainsReachableAndDeliversItsOriginalBeyondTheFirstLocalPage() = runBlocking {
        val storage = local("fifty-one-communities.sqlite")
        SpacesServiceStore(files.newFolder("fifty-one-service").toPath(), service, clock).use { remote ->
            val client = SpaceClient(owner, "fixture-node", service.publicKey, storage, clock) { remote.exchange(it.envelope()) }
            val ids = (0..50).map { client.create("Community $it") }
            assertEquals(51, client.communities().size)
            assertEquals(ids.toSet(), client.communities().map { it.id }.toSet())
            for (community in client.communities()) client.flush(community.id)
            val reopened = SpaceClient(owner, "fixture-node", service.publicKey, storage, clock) { remote.exchange(it.envelope()) }
            assertEquals(51, reopened.communities().count { it.joined && !it.pending })
        }
    }

    @Test fun aColdJoinOpensRecentMessagesWithTheirKeysAndCanLoadAnEarlierPage() = runBlocking {
        val guest = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        SpacesServiceStore(files.newFolder("paged-community").toPath(), service, clock).use { remote ->
            val ownerClient = SpaceClient(owner, "fixture-node", service.publicKey, local("paged-owner.sqlite"), clock) { remote.exchange(it.envelope()) }
            val guestClient = SpaceClient(guest, "fixture-node", service.publicKey, local("paged-guest.sqlite"), clock) { remote.exchange(it.envelope()) }
            val space = ownerClient.create("A busy conversation")
            ownerClient.flush(space)
            for (index in 0..69) ownerClient.send(space, "Post $index")
            ownerClient.flush(space); ownerClient.flush(space)
            val link = ownerClient.invite(space)
            ownerClient.flush(space)
            guestClient.join(link); guestClient.flush(space)
            ownerClient.admitPending(space); ownerClient.flush(space)
            guestClient.refresh(space)
            assertTrue(guestClient.messages(space).contains("Post 69"))
            assertTrue(guestClient.timeline(space).all { !it.missingKeys && it.text != null })
            assertTrue(guestClient.timeline(space).size <= 50)
            val from = guestClient.older(space)
            assertEquals("Post 0", guestClient.timeline(space, from = from).first().text)
        }
    }

    @Test fun aMemberWhoReturnsAfterAnotherAdmissionCanStillRecoverItsEarlierHistoryGrant() = runBlocking {
        val guest = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val newcomer = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        SpacesServiceStore(files.newFolder("late-history-grant").toPath(), service, clock).use { remote ->
            val ownerClient = SpaceClient(owner, "fixture-node", service.publicKey, local("late-owner.sqlite"), clock) { remote.exchange(it.envelope()) }
            val guestClient = SpaceClient(guest, "fixture-node", service.publicKey, local("late-guest.sqlite"), clock) { remote.exchange(it.envelope()) }
            val nextClient = SpaceClient(newcomer, "fixture-node", service.publicKey, local("late-next.sqlite"), clock) { remote.exchange(it.envelope()) }
            val space = ownerClient.create("Available history")
            ownerClient.flush(space)
            ownerClient.send(space, "Earlier text before either member joined")
            ownerClient.flush(space)
            val link = ownerClient.invite(space)
            ownerClient.flush(space)
            guestClient.join(link); guestClient.flush(space)
            ownerClient.admitPending(space); ownerClient.flush(space)
            // This member has not consumed its Welcome or key grant yet.
            nextClient.join(link); nextClient.flush(space)
            ownerClient.admitPending(space); ownerClient.flush(space)
            guestClient.refresh(space)
            assertEquals(listOf("Earlier text before either member joined"), guestClient.messages(space))
            guestClient.send(space, "Back after the community grew")
            guestClient.flush(space); ownerClient.refresh(space)
            assertEquals(listOf("Earlier text before either member joined", "Back after the community grew"), ownerClient.messages(space))
        }
    }

    @Test fun anEligibleJoinAfterFiftyInactiveRequestsIsStillAdmitted() = runBlocking {
        val guest = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val newcomer = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        SpacesServiceStore(files.newFolder("many-join-requests").toPath(), service, clock).use { remote ->
            val ownerClient = SpaceClient(owner, "fixture-node", service.publicKey, local("many-owner.sqlite"), clock) { remote.exchange(it.envelope()) }
            var signedJoin: nl.davidengelen.redsquare.saito.SaitoTransaction? = null
            val guestClient = SpaceClient(guest, "fixture-node", service.publicKey, local("many-guest.sqlite"), clock) {
                if (SaitoEnvelope.decode(it.envelope()).body.get("kind")?.asString == "join") signedJoin = it
                remote.exchange(it.envelope())
            }
            val nextClient = SpaceClient(newcomer, "fixture-node", service.publicKey, local("many-next.sqlite"), clock) { remote.exchange(it.envelope()) }
            val space = ownerClient.create("A welcoming community")
            ownerClient.flush(space)
            val link = ownerClient.invite(space)
            ownerClient.flush(space)
            guestClient.join(link); guestClient.flush(space)
            val capability = com.google.gson.JsonParser.parseString(java.util.Base64.getUrlDecoder()
                .decode(java.net.URI(link).rawFragment).toString(Charsets.UTF_8)).asJsonObject
            val bearer = SaitoWallet.fromPrivateKey(capability.get("capability").asString)
            val rawScope = capability.getAsJsonObject("scope")
            val scope = nl.davidengelen.redsquare.spaces.SpaceCrypto.Scope(service.publicKey, space,
                rawScope.get("channel").asString, rawScope.get("generation").asString)
            val first = SaitoEnvelope.decode(requireNotNull(signedJoin).envelope()).body
            // These are genuine signed requests queued while the identity has
            // not yet joined. Its later admission makes all fifty inactive.
            for (index in 1..49) {
                val operation = (1000 + index).toString(16).padStart(64, '0')
                val body = first.deepCopy().apply {
                    addProperty("operation_id", operation)
                    val binding = getAsJsonObject("key_package").get("signature").asString
                    addProperty("invite_proof", bearer.sign(("SaitoSpacesJoin/v1:${scope.groupId}:" +
                        "${get("invite_id").asString}:${guest.publicKey}:$operation:$binding").toByteArray(Charsets.UTF_8)))
                }
                remote.exchange(nl.davidengelen.redsquare.saito.SaitoTransaction.create(guest, body, clock.millis() + index,
                    listOf(service.publicKey)).envelope())
            }
            ownerClient.admitPending(space); ownerClient.flush(space)
            nextClient.join(link); nextClient.flush(space)
            ownerClient.admitPending(space); ownerClient.flush(space)
            nextClient.refresh(space)
            assertTrue(nextClient.community(space).joined)
        }
    }

    @Test fun queuedContentSurvivesAnUnrelatedControlAndAFencedKeyChangeDoesNotBlockLaterMessages() = runBlocking {
        val guest = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val newcomer = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        SpacesServiceStore(files.newFolder("changing-community").toPath(), service, clock).use { remote ->
            val ownerClient = SpaceClient(owner, "fixture-node", service.publicKey, local("changing-owner.sqlite"), clock) { remote.exchange(it.envelope()) }
            val guestClient = SpaceClient(guest, "fixture-node", service.publicKey, local("changing-guest.sqlite"), clock) { remote.exchange(it.envelope()) }
            val nextClient = SpaceClient(newcomer, "fixture-node", service.publicKey, local("changing-next.sqlite"), clock) { remote.exchange(it.envelope()) }
            val space = ownerClient.create("A changing community")
            ownerClient.flush(space)
            val firstLink = ownerClient.invite(space)
            ownerClient.flush(space)
            guestClient.join(firstLink); guestClient.flush(space)
            ownerClient.admitPending(space); ownerClient.flush(space); guestClient.refresh(space)
            guestClient.send(space, "Queued before the next invitation")
            val nextLink = ownerClient.invite(space)
            ownerClient.flush(space)
            guestClient.flush(space)
            ownerClient.refresh(space)
            assertEquals(listOf("Queued before the next invitation"), ownerClient.messages(space))
            val old = guestClient.send(space, "Original waiting through a key change")
            nextClient.join(nextLink); nextClient.flush(space)
            ownerClient.admitPending(space); ownerClient.flush(space)
            guestClient.flush(space)
            guestClient.refresh(space)
            assertTrue(guestClient.timeline(space).single { it.id == old }.rejected)
            guestClient.send(space, "Fresh text after the key change")
            guestClient.flush(space); ownerClient.refresh(space)
            assertEquals(listOf("Queued before the next invitation", "Fresh text after the key change"), ownerClient.messages(space))
        }
    }

    @Test fun aRejectedAdmissionReopensOnItsCancelledBranchAndCanAdmitTheSameRequest() = runBlocking {
        val guest = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val storage = local("rejected-admission-owner.sqlite")
        val directory = files.newFolder("rejected-admission-service").toPath()
        var remote = SpacesServiceStore(directory, service, clock)
        var invitation: nl.davidengelen.redsquare.saito.SaitoTransaction? = null
        var rejectedOriginal: nl.davidengelen.redsquare.saito.SaitoTransaction? = null
        fun ownerClient() = SpaceClient(owner, "fixture-node", service.publicKey, storage, clock) { original ->
            when (SaitoEnvelope.decode(original.envelope()).body.get("kind")?.asString) {
                "invite" -> invitation = original
                "admit" -> if (rejectedOriginal == null) rejectedOriginal = original
            }
            remote.exchange(original.envelope())
        }
        try {
            var client = ownerClient()
            val guestClient = SpaceClient(guest, "fixture-node", service.publicKey, local("rejected-admission-guest.sqlite"), clock) {
                remote.exchange(it.envelope())
            }
            val space = client.create("Admission after a concurrent change")
            client.flush(space)
            val link = client.invite(space)
            client.flush(space)
            guestClient.join(link); guestClient.flush(space)
            client.admitPending(space)
            // A real signed control wins the compare-and-set before this
            // pending MLS proposal is delivered. No mocked rejection result.
            val prior = requireNotNull(invitation)
            val concurrentBody = SaitoEnvelope.decode(prior.envelope()).body.deepCopy().apply {
                addProperty("operation_id", "9".repeat(64)); addProperty("control_id", prior.id)
            }
            val concurrent = nl.davidengelen.redsquare.saito.SaitoTransaction.create(owner, concurrentBody,
                clock.millis() + 1, listOf(service.publicKey))
            remote.submit(concurrent.envelope())
            client.flush(space)
            val rejected = requireNotNull(rejectedOriginal)
            val disposition = remote.submit(rejected.envelope())
            assertEquals("rejection", SaitoEnvelope.decode(disposition).body.get("stream").asString)
            remote.close(); remote = SpacesServiceStore(directory, service, clock)
            assertEquals(disposition, remote.submit(rejected.envelope()))
            client = ownerClient()
            client.refresh(space)
            client.admitPending(space); client.flush(space)
            guestClient.refresh(space)
            assertTrue(guestClient.community(space).joined)
            guestClient.send(space, "Joined after a durably rejected proposal")
            guestClient.flush(space); client.refresh(space)
            assertEquals(listOf("Joined after a durably rejected proposal"), client.messages(space))
            // Even after later successful membership, the rejected original
            // remains fenced and returns its identical terminal disposition.
            assertEquals(disposition, remote.submit(rejected.envelope()))
        } finally { remote.close() }
    }

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
