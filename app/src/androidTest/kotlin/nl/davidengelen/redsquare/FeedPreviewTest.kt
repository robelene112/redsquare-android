package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import nl.davidengelen.redsquare.saito.SaitoWallet
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.Assert.assertTrue
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FeedPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before fun clearPublicFeedCache() {
        listOf("feed.owner.v1", "feed.owner.v1.bak", "feed.owner.v1.new").forEach {
            java.io.File(instrumentation.targetContext.noBackupFilesDir, it).delete()
        }
    }

    @Test fun authenticatedFeedSurvivesReopeningAndRecoversWithoutLosingPosts() {
        val fixture = asset("native-feed-reference.json")
        val peer = ControlledFeedPeer("[${fixture.get("archiveRow")}]")
        val server = peer.start()
        val port = server.port
        var replacement: MockWebServer? = null
        try {
            ActivityScenario.launch<MainActivity>(intent(port)).use { activity ->
                captureFailure {
                    waitForText("Native Red Square reference post")
                    ui.onNodeWithText("Anon-jesTu2").assertIsDisplayed()
                    peer.assertRequestObserved()
                    server.close()
                    activity.recreate()
                    waitForText("Native Red Square reference post")
                    waitForText("Server unavailable")
                    replacement = peer.start(port)
                    ui.onNodeWithText("Refresh").performClick()
                    waitForText("Back online")
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    ui.waitUntil(6_000) { ui.onAllNodes(hasText("Back online")).fetchSemanticsNodes().isEmpty() }
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    peer.assertHealthy()
                }
            }
        } finally { server.close(); replacement?.close() }
    }

    @Test fun refreshAndRecreationKeepTheVisiblePostWhenNewPostsArrive() {
        val fixture = asset("native-feed-scroll-reference.json")
        val peer = ControlledFeedPeer(fixture.get("initialRows").toString())
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(intent(server.port)).use { activity ->
                captureFailure {
                    waitForText("Reference post 20")
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Reference post 05"))
                    ui.onNodeWithText("Reference post 05").assertIsDisplayed()
                    peer.rows = fixture.get("refreshedRows").toString()
                    val before = peer.requests.get()
                    ui.onNodeWithText("Refresh").performClick()
                    ui.waitUntil(15_000) { peer.requests.get() > before }
                    ui.waitUntil(15_000) {
                        ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.waitForIdle()
                    ui.onNodeWithText("Reference post 05").assertIsDisplayed()
                    activity.recreate()
                    waitForText("Reference post 05")
                    ui.onNodeWithText("Reference post 05").assertIsDisplayed()
                    ui.onNode(hasScrollAction()).performScrollToIndex(0)
                    waitForText("New reference post")
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun unavailableCacheWarnsWithoutHidingLivePostsAndCanRecover() {
        val original = asset("native-feed-reference.json")
        val updated = asset("native-feed-scroll-reference.json").getAsJsonArray("refreshedRows")[0]
        val peer = ControlledFeedPeer("[${original.get("archiveRow")}]")
        val blocked = java.io.File(instrumentation.targetContext.noBackupFilesDir, "feed.owner.v1.new")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(intent(server.port)).use {
                captureFailure {
                    waitForText("Native Red Square reference post")
                    check(blocked.mkdir())
                    val obstruction = java.io.File(blocked, "obstruction").apply { writeText("public fixture") }
                    try {
                        peer.rows = "[$updated]"
                        ui.onNodeWithText("Refresh").performClick()
                        waitForText("New reference post")
                        waitForText("Some posts could not be saved or opened for offline reading.")
                    } finally {
                        check(obstruction.delete())
                        check(blocked.delete())
                    }
                    ui.onNodeWithText("Refresh").performClick()
                    ui.waitUntil(15_000) {
                        ui.onAllNodes(hasText("Some posts could not be saved or opened for offline reading.")).fetchSemanticsNodes().isEmpty()
                    }
                    ui.onNodeWithText("New reference post").assertIsDisplayed()
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun unreadableRefreshExplainsTheProblemWhileKeepingCachedPosts() {
        val fixture = asset("native-feed-reference.json")
        val peer = ControlledFeedPeer("[${fixture.get("archiveRow")}]")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(intent(server.port)).use {
                captureFailure {
                    waitForText("Native Red Square reference post")
                    val invalid = fixture.getAsJsonObject("archiveRow").deepCopy().apply { addProperty("tx", "{}") }
                    peer.rows = "[$invalid]"
                    ui.onNodeWithText("Refresh").performClick()
                    waitForText("The server returned posts this app could not read.")
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    peer.assertHealthy()
                }
            }
        }
    }

    private fun asset(name: String) = JsonParser.parseReader(instrumentation.context.assets.open(name).reader()).asJsonObject
    private fun intent(port: Int) = Intent(instrumentation.targetContext, MainActivity::class.java)
        .putExtra("feedPeerUrl", "ws://127.0.0.1:$port/wsopen")
    private fun waitForText(text: String) {
        ui.waitUntil(15_000) { ui.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(text).assertIsDisplayed()
    }
    private fun captureFailure(check: () -> Unit) {
        try { check() } catch (failure: Throwable) {
            captureFeedEvidence("feed-ui-failure.png")
            throw failure
        }
    }
}

/** Actual binary socket boundary; signed posts are independent WASM fixtures. */
private class ControlledFeedPeer(@Volatile var rows: String) {
    private val peer = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public test material only.
    private val counter = ByteArray(32) { 7 }
    private val received = CountDownLatch(1)
    val requests = java.util.concurrent.atomic.AtomicInteger()
    @Volatile private var failure: Throwable? = null

    fun start(port: Int = 0): MockWebServer {
        val server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse = response()
        }
        server.start(java.net.InetAddress.getByName("127.0.0.1"), port)
        return server
    }

    private fun response() = MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
        private var authenticated = false
        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
            try {
                val frame = bytes.toByteArray()
                when (frame[0].toInt()) {
                    1 -> {
                        require(frame.size == 33)
                        val key = "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        val signature = peer.sign(frame.copyOfRange(1, 33)).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        webSocket.send((byteArrayOf(2) + key + signature + counter).toByteString())
                    }
                    2 -> {
                        require(frame.size == 130)
                        require(frame.copyOfRange(98, 130).all { it == 0.toByte() })
                        var keyNumber = java.math.BigInteger(1, frame.copyOfRange(1, 34))
                        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
                        var clientKey = ""
                        while (keyNumber.signum() > 0) {
                            val parts = keyNumber.divideAndRemainder(java.math.BigInteger.valueOf(58))
                            clientKey = alphabet[parts[1].toInt()] + clientKey
                            keyNumber = parts[0]
                        }
                        val answer = frame.copyOfRange(34, 98).joinToString("") { "%02x".format(it) }
                        require(SaitoWallet.verify(counter, answer, clientKey))
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
                        webSocket.send((byteArrayOf(13) + frame.copyOfRange(1, 5) + rows.toByteArray()).toByteString())
                        requests.incrementAndGet()
                        received.countDown()
                    }
                }
            } catch (problem: Throwable) { failure = problem; received.countDown() }
        }
    }).build()

    fun assertRequestObserved() { assertTrue("Archive request was not observed", received.await(5, TimeUnit.SECONDS)); assertHealthy() }
    fun assertHealthy() { failure?.let { throw AssertionError("Controlled peer rejected the request", it) } }
}
