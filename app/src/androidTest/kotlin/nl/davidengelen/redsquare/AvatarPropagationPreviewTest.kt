package nl.davidengelen.redsquare

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** One signed synthetic public profile, exercised through the real application. */
class AvatarPropagationPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun signedProfilePictureAppearsOnItsFeedIdentity() {
        val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(220, 30, 40)) }
        val source = try {
            val bytes = ByteArrayOutputStream().also { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
            "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } finally { bitmap.recycle() }
        val signed = SaitoTransaction.create(author, JsonObject().apply {
            addProperty("module", "Profile"); addProperty("request", "update profile"); addProperty("snapshot", 1)
            add("data", JsonObject().apply { addProperty("image", source) })
        }, 1_900_000_000_000L)
        val row = JsonObject().apply {
            addProperty("tx", signed.envelope()); addProperty("field1", "Profile"); addProperty("field2", author.publicKey)
        }
        val requests = AtomicInteger()
        withAppFrameFixture("avatar-feed", "avatar-evidence", profileArchiveResponse = { query ->
            if (query.get("field2").asString == author.publicKey) {
                requests.incrementAndGet(); "[$row]"
            } else "[]"
        }) {
            val label = "View profile for Anon-${author.publicKey.take(6)}"
            ui.waitUntil(15_000) { ui.onNodeWithContentDescription(label).isDisplayed() }
            ui.waitUntil(10_000) {
                val image = ui.onNodeWithContentDescription(label).captureToImage().asAndroidBitmap()
                val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
                val pixel = image.getPixel((20 * density).toInt(), image.height / 2)
                kotlin.math.abs(Color.red(pixel) - 220) < 15 &&
                    kotlin.math.abs(Color.green(pixel) - 30) < 15 && kotlin.math.abs(Color.blue(pixel) - 40) < 15
            }
            assertTrue("Visible rows must use the selected node's signed Profile", requests.get() >= 1)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "avatar-evidence/feed-custom-picture.png"))
        }
    }
}
