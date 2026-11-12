package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.TalkRuntime
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TalkInvitationPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun aNarrowInvitationPreviewsWithoutCaptureOrChangingTheNode() = invitation(320)
    @Test fun aWideInvitationPreviewsWithoutCaptureOrChangingTheNode() = invitation(412)
    @Test fun aSharedTalkUrlResolvesThroughAndroidToNativePrejoinWithoutCapture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val caller = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
        val link = TalkLink(SaitoBackend.OFFICIAL, TalkRoom(caller, caller)).url()
        withAppFrameFixture("talk-external-invitation", "talk-invitation-evidence") { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            // Ordinary implicit VIEW action constrained to this installed test package.
            // Android verified/default association with third-party domains is a separate36 qualification.
            val external = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                .addCategory(android.content.Intent.CATEGORY_BROWSABLE).setPackage(context.packageName)
            assertNotNull(context.packageManager.resolveActivity(external, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY))
            activity.onActivity { it.startActivity(external) }
            ui.waitUntil(5000) { ui.onNodeWithContentDescription("Switch app: Saito Talk").isDisplayed() }
            ui.onNodeWithText("Ready to join?").assertExists()
            assertTrue(TalkRuntime.call.value?.phase?.live != true)
            assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
            captureNativeScreen(File(context.cacheDir, "talk-invitation-evidence/implicit-view-prejoin.png"))
        }
    }
    @Test fun anotherInvitationPreviewsWithoutReplacingTheActiveCall() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withAppFrameFixture("talk-busy-invitation", "talk-invitation-evidence", advertisedServices = "archive||;relay||") { activity ->
            var token: String? = null
            var account: nl.davidengelen.redsquare.talk.TalkAccount? = null
            try {
                ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                val wallet = AndroidWalletStore(context).loadExisting()
                var endpoint: String? = null
                activity.onActivity { endpoint = it.intent.getStringExtra("feedPeerUrl") }
                account = TalkRuntime.account(context, wallet, SaitoBackend.OWNER, requireNotNull(endpoint)).also { it.attach() }
                assertTrue(kotlinx.coroutines.runBlocking { requireNotNull(account).start(TalkLink(SaitoBackend.OWNER,
                    TalkRoom(SaitoWallet.create().publicKey, wallet.publicKey)), null, false, false) })
                token = requireNotNull(TalkRuntime.call.value).token
                val other = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
                val link = TalkLink(SaitoBackend.OFFICIAL, TalkRoom(SaitoWallet.create().publicKey, other)).url()
                activity.onActivity { it.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                    .setPackage(context.packageName)) }
                ui.waitUntil(5000) { ui.onNodeWithText("Ready to join?").isDisplayed() }
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Join room") and hasClickAction())
                ui.onNode(hasText("Join room") and hasClickAction()).assertIsNotEnabled()
                ui.onNodeWithText(context.getString(R.string.talk_other_call_active)).assertExists()
                assertEquals(token, TalkRuntime.call.value?.token)
                assertFalse(requireNotNull(TalkRuntime.call.value).camera)
                assertFalse(requireNotNull(TalkRuntime.call.value).microphone)
                assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                captureNativeScreen(File(context.cacheDir, "talk-invitation-evidence/busy-prejoin.png"))
                ui.onNode(hasText("Return to call") and hasClickAction()).performClick()
                ui.waitUntil(5000) { ui.onNodeWithText("Call network: saito.davidengelen.nl").isDisplayed() }
                ui.onNode(hasText("End call") and hasClickAction()).performClick()
                ui.waitUntil(5000) { TalkRuntime.call.value?.phase == nl.davidengelen.redsquare.talk.CallPhase.ENDED }
            } finally { token?.let(TalkRuntime::end); account?.detach() }
        }
    }
    private fun invitation(width: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = TalkRoom(SaitoWallet.fromPrivateKey("0".repeat(63) + "3").publicKey,
            SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey)
        val link = TalkLink(SaitoBackend.OFFICIAL, room).url()
        val post = linkedFeedRow("A community call invitation.", link,
            """{"link_properties":{"og:title":"Join the community room","og:url":"$link"}}""")
        withAppFrameFixture("talk-invitation-$width", "talk-invitation-evidence", feedRows = "[$post]") { activity ->
            val folder = File(context.cacheDir, "talk-invitation-evidence").apply { mkdirs() }
            ui.waitUntil(15_000) { ui.onNodeWithText("A community call invitation.").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.withPostViewports(activity, folder, "invitation-$width", widthDp = width) { label, theme ->
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Join the community room") and hasClickAction())
                ui.onNode(hasText("Join the community room") and hasClickAction()).performClick()
                ui.waitUntil(5000) { ui.onNodeWithContentDescription("Switch app: Saito Talk").isDisplayed() }
                ui.onNodeWithText("Ready to join?").assertExists()
                ui.onNodeWithText("Call network: saito.io").assertExists()
                ui.onNodeWithText("Your microphone and camera stay off until you start or join the call.").performScrollTo().assertIsDisplayed()
                assertTrue("An invitation is a preview, not consent to capture", TalkRuntime.call.value?.phase?.live != true)
                assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Join room") and hasClickAction())
                val join = ui.onNode(hasText("Join room") and hasClickAction()).assertIsDisplayed().fetchSemanticsNode()
                assertTrue(join.boundsInRoot.height / context.resources.displayMetrics.density >= 48f)
                captureNativeScreen(File(folder, "$label-prejoin.png"))
                ui.onNode(hasText("Back") and hasClickAction()).performScrollTo().performClick()
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Create a room") and hasClickAction())
                ui.onNodeWithText("Create a room").assertIsDisplayed()
                ui.onNodeWithContentDescription("Switch app: Saito Talk").performClick()
                ui.onNode(hasText("Red Square") and hasClickAction()).performClick()
                ui.waitUntil(5000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
            }
        }
    }
}
