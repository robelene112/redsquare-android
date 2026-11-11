package nl.davidengelen.redsquare

import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import nl.davidengelen.redsquare.feed.PublicStringStore
import nl.davidengelen.redsquare.saito.SaitoLocalState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real signed feed rendering while a separate owned durable-cache critical section is pending. */
class FeedScrollIoPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun newPostsRenderBeforeAnUnrelatedCacheCommitReleasesItsDurableLock() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val rows = instrumentation.context.assets.open("native-feed-scroll-reference.json").reader().use {
            JsonParser.parseReader(it).asJsonObject.get("initialRows").toString()
        }
        withAppFrameFixture("scroll-io", "feed-scroll-io-evidence", feedRows = rows) {
            ui.waitUntil(15_000) { ui.onNodeWithText("Reference post 20").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.onNodeWithText("Reference post 05").assertDoesNotExist()
            val name = "scrollio.${UUID.randomUUID().toString().replace("-", "")}.v1"
            val files = listOf(name, "$name.bak", "$name.new").map { File(context.noBackupFilesDir, it) }
            check(files.none { it.exists() })
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val holding = AtomicBoolean(false)
            val workerFailure = AtomicReference<Throwable?>(null)
            val worker = thread(name = "scroll-owned-cache", isDaemon = true) {
                try {
                    synchronized(SaitoLocalState.lock) {
                        // Exercise a real bounded atomic cache write, then hold its
                        // enclosing durable transaction as a controlled slow-I/O seam.
                        PublicStringStore(context, name, 1).save(listOf("Owned scroll observation"))
                        holding.set(true)
                        entered.countDown()
                        release.await(8, TimeUnit.SECONDS)
                        holding.set(false)
                    }
                } catch (failure: Throwable) {
                    workerFailure.set(failure)
                    entered.countDown()
                }
            }
            var scrollMillis: Long? = null
            var renderedWhileHeld = false
            var primary: Throwable? = null
            try {
                assertTrue("Owned cache transaction must begin", entered.await(3, TimeUnit.SECONDS))
                workerFailure.get()?.let { throw it }
                assertTrue(holding.get())
                val started = SystemClock.uptimeMillis()
                try {
                    // Jump far enough to require new real PostCard compositions,
                    // including their Like/Repost controls, rather than reuse visible rows.
                    ui.onNodeWithTag("feed-post-list").performScrollToIndex(15)
                    ui.onNodeWithText("Reference post 05").assertIsDisplayed()
                    renderedWhileHeld = holding.get()
                } finally { scrollMillis = SystemClock.uptimeMillis() - started }
                assertTrue("Scrolling must finish within3000ms while unrelated durable I/O remains pending",
                    requireNotNull(scrollMillis) <= 3_000)
                assertTrue("The new post must render before the cache transaction is released", renderedWhileHeld)
            } catch (failure: Throwable) {
                primary = failure
                throw failure
            } finally {
                // Release the owned store before failure capture or Activity closure.
                release.countDown()
                cleanupAfterTest(primary, {
                    worker.join(3_000)
                    check(!worker.isAlive)
                    workerFailure.get()?.let { throw it }
                }, {
                    synchronized(SaitoLocalState.lock) {
                        files.forEach { if (it.exists()) check(it.delete()) }
                        check(files.none { it.exists() })
                    }
                }, {
                    File(context.cacheDir, "feed-scroll-io-evidence/scroll-observation.json").writeText(
                        JsonObject().apply {
                            addProperty("scrollMillis", scrollMillis)
                            addProperty("renderedWhileDurableLockHeld", renderedWhileHeld)
                            addProperty("ownedWorkerStopped", !worker.isAlive)
                            addProperty("ownedFilesRestoredAbsent", files.none { it.exists() })
                            addProperty("qualification", "Controlled durable transaction delay and real native scrolling; not physical frame-rate acceptance")
                        }.toString())
                })
            }
            captureNativeScreen(File(context.cacheDir, "feed-scroll-io-evidence/new-post-after-cache-restoration.png"))
        }
    }
}
