package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.feed.ProfileDraftStore
import nl.davidengelen.redsquare.saito.*
import org.hamcrest.CoreMatchers.anyOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProfileImagePreparationRecreationTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun selectedAvatarContinuesAcrossRecreationWithoutLosingNewBioText() = exercise(false)
    @Test fun selectedBannerContinuesAcrossRecreationWithoutLosingNewBioText() = exercise(true)

    private fun exercise(banner: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val backends = AndroidBackendStore(context)
        val oldBackend = backends.load()
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val retained = ChatFixtureState(context, listOf("profiledraft.${SaitoBackend.OWNER.id}.${wallet.publicKey}"), wallet.publicKey)
        val uri = Uri.parse("content://nl.davidengelen.redsquare.dev.test.chat-photo-faults/stalled.png")
        fun provider(action: String, extras: Bundle? = null) = requireNotNull(context.contentResolver.call(uri, action, null, extras))
        val source = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("images").get("red").asString }
        val label = if (banner) "Profile banner" else "Profile picture"
        val field = if (banner) "banner" else "image"
        val control = if (banner) "Change banner" else "Change profile picture"
        val text = "Bio edited while selected $field preparation is running"
        var primary: Throwable? = null
        var intentsInitialized = false
        try {
            retained.prepare()
            backends.save(SaitoBackend.OWNER)
            provider("reset")
            Intents.init()
            intentsInitialized = true
            val pick = anyOf(hasAction(Intent.ACTION_OPEN_DOCUMENT), hasAction(Intent.ACTION_GET_CONTENT),
                hasAction("android.provider.action.PICK_IMAGES"), hasAction("androidx.activity.result.contract.action.PICK_IMAGES"),
                hasAction("com.google.android.gms.provider.action.PICK_IMAGES"))
            Intents.intending(pick).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)))
            val peer = ControlledFeedPeer("[]", profileArchiveResponse = { "[]" })
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    ui.waitUntil(20_000) { ui.refreshableContentReady() }
                    ui.onNodeWithText("Your profile").performClick()
                    ui.waitUntil(20_000) { ui.onNodeWithText("Edit profile").isDisplayed() &&
                        ui.onAllNodesWithText(context.getString(R.string.profile_details_loading)).fetchSemanticsNodes().isEmpty() }
                    ui.onNodeWithText("Edit profile").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement("Earlier bio retained 121")
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    ui.onNodeWithText(control).performScrollTo().assertIsEnabled().performClick()
                    ui.waitUntil(10_000) { provider("status").getBoolean("opened") }
                    ui.onNode(hasSetTextAction()).performScrollTo().assertIsEnabled().performTextReplacement(text)
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    var first = 0
                    scenario.onActivity { first = System.identityHashCode(it) }
                    scenario.recreate()
                    scenario.onActivity { assertNotEquals(first, System.identityHashCode(it)) }
                    assertTrue("The selected provider read survives replacement", provider("complete", Bundle().apply {
                        putByteArray("png", android.util.Base64.decode(source.substringAfter(','), android.util.Base64.NO_WRAP))
                    }).getBoolean("delivered"))
                    val store = ProfileDraftStore(context, wallet.publicKey, SaitoBackend.OWNER)
                    ui.waitUntil(15_000) { store.load().changes.get(field)?.asString?.startsWith("data:image/jpeg;base64,") == true }
                    ui.onNode(hasSetTextAction()).assert(hasText(text))
                    val saved = store.load()
                    assertEquals(text, saved.changes.get("description").asString)
                    assertNull(saved.transaction)
                    val bytes = android.util.Base64.decode(saved.changes.get(field).asString.substringAfter(','), android.util.Base64.NO_WRAP)
                    val bitmap = requireNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    try { assertEquals(if(banner) 3 else 1, bitmap.width / bitmap.height) } finally { bitmap.recycle() }
                    ui.onNodeWithContentDescription(label).performScrollTo()
                    ui.waitUntil(10_000) {
                        val visible = ui.onNodeWithContentDescription(label).assertIsDisplayed().captureToImage().asAndroidBitmap()
                        val pixel = visible.getPixel(visible.width/2, visible.height/2)
                        kotlin.math.abs(android.graphics.Color.red(pixel)-220)<=5 &&
                            kotlin.math.abs(android.graphics.Color.green(pixel)-30)<=5 && kotlin.math.abs(android.graphics.Color.blue(pixel)-40)<=5
                    }
                    val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
                    val directory = File(context.cacheDir, "profile-edit-evidence").apply { mkdirs() }
                    check(device.takeScreenshot(File(directory,"selected-profile-$field.png")))
                    device.dumpWindowHierarchy(File(directory,"selected-profile-$field.xml"))
                    assertEquals(1, provider("status").getInt("openCalls"))
                    Intents.intended(pick, androidx.test.espresso.intent.VerificationModes.times(1))
                    check(wallet.exportJson() == AndroidWalletStore(context).loadExisting().exportJson()) { "Selected image preparation must preserve the original wallet" }
                    assertEquals(SaitoBackend.OWNER, backends.load())
                    assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary,
                { if(intentsInitialized) Intents.release() },
                { provider("reset") },
                { retained.restoreFiles() },
                { retained.restorePreferences() },
                { backends.save(oldBackend) })
        }
    }
}
