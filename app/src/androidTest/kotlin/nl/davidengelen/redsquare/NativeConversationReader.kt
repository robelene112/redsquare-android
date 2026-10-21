package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.google.gson.JsonObject
import org.junit.Assert.assertTrue
import java.io.File

/** Shared native observations only. Each protocol fixture owns delivery, retention and restoration. */
internal class NativeConversationReader(private val ui: ComposeTestRule, private val folder: File,
    private val label: String, private val messagesLabel: String, private val expectedMessages: () -> Int) {

    fun assertOpensAtNewest(body: String) {
        ui.onNodeWithContentDescription(messagesLabel).assertIsDisplayed()
        capture("opened")
        // Opening has no touch or accessibility scroll operator.
        ui.onNodeWithText(body).assertIsDisplayed()
    }

    fun followIncoming(body: String, deliver: () -> Unit) {
        // Physically finish reading the current last bubble before reception.
        repeat(6) {
            if (!atEnd()) ui.onNodeWithContentDescription(messagesLabel).performTouchInput { swipeUp() }
        }
        ui.waitUntil(5_000) { atEnd() }
        capture("reading-at-end")
        deliver()
        capture("received-while-at-end")
        ui.waitUntil(10_000) { ui.onNodeWithText(body).isDisplayed() && atEnd() }
        capture("following-new-message")
    }

    fun receiveWhileReadingOlder(older: String, incoming: String, deliver: () -> Unit) {
        ui.onNodeWithContentDescription(messagesLabel).performScrollToNode(hasText(older))
        ui.onNodeWithText(older).performScrollTo().assertIsDisplayed()
        assertTrue("The reader deliberately moved away from the conversation end", !atEnd())
        val before = ui.onNodeWithText(older).getUnclippedBoundsInRoot().top.value
        capture("reading-older")
        deliver()
        val newMessages = hasText("New messages") and hasClickAction() and isEnabled()
        ui.waitUntil(10_000) { ui.onNode(newMessages).isDisplayed() }
        ui.onNodeWithText(older).assertIsDisplayed()
        val after = ui.onNodeWithText(older).getUnclippedBoundsInRoot().top.value
        assertTrue("Incoming messages preserve the older text position within one pixel",
            kotlin.math.abs(after - before) <= 1f)
        val density = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        File(folder, "$label-reading-anchor.json").writeText(JsonObject().apply {
            addProperty("text", older); addProperty("beforeTop", before); addProperty("afterTop", after)
            addProperty("oneDpEqualsOnePixel", density == 1f)
        }.toString())
        check(density == 1f)
        capture("new-message-action-with-older-position")
        ui.onNode(newMessages).performClick()
        ui.waitUntil(10_000) { ui.onNodeWithText(incoming).isDisplayed() && atEnd() }
        ui.onNode(newMessages).assertDoesNotExist()
        capture("new-message-action-opened-latest")
    }

    private fun capture(stage: String) {
        captureNativeScreen(File(folder, "$label-$stage.png"))
        File(folder, "$label-$stage.json").writeText(JsonObject().apply {
            addProperty("stage", stage)
            addProperty("expectedMessages", expectedMessages())
            addProperty("atEnd", atEnd())
        }.toString())
    }

    private fun atEnd(): Boolean {
        val axis = ui.onNodeWithContentDescription(messagesLabel).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: return false
        val observation = ui.runOnIdle { axis.value() to axis.maxValue() }
        return observation.first == observation.second
    }
}
