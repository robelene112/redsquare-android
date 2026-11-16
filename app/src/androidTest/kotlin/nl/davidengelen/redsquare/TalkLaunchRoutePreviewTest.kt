package nl.davidengelen.redsquare

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.TalkRuntime
import org.junit.Rule
import org.junit.Test
import java.io.File

class TalkLaunchRoutePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

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
