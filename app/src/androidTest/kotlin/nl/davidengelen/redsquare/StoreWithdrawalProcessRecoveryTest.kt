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

/** Host-driven distinct processes, from an actual reviewed/authenticated owned-peer withdrawal. */
class StoreWithdrawalProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val checkpoint get() = File(context.cacheDir, "store-withdrawal-process-checkpoint.json")
    private val originals get() = File(context.noBackupFilesDir, "store-withdrawal-process-originals")
    private val evidence get() = File(context.cacheDir, "store-withdrawal-process-evidence").apply { mkdirs() }
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
    private val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
    private val draft = "Retain this unsent Chat draft through a Store withdrawal restart"
    private val token = "11100000-0000-4000-8000-000000000001"
    private val prefixes get() = listOf("wallet.", "backend.", "feed.", "conversation.", "profile.", "profilemeta.", "profiledraft.",
        "search.", "authorposts.", "postdraft.", "namemap.", "following.", "quoteoriginal.", "vault.", "stack.", "stackwindow.",
        "stackdraft.", "transfer.", "store-orders.", "store-listings.", "store-delists.", "store-images.", "store-sale-draft.", "store-withdrawals.", "chatwork.", "fileshare.", "chatpush.$owner.", "groupchatpush.$owner.", "avatarprofiles.", "displaynames.")
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

    @Test fun terminateAfterAnAuthenticatedWithdrawalWithUnknownAvailability() {
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
        write(row)
        withVaultDeviceCredential { device ->
            withStoreSellerFixture("withdrawal-process-before-death", crypto = OwnedStoreCryptoFixture()) { scene, _, _ ->
                val treasury = OwnedStoreWithdrawalFixture().also { it.attach(scene) }
                val previous = AndroidWalletStore(context).loadExisting()
                val wallet = previous.withChatContact(contact)
                AndroidWalletStore(context).replace(previous, wallet)
                ChatWorkStore(context, wallet, SaitoBackend.OWNER, contact).saveDraft(draft)
                FileTransferStore(context, owner, SaitoBackend.OWNER, contact).save(FileTransfer(token, owner, SaitoBackend.OWNER,
                    contact, "ws://127.0.0.1:1/wsopen", Fileshare.Offer("store113", "retained-file.bin", 1, "application/octet-stream"), true, FilePhase.CANCELLED))
                scene.peer.disconnectAfterStoreWithdrawal = true
                ui.openNativeStoreSelling(); ui.reviewNativeStoreWithdrawal()
                ui.onNodeWithText("Authorize withdrawal").performClick(); waitForSystemPin(device)
                assertTrue(treasury.submissions.isEmpty()); enterRealSystemPin(device)
                ui.waitUntil(25_000) { ui.onNode(hasText("Retry original request") and isEnabled()).isDisplayed() }
                val candidate = StoreWithdrawalStore(context, owner).read().single().candidate
                assertEquals(listOf(candidate.transaction.envelope()), treasury.submissions.toList())
                assertEquals(1, treasury.quotes); assertTrue(scene.peer.transactions.isEmpty())
                row.addProperty("phase", "pending"); row.addProperty("owner", owner); row.addProperty("backend", "owner")
                row.addProperty("walletDigest", walletDigest()); row.addProperty("candidate", candidate.transaction.encoded())
                row.add("retainedWork", retainedWork())
                captureNativeScreen(File(evidence, "pending-withdrawal-before-real-process-death.png")); write(row)
                Process.killProcess(Process.myPid()); error("Expected actual process termination")
            }
        }
    }

    @Test fun coldWithdrawalRequiresFreshAuthorizationAndRetriesOnlyOriginal() {
        val row = read(); assertEquals("pending", row.get("phase").asString)
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        assertEquals(owner, row.get("owner").asString); assertEquals(row.get("walletDigest").asString, walletDigest())
        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
        assertTrue(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        withStoreSellerFixture("withdrawal-process-cold", preserve = true, crypto = OwnedStoreCryptoFixture()) { scene, _, _ ->
            val treasury = OwnedStoreWithdrawalFixture().also { it.attach(scene) }
            ui.openNativeStoreSelling(); ui.onNodeWithText("Proceeds").performClick()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Withdrawal · 6.25 USDT").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Withdrawal · 6.25 USDT").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Withdrawal · 6.25 USDT").performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Retry original request").isDisplayed() }
            assertTrue(treasury.submissions.isEmpty()); assertEquals(0, treasury.quotes)
            assertEquals(draft, ChatWorkStore(context, AndroidWalletStore(context).loadExisting(), SaitoBackend.OWNER, contact).load().draft)
            assertEquals(FilePhase.CANCELLED, FileTransferStore(context, owner, SaitoBackend.OWNER, contact).load().single { it.token == token }.phase)
            assertEquals(row.getAsJsonObject("retainedWork"), retainedWork())
            captureNativeScreen(File(evidence, "cold-withdrawal-no-automatic-submission.png"))
            ui.onNodeWithText("Retry original request").performClick(); waitForSystemPin(device)
            assertTrue("Old authority cannot survive process death", treasury.submissions.isEmpty())
            enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNodeWithText("Requested from the treasury. External funds have not been sent.").isDisplayed() }
            val saved = StoreWithdrawalStore(context, owner).read().single()
            assertEquals(row.get("candidate").asString, saved.candidate.transaction.encoded())
            assertEquals(listOf(saved.candidate.transaction.envelope()), treasury.submissions.toList()); assertEquals(0, treasury.quotes)
            assertEquals(SaitoStoreWithdrawal.State.SUBMITTED, saved.result?.state)
            assertEquals(row.get("walletDigest").asString, walletDigest()); assertEquals(row.getAsJsonObject("retainedWork"), retainedWork())
            assertTrue(scene.peer.transactions.isEmpty())
            captureNativeScreen(File(evidence, "cold-authenticated-original-manual-request.png"))
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
