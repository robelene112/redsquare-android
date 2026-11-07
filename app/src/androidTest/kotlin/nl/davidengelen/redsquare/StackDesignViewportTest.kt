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
            val target = hasText(text, substring = text in listOf("visual finish", "A newer Markdown revision."))
            // Persistent forms already expose their descendants; virtualised reading
            // lists first need to reveal the requested item through their public scroll action.
            if (onAllNodes(target).fetchSemanticsNodes().isEmpty())
                onNodeWithTag("stack-content").performScrollToNode(target)
            onNode(target).performScrollTo().assertIsDisplayed()
        }
        fun click(text: String) { reveal(text); onNodeWithText(text).performClick() }
        fun nativeTarget(label: String) {
            val minimum = 48 * instrumentation.targetContext.resources.displayMetrics.density
            fun text(node: android.view.accessibility.AccessibilityNodeInfo): String = buildString {
                node.text?.let { append(it) }
                for (index in 0 until node.childCount) node.getChild(index)?.let { append(text(it)) }
            }
            waitUntil(5_000) {
                val targets = nativeWindowNodes().filter { it.refresh() && it.isVisibleToUser && it.isEnabled &&
                    it.isClickable && label in text(it) }
                targets.size == 1 && android.graphics.Rect().apply { targets.single().getBoundsInScreen(this) }
                    .let { it.width() >= minimum && it.height() >= minimum }
            }
        }
        fun capture(label: String) { waitForIdle(); captureNativeScreen(File(folder, "$label.png")) }
        withPostViewports(activity, folder, "stack", scales, width) { label, theme ->
            switch("Red Square", "Stack")
            // The previous matrix cell deliberately left its real draft in Write.
            // Return through its local Back without discarding that retained work.
            if (!onNode(hasText("Discover") and hasClickAction()).isDisplayed()) {
                onNode(hasText("Back") and hasClickAction()).assertIsDisplayed().performClick()
                waitUntil(5_000) { onNode(hasText("Discover") and hasClickAction()).isDisplayed() }
            }
            onNode(hasText("Discover") and hasClickAction()).performClick()
            onNodeWithContentDescription("Write an article").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            reveal("Good ideas deserve\na little more room.")
            capture("$label-discover")
            click("A Node in a Browser Tab")
            reveal("Notes from the edges of an open web.")
            capture("$label-reader-intro")
            onNodeWithTag("stack-content").performScrollToIndex(3)
            capture("$label-reader-body")
            if (width == 412 && theme == "Dark") {
                for (action in listOf("Discuss on Red Square", "Share article")) {
                    click(action)
                    onNodeWithText("This is a sample article. Discussion links and sharing will be connected when real Stack articles are available. Nothing is sent from this preview.").assertIsDisplayed()
                    nativeTarget("Done")
                    capture("$label-${if (action.startsWith("Discuss")) "discussion" else "sharing"}-preview")
                    onNode(hasText("Done") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
                    waitUntil(5_000) { onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
                }
            }
            onNode(hasText("Back") and hasClickAction()).performClick()
            onNodeWithContentDescription("Write an article").performClick()
            click("Markdown")
            val source = "## A retained idea\n\nA **careful** question for $label."
            onNode(hasSetTextAction() and hasText("Markdown source")).performScrollTo().performTextReplacement(source)
            onNode(hasSetTextAction() and hasText("Markdown source")).performClick()
            fun keyboardVisible(): Boolean {
                var visible = false
                activity.onActivity {
                    val insets = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                    visible = insets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true ||
                        (insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0) > 0
                }
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
            nativeTarget("Stack design preview")
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
                onNode(hasSetTextAction() and hasContentDescription("Article body")).performScrollTo().performTextInput(" older visual buffer")
                click("Markdown")
                val canonical = "## Canonical source\n\nA newer Markdown revision."
                onNode(hasSetTextAction() and hasText("Markdown source")).performScrollTo().performTextReplacement(canonical)
                onNodeWithText("Review", substring = false).performClick()
                reveal("A newer Markdown revision.")
                onNode(hasText("Back") and hasClickAction()).performClick()
                reveal("Markdown source")
                onNode(hasSetTextAction() and hasText("Markdown source"))
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(canonical)))
                capture("$label-canonical-source-return")
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
                capture("$label-publication-success")
                click("View article preview")
                capture("$label-published-reader")
                onNode(hasText("Back") and hasClickAction()).performClick()
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
                click("Drafts")
                capture("$label-drafts")
                onNode(hasText("Back") and hasClickAction()).performClick()
                click("Subscriptions and access")
                capture("$label-subscriptions")
                click("Subscriber access")
                reveal("Access name")
                capture("$label-author-access")
                click("Save access preview")
                onNodeWithText("Preview access settings kept. Nothing was sent to Saito Store.").assertIsDisplayed()
            }
            if (width == 320 && theme == "Dark") {
                val table = "A | B\n- | -\nx | y"
                click("Markdown")
                onNode(hasSetTextAction() and hasText("Markdown source")).performScrollTo().performTextReplacement(table)
                // A semantic replacement can queue an IME show after returning.
                // Observe that real window before Back, as in the initial edit above.
                onNode(hasSetTextAction() and hasText("Markdown source")).performClick()
                waitUntil(10_000) { keyboardVisible() }
                UiDevice.getInstance(instrumentation).pressBack()
                waitUntil(10_000) { !keyboardVisible() }
                reveal("Visual")
                onNode(hasText("Visual") and hasClickAction()).assertIsDisplayed().assertIsNotEnabled()
                capture("$label-protected-markdown")
            }
            switch("Stack", "Red Square")
            onNode(hasText("Home") and isSelected()).assertIsDisplayed()
        }
    }
}
