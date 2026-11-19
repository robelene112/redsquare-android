package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceCrypto
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.sql.DriverManager

class SpaceContentTest {
    @get:Rule val files = TemporaryFolder()
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures only.
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val outsider = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private val scope = SpaceCrypto.Scope(service.publicKey, "a".repeat(64), "b".repeat(64))

    @Test fun aPrivateChannelReturnsAuthenticatedCiphertextToItsMemberWithoutStoringPlaintextOrServingAnOutsider() {
        val creator = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope)
        val creation = SaitoTransaction.create(owner, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
            addProperty("space_id", scope.spaceId); addProperty("operation_id", "d".repeat(64)); addProperty("kind", "create")
            addProperty("visibility", "private"); addProperty("channel_id", scope.channelId); addProperty("epoch", 0)
            addProperty("metadata", Base64.getEncoder().encodeToString(ByteArray(40) { 11 }))
            add("devices", JsonObject().apply { creator.bindings.forEach { (device, signer) -> addProperty(device, signer) } })
        }, clock.millis(), listOf(service.publicKey))
        val text = "A private message never stored in plaintext"
        val original = creator.publish(owner, "message", "e".repeat(64), creation.id, text, clock.millis() + 1)
        val directory = files.newFolder("private-content").toPath()
        SpacesServiceStore(directory, service, clock).use { store ->
            store.submit(creation.envelope())
            val receipt = store.submit(original.envelope())
            val verified = SaitoEnvelope.decode(receipt)
            assertEquals(service.publicKey, verified.author)
            assertEquals("content", verified.body.get("stream").asString)
            assertEquals(scope.channelId, verified.body.get("channel_id").asString)
            assertEquals(0L, verified.body.get("position").asLong)
            assertEquals(receipt, store.submit(original.envelope()))
            val page = read(store, owner)
            assertEquals(listOf(receipt), page.getAsJsonArray("items").map { it.asString })
            val received = SaitoEnvelope.decode(page.getAsJsonArray("items")[0].asString).body.get("event").asString
            assertEquals(text, creator.read(received).text)
            assertThrows(IllegalArgumentException::class.java) { read(store, outsider) }
        }
        Files.list(directory).use { paths -> paths.filter(Files::isRegularFile).forEach { path ->
            assertFalse("Service storage contains only ciphertext and routing metadata",
                Files.readAllBytes(path).toString(Charsets.ISO_8859_1).contains(text))
        } }
    }

    @Test fun twoIndependentMembersRecoverEarlierTextAndExchangeAfterReopeningTheService() {
        val guest = outsider
        val bearer = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        var creator = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope)
        val device = SpaceCrypto.device(guest.publicKey, "6".repeat(64))
        val keyPackage = device.keyPackage(scope, guest)
        fun event(actor: SaitoWallet, kind: String, operation: Char, extra: JsonObject.() -> Unit) =
            SaitoTransaction.create(actor, JsonObject().apply {
                addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
                addProperty("space_id", scope.spaceId); addProperty("operation_id", operation.toString().repeat(64))
                addProperty("kind", kind); extra()
            }, clock.millis(), listOf(service.publicKey))
        val creation = event(owner, "create", '1') {
            addProperty("visibility", "private"); addProperty("channel_id", scope.channelId); addProperty("epoch", 0)
            addProperty("metadata", Base64.getEncoder().encodeToString(ByteArray(40) { 11 }))
            add("devices", JsonObject().apply { creator.bindings.forEach { (key, value) -> addProperty(key, value) } })
        }
        val invitation = event(owner, "invite", '2') {
            addProperty("control_id", creation.id); addProperty("invite_key", bearer.publicKey)
            addProperty("expires_at", clock.millis() + 604_800_000)
        }
        val join = event(guest, "join", '3') {
            addProperty("channel_id", scope.channelId); addProperty("control_id", invitation.id); addProperty("invite_id", invitation.id)
            add("key_package", JsonObject().apply {
                addProperty("wallet", guest.publicKey); addProperty("device", keyPackage.deviceId)
                addProperty("package", keyPackage.packageBytes); addProperty("signer", keyPackage.signer)
                addProperty("signature", keyPackage.walletSignature)
            })
            addProperty("invite_proof", bearer.sign(("SaitoSpacesJoin/v1:${scope.groupId}:${invitation.id}:" +
                "${guest.publicKey}:${"3".repeat(64)}:${keyPackage.walletSignature}").toByteArray(Charsets.UTF_8)))
        }
        val old = creator.publish(owner, "message", "4".repeat(64), invitation.id, "Before you joined", clock.millis())
        val admission = creator.admit(keyPackage)
        val admit = event(owner, "admit", '5') {
            addProperty("control_id", invitation.id); addProperty("channel_id", scope.channelId)
            addProperty("epoch", admission.session.epoch); addProperty("join", join.envelope())
            addProperty("welcome", admission.welcome); addProperty("commit", admission.commit)
            add("devices", JsonObject().apply { admission.bindings.forEach { (key, value) -> addProperty(key, value) } })
        }
        var member = device.join(scope, admission.welcome, admission.bindings)
        assertThrows(SpaceCrypto.MissingKeys::class.java) { member.read(old.envelope()) }
        creator = admission.session
        val grant = creator.history(owner, member.credential, "7".repeat(64), admit.id, clock.millis())
        creator = grant.session
        val directory = files.newFolder("two-members").toPath()
        val accepted = SpacesServiceStore(directory, service, clock).use { store ->
            store.submit(creation.envelope()); store.submit(invitation.envelope())
            store.submit(old.envelope()); store.submit(join.envelope()); store.submit(admit.envelope())
            store.submit(grant.transaction.envelope())
        }
        // Each identity restores its own separate serialized MLS state.
        creator = SpaceCrypto.restore(creator.save())
        member = SpaceCrypto.restore(member.save())
        SpacesServiceStore(directory, service, clock).use { store ->
            assertEquals(accepted, store.submit(grant.transaction.envelope()))
            val page = read(store, guest).getAsJsonArray("items")
            assertEquals(2, page.size())
            val history = SaitoEnvelope.decode(page[1].asString).body.get("event").asString
            member = member.acceptHistory(history)
            assertEquals("Before you joined", member.read(SaitoEnvelope.decode(page[0].asString).body.get("event").asString).text)
            val response = member.publish(guest, "message", "8".repeat(64), admit.id, "Thanks for the invitation", clock.millis())
            store.submit(response.envelope())
            val received = read(store, owner).getAsJsonArray("items")[2].asString
            assertEquals("Thanks for the invitation", creator.read(SaitoEnvelope.decode(received).body.get("event").asString).text)
        }
    }

    @Test fun versionOneStorageRejectsForeignIdentityBeforeMigrationAndPreservesLegacyTables() {
        val directory = files.newFolder("version-one").toPath()
        // Independent schema-v1 fixture, before content storage existed.
        DriverManager.getConnection("jdbc:sqlite:${directory.resolve("spaces.sqlite")}").use { db ->
            db.createStatement().use { sql ->
                sql.execute("CREATE TABLE service_metadata(id INTEGER PRIMARY KEY CHECK(id=1),service_key TEXT NOT NULL)")
                for (table in listOf("controls", "joins")) sql.execute("""CREATE TABLE $table (
                    space_id TEXT NOT NULL, position INTEGER NOT NULL CHECK(position>=0),
                    event_id TEXT NOT NULL, operation_id TEXT NOT NULL, actor TEXT NOT NULL,
                    original TEXT NOT NULL, receipt TEXT NOT NULL, accepted_at INTEGER NOT NULL,
                    PRIMARY KEY(space_id,position), UNIQUE(space_id,event_id), UNIQUE(space_id,operation_id))""")
                sql.execute("PRAGMA user_version=1")
            }
            db.prepareStatement("INSERT INTO service_metadata VALUES(1,?)").use { sql ->
                sql.setString(1, service.publicKey); sql.executeUpdate()
            }
        }
        val before = Files.readAllBytes(directory.resolve("spaces.sqlite"))
        assertThrows(IllegalStateException::class.java) { SpacesServiceStore(directory, outsider, clock) }
        DriverManager.getConnection("jdbc:sqlite:${directory.resolve("spaces.sqlite")}").use { db ->
            db.createStatement().use { sql -> sql.executeQuery("PRAGMA user_version").use { rows ->
                assertTrue(rows.next()); assertEquals(1, rows.getInt(1))
            } }
        }
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("spaces.sqlite")))
        SpacesServiceStore(directory, service, clock).close()
        DriverManager.getConnection("jdbc:sqlite:${directory.resolve("spaces.sqlite")}").use { db ->
            db.createStatement().use { sql ->
                sql.executeQuery("PRAGMA user_version").use { rows -> assertTrue(rows.next()); assertEquals(4, rows.getInt(1)) }
                // Old controls and joins remain readable. No destructive down-migration is performed.
                for (table in listOf("controls", "joins", "records", "rejections", "public_spaces")) sql.executeQuery("SELECT count(*) FROM $table").use { rows ->
                    assertTrue(rows.next()); assertEquals(0, rows.getInt(1))
                }
            }
        }
    }

    private fun read(store: SpacesServiceStore, actor: SaitoWallet): JsonObject {
        val target = JsonObject().apply {
            addProperty("space_id", scope.spaceId); addProperty("stream", "content")
            addProperty("channel_id", scope.channelId); addProperty("cursor", 0)
        }
        fun request(kind: String, nonce: String? = null) = SaitoTransaction.create(actor, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", kind); addProperty("version", 1)
            addProperty("request_id", "f".repeat(64)); add("scope", target.deepCopy())
            nonce?.let { addProperty("nonce", it) }
        }, clock.millis(), listOf(service.publicKey)).envelope()
        val challenge = SaitoEnvelope.decode(store.exchange(request("challenge")))
        assertEquals(service.publicKey, challenge.author)
        val page = SaitoEnvelope.decode(store.exchange(request("read", challenge.body.get("nonce").asString)))
        assertEquals(service.publicKey, page.author)
        assertEquals(actor.publicKey, page.body.get("caller").asString)
        assertEquals(target, page.body.getAsJsonObject("scope"))
        return page.body
    }
}
