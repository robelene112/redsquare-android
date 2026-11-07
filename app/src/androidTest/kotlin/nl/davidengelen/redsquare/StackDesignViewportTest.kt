package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.Rule
import org.junit.Test

class StackDesignViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun compactDiscoveryReadingAndCanonicalDraft() = ui.verifyStackDesign(320, listOf(1f, 2f))
}

class StackDesignPixelViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun pixelWritingPublicationAndAccessPreview() = ui.verifyStackDesign(412, listOf(1.3f))
}

private fun ComposeTestRule.verifyStackDesign(width: Int, scales: List<Float>) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val folderName = "stack-design-$width-evidence"
    val folder = File(instrumentation.targetContext.cacheDir, folderName).apply { mkdirs() }
    withAppFrameFixture("stack-design-$width", folderName) { activity ->
        waitUntil(15_000) { onNodeWithText("Later").isDisplayed() }
        onNodeWithText("Later").performClick()
        fun switch(current: String, next: String) {
            revealAppNavigation()
            onNodeWithContentDescription("Switch app: $current").assertIsDisplayed().performClick()
            onNode(hasText(next) and hasClickAction() and hasAnyAncestor(isPopup() or isDialog())).assertIsDisplayed().performClick()
            waitUntil(5_000) { onNodeWithContentDescription("Switch app: $next").isDisplayed() }
        }
        fun reveal(text: String) {
            onNodeWithTag("stack-content").performScrollToNode(hasText(text, substring = text == "visual finish"))
            onNodeWithText(text, substring = text == "visual finish").assertIsDisplayed()
        }
        fun click(text: String) { reveal(text); onNodeWithText(text).performClick() }
        fun capture(label: String) { waitForIdle(); captureNativeScreen(File(folder, "$label.png")) }
        withPostViewports(activity, folder, "stack", scales, width) { label, theme ->
            switch("Red Square", "Stack")
            onNode(hasText("Discover") and hasClickAction()).performClick()
            onNodeWithContentDescription("Write an article").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            reveal("Good ideas deserve\na little more room.")
            capture("$label-discover")
            click("A Node in a Browser Tab")
            reveal("Notes from the edges of an open web.")
            capture("$label-reader-intro")
            onNodeWithTag("stack-content").performScrollToIndex(3)
            capture("$label-reader-body")
            onNodeWithContentDescription("Back").performClick()
            onNodeWithContentDescription("Write an article").performClick()
            click("Markdown")
            val source = "## A retained idea\n\nA **careful** question for $label."
            onNode(hasSetTextAction() and hasText("Markdown source")).performScrollTo().performTextReplacement(source)
            onNode(hasSetTextAction() and hasText("Markdown source")).performClick()
            fun keyboardVisible(): Boolean {
                var visible = false
                activity.onActivity { visible = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
                return visible
            }
            waitUntil(10_000) { keyboardVisible() }
            capture("$label-markdown-keyboard")
            UiDevice.getInstance(instrumentation).pressBack()
            waitUntil(10_000) { !keyboardVisible() }
            reveal("Markdown source")
            capture("$label-markdown")
            switch("Stack", "Red Square")
            switch("Red Square", "Stack")
            reveal("Markdown source")
            onNode(hasSetTextAction() and hasText("Markdown source"))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(source)))
            click("Visual")
            capture("$label-visual")
            click("Markdown")
            onNode(hasSetTextAction() and hasText("Markdown source"))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(source)))
            if (width == 412 && theme == "Dark") {
                activity.recreate()
                waitUntil(15_000) { onNodeWithText("Stack design preview").isDisplayed() }
                reveal("Markdown source")
                onNode(hasSetTextAction() and hasText("Markdown source"))
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(source)))
                click("Visual")
                onNode(hasSetTextAction() and hasContentDescription("Article body")).performScrollTo().performTextInput(" visual finish")
                onNodeWithText("Review", substring = false).performClick()
                reveal("visual finish")
                reveal("Who can read this?")
                capture("$label-publication")
                click("Restricted")
                reveal("Allowed usernames or public keys")
                click("Subscribers")
                reveal("Subscription price (SAITO)")
                click("Publish preview")
                onNode(hasText("Design preview only. No article, NFT or Red Square post is sent to the network.") and hasAnyAncestor(isDialog())).assertIsDisplayed()
                onNodeWithText("Show preview").performClick()
                waitUntil(5_000) { onNodeWithText("Publication preview").isDisplayed() }
                click("Back to My articles")
                onNode(hasText("Discover") and hasClickAction()).performClick()
                click("The quiet value of an open network")
                click("View access options")
                capture("$label-access")
                click("Review access")
                capture("$label-access-review")
                click("Confirm preview access")
                onNodeWithText("Show preview").performClick()
                waitUntil(5_000) { onNodeWithText("Article preview").isDisplayed() }
                onNode(hasText("My articles") and hasClickAction()).performClick()
                click("Subscriptions and access")
                capture("$label-subscriptions")
                click("Subscriber access")
                reveal("Access name")
                capture("$label-author-access")
                click("Save access preview")
                onNodeWithText("Preview access settings kept. Nothing was sent to Saito Store.").assertIsDisplayed()
            }
            if (width == 320 && theme == "Dark") {
                val table = "A | B\n--- | ---\nx | y"
                click("Markdown")
                onNode(hasSetTextAction() and hasText("Markdown source")).performScrollTo().performTextReplacement(table)
                onNode(hasText("Visual") and hasClickAction()).assertIsNotEnabled()
                capture("$label-protected-markdown")
            }
            switch("Stack", "Red Square")
            onNode(hasText("Home") and isSelected()).assertIsDisplayed()
        }
    }
}
