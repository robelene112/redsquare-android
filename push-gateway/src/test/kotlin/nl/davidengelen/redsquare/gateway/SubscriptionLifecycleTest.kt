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
import java.security.MessageDigest
import java.time.Duration

class SubscriptionLifecycleTest {
    @get:Rule val directory = TemporaryFolder()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val publicOrigin = "https://push.fixture.invalid"

    @Test(timeout = 30_000) fun aWalletSubscriptionSurvivesServerRestartAndCanBeRetiredWithoutItsWalletKey() {
        val state = directory.newFolder("gateway").toPath()
        val wallet = SaitoWallet.create()
        val subscription = "12".repeat(32)
        val secret = "34".repeat(32) // Synthetic fixture authority only.
        val revokeHash = sha256(secret)
        val installation = "1b426cf0-ec9e-499f-8abb-e4b57090c336"
        val fid = "c-fixture-installation-identifier"
        val expiresAt = System.currentTimeMillis() + Duration.ofDays(3).toMillis()
        lateinit var binding: JsonObject
        lateinit var signature: String

        PushGateway(state, publicOrigin).use { gateway ->
            gateway.start()
            val challengeResponse = post(gateway, "/v1/challenges", JsonObject().apply {
                addProperty("version", 1); addProperty("owner", wallet.publicKey)
            })
            assertEquals("Wallet challenge must be available", 201, challengeResponse.statusCode())
            val challenge = JsonParser.parseString(challengeResponse.body()).asJsonObject
            val nonce = challenge.get("nonce").asString
            assertTrue(nonce.matches(Regex("[0-9a-f]{64}")))
            assertTrue(challenge.get("expiresAt").asLong > System.currentTimeMillis())
            binding = JsonObject().apply {
                addProperty("version", 1); addProperty("nonce", nonce)
                addProperty("subscription", subscription); addProperty("installation", installation)
                addProperty("fid", fid); addProperty("owner", wallet.publicKey)
                addProperty("backend", "owner"); addProperty("categories", "mentions,replies")
                addProperty("expiresAt", expiresAt); addProperty("revocationHash", revokeHash)
            }
            // Literal contract ordering, independent of the gateway's implementation.
            val intent = "Saito push bind v1\n$publicOrigin/v1/subscriptions/bind\n$nonce\n" +
                "$subscription\n$installation\n${sha256(fid)}\n${wallet.publicKey}\nowner\n" +
                "mentions,replies\n$expiresAt\n$revokeHash"
            signature = wallet.sign(intent.toByteArray(Charsets.UTF_8))
            val response = post(gateway, "/v1/subscriptions/bind", binding, signature)
            assertEquals("Signed installation subscription must commit", 201, response.statusCode())
            assertEquals(subscription, JsonParser.parseString(response.body()).asJsonObject.get("subscription").asString)
        }

        // A new runtime instance must load its durable state. This is not process-crash proof.
        PushGateway(state, publicOrigin).use { gateway ->
            gateway.start()
            val authority = JsonObject().apply {
                addProperty("version", 1); addProperty("subscription", subscription)
                addProperty("revocationSecret", secret)
            }
            val status = post(gateway, "/v1/subscriptions/status", authority)
            assertEquals(200, status.statusCode())
            val active = JsonParser.parseString(status.body()).asJsonObject
            assertTrue(active.get("active").asBoolean)
            assertEquals(wallet.publicKey, active.get("owner").asString)
            assertEquals("owner", active.get("backend").asString)
            assertEquals(expiresAt, active.get("expiresAt").asLong)
            // Retirement supplies only the installation authority, never a wallet signature/key.
            assertEquals(200, post(gateway, "/v1/subscriptions/revoke", authority).statusCode())
            val retired = post(gateway, "/v1/subscriptions/status", authority)
            assertEquals(200, retired.statusCode())
            assertFalse(JsonParser.parseString(retired.body()).asJsonObject.get("active").asBoolean)
            // An old in-flight bind cannot resurrect the identity after retirement.
            assertEquals(410, post(gateway, "/v1/subscriptions/bind", binding, signature).statusCode())
        }
    }

    private fun post(gateway: PushGateway, path: String, body: JsonObject, signature: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${gateway.port}$path"))
            .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        if (signature != null) request.header("X-Saito-Signature", signature)
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
