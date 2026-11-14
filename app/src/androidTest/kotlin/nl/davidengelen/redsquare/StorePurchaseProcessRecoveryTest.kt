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

/** Host-driven distinct processes, from an actual reviewed/authenticated owned-peer purchase. */
class StorePurchaseProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val checkpoint get() = File(context.cacheDir, "store-purchase-process-checkpoint.json")
    private val originals get() = File(context.noBackupFilesDir, "store-purchase-process-originals")
    private val evidence get() = File(context.cacheDir, "store-purchase-process-evidence").apply { mkdirs() }
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "4").publicKey
    private val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
    private val draft = "Retain this unsent Chat draft through a Store purchase restart"
    private val token = "11000000-0000-4000-8000-000000000001"
    private val prefixes get() = listOf("wallet.", "backend.", "feed.", "conversation.", "profile.", "profilemeta.", "profiledraft.",
        "search.", "authorposts.", "postdraft.", "namemap.", "following.", "quoteoriginal.", "vault.", "stack.", "stackwindow.",
        "stackdraft.", "transfer.", "store-orders.", "chatwork.", "fileshare.", "chatpush.$owner.", "groupchatpush.$owner.", "avatarprofiles.", "displaynames.")
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

    @Test fun terminateAfterAnAuthenticatedStorePaymentWithUnknownDelivery() {
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
            withStorePurchaseFixture("process-before-death", 1) { scene ->
                val wallet = AndroidWalletStore(context).loadExisting()
                ChatWorkStore(context, wallet, SaitoBackend.OWNER, contact).saveDraft(draft)
                FileTransferStore(context, owner, SaitoBackend.OWNER, contact).save(FileTransfer(token, owner, SaitoBackend.OWNER,
                    contact, "ws://127.0.0.1:1/wsopen", Fileshare.Offer("store110", "retained-file.bin", 1, "application/octet-stream"), true, FilePhase.CANCELLED))
                scene.node.peer.disconnectAfterTransaction = true
                ui.openStorePurchaseProduct()
                ui.waitUntil(15_000) { ui.onAllNodesWithText("Confirm and pay").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNodeWithText("Confirm and pay").performScrollTo().performClick(); waitForSystemPin(device)
                assertTrue(scene.node.peer.transactions.isEmpty()); enterRealSystemPin(device)
                ui.waitUntil(25_000) { ui.onNode(hasText("Retry original payment") and isEnabled()).isDisplayed() }
                val candidate = requireNotNull(scene.node.candidate())
                assertEquals(1, scene.node.peer.transactions.size)
                assertEquals(candidate.transaction.encoded(), StoreOrderStore(context, owner).read().single().purchase.transaction.encoded())
                row.addProperty("phase", "pending"); row.addProperty("owner", owner); row.addProperty("backend", "owner")
                row.addProperty("walletDigest", walletDigest()); row.addProperty("candidate", candidate.transaction.encoded())
                row.add("retainedWork", retainedWork())
                captureNativeScreen(File(evidence, "pending-order-before-real-process-death.png")); write(row)
                Process.killProcess(Process.myPid()); error("Expected actual process termination")
            }
        }
    }

    @Test fun coldOrderRequiresFreshAuthorizationAndRetriesOnlyTheOriginalPayment() {
        val row = read(); assertEquals("pending", row.get("phase").asString)
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        assertEquals(owner, row.get("owner").asString); assertEquals(row.get("walletDigest").asString, walletDigest())
        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
        assertTrue(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        withStorePurchaseFixture("process-cold", 1, preserveOrders = true) { scene ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }; ui.onNodeWithText("Later").performClick()
            ui.onNodeWithContentDescription("Switch app: Red Square").performClick(); ui.onNodeWithText("Store").performScrollTo().performClick()
            ui.onNodeWithText("Orders").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Field notes access key").isDisplayed() }; ui.onNodeWithText("Field notes access key").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Retry original payment").isDisplayed() }
            assertTrue(scene.node.peer.transactions.isEmpty())
            assertEquals(draft, ChatWorkStore(context, AndroidWalletStore(context).loadExisting(), SaitoBackend.OWNER, contact).load().draft)
            assertEquals(FilePhase.CANCELLED, FileTransferStore(context, owner, SaitoBackend.OWNER, contact).load().single { it.token == token }.phase)
            assertEquals(row.getAsJsonObject("retainedWork"), retainedWork())
            captureNativeScreen(File(evidence, "cold-order-no-automatic-payment.png"))
            ui.onNodeWithText("Retry original payment").performScrollTo().performClick(); waitForSystemPin(device)
            assertTrue("Old authority cannot survive process death", scene.node.peer.transactions.isEmpty())
            enterRealSystemPin(device)
            ui.waitUntil(25_000) { scene.node.peer.transactions.size == 1 && ui.onNode(hasText("Check order") and isEnabled()).isDisplayed() }
            assertEquals(row.get("candidate").asString, requireNotNull(scene.node.candidate()).transaction.encoded())
            assertEquals(row.get("candidate").asString, StoreOrderStore(context, owner).read().single().purchase.transaction.encoded())
            assertEquals(row.get("walletDigest").asString, walletDigest()); assertEquals(row.getAsJsonObject("retainedWork"), retainedWork())
            captureNativeScreen(File(evidence, "cold-fresh-auth-original-payment.png"))
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
