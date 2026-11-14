package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoreRentalCreationPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun rentalCreationIsAnExplicitNativeChoiceWithoutPublishingOrMovingTheMaster() {
        withStoreSellerFixture("rental-entry") { scene, _, _ ->
            ui.openNativeStoreSelling()
            ui.onNodeWithText("New listing").performClick()
            ui.onNodeWithText("Create rental").performScrollTo().assertIsEnabled().performClick()
            ui.waitUntil(5_000) { ui.onNodeWithText("Rental duration (hours)").isDisplayed() }
            ui.onNodeWithText("The Vault master stays yours. Create a separate temporary access key.").assertIsDisplayed()
            assertTrue(scene.peer.transactions.isEmpty())
            captureNativeScreen(File(scene.evidence, "native-rental-creation-entry.png"))
        }
    }
    @Test fun aCreatorReviewsAndAuthenticatesSeparateRentalInventoryThenPublishesItsExactOffer() = withVaultDeviceCredential { device ->
        withStoreSellerFixture("rental-mint", expected = 2, rental = true) { scene, _, _ ->
            ui.openNativeStoreSelling()
            ui.onNodeWithText("New listing").performClick()
            ui.onNodeWithText("Create rental").performScrollTo().performClick()
            ui.onNodeWithText("Rental duration (hours)").performScrollTo().performTextReplacement("3")
            ui.onNodeWithText("Item title").performScrollTo().performTextReplacement("Native sale")
            ui.onNodeWithText("Description").performScrollTo().performTextReplacement("Three hours of protected field notes.")
            ui.onNodeWithText("Price per item (SAITO)").performScrollTo().performTextReplacement("5")
            ui.waitUntil(15_000) { ui.onAllNodesWithText("Upload notes.txt").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNodeWithText("Upload notes.txt").performScrollTo().performClick()
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
            val vector = context.assets.open("native-images-reference.json").reader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
            val image = vector.getAsJsonObject("images").get("red").asString
            val name = "rental-cover.bin"
            val uri = android.net.Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/$name")
            scene.context.contentResolver.call(uri, "prepare", name, android.os.Bundle().apply { putByteArray("bytes", java.util.Base64.getDecoder().decode(image.substringAfter(','))) })
            androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.content.Intent.ACTION_OPEN_DOCUMENT))
                .respondWith(android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, android.content.Intent().setData(uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            ui.onNodeWithText("Choose rental cover").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Review rental inventory") and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Review rental inventory").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Review rental inventory").isDisplayed() }
            ui.onNodeWithText("Inventory deposit: 1 SAITO").performScrollTo().assertIsDisplayed()
            assertTrue(scene.peer.transactions.isEmpty())
            ui.onNodeWithText("Create rental inventory").performClick()
            waitForSystemPin(device); assertTrue(scene.peer.transactions.isEmpty()); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Check rental inventory") and isEnabled()).isDisplayed() }
            val mint = requireNotNull(scene.seller?.image())
            assertEquals("store-nft-rental", mint.type); assertEquals(3, requireNotNull(mint.rental).hours)
            assertTrue(mint.inputs.none { it.block == java.math.BigInteger("2000002") })
            captureNativeScreen(File(scene.evidence, "rental-inventory-authenticated-master-retained.png"))
            requireNotNull(scene.seller).confirmImage.set(true)
            val observed = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                var balance: WalletBalances? = null
                scene.activity.onActivity { balance = WalletBalances.fromIntent(it.intent) }
                requireNotNull(balance).read(nl.davidengelen.redsquare.saito.SaitoBackend.OWNER, mint.author)
            }
            assertTrue(requireNotNull(observed).nfts.any { it.id == scene.reference.get("id").asString && it.type == "vault-nft-rental" })
            ui.onNodeWithText("Check rental inventory").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Continue listing") and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Continue listing").performClick()
            ui.waitUntil(15_000) { ui.onNode(hasText("Review sale offer") and isEnabled()).isDisplayed() }
            ui.onNodeWithText("Review sale offer").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText("Authorize and publish").isDisplayed() }
            ui.onNodeWithText("Authorize and publish").performClick()
            waitForSystemPin(device); assertEquals(1, scene.peer.transactions.size); enterRealSystemPin(device)
            ui.waitUntil(25_000) { ui.onNode(hasText("Check and retry original offer") and isEnabled()).isDisplayed() }
            val offered = storeSaleCandidate(scene)
            assertEquals(mint.nftId, offered.holding.id); assertEquals(mint.rental, offered.terms.rental)
            assertTrue(scene.peer.vaultUploads.isEmpty())
            captureNativeScreen(File(scene.evidence, "native-rental-original-delegation-offered.png"))
        }
    }
}
