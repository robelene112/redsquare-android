package nl.davidengelen.redsquare.gateway

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.gson.JsonParser
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.util.Date
import java.util.concurrent.TimeUnit

class FcmSenderTest {
    @Test(timeout = 30_000) fun sendsOnlyAnOpaqueWakeAndDistinguishesAcceptanceTemporaryAndObsoleteTargets() {
        val credentials = GoogleCredentials.create(AccessToken("synthetic-owned-oauth", Date(System.currentTimeMillis() + 3_600_000)))
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().body("{\"name\":\"projects/owned-fixture/messages/first\"}").build())
            server.enqueue(MockResponse.Builder().code(503).addHeader("Retry-After", "60")
                .body("x".repeat(70_000)).build())
            server.enqueue(MockResponse.Builder().code(404).body("""
                {"error":{"status":"NOT_FOUND","details":[
                    {"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}
                ]}}
            """.trimIndent()).build())
            server.enqueue(MockResponse.Builder().code(400).body("x".repeat(70_000)).build())
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val sender = FcmSender("owned-fixture", credentials, "http://127.0.0.1:${server.port}")
            val expiry = System.currentTimeMillis() + 3_600_000
            val wake = "a".repeat(64)
            assertEquals("Cloud acceptance must return the actual message name",
                FcmSender.Result.Accepted("projects/owned-fixture/messages/first"), sender.send("controlled-fid", wake, expiry))
            assertEquals(FcmSender.Result.Retry(60_000), sender.send("controlled-fid", wake, expiry))
            assertEquals(FcmSender.Result.Unregistered, sender.send("controlled-fid", wake, expiry))
            assertEquals(FcmSender.Result.Rejected(400), sender.send("controlled-fid", wake, expiry))
            repeat(4) {
                val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                assertEquals("POST", request.method)
                assertEquals("/v1/projects/owned-fixture/messages:send", request.url.encodedPath)
                assertEquals("Bearer synthetic-owned-oauth", request.headers["Authorization"])
                val body = JsonParser.parseString(requireNotNull(request.body).utf8()).asJsonObject
                assertEquals(setOf("message"), body.keySet())
                val message = body.getAsJsonObject("message")
                assertEquals(setOf("fid", "data", "android"), message.keySet())
                assertEquals("controlled-fid", message.get("fid").asString)
                assertEquals(JsonParser.parseString("{\"version\":\"1\",\"wake\":\"${"a".repeat(64)}\"}"), message.get("data"))
                val android = message.getAsJsonObject("android")
                assertEquals(setOf("priority", "ttl"), android.keySet())
                assertEquals("HIGH", android.get("priority").asString)
                val ttl = android.get("ttl").asString
                assertTrue(ttl.endsWith("s"))
                assertTrue(ttl.dropLast(1).toLong() in 3500..3600)
            }
            assertEquals(4, server.requestCount)
            // The external credential provider may renew over the network before a send.
            val refreshed = java.util.concurrent.atomic.AtomicBoolean(false)
            val slowCredentials = object : GoogleCredentials() {
                override fun getRequestMetadata(uri: java.net.URI?): Map<String, List<String>> {
                    Thread.sleep(1250)
                    refreshed.set(true)
                    return credentials.getRequestMetadata(uri)
                }
            }
            val slowSender = FcmSender("owned-fixture", slowCredentials, "http://127.0.0.1:${server.port}")
            assertEquals(FcmSender.Result.Rejected(410),
                slowSender.send("controlled-fid", wake, System.currentTimeMillis() + 1000))
            assertTrue("Expiry must be rechecked after the actual external credential delay", refreshed.get())
            assertEquals("An expired wake must not reach HTTP", 4, server.requestCount)
        }
    }
}
