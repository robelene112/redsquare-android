package nl.davidengelen.redsquare

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import nl.davidengelen.redsquare.feed.AvatarImageCache
import nl.davidengelen.redsquare.feed.AvatarProfileCache
import nl.davidengelen.redsquare.saito.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/** Android persistence/decoding boundary; native route rendering has separate coverage. */
class AvatarCachePreviewTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun diskRasterIsBoundedAndSignedVersionAndNodeHaveSeparateKeys() = runBlocking {
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val directory = File(context.cacheDir, "profile-avatar-images")
        val retained = directory.listFiles()?.associate { it.name to it.readBytes() }.orEmpty()
        val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
        fun png(color: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            return try { ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray() }
            finally { bitmap.recycle() }
        }
        var primary: Throwable? = null
        try {
            directory.listFiles()?.forEach { check(it.delete()) }
            MockWebServer().use { server ->
                server.enqueue(MockResponse.Builder().body(Buffer().write(png(Color.RED))).build())
                server.enqueue(MockResponse.Builder().body(Buffer().write(png(Color.BLUE))).build())
                server.enqueue(MockResponse.Builder().body(Buffer().write(png(Color.GREEN))).build())
                server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
                val source = "http://127.0.0.1:${server.port}/picture.png"
                val first = AvatarImageCache.load(context, SaitoBackend.OWNER, author, "first-signed-version", source)
                assertEquals(Color.RED, first.getPixel(0, 0)); assertEquals(256, first.width)
                assertTrue(first.height <= 256)
                assertEquals(Color.RED, AvatarImageCache.load(context, SaitoBackend.OWNER, author, "first-signed-version", source).getPixel(0, 0))
                assertEquals(1, server.requestCount)
                assertEquals(Color.BLUE, AvatarImageCache.load(context, SaitoBackend.OWNER, author, "new-signed-version", source).getPixel(0, 0))
                assertEquals(Color.GREEN, AvatarImageCache.load(context, SaitoBackend.OFFICIAL, author, "new-signed-version", source).getPixel(0, 0))
                assertEquals(3, server.requestCount)
                // Native disk decoding is exercised independently of the loader's memory cache.
                val files = directory.listFiles()!!.filter { it.extension == "png" }
                assertEquals(3, files.size)
                files.forEach { file ->
                    assertTrue(file.length() in 1..384L * 1024)
                    val raster = BitmapFactory.decodeFile(file.path)
                    assertTrue(raster.width <= 256 && raster.height <= 256)
                }
                server.close()
                assertEquals(Color.BLUE, AvatarImageCache.load(context, SaitoBackend.OWNER, author, "new-signed-version", source).getPixel(0, 0))
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, {
            directory.listFiles()?.forEach { check(it.delete()) }
            directory.mkdirs()
            retained.forEach { (name, bytes) -> File(directory, name).writeBytes(bytes) }
        }) }
    }

    @Test fun signedRemovalWinsOverStaleReadsAndAnotherSignerCannotSupplyItsPicture() = runBlocking {
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val other = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val state = ChatFixtureState(context, emptyList(), author.publicKey)
        fun profile(signer: SaitoWallet, time: Long, image: String?) = SaitoTransaction.create(signer, JsonObject().apply {
            addProperty("module", "Profile"); addProperty("request", "update profile"); addProperty("snapshot", 1)
            add("data", JsonObject().apply { image?.let { addProperty("image", it) } })
        }, time).let { tx -> JsonObject().apply { addProperty("tx", tx.envelope()) }.let { "[$it]" } }
        var primary: Throwable? = null
        try {
            state.prepare()
            val cache = AvatarProfileCache(context, SaitoBackend.OWNER)
            val first = SaitoProfiles.readArchive(profile(author, 100, "https://example.invalid/photo.png"), author.publicKey)
            cache.save(first)
            assertEquals(first.image, AvatarProfileCache(context, SaitoBackend.OWNER).load(author.publicKey).image)
            assertNull(AvatarProfileCache(context, SaitoBackend.OFFICIAL).load(author.publicKey).image)
            cache.save(SaitoProfiles.readArchive(profile(other, 200, "https://example.invalid/wrong.png"), author.publicKey))
            assertEquals(first.image, cache.load(author.publicKey).image)
            cache.save(SaitoProfiles.readArchive(profile(author, 300, null), author.publicKey))
            cache.save(first)
            assertNull(cache.load(author.publicKey).image)
            assertEquals(300L, cache.load(author.publicKey).updates.single().createdAt)
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { state.restoreFiles() }, { state.restorePreferences() }) }
    }
}
