package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mockwebserver3.*
import nl.davidengelen.redsquare.saito.*
import java.io.File
import java.net.InetAddress

internal data class StorePurchaseScene(val node: OwnedStorePurchaseFixture, val activity: ActivityScenario<MainActivity>, val evidence: File)

internal fun withStorePurchaseFixture(name: String, expectedTransactions: Int = 0, preserveOrders: Boolean = false, fullPurchase: Boolean = false, crypto: OwnedStoreCryptoFixture? = null, block: (StorePurchaseScene) -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    val vector = instrumentation.context.assets.open(if (fullPurchase) "native-store-full-purchase-reference.json" else "native-store-purchase-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject }
    val listing = instrumentation.context.assets.open("native-store-listing-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject }
    val wallets = AndroidWalletStore(context); val original = wallets.loadOrCreate()
    val buyer = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
    val state = ChatFixtureState(context, listOf("transfer.${buyer.publicKey}.", "store-orders.${buyer.publicKey}."), buyer.publicKey)
    val node = OwnedStorePurchaseFixture(vector, crypto?.let { owned -> { owned.payment } })
    crypto?.node = node
    var primary: Throwable? = null
    try {
        if (!preserveOrders) { state.prepare(); wallets.replace(original, buyer) }
        else check(wallets.loadExisting().publicKey == buyer.publicKey)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val chain = crypto?.chains?.get(request.target.removePrefix("/network/chains/"))
                    if (request.target.startsWith("/network/chains/") && chain != null) return MockResponse(body = chain.toString())
                    val response = node.response(request.target)
                    val held = node.blockReadGate
                    if (request.target.startsWith("/block/") && held != null && node.holdNextBlock.compareAndSet(true, false)) {
                        node.heldReplyStartedAt = android.os.SystemClock.elapsedRealtime()
                        node.blockedReads.incrementAndGet()
                        check(held.await(30, java.util.concurrent.TimeUnit.SECONDS))
                        node.completedHeldReplies.incrementAndGet()
                    }
                    return response
                }
            }
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            withAppFrameFixture(name, "store-purchase-evidence", feedRows = "[]", expectedTransactionCount = expectedTransactions,
                peerIdentity = SaitoWallet.fromPrivateKey("0".repeat(63) + "3"), advertisedServices = "archive||;Store||" + if (crypto == null) "" else ";Mixin||;buysaito||",
                balanceApiUrl = "http://127.0.0.1:${server.port}/balance/",
                storeCryptoNetworksApi = crypto?.let { "http://127.0.0.1:${server.port}/network/chains/" }, archiveResponse = { criteria ->
                    if (criteria.get("sig")?.asString == listing.getAsJsonObject("listing").get("id").asString)
                        JsonArray().apply { add(JsonObject().apply { addProperty("tx", listing.getAsJsonObject("listing").get("envelope").asString) }) }.toString()
                    else "[]"
                }, configurePeer = { peer ->
                    node.peer = peer
                    peer.cryptoResponse = crypto?.let { owned -> { message -> owned.reply(message) } }
                    peer.storeResponse = { query -> if (query.get("request").asString == "load-listing")
                        JsonObject().apply { add("listing", listing.getAsJsonObject("page").getAsJsonArray("listings")[0]) }.toString()
                        else listing.get("page").toString() }
                }) { activity -> block(StorePurchaseScene(node, activity, File(context.cacheDir, "store-purchase-evidence").apply { mkdirs() })) }
        }
    } catch (failure: Throwable) { primary = failure; throw failure }
    finally { if (!preserveOrders) cleanupAfterTest(primary, { state.restoreFiles() }, { state.restorePreferences() }, {
        val current = wallets.loadExisting(); if (current.exportJson() != original.exportJson()) wallets.replace(current, original)
        check(wallets.loadExisting().exportJson() == original.exportJson())
    }) }
}

internal fun ComposeTestRule.openStorePurchaseProduct(quantity: String = "1") {
    waitUntil(15_000) { onNodeWithText("Later").isDisplayed() }; onNodeWithText("Later").performClick()
    onNodeWithContentDescription("Switch app: Red Square").performClick()
    onNodeWithText("Store").performScrollTo().performClick()
    waitUntil(15_000) { onNodeWithText("Field notes access key").isDisplayed() }
    onNodeWithText("Field notes access key").performClick()
    waitUntil(15_000) { onNodeWithText("Original creator-authenticated field notes.").isDisplayed() }
    if (quantity != "1") onNodeWithText("Quantity").performScrollTo().performTextReplacement(quantity)
    onNodeWithText("Review purchase").performScrollTo().performClick()
}
