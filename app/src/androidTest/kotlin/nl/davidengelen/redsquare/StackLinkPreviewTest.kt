package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.espresso.intent.Intents
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Signed links cross the real module/peer boundary; Android browser intents stay intercepted. */
class StackLinkPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aLinkedOlderArticleOpensNativelySurvivesRecreationAndReturnsToItsPostWithoutChangingTheSavedNode() = withInterceptedPostLinks {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val row = instrumentation.context.assets.open("native-stack-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("reader")
        }
        val signature = row.get("sig").asString
        val author = row.get("field2").asString
        val url = "https://saito.io/stack/$author/$signature"
        val post = linkedFeedRow("Read this older Stack article.", url,
            """{"link_properties":{"og:title":"Read the linked article","og:url":"https://example.invalid/wrong-target"}}""")
        ControlledFeedPeer("[]", archiveResponse = { criteria ->
            // This article is deliberately absent from discovery. Only the exact signed target is served.
            if (criteria.get("field1").asString == "Stack" && criteria.get("sig")?.asString == signature)
                "[$row]" else "[]"
        }).start().use { source ->
            withAppFrameFixture("stack-link", "stack-link-evidence", feedRows = "[$post]") { activity ->
                activity.onActivity { it.intent.putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${source.port}/wsopen") }
                ui.waitUntil(15_000) { ui.onNodeWithText("Read this older Stack article.").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Read the linked article") and hasClickAction())
                ui.onNode(hasText("Read the linked article") and hasClickAction()).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Switch app: Stack").isDisplayed() }
                ui.waitUntil(15_000) { ui.onNodeWithText("Open networks, real writing").isDisplayed() }
                ui.onNodeWithTag("stack-live-content").performScrollToNode(hasText("Original Markdown.", substring = true))
                ui.onNodeWithText("Original Markdown.", substring = true).assertIsDisplayed()
                assertTrue(Intents.getIntents().none { it.action == Intent.ACTION_VIEW })
                captureNativeScreen(File(instrumentation.targetContext.cacheDir, "stack-link-evidence/linked-article.png"))
                activity.recreate()
                ui.waitUntil(15_000) { ui.onNodeWithText("Original Markdown.", substring = true).isDisplayed() }
                UiDevice.getInstance(instrumentation).pressBack()
                ui.waitUntil(5_000) { ui.onNodeWithText("Read this older Stack article.").isDisplayed() }
                ui.onNodeWithContentDescription("Switch app: Red Square").assertExists()
                assertEquals(nl.davidengelen.redsquare.saito.SaitoBackend.OWNER,
                    nl.davidengelen.redsquare.saito.AndroidBackendStore(instrumentation.targetContext).load())
                assertTrue(Intents.getIntents().none { it.action == Intent.ACTION_VIEW })
                captureNativeScreen(File(instrumentation.targetContext.cacheDir, "stack-link-evidence/returned-post.png"))
            }
        }
    }
}
