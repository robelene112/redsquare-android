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

    @Test fun oldAndUnavailableReportsRemainHonestUntilTheNodeReconnects() {
        val mode = java.util.concurrent.atomic.AtomicReference("old")
        MockWebServer().use { node ->
            node.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    check(request.target == "/options")
                    return when (mode.get()) {
                        "missing" -> MockResponse.Builder().body("{\"blockchain\":{}}").build()
                        "disconnected" -> MockResponse.Builder()
                            .onResponseStart(mockwebserver3.SocketEffect.CloseSocket()).build()
                        else -> {
                            val old = mode.get() == "old"
                            val height = if (old) 2345 else 3456
                            val time = System.currentTimeMillis() - if (old) 30 * 60_000 else 1_000
                            MockResponse.Builder().body("""{"blockchain":{"last_block_id":$height,"last_timestamp":$time,"last_block_hash":"${"cd".repeat(32)}"}}""").build()
                        }
                    }
                }
            }
            node.start(InetAddress.getByName("127.0.0.1"), 0)
            withAppFrameFixture("availability", "node-block-evidence",
                nodeBlockApiUrl = "http://127.0.0.1:${node.port}/options") { _ ->
                val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                    "node-block-evidence").apply { mkdirs() }
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
                ui.waitUntil(15_000) { ui.onNodeWithText("Block 2,345", substring = true).isDisplayed() }
                captureNativeScreen(File(folder, "old-before-freshness-check.png"))
                ui.waitUntil(10_000) { ui.onNodeWithText("Block 2,345 · older").isDisplayed() }
                ui.onNodeWithContentDescription("Connection details").performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Older block information").isDisplayed() }
                captureState(folder, "older-block-details", "Older block information")

                mode.set("missing")
                requestCheck()
                ui.waitUntil(15_000) { ui.onNodeWithText("Block information unavailable").isDisplayed() }
                ui.onNode(hasText("Block 2,345") and hasAnyAncestor(isDialog())).assertIsDisplayed()
                ui.onNodeWithText("Block 0").assertDoesNotExist()
                captureState(folder, "missing-report-retains-source", "Block information unavailable")

                mode.set("disconnected")
                requestCheck()
                ui.waitUntil(15_000) { ui.onNodeWithText("Could not reach this node").isDisplayed() }
                ui.onNode(hasText("Block 2,345") and hasAnyAncestor(isDialog())).assertIsDisplayed()
                ui.onNode(hasText("Node: saito.davidengelen.nl") and hasAnyAncestor(isDialog())).assertIsDisplayed()
                captureState(folder, "disconnected-retains-source", "Could not reach this node")

                mode.set("fresh")
                requestCheck()
                ui.waitUntil(15_000) { ui.onNode(hasText("Block 3,456") and hasAnyAncestor(isDialog())).isDisplayed() }
                ui.waitUntil(5_000) {
                    ui.onAllNodesWithText("Could not reach this node").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() &&
                        ui.onAllNodesWithText("Older block information").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
                }
                ui.onNodeWithText("Could not reach this node").assertDoesNotExist()
                ui.onNodeWithText("Older block information").assertDoesNotExist()
                captureState(folder, "node-reconnected", "Block 3,456")
                ui.onNodeWithText("Done").performClick()
                ui.waitUntil(10_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                ui.onNodeWithText("Block 3,456").assertIsDisplayed()
                ui.onNodeWithText("Node: saito.davidengelen.nl").assertIsDisplayed()
                ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            }
        }
    }


    @Test fun missingInitialMetadataDoesNotInventAZeroHeightOrHideAvailablePosts() {
        MockWebServer().use { node ->
            node.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    check(request.target == "/options")
                    return MockResponse.Builder().body("{\"blockchain\":{}}").build()
                }
            }
            node.start(InetAddress.getByName("127.0.0.1"), 0)
            withAppFrameFixture("initial-missing", "node-block-evidence",
                nodeBlockApiUrl = "http://127.0.0.1:${node.port}/options") { _ ->
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                val readyDeadline = android.os.SystemClock.elapsedRealtime() + 15_000
                // Node metadata and authenticated feed application finish independently.
                ui.waitUntil(15_000) {
                    ui.onNodeWithText("Block unavailable").isDisplayed() &&
                        ui.onNodeWithText("Native Red Square reference post").isDisplayed()
                }
                check(android.os.SystemClock.elapsedRealtime() <= readyDeadline) { "Initial metadata and available posts retain the original fifteen-second goal" }
                ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                ui.onNodeWithContentDescription("Connection details").performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Block information unavailable").isDisplayed() }
                ui.onNodeWithText("Block height has not been observed.").assertIsDisplayed()
                ui.onNodeWithText("Block 0").assertDoesNotExist()
                ui.onNodeWithText("Last successful check").assertDoesNotExist()
                val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                    "node-block-evidence").apply { mkdirs() }
                captureState(folder, "initial-height-unknown", "Block height has not been observed.")
                ui.onNodeWithText("Done").performClick()
            }
        }
    }

    private fun requestCheck() {
        val action = hasText("Check again") and hasClickAction()
        ui.waitUntil(15_000) { ui.onNode(action and isEnabled()).isDisplayed() }
        ui.onNode(action).assertIsEnabled().performClick()
    }


    private fun captureState(folder: File, name: String, expectedText: String) {
        captureNativeScreen(File(folder, "$name.png"))
        ui.waitForIdle()
        captureNativeScreen(File(folder, "$name-no-input.png"), expectedText)
    }

}
