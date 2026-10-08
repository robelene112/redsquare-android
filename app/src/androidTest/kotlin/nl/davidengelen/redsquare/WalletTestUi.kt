package nl.davidengelen.redsquare

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Capture only the synthetic test wallet's public screen when a wait fails. */
fun displayedWalletAddress(ui: ComposeTestRule): String {
    try {
        ui.waitUntil(10_000) {
            ui.onAllNodes(hasContentDescription("Wallet address")).fetchSemanticsNodes().isNotEmpty()
        }
        return ui.onNodeWithContentDescription("Wallet address")
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
    } catch (failure: Throwable) {
        try {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
                try {
                    File(instrumentation.targetContext.cacheDir, "wallet-ui-failure.png").outputStream().use {
                        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally { screenshot.recycle() }
            }
        } catch (_: Exception) { /* Preserve the original test failure. */ }
        throw failure
    }
}
