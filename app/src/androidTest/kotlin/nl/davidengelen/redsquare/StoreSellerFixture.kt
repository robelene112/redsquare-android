package nl.davidengelen.redsquare

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
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

internal fun androidx.compose.ui.test.junit4.ComposeTestRule.openNativeStoreSelling() {
        waitUntil(15_000) { onNodeWithText("Later").isDisplayed() }; onNodeWithText("Later").performClick()
        onNodeWithContentDescription("Switch app: Red Square").performClick()
        onNodeWithText("Store").performScrollTo().performClick()
        waitUntil(15_000) { onNodeWithText("My store").isDisplayed() }
        onNodeWithText("My store").performClick()
        waitUntil(15_000) { onNode(hasText("New listing") and isEnabled()).isDisplayed() }
    }
internal fun androidx.compose.ui.test.junit4.ComposeTestRule.reviewNativeStoreSale() {
        onNodeWithText("New listing").performClick()
        onNodeWithText("Item title").performScrollTo().performTextReplacement("Native sale")
        onNodeWithText("Description").performScrollTo().performTextReplacement("A genuine owned key, offered from Android.")
        onNodeWithText("Price per item (SAITO)").performScrollTo().performTextReplacement("5")
        waitUntil(15_000) { onAllNodesWithText("Field notes.txt").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        onNodeWithText("Field notes.txt").performScrollTo().performClick()
        onNodeWithText("Review sale offer").performScrollTo().assertIsEnabled().performClick()
        waitUntil(15_000) { onAllNodesWithText("Authorize and publish").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
internal fun storeSaleCandidate(scene: VaultReadingScene): SaitoStoreListing {
        val listings = scene.peer.transactions.mapNotNull { frame ->
            val wire = frame.drop(1).toByteArray(); val header = ByteBuffer.wrap(wire)
            val length = 93 + 59 * (header.getInt(0) + header.getInt(4))
            val body = JsonParser.parseString(wire.copyOfRange(length, length + header.getInt(8)).decodeToString()).asJsonObject
            if (body.get("request")?.asString != "list-asset") null else {
                val bytes = wire.copyOf(length + header.getInt(8)); ByteBuffer.wrap(bytes).putInt(12, 0)
                SaitoStoreListing.restore(Base64.getEncoder().encodeToString(bytes))
            }
        }
        return listings.distinctBy { it.transaction.encoded() }.single()
    }
internal fun withStoreSellerFixture(name: String, expected: Int = 0, minting: Boolean = false, image: Boolean = false, prelisted: Boolean = false, remaining: Boolean = false, preserve: Boolean = false, crypto: OwnedStoreCryptoFixture? = null, block: (VaultReadingScene, AtomicBoolean, AtomicBoolean) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
        val saved = ChatFixtureState(context, listOf("store-listings.$owner.", "store-sale-draft.$owner.", "store-delists.$owner.", "store-images.$owner."), owner)
        var primary: Throwable? = null
        try {
            if (!preserve) saved.prepare()
            withVaultReadingFixture("store-sale-$name", services = "archive||;vault||;Store||" + if (crypto == null) "" else ";Mixin||;buysaito||", captureFolder = "store-seller-evidence", retainTransfers = !preserve, expectedMintCount = expected, minting = minting, sellerImage = image, sellerListed = prelisted, sellerRemaining = remaining, preserveSeller = preserve) { scene ->
                scene.peer.cryptoResponse = crypto?.let { owned -> { signed -> owned.reply(signed) } }
                val report = AtomicBoolean(prelisted); val approved = AtomicBoolean(false); val submitted = AtomicBoolean(false)
                val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("native-store-listing-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject }
                val removal = if (remaining) InstrumentationRegistry.getInstrumentation().context.assets.open("native-store-remaining-delist-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject } else null
                scene.peer.storeResponse = { query ->
                    val data = query.getAsJsonObject("data")
                    when (query.get("request").asString) {
                        "load-seller-inventory" -> JsonObject().apply {
                            addProperty("seller", owner)
                            add("active", JsonArray().apply { if (report.get() && scene.seller?.confirmDelist?.get() != true) {
                                val original = if (prelisted) null else storeSaleCandidate(scene)
                                add(reference.getAsJsonObject("page").getAsJsonArray("listings")[0].asJsonObject.deepCopy().apply {
                                    addProperty("seller", owner); addProperty("listing_signature", original?.transaction?.id ?: removal?.getAsJsonObject("priorFulfillment")?.get("id")?.asString ?: reference.getAsJsonObject("listing").get("id").asString); addProperty("title", if (prelisted) "Field notes access key" else "Native sale")
                                    addProperty("price", if (prelisted) 250000000 else 500000000); addProperty("quantity_total", if (prelisted && !remaining) 2 else 1); addProperty("quantity_available", if (prelisted && !remaining) 2 else 1)
                                    addProperty("approved", if (approved.get()) 1 else if (submitted.get()) 2 else 0)
                                })
                            } }); add("sold", JsonArray())
                        }.toString()
                        "load-listings" -> reference.getAsJsonObject("page").deepCopy().apply {
                            addProperty("public_key", data.get("public_key").asString); addProperty("category", data.get("category").asString)
                        }.toString()
                        "load-listing-spend" -> {
                            check(prelisted)
                            if (scene.seller?.confirmDelist?.get() == true) "null" else removal?.get("row")?.toString() ?: InstrumentationRegistry.getInstrumentation().context.assets.open("native-store-delist-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject.get("row").toString() }
                        }
                        "submit-listing" -> JsonObject().also { submitted.set(true) }.apply { addProperty("ok", true); addProperty("signature", data.get("signature").asString); addProperty("approved", if (approved.get()) 1 else if (submitted.get()) 2 else 0) }.toString()
                        else -> error("Unexpected owned Store request")
                    }
                }
                block(scene, report, approved)
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { if (!preserve) cleanupAfterTest(primary, { saved.restoreFiles() }, { saved.restorePreferences() }) }
    }
