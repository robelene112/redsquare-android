package nl.davidengelen.redsquare.vault

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.store.readStoreProduct

/** Read public signed Store history for a known master. This does not establish current provider access. */
internal suspend fun creatorRentalHistory(context: Context, wallet: SaitoWallet, backend: SaitoBackend,
    endpoint: String, key: VaultKey, current: () -> Unit): List<SaitoVaultRental> = withContext(Dispatchers.IO) {
    require(key.holding.type == "vault-nft-rental" && key.holding.creator == wallet.publicKey)
    current()
    val local = StoreListingStore(context, wallet.publicKey).read().filter { it.backend == backend && it.listing.terms.rental?.fileId == key.fileId }
        .associate { it.listing.transaction.id to SaitoStoreLineage.readSaved(it.listing.transaction.id, it.listing.transaction.envelope(), emptyList()) }
    SaitoPeer(wallet, endpoint).use { peer ->
        val active = currentCoroutineContext().job
        suspend fun request(body: String): String = peer.request(body.toByteArray()) { active.ensureActive(); current() }.decodeToString()
        require("archive" in peer.services())
        val reported = if ("Store" in peer.services()) SaitoStoreSeller.readInventory(request(SaitoStoreSeller.inventoryRequest(wallet.publicKey)), wallet.publicKey)
            .map { it.summary }.associateBy { it.signature } else emptyMap()
        val ids = (local.keys + reported.keys).toList()
        val permits = Semaphore(4)
        coroutineScope { ids.map { id -> async { permits.withPermit {
            val product = local[id] ?: readStoreProduct(peer, id, reported[id]) ?: return@withPermit emptyList()
            val offer = try { SaitoStoreRental.offer(product) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
            if (offer == null || offer.source.fileId != key.fileId || offer.source.creator != wallet.publicKey) return@withPermit emptyList()
            val query = JsonObject().apply {
                addProperty("request", "archive"); add("data", JsonObject().apply {
                    addProperty("request", "load"); addProperty("field4", product.nftId); addProperty("limit", 10)
                })
            }
            val reply = request(query.toString())
            require(reply.toByteArray().size <= 16 * 1024 * 1024)
            val rows = JsonParser.parseString(reply).asJsonArray.also { require(it.size() <= 10) }
            rows.mapNotNull { row ->
                try {
                    val envelope = row.asJsonObject.get("tx").asString.also { require(it.toByteArray().size <= 2 * 1024 * 1024) }
                    val body = JsonParser.parseString(java.util.Base64.getDecoder().decode(JsonParser.parseString(envelope).asJsonObject.get("m").asString).decodeToString()).asJsonObject
                    val payment = body.getAsJsonObject("fulfill_sale")?.get("sale_signature")?.asString ?: return@mapNotNull null
                    val archive = request(SaitoStore.listingArchiveRequest(payment))
                    SaitoStoreRental.creatorEntitlement(product, envelope, archive).also { path ->
                        require(path.fileId == key.fileId && path.creator == wallet.publicKey && path.masterScript.hash == SaitoAccessScript.parse(requireNotNull(key.customScript)).hash)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }
        } } }.awaitAll().flatten().distinctBy { it.source }.sortedByDescending { requireNotNull(it.grant).expiresAt } }
    }
}
