package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProfileEditPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    @Test fun viewingAProfileShowsItsVerifiedBioAndGeneratedSaitoAvatar() {
        val feed = instrumentation.context.assets.open("native-feed-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val profiles = instrumentation.context.assets.open("native-profile-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val author = profiles.getAsJsonObject("snapshot").get("field2").asString
        assertEquals(author, feed.get("field2").asString)
        val peer = ControlledFeedPeer("[$feed]", profileArchiveResponse = { criteria ->
            assertEquals(author, criteria.get("field2").asString)
            assertEquals(100, criteria.get("limit").asInt)
            JsonArray().apply {
                add(profiles.getAsJsonObject("newerLegacy")); add(profiles.getAsJsonObject("snapshot"))
            }.toString()
        }, archiveResponse = { criteria ->
            assertEquals("RedSquare", criteria.get("field1").asString)
            assertEquals(20, criteria.get("limit").asInt)
            assertEquals(1, criteria.get("flagged_ne").asInt)
            if (criteria.has("field2")) assertEquals(author, criteria.get("field2").asString)
            else assertEquals("", criteria.get("field4").asString)
            "[$feed]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    ui.waitUntil(20_000) { peer.requests.get() >= 2 &&
                        ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    val post = hasText("Native Red Square reference post")
                    ui.onNode(hasScrollAction()).performScrollToNode(post)
                    val identity = hasText(author) and hasAnyAncestor(hasAnyChild(post))
                    ui.onNode(identity).assertIsDisplayed().performClick()
                    ui.waitUntil(20_000) { ui.onAllNodesWithText("Signed bio 🌱\nSecond line").fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Signed bio 🌱\nSecond line").assertIsDisplayed()
                    ui.onNodeWithContentDescription("Generated profile picture").assertIsDisplayed()
                    ui.onAllNodesWithText("Later legacy bio").assertCountEquals(0)
                    capture("verified-profile.png")
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } catch (failure: Throwable) {
                    try { capture("first-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                }
            }
        }
    }

    @Test fun profileHistoryRemainsReachableWithAFullMultilineBio() {
        val feed = instrumentation.context.assets.open("native-feed-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val row = instrumentation.context.assets.open("native-profile-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("longBio") }
        val author = row.get("field2").asString
        val bio = (1..40).joinToString("\n") { "Public biography line $it" }
        val peer = ControlledFeedPeer("[$feed]", profileArchiveResponse = { "[$row]" }, archiveResponse = { criteria ->
            assertEquals("RedSquare", criteria.get("field1").asString)
            if (criteria.has("field2")) assertEquals(author, criteria.get("field2").asString)
            "[$feed]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    val post = hasText("Native Red Square reference post")
                    ui.onNode(hasScrollAction()).performScrollToNode(post)
                    ui.onNode(hasText(author) and hasAnyAncestor(hasAnyChild(post))).performClick()
                    val device = UiDevice.getInstance(instrumentation)
                    assertTrue("Full signed bio was not exposed", device.wait(Until.hasObject(By.text(bio)), 20_000))
                    capture("long-bio-before-scroll.png")
                    val list = UiScrollable(UiSelector().scrollable(true)).setMaxSearchSwipes(10)
                    assertTrue("Profile tabs are unreachable", list.scrollIntoView(UiSelector().text("Posts")))
                    ui.onNodeWithText("Posts").assertIsDisplayed()
                    ui.onNodeWithText("Replies").assertIsDisplayed()
                    ui.onNodeWithText("Likes").assertIsDisplayed()
                    assertTrue("Profile post is unreachable", list.scrollIntoView(UiSelector().text("Native Red Square reference post")))
                    ui.onNode(post).assertIsDisplayed()
                    capture("long-bio-reachable-history.png")
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } catch (failure: Throwable) {
                    try { capture("long-bio-first-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                }
            }
        }
    }

    private fun capture(name: String) {
        val file = File(context.cacheDir, "profile-edit-evidence/$name").apply { parentFile?.mkdirs() }
        val device = UiDevice.getInstance(instrumentation)
        check(device.takeScreenshot(file))
        device.dumpWindowHierarchy(File(file.parentFile, file.nameWithoutExtension + ".xml"))
    }
}
