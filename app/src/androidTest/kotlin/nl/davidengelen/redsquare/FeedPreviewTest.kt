package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import nl.davidengelen.redsquare.saito.SaitoWallet
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FeedPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun authenticatedArchivePostsAppearOnTheNativeFeed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = JsonParser.parseReader(instrumentation.context.assets.open("native-feed-reference.json").reader()).asJsonObject
        val peer = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public test material only.
        val counter = ByteArray(32) { 7 }
        val received = CountDownLatch(1)
        var authenticated = false
        var failure: Throwable? = null
        val server = MockWebServer()
        server.enqueue(MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                try {
                    val frame = bytes.toByteArray()
                    when (frame[0].toInt()) {
                        1 -> {
                            require(frame.size == 33)
                            val key = java.util.Base64.getDecoder().decode(fixture.getAsJsonObject("webEnvelope").get("t").asString).copyOfRange(93, 126)
                            val signature = peer.sign(frame.copyOfRange(1, 33)).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            webSocket.send((byteArrayOf(2) + key + signature + counter).toByteString())
                        }
                        2 -> {
                            require(frame.size == 130)
                            // The following request is accepted only after its counter challenge is answered.
                            require(frame.copyOfRange(98, 130).all { it == 0.toByte() })
                            authenticated = true
                        }
                        12 -> {
                            require(authenticated)
                            val bodyLength = ByteBuffer.wrap(frame, 13, 4).int
                            val inputs = ByteBuffer.wrap(frame, 5, 4).int
                            val outputs = ByteBuffer.wrap(frame, 9, 4).int
                            val offset = 5 + 93 + 59 * (inputs + outputs)
                            val body = JsonParser.parseString(String(frame, offset, bodyLength, Charsets.UTF_8)).asJsonObject
                            require(body.get("request").asString == "archive")
                            val criteria = body.getAsJsonObject("data")
                            require(criteria.get("request").asString == "load")
                            require(criteria.get("field1").asString == "RedSquare")
                            require(criteria.get("field4").asString == "")
                            require(criteria.get("flagged_ne").asInt == 1)
                            val result = "[${fixture.get("archiveRow")}]".toByteArray()
                            webSocket.send((byteArrayOf(13) + frame.copyOfRange(1, 5) + result).toByteString())
                            received.countDown()
                        }
                    }
                } catch (problem: Throwable) { failure = problem; received.countDown() }
            }
        }).build())
        server.start()
        try {
            val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
                .putExtra("feedPeerUrl", server.url("/wsopen").toString().replaceFirst("http:", "ws:"))
            ActivityScenario.launch<MainActivity>(intent).use {
                ui.waitUntil(15_000) { ui.onAllNodes(hasText("Native Red Square reference post")).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                ui.onNodeWithText("Anon-jesTu2").assertIsDisplayed()
                assertTrue("Archive request was not observed", received.await(1, TimeUnit.SECONDS))
                failure?.let { throw AssertionError("Controlled peer rejected the request", it) }
            }
        } finally { server.close() }
    }
}
