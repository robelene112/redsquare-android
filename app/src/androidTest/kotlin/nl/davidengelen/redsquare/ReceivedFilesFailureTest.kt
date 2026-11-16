package nl.davidengelen.redsquare

import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.fileshare.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Native storage page with genuine encrypted files and controlled provider/unlink IO failures only. */
class ReceivedFilesFailureTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun activeExportsAndOtherScopesStayIntactAndPartialRemovalCanBeFinishedDeliberately() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val wallet = wallets.loadOrCreate()
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
        val other = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
        val retained = ChatFixtureState(context, listOf("fileshare."), wallet.publicKey)
        val folder = File(context.cacheDir, "received-storage-evidence").apply { mkdirs() }
        val exported = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/export-stalled-${UUID.randomUUID()}.bin")
        val executor = Executors.newSingleThreadExecutor()
        var inbox: FileTransferStore.FrameInbox? = null
        var primary: Throwable? = null
        try {
            retained.prepare()
            withAppFrameFixture("storage-failure", "received-storage-evidence", "[]", archiveResponse = { "[]" }, preserveVault = true) { activity ->
                fun receive(owner: String = wallet.publicKey, backend: SaitoBackend = SaitoBackend.OWNER,
                    name: String = "partial-report.txt", finalPhase: FilePhase = FilePhase.COMPLETE): FileTransfer {
                    val value = FileTransfer(UUID.randomUUID().toString(), owner, backend, contact, "ws://127.0.0.1:1/wsopen",
                        Fileshare.Offer("partial130", name, 5, "text/plain"), true, FilePhase.RECEIVING)
                    val store = FileTransferStore(context, owner, backend, contact)
                    store.save(value)
                    store.begin(value).use { it.append(byteArrayOf(1, 2, 3, 4, 5)); it.finish(5) }
                    return value.copy(phase = finalPhase, bytes = 5).also(store::save)
                }
                val value = receive()
                val foreign = receive(other, name = "Other identity.txt")
                val official = receive(backend = SaitoBackend.OFFICIAL, name = "Other node.txt")
                val active = receive(name = "Active transfer.txt", finalPhase = FilePhase.RECEIVING)
                val store = FileTransferStore(context, wallet.publicKey, SaitoBackend.OWNER, contact)
                val untouched = context.noBackupFilesDir.listFiles()!!.filter { file ->
                    listOf(foreign, official, active).any { file.name.endsWith("${it.token}.data") }
                }.associate { it to it.readBytes() }
                assertEquals(3, untouched.size)
                val vault = context.noBackupFilesDir.listFiles()!!.filter { it.name.startsWith("vault.") && it.isFile }
                    .associate { it to it.readBytes() }
                val originalWallet = wallets.loadExisting().exportJson()
                context.contentResolver.call(exported, "prepare", exported.lastPathSegment, Bundle().apply { putByteArray("bytes", byteArrayOf()) })
                val export = executor.submit { store.export(value, exported) }
                ui.waitUntil(5_000) { context.contentResolver.call(exported, "status", exported.lastPathSegment, null)!!.getBoolean("firstWritten") }
                val storage = ReceivedFiles(context, ReceivedFilesScope(wallet.publicKey, SaitoBackend.OWNER))
                val held = storage.snapshot().files.single()
                assertTrue(held.busy)
                assertEquals(41L, held.retainedBytes)
                assertTrue(runCatching { storage.remove(held) }.isFailure)
                assertEquals(LocalFileCopy.AVAILABLE, store.load().single { it.token == value.token }.localCopy)
                ui.openSettings()
                ui.onNodeWithText("Storage").performScrollTo().performClick()
                ui.waitUntil(5_000) { ui.onNodeWithText("partial-report.txt").isDisplayed() }
                ui.onNodeWithText("Remove").assertIsNotEnabled()
                captureNativeScreen(File(folder, "actual-export-disables-removal.png"))
                context.contentResolver.call(exported, "release", exported.lastPathSegment, null)
                export.get(5, TimeUnit.SECONDS)
                assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), context.contentResolver.openInputStream(exported)!!.use { it.readBytes() })

                // Retain a genuine encrypted inbox remainder, then fail only its filesystem unlink.
                inbox = store.inbox(value.copy(phase = FilePhase.RECEIVING))
                inbox!!.append("leftover")
                val adverse = ReceivedFiles(context, ReceivedFilesScope(wallet.publicKey, SaitoBackend.OWNER),
                    unlink = { file -> if (file.name.endsWith("${value.token}.inbox")) false else file.delete() })
                val before = adverse.snapshot().files.single()
                assertEquals(81L, before.retainedBytes) // 41-byte payload plus 8-byte frame + 32-byte encrypted overhead.
                assertTrue(runCatching { adverse.remove(before) }.isFailure)
                assertEquals(40L, storage.snapshot().bytes)
                assertEquals(LocalFileCopy.REMOVED, store.load().single { it.token == value.token }.localCopy)
                assertTrue(runCatching { store.export(value, exported) }.isFailure)
                ui.onNodeWithText("Refresh").performClick()
                ui.waitUntil(5_000) { ui.onNodeWithText("40 B on this device").isDisplayed() }
                ui.onNodeWithText("Removal incomplete. Remaining bytes are included above. Remove again to finish.").assertIsDisplayed()
                captureNativeScreen(File(folder, "partial-removal-counted-truthfully.png"))
                activity.recreate()
                ui.waitUntil(5_000) { ui.onNodeWithText("40 B on this device").isDisplayed() }
                ui.onNodeWithText("Remove").performClick()
                ui.onNodeWithText("Remove copy").performClick()
                ui.waitUntil(5_000) { ui.onNodeWithText("No received files stored").isDisplayed() }
                assertEquals(0L, storage.snapshot().bytes)
                assertEquals(FilePhase.COMPLETE, store.load().single { it.token == value.token }.phase)
                untouched.forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
                vault.forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
                assertEquals(originalWallet, wallets.loadExisting().exportJson())
                assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), context.contentResolver.openInputStream(exported)!!.use { it.readBytes() })
                captureNativeScreen(File(folder, "partial-removal-finished.png"))
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary,
            { context.contentResolver.call(exported, "release", exported.lastPathSegment, null) },
            { executor.shutdown(); check(executor.awaitTermination(5, TimeUnit.SECONDS)) },
            { inbox?.close() }, { retained.restoreFiles() }, { retained.restorePreferences() },
            { context.contentResolver.delete(exported, null, null) }) }
    }
}
