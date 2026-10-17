package nl.davidengelen.redsquare.gateway

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.TimeUnit

class GatewayProcessTest {
    @get:Rule val directory = TemporaryFolder()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    @Test(timeout = 40_000) fun acknowledgedBindingSurvivesAbruptStandaloneProcessDeath() {
        val state = directory.newFolder("state").toPath()
        val origin = "https://push.fixture.invalid"
        val wallet = SaitoWallet.create()
        val id = "9a".repeat(32)
        val secret = "bc".repeat(32)
        val installation = "e71abcc5-11a0-4cf9-96ef-b8a98ce07c14"
        val fid = "c-process-fixture-installation"
        val expiry = System.currentTimeMillis() + Duration.ofDays(3).toMillis()
        val authority = JsonObject().apply {
            addProperty("version", 1); addProperty("subscription", id); addProperty("revocationSecret", secret)
        }
        start(state, origin).use { first ->
            val nonceResponse = post(first.port, "/v1/challenges", JsonObject().apply {
                addProperty("version", 1); addProperty("owner", wallet.publicKey)
            })
            assertEquals(201, nonceResponse.statusCode())
            val nonce = JsonParser.parseString(nonceResponse.body()).asJsonObject.get("nonce").asString
            val hash = sha256(secret)
            val body = JsonObject().apply {
                addProperty("version", 1); addProperty("nonce", nonce); addProperty("subscription", id)
                addProperty("installation", installation); addProperty("fid", fid); addProperty("owner", wallet.publicKey)
                addProperty("backend", "owner"); addProperty("categories", "mentions,replies")
                addProperty("expiresAt", expiry); addProperty("revocationHash", hash)
            }
            val intent = "Saito push bind v1\n$origin/v1/subscriptions/bind\n$nonce\n$id\n$installation\n" +
                "${sha256(fid)}\n${wallet.publicKey}\nowner\nmentions,replies\n$expiry\n$hash"
            assertEquals(201, post(first.port, "/v1/subscriptions/bind", body,
                wallet.sign(intent.toByteArray(Charsets.UTF_8))).statusCode())
            assertTrue("Acknowledged gateway must still be running before the forced stop", first.process.isAlive)
            first.process.destroyForcibly() // No shutdown hook or graceful snapshot flush.
            assertTrue("First JVM must exit", first.process.waitFor(5, TimeUnit.SECONDS))
        }
        start(state, origin).use { second ->
            val response = post(second.port, "/v1/subscriptions/status", authority)
            assertEquals(200, response.statusCode())
            val retained = JsonParser.parseString(response.body()).asJsonObject
            assertTrue(retained.get("active").asBoolean)
            assertEquals(wallet.publicKey, retained.get("owner").asString)
            assertEquals("owner", retained.get("backend").asString)
            assertEquals(expiry, retained.get("expiresAt").asLong)
            assertEquals(200, post(second.port, "/v1/subscriptions/revoke", authority).statusCode())
            val inactive = post(second.port, "/v1/subscriptions/status", authority)
            assertEquals(200, inactive.statusCode())
            assertFalse(JsonParser.parseString(inactive.body()).asJsonObject.get("active").asBoolean)
        }
    }

    private class Running(val process: Process, val port: Int) : AutoCloseable {
        override fun close() {
            try {
                if (process.isAlive) process.destroy()
                if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
                check(process.waitFor(5, TimeUnit.SECONDS))
            } catch (failure: Throwable) {
                try {
                    process.destroyForcibly()
                    check(process.waitFor(5, TimeUnit.SECONDS))
                } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                if (failure is InterruptedException) Thread.currentThread().interrupt()
                throw failure
            }
        }
    }

    private fun start(state: Path, origin: String): Running {
        val output = directory.newFile().toPath()
        val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", System.getProperty("gateway.runtimeClasspath"), GatewayMain::class.java.name,
            "--state", state.toString(), "--origin", origin, "--port", "0")
            .redirectErrorStream(true).redirectOutput(output.toFile()).start()
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < deadline) {
                val ready = output.toFile().readLines().firstOrNull { it.startsWith("listening=127.0.0.1:") }
                if (ready != null) return Running(process, ready.substringAfterLast(':').toInt())
                assertTrue("Standalone gateway must stay running until ready", process.isAlive)
                Thread.sleep(25)
            }
            fail("Standalone gateway did not become ready within10seconds")
            error("unreachable")
        } catch (failure: Throwable) {
            try { Running(process, 0).close() }
            catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    private fun post(port: Int, path: String, body: JsonObject, signature: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .timeout(Duration.ofSeconds(3)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        if (signature != null) request.header("X-Saito-Signature", signature)
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
