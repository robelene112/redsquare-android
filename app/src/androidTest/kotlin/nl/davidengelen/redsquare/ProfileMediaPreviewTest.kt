package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.hamcrest.CoreMatchers.anyOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real image preparation, encrypted drafts, signing and profile pixels; only picker results are supplied. */
class ProfileMediaPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun selectedPictureAndBannerSurviveDeletedSourcesAndPublishWithTheRetainedBioAndIdentity() =
        selectAndPublish(removeAfterPublication = false)

    @Test fun removingSelectedPictureAndBannerSurvivesDraftRecoveryAndPublishesGeneratedDefaults() =
        selectAndPublish(removeAfterPublication = true)

    private fun selectAndPublish(removeAfterPublication: Boolean) {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("profilemeta.") || it.name.startsWith("profiledraft.")
        }?.forEach { check(it.delete()) }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val reference = instrumentation.context.assets.open("native-profile-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("snapshot") }
        val owner = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        assertEquals(owner, reference.get("field2").asString)
        val store = AndroidWalletStore(context)
        val original = store.loadOrCreate()
        if (original.publicKey != owner) store.replace(original, SaitoWallet.fromPrivateKey("0".repeat(63) + "1"))
        val owned = mutableListOf<Uri>()
        val indexed = AtomicReference<JsonObject?>()
        val publications = AtomicInteger()
        val prefix = if (removeAfterPublication) "removal-" else ""
        val bio = "My selected profile 🌱\nKeep this bio and my identity"
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val transaction = postingTransaction(frame)
            val signed = SaitoEnvelope.decode(transaction.envelope())
            assertEquals(owner, signed.author)
            assertEquals(listOf(owner), signed.recipients)
            assertEquals("Profile", signed.body.get("module").asString)
            assertEquals("update profile", signed.body.get("request").asString)
            assertEquals(1, signed.body.get("snapshot").asInt)
            assertTrue(signed.createdAt > 1700001000001L)
            val fields = signed.body.getAsJsonObject("data")
            assertEquals(bio, fields.get("description").asString)
            assertEquals(JsonParser.parseString("[\"https://archive.example.test\"]"), fields.get("archive"))
            assertEquals(JsonParser.parseString("{\"theme\":\"blue\"}"), fields.get("extension"))
            when (publications.incrementAndGet()) {
                1 -> {
                    assertEquals(setOf("description", "archive", "extension", "image", "banner"), fields.keySet())
                    assertPreparedPixels(fields.get("image").asString, intArrayOf(220, 30, 40), 1)
                    assertPreparedPixels(fields.get("banner").asString, intArrayOf(20, 60, 220), 3)
                }
                2 -> {
                    assertTrue(removeAfterPublication)
                    assertEquals(setOf("description", "archive", "extension"), fields.keySet())
                }
                else -> fail("Unexpected extra profile publication")
            }
            indexed.set(JsonObject().apply {
                addProperty("sig", transaction.id); addProperty("created_at", signed.createdAt)
                addProperty("field1", "Profile"); addProperty("field2", owner); addProperty("tx", transaction.envelope())
            })
        }, profileArchiveResponse = { criteria ->
            assertEquals(owner, criteria.get("field2").asString)
            listOfNotNull(indexed.get(), reference).filter {
                !criteria.has("sig") || criteria.get("sig") == it.get("sig")
            }.joinToString(prefix = "[", postfix = "]")
        }, archiveResponse = { "[]" })
        var primary: Throwable? = null
        try {
            peer.start().use { server ->
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                ActivityScenario.launch<MainActivity>(intent).use {
                    captureFailures {
                        openEditor()
                        ui.onNode(hasSetTextAction()).performTextReplacement(bio)
                        selectImage("Change profile picture", "red", "Profile picture", intArrayOf(220, 30, 40), owned)
                        selectImage("Change banner", "blue", "Profile banner", intArrayOf(20, 60, 220), owned)
                        assertTrue(peer.transactions.isEmpty())
                        capture("${prefix}selected-profile-media.png")
                        ui.onNodeWithText("Save draft").assertIsEnabled().performClick()
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
                        owned.toList().forEach { uri ->
                            assertEquals(1, context.contentResolver.delete(uri, null, null)); owned.remove(uri)
                        }
                    }
                }
                ActivityScenario.launch<MainActivity>(intent).use {
                    captureFailures {
                        openEditor()
                        ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                            SemanticsProperties.EditableText, AnnotatedString(bio))).assertIsEnabled()
                        assertVisiblePixels("Profile picture", intArrayOf(220, 30, 40))
                        assertVisiblePixels("Profile banner", intArrayOf(20, 60, 220))
                        assertTrue(peer.transactions.isEmpty())
                        ui.onNodeWithText("Save profile").assertIsEnabled().performClick()
                        val status = "Profile available on this node."
                        ui.waitUntil(25_000) { ui.onAllNodesWithText(status).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText(status).assertIsDisplayed()
                        assertEquals(1, peer.transactions.size)
                        exportFrame("${prefix}profile-media-frames.json", peer.transactions.single(), requireNotNull(indexed.get()))
                        capture("${prefix}selected-profile-media-available.png", status)
                        ui.onNodeWithText("Back").performClick()
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() &&
                            ui.onAllNodesWithText(context.getString(R.string.profile_details_loading)).fetchSemanticsNodes().isEmpty() }
                        ui.onNodeWithText(bio).assertIsDisplayed()
                        assertVisiblePixels("Profile picture", intArrayOf(220, 30, 40))
                        assertVisiblePixels("Profile banner", intArrayOf(20, 60, 220))
                        capture("${prefix}selected-profile-media-public.png")
                        assertEquals(owner, store.loadExisting().publicKey)
                        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                        assertEquals(1, peer.transactions.size); peer.assertHealthy()
                        if (removeAfterPublication) {
                            ui.onNodeWithText("Edit profile").performClick()
                            ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                            ui.onNodeWithText("Use generated profile picture").performScrollTo().assertIsEnabled().performClick()
                            ui.onNodeWithText("Remove banner").performScrollTo().assertIsEnabled().performClick()
                            assertGeneratedDefaults()
                            ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                                SemanticsProperties.EditableText, AnnotatedString(bio)))
                            assertEquals(1, peer.transactions.size)
                            capture("profile-removed-media-draft.png", "Save profile")
                            ui.onNodeWithText("Save draft").assertIsEnabled().performClick()
                            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
                            assertVisiblePixels("Profile picture", intArrayOf(220, 30, 40))
                            assertVisiblePixels("Profile banner", intArrayOf(20, 60, 220))
                            assertEquals(1, peer.transactions.size)
                        }
                    }
                }
                if (removeAfterPublication) ActivityScenario.launch<MainActivity>(intent).use {
                    captureFailures {
                        openEditor()
                        ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                            SemanticsProperties.EditableText, AnnotatedString(bio))).assertIsEnabled()
                        assertGeneratedDefaults()
                        assertEquals(1, peer.transactions.size)
                        ui.onNodeWithText("Save profile").assertIsEnabled().performClick()
                        val status = "Profile available on this node."
                        ui.waitUntil(25_000) { ui.onAllNodesWithText(status).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText(status).assertIsDisplayed()
                        assertEquals(2, peer.transactions.size)
                        exportFrame("profile-removed-media-frames.json", peer.transactions.last(), requireNotNull(indexed.get()))
                        capture("profile-removed-media-available.png", status)
                        ui.onNodeWithText("Back").performClick()
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() &&
                            ui.onAllNodesWithText(context.getString(R.string.profile_details_loading)).fetchSemanticsNodes().isEmpty() }
                        ui.onNodeWithText(bio).assertIsDisplayed()
                        assertGeneratedDefaults()
                        capture("profile-removed-media-public.png")
                        assertEquals(owner, store.loadExisting().publicKey)
                        assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                        assertEquals(2, peer.transactions.size); peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            var cleanupFailure: Throwable? = null
            try { cleanupSyntheticImages(context.contentResolver, owned) }
            catch (cleanup: Throwable) { cleanupFailure = cleanup }
            try {
                val active = store.loadExisting()
                check(active.publicKey == owner)
                if (active.publicKey != original.publicKey) store.replace(active, original)
            } catch (cleanup: Throwable) {
                if (cleanupFailure != null) cleanupFailure.addSuppressed(cleanup) else cleanupFailure = cleanup
            }
            cleanupFailure?.let { if (primary != null) primary.addSuppressed(it) else throw it }
        }
    }

    private fun openEditor() {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
        ui.onNodeWithText("Your profile").performClick()
        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() &&
            ui.onAllNodesWithText(context.getString(R.string.profile_details_loading)).fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("Edit profile").performClick()
        ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun selectImage(control: String, color: String, label: String, expected: IntArray, owned: MutableList<Uri>) {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.onNodeWithText(control).performScrollTo().assertIsDisplayed().assertIsEnabled()
        val source = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("images").get(color).asString }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "redsquare-profile-$color.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }))
        owned.add(uri)
        context.contentResolver.openOutputStream(uri)!!.use {
            it.write(android.util.Base64.decode(source.substringAfter(','), android.util.Base64.NO_WRAP))
        }
        Intents.init()
        try {
            Intents.intending(anyOf(hasAction(Intent.ACTION_OPEN_DOCUMENT), hasAction(Intent.ACTION_GET_CONTENT),
                hasAction("android.provider.action.PICK_IMAGES"), hasAction("androidx.activity.result.contract.action.PICK_IMAGES"),
                hasAction("com.google.android.gms.provider.action.PICK_IMAGES")))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            ui.onNodeWithText(control).performClick()
            assertVisiblePixels(label, expected)
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Save draft") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        } finally { Intents.release() }
    }

    private fun assertGeneratedDefaults() {
        ui.onNodeWithContentDescription(context.getString(R.string.profile_generated_picture))
            .performScrollTo().assertIsDisplayed()
        ui.onAllNodesWithContentDescription("Profile picture").assertCountEquals(0)
        ui.onAllNodesWithContentDescription("Profile banner").assertCountEquals(0)
    }

    private fun exportFrame(name: String, frame: ByteArray, indexed: JsonObject) {
        File(context.cacheDir, "profile-edit-evidence/$name").writeText(JsonObject().apply {
            addProperty("synthetic", true)
            addProperty("frame", Base64.getEncoder().encodeToString(frame))
            addProperty("envelope", indexed.get("tx").asString)
        }.toString())
    }

    private fun assertVisiblePixels(label: String, expected: IntArray) {
        ui.waitUntil(15_000) { ui.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription(label).performScrollTo()
        val bitmap = ui.onNodeWithContentDescription(label).assertIsDisplayed().captureToImage().asAndroidBitmap()
        assertColor(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2), expected)
    }

    private fun assertPreparedPixels(source: String, expected: IntArray, ratio: Int) {
        assertTrue(source.startsWith("data:image/jpeg;base64,"))
        val bytes = android.util.Base64.decode(source.substringAfter(','), android.util.Base64.NO_WRAP)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            assertEquals(bitmap.width, ratio * bitmap.height)
            assertColor(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2), expected)
        } finally { bitmap.recycle() }
    }

    private fun assertColor(pixel: Int, expected: IntArray) {
        listOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)).forEachIndexed { channel, value ->
            assertTrue("Profile pixel channel $channel: $value", kotlin.math.abs(value - expected[channel]) <= 5)
        }
    }

    private fun captureFailures(block: () -> Unit) {
        withFailureCapture({ capture("profile-media-failure.png") }, block)
    }

    private fun capture(name: String, expectedText: String? = null) {
        ui.waitForIdle()
        captureNativeScreen(File(context.cacheDir, "profile-edit-evidence/$name"), expectedText)
    }
}
