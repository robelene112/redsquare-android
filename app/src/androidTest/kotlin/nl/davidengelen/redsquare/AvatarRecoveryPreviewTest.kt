package nl.davidengelen.redsquare

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import nl.davidengelen.redsquare.saito.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Availability failures keep native navigation alive and preserve signed changes. */
@OptIn(ExperimentalTestApi::class)
class AvatarRecoveryPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val color = Color.rgb(220, 30, 40)

    @Test fun anUnavailablePictureHasGeneratedFallbackAndExplicitProfileRetry() {
        val available = AtomicBoolean(false)
        val bytes = png()
        MockWebServer().use { images ->
            images.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = if (available.get())
                    MockResponse.Builder().body(Buffer().write(bytes)).build()
                else MockResponse.Builder().code(503).build()
            }
            images.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            withAvatarJourney("Light") { _, _, response, _, author ->
                waitForFeed()
                response.set(signed(author, 20, "http://127.0.0.1:${images.port}/avatar.png"))
                refresh()
                ui.waitUntil(10_000) { images.requestCount >= 1 }
                row(author.publicKey).performClick()
                ui.waitUntil(15_000) { ui.onNodeWithContentDescription("Retry profile picture").isDisplayed() }
                capture("unavailable-generated-retry")
                available.set(true)
                ui.onNodeWithContentDescription("Retry profile picture").performClick()
                hasColor(ui.onNodeWithContentDescription("Profile picture"))
                capture("retry-profile-loaded")
                ui.onNode(hasText("Home") and hasClickAction()).performClick()
                hasColor(row(author.publicKey))
                capture("retry-shared-feed-loaded")
            }
        }
    }

    @Test fun cacheWriteFailureDoesNotCrashProfileOrIgnoreSignedRemoval() = withAvatarJourney("Dark") { _, _, response, _, author ->
        waitForFeed()
        response.set(signed(author, 30, "data:image/png;base64," + Base64.encodeToString(png(), Base64.NO_WRAP)))
        // A directory at the owned cache path causes a real Android AtomicFile write failure.
        val cache = File(context.noBackupFilesDir, "avatarprofiles.owner.v1")
        synchronized(SaitoLocalState.lock) { cache.delete(); check(cache.mkdir()) }
        try {
            refresh()
            hasColor(row(author.publicKey))
            row(author.publicKey).performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Profile").isDisplayed() }
            hasColor(ui.onNodeWithContentDescription("Profile picture"))
            capture("unwritable-profile-still-loaded")
            ui.onNode(hasText("Home") and hasClickAction()).performClick()
            response.set(signed(author, 31, null))
            refresh()
            ui.waitUntil(10_000) { !containsColor(row(author.publicKey)) }
            capture("unwritable-signed-removal")
        } finally { check(cache.delete()) }
    }

    @Test fun threeLargeSignedPicturesStayVisibleTogetherWithoutSourceBudgetEviction() {
        val sources = (1..3).associate { key ->
            val author = SaitoWallet.fromPrivateKey("0".repeat(63) + key)
            val source = "data:image/png;base64," + Base64.encodeToString(png(noise = true), Base64.NO_WRAP)
            assertTrue("Fixture must exceed former2MiB combined source budget", source.length > 720_000)
            author.publicKey to signed(author, 40, source)
        }
        val posts = (1..3).map { key ->
            val author = SaitoWallet.fromPrivateKey("0".repeat(63) + key)
            SaitoTransaction.create(author, RedSquarePosts.message("Large avatar $key"), 1_790_000_000_000L + key)
                .let { JsonObject().apply { addProperty("tx", it.envelope()) } }
        }.joinToString(",", "[", "]")
        withAppFrameFixture("large-avatars", "avatar-evidence", feedRows = posts,
            profileArchiveResponse = { query -> sources[query.get("field2").asString] ?: "[]" }) {
            ui.waitUntil(15_000) { ui.onNodeWithText("Large avatar 3").isDisplayed() }
            if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
            for (key in sources.keys) hasColor(row(key))
            // Recheck first after all three loads, not just each picture's transient appearance.
            for (key in sources.keys) assertTrue(containsColor(row(key)))
            capture("three-large-avatars-retained")
        }
    }

    private fun waitForFeed() = ui.waitUntil(15_000) { ui.onNodeWithText("Shared avatar journey").isDisplayed() }
    private fun row(key: String) = ui.onNodeWithContentDescription("View profile for Anon-${key.take(6)}")
    private fun refresh() = ui.waitUntil(15_000) { runCatching {
        ui.onNodeWithTag("feed-post-list").performCustomAccessibilityActionWithLabel("Refresh"); true
    }.getOrDefault(false) }
    private fun hasColor(node: SemanticsNodeInteraction) = ui.waitUntil(10_000) { runCatching { containsColor(node) }.getOrDefault(false) }
    private fun containsColor(node: SemanticsNodeInteraction): Boolean {
        val image = node.captureToImage().asAndroidBitmap()
        var matches = 0
        for (y in 0 until image.height step 2) for (x in 0 until image.width step 2) {
            val pixel = image.getPixel(x, y)
            if (kotlin.math.abs(Color.red(pixel) - 220) < 10 && kotlin.math.abs(Color.green(pixel) - 30) < 10 &&
                kotlin.math.abs(Color.blue(pixel) - 40) < 10) matches++
        }
        return matches >= 25
    }
    private fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "avatar-evidence/$name.png"))
    private fun signed(author: SaitoWallet, revision: Int, source: String?): String = SaitoTransaction.create(author, JsonObject().apply {
        addProperty("module", "Profile"); addProperty("request", "update profile"); addProperty("snapshot", 1)
        add("data", JsonObject().apply { source?.let { addProperty("image", it) } })
    }, 1_900_000_000_000L + revision).let { tx -> JsonObject().apply { addProperty("tx", tx.envelope()) }.let { "[$it]" } }
    private fun png(noise: Boolean = false): ByteArray {
        val size = if (noise) 448 else 32
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        if (noise) {
            val random = java.util.Random(82)
            for (y in 0 until size) for (x in 0 until size)
                bitmap.setPixel(x, y, if (x in 187..260 && y in 187..260) color else Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)))
        } else bitmap.eraseColor(color)
        return try { ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray() }
        finally { bitmap.recycle() }
    }
}
