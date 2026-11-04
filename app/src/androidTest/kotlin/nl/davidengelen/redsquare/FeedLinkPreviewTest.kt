package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64

/** Signed post target and node-provided Open Graph hints at the existing native/peer boundary. */
class FeedLinkPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aRealLinkedPostShowsItsAvailablePreviewAndOpensOnlyItsOriginalTarget() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only.
        val link = "https://saito.io/stack/${author.publicKey}/${"a".repeat(128)}"
        val transaction = SaitoTransaction.create(author, JsonObject().apply {
            addProperty("module", "RedSquare"); addProperty("request", "create tweet")
            add("data", JsonObject().apply {
                addProperty("text", "A linked essay worth reading."); addProperty("link", link)
            })
        }, 1_700_000_000_000)
        val row = postingArchiveRow(transaction)
        val envelope = JsonParser.parseString(row.get("tx").asString).asJsonObject
        envelope.addProperty("opt", Base64.getEncoder().encodeToString("""
            {"link_properties":{"og:title":"Independent writing, together",
            "og:description":"A controlled article preview supplied by this node."}}
        """.trimIndent().toByteArray(Charsets.UTF_8)))
        row.addProperty("tx", envelope.toString())
        var primary: Throwable? = null
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_VIEW)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
            withAppFrameFixture("article-card", "feed-redesign-evidence", "[$row]") {
                ui.waitUntil(15_000) { ui.onNodeWithText("A linked essay worth reading.").isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                val article = hasText("Independent writing, together") and hasClickAction()
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(article)
                ui.onNode(article).performScrollTo().assertIsDisplayed()
                ui.onNodeWithText("A controlled article preview supplied by this node.", useUnmergedTree = true).assertExists()
                ui.onNodeWithText("Design sample · 6 min read").assertDoesNotExist()
                captureNativeScreen(File(context.cacheDir, "feed-redesign-evidence/available-stack-preview.png"))
                ui.onNode(article).performClick()
                ui.waitUntil(5_000) { Intents.getIntents().any { it.action == Intent.ACTION_VIEW } }
                val opened = Intents.getIntents().filter { it.action == Intent.ACTION_VIEW }
                assertEquals(1, opened.size)
                assertEquals(link, opened.single().dataString)
            }
        } catch (error: Throwable) { primary = error; throw error }
        finally { cleanupAfterTest(primary, { Intents.release() }) }
    }
}
