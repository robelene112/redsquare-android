package nl.davidengelen.redsquare

import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.feed.PostDraftStore
import nl.davidengelen.redsquare.notifications.SocialPush
import nl.davidengelen.redsquare.notifications.SocialPushResult
import nl.davidengelen.redsquare.notifications.SocialPushSettings
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual system tap, mounted composer and independent signed Archive, without outgoing writes. */
class SocialPushTapPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun tappingAReplyPreservesTheLatestUnsentPostAndPhotoAndOpensTheExactConversation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(activeMainActivities().isEmpty()) { "The tap fixture requires no pre-existing MainActivity" }
        val fixture = instrumentation.context.assets.open("native-notification-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val images = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val photo = images.getAsJsonObject("images").get("red").asString
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only.
        val owner = wallet.publicKey
        val root = fixture.getAsJsonObject("root")
        val reply = fixture.getAsJsonObject("incomingReply")
        val signature = "4626eafbb90d66fd650de2a943864198e3daeae0b8876b4c224625b4a438113508e44287fee31d34d117356565d67036537c65dbcc66d8589ea300e2fb414526"
        assertEquals(signature, reply.get("sig").asString)
        val peer = ControlledFeedPeer("[$root]", archiveResponse = { criteria ->
            when {
                criteria.has("sig") -> when (criteria.get("sig").asString) {
                    signature -> "[$reply]"
                    root.get("sig").asString -> "[$root]"
                    else -> "[]"
                }
                criteria.get("field1")?.asString == "RedSquare" -> "[$root]"
                else -> "[]"
            }
        })
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val previousJson = previous.exportJson()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("socialpush.$owner.", "personal.$owner.",
            "postdraft.owner.$owner"), owner)
        val manager = context.getSystemService(NotificationManager::class.java)
        val priorAlerts = manager.activeNotifications.map { it.tag to it.id }.toSet()
        val device = UiDevice.getInstance(instrumentation)
        val latest = "Latest unsent post 🌱\nKeep the photo and this last edit"
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(previous, wallet)
            backends.save(SaitoBackend.OWNER)
            SocialPushSettings(context, owner).setEnabled(true)
            // Existing encrypted draft boundary supplies a normalized photo. The latest text is typed in the UI.
            PostDraftStore(context, owner, SaitoBackend.OWNER).save(
                PostDraftStore.Saved("Earlier saved text", images = listOf(photo)))
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint)).use {
                    withFailureCapture({
                        captureNativeScreen(File(context.cacheDir, "social-push-evidence/tap-post-failure.png"))
                    }) {
                        waitFor(hasText("New post") and hasClickAction() and isEnabled())
                        ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        waitFor(hasSetTextAction() and isEnabled())
                        ui.onNode(hasSetTextAction()).assertTextEquals("Earlier saved text")
                        ui.onNodeWithContentDescription("Image 1 of 1").performScrollTo().assertIsDisplayed()
                        ui.onNode(hasSetTextAction()).performTextReplacement(latest)
                        val payload = JsonObject().apply {
                            addProperty("version", 1); addProperty("wallet", owner)
                            addProperty("backend", "owner"); addProperty("signature", signature)
                        }.toString()
                        assertEquals(SocialPushResult.PUBLISHED, runBlocking(Dispatchers.IO) {
                            SocialPush.receive(context, payload, endpoint, 1700000001000)
                        })
                        assertTrue(device.openNotification())
                        val alert = requireNotNull(device.wait(Until.findObject(By.text("Replied to your post")), 10_000))
                        captureNativeScreen(File(context.cacheDir, "social-push-evidence/tap-post-alert.png"), "Replied to your post")
                        alert.click()
                        waitFor(hasText("Conversation"))
                        waitFor(hasText("Reply notification reference"))
                        ui.onNodeWithText("Reply notification reference").assertIsDisplayed()
                        captureNativeScreen(File(context.cacheDir, "social-push-evidence/tap-post-conversation.png"), "Reply notification reference")
                        ui.onNodeWithText("Back").performClick()
                        waitFor(hasText("New post") and hasClickAction() and isEnabled())
                        ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        waitFor(hasSetTextAction() and isEnabled())
                        ui.onNode(hasSetTextAction()).assertTextEquals(latest)
                        val bitmap = ui.onNodeWithContentDescription("Image 1 of 1").performScrollTo()
                            .assertIsDisplayed().captureToImage().asAndroidBitmap()
                        assertEquals(0xffdc1e28.toInt(), bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
                        captureNativeScreen(File(context.cacheDir, "social-push-evidence/tap-post-draft-restored.png"))
                        val saved = PostDraftStore(context, owner, SaitoBackend.OWNER).load()
                        assertEquals(latest, saved.text)
                        assertEquals(listOf(photo), saved.images)
                        assertNull(saved.transaction)
                        assertFalse(saved.queued)
                        check(wallets.loadExisting().exportJson() == wallet.exportJson()) { "Active fixture wallet changed" }
                        assertEquals(SaitoBackend.OWNER, backends.load())
                        assertTrue(peer.transactions.isEmpty() && peer.reports.isEmpty() && peer.relayedMessages.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            var activitiesStopped = false
            fun requireStopped() = check(activitiesStopped) { "Installation restoration deferred because a fixture Activity remains active" }
            val cleanup = listOf<() -> Unit>(
                { finishFixtureActivities(); activitiesStopped = true },
                { manager.activeNotifications.filter { (it.tag to it.id) !in priorAlerts }.forEach { manager.cancel(it.tag, it.id) } },
                { requireStopped(); retained.restoreFiles() }, { requireStopped(); retained.restorePreferences() },
                { requireStopped(); wallets.replace(wallets.loadExisting(), previous); check(wallets.loadExisting().exportJson() == previousJson) },
                { requireStopped(); backends.save(previousBackend); check(backends.load() == previousBackend) })
            var cleanupFailure: Throwable? = null
            cleanup.forEach { action -> try { action() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure!!.addSuppressed(failure)
            } }
            cleanupFailure?.let { if (primary != null) primary!!.addSuppressed(it) else throw it }
        }
    }

    @Test fun anExportedLaunchIgnoresMalformedNotificationStateExtras() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(activeMainActivities().isEmpty())
        val wallets = AndroidWalletStore(context)
        val wallet = wallets.loadOrCreate()
        val backend = AndroidBackendStore(context).load()
        val fixture = instrumentation.context.assets.open("native-notification-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val peer = ControlledFeedPeer("[${fixture.getAsJsonObject("root")}]")
        val manager = context.getSystemService(NotificationManager::class.java)
        val alerts = manager.activeNotifications.map { it.tag to it.id }.toSet()
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("social.initialized", false)
                .putExtra("social.generation", "External generation is not a number")
                .putExtra("social.pending", "External pending state must not be used")
                .setData(android.net.Uri.Builder().scheme("redsquare").authority("social")
                    .appendPath(backend.id).appendPath(wallet.publicKey)
                    .appendPath("4626eafbb90d66fd650de2a943864198e3daeae0b8876b4c224625b4a438113508e44287fee31d34d117356565d67036537c65dbcc66d8589ea300e2fb414526").build())
            var primary: Throwable? = null
            try {
                ActivityScenario.launch<MainActivity>(intent).use {
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir, "social-push-evidence/external-state-failure.png")) }) {
                        waitFor(hasText("New post") and hasClickAction() and isEnabled())
                        // This unrelated wallet has not opted in. A real validated route is retained safely,
                        // rather than accepting external saved-state keys or silently ignoring the URI.
                        val unavailable = context.getString(R.string.social_push_open_failed)
                        waitFor(hasText(unavailable))
                        ui.onNodeWithText(unavailable).assertExists()
                        captureNativeScreen(File(context.cacheDir, "social-push-evidence/external-state-rejected.png"))
                        check(wallets.loadExisting().exportJson() == wallet.exportJson()) { "Active installation wallet changed" }
                        assertEquals(backend, AndroidBackendStore(context).load())
                        assertEquals(alerts, manager.activeNotifications.map { it.tag to it.id }.toSet())
                        assertTrue(peer.transactions.isEmpty() && peer.reports.isEmpty() && peer.relayedMessages.isEmpty())
                        peer.assertHealthy()
                    }
                }
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                try { finishFixtureActivities() }
                catch (failure: Throwable) { if (primary != null) primary!!.addSuppressed(failure) else throw failure }
            }
        }
    }

    private fun activeMainActivities(): List<MainActivity> {
        var result = emptyList<MainActivity>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            result = Stage.values().filter { it != Stage.DESTROYED }
                .flatMap { monitor.getActivitiesInStage(it) }.filterIsInstance<MainActivity>().distinct()
        }
        return result
    }

    private fun finishFixtureActivities() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activities = activeMainActivities()
        instrumentation.runOnMainSync { activities.forEach { it.finish() } }
        instrumentation.waitForIdleSync()
        ui.waitUntil(5_000) { activeMainActivities().isEmpty() }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        ui.waitUntil(15_000) { ui.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }
}
