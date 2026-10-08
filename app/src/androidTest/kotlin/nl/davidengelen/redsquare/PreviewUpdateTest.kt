package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException

class PreviewUpdateTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aNewerReviewBuildOffersTheReviewPage() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val worker = Thread {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 2_000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* HTTP headers */ }
                    val body = """{"applicationId":"nl.davidengelen.redsquare","versionCode":2000000000,"versionName":"future-preview"}""".toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body)
                        flush()
                    }
                }
            } catch (failure: SocketException) {
                if (!server.isClosed) throw failure
            }
        }.apply { isDaemon = true; start() }
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val intent = Intent(context, MainActivity::class.java).putExtra(
                "reviewManifestUrl", "http://127.0.0.1:${server.localPort}/latest.json")
            ActivityScenario.launch<MainActivity>(intent).use {
                ui.waitUntil(10_000) {
                    ui.onAllNodes(hasText("Preview update available")).fetchSemanticsNodes().isNotEmpty()
                }
                ui.onNodeWithText("Preview update available").assertIsDisplayed()
                ui.onNodeWithText("View update").assertIsDisplayed()
            }
        } finally {
            server.close()
            worker.join(2_000)
        }
    }
}
