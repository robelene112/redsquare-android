package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.feed.SearchViewState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SearchRecoveryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun disposalPausesBeforeCancelledLookupFinishesUnwinding() {
        val view = SearchViewState().apply { query = "reference post"; search() }
        val lookup = Job()
        // A cancelled job remains incomplete until its still-unwinding child finishes.
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val child = CoroutineScope(Dispatchers.Default + lookup).launch {
            try { started.complete(Unit); awaitCancellation() }
            finally { withContext(NonCancellable) { release.await() } }
        }
        view.activeLookup = lookup
        var primary: Throwable? = null
        try {
            runBlocking { withTimeout(5_000) { started.await() } }
            view.pause()
            assertTrue(lookup.isCancelled)
            assertFalse("Cancellation cleanup is deliberately still pending", lookup.isCompleted)
            assertTrue(view.paused)
            assertEquals(view.request, view.completed)
            assertNull(view.activeLookup)
            assertEquals(listOf("RedSquare", "RedSquareRetweet"), view.pending)
            assertEquals("reference post", view.searchedQuery)
            view.retry()
            assertFalse(view.paused)
            assertNotEquals(view.request, view.completed)
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary,
                { lookup.cancel(); release.complete(Unit) },
                { runBlocking { withTimeout(5_000) { child.join() } } })
        }
    }

    @Test fun retryingAUsernameFailureDoesNotRepeatCompletedPostStreams() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val rows = instrumentation.context.assets.open("native-registry-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("rows").toString()
        }
        val available = AtomicBoolean(false)
        val names = AtomicInteger()
        val posts = AtomicInteger()
        val quotes = AtomicInteger()
        withAppFrameFixture("username-retry", "search-recovery-evidence", "[]",
            registryResponse = { criteria ->
                if (criteria.get("request").asString == "registry namecheck") {
                    names.incrementAndGet()
                    if (available.get()) rows else "unavailable"
                } else "{}"
            }, archiveResponse = { criteria ->
                if (criteria.get("limit").asInt == 100 && !criteria.has("field4")) posts.incrementAndGet()
                "[]"
            }, quoteArchiveResponse = { criteria ->
                if (criteria.get("limit").asInt == 100 && !criteria.has("field4")) quotes.incrementAndGet()
                "[]"
            }) { _ ->
            ui.waitUntil(15_000) { ui.onNode(hasText("Later") and hasClickAction()).isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.onNode(hasText("Search") and hasClickAction()).performClick()
            ui.onNode(hasSetTextAction()).performTextInput("NativeCase7@saito")
            ui.waitUntil(15_000) { ui.onNode(hasContentDescription("Search history") and isEnabled()).isDisplayed() }
            ui.onNode(hasSetTextAction()).performImeAction()
            ui.waitUntil(15_000) {
                ui.onNodeWithText("Usernames could not be checked.").isDisplayed() &&
                    ui.onNode(hasText("Retry search") and isEnabled()).isDisplayed() &&
                    posts.get() == 1 && quotes.get() == 1
            }
            ui.onNodeWithText("Retry search").assertIsEnabled()
            assertEquals(1, names.get()); assertEquals(1, posts.get()); assertEquals(1, quotes.get())
            available.set(true)
            ui.onNodeWithText("Retry search").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Identity details for NativeCase7@saito").isDisplayed() }
            ui.onNodeWithContentDescription("Identity details for NativeCase7@saito").performClick()
            ui.onNodeWithText("jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR").assertIsDisplayed()
            assertEquals(2, names.get()); assertEquals(1, posts.get()); assertEquals(1, quotes.get())
            captureNativeScreen(File(instrumentation.targetContext.cacheDir,
                "search-recovery-evidence/username-retried.png"), "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR")
        }
    }

    @Test fun leavingAnUnfinishedSearchRetainsResultsAndRequiresExplicitContinuation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.cacheDir, "search-recovery-evidence").apply { mkdirs() }
        val rows = instrumentation.context.assets.open("native-feed-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("archiveRow").let { "[$it]" }
        }
        val posts = AtomicInteger()
        val quotes = AtomicInteger()
        val quoteStarted = CountDownLatch(1)
        val releaseQuote = CountDownLatch(1)
        try {
            withAppFrameFixture("unfinished-navigation", "search-recovery-evidence", "[]",
                archiveResponse = { criteria ->
                    if (criteria.get("limit").asInt == 100 && !criteria.has("field4")) {
                        posts.incrementAndGet(); rows
                    } else "[]"
                }, quoteArchiveResponse = { criteria ->
                    if (criteria.get("limit").asInt == 100 && !criteria.has("field4")) {
                        if (quotes.incrementAndGet() == 1) {
                            quoteStarted.countDown()
                            check(releaseQuote.await(20, TimeUnit.SECONDS))
                        }
                    }
                    "[]"
                }) { activity ->
                ui.waitUntil(15_000) { ui.onNode(hasText("Later") and hasClickAction()).isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                ui.onNode(hasText("Search") and hasClickAction()).performClick()
                ui.onNode(hasSetTextAction()).performTextInput("reference post")
                ui.waitUntil(15_000) { ui.onNode(hasContentDescription("Search history") and isEnabled()).isDisplayed() }
                ui.onNode(hasSetTextAction()).performImeAction()
                ui.waitUntil(10_000) { quoteStarted.count == 0L &&
                    ui.onNodeWithText("Native Red Square reference post").isDisplayed() }
                ui.onNode(hasText("Home") and hasClickAction()).performClick()
                ui.waitUntil(10_000) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                releaseQuote.countDown()
                ui.onNode(hasText("Search") and hasClickAction()).performClick()
                fun assertPaused() {
                    ui.waitUntil(15_000) { ui.onNodeWithText("Search paused.").isDisplayed() }
                    ui.onNode(hasSetTextAction()).assertTextContains("reference post")
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    assertEquals("Navigation keeps the completed post stream", 1, posts.get())
                    assertEquals("Navigation does not restart the unfinished quote stream", 1, quotes.get())
                    ui.onNodeWithText("Continue search").assertIsEnabled()
                }
                assertPaused()
                captureNativeScreen(File(folder, "paused-after-return.png"), "Search paused.")
                activity.recreate()
                assertPaused()
                captureNativeScreen(File(folder, "paused-after-recreation.png"), "Search paused.")
                ui.onNodeWithText("Continue search").performClick()
                ui.waitUntil(15_000) { quotes.get() == 2 &&
                    ui.onNode(hasContentDescription("Search history") and isEnabled()).isDisplayed() }
                ui.onNodeWithText("Search paused.").assertDoesNotExist()
                ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                assertEquals("Continue fetches only the unfinished stream", 1, posts.get())
                captureNativeScreen(File(folder, "continued-explicitly.png"), "Native Red Square reference post")
            }
        } finally { releaseQuote.countDown() }
    }
}
