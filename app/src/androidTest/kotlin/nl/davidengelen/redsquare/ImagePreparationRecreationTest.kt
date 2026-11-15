package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.hamcrest.CoreMatchers.anyOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Selected provider read is held across actual Activity replacement, never a second selection. */
class ImagePreparationRecreationTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun selectedPostImageContinuesOnceAcrossRecreationWithoutPublishing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("postdraft.") || it.name.startsWith("editrecovery.") }
            ?.forEach { check(it.delete()) }
        val fixture = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val bytes = android.util.Base64.decode(fixture.getAsJsonObject("images").get("red").asString.substringAfter(','), android.util.Base64.NO_WRAP)
        val uri = Uri.parse("content://nl.davidengelen.redsquare.dev.test.chat-photo-faults/stalled.png")
        fun provider(action: String, extras: Bundle? = null) = requireNotNull(context.contentResolver.call(uri, action, null, extras))
        provider("reset")
        Intents.init()
        try {
            val pick = anyOf(hasAction(Intent.ACTION_OPEN_DOCUMENT), hasAction(Intent.ACTION_GET_CONTENT),
                hasAction("android.provider.action.PICK_IMAGES"), hasAction("androidx.activity.result.contract.action.PICK_IMAGES"),
                hasAction("com.google.android.gms.provider.action.PICK_IMAGES"))
            Intents.intending(pick).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK,
                Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            val peer = ControlledFeedPeer("[${fixture.getAsJsonObject("ordinary")}]")
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    ui.waitUntil(20_000) { peer.requests.get() > 0 && ui.refreshableContentReady() }
                    ui.onNode(hasText("New post") and hasClickAction()).performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement("Selected image retained 121")
                    ui.clickImagePicker()
                    ui.waitUntil(10_000) { provider("status").getBoolean("opened") }
                    var first = 0
                    scenario.onActivity { first = System.identityHashCode(it) }
                    scenario.recreate()
                    scenario.onActivity { assertNotEquals(first, System.identityHashCode(it)) }
                    assertTrue("The original selected provider read survives Activity replacement",
                        provider("complete", Bundle().apply { putByteArray("png", bytes) }).getBoolean("delivered"))
                    ui.waitUntil(15_000) { ui.onAllNodesWithText("Remove image 1").fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).assert(hasText("Selected image retained 121"))
                    ui.onAllNodesWithText("Remove image 2").assertCountEquals(0)
                    ui.assertSyntheticRedImage()
                    assertEquals("Exactly one provider preparation", 1, provider("status").getInt("openCalls"))
                    Intents.intended(pick, androidx.test.espresso.intent.VerificationModes.times(1))
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                }
            }
        } finally {
            withImageFixtureCleanup({ provider("reset") }) { Intents.release() }
        }
    }
}
