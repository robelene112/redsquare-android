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

class StoreSellerCreationPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun aNewFileUsesVaultInlineAndKeepsTheListingDraftThroughMintAndUpload() = withVaultDeviceCredential { device ->
        withStoreSellerFixture("create-file", expected = 2, minting = true) { scene, _, _ ->
            ui.openNativeStoreSelling()
            ui.onNodeWithText("New listing").performClick()
            ui.onNodeWithText("Item title").performTextReplacement("Native sale")
            ui.onNodeWithText("Description").performTextReplacement("A newly created file access key.")
            ui.onNodeWithText("Price per item (SAITO)").performTextReplacement("5")
            val uri = android.net.Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/store-new-file.bin")
            val bytes = "Native Store file creation\n".toByteArray()
            scene.context.contentResolver.call(uri, "prepare", "store-new-file.bin", android.os.Bundle().apply { putByteArray("bytes", bytes) })
            androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.content.Intent.ACTION_OPEN_DOCUMENT))
                .respondWith(android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, android.content.Intent().setData(uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            ui.onNodeWithText("Create file access key").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Review upload").isDisplayed() }
            ui.onNodeWithText("Create key and upload").performScrollTo().performClick()
            waitForSystemPin(device); assertTrue(scene.peer.transactions.isEmpty()); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNodeWithText("Continue listing").isDisplayed() }
            assertEquals(1, scene.peer.transactions.size); assertEquals(1, scene.peer.vaultUploads.size)
            val minted = requireNotNull(scene.mint?.candidate())
            captureNativeScreen(File(scene.evidence, "new-file-vault-key-complete-inline.png"))
            ui.onNodeWithText("Continue listing").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("store-new-file.bin").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("store-new-file.bin").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Item title").performScrollTo().assertTextContains("Native sale")
            ui.onNodeWithText("Review sale offer").performScrollTo().assertIsEnabled().performClick()
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Authorize and publish").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Authorize and publish").performClick()
            waitForSystemPin(device); assertEquals(1, scene.peer.transactions.size); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Check and retry original offer") and isEnabled()).isDisplayed() }
            assertEquals(2, scene.peer.transactions.size); assertEquals(1, scene.peer.vaultUploads.size)
            val listed = storeSaleCandidate(scene)
            assertEquals(minted.nftId, listed.holding.id)
            assertEquals(minted.fileId, SaitoStoreListing.metadata(listed.holding, listed.transaction.envelope()).getAsJsonObject("data").get("file_id").asString)
            captureNativeScreen(File(scene.evidence, "new-file-original-key-listed-with-draft-intact.png"))
        }
    }
    @Test fun aPublicImageMintRecoversItsOriginalAndThenBecomesARealSaleOffer() = withVaultDeviceCredential { device ->
        withStoreSellerFixture("create-image", expected = 2, image = true) { scene, _, _ ->
            ui.openNativeStoreSelling()
            ui.onNodeWithText("New listing").performClick()
            ui.onNodeWithText("Item title").performTextReplacement("Native sale")
            ui.onNodeWithText("Description").performTextReplacement("A public image created natively.")
            ui.onNodeWithText("Price per item (SAITO)").performTextReplacement("5")
            val reference = InstrumentationRegistry.getInstrumentation().context.assets.open("native-images-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject }
            val bytes = Base64.getDecoder().decode(reference.getAsJsonObject("images").get("red").asString.substringAfter(','))
            val name = "store-image-item.bin"
            val uri = android.net.Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/$name")
            scene.context.contentResolver.call(uri, "prepare", name, android.os.Bundle().apply { putByteArray("bytes", bytes) })
            androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.content.Intent.ACTION_OPEN_DOCUMENT))
                .respondWith(android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, android.content.Intent().setData(uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            ui.onNodeWithText("Create image NFT").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Review image creation") and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Review image creation").performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Authorize and create image NFT").isDisplayed() }
            assertTrue(scene.peer.transactions.isEmpty()); assertTrue(scene.peer.vaultUploads.isEmpty())
            captureNativeScreen(File(scene.evidence, "public-image-review-before-authorization.png"))
            scene.peer.disconnectAfterTransaction = true
            ui.onNodeWithText("Authorize and create image NFT").performClick()
            waitForSystemPin(device); assertTrue(scene.peer.transactions.isEmpty()); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Retry original image creation") and isEnabled()).isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            val node = requireNotNull(scene.seller); val mint = requireNotNull(node.image())
            assertEquals(reference.getAsJsonObject("images").get("red").asString, mint.image)
            scene.activity.recreate()
            ui.waitUntil(15_000) { ui.onNode(hasText("New listing") and isEnabled()).isDisplayed() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText("Native sale")); ui.onNodeWithText("Native sale").performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Check image creation").isDisplayed() }
            assertEquals(mint.transaction.encoded(), requireNotNull(node.image()).transaction.encoded())
            node.confirmImage.set(true); scene.peer.disconnectAfterTransaction = false
            ui.onNodeWithText("Check image creation").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Continue listing") and isEnabled()).isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            captureNativeScreen(File(scene.evidence, "original-image-inventory-observed-after-recreation.png"))
            ui.onNodeWithText("Continue listing").performClick()
            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Review sale offer") and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Review sale offer").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Authorize and publish").isDisplayed() }
            ui.onNodeWithText("Authorize and publish").performClick()
            waitForSystemPin(device); assertEquals(1, scene.peer.transactions.size); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Check and retry original offer") and isEnabled()).isDisplayed() }
            assertEquals(2, scene.peer.transactions.size); assertTrue(scene.peer.vaultUploads.isEmpty())
            val sale = storeSaleCandidate(scene)
            assertEquals(mint.nftId, sale.holding.id); assertEquals("image", sale.holding.type)
            captureNativeScreen(File(scene.evidence, "real-native-image-offer-submitted.png"))
        }
    }
}
