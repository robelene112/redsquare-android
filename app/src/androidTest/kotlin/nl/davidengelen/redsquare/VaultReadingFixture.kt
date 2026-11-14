package nl.davidengelen.redsquare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mockwebserver3.*
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import java.io.File
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

internal data class VaultReadingScene(val activity: ActivityScenario<MainActivity>, val peer: ControlledFeedPeer,
    val reference: JsonObject, val ownsKey: AtomicBoolean, val evidence: File, val context: Context, val destination: Uri, val mint: OwnedVaultMintFixture? = null, val balanceRequests: () -> Int = { 0 }, val transfer: OwnedVaultKeyTransferFixture? = null, val seller: OwnedStoreSellerFixture? = null)

/** The existing owned peer, HTTP balance, document provider and native Activity boundaries. */
internal fun withVaultReadingFixture(name: String, services: String = "archive||;vault||", inventory: Boolean = false, captureFolder: String = "vault-reading-evidence", large: Boolean = false, failedExport: Boolean = false, retainTransfers: Boolean = false, minting: Boolean = false, expectedMintCount: Int = if (minting) 1 else 0, preserveVault: Boolean = false, priorMint: String? = null, custom: Boolean = false, keyTransfer: Boolean = false, rentalMint: Boolean = false, balanceReplyGate: java.util.concurrent.CountDownLatch? = null, sellerImage: Boolean = false, sellerListed: Boolean = false, sellerRemaining: Boolean = false, preserveSeller: Boolean = false, storeCrypto: OwnedStoreCryptoFixture? = null, block: (VaultReadingScene) -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    val originalReference = instrumentation.context.assets.open("native-vault-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject }
    val vector = if (large) vaultLargeReference(originalReference) else if (custom) vaultCustomReference(originalReference) else originalReference
    val transferred = if (keyTransfer) {
        val transferReference = instrumentation.context.assets.open("native-vault-transfer-reference.json").reader()
            .use { JsonParser.parseReader(it).asJsonObject }
        OwnedVaultKeyTransferFixture(vector, transferReference)
    } else null
    require(!rentalMint || minting)
    val sellerNode = if (sellerImage || sellerListed) OwnedStoreSellerFixture(vector,
        instrumentation.context.assets.open("native-store-listing-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject },
        instrumentation.context.assets.open(if (sellerRemaining) "native-store-remaining-delist-reference.json" else "native-store-delist-reference.json").reader().use { JsonParser.parseReader(it).asJsonObject }, sellerImage, sellerListed) else null
    val wallets = AndroidWalletStore(context)
    val original = wallets.loadOrCreate()
    val fixture = if (preserveSeller) original.also { check(it.publicKey == SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey) } else SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture identity. Never fund.
    assertEquals(vector.get("owner").asString, fixture.publicKey)
    val document = if (failedExport) "vault-write-fails.bin" else "vault-saved.bin"
    val destination = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/$document")
    val evidence = File(context.cacheDir, captureFolder).apply { mkdirs() }
    val inventoryRows = if (inventory) vector.getAsJsonArray("inventory").map { it.asJsonObject }
        else listOf(vector)
    val ownsKey = AtomicBoolean(true)
    var failure: Throwable? = null
    var intentsStarted = false
    var transfers: ChatFixtureState? = null
    val minted = if (minting) {
        val mintReference = instrumentation.context.assets.open("native-vault-mint-reference.json").reader()
            .use { JsonParser.parseReader(it).asJsonObject }
        if (rentalMint) {
            val rental = mintReference.getAsJsonArray("advanced").map { it.asJsonObject }.single { it.get("type").asString == "rental" }
            mintReference.addProperty("nftId", rental.get("nftId").asString)
            mintReference.add("file", rental.getAsJsonObject("file").deepCopy())
            mintReference.add("mint", rental.getAsJsonObject("mint").deepCopy())
            val rights = instrumentation.context.assets.open("native-vault-rental-reference.json").reader()
                .use { JsonParser.parseReader(it).asJsonObject }
            mintReference.add("masterCreatorAccepted", rights.get("masterCreatorAccepted"))
        }
        OwnedVaultMintFixture(mintReference, fixture.publicKey, ownsKey,
            priorMint?.let(nl.davidengelen.redsquare.saito.SaitoVaultMint::readCandidate))
    } else null
    try {
        if (!preserveSeller) wallets.replace(original, fixture)
        if (retainTransfers) {
            val retained = ChatFixtureState(context, listOf("transfer.${fixture.publicKey}."), fixture.publicKey)
            transfers = retained
            retained.prepare()
        }
        context.contentResolver.call(destination, "prepare", document, Bundle().apply { putByteArray("bytes", byteArrayOf()) })
        Intents.init(); intentsStarted = true
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(android.app.Instrumentation.ActivityResult(Activity.RESULT_OK,
            Intent().setData(destination).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)))
        MockWebServer().use { balance ->
            balance.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val chain = storeCrypto?.chains?.get(request.target.removePrefix("/network/chains/"))
                    if (request.target.startsWith("/network/chains/") && chain != null) return MockResponse(body = chain.toString())
                    sellerNode?.let { return it.response(request.target) }
                    minted?.let { return it.response(request.target) }
                    transferred?.let { return it.response(request.target) }
                    if (request.target == "/options") return MockResponse(body = "{\"consensus\":{\"genesis_period\":10000000}}")
                    if (request.target != "/balance/${fixture.publicKey}") return MockResponse(code = 404)
                    val originalSnapshot = vector.get("snapshot").asString
                    val ordinary = originalSnapshot.lines().take(2).joinToString("\n") + "\n"
                    val full = if (inventory) ordinary + inventoryRows.flatMap { it.getAsJsonArray("snapshotRows").map { line -> line.asString } }.joinToString("\n") + "\n" else originalSnapshot
                    val snapshot = (if (ownsKey.get()) full else ordinary).replaceBefore('-', System.currentTimeMillis().toString())
                    balanceReplyGate?.let { check(it.await(30, java.util.concurrent.TimeUnit.SECONDS)) { "Owned balance reply was not released" } }
                    return MockResponse(body = snapshot)
                }
            }
            balance.start(InetAddress.getByName("127.0.0.1"), 0)
            lateinit var referencePeer: ControlledFeedPeer
            withAppFrameFixture("vault-$name", evidence.name, expectedTransactionCount = expectedMintCount, preserveVault = preserveVault, beforeStateRestore = { endpoint ->
                cleanupAfterTest(null, { kotlinx.coroutines.runBlocking {
                    nl.davidengelen.redsquare.vault.VaultUploads.closeAndJoin(fixture.publicKey,
                        nl.davidengelen.redsquare.saito.SaitoBackend.OWNER, endpoint)
                } }, { minted?.close() })
            }, balanceApiUrl = "http://127.0.0.1:${balance.port}/balance/",
                storeCryptoNetworksApi = storeCrypto?.let { "http://127.0.0.1:${balance.port}/network/chains/" },
                advertisedServices = services, peerIdentity = SaitoWallet.fromPrivateKey("0".repeat(63) + if (sellerNode != null) "3" else "1"), configurePeer = { peer ->
                    referencePeer = peer
                    sellerNode?.connect(peer)
                    minted?.connect(peer)
                    transferred?.connect(peer)
                    if (minted == null) peer.vaultResponse = accessResponse@ { signed ->
                        val data = signed.body.getAsJsonObject("data")
                        val selected = inventoryRows.single { it.getAsJsonObject("file").get("id").asString == data.getAsJsonObject("data").get("file_id").asString }
                        assertEquals(selected.get("scriptHash").asString, data.get("access_hash").asString)
                        val submitted = JsonParser.parseString(data.get("access_script").asString).asJsonObject
                        if (custom) {
                            val locking = submitted.deepCopy().apply { remove("witness") }
                            assertEquals(selected.getAsJsonObject("script"), locking)
                            val input = submitted.getAsJsonObject("witness").get("input").asString
                            val decision = requireNotNull(selected.getAsJsonObject("custom").getAsJsonArray("decisions")
                                .map { it.asJsonObject }.singleOrNull { it.get("input").asString == input }) {
                                "An unplanned custom input reached the owned reference provider"
                            }
                            if (decision.get("accepted").asInt != 1) return@accessResponse "{\"status\":\"err\",\"err\":\"access_denied_script_failed\"}"
                        } else assertEquals(selected.getAsJsonObject("script"), submitted)
                        JsonObject().apply {
                            addProperty("status", "success")
                            add("txs", com.google.gson.JsonArray().apply { add(selected.getAsJsonObject("file").get("envelope").asString) })
                        }.toString()
                    }
                }, archiveResponse = { criteria ->
                    if (sellerNode != null) sellerNode.archive(criteria) else if (minted != null) minted.archive(criteria) else {
                    val selected = inventoryRows.firstOrNull { it.get("id").asString == criteria.get("field4")?.asString }
                    if (selected != null) "[" + JsonObject().apply { addProperty("tx", selected.getAsJsonObject("key").get("envelope").asString) } + "]"
                    else "[]"
                    }
                }) { activity ->
                    try { block(VaultReadingScene(activity, referencePeer, vector, ownsKey, evidence, context, destination, minted, { balance.requestCount }, transferred, sellerNode)) }
                    catch (problem: Throwable) {
                        File(evidence, "$name-original-failure.txt").writeText(problem.stackTraceToString())
                        throw problem
                    }
                    finally {
                        balanceReplyGate?.countDown()
                        if (minted != null) File(evidence, "owned-protocol-facts.json").writeText(JsonObject().apply {
                            addProperty("mintFrames", referencePeer.transactions.size)
                            addProperty("fileUploads", referencePeer.vaultUploads.size)
                            addProperty("originalCandidateObserved", minted.candidate() != null)
                            addProperty("nodeReportsOwnership", ownsKey.get())
                            addProperty("peerAcceptedRequests", runCatching { referencePeer.assertHealthy(); true }.getOrDefault(false))
                        }.toString())
                    }
                }
        }
    } catch (problem: Throwable) { failure = problem; throw problem }
    finally {
        cleanupAfterTest(failure, { if (intentsStarted) Intents.release() }, { minted?.close() },
            { transfers?.restoreFiles() }, { transfers?.restorePreferences() }, {
            val current = wallets.loadExisting()
            val fixturePreserved = current.exportJson() == fixture.exportJson()
            if (current.exportJson() != original.exportJson()) wallets.replace(current, original)
            check(wallets.loadExisting().exportJson() == original.exportJson())
            check(fixturePreserved) { "Vault journey changed the active fixture identity or options" }
        })
    }
}

internal fun ComposeTestRule.openNativeVault() {
    waitUntil(15_000) { onNodeWithText("Later").isDisplayed() }
    onNodeWithText("Later").performClick()
    switchStackModule("Red Square", "Vault", scrollTarget = true)
}
