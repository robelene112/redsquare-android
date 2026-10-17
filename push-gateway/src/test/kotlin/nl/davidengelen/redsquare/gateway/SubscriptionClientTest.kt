package nl.davidengelen.redsquare.gateway

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.PushSubscriptionClient
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Duration

class SubscriptionClientTest {
    @get:Rule val directory = TemporaryFolder()

    @Test(timeout = 30_000) fun walletReplacementRetiresAnUncertainBindingWithoutTheOldWallet() {
        val origin = "https://push.fixture.invalid"
        val journal = directory.newFolder("client").toPath().resolve("journal")
        val firstWallet = SaitoWallet.create()
        val replacement = SaitoWallet.create()
        PushGateway(directory.newFolder("server").toPath(), origin).use { gateway ->
            gateway.start()
            val connection = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
            var uncertain: JsonObject? = null
            var latest: JsonObject? = null
            var authority: JsonObject? = null
            val http = PushSubscriptionClient.Http { requestedOrigin, path, body, signature ->
                assertEquals(origin, requestedOrigin)
                val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${gateway.port}$path"))
                    .timeout(Duration.ofSeconds(3)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                if (signature != null) request.header("X-Saito-Signature", signature)
                val response = connection.send(request.build(), HttpResponse.BodyHandlers.ofString())
                if (path == "/v1/subscriptions/bind") {
                    assertEquals("The real gateway must accept the client's signed contract", 201, response.statusCode())
                    val sent = JsonParser.parseString(body).asJsonObject
                    if (uncertain == null) {
                        uncertain = sent
                        throw IOException("Owned boundary drops the first acknowledgement after commit")
                    }
                    latest = sent
                }
                if (path == "/v1/subscriptions/revoke") {
                    assertNull("Retirement must not need the outgoing wallet's signature", signature)
                    assertEquals(200, response.statusCode())
                    authority = JsonParser.parseString(body).asJsonObject
                }
                PushSubscriptionClient.Reply(response.statusCode(), response.body())
            }
            try {
                PushSubscriptionClient(origin, DurableJournal(journal), http).reconcile {
                    PushSubscriptionClient.Demand(firstWallet, SaitoBackend.OWNER, "c-original-fid")
                }
                fail("A dropped acknowledgement must remain an uncertain operation")
            } catch (_: IOException) { /* A later process must reconcile the retained authority. */ }

            // A fresh client has only the journal and replacement wallet, no outgoing signing key.
            PushSubscriptionClient(origin, DurableJournal(journal), http).reconcile {
                PushSubscriptionClient.Demand(replacement, SaitoBackend.OFFICIAL, "d-replacement-fid")
            }
            val old = requireNotNull(uncertain)
            val revoked = requireNotNull(authority)
            assertEquals(old.get("subscription").asString, revoked.get("subscription").asString)
            val inactive = http.post(origin, "/v1/subscriptions/status", revoked.toString(), null)
            assertEquals(200, inactive.status)
            assertFalse(JsonParser.parseString(inactive.body).asJsonObject.get("active").asBoolean)
            val current = requireNotNull(latest)
            assertEquals(replacement.publicKey, current.get("owner").asString)
            assertEquals("official", current.get("backend").asString)
            assertEquals("d-replacement-fid", current.get("fid").asString)
            assertNotEquals(old.get("subscription").asString, current.get("subscription").asString)

            // Opt-out after another client reconstruction also uses only installation authority.
            PushSubscriptionClient(origin, DurableJournal(journal), http).reconcile { null }
            val off = http.post(origin, "/v1/subscriptions/status", requireNotNull(authority).toString(), null)
            assertEquals(200, off.status)
            assertFalse(JsonParser.parseString(off.body).asJsonObject.get("active").asBoolean)
        }
    }

    /** Owned filesystem boundary; no mock of subscription or gateway internals. */
    private class DurableJournal(private val path: Path) : PushSubscriptionClient.Journal {
        override fun load(): String? = if (Files.exists(path)) Files.readString(path) else null
        override fun save(value: String) {
            val pending = path.resolveSibling("journal.pending")
            java.nio.channels.FileChannel.open(pending, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE).use {
                val bytes = java.nio.ByteBuffer.wrap(value.toByteArray(Charsets.UTF_8))
                while (bytes.hasRemaining()) check(it.write(bytes) > 0)
                it.force(true)
            }
            Files.move(pending, path, ATOMIC_MOVE, REPLACE_EXISTING)
            java.nio.channels.FileChannel.open(path.parent, java.nio.file.StandardOpenOption.READ).use { it.force(true) }
        }
    }
}
