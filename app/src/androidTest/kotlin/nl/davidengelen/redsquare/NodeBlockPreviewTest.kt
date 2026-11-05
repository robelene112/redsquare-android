package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Rule
import org.junit.Test

/** Genuine selected-node published block metadata, independent of signed Archive feed delivery. */
class NodeBlockPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun observedHeightIsVisibleInTheSharedHeaderAndItsSourceDetails() {
        MockWebServer().use { node ->
            node.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    check(request.target == "/options")
                    return MockResponse.Builder().body("""{"blockchain":{"last_block_id":1234,"last_timestamp":${System.currentTimeMillis() - 1_000},"last_block_hash":"${"ab".repeat(32)}"}}""").build()
                }
            }
            node.start(InetAddress.getByName("127.0.0.1"), 0)
            withAppFrameFixture("height-tracer", "node-block-evidence",
                nodeBlockApiUrl = "http://127.0.0.1:${node.port}/options") { _ ->
                val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                    "node-block-evidence").apply { mkdirs() }
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
                captureNativeScreen(File(folder, "header-before-height-check.png"))
                ui.waitUntil(15_000) { ui.onNodeWithText("Block 1,234").isDisplayed() }
                ui.onNodeWithText("Node: saito.davidengelen.nl").assertIsDisplayed()
                ui.onNode(hasContentDescription("Connection details") and hasClickAction())
                    .assertHeightIsAtLeast(48.dp).performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Connection details").isDisplayed() }
                ui.onNode(hasText("Block 1,234") and hasAnyAncestor(isDialog())).assertIsDisplayed()
                ui.onNode(hasText("Node: saito.davidengelen.nl") and hasAnyAncestor(isDialog())).assertIsDisplayed()
                ui.onNodeWithText("Post history refreshes separately.").assertIsDisplayed()
                captureNativeScreen(File(folder, "observed-height-details.png"))
                ui.onNodeWithText("Done").performClick()
                ui.waitUntil(10_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            }
        }
    }
}
