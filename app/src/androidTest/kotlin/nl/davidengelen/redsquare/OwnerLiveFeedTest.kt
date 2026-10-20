package nl.davidengelen.redsquare

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.RedSquareFeed
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoPeer

/** Separate, explicitly enabled public read. The harness clears only the emulator's .dev app first. */
class OwnerLiveFeedTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun freshInstallationReadsTheSelectedApprovedPublicFeed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(InstrumentationRegistry.getArguments().getString("publicFeedRead") == "1")
        val selected = InstrumentationRegistry.getArguments().getString("publicBackend") ?: "owner"
        require(selected == "owner" || selected == "official")
        val backend = if (selected == "official") SaitoBackend.OFFICIAL else SaitoBackend.OWNER
        // Setup through the real selection store before launch. The separate
        // controlled UI tests prove switching; this read must not see owner
        // content while a first official connection is still loading.
        AndroidBackendStore(instrumentation.targetContext).save(backend)
        val identity = Regex("[1-9A-HJ-NP-Za-km-z]{44,45}")
        val visibleAuthor = SemanticsMatcher("Visible Saito post author") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { identity.matches(it.text) } == true
        }
        val directory = File(instrumentation.targetContext.cacheDir, "live-feed-evidence").apply { mkdirs() }
        ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext, MainActivity::class.java)).use {
            try {
                ui.waitUntil(30_000) { ui.onAllNodes(visibleAuthor).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Node: ${backend.domain}").assertIsDisplayed()
                ui.onAllNodes(visibleAuthor)[0].assertIsDisplayed()
                ui.waitUntil(10_000) {
                    ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                }
                ui.onNodeWithText("Refresh").assertIsEnabled()
                ui.waitForIdle()
                instrumentation.waitForIdleSync()
                val feedImage = ui.onRoot().captureToImage().asAndroidBitmap()
                File(directory, "feed.png").outputStream().use {
                    check(feedImage.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                val visible = ui.onAllNodes(visibleAuthor).fetchSemanticsNodes().size
                File(directory, "result.txt").writeText("PASS: $visible visible author identities on the $selected fresh native feed.\nPublic read only; signature verification does not prove chain inclusion.\n")
                val wallet = AndroidWalletStore(instrumentation.targetContext).loadExisting()
                runBlocking {
                    withTimeout(40_000) {
                        SaitoPeer(wallet, backend.peerUrl).use { peer ->
                            for (stream in listOf("RedSquare", "RedSquareRetweet")) {
                                val criteria = JsonObject().apply {
                                    addProperty("request", "load"); addProperty("field1", stream)
                                    addProperty("flagged_ne", 1); addProperty("limit", 100)
                                    addProperty("created_earlier_than", System.currentTimeMillis() + 1)
                                }
                                val query = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                                val response = peer.request(query.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
                                // Observe the public RPC shape before strict decoding; never
                                // reinterpret an object/error response as an empty successful page.
                                runCatching {
                                    val bytes = response.toByteArray(Charsets.UTF_8)
                                    val shape = JsonObject().apply {
                                        addProperty("backend", backend.id); addProperty("stream", stream)
                                        addProperty("bytes", bytes.size)
                                        addProperty("sha256", java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) })
                                        addProperty("topLevel", when (response.firstOrNull { !it.isWhitespace() }) {
                                            '[' -> "array"; '{' -> "object"; else -> "other"
                                        })
                                        // Field names only, bounded and filtered; no remote values or post bodies.
                                        if (response.trimStart().startsWith("{")) {
                                            val names = com.google.gson.JsonParser.parseString(response).asJsonObject.keySet()
                                                .filter { it.matches(Regex("[A-Za-z0-9_]{1,64}")) }.sorted().take(16)
                                            add("objectFields", com.google.gson.JsonArray().apply { names.forEach { add(it) } })
                                        }
                                    }
                                    File(directory, "public-response-$stream.json").writeText(shape.toString())
                                }
                                val page = if (stream == "RedSquare") RedSquareFeed.readSearch(response)
                                    else RedSquareFeed.readCurrentQuotes(response)
                                File(directory, "public-search.txt").appendText(
                                    "PASS backend=${backend.id} observedAt=${System.currentTimeMillis()} " +
                                        "criteria=$criteria rows=${page.rowCount} posts=${page.posts.size} ignored=${page.ignored}\n")
                            }
                        }
                    }
                }
                // Separate read-only HTTPS integration: no controlled endpoint override or monetary send.
                withFailureCapture({ captureNativeScreen(File(directory, "wallet-first-failure.png")) }) {
                    ui.onNodeWithText("Wallet").performClick()
                    ui.onNodeWithContentDescription("Wallet address").assertTextEquals(wallet.publicKey)
                    ui.onNodeWithText("Balance and receive").performScrollTo().performClick()
                    ui.waitUntil(15_000) { ui.onNodeWithText("0 SAITO").isDisplayed() }
                    ui.onNodeWithText("Balance from ${backend.domain}").assertIsDisplayed()
                    ui.onNodeWithContentDescription("Receiving address").performScrollTo()
                        .assertTextEquals(wallet.publicKey)
                    captureNativeScreen(File(directory, "balance.png"), "Balance and receive")
                    ui.onNode(hasText("Done") and hasAnyAncestor(isDialog() and
                        hasAnyDescendant(hasContentDescription("Receiving address")))).performClick()
                    ui.onNodeWithText("Send SAITO").performScrollTo().performClick()
                    // Zero available funding requires the real options/genesis parameter and own-key snapshot.
                    ui.waitUntil(15_000) { ui.onNodeWithText("Available to send: 0 SAITO").isDisplayed() }
                    ui.onAllNodes(hasText("Pending. Inclusion has not been verified.")).assertCountEquals(0)
                    captureNativeScreen(File(directory, "funding.png"), "Available to send: 0 SAITO")
                    ui.onNode(hasText("Done") and hasAnyAncestor(isDialog() and
                        hasAnyDescendant(hasText("Available to send: 0 SAITO")))).performClick()
                    ui.onNodeWithText("Done").performClick()
                    ui.onNodeWithText("Node: ${backend.domain}").assertIsDisplayed()
                    File(directory, "public-wallet.txt").writeText(
                        "PASS backend=${backend.id} observedAt=${System.currentTimeMillis()} publicKey=${wallet.publicKey}\n" +
                            "Fresh unfunded native wallet: selected-server balance0SAITO and available funding0SAITO.\n" +
                            "Read-only HTTPS; no positive-funding, block-inclusion, consensus or transfer-admission claim.\n")
                }
                check(AndroidWalletStore(instrumentation.targetContext).loadExisting().publicKey == wallet.publicKey)
                check(AndroidBackendStore(instrumentation.targetContext).load() == backend)
            } finally {
                captureFeedEvidence("live-feed-evidence/screen.png")
            }
        }
    }
}
