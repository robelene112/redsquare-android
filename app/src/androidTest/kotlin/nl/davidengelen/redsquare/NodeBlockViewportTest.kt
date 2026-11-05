package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import mockwebserver3.*
import org.junit.Rule
import org.junit.Test

class NodeBlockViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun narrowBlockStatusAndDetailsRemainReadable() = ui.nodeBlockViewports(320)
}
class NodeBlockPixelViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun pixelBlockStatusAndDetailsRemainReadable() = ui.nodeBlockViewports(412)
}

private fun ComposeTestRule.nodeBlockViewports(width: Int) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val folder = File(context.cacheDir, "node-viewport-evidence").apply { mkdirs() }
    MockWebServer().use { node ->
        node.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                check(request.target == "/options")
                return MockResponse.Builder().body("""{"blockchain":{"last_block_id":1180213,"last_timestamp":${System.currentTimeMillis() - 30 * 60_000},"last_block_hash":"${"ab".repeat(32)}"}}""").build()
            }
        }
        node.start(InetAddress.getByName("127.0.0.1"), 0)
        withAppFrameFixture("viewport-$width", "node-viewport-evidence",
            nodeBlockApiUrl = "http://127.0.0.1:${node.port}/options") { activity ->
            waitUntil(15_000) { onNodeWithText("Later").isDisplayed() }
            onNodeWithText("Later").performClick()
            withPostViewports(activity, folder, "node", widthDp = width) { label, _ ->
                waitUntil(15_000) { onNodeWithText("Block 1,180,213 · older").isDisplayed() }
                val details = onNode(hasContentDescription("Connection details") and hasClickAction())
                details.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                captureNativeScreen(File(folder, "$label-header.png"))
                details.performClick()
                waitUntil(10_000) { onNodeWithText("Older block information").isDisplayed() }
                onNode(hasText("Node: saito.davidengelen.nl") and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
                onNode(hasText("Block 1,180,213") and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
                captureNativeScreen(File(folder, "$label-block.png"))
                onNodeWithText("Block time").performScrollTo().assertIsDisplayed()
                onNodeWithText("Last successful check").performScrollTo().assertIsDisplayed()
                onNodeWithText("Post history refreshes separately.").performScrollTo().assertIsDisplayed()
                val close = onNode(hasText("Done") and hasClickAction() and hasAnyAncestor(isDialog()))
                close.assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                onNode(hasText("Check again") and hasClickAction()).assertIsDisplayed()
                    .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                val nativeActions = com.google.gson.JsonObject()
                var density = 0f
                activity.onActivity { density = it.resources.displayMetrics.density }
                val root = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
                for (name in listOf("Done", "Check again")) {
                    val text = root.findAccessibilityNodeInfosByText(name).single { it.text?.toString() == name && it.isVisibleToUser }
                    val action = if (text.isClickable) text else requireNotNull(text.parent).also { check(it.isClickable) }
                    val bounds = android.graphics.Rect().apply { action.getBoundsInScreen(this) }
                    nativeActions.add(name, com.google.gson.JsonObject().apply {
                        addProperty("left", bounds.left); addProperty("top", bounds.top)
                        addProperty("right", bounds.right); addProperty("bottom", bounds.bottom)
                        addProperty("density", density)
                    })
                    check(bounds.height() >= 48 * density && bounds.width() >= 48 * density) {
                        "Native $name target must retain48dp access, observed ${bounds.width()}x${bounds.height()} at density$density"
                    }
                }
                File(folder, "$label-native-actions.json").writeText(nativeActions.toString())
                captureNativeScreen(File(folder, "$label-times-and-actions.png"))
                close.performClick()
                waitUntil(10_000) { onAllNodes(isDialog()).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
                onNode(hasText("Home") and isSelected()).assertIsDisplayed()
                onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            }
        }
    }
}
