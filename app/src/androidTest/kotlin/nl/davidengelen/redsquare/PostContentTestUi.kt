package nl.davidengelen.redsquare

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals

/** Shared actual-pixel and attributed-original contract; callers retain their own route/target checks. */
internal fun ComposeTestRule.assertPostMediaContent(reference: PostContentFixture, quoteScrollDp: Float = 0f,
    capture: (String) -> Unit) {
    for ((index, expected) in listOf(0xffdc1e28.toInt(), 0xff143cdc.toInt()).withIndex()) {
        val image = hasContentDescription("Image ${index + 1} of 2")
        waitUntil(15_000) { onAllNodes(image).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasScrollAction()).performScrollToNode(image)
        val bitmap = onNode(image).assertIsDisplayed().captureToImage().asAndroidBitmap()
        try { assertEquals("Signed image order and actual decoded color must remain intact", expected,
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) }
        finally { bitmap.recycle() }
        capture("image-${index + 1}")
    }
    val quoted = hasAnyAncestor(hasContentDescription("Quoted original"))
    val originalText = hasText("Quoted viewport original") and quoted
    onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(originalText)
    if (quoteScrollDp != 0f) {
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
            check(scroll(0f, quoteScrollDp * density))
        }
        waitForIdle()
    }
    onNode(originalText, useUnmergedTree = true).assertIsDisplayed()
    onNode(hasText(reference.originalName) and quoted, useUnmergedTree = true).assertIsDisplayed()
    capture("verified-quote")
}
