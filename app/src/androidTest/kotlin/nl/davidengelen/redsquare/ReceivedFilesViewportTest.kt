package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.fileshare.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ReceivedFilesViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun compactStorageStaysReachableAtLargeText() = ui.verifyReceivedFilesViewports(320)
}
class ReceivedFilesPixelViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun pixelStorageStaysReachableAtLargeText() = ui.verifyReceivedFilesViewports(412)
}

private fun ComposeTestRule.verifyReceivedFilesViewports(width: Int) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    val wallet = AndroidWalletStore(context).loadOrCreate()
    val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
    val retained = ChatFixtureState(context, listOf("fileshare."), wallet.publicKey)
    val folder = File(context.cacheDir, "received-storage-evidence").apply { mkdirs() }
    var primary: Throwable? = null
    try {
        retained.prepare()
        withAppFrameFixture("storage-viewport-$width", "received-storage-evidence", "[]", archiveResponse = { "[]" }) { activity ->
            val store = FileTransferStore(context, wallet.publicKey, SaitoBackend.OWNER, contact)
            repeat(40) { index ->
                val value = FileTransfer(UUID.randomUUID().toString(), wallet.publicKey, SaitoBackend.OWNER, contact,
                    "ws://127.0.0.1:1/wsopen", Fileshare.Offer("viewport$index", "Retained document ${index + 1}.txt", 5, "text/plain"),
                    true, FilePhase.RECEIVING, createdAt = index.toLong())
                store.save(value)
                store.begin(value).use { it.append(byteArrayOf(1, 2, 3, 4, 5)); it.finish(5) }
                store.save(value.copy(phase = FilePhase.COMPLETE, bytes = 5))
            }
            withPostViewports(activity, folder, "received-files", widthDp = width) { label, _ ->
                openSettings()
                onNodeWithText("Storage").performScrollTo().performClick()
                waitUntil(10_000) { onAllNodesWithText("1.6 KB on this device").fetchSemanticsNodes().isNotEmpty() }
                if (!onNodeWithText("1.6 KB on this device").isDisplayed()) onNodeWithText("1.6 KB on this device").performScrollTo()
                onNodeWithText("1.6 KB on this device").assertIsDisplayed()
                val list = onNode(hasScrollToIndexAction())
                val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
                list.performScrollToNode(hasText("Retained document 1.txt"))
                onNodeWithText("Retained document 1.txt").assertIsDisplayed()
                onNodeWithContentDescription("Remove Retained document 1.txt")
                    .assertHeightIsAtLeast(48.dp).performClick()
                onNodeWithText("Remove local copy?").assertIsDisplayed()
                onNodeWithText("Remove copy").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                onNodeWithText("Cancel").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                check(android.os.SystemClock.elapsedRealtime() <= deadline) { "Storage scroll and confirmation exceeded 5 seconds" }
                captureNativeScreen(File(folder, "$label-storage-confirmation.png"))
                onNodeWithText("Cancel").performClick()
                assertEquals(40, store.load().size)
                onNodeWithText("Back to Settings").performClick()
                onNode(hasText("Close settings") and hasClickAction()).performClick()
                waitUntil(5_000) { onNode(hasText("Home") and isSelected()).isDisplayed() }
            }
        }
    } catch (failure: Throwable) { primary = failure; throw failure }
    finally { cleanupAfterTest(primary, { retained.restoreFiles() }, { retained.restorePreferences() }) }
}
