package nl.davidengelen.redsquare

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.uiautomator.*
import java.util.regex.Pattern
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

/** Select an exact test-provider file and wait for its validated native preview. */
fun chooseWalletImport(ui: ComposeTestRule, filename: String, incoming: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val device = UiDevice.getInstance(instrumentation)
    val row = By.text(ExportTestDocumentsProvider.TITLE).res("android", "title")
    val drawer = By.res(Pattern.compile(".*:id/roots_list"))
    val providerHeader = By.res(Pattern.compile(".*:id/header_title")).text(
        Pattern.compile(".*" + Pattern.quote(ExportTestDocumentsProvider.TITLE) + ".*", Pattern.CASE_INSENSITIVE))
    val contentList = By.res(Pattern.compile(".*:id/dir_list"))
    val selected = By.desc(context.getString(R.string.wallet_import_address)).text(incoming)
    val deadline = SystemClock.uptimeMillis() + 30_000
    while (SystemClock.uptimeMillis() < deadline) {
        try {
            // The Compose clock can synchronize only after the external picker returns.
            if (device.currentPackageName == context.packageName) {
                ui.waitForIdle()
                if (device.hasObject(selected)) return
            }
            val opened = device.findObject(drawer)
            if (opened != null) {
                opened.findObject(row)?.click()
                device.wait(Until.gone(drawer), 2_000)
            } else {
                val file = device.findObject(By.text(filename).res("android", "title"))
                if (file != null) {
                    file.click()
                    device.wait(Until.hasObject(selected), 2_000)
                } else if (device.hasObject(providerHeader) && device.hasObject(contentList)) {
                    // A successful earlier export can leave this exact backup below
                    // the viewport. Scroll only the selected test provider's content,
                    // never the roots drawer or another provider.
                    UiScrollable(UiSelector().resourceIdMatches(".*:id/dir_list").scrollable(true))
                        .setMaxSearchSwipes(8)
                        .scrollIntoView(UiSelector().resourceId("android:id/title").text(filename))
                } else device.findObject(By.desc("Show roots"))?.let {
                    it.click()
                    device.wait(Until.hasObject(row), 2_000)
                }
            }
        } catch (_: StaleObjectException) { /* Reobserve the actual picker. */ }
        SystemClock.sleep(100)
    }
    error("Wallet reference import document was not displayed")
}
