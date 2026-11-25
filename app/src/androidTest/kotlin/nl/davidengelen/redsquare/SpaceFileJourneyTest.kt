package nl.davidengelen.redsquare

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.util.AtomicFile
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class SpaceFileJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in setOf("ranchu", "goldfish"))
    }
    private val device get() = UiDevice.getInstance(instrumentation)
    private val evidence get() = File(context.cacheDir, "spaces-files-evidence").apply { mkdirs() }
    private val checkpoint get() = File(context.cacheDir, "spaces-files-checkpoint.json")
    private val name = "Space library acceptance.png"
    private val sourceBytes by lazy {
        android.graphics.Bitmap.createBitmap(85, 85, android.graphics.Bitmap.Config.ARGB_8888).let { bitmap ->
            val pixels = IntArray(85 * 85) { pos -> (0xff000000L + (pos * 131_071L and 0xffffff)).toInt() }
            bitmap.setPixels(pixels, 0, 85, 0, 0, 85, 85)
            java.io.ByteArrayOutputStream().use { sink ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, sink))
                bitmap.recycle()
                sink.toByteArray()
            }
        }
    }

    @Test fun chooseProviderAndUploadFromTheAndroidDocumentPicker() = withProviderCertificate {
        val wallet = AndroidWalletStore(context)
        wallet.replace(wallet.loadOrCreate(), SaitoWallet.fromPrivateKey("0".repeat(63) + "3"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        File(context.noBackupFilesDir, "publishing-policy-fixture.json").writeText(
            """{"revision":"spaces-file-fixture","ready":true,"enforce":false,"terms":"Isolated fixture terms","community":"Isolated fixture rules"}""")
        val fixture = request("file-library")
        val document = insertDocument()
        try {
            launch(fixture.get("link").asString).use {
                capturing("file-first-failure") {
                    waitVisible("No storage provider selected.", 30_000)
                    click("Shared library")
                    val providerUrl = ui.onNode(hasText("Storage provider URL") and hasSetTextAction())
                    ui.waitUntil(8000) { providerUrl.isDisplayed() }
                    providerUrl.performClick()
                    providerUrl.performTextInput(fixture.get("origin").asString)
                    device.pressBack()
                    click("Review terms")
                    val price = text("Price is 0 SAITO for this term.")
                    ui.waitUntil(15000) { price.isDisplayed() || runCatching { price.fetchSemanticsNode() }.isSuccess }
                    price.performScrollTo().assertIsDisplayed()
                    capture("file-terms")
                    assertEquals(0, request("file-provider-downloads").get("nativeDownloads").asInt)
                    click("Confirm storage terms")
                    click("Upload a file", timeout = 25_000, lazy = true)
                    val source = device.wait(Until.findObject(By.text(name)), 10000)
                    if (source == null) {
                        device.findObject(By.descContains("Show roots"))?.click()
                        device.wait(Until.findObject(By.text("Downloads")), 5000)?.click()
                    }
                    val selected = source ?: device.wait(Until.findObject(By.text(name)), 10_000)
                    assertNotNull("System DocumentsUI must expose the created document", selected)
                    selected!!.click()
                    val published = ui.onNode(hasText(name) and hasClickAction())
                    ui.waitUntil(35_000) { published.isDisplayed() }
                    assertEquals(0, request("file-provider-downloads").get("nativeDownloads").asInt)
                    capture("file-library")
                    click(name)
                    waitVisible("Keep an offline copy")
                    click("Keep an offline copy")
                    waitVisible("Remove offline copy", 30_000)
                    assertEquals(1, request("file-provider-downloads").get("nativeDownloads").asInt)
                    capture("file-keep-offline")
                    val proof = request("file-received")
                    assertEquals(1, proof.get("files").asInt)
                    assertEquals(name, proof.get("fileName").asString)
                    assertEquals(sourceBytes.size, proof.get("size").asInt)
                    assertEquals(digest(), proof.get("sha256").asString)
                    val saved = fixture.deepCopy().apply {
                        addProperty("phase", "ready-for-process-kill")
                        addProperty("pid", Process.myPid())
                        addProperty("providerPid", proof.get("providerPid").asLong)
                        addProperty("record", proof.get("record").asString)
                        addProperty("lease", proof.get("lease").asString)
                        addProperty("sha256", digest())
                        addProperty("used", proof.get("used").asLong)
                    }
                    val file = AtomicFile(checkpoint)
                    val stream = file.startWrite()
                    try { stream.write(saved.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
                    catch (problem: Throwable) { file.failWrite(stream); throw problem }
                    context.contentResolver.delete(document, null, null)
                    while (true) android.os.SystemClock.sleep(200)
                }
            }
        } finally { context.contentResolver.delete(document, null, null) }
    }

    @Test fun restoreOfflineCopyAndSeparateLocalFromSharedRemoval() = withProviderCertificate {
        val before = JsonParser.parseString(checkpoint.readText()).asJsonObject
        assertNotEquals(before.get("pid").asInt, Process.myPid())
        launch(before.get("link").asString).use {
            capturing("file-recovery-failure") {
                waitVisible(name, 25_000)
                click(name)
                waitVisible("Remove offline copy")
                request("file-provider-stop")
                click("Open file")
                val receiver = device.wait(Until.findObject(By.text("Spaces file verifier")), 10_000)
                assertNotNull("A separate Android file consumer must be offered", receiver)
                capture("file-open-chooser")
                receiver!!.click()
                assertTrue("The separate viewer must read the exact saved bytes with storage offline",
                    device.wait(Until.hasObject(By.text(before.get("sha256").asString)), 15_000))
                capture("file-viewer-bytes")
                device.pressBack()
                waitVisible("Remove offline copy")
                request("file-provider-start")
                val stillStored = request("file-received")
                assertEquals(before.get("sha256").asString, stillStored.get("sha256").asString)
                assertEquals(before.get("record").asString, stillStored.get("record").asString)
                assertEquals(before.get("lease").asString, stillStored.get("lease").asString)
                assertNotEquals(before.get("providerPid").asLong, stillStored.get("providerPid").asLong)
                assertEquals(1, stillStored.get("nativeDownloads").asInt)

                click("Remove offline copy")
                clickDialog("Remove offline copy", "Remove the copy saved on this device?")
                waitVisible("Keep an offline copy")
                assertEquals(before.get("used").asLong, request("file-received").get("used").asLong)
                capture("file-local-removal")

                click("Remove from Space")
                clickDialog("Remove from Space", "Remove this file from the shared library and storage?")
                waitVisible("No shared files yet", 25_000)
                val final = request("file-received")
                assertEquals(0, final.get("files").asInt)
                assertEquals(0, final.get("used").asInt)
                capture("file-shared-removal")
                File(evidence, "file-final.json").writeText(final.apply {
                    addProperty("recoveredPid", Process.myPid())
                    addProperty("originalPid", before.get("pid").asInt)
                }.toString())
            }
        }
    }

    private fun clickDialog(button: String, description: String) {
        ui.waitUntil(8000) { ui.onNodeWithText(description, substring = true).isDisplayed() }
        ui.onNode(hasText(button) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
    }

    private fun insertDocument(): Uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
        })).also { uri ->
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(sourceBytes) }
    }

    private fun digest() = MessageDigest.getInstance("SHA-256").digest(sourceBytes).joinToString("") { "%02x".format(it) }
    private fun text(value: String) = ui.onNodeWithText(value)
    private fun waitVisible(value: String, timeout: Long = 15000) = ui.waitUntil(timeout) { text(value).isDisplayed() }

    private fun click(label: String, timeout: Long = 13000, lazy: Boolean = false) {
        val button = ui.onNode(hasText(label) and hasClickAction() and isEnabled())
        if (lazy) {
            ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
        }
        ui.waitUntil(timeout) { button.isDisplayed() }
        button.performTouchInput { click() }
    }

    private fun capture(name: String) {
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(evidence, "$name.png").outputStream().use {
            check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
    }

    private fun capturing(name: String, action: () -> Unit) {
        try { action() } catch (failure: Throwable) {
            runCatching { capture(name) }.onFailure(failure::addSuppressed)
            throw failure
        }
    }

    private fun launch(link: String): ActivityScenario<MainActivity> {
        val peer = port("spacesPeerPort")
        return ActivityScenario.launch(Intent(Intent.ACTION_VIEW, Uri.parse(link), context, MainActivity::class.java)
            .putExtra("feedPeerUrl", "ws://127.0.0.1:$peer/wsopen")
            .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:$peer/wsopen")
            .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/"))
    }

    private fun request(path: String): JsonObject {
        val connection = URL("http://127.0.0.1:${port("spacesControlPort")}/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            require(connection.responseCode == 200)
            return connection.inputStream.use { source -> JsonParser.parseString(source.bufferedReader().readText()).asJsonObject }
        } finally { connection.disconnect() }
    }

    private fun port(name: String) = requireNotNull(arguments.getString(name)).toInt().also { require(it in 1024..65535) }

    private fun withProviderCertificate(action: () -> Unit) {
        val before = HttpsURLConnection.getDefaultSSLSocketFactory()
        val bytes = Base64.getDecoder().decode(requireNotNull(arguments.getString("spacesFileCertificate")))
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(bytes.inputStream())
        val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null); setCertificateEntry("owned-provider", certificate)
        }
        val managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, managers.trustManagers, null) }
        HttpsURLConnection.setDefaultSSLSocketFactory(ssl.socketFactory)
        try { action() } finally { HttpsURLConnection.setDefaultSSLSocketFactory(before) }
    }
}
