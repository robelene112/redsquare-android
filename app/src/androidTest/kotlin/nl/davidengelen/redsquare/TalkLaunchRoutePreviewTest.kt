package nl.davidengelen.redsquare

import android.content.Intent
import android.app.NotificationManager
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.CallPhase
import nl.davidengelen.redsquare.talk.TalkAccount
import nl.davidengelen.redsquare.talk.TalkRuntime
import nl.davidengelen.redsquare.talk.TalkService
import org.junit.Rule
import org.junit.Test
import java.io.File

class TalkLaunchRoutePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aRealCallNotificationReturnsToTheCallAndItsConsumedColdRouteDoesNotReplayAfterEnding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notifications = context.getSystemService(NotificationManager::class.java)
        withAppFrameFixture("call-return", "talk-launch-route-evidence", mapOfficialPeer = true,
            advertisedServices = "archive||;relay||") { firstActivity ->
            var account: TalkAccount? = null
            var token: String? = null
            try {
                waitText("Later"); ui.onNodeWithText("Later").performClick()
                val wallet = AndroidWalletStore(context).loadExisting()
                var original: Intent? = null
                firstActivity.onActivity { original = Intent(it.intent) }
                val endpoint = requireNotNull(original).getStringExtra("feedPeerUrl")!!
                account = TalkRuntime.account(context, wallet, SaitoBackend.OWNER, endpoint).also { it.attach() }
                check(runBlocking { requireNotNull(account).start(TalkLink(SaitoBackend.OWNER,
                    TalkRoom(SaitoWallet.create().publicKey, wallet.publicKey, mode = TalkMode.VOICE)), null, false, false) })
                token = requireNotNull(TalkRuntime.call.value).token
                ui.waitUntil(5_000) { notifications.activeNotifications.any { it.id == TalkService.ID } }
                val returnUri = Uri.Builder().scheme("redsquare-talk").authority("call").appendPath(requireNotNull(token))
                    .appendQueryParameter("owner", wallet.publicKey).appendQueryParameter("backend", SaitoBackend.OWNER.id).build()
                // Model an original cold notification launch with the owned endpoint configuration.
                // Then exercise the actual Android notification PendingIntent against that Activity.
                firstActivity.close()
                ActivityScenario.launch<MainActivity>(Intent(requireNotNull(original)).setAction(Intent.ACTION_VIEW)
                    .setData(returnUri)).use { activity ->
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                        "talk-launch-route-evidence/call-return-failure.png")) }) {
                        ui.waitUntil(5_000) { ui.onNodeWithContentDescription("Switch app: Saito Talk").isDisplayed() }
                        check(TalkRuntime.call.value?.token == token && TalkRuntime.call.value?.phase?.live == true)
                        check(!requireNotNull(TalkRuntime.call.value).camera && !requireNotNull(TalkRuntime.call.value).microphone)
                        switchModule("Saito Talk", "Stack"); waitStack()
                        requireNotNull(notifications.activeNotifications.single { it.id == TalkService.ID }
                            .notification.contentIntent).send()
                        ui.waitUntil(5_000) { ui.onNodeWithContentDescription("Switch app: Saito Talk").isDisplayed() }
                        check(TalkRuntime.call.value?.token == token)
                        captureNativeScreen(File(context.cacheDir, "talk-launch-route-evidence/actual-notification-return.png"))
                        ui.onNode(hasText("End call") and hasClickAction()).performClick()
                        ui.waitUntil(5_000) { TalkRuntime.call.value?.phase == CallPhase.ENDED &&
                            notifications.activeNotifications.none { it.id == TalkService.ID } }
                        switchModule("Saito Talk", "Stack"); waitStack()
                        activity.recreate(); waitStack()
                        ui.onAllNodesWithText(context.getString(R.string.talk_return_expired)).assertCountEquals(0)
                        check(TalkRuntime.call.value?.phase?.live != true)
                        activity.onActivity {
                            check(it.intent.data == returnUri)
                            check(it.intent.getStringExtra("feedPeerUrl") == endpoint)
                            check(it.intent.getStringExtra("feedOfficialPeerUrl") == endpoint)
                        }
                        check(wallet.exportJson() == AndroidWalletStore(context).loadExisting().exportJson())
                        check(AndroidBackendStore(context).load() == SaitoBackend.OWNER)
                        val folder = File(context.cacheDir, "talk-launch-route-evidence").apply { mkdirs() }
                        captureNativeScreen(File(folder, "ended-call-consumed-stack.png"))
                        File(folder, "call-return.json").writeText(JsonObject().apply {
                            addProperty("originalColdReturnRouteHandled", true)
                            addProperty("actualAndroidNotificationReturnedToExactCall", true)
                            addProperty("callEndedAndForegroundRemoved", true)
                            addProperty("recreationRetainedStackWithoutExpiredReplay", true)
                            addProperty("originalLaunchDataAndEndpointsPreserved", true)
                            addProperty("captureRestarted", false)
                        }.toString())
                    }
                }
            } finally { token?.let(TalkRuntime::end); account?.detach() }
        }
    }

    @Test fun consumedColdInvitationsDoNotReplayAndNewPendingDeliveriesSurviveRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val host = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
        val nextRoom = SaitoWallet.fromPrivateKey("0".repeat(63) + "3").publicKey
        val first = TalkLink(SaitoBackend.OWNER, TalkRoom(host, host)).url()
        val second = TalkLink(SaitoBackend.OFFICIAL, TalkRoom(nextRoom, host)).url()
        val fault = File(context.noBackupFilesDir, "backend.pending")
        withAppFrameFixture("cold-and-pending", "talk-launch-route-evidence", mapOfficialPeer = true,
            advertisedServices = "archive||;relay||", launchUri = first) { activity ->
            var originalEndpoint: String? = null
            activity.onActivity { originalEndpoint = it.intent.getStringExtra("feedPeerUrl") }
            var faultOwned = false
            var primary: Throwable? = null
            try {
                waitText("Ready to join?")
                ui.onNodeWithText(context.getString(R.string.talk_network, SaitoBackend.OWNER.domain)).assertIsDisplayed()
                check(TalkRuntime.call.value?.phase?.live != true)
                cancelInvitation()
                switchModule("Saito Talk", "Stack"); waitStack()
                activity.recreate(); waitStack()
                captureNativeScreen(File(context.cacheDir, "talk-launch-route-evidence/cold-consumed-stack.png"))

                check(!fault.exists()); check(fault.mkdir()); faultOwned = true
                activity.recreate()
                waitText(context.getString(R.string.backend_load_error))
                deliver(activity, second)
                activity.recreate()
                waitText(context.getString(R.string.backend_load_error))
                check(fault.delete()); faultOwned = false
                ui.openSettings()
                ui.onNodeWithText("Node").performScrollTo().performClick()
                ui.onNodeWithText(SaitoBackend.OWNER.domain).performScrollTo().performClick()
                waitText("Ready to join?")
                ui.onNodeWithText(context.getString(R.string.talk_network, SaitoBackend.OFFICIAL.domain)).assertIsDisplayed()
                check(TalkRuntime.call.value?.phase?.live != true)
                captureNativeScreen(File(context.cacheDir, "talk-launch-route-evidence/pending-new-delivery.png"))
                cancelInvitation()
                switchModule("Saito Talk", "Stack"); waitStack()
                activity.recreate(); waitStack()
                captureNativeScreen(File(context.cacheDir, "talk-launch-route-evidence/new-delivery-consumed-stack.png"))

                // A later Android delivery is new even when the user opens the same URL again.
                deliver(activity, second); waitText("Ready to join?")
                ui.onNodeWithText(context.getString(R.string.talk_network, SaitoBackend.OFFICIAL.domain)).assertIsDisplayed()
                activity.onActivity {
                    check(it.intent.data.toString() == first)
                    check(it.intent.getStringExtra("feedPeerUrl") == originalEndpoint)
                    check(it.intent.getStringExtra("feedOfficialPeerUrl") == originalEndpoint)
                }
                check(AndroidBackendStore(context).load() == SaitoBackend.OWNER)
                check(TalkRuntime.call.value?.phase?.live != true)
                val folder = File(context.cacheDir, "talk-launch-route-evidence").apply { mkdirs() }
                File(folder, "cold-and-pending.json").writeText(JsonObject().apply {
                    addProperty("coldInvitationConsumedOnce", true)
                    addProperty("pendingNewDeliverySurvivedRecreation", true)
                    addProperty("laterConsumedDeliveryDidNotReplay", true)
                    addProperty("sameUrlNewDeliveryAccepted", true)
                    addProperty("originalLaunchDataAndEndpointsPreserved", true)
                    addProperty("captureStarted", false)
                }.toString())
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally { cleanupAfterTest(primary, {
                if (faultOwned && fault.exists()) check(fault.isDirectory && fault.delete())
            }) }
        }
    }

    private fun waitText(text: String, timeout: Long = 15_000) {
        ui.waitUntil(timeout) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun switchModule(from: String, to: String) {
        ui.onNodeWithContentDescription("Switch app: " + from).performClick()
        ui.onNode(hasText(to) and hasClickAction()).performClick()
        ui.waitUntil(5_000) { ui.onNodeWithContentDescription("Switch app: " + to).isDisplayed() }
    }

    private fun waitStack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ui.waitUntil(5_000) { ui.onNodeWithContentDescription("Switch app: Stack").isDisplayed() }
        ui.waitUntil(5_000) { ui.onAllNodesWithTag("stack-live-content").fetchSemanticsNodes().isNotEmpty() }
        val content = ui.onNodeWithTag("stack-live-content")
        content.performScrollToNode(hasText("Latest") and hasClickAction())
        ui.onNode(hasText("Latest") and hasClickAction()).performClick()
        content.performScrollToIndex(0)
        waitText(context.getString(R.string.stack_empty))
        ui.onNodeWithContentDescription("Switch app: Stack").assertIsDisplayed()
    }

    private fun cancelInvitation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(context.getString(R.string.talk_cancel)) and hasClickAction())
        ui.onNode(hasText(context.getString(R.string.talk_cancel)) and hasClickAction()).performClick()
        waitText("Create a room")
    }

    private fun deliver(activity: ActivityScenario<MainActivity>, url: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        activity.onActivity { it.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
    }
}
