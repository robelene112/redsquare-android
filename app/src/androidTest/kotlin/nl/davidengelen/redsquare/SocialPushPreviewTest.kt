package nl.davidengelen.redsquare

import android.app.Notification
import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.notifications.SocialPush
import nl.davidengelen.redsquare.notifications.SocialPushSettings
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Inject only the external wake-up transport; use actual signed Archive and Android alert boundaries. */
class SocialPushPreviewTest {
    @Test fun anAvailableSignedReplyShowsOneAndroidReplyAlertAfterVerifyingItsParent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val fixture = instrumentation.context.assets.open("native-notification-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only.
        val owner = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        assertEquals(owner, wallet.publicKey)
        val reply = fixture.getAsJsonObject("incomingReply")
        val target = "4626eafbb90d66fd650de2a943864198e3daeae0b8876b4c224625b4a438113508e44287fee31d34d117356565d67036537c65dbcc66d8589ea300e2fb414526"
        assertEquals(target, reply.get("sig").asString)
        val root = fixture.getAsJsonObject("root")
        val reads = CopyOnWriteArrayList<String>()
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            require(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            require(criteria.get("limit").asInt == 1)
            val signature = criteria.get("sig").asString
            reads.add(signature)
            when (signature) {
                target -> "[$reply]"
                "c87f3ca402840cd7add095dbf1fda809f34d9cd3f7dae9af022e2a0f2a631f376beb54dabe886a5a73b02abe79afe51652efa57826ccd2591001d73f90544fd9" -> "[$root]"
                else -> error("Unexpected exact social reference")
            }
        })
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val previousJson = previous.exportJson()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("socialpush.$owner.", "personal.$owner."), owner)
        val manager = context.getSystemService(NotificationManager::class.java)
        val priorAlerts = manager.activeNotifications.map { it.tag to it.id }.toSet()
        val device = UiDevice.getInstance(instrumentation)
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(previous, wallet)
            backends.save(SaitoBackend.OWNER)
            SocialPushSettings(context, owner).setEnabled(true)
            peer.start().use { server ->
                val payload = JsonObject().apply {
                    addProperty("version", 1); addProperty("wallet", owner)
                    addProperty("backend", "owner"); addProperty("signature", target)
                }.toString()
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                withFailureCapture({
                    device.openNotification()
                    captureNativeScreen(File(context.cacheDir, "social-push-evidence/reply-alert-failure.png"))
                }) {
                    runBlocking(Dispatchers.IO) { SocialPush.receive(context, payload, endpoint, 1700000001000) }
                    val alerts = manager.activeNotifications.filter { (it.tag to it.id) !in priorAlerts }
                    assertEquals("The verified reply must publish an actual Android alert", 1, alerts.size)
                    val alert = alerts.single().notification
                    assertEquals("replies", alert.channelId)
                    assertEquals("Replied to your post", alert.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                    assertTrue("Candidate and signed parent must both be read", reads.contains(target) && reads.contains(root.get("sig").asString))
                    assertTrue(device.openNotification())
                    assertTrue("The system shade must visibly show the reply", device.wait(Until.hasObject(By.text("Replied to your post")), 15_000))
                    captureNativeScreen(File(context.cacheDir, "social-push-evidence/reply-alert.png"), "Replied to your post")
                    device.pressBack()
                    runBlocking(Dispatchers.IO) { SocialPush.receive(context, payload, endpoint, 1700000001001) }
                    assertEquals("Redelivery must retain one alert", 1,
                        manager.activeNotifications.count { (it.tag to it.id) !in priorAlerts })
                    assertTrue(peer.transactions.isEmpty() && peer.reports.isEmpty() && peer.relayedMessages.isEmpty())
                    peer.assertHealthy()
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            val cleanup = listOf<() -> Unit>(
                { device.pressBack() },
                { manager.activeNotifications.filter { (it.tag to it.id) !in priorAlerts }.forEach { manager.cancel(it.tag, it.id) } },
                { retained.restoreFiles() },
                { retained.restorePreferences() },
                { wallets.replace(wallets.loadExisting(), previous); check(wallets.loadExisting().exportJson() == previousJson) },
                { backends.save(previousBackend); check(backends.load() == previousBackend) })
            var cleanupFailure: Throwable? = null
            cleanup.forEach { action -> try { action() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure!!.addSuppressed(failure)
            } }
            cleanupFailure?.let { if (primary != null) primary!!.addSuppressed(it) else throw it }
        }
    }
}
