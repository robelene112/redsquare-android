package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
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
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("profilemeta.") || it.name.startsWith("profiledraft.")
        }?.forEach {
            check(it.delete()) { "Could not reset emulator profile fixtures" }
        }
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
                        ui.onAllNodesWithText(context.getString(R.string.feed_loading)).fetchSemanticsNodes().isEmpty() &&
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
                    ui.waitUntil(20_000) { peer.requests.get() >= 2 &&
                        ui.onAllNodesWithText(context.getString(R.string.feed_loading)).fetchSemanticsNodes().isEmpty() &&
                        ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    val post = hasText("Native Red Square reference post")
                    ui.onNode(hasScrollAction()).performScrollToNode(post)
                    ui.onNode(hasText(author) and hasAnyAncestor(hasAnyChild(post))).performClick()
                    ui.waitUntil(20_000) { ui.onAllNodesWithText(bio).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText(bio).assertExists()
                    capture("long-bio-before-scroll.png")
                    scrollUntilDisplayed("Posts")
                    ui.onNodeWithText("Posts").assertIsDisplayed()
                    ui.onNodeWithText("Replies").assertIsDisplayed()
                    ui.onNodeWithText("Likes").assertIsDisplayed()
                    scrollUntilDisplayed("Native Red Square reference post")
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

    @Test fun cachedProfileBioSurvivesAFreshActivityWhenSelectedBackendIsUnavailable() {
        val feed = instrumentation.context.assets.open("native-feed-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val row = instrumentation.context.assets.open("native-profile-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("snapshot") }
        val author = row.get("field2").asString
        val bio = "Signed bio 🌱\nSecond line"
        val peer = ControlledFeedPeer("[$feed]", profileArchiveResponse = { criteria ->
            assertEquals(author, criteria.get("field2").asString)
            "[$row]"
        }, archiveResponse = { criteria ->
            assertEquals("RedSquare", criteria.get("field1").asString)
            if (criteria.has("field2")) assertEquals(author, criteria.get("field2").asString)
            "[$feed]"
        })
        val server = peer.start()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
        server.use {
            ActivityScenario.launch<MainActivity>(intent).use {
                try {
                    ui.waitUntil(20_000) { peer.requests.get() >= 2 &&
                        ui.onAllNodesWithText(context.getString(R.string.feed_loading)).fetchSemanticsNodes().isEmpty() &&
                        ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    openReferenceAuthor(author)
                    ui.waitUntil(20_000) { ui.onAllNodesWithText(bio).fetchSemanticsNodes().isNotEmpty() &&
                        ui.onAllNodesWithText(context.getString(R.string.profile_details_loading)).fetchSemanticsNodes().isEmpty() &&
                        ui.onAllNodesWithText(context.getString(R.string.profile_loading)).fetchSemanticsNodes().isEmpty() }
                    ui.onNodeWithText(bio).assertIsDisplayed()
                    ui.onNodeWithText("Refresh").assertIsEnabled()
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    peer.assertHealthy()
                    assertTrue(peer.transactions.isEmpty())
                } catch (failure: Throwable) {
                    try { capture("cache-online-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                }
            }
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            try {
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                openReferenceAuthor(author)
                ui.waitUntil(25_000) {
                    ui.onAllNodesWithText(context.getString(R.string.profile_details_error)).fetchSemanticsNodes().isNotEmpty()
                }
                ui.onNodeWithText(bio).assertIsDisplayed()
                ui.onNodeWithContentDescription("Generated profile picture").assertIsDisplayed()
                assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                capture("cached-profile-offline.png")
            } catch (failure: Throwable) {
                try { capture("cache-offline-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                throw failure
            }
        }
    }

    @Test fun aSavedOwnBioDraftReturnsAfterAFreshActivityWithoutPublishing() {
        val bio = "A private draft 🌱\nNot published yet"
        val peer = ControlledFeedPeer("[]", profileArchiveResponse = { "[]" }, archiveResponse = { "[]" })
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            ActivityScenario.launch<MainActivity>(intent).use {
                try {
                    ui.waitUntil(20_000) { peer.requests.get() >= 2 &&
                        ui.onAllNodesWithText(context.getString(R.string.feed_loading)).fetchSemanticsNodes().isEmpty() &&
                        ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    ui.onNodeWithText("Your profile").assertIsDisplayed().performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Edit profile").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement(bio)
                    ui.onNodeWithText("Save draft").performClick()
                    ui.waitUntil(10_000) {
                        ui.onAllNodes(hasText("Edit profile") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onAllNodesWithText(bio).assertCountEquals(0)
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } catch (failure: Throwable) {
                    try { capture("bio-draft-first-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                }
            }
            ActivityScenario.launch<MainActivity>(intent).use {
                try {
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Your profile").performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Edit profile").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).assert(
                        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(bio)))
                    ui.onNodeWithText("Save draft").assertIsEnabled()
                    capture("restored-bio-draft.png")
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } catch (failure: Throwable) {
                    try { capture("bio-draft-restoration-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                }
            }
        }
    }

    private fun openReferenceAuthor(author: String) {
        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
        val post = hasText("Native Red Square reference post")
        ui.onNode(hasScrollAction()).performScrollToNode(post)
        ui.onNode(hasText(author) and hasAnyAncestor(hasAnyChild(post))).assertIsDisplayed().performClick()
    }

    private fun scrollUntilDisplayed(text: String) {
        repeat(10) {
            if (ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() && ui.onNodeWithText(text).isDisplayed()) return
            ui.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        }
        ui.onNodeWithText(text).assertIsDisplayed()
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        val file = File(context.cacheDir, "profile-edit-evidence/$name").apply { parentFile?.mkdirs() }
        val device = UiDevice.getInstance(instrumentation)
        check(device.takeScreenshot(file))
        device.dumpWindowHierarchy(File(file.parentFile, file.nameWithoutExtension + ".xml"))
    }
}
