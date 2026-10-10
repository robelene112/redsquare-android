package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import org.hamcrest.CoreMatchers.anyOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

/** Known input pixels and hand-written EXIF tags; observe normalized bytes on the real socket. */
class ImagePreparationTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val owned = mutableListOf<Uri>()
    private val palette = listOf(Color.RED, Color.BLUE, Color.GREEN, Color.WHITE)

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("postdraft.") || it.name.startsWith("feed.") }
            ?.forEach { check(it.delete()) }
    }

    @Test fun allEightExifOrientationsBecomeUprightPixelsWithoutSourceMetadata() {
        val maps = listOf(listOf(0,1,2,3), listOf(1,0,3,2), listOf(3,2,1,0), listOf(2,3,0,1),
            listOf(0,2,1,3), listOf(2,0,3,1), listOf(3,1,2,0), listOf(1,3,0,2))
        val jpeg = raster(Bitmap.CompressFormat.JPEG)
        for (batch in listOf(1..4, 5..8)) {
            val selected = batch.map { write(withExif(jpeg, it), "image/jpeg") }
            withComposer { peer, sent ->
                select(selected)
                waitForCount(4)
                ui.onNodeWithText("Send").performClick()
                waitForAvailable(peer)
                val images = SaitoEnvelope.decode(requireNotNull(sent.get()).get("tx").asString).body.getAsJsonObject("data").getAsJsonArray("images")
                assertEquals(4, images.size())
                batch.forEachIndexed { index, orientation ->
                    decoded(images[index].asString) { bitmap ->
                        assertEquals(if (orientation < 5) 64 else 48, bitmap.width)
                        assertEquals(if (orientation < 5) 48 else 64, bitmap.height)
                        val coordinates = listOf(1 to 1, 3 to 1, 1 to 3, 3 to 3)
                        coordinates.forEachIndexed { corner, (x, y) ->
                            closeColor(palette[maps[orientation - 1][corner]], bitmap.getPixel(bitmap.width * x / 4, bitmap.height * y / 4))
                        }
                    }
                }
                assertEquals(1, peer.transactions.size)
            }
        }
    }

    @Test fun pngAlphaWebpGifAndJpegAreFlattenedAndFittedWithinTheMessageBudget() {
        val transparent = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().also { output ->
            try { assertTrue(transparent.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            finally { transparent.recycle() }
        }.toByteArray()
        val wide = Bitmap.createBitmap(4000, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val webp = ByteArrayOutputStream().also { output ->
            try { assertTrue(wide.compress(Bitmap.CompressFormat.WEBP, 100, output)) }
            finally { wide.recycle() }
        }.toByteArray()
        // GIF89a, one green pixel. LZW clear/index/end codes are 4,1,5.
        val gif = "4749463839610100010080000000000000ff002c00000000010001000002024c01003b"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val selected = listOf(write(withExif(raster(Bitmap.CompressFormat.JPEG), 1), "image/jpeg"),
            write(png, "image/png"), write(webp, "image/webp"), write(gif, "image/gif"))
        withComposer { peer, sent ->
            select(selected)
            waitForCount(4)
            ui.onNodeWithText("Add images").assertIsNotEnabled()
            ui.onNodeWithText("Send").performClick()
            waitForAvailable(peer)
            val message = SaitoEnvelope.decode(requireNotNull(sent.get()).get("tx").asString).body
            assertTrue(message.toString().toByteArray(Charsets.UTF_8).size <= 1024 * 1024)
            val images = message.getAsJsonObject("data").getAsJsonArray("images")
            decoded(images[1].asString) { closeColor(Color.WHITE, it.getPixel(it.width / 2, it.height / 2)) }
            decoded(images[2].asString) {
                assertEquals(1920, it.width); assertEquals(960, it.height)
                closeColor(Color.BLUE, it.getPixel(960, 480))
            }
            decoded(images[3].asString) {
                assertEquals(1, it.width); assertEquals(1, it.height)
                closeColor(Color.GREEN, it.getPixel(0, 0))
            }
            assertEquals(1, peer.transactions.size)
        }
    }

    @Test fun failedAndExcessSelectionsPreservePriorTextAndImagesWithoutPartialBatches() {
        val original = write(raster(Bitmap.CompressFormat.PNG), "image/png")
        val malformed = write("not an image".toByteArray(), "image/png")
        val missing = write(byteArrayOf(1), "image/png").also {
            assertEquals(1, context.contentResolver.delete(it, null, null))
            owned.remove(it)
        }
        val oversized = write(byteArrayOf(1), "image/png").also { uri ->
            context.contentResolver.openOutputStream(uri, "w")!!.use { output ->
                val block = ByteArray(8192)
                repeat(4096) { output.write(block) }
                output.write(0)
            }
        }
        withComposer { peer, sent ->
            select(listOf(original)); waitForCount(1)
            val before = ui.onNode(hasSetTextAction()).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
            for (selection in listOf(listOf(original, malformed), listOf(missing), List(5) { original }, listOf(oversized))) {
                select(selection)
                ui.waitUntil(20_000) { ui.onAllNodesWithText("Could not prepare or save the images. Your draft is still here.").fetchSemanticsNodes().isNotEmpty() &&
                    ui.onAllNodes(hasText("Add images") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Remove image 1").assertExists()
                ui.onAllNodesWithText("Remove image 2").assertCountEquals(0)
                ui.onNode(hasSetTextAction()).assert(hasText(before))
                assertTrue(peer.transactions.isEmpty())
            }
            ui.onNodeWithText("Send").performClick(); waitForAvailable(peer)
            assertEquals(1, SaitoEnvelope.decode(requireNotNull(sent.get()).get("tx").asString).body.getAsJsonObject("data").getAsJsonArray("images").size())
        }
    }

    private fun withComposer(block: (ControlledFeedPeer, AtomicReference<JsonObject?>) -> Unit) {
        val sent = AtomicReference<JsonObject?>()
        val fixture = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("ordinary") }
        val peer = ControlledFeedPeer("[$fixture]", onTransaction = { frame -> sent.set(postingArchiveRow(postingTransaction(frame))) },
            archiveResponse = { criteria -> if (criteria.has("sig")) sent.get()?.let { "[$it]" } ?: "[]" else "[$fixture]" })
        withImageFixtureCleanup({
            try { cleanupSyntheticImages(context.contentResolver, owned.toList()) } finally { owned.clear() }
        }) {
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    try {
                        ui.waitUntil(20_000) { peer.requests.get() > 0 && ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(hasSetTextAction()).performTextReplacement("Image preparation fixture")
                        block(peer, sent)
                        ui.waitUntil(10_000) { ui.onAllNodes(hasText("Back") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText("Back").performClick()
                        ui.waitUntil(10_000) { ui.onAllNodes(hasText("New post") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
                    } catch (failure: Throwable) {
                        val file = File(context.cacheDir, "images-evidence/preparation-failure.png").apply { parentFile?.mkdirs() }
                        androidx.test.uiautomator.UiDevice.getInstance(instrumentation).takeScreenshot(file)
                        throw failure
                    }
                }
            }
        }
    }

    private fun select(uris: List<Uri>) {
        val result = Intent().setData(uris.first()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (uris.size > 1) result.clipData = ClipData.newRawUri("Synthetic images", uris.first()).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        Intents.init()
        try {
            Intents.intending(anyOf(hasAction(Intent.ACTION_OPEN_DOCUMENT), hasAction(Intent.ACTION_GET_CONTENT),
                hasAction("android.provider.action.PICK_IMAGES"), hasAction("androidx.activity.result.contract.action.PICK_IMAGES"),
                hasAction("com.google.android.gms.provider.action.PICK_IMAGES")))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, result))
            ui.onNodeWithText("Add images").performScrollTo().performClick()
            ui.waitForIdle()
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Add images") and isEnabled()).fetchSemanticsNodes().isNotEmpty() ||
                ui.onAllNodes(hasText("Remove image 4") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        } finally { Intents.release() }
    }

    private fun waitForCount(count: Int) = ui.waitUntil(20_000) { ui.onAllNodes(hasText("Remove image $count") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    private fun waitForAvailable(peer: ControlledFeedPeer) = ui.waitUntil(20_000) { peer.assertHealthy(); ui.onAllNodesWithText("Available on this server.").fetchSemanticsNodes().isNotEmpty() }

    private fun write(bytes: ByteArray, mime: String): Uri {
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "redsquare-preparation-${owned.size}")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
        }))
        owned.add(uri)
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        return uri
    }

    private fun raster(format: Bitmap.CompressFormat): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val paint = Paint()
            palette.forEachIndexed { index, color ->
                paint.color = color
                val left = (index % 2) * 32f; val top = (index / 2) * 24f
                drawRect(left, top, left + 32, top + 24, paint)
            }
        }
        return ByteArrayOutputStream().also { output ->
            try { assertTrue(bitmap.compress(format, 100, output)) } finally { bitmap.recycle() }
        }.toByteArray()
    }

    private fun withExif(jpeg: ByteArray, orientation: Int): ByteArray {
        // Little-endian TIFF: orientation, description and a synthetic GPS latitude IFD.
        val description = "Synthetic GPS privacy marker\u0000".toByteArray()
        val tiff = ByteBuffer.allocate(116 + description.size).order(ByteOrder.LITTLE_ENDIAN)
        tiff.put('I'.code.toByte()).put('I'.code.toByte()).putShort(42).putInt(8).putShort(3)
        tiff.putShort(0x112).putShort(3).putInt(1).putShort(orientation.toShort()).putShort(0)
        tiff.putShort(0x10e).putShort(2).putInt(description.size).putInt(116)
        tiff.putShort(0x8825.toShort()).putShort(4).putInt(1).putInt(50).putInt(0)
        tiff.putShort(3)
        tiff.putShort(0).putShort(1).putInt(4).putInt(0x0302)
        tiff.putShort(1).putShort(2).putInt(2).putInt('N'.code)
        tiff.putShort(2).putShort(5).putInt(3).putInt(92).putInt(0)
        tiff.putInt(52).putInt(1).putInt(5).putInt(1).putInt(0).putInt(1).put(description)
        val payload = "Exif\u0000\u0000".toByteArray() + tiff.array()
        val marker = byteArrayOf(0xff.toByte(), 0xe1.toByte(), ((payload.size + 2) shr 8).toByte(), (payload.size + 2).toByte())
        return jpeg.copyOfRange(0, 2) + marker + payload + jpeg.copyOfRange(2, jpeg.size)
    }

    private fun decoded(uri: String, block: (Bitmap) -> Unit) {
        assertTrue(uri.startsWith("data:image/jpeg;base64,")); assertTrue(uri.length <= 512 * 1024)
        val bytes = android.util.Base64.decode(uri.substringAfter(','), android.util.Base64.NO_WRAP)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertFalse(text.contains("Exif\u0000\u0000")); assertFalse(text.contains("Synthetic GPS privacy marker"))
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try { block(bitmap) } finally { bitmap.recycle() }
    }

    private fun closeColor(expected: Int, actual: Int) {
        listOf(Color.red(expected) to Color.red(actual), Color.green(expected) to Color.green(actual), Color.blue(expected) to Color.blue(actual)).forEach {
            assertTrue("Expected color $expected, got $actual", kotlin.math.abs(it.first - it.second) <= 20)
        }
    }
}
