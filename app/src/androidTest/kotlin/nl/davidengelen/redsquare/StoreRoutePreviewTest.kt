package nl.davidengelen.redsquare

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreRoutePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun feedLinksAndBothPlatformRoutesReturnToTheirExactOriginWithoutSelectingANode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val reference = instrumentation.context.assets.open("native-store-listing-reference.json")
            .reader().use { JsonParser.parseReader(it).asJsonObject }
        val seller = reference.get("seller").asString
        val id = reference.get("nftId").asString
        val signature = reference.getAsJsonObject("listing").get("id").asString
        val modern = "https://saito.io/store/$seller/$id"
        val legacy = "https://saito.io/store/listing/$signature"
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val transaction = SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "RedSquare"); addProperty("request", "create tweet")
            add("data", JsonObject().apply { addProperty("text", "Exact Store offer\n$modern") })
        }, 1_700_000_000_000)
        val rows = "[${postingArchiveRow(transaction)}]"
        lateinit var peer: ControlledFeedPeer
        withAppFrameFixture("store-routes", "store-route-evidence", feedRows = rows,
            advertisedServices = "archive||;Store||", mapOfficialPeer = true,
            archiveResponse = { query ->
                if (query.get("sig")?.asString == signature) JsonArray().apply {
                    add(JsonObject().apply { addProperty("tx", reference.getAsJsonObject("listing").get("envelope").asString) })
                }.toString() else if (query.get("field1")?.asString == "RedSquare") rows else "[]"
            }, configurePeer = { owned ->
                peer = owned
                owned.storeResponse = { request ->
                    assertEquals("load-listing", request.get("request").asString)
                    val data = request.getAsJsonObject("data")
                    assertTrue(data.get("signature")?.asString == signature ||
                        (data.get("seller")?.asString == seller && data.get("nft_id")?.asString == id))
                    JsonObject().apply { add("listing", reference.getAsJsonObject("page").getAsJsonArray("listings")[0]) }.toString()
                }
            }) { scenario ->
            for (url in listOf(modern, legacy)) {
                val resolved = context.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(context.packageName),
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
            }
            ui.waitUntil(15_000) { ui.onNodeWithText("Later").isDisplayed() }
            ui.onNodeWithText("Later").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Exact Store offer").isDisplayed() }
            ui.onNodeWithText("Open in Store").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Original creator-authenticated field notes.").isDisplayed() }
            ui.onNodeWithText("Node: saito.io").assertIsDisplayed()
            assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
            captureNativeScreen(File(context.cacheDir, "store-route-evidence/foreign-node-native-product.png"))
            ui.onNodeWithText("Back").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Exact Store offer").isDisplayed() }
            ui.onNodeWithContentDescription("Switch app: Red Square").assertIsDisplayed()
            ui.onNodeWithText("Your profile").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Edit profile").isDisplayed() }
            for (url in listOf(modern, legacy)) {
                scenario.onActivity { activity -> activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .setClass(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
                ui.waitUntil(15_000) { ui.onNodeWithText("Original creator-authenticated field notes.").isDisplayed() }
                assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(15_000) { ui.onNodeWithText("Edit profile").isDisplayed() }
            }
            assertTrue(peer.transactions.isEmpty()); assertTrue(peer.vaultUploads.isEmpty())
            assertEquals(wallet.exportJson(), AndroidWalletStore(context).loadExisting().exportJson())
            captureNativeScreen(File(context.cacheDir, "store-route-evidence/profile-origin-restored.png"))
        }
    }
}
