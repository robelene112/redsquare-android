package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Genuine signed archive/file inputs over the actual shared native peer during live RTC work. */
internal class VaultStoreCoexistenceFixture {
    private fun asset(name: String) = InstrumentationRegistry.getInstrumentation().context.assets.open(name).reader().use {
        JsonParser.parseReader(it).asJsonObject
    }
    private val vault = asset("native-vault-reference.json")
    private val store = asset("native-store-listing-reference.json")
    val feed = asset("native-feed-scroll-reference.json").get("initialRows").toString()
    private val entered = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val replied = CountDownLatch(1)
    private val first = AtomicBoolean(true)
    private val workers = Executors.newCachedThreadPool()
    fun archive(query: JsonObject): String {
        val envelope = when {
            query.get("field4")?.asString == vault.get("id").asString -> vault.getAsJsonObject("key").get("envelope").asString
            query.get("sig")?.asString == store.getAsJsonObject("listing").get("id").asString -> store.getAsJsonObject("listing").get("envelope").asString
            else -> return if (query.get("field1")?.asString == "RedSquare") feed else "[]"
        }
        return JsonArray().apply { add(JsonObject().apply { addProperty("tx", envelope) }) }.toString()
    }
    fun configure(peer: ControlledFeedPeer) {
        peer.storeResponseExecutor = workers
        peer.storeResponse = {
            if (first.compareAndSet(true, false)) {
                entered.countDown()
                check(release.await(15, TimeUnit.SECONDS)) { "Owned stale Store response was not released" }
                replied.countDown()
            }
            store.get("page").toString()
        }
        peer.vaultResponse = { signed ->
            assertEquals(vault.get("owner").asString, signed.author)
            val data = signed.body.getAsJsonObject("data")
            assertEquals(vault.get("scriptHash").asString, data.get("access_hash").asString)
            assertEquals(vault.getAsJsonObject("script"), JsonParser.parseString(data.get("access_script").asString))
            JsonObject().apply { addProperty("status", "success"); add("txs", JsonArray().apply { add(vault.getAsJsonObject("file").get("envelope")) }) }.toString()
        }
    }
    suspend fun exercise(owner: SaitoWallet, endpoint: String, peer: ControlledFeedPeer, evidence: File) = coroutineScope {
        val cancelledStore = SaitoPeer(owner, endpoint)
        try {
            val pending = async { runCatching { cancelledStore.request(SaitoStore.catalogueRequest().toByteArray()) } }
            withTimeout(5_000) { while (entered.count != 0L) delay(20) }
            SaitoPeer(owner, endpoint).use { vaultPeer ->
                val snapshot = SaitoBalanceSnapshot.parse(vault.get("snapshot").asString.toByteArray(), owner.publicKey, System.currentTimeMillis())
                val holding = snapshot.nfts.single { it.id == vault.get("id").asString }
                val key = requireNotNull(SaitoVault.readKeyArchive(holding, vaultPeer.request(SaitoVault.archiveRequest(holding).toByteArray()).decodeToString()))
                val file = SaitoVault.readFile(key, vaultPeer.request(SaitoVault.access(owner, key, System.currentTimeMillis())) {}.decodeToString())
                assertEquals("Field notes.txt", file.name)
                assertArrayEquals(java.util.Base64.getDecoder().decode(vault.get("fileContentsBase64").asString), file.bytes)
                cancelledStore.close()
                withTimeout(5_000) { assertTrue(pending.await().isFailure) }
                // The old callback is emitted after its feature lease closes. It must not
                // satisfy the next feature's request or retire the authenticated socket.
                release.countDown()
                withTimeout(5_000) { while (replied.count != 0L) delay(20) }
                SaitoPeer(owner, endpoint).use { current ->
                    val page = SaitoStore.readCatalogue(current.request(SaitoStore.catalogueRequest().toByteArray()).decodeToString())
                    assertEquals("Field notes access key", page.items.single().title)
                    val product = requireNotNull(nl.davidengelen.redsquare.store.readStoreProduct(current, page.items.single().signature, page.items.single()))
                    assertEquals(page.items.single().nftId, product.nftId)
                    val request = JsonObject().apply {
                        addProperty("request", "archive"); add("data", JsonObject().apply {
                            addProperty("request", "load"); addProperty("field1", "RedSquare"); addProperty("field4", "")
                            addProperty("flagged_ne", 1); addProperty("limit", 20); addProperty("created_earlier_than", System.currentTimeMillis())
                        })
                    }
                    val posts = RedSquareFeed.readArchive(current.request(request.toString().toByteArray()).decodeToString()).posts
                    assertEquals(20, posts.size)
                    val like = SaitoTransaction.create(owner, JsonObject().apply {
                        addProperty("module", "RedSquare"); addProperty("request", "like tweet")
                        add("data", JsonObject().apply { addProperty("signature", posts.first().id) })
                    }, System.currentTimeMillis(), listOf(posts.first().author))
                    current.submit(like); current.finishBroadcast()
                    withTimeout(5_000) { while (peer.transactions.isEmpty()) delay(20) }
                    assertEquals(like.encoded(), postingTransaction(peer.transactions.single()).encoded())
                }
                assertEquals(1, peer.authenticatedConnectionIds.size)
                assertEquals(1, peer.connectionAttempts.get())
            }
            File(evidence, "vault-store-shared-channel.json").writeText(JsonObject().apply {
                addProperty("signedStoreOffer", true); addProperty("authenticatedVaultFile", true)
                addProperty("socialPosts", 20); addProperty("signedLikeDelivered", true)
                addProperty("cancelledStoreRequest", true); addProperty("lateStoreReplyRetired", true)
                addProperty("physicalConnections", peer.connectionAttempts.get())
                addProperty("qualification", "Owned authenticated transport during native Talk/FileSession; no public financial writes")
            }.toString())
        } finally { cancelledStore.close(); release.countDown() }
    }
    fun close() {
        release.countDown(); workers.shutdown(); check(workers.awaitTermination(5, TimeUnit.SECONDS))
    }
}
