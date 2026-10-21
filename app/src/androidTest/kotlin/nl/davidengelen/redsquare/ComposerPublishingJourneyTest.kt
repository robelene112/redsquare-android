package nl.davidengelen.redsquare

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.work.WorkManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ComposerPostJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun imageDraftReturnsAndPublishesOnlyAfterDeliberateAgreement() = runComposerPublishingJourney(ui, ComposerJourneyKind.POST)
}

class ComposerReplyJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun imageReplyReturnsWithItsThreadAndPublishesOnlyAfterDeliberateAgreement() = runComposerPublishingJourney(ui, ComposerJourneyKind.REPLY)
}

class ComposerQuoteJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun imageQuoteReturnsWithItsOriginalAndPublishesOnlyAfterDeliberateAgreement() = runComposerPublishingJourney(ui, ComposerJourneyKind.QUOTE)
}

internal enum class ComposerJourneyKind { POST, REPLY, QUOTE }

/** Real UI, owned media, durable return, agreement and signed loopback readback. */
private fun runComposerPublishingJourney(ui: ComposeTestRule, kind: ComposerJourneyKind) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    check(context.packageName == "nl.davidengelen.redsquare.dev" &&
        android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    val wallets = AndroidWalletStore(context)
    val wallet = wallets.loadOrCreate()
    val backends = AndroidBackendStore(context)
    val originalBackend = backends.load()
    val retained = ChatFixtureState(context, listOf("feed.", "conversation.", "profile.", "profilemeta.",
        "postdraft.", "authorposts.", "quoteoriginal.", "namemap.", "publishing-agreement.", "publishing-policy-fixture."), wallet.publicKey)
    val original = instrumentation.context.assets.open("native-following-reference.json").bufferedReader()
        .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root") }
    val referenceText = "Native Red Square reference post"
    val expectedText = AtomicReference<String>()
    val authoredTexts = mutableSetOf<String>()
    val ownedCandidates = ConcurrentHashMap.newKeySet<String>()
    val indexed = ConcurrentHashMap<String, JsonObject>()
    val visible = AtomicBoolean(false)
    val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
        val candidate = postingTransaction(frame)
        val signed = SaitoEnvelope.decode(candidate.envelope())
        val data = signed.body.getAsJsonObject("data")
        assertEquals(wallet.publicKey, signed.author)
        assertEquals("RedSquare", signed.body.get("module").asString)
        assertEquals("create tweet", signed.body.get("request").asString)
        assertEquals(expectedText.get(), data.get("text").asString)
        ownedCandidates.add(candidate.id)
        assertEquals(1, data.getAsJsonArray("images").size())
        val source = data.getAsJsonArray("images")[0].asString
        assertTrue(source.startsWith("data:image/jpeg;base64,"))
        val bytes = android.util.Base64.decode(source.substringAfter(','), android.util.Base64.NO_WRAP)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            assertEquals(12, bitmap.width); assertEquals(8, bitmap.height)
            val pixel = bitmap.getPixel(6, 4)
            assertTrue(kotlin.math.abs(Color.red(pixel) - 220) <= 5)
            assertTrue(kotlin.math.abs(Color.green(pixel) - 30) <= 5)
            assertTrue(kotlin.math.abs(Color.blue(pixel) - 40) <= 5)
        } finally { bitmap.recycle() }
        val recipients = if (kind == ComposerJourneyKind.POST) listOf(wallet.publicKey)
            else listOf(wallet.publicKey, original.get("field2").asString).distinct()
        assertEquals(recipients, signed.recipients)
        when (kind) {
            ComposerJourneyKind.POST -> { assertFalse(data.has("parent_id")); assertFalse(data.has("retweet_tx")) }
            ComposerJourneyKind.REPLY -> {
                assertEquals(original.get("sig").asString, data.get("parent_id").asString)
                assertEquals(original.get("sig").asString, data.get("thread_id").asString)
            }
            ComposerJourneyKind.QUOTE -> {
                assertFalse(data.has("parent_id"))
                assertEquals(original.get("sig").asString, data.get("signature").asString)
                assertEquals(original.get("tx").asString, data.get("retweet_tx").asString)
            }
        }
        indexed[candidate.id] = postingArchiveRow(candidate)
    }, archiveResponse = { criteria ->
        if (criteria.get("field1").asString != "RedSquare") "[]"
        else (listOf(original) + if (visible.get()) indexed.values.toList() else emptyList()).filter { row ->
            (!criteria.has("sig") || criteria.get("sig").asString == row.get("sig").asString) &&
                (!criteria.has("field2") || criteria.get("field2").asString == row.get("field2").asString) &&
                (!criteria.has("field5") || criteria.get("field5").asString == row.get("field5").asString)
        }.joinToString(prefix = "[", postfix = "]")
    })
    val folder = File(context.cacheDir, "composer-publishing-evidence").apply { mkdirs() }
    val policy = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
    var primary: Throwable? = null
    try {
        retained.prepare()
        backends.save(SaitoBackend.OWNER)
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { activity ->
                ui.waitUntil(20_000) { ui.onNodeWithText(referenceText).isDisplayed() }
                ui.withPostViewports(activity, folder, "composer-${kind.name.lowercase()}", scales = listOf(1.3f)) { label, _ ->
                    val text = "A retained ${kind.name.lowercase()} for $label.\nKeep my words and image together."
                    val before = peer.transactions.size
                    expectedText.set(text)
                    authoredTexts.add(text)
                    visible.set(false)
                    policy.writeText(JsonObject().apply {
                        addProperty("revision", "owned-composer-${java.util.UUID.randomUUID()}")
                        addProperty("ready", true); addProperty("enforce", true)
                        addProperty("terms", "Controlled composer terms. No real publisher approval.")
                        addProperty("community", "Controlled composer rules. Publish only by a deliberate choice.")
                    }.toString())
                    val editor = hasSetTextAction() and isEnabled()
                    fun open() {
                        if (kind == ComposerJourneyKind.POST) ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        else {
                            // Earlier theme publications may quote the same text. Open only the actual root body.
                            val body = hasText(referenceText) and !hasAnyAncestor(hasContentDescription("Quoted original"))
                            ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(body)
                            ui.onNode(body, useUnmergedTree = true).assertIsDisplayed().performClick()
                            ui.waitUntil(10_000) { ui.onNodeWithText("Conversation").isDisplayed() }
                            if (kind == ComposerJourneyKind.REPLY) ui.onNodeWithText("Reply").performClick()
                            else {
                                val referenceCard = hasClickAction() and hasAnyChild(hasText(referenceText))
                                val quote = hasText("Quote") and hasClickAction() and hasAnyAncestor(referenceCard)
                                ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(quote)
                                ui.onNode(quote, useUnmergedTree = true).assertIsDisplayed().performClick()
                            }
                        }
                        ui.waitUntil(10_000) { ui.onNode(editor).isDisplayed() }
                    }
                    fun home() {
                        if (kind != ComposerJourneyKind.POST) {
                            ui.waitUntil(10_000) { ui.onNodeWithText("Conversation").isDisplayed() }
                            ui.onNodeWithText("Back").performClick()
                        }
                        ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
                    }
                    open()
                    ui.onNode(editor).performTextReplacement(text)
                    ui.attachSyntheticImage()
                    captureNativeScreen(File(folder, "$label-attached.png"))
                    ui.onNodeWithText("Back").performClick()
                    home()
                    open()
                    ui.onNode(editor).assertTextEquals(text)
                    ui.onNodeWithText("Saved on this device").assertIsDisplayed()
                    ui.assertSyntheticRedImage()
                    if (kind == ComposerJourneyKind.QUOTE) ui.onNodeWithText(referenceText).assertExists()
                    ui.onNode(editor).performScrollTo().performClick()
                    fun imeVisible(): Boolean {
                        var result = false
                        activity.onActivity { result = ViewCompat.getRootWindowInsets(it.window.decorView)
                            ?.isVisible(WindowInsetsCompat.Type.ime()) == true }
                        return result
                    }
                    ui.waitUntil(10_000) { imeVisible() }
                    ui.onNode(hasText("Send") and hasClickAction()).assertIsDisplayed().assertIsEnabled()
                    captureNativeScreen(File(folder, "$label-returned-ime.png"))
                    UiDevice.getInstance(instrumentation).pressBack()
                    ui.waitUntil(10_000) { !imeVisible() }
                    ui.onNodeWithText("Send").performClick()
                    ui.waitUntil(10_000) { ui.onNodeWithText("Review publishing terms").isDisplayed() }
                    assertEquals(before, peer.transactions.size)
                    captureNativeScreen(File(folder, "$label-policy.png"))
                    ui.onNodeWithText("Cancel").performClick()
                    ui.waitUntil(10_000) { ui.onNode(editor).isDisplayed() }
                    ui.onNode(editor).assertTextEquals(text)
                    assertEquals(before, peer.transactions.size)
                    ui.onNodeWithText("Send").performClick()
                    ui.waitUntil(10_000) { ui.onNodeWithText("Review publishing terms").isDisplayed() }
                    ui.onNodeWithText("Accept").performClick()
                    ui.waitUntil(10_000) { ui.onNodeWithText("Agreement saved. New content stays unsent. Queued messages may resume.").isDisplayed() }
                    assertEquals("Accepting alone cannot publish", before, peer.transactions.size)
                    ui.onNodeWithText("Send").assertIsEnabled().performClick()
                    ui.waitUntil(10_000) {
                        peer.assertHealthy()
                        ui.onNodeWithText("Submitted. Waiting for this node.").isDisplayed()
                    }
                    assertEquals(before + 1, peer.transactions.size)
                    val candidate = postingTransaction(peer.transactions.last()).id
                    ui.onNode(hasText("Retry") and hasClickAction()).assertIsNotEnabled()
                    ui.onNode(hasSetTextAction()).assertIsNotEnabled().assertTextEquals(text)
                    ui.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
                        ProgressBarRangeInfo.Indeterminate)).assertCountEquals(1)
                    captureNativeScreen(File(folder, "$label-submitted.png"))
                    visible.set(true)
                    ui.waitUntil(20_000) {
                        peer.assertHealthy()
                        ui.onNodeWithText("Available on this node.").isDisplayed()
                    }
                    ui.waitUntil(10_000) { ui.onAllNodes(hasText("Done") and hasClickAction() and isEnabled()).fetchSemanticsNodes().size == 1 }
                    assertEquals(before + 1, peer.transactions.size)
                    assertEquals(candidate, postingTransaction(peer.transactions.last()).id)
                    captureNativeScreen(File(folder, "$label-available.png"))
                    ui.onNodeWithText("Done").performClick()
                    home()
                    assertTrue(wallet.exportJson() == wallets.loadExisting().exportJson())
                    assertEquals(SaitoBackend.OWNER, backends.load())
                    peer.assertHealthy()
                }
                assertEquals("One deliberate post per theme", 2, peer.transactions.size)
                assertEquals(2, indexed.size)
                assertTrue(peer.reports.isEmpty())
            }
        }
    } catch (failure: Throwable) { primary = failure; throw failure }
    finally {
        cleanupAfterTest(primary, {
            val reference = RedSquareFeed.decode(original.get("tx").asString)
            val saved = nl.davidengelen.redsquare.feed.PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER,
                parent = reference.takeIf { kind == ComposerJourneyKind.REPLY },
                quote = reference.takeIf { kind == ComposerJourneyKind.QUOTE }).load()
            saved.transaction?.takeIf { saved.text in authoredTexts }?.let { candidate ->
                assertEquals(wallet.publicKey, RedSquareFeed.decode(candidate.envelope()).author)
                ownedCandidates.add(candidate.id)
            }
        }, {
            cancelFixturePublishingJobs(context, ownedCandidates)
        }, { retained.restoreFiles() }, { retained.restorePreferences() },
            { backends.save(originalBackend); check(backends.load() == originalBackend) },
            { check(wallets.loadExisting().exportJson() == wallet.exportJson()) })
    }
}

/** Await every verified fixture-owned job independently before restoring its saved records. */
internal fun cancelFixturePublishingJobs(context: android.content.Context, candidates: Set<String>) {
    val failures = mutableListOf<Throwable>()
    for (id in candidates) try {
        WorkManager.getInstance(context).cancelUniqueWork("post:$id").result.get(5, TimeUnit.SECONDS)
    } catch (failure: Throwable) { failures.add(failure) }
    if (failures.isNotEmpty()) {
        failures.drop(1).forEach(failures.first()::addSuppressed)
        throw failures.first()
    }
}
