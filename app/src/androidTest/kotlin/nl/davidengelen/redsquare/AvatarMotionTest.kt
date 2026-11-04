package nl.davidengelen.redsquare

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/** Actual platform animation settings and frames, without a Compose test clock. */
class AvatarMotionTest {
    @Test fun signedPictureReplacementAndRemovalKeepTheirGeometryAtNormalAndZeroMotion() {
        for (scale in listOf(0, 1)) withNativeModuleMotionScale(scale) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val device = UiDevice.getInstance(instrumentation)
            val folder = File(context.cacheDir, "avatar-evidence").apply { mkdirs() }
            withAvatarJourney("Dark") { activity, _, response, _, author ->
                check(device.wait(Until.hasObject(By.text("Shared avatar journey")), 15_000))
                val label = "View profile for Anon-${author.publicKey.take(6)}"
                val before = requireNotNull(device.findObject(By.desc(label))).visibleBounds
                fun colorAtAvatar(): Int {
                    val bounds = requireNotNull(device.findObject(By.desc(label))).visibleBounds
                    val density = context.resources.displayMetrics.density
                    val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                    return try { screenshot.getPixel(bounds.left + (20 * density).toInt(), bounds.centerY()) }
                    finally { screenshot.recycle() }
                }
                fun awaitColor(expected: Int?) {
                    val deadline = SystemClock.elapsedRealtime() + 10_000
                    fun matches(): Boolean = runCatching {
                        val pixel = colorAtAvatar()
                        fun near(value: Int) = kotlin.math.abs(Color.red(pixel) - Color.red(value)) < 15 &&
                            kotlin.math.abs(Color.green(pixel) - Color.green(value)) < 15 &&
                            kotlin.math.abs(Color.blue(pixel) - Color.blue(value)) < 15
                        if (expected != null) near(expected) else !near(Color.RED) && !near(Color.BLUE)
                    }.getOrDefault(false)
                    while (!matches() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
                    check(matches()) { "Signed avatar state must render within10s" }
                }
                for ((index, color) in listOf(Color.RED, Color.BLUE, null).withIndex()) {
                    val source = color?.let {
                        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(it) }
                        try { val bytes = ByteArrayOutputStream().also { stream -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) }
                            "data:image/png;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                        } finally { bitmap.recycle() }
                    }
                    val tx = SaitoTransaction.create(author, JsonObject().apply {
                        addProperty("module", "Profile"); addProperty("request", "update profile"); addProperty("snapshot", 1)
                        add("data", JsonObject().apply { source?.let { addProperty("image", it) } })
                    }, 1_900_000_000_100L + index)
                    response.set(JsonObject().apply { addProperty("tx", tx.envelope()) }.let { "[$it]" })
                    val movie = File(folder, "scale-$scale-avatar-$index.mp4")
                    val recording = recordNativeTransition(movie, recordingSeconds = 5) {
                        // A one-post feed can have no forward scroll range. Pull inside the
                        // positively observed post and above the real navigation captions.
                        val post = requireNotNull(device.findObject(By.text("Shared avatar journey"))).visibleBounds
                        val navigation = requireNotNull(device.findObject(By.text("Home"))).visibleBounds
                        check(navigation.top - 50 > post.top + 80)
                        check(device.swipe(post.centerX(), post.top + 4, post.centerX(), navigation.top - 50, 30))
                        awaitColor(color)
                    }
                    val frames = retainNativeMotionFrames(movie)
                    val last = frames.getAsJsonArray("decodedOriginalFrames").last().asJsonObject.get("file").asString
                    val rendered = BitmapFactory.decodeFile(File(folder, last).path)
                    try {
                        fun count(target: Int): Int {
                            var found = 0
                            for (y in 0 until rendered.height step 4) for (x in 0 until rendered.width step 4) {
                                val pixel = rendered.getPixel(x, y)
                                if (kotlin.math.abs(Color.red(pixel) - Color.red(target)) < 15 &&
                                    kotlin.math.abs(Color.green(pixel) - Color.green(target)) < 15 &&
                                    kotlin.math.abs(Color.blue(pixel) - Color.blue(target)) < 15) found++
                            }
                            return found
                        }
                        if (color != null) assertTrue("The recording itself must contain the new picture", count(color) >= 20)
                        else assertTrue("The recording itself must contain the signed removal", count(Color.RED) == 0 && count(Color.BLUE) == 0)
                    } finally { rendered.recycle() }
                    frames.add("recording", recording)
                    File(folder, "scale-$scale-avatar-$index.json").writeText(frames.toString())
                    val after = requireNotNull(device.findObject(By.desc(label))).visibleBounds
                    // The ordinary pull-success notice can move the list. Photo loading must
                    // preserve the row's intrinsic geometry through every replacement.
                    assertEquals(before.width(), after.width())
                    assertEquals(before.height(), after.height())
                }
                observeModuleWindowMotionScale(activity, scale, folder)
            }
        }
    }
}
