package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceJournal
import nl.davidengelen.redsquare.spaces.SpaceCrypto
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SpacesServiceStoreTest {
    @get:Rule val files = TemporaryFolder()
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe test identities only.
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)

    @Test fun readinessReportsOnlyTheActualLocalServiceIdentityAndStorageVersion() {
        SpacesServiceServer(files.newFolder("readiness").toPath(), service, clock).use { server ->
            server.start()
            val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/v1/readiness"))
                .timeout(java.time.Duration.ofSeconds(5)).GET().build()
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, response.statusCode())
            val body = com.google.gson.JsonParser.parseString(response.body()).asJsonObject
            assertEquals("spaces-v1", body.get("capability").asString)
            assertEquals(service.publicKey, body.get("service_key").asString)
            assertEquals(4, body.get("schema").asInt)
            assertEquals("ready", body.get("state").asString)
            assertFalse(body.get("file_hosting").asBoolean)
            assertEquals(listOf("unread-v1", "channel-archive-v1", "role-management-v1", "public-channel-edit-v1", "private-channel-access-v1", "private-channel-edit-v1", "membership-management-v1", "ownership-management-v1", "category-management-v1", "visibility-management-v1", "dissolution-v1"), body.getAsJsonArray("features").map { it.asString })
        }
    }

    @Test(timeout = 45_000) fun authenticatedPrivateReadingBindsTheNonceToItsCallerAndConsumesItOnce() {
        val outsider = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val scope = JsonObject().apply {
            addProperty("space_id", "a".repeat(64)); addProperty("stream", "control"); addProperty("cursor", 0)
        }
        fun request(actor: SaitoWallet, kind: String, nonce: String? = null) = SaitoTransaction.create(actor, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", kind); addProperty("version", 1)
            addProperty("request_id", "f".repeat(64)); add("scope", scope.deepCopy())
            nonce?.let { addProperty("nonce", it) }
        }, clock.millis(), listOf(service.publicKey)).envelope()
        SpacesServiceServer(files.newFolder("private-reading").toPath(), service, clock).use { server ->
            server.start()
            val original = creation()
            val accepted = post(server.port, original.envelope())
            val challenge = SaitoEnvelope.decode(post(server.port, request(owner, "challenge")))
            assertEquals(service.publicKey, challenge.author)
            val nonce = challenge.body.get("nonce").asString
            assertEquals(409, http(server.port, request(outsider, "read", nonce)).statusCode())
            val otherNonce = SaitoEnvelope.decode(post(server.port, request(outsider, "challenge"))).body.get("nonce").asString
            // An authenticated denied read is a signed disposition, not an
            // unsigned transport failure. Invalid caller/nonces stay409.
            val deniedResponse = http(server.port, request(outsider, "read", otherNonce))
            assertEquals(200, deniedResponse.statusCode())
            val denied = SaitoEnvelope.decode(deniedResponse.body())
            assertEquals(service.publicKey, denied.author)
            assertEquals(listOf(service.publicKey, outsider.publicKey), denied.recipients)
            assertEquals("denied", denied.body.get("request").asString)
            assertEquals("unavailable", denied.body.get("reason").asString)
            assertEquals(outsider.publicKey, denied.body.get("caller").asString)
            assertEquals("f".repeat(64), denied.body.get("request_id").asString)
            assertEquals(scope, denied.body.getAsJsonObject("scope"))
            assertEquals(otherNonce, denied.body.get("nonce").asString)
            assertFalse(denied.body.has("items"))
            assertFalse(denied.body.has("next_cursor"))
            assertEquals(409, http(server.port, request(outsider, "read", otherNonce)).statusCode())
            val read = request(owner, "read", nonce)
            val page = SaitoEnvelope.decode(post(server.port, read))
            assertEquals(service.publicKey, page.author)
            assertEquals("page", page.body.get("request").asString)
            assertEquals(owner.publicKey, page.body.get("caller").asString)
            assertEquals(scope, page.body.getAsJsonObject("scope"))
            assertEquals(listOf(accepted), page.body.getAsJsonArray("items").map { it.asString })
            assertEquals(1L, page.body.get("next_cursor").asLong)
            assertEquals(409, http(server.port, read).statusCode())
        }
    }

    @Test(timeout = 30_000) fun anActualServiceProcessTerminationRetainsTheOriginalAcceptedOperation() {
        val root = files.newFolder("process-community").toPath()
        val identity = Files.createFile(root.resolve("identity.json"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        // Entirely public unsafe identity; no owner/node wallet file is read.
        Files.writeString(identity, JsonObject().apply {
            addProperty("version", 1); addProperty("publicKey", service.publicKey)
            addProperty("privateKey", "0".repeat(63) + "2")
        }.toString())
        val original = creation()
        val first = withServiceProcess(root.resolve("state"), identity) { port, pid ->
            pid to post(port, original.envelope())
        }
        val second = withServiceProcess(root.resolve("state"), identity) { port, pid ->
            pid to post(port, original.envelope())
        }
        assertNotEquals(first.first, second.first)
        assertEquals(first.second, second.second)
        assertEquals(original.id, SaitoEnvelope.decode(SaitoEnvelope.decode(second.second).body.get("event").asString).id)
    }

    private fun <T> withServiceProcess(directory: Path, identity: Path, action: (Int, Long) -> T): T {
        val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
            "-cp", System.getProperty("spaces.service.runtimeClasspath"), SpacesServiceMain::class.java.name).apply {
            environment()["SPACES_STATE_DIR"] = directory.toString()
            environment()["SPACES_IDENTITY_FILE"] = identity.toString()
            environment()["SPACES_LISTEN_PORT"] = "0"
            redirectErrorStream(true)
        }.start()
        val reader = Executors.newSingleThreadExecutor()
        var originalFailure: Throwable? = null
        try {
            val line = reader.submit<String?> { process.inputStream.bufferedReader().use { it.readLine() } }.get(10, TimeUnit.SECONDS)
            val parts = requireNotNull(line).split(' ')
            check(parts.size == 3 && parts[0] == "SPACES_READY" && parts[2] == service.publicKey)
            return action(parts[1].toInt(), process.pid())
        } catch (failure: Throwable) { originalFailure = failure; throw failure }
        finally {
            var cleanupFailure: Throwable? = null
            for (cleanup in listOf<() -> Unit>({
                process.destroyForcibly()
                check(process.waitFor(5, TimeUnit.SECONDS)) { "Owned service process did not exit" }
            }, {
                reader.shutdownNow()
                check(reader.awaitTermination(5, TimeUnit.SECONDS)) { "Owned service reader did not stop" }
            })) try { cleanup() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure.addSuppressed(failure)
            }
            cleanupFailure?.let { if (originalFailure == null) throw it else originalFailure.addSuppressed(it) }
        }
    }

    private fun post(port: Int, original: String): String {
        val received = http(port, original)
        assertEquals(200, received.statusCode())
        return received.body()
    }

    private fun http(port: Int, original: String): HttpResponse<String> {
        val client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/v1/exchange"))
            .timeout(java.time.Duration.ofSeconds(10)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(original)).build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test fun aSignedJoinIsStoredAsARequestWithoutPretendingMembershipOrKeysAreReady() {
        val guest = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val bearer = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        val scope = SpaceCrypto.Scope(service.publicKey, "a".repeat(64), "b".repeat(64))
        val keys = SpaceCrypto.device(guest.publicKey, "6".repeat(64))
        val keyPackage = keys.keyPackage(scope, guest)
        val original = creation()
        val invitation = SaitoTransaction.create(owner, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
            addProperty("space_id", scope.spaceId); addProperty("operation_id", "d".repeat(64)); addProperty("kind", "invite")
            addProperty("control_id", original.id); addProperty("invite_key", bearer.publicKey)
            addProperty("expires_at", clock.millis() + 604_800_000)
        }, clock.millis(), listOf(service.publicKey))
        val join = SaitoTransaction.create(guest, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
            addProperty("space_id", scope.spaceId); addProperty("channel_id", scope.channelId)
            addProperty("operation_id", "e".repeat(64)); addProperty("kind", "join")
            addProperty("control_id", invitation.id); addProperty("invite_id", invitation.id)
            add("key_package", JsonObject().apply {
                addProperty("wallet", guest.publicKey); addProperty("device", keyPackage.deviceId)
                addProperty("package", keyPackage.packageBytes); addProperty("signer", keyPackage.signer)
                addProperty("signature", keyPackage.walletSignature)
            })
            addProperty("invite_proof", bearer.sign(("SaitoSpacesJoin/v1:${scope.groupId}:${invitation.id}:" +
                "${guest.publicKey}:${"e".repeat(64)}:${keyPackage.walletSignature}").toByteArray(Charsets.UTF_8)))
        }, clock.millis(), listOf(service.publicKey))
        val directory = files.newFolder("joining").toPath()
        val receipt = SpacesServiceStore(directory, service, clock).use { store ->
            store.submit(original.envelope()); store.submit(invitation.envelope())
            store.submit(join.envelope())
        }
        val verified = SaitoEnvelope.decode(receipt)
        assertEquals(service.publicKey, verified.author)
        assertEquals("join", verified.body.get("stream").asString)
        assertEquals(join.envelope(), verified.body.get("event").asString)
        assertFalse(verified.body.has("welcome"))
        assertThrows(IllegalArgumentException::class.java) { SpaceJournal(scope.spaceId, service.publicKey).receive(receipt) }
        SpacesServiceStore(directory, service, clock).use { store ->
            assertEquals(receipt, store.submit(join.envelope()))
            val scope = JsonObject().apply {
                addProperty("space_id", "a".repeat(64)); addProperty("stream", "join"); addProperty("cursor", 0)
            }
            val page = readAs(store, owner, scope)
            assertEquals(listOf(receipt), page.body.getAsJsonArray("items").map { it.asString })
            assertEquals(1L, page.body.get("next_cursor").asLong)
            val denied = readAs(store, guest, scope)
            assertEquals("denied", denied.body.get("request").asString)
            assertEquals("unavailable", denied.body.get("reason").asString)
            assertFalse(denied.body.has("items"))
            assertFalse(denied.body.has("next_cursor"))
        }
    }

    private fun readAs(store: SpacesServiceStore, actor: SaitoWallet, scope: JsonObject): nl.davidengelen.redsquare.saito.SignedSaitoMessage {
        fun request(kind: String, nonce: String? = null) = SaitoTransaction.create(actor, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", kind); addProperty("version", 1)
            addProperty("request_id", "f".repeat(64)); add("scope", scope.deepCopy())
            nonce?.let { addProperty("nonce", it) }
        }, clock.millis(), listOf(service.publicKey)).envelope()
        val challenge = SaitoEnvelope.decode(store.exchange(request("challenge")))
        assertEquals(service.publicKey, challenge.author)
        val nonce = challenge.body.get("nonce").asString
        val page = SaitoEnvelope.decode(store.exchange(request("read", nonce)))
        assertEquals(service.publicKey, page.author)
        assertEquals(listOf(service.publicKey, actor.publicKey).distinct(), page.recipients)
        assertEquals("f".repeat(64), page.body.get("request_id").asString)
        assertEquals(nonce, page.body.get("nonce").asString)
        assertEquals(actor.publicKey, page.body.get("caller").asString)
        assertEquals(scope, page.body.getAsJsonObject("scope"))
        return page
    }

    @Test fun anIndependentHttpClientReceivesTheActualSignedDurableResult() {
        val directory = files.newFolder("http-community").toPath()
        val original = creation()
        SpacesServiceServer(directory, service, clock).use { server ->
            server.start()
            val client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()
            val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${server.port}/v1/exchange"))
                .timeout(java.time.Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(original.envelope())).build()
            val received = client.send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, received.statusCode())
            val authenticated = SaitoEnvelope.decode(received.body())
            assertEquals(service.publicKey, authenticated.author)
            assertEquals(original.envelope(), authenticated.body.get("event").asString)
            val replay = client.send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, replay.statusCode())
            assertEquals(received.body(), replay.body())
        }
    }

    @Test fun aLostCreationAcknowledgmentReopensWithTheOriginalReceiptAndOneOwner() {
        val original = creation()
        val directory = files.newFolder("community").toPath()
        val receipt = SpacesServiceStore(directory, service, clock).use { it.submit(original.envelope()) }
        // The first client loses the acknowledgment and discards its connection.
        SpacesServiceStore(directory, service, clock).use { reopened ->
            assertEquals(receipt, reopened.submit(original.envelope()))
            val body = SaitoEnvelope.decode(original.envelope()).body
            val changedOriginal = SaitoTransaction.create(owner, body, clock.millis() + 1, listOf(service.publicKey))
            assertThrows(IllegalArgumentException::class.java) { reopened.submit(changedOriginal.envelope()) }
        }
        val journal = SpaceJournal("a".repeat(64), service.publicKey)
        val accepted = journal.receive(receipt)
        assertEquals(owner.publicKey, accepted.owner)
        assertEquals(1, accepted.members.size)
        assertEquals(1L, accepted.nextPosition)
        assertEquals(accepted, journal.receive(receipt))
    }

    private fun creation(): SaitoTransaction {
        val body = JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
            addProperty("space_id", "a".repeat(64)); addProperty("operation_id", "c".repeat(64)); addProperty("kind", "create")
            addProperty("visibility", "private"); addProperty("channel_id", "b".repeat(64)); addProperty("epoch", 0)
            addProperty("metadata", Base64.getEncoder().encodeToString(ByteArray(40) { 11 }))
            add("devices", JsonObject().apply {
                addProperty("SaitoSpaces/v1:${owner.publicKey}:${"5".repeat(64)}", Base64.getEncoder().encodeToString(ByteArray(32) { 12 }))
            })
        }
        return SaitoTransaction.create(owner, body, clock.millis(), listOf(service.publicKey))
    }
}
