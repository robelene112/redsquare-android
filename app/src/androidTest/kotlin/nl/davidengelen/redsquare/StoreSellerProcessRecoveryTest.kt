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

/** Host-driven distinct processes, from an actual reviewed/authenticated owned-peer offer. */
class StoreSellerProcessRecoveryTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val checkpoint get() = File(context.cacheDir, "store-seller-process-checkpoint.json")
    private val originals get() = File(context.noBackupFilesDir, "store-seller-process-originals")
    private val evidence get() = File(context.cacheDir, "store-seller-process-evidence").apply { mkdirs() }
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
    private val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
    private val draft = "Retain this unsent Chat draft through a Store offer restart"
    private val token = "11100000-0000-4000-8000-000000000001"
    private val prefixes get() = listOf("wallet.", "backend.", "feed.", "conversation.", "profile.", "profilemeta.", "profiledraft.",
        "search.", "authorposts.", "postdraft.", "namemap.", "following.", "quoteoriginal.", "vault.", "stack.", "stackwindow.",
        "stackdraft.", "transfer.", "store-orders.", "store-listings.", "store-delists.", "store-images.", "store-sale-draft.", "chatwork.", "fileshare.", "chatpush.$owner.", "groupchatpush.$owner.", "avatarprofiles.", "displaynames.")
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

    @Test fun terminateAfterAnAuthenticatedStoreOfferWithUnknownAvailability() {
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
            withStoreSellerFixture("process-before-death", expected = 1) { scene, _, _ ->
                val previous = AndroidWalletStore(context).loadExisting()
                val wallet = previous.withChatContact(contact)
                AndroidWalletStore(context).replace(previous, wallet)
                ChatWorkStore(context, wallet, SaitoBackend.OWNER, contact).saveDraft(draft)
                FileTransferStore(context, owner, SaitoBackend.OWNER, contact).save(FileTransfer(token, owner, SaitoBackend.OWNER,
                    contact, "ws://127.0.0.1:1/wsopen", Fileshare.Offer("store111", "retained-file.bin", 1, "application/octet-stream"), true, FilePhase.CANCELLED))
                scene.peer.disconnectAfterTransaction = true
                ui.openNativeStoreSelling(); ui.reviewNativeStoreSale()
                ui.waitUntil(15_000) { ui.onAllNodesWithText("Authorize and publish").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNodeWithText("Authorize and publish").performClick(); waitForSystemPin(device)
                assertTrue(scene.peer.transactions.isEmpty()); enterRealSystemPin(device)
                ui.waitUntil(25_000) { ui.onNode(hasText("Check and retry original offer") and isEnabled()).isDisplayed() }
                val candidate = storeSaleCandidate(scene)
                assertEquals(1, scene.peer.transactions.size)
                assertEquals(candidate.transaction.encoded(), StoreListingStore(context, owner).read().single().listing.transaction.encoded())
                row.addProperty("phase", "pending"); row.addProperty("owner", owner); row.addProperty("backend", "owner")
                row.addProperty("walletDigest", walletDigest()); row.addProperty("candidate", candidate.transaction.encoded())
                row.add("retainedWork", retainedWork())
                captureNativeScreen(File(evidence, "pending-order-before-real-process-death.png")); write(row)
                Process.killProcess(Process.myPid()); error("Expected actual process termination")
            }
        }
    }

    @Test fun coldOfferRequiresFreshAuthorizationAndRetriesOnlyTheOriginalOffer() {
        val row = read(); assertEquals("pending", row.get("phase").asString)
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        assertEquals(owner, row.get("owner").asString); assertEquals(row.get("walletDigest").asString, walletDigest())
        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
        assertTrue(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        withStoreSellerFixture("process-cold", expected = 1, preserve = true) { scene, _, _ ->
            ui.openNativeStoreSelling()
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Native sale"))
            ui.onNodeWithText("Native sale").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Check and retry original offer").isDisplayed() }
            assertTrue(scene.peer.transactions.isEmpty())
            assertEquals(draft, ChatWorkStore(context, AndroidWalletStore(context).loadExisting(), SaitoBackend.OWNER, contact).load().draft)
            assertEquals(FilePhase.CANCELLED, FileTransferStore(context, owner, SaitoBackend.OWNER, contact).load().single { it.token == token }.phase)
            assertEquals(row.getAsJsonObject("retainedWork"), retainedWork())
            captureNativeScreen(File(evidence, "cold-order-no-automatic-payment.png"))
            ui.onNodeWithText("Check and retry original offer").performClick(); waitForSystemPin(device)
            assertTrue("Old authority cannot survive process death", scene.peer.transactions.isEmpty())
            enterRealSystemPin(device)
            ui.waitUntil(25_000) { scene.peer.transactions.size == 1 && ui.onNode(hasText("Check and retry original offer") and isEnabled()).isDisplayed() }
            assertEquals(row.get("candidate").asString, storeSaleCandidate(scene).transaction.encoded())
            assertEquals(row.get("candidate").asString, StoreListingStore(context, owner).read().single().listing.transaction.encoded())
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
