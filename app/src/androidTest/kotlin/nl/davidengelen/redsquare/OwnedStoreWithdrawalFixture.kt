package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import nl.davidengelen.redsquare.saito.*
import java.util.concurrent.CopyOnWriteArrayList

/** Owned authenticated peer responses, anchored to independently executed actual Store/Mixin algorithms. */
internal class OwnedStoreWithdrawalFixture {
    private val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("native-store-seller-payments-reference.json")
        .reader().use { JsonParser.parseReader(it).asJsonObject }
    private val signer = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    @Volatile var settled = true
    @Volatile var quoteAge = 0L
    @Volatile var quotes = 0
    val submissions = CopyOnWriteArrayList<String>()
    fun reply(query: JsonObject, ordinary: (JsonObject) -> String): String = when (query.get("request").asString) {
        "load-web3-proceeds" -> reference.get(if (settled) "eligible" else "pending").toString()
        "store-withdrawal-quote" -> {
            quotes++
            val data = reference.getAsJsonObject("quote").getAsJsonObject("data").deepCopy().apply {
                addProperty("publickey", signer.publicKey); addProperty("timestamp", "${System.currentTimeMillis() - quoteAge}-$quotes")
                addProperty("withdrawal_address", query.getAsJsonObject("data").get("withdrawal_address").asString)
            }
            val sorted = JsonObject().apply { data.keySet().sorted().forEach { add(it, data.get(it)) } }
            JsonObject().apply { add("data", data); addProperty("signature", signer.sign(sorted.toString().toByteArray())) }.toString()
        }
        "store-withdrawal" -> {
            submissions.add(query.getAsJsonObject("data").get("transaction").asString)
            reference.get("submitted").toString()
        }
        else -> ordinary(query)
    }
    fun attach(scene: VaultReadingScene) {
        val ordinary = requireNotNull(scene.peer.storeResponse)
        scene.peer.storeResponse = { reply(it, ordinary) }
    }
}

internal fun ComposeTestRule.enterNativeStoreDestination(address: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    fun ime(): Boolean? {
        var visible: Boolean? = null
        instrumentation.runOnMainSync {
            val root = android.view.inspector.WindowInspector.getGlobalWindowViews().singleOrNull { it.isShown && it.hasWindowFocus() }
            visible = root?.let { androidx.core.view.ViewCompat.getRootWindowInsets(it) }?.let {
                it.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) || it.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom > 0
            }
        }
        return visible
    }
    onNodeWithText("Destination address").performScrollTo().performClick()
    waitUntil(15_000) { ime() == true }
    onNodeWithText("Destination address").assertIsFocused().performTextReplacement(address)
    onNodeWithText("Destination address").assertTextContains(address)
    androidx.test.uiautomator.UiDevice.getInstance(instrumentation).pressBack()
    waitUntil(5_000) { ime() == false }
    onNodeWithText("Destination address").assertTextContains(address)
}

internal fun ComposeTestRule.reviewNativeStoreWithdrawal() {
    onNodeWithText("Proceeds").performScrollTo().performClick()
    val start = android.os.SystemClock.elapsedRealtime()
    waitUntil(15_000) { onAllNodesWithText("Available to request: 6.25 USDT").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    onNodeWithText("Available to request: 6.25 USDT").performScrollTo().assertIsDisplayed()
    org.junit.Assert.assertTrue("The actual amount remains reachable within its original deadline", android.os.SystemClock.elapsedRealtime() - start < 15_000)
    onNodeWithText("Withdraw USDT").performScrollTo().performClick()
    enterNativeStoreDestination("0x1111111111111111111111111111111111111111")
    onNodeWithText("Review withdrawal").performScrollTo().performClick()
    waitUntil(15_000) { onNodeWithText("Requested payout: 6.25 USDT").isDisplayed() }
}
