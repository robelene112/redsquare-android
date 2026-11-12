package nl.davidengelen.redsquare

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TalkActiveViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun videoCallControlsStayVisibleAndReachableAt320dpAcrossThemesAndLargeText() = active(320)
    @Test fun videoCallControlsStayVisibleAndReachableAt412dpAcrossThemesAndLargeText() = active(412)
    private fun active(width: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withAppFrameFixture("talk-active-$width", "talk-active-evidence", advertisedServices = "archive||;relay||") { activity ->
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            val wallet = AndroidWalletStore(context).loadExisting()
            val backend = AndroidBackendStore(context).load()
            var endpoint: String? = null
            activity.onActivity { endpoint = it.intent.getStringExtra("feedPeerUrl") }
            val account = TalkRuntime.account(context, wallet, backend, requireNotNull(endpoint)).also { it.attach() }
            var token: String? = null
            try {
                assertTrue(runBlocking { account.start(TalkLink(backend, TalkRoom(SaitoWallet.create().publicKey, wallet.publicKey)), null, true, true) })
                val call = requireNotNull(TalkRuntime.call.value).also { token = it.token }
                val uri = Uri.Builder().scheme("redsquare-talk").authority("call").appendPath(call.token)
                    .appendQueryParameter("owner", wallet.publicKey).appendQueryParameter("backend", backend.id).build()
                activity.onActivity { it.startActivity(Intent(it, MainActivity::class.java).setData(uri)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
                ui.waitUntil(5000) { ui.onNodeWithContentDescription("Switch app: Saito Talk").isDisplayed() }
                fun home() {
                    ui.onNodeWithContentDescription("Switch app: Saito Talk").performClick()
                    ui.onNode(hasText("Red Square") and hasClickAction()).performClick()
                    ui.waitUntil(5000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                }
                home()
                val folder = File(context.cacheDir, "talk-active-evidence").apply { mkdirs() }
                ui.withPostViewports(activity, folder, "active-$width", widthDp = width) { label, theme ->
                    ui.onNodeWithText("Return to call").performClick()
                    ui.waitUntil(5000) { ui.onNodeWithContentDescription("Switch app: Saito Talk").isDisplayed() }
                    for (text in listOf("Mute", "Camera off", "Switch camera", "Audio output", "End call")) {
                        val node = ui.onNode(hasText(text) and hasClickAction()).assertIsDisplayed().fetchSemanticsNode()
                        assertTrue("$text remains a48dp action in $label", node.boundsInRoot.height / context.resources.displayMetrics.density >= 48f)
                    }
                    assertEquals(call.token, TalkRuntime.call.value?.token)
                    assertTrue(requireNotNull(TalkRuntime.call.value).phase.started)
                    captureNativeScreen(File(folder, "$label-active.png"))
                    home()
                }
                TalkRuntime.end(call.token)
                ui.waitUntil(5000) { TalkRuntime.call.value?.phase == CallPhase.ENDED }
            } finally { token?.let(TalkRuntime::end); account.detach() }
        }
    }
}
