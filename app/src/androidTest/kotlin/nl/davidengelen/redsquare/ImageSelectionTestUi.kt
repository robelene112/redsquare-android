package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.hamcrest.CoreMatchers.anyOf
import org.junit.Assert.assertTrue

/** Owned public test PNG; the result stub ends before any wallet picker is opened. */
internal fun ComposeTestRule.attachSyntheticImage() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    check(context.packageName == "nl.davidengelen.redsquare.dev")
    check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    val source = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
        .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("images").get("red").asString }
    val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "redsquare-retained-red.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
    }))
    withImageFixtureCleanup({ cleanupSyntheticImages(context.contentResolver, listOf(uri)) }) {
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
            clickImagePicker()
            waitUntil(20_000) { onAllNodes(hasText("Remove image 1") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            assertSyntheticRedImage()
        } finally { Intents.release() }
    }
}

/** Dismiss the IME and require the attachment control in the actual viewport. */
internal fun ComposeTestRule.clickImagePicker() {
    androidx.test.espresso.Espresso.closeSoftKeyboard()
    waitUntil(10_000) {
        try { onNodeWithText("Add images").performScrollTo().isDisplayed() }
        catch (_: AssertionError) { false }
    }
    onNodeWithText("Add images").assertIsDisplayed().assertIsEnabled().performClick()
}

internal fun ComposeTestRule.assertSyntheticRedImage(postText: String? = null) {
    val image = hasContentDescription("Image 1 of 1").let { label ->
        if (postText == null) label
        else label and hasAnyAncestor(hasAnyChild(hasText(postText) and hasClickAction()))
    }
    waitUntil(15_000) { onAllNodes(image).fetchSemanticsNodes().isNotEmpty() }
    if (postText != null) onNode(hasScrollAction()).performScrollToNode(image)
    else onNode(image).performScrollTo()
    val bitmap = onNode(image).assertIsDisplayed().captureToImage().asAndroidBitmap()
    val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    assertTrue(kotlin.math.abs(Color.red(pixel) - 220) <= 5)
    assertTrue(kotlin.math.abs(Color.green(pixel) - 30) <= 5)
    assertTrue(kotlin.math.abs(Color.blue(pixel) - 40) <= 5)
}

/** Keep assertion failures primary while attempting every owned fixture cleanup. */
internal inline fun <T> withImageFixtureCleanup(cleanup: () -> Unit, block: () -> T): T {
    var failure: Throwable? = null
    try { return block() }
    catch (problem: Throwable) { failure = problem; throw problem }
    finally {
        try { cleanup() }
        catch (problem: Throwable) {
            val primary = failure
            if (primary == null) throw problem else primary.addSuppressed(problem)
        }
    }
}

internal fun cleanupSyntheticImages(resolver: android.content.ContentResolver, uris: List<android.net.Uri>) {
    var failure: Throwable? = null
    uris.forEach { uri ->
        try { check(resolver.delete(uri, null, null) == 1) { "Owned synthetic image source was not deleted" } }
        catch (problem: Throwable) {
            val previous = failure
            if (previous == null) failure = problem else previous.addSuppressed(problem)
        }
    }
    failure?.let { throw it }
}
