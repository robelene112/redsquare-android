package nl.davidengelen.redsquare

import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.chat.ChatWorkStore
import nl.davidengelen.redsquare.fileshare.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real encrypted retained file, native account navigation and actual provider export, without network writes. */
class FileshareStorageManagementTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aReceivedCopyStaysAfterExportUntilConfirmedRemovalAndHistorySurvivesRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only.
        assertNotEquals(original.publicKey, contact.publicKey)
        val wallet = original.withChatContact(contact.publicKey)
        val retained = ChatFixtureState(context, listOf("fileshare.", "chat.owner.${wallet.publicKey}.",
            "chatwork.owner.${wallet.publicKey}."), wallet.publicKey)
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val token = UUID.randomUUID().toString()
        val name = "retained-report.txt"
        val exported = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/retention130-${UUID.randomUUID()}.bin")
        var exportCreated = false
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(original, wallet)
            withAppFrameFixture("received-storage", evidenceFolder = "received-storage-evidence", feedRows = "[]",
                advertisedServices = "archive||;relay||", archiveResponse = { "[]" }) { activity ->
                val store = FileTransferStore(context, wallet.publicKey, SaitoBackend.OWNER, contact.publicKey)
                val value = FileTransfer(token, wallet.publicKey, SaitoBackend.OWNER, contact.publicKey,
                    "ws://127.0.0.1:1/wsopen", Fileshare.Offer("retention130", name, 5, "text/plain"), true,
                    FilePhase.COMPLETE, 5)
                // Actual reception commits its metadata before creating the first encrypted payload.
                store.save(value.copy(phase = FilePhase.RECEIVING, bytes = 0))
                store.begin(value).use { spool -> spool.append(bytes); spool.finish(5) }
                store.save(value)
                val payload = context.noBackupFilesDir.listFiles()!!.single { it.name.endsWith("$token.data") }
                assertEquals("Independent five-byte, one-frame encrypted spool format", 41L, payload.length())
                context.contentResolver.call(exported, "prepare", exported.lastPathSegment, Bundle().apply { putByteArray("bytes", byteArrayOf()) })
                exportCreated = true
                store.export(value, exported)
                store.save(value.copy(phase = FilePhase.SAVED))
                assertArrayEquals(bytes, context.contentResolver.openInputStream(exported)!!.use { it.readBytes() })
                assertTrue("Save as retains the app's original encrypted copy", payload.isFile)
                val history = PrivateChat.create(contact, wallet.publicKey, "Retain this chat history", System.currentTimeMillis())
                ChatHistoryStore(context, wallet, SaitoBackend.OWNER, contact.publicKey).save(listOf(history.envelope()))
                ChatWorkStore(context, wallet, SaitoBackend.OWNER, contact.publicKey).saveDraft("Keep this local draft")

                ui.openSettings()
                ui.onNodeWithText("Storage").performScrollTo().assertIsDisplayed().performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText(name).isDisplayed() }
                ui.onNodeWithText("Received files").assertIsDisplayed()
                ui.onNodeWithText("41 B on this device").assertIsDisplayed()
                captureNativeScreen(File(context.cacheDir, "received-storage-evidence/retained-after-export.png"))
                ui.onNodeWithText("Remove").performClick()
                ui.onNodeWithText("Remove local copy?").assertIsDisplayed()
                ui.onNodeWithText("Cancel").performClick()
                ui.waitUntil(5_000) { ui.onAllNodesWithText("Remove local copy?").fetchSemanticsNodes().isEmpty() }
                assertTrue(payload.isFile)
                ui.onNodeWithText("41 B on this device").assertIsDisplayed()
                ui.onNodeWithText("Remove").performClick()
                ui.onNodeWithText("Remove copy").performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("No received files stored").isDisplayed() }
                assertFalse(payload.exists())
                assertEquals(FilePhase.SAVED, store.load().single().phase)
                assertEquals(name, store.load().single().offer.name)
                assertArrayEquals(bytes, context.contentResolver.openInputStream(exported)!!.use { it.readBytes() })
                assertEquals("Keep this local draft", ChatWorkStore(context, wallet, SaitoBackend.OWNER, contact.publicKey).load().draft)
                assertEquals(history.id, ChatHistoryStore(context, wallet, SaitoBackend.OWNER, contact.publicKey).load().entries.single().message.id)
                activity.recreate()
                ui.waitUntil(10_000) { ui.onNodeWithText("No received files stored").isDisplayed() }
                assertFalse(payload.exists())
                captureNativeScreen(File(context.cacheDir, "received-storage-evidence/removed-after-recreation.png"))
                ui.onNodeWithText("Back to Settings").performClick()
                ui.onNode(hasText("Close settings") and hasClickAction()).performClick()
                ui.onNodeWithText("Chat").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText(contact.publicKey).isDisplayed() }
                ui.onNodeWithText(contact.publicKey).performClick()
                ui.waitUntil(10_000) { ui.onNodeWithText("Local copy removed").isDisplayed() }
                ui.onNodeWithText(name).assertIsDisplayed()
                ui.onNodeWithText("Retain this chat history").assertExists()
                ui.onNodeWithText("Keep this local draft").assertExists()
                ui.onAllNodesWithText("Save as…").assertCountEquals(0)
                captureNativeScreen(File(context.cacheDir, "received-storage-evidence/history-local-copy-removed.png"))
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary,
                { retained.restoreFiles() }, { retained.restorePreferences() },
                { val current = wallets.loadExisting(); check(current.publicKey == original.publicKey)
                    if (current.exportJson() != original.exportJson()) wallets.replace(current, original)
                    check(wallets.loadExisting().exportJson() == original.exportJson()) },
                { if (exportCreated) check(context.contentResolver.delete(exported, null, null) == 1) })
        }
    }
}
