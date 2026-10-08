package nl.davidengelen.redsquare

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

fun displayedWalletAddress(ui: ComposeTestRule): String = captureWalletFailure {
    ui.waitUntil(10_000) {
        ui.onAllNodes(hasContentDescription("Wallet address")).fetchSemanticsNodes().isNotEmpty()
    }
    ui.onNodeWithContentDescription("Wallet address")
        .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
}

fun waitForWalletRecovery(ui: ComposeTestRule) = captureWalletFailure {
    ui.waitUntil(10_000) { ui.onAllNodes(hasText("Retry")).fetchSemanticsNodes().isNotEmpty() }
}

/** Capture only the synthetic test wallet's public screen when a wait fails. */
private fun <T> captureWalletFailure(check: () -> T): T {
    try { return check() }
    catch (failure: Throwable) {
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
