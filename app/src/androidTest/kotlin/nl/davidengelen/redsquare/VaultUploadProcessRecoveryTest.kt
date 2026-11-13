package nl.davidengelen.redsquare

import android.app.KeyguardManager
import android.os.Build
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.chat.ChatWorkStore
import nl.davidengelen.redsquare.fileshare.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Host-driven distinct processes, from an actual reviewed/authenticated owned-peer mint. */
class VaultUploadProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val checkpoint get() = File(context.cacheDir, "vault-upload-process-checkpoint.json")
    private val originals get() = File(context.noBackupFilesDir, "vault-upload-process-originals")
    private val evidence get() = File(context.cacheDir, "vault-upload-process-evidence").apply { mkdirs() }
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
    private val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
    private val draft = "Retain this unsent Chat draft through a Vault upload restart"
    private val token = "10700000-0000-4000-8000-000000000001"
    private val contents = "This original Vault file survives actual Android process death".toByteArray()
    private val prefixes get() = listOf("wallet.", "backend.", "feed.", "conversation.", "profile.", "profilemeta.", "profiledraft.",
        "search.", "authorposts.", "postdraft.", "namemap.", "following.", "quoteoriginal.", "vault.", "stack.", "stackwindow.",
        "stackdraft.", "transfer.", "chatwork.", "fileshare.", "chatpush.$owner.", "groupchatpush.$owner.", "avatarprofiles.", "displaynames.")
    private fun names() = context.noBackupFilesDir.listFiles().orEmpty().filter { it.isFile && prefixes.any(it.name::startsWith) }.map { it.name }.toSet()
    private fun preferences() = listOf("name-directory" to "enabled", "notification-permission" to "offered",
        "notification-permission" to "social-offered:$owner", "wallet-backup-reminders" to "hidden:$owner")
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun walletDigest() = digest(AndroidWalletStore(context).loadExisting().exportJson().toByteArray())
    private fun read() = JsonParser.parseString(checkpoint.readText()).asJsonObject
    private fun write(row: JsonObject) { FileOutputStream(checkpoint).use { it.write(row.toString().toByteArray()); it.fd.sync() } }
    private fun retainedWork() = JsonObject().apply {
        names().filter { it.startsWith("chatwork.") || it.startsWith("fileshare.") }.sorted().forEach { name ->
            addProperty(name, digest(File(context.noBackupFilesDir, name).readBytes()))
        }
    }

    @Test fun terminateAfterAnAuthenticatedVaultMintAndRejectedFile() {
        check(!checkpoint.exists() && !originals.exists())
        AndroidWalletStore(context).loadOrCreate()
        val row = JsonObject().apply {
            addProperty("phase", "prepared"); addProperty("pid", Process.myPid())
            addProperty("originalWalletDigest", walletDigest()); addProperty("originalBackend", AndroidBackendStore(context).load().id)
            add("savedFiles", snapshotProcessFixtureFiles(context, originals, names()))
            add("preferences", JsonArray().apply { preferences().forEach { (name, key) ->
                val pref = context.getSharedPreferences(name, 0)
                add(JsonObject().apply { addProperty("name", name); addProperty("key", key)
                    if (pref.contains(key)) addProperty("value", pref.getBoolean(key, false)) })
            } })
        }
        write(row) // Every fixture mutation follows the durable original-installation checkpoint.
        withVaultDeviceCredential { device ->
            withVaultReadingFixture("process-before-death", captureFolder = evidence.name, retainTransfers = true, minting = true) { scene ->
                val wallet = AndroidWalletStore(context).loadExisting()
                ChatWorkStore(context, wallet, SaitoBackend.OWNER, contact).saveDraft(draft)
                FileTransferStore(context, owner, SaitoBackend.OWNER, contact).save(FileTransfer(token, owner, SaitoBackend.OWNER,
                    contact, "ws://127.0.0.1:1/wsopen", Fileshare.Offer("vault107", "retained-file.bin", 1, "application/octet-stream"),
                    true, FilePhase.CANCELLED))
                requireNotNull(scene.mint).rejectUpload.set(true)
                ui.reviewVaultFile(scene, "vault-process.bin", contents)
                enterRealSystemPin(device)
                ui.waitUntil(25_000) { ui.onNodeWithText("Waiting for availability").isDisplayed() }
                assertEquals(1, scene.peer.transactions.size); assertEquals(1, scene.peer.vaultUploads.size)
                val candidate = requireNotNull(scene.mint.candidate())
                assertEquals(candidate.transaction.encoded(), TransferStore(context, owner).pendingMints().single().candidate.transaction.encoded())
                row.addProperty("phase", "pending"); row.addProperty("owner", owner); row.addProperty("backend", "owner")
                row.addProperty("walletDigest", walletDigest()); row.addProperty("candidate", candidate.transaction.encoded())
                row.addProperty("fileId", candidate.fileId); row.add("retainedWork", retainedWork())
                captureNativeScreen(File(evidence, "pending-original-before-real-process-death.png"))
                write(row)
                Process.killProcess(Process.myPid())
                error("Expected actual process termination")
            }
        }
    }

    @Test fun coldRetryRequiresFreshDeviceAuthorizationAndUploadsTheOriginalWithoutAnotherMint() {
        val row = read(); assertEquals("pending", row.get("phase").asString)
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        assertEquals(owner, row.get("owner").asString); assertEquals(row.get("walletDigest").asString, walletDigest())
        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
        assertTrue(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        withVaultReadingFixture("process-cold", captureFolder = evidence.name, minting = true, expectedMintCount = 0,
            preserveVault = true, priorMint = row.get("candidate").asString) { scene ->
            ui.openNativeVault()
            ui.waitUntil(15_000) { ui.onNodeWithText("Waiting for availability").isDisplayed() }
            assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
            assertEquals(draft, ChatWorkStore(context, AndroidWalletStore(context).loadExisting(), SaitoBackend.OWNER, contact).load().draft)
            assertEquals(FilePhase.CANCELLED, FileTransferStore(context, owner, SaitoBackend.OWNER, contact).load().single { it.token == token }.phase)
            assertEquals(row.getAsJsonObject("retainedWork"), retainedWork())
            captureNativeScreen(File(evidence, "cold-original-visible-without-automatic-send.png"))
            ui.onNodeWithText("Retry original upload").performScrollTo().performClick()
            waitForSystemPin(device)
            assertTrue("Old authorization must not survive process loss", scene.peer.vaultUploads.isEmpty())
            assertTrue(scene.peer.transactions.isEmpty())
            enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNodeWithText("Upload complete").isDisplayed() }
            assertTrue("The existing owned key must not be minted again", scene.peer.transactions.isEmpty())
            assertEquals(1, scene.peer.vaultUploads.size)
            assertEquals(row.get("candidate").asString, TransferStore(context, owner).pendingMints().single().candidate.transaction.encoded())
            assertEquals(row.get("fileId").asString, requireNotNull(scene.mint).candidate()?.fileId)
            assertArrayEquals(contents, scene.mint.storedContents())
            assertEquals(row.get("walletDigest").asString, walletDigest())
            assertEquals(row.getAsJsonObject("retainedWork"), retainedWork())
            captureNativeScreen(File(evidence, "cold-fresh-auth-exact-file-same-key-complete.png"))
            ui.onNodeWithText("Back to files").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("vault-process.bin").isDisplayed() }
            row.addProperty("phase", "recovered"); row.addProperty("recoveredPid", Process.myPid()); write(row)
        }
    }

    @Test fun restoreOriginalEncryptedInstallationFiles() {
        if (!checkpoint.exists()) return
        val row = read()
        val preferenceCleanup: List<() -> Unit> = row.getAsJsonArray("preferences").map { entry ->
            {
                val pref = entry.asJsonObject; val name = pref.get("name").asString; val key = pref.get("key").asString
                check(name to key in preferences())
                check(context.getSharedPreferences(name, 0).edit().apply {
                    if (pref.has("value")) putBoolean(key, pref.get("value").asBoolean) else remove(key)
                }.commit())
            }
        }
        cleanupAfterTest(null, {
            restoreProcessFixtureFiles(context, originals, names() + row.getAsJsonArray("savedFiles").map { it.asString }, row.getAsJsonArray("savedFiles"))
        }, *preferenceCleanup.toTypedArray())
        assertEquals(row.get("originalWalletDigest").asString, walletDigest())
        assertEquals(row.get("originalBackend").asString, AndroidBackendStore(context).load().id)
        assertFalse(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        check(originals.deleteRecursively()); check(checkpoint.delete())
    }
}
