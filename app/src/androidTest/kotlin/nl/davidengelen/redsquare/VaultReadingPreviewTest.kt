package nl.davidengelen.redsquare

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.util.Base64

/** Native file/peer/document seams with an independent pinned WASM reference; no live writes. */
class VaultReadingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aRealKeyDownloadsExactBytesAndItsOfflineCopySurvivesRecreationWithoutAWorkingNode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val vector = instrumentation.context.assets.open("native-vault-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject }
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val fixture = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Published unsafe identity. Never fund.
        assertEquals(vector.get("owner").asString, fixture.publicKey)
        val destination = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/vault-saved.bin")
        val evidence = File(context.cacheDir, "vault-reading-evidence").apply { mkdirs() }
        var failure: Throwable? = null
        try {
            wallets.replace(original, fixture)
            context.contentResolver.call(destination, "prepare", "vault-saved.bin", Bundle().apply { putByteArray("bytes", byteArrayOf()) })
            Intents.init()
            Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(android.app.Instrumentation.ActivityResult(Activity.RESULT_OK,
                Intent().setData(destination).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)))
            MockWebServer().use { balance ->
                balance.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        if (request.target != "/balance/${fixture.publicKey}") return MockResponse(code = 404)
                        val snapshot = vector.get("snapshot").asString.replaceBefore('-', System.currentTimeMillis().toString())
                        return MockResponse(body = snapshot)
                    }
                }
                balance.start(InetAddress.getByName("127.0.0.1"), 0)
                withAppFrameFixture("vault-reading", "vault-reading-evidence", balanceApiUrl = "http://127.0.0.1:${balance.port}/balance/",
                    advertisedServices = "archive||;vault||", configurePeer = { peer ->
                        peer.vaultResponse = { signed ->
                            val data = signed.body.getAsJsonObject("data")
                            assertEquals(vector.get("scriptHash").asString, data.get("access_hash").asString)
                            assertEquals(vector.getAsJsonObject("script"), JsonParser.parseString(data.get("access_script").asString))
                            JsonObject().apply {
                                addProperty("status", "success")
                                add("txs", com.google.gson.JsonArray().apply { add(vector.getAsJsonObject("file").get("envelope").asString) })
                            }.toString()
                        }
                    }, archiveResponse = { criteria ->
                        if (criteria.get("field4")?.asString == vector.get("id").asString) {
                            "[" + JsonObject().apply { addProperty("tx", vector.getAsJsonObject("key").get("envelope").asString) } + "]"
                        } else "[]"
                    }) { activity ->
                    ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
                    ui.onNodeWithText("Later").performClick()
                    ui.switchStackModule("Red Square", "Vault")
                    ui.waitUntil(15_000) { ui.onNodeWithText("Field notes.txt").isDisplayed() }
                    captureNativeScreen(File(evidence, "key.png"))
                    ui.onNodeWithText("Keep offline").performScrollTo().performClick()
                    ui.waitUntil(15_000) { ui.onNodeWithText("Available offline").isDisplayed() }
                    captureNativeScreen(File(evidence, "offline-saved.png"))
                    activity.onActivity {
                        it.intent.putExtra("feedPeerUrl", "ws://127.0.0.1:1/wsopen")
                        it.intent.putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
                    }
                    activity.recreate()
                    ui.waitUntil(15_000) { ui.onNodeWithText("Available offline").isDisplayed() }
                    ui.onNodeWithText("Save as…").performScrollTo().performClick()
                    ui.waitUntil(15_000) { ui.onNodeWithText("File saved").isDisplayed() }
                    val saved = context.contentResolver.openInputStream(destination)!!.use { it.readBytes() }
                    assertArrayEquals(Base64.getDecoder().decode(vector.get("fileContentsBase64").asString), saved)
                    File(evidence, "exported-file.bin").writeBytes(saved)
                    captureNativeScreen(File(evidence, "offline-recreated.png"))
                    ui.onNodeWithText("Remove offline copy").performScrollTo().performClick()
                    ui.waitUntil(10_000) { ui.onNodeWithText("Offline copy removed").isDisplayed() }
                    ui.onAllNodesWithText("Available offline").assertCountEquals(0)
                    captureNativeScreen(File(evidence, "offline-removed.png"))
                }
            }
        } catch (problem: Throwable) { failure = problem; throw problem }
        finally {
            cleanupAfterTest(failure, { Intents.release() }, {
                val current = wallets.loadExisting()
                if (current.exportJson() == fixture.exportJson()) wallets.replace(current, original)
                check(wallets.loadExisting().exportJson() == original.exportJson())
            })
        }
    }
}
