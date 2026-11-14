package nl.davidengelen.redsquare

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mockwebserver3.MockResponse
import nl.davidengelen.redsquare.saito.*
import org.bouncycastle.crypto.digests.Blake3Digest
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/** Owned node observations. Pinned WASM vectors independently establish the contract, not consensus. */
internal class OwnedStoreSellerFixture(private val source: JsonObject, private val listing: JsonObject,
    private val removal: JsonObject, private val imageCreation: Boolean, private val prelisted: Boolean) {
    val confirmImage = AtomicBoolean(false)
    val confirmDelist = AtomicBoolean(false)
    private var peer: ControlledFeedPeer? = null
    private var included: Triple<String, String, ByteArray>? = null
    private var blockTime = 0L
    private val owner = source.get("owner").asString
    private val creator = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
    fun connect(value: ControlledFeedPeer) { peer = value }
    private fun originals(): List<Pair<JsonObject, String>> = peer?.transactions.orEmpty().map { frame ->
        require(frame[0] == 4.toByte())
        val wire = frame.copyOfRange(1, frame.size); val h = ByteBuffer.wrap(wire)
        val start = 93 + 59 * (h.getInt(0) + h.getInt(4)); val end = start + h.getInt(8)
        val body = JsonParser.parseString(wire.copyOfRange(start, end).decodeToString()).asJsonObject
        val original = wire.copyOf(end); ByteBuffer.wrap(original).putInt(12, 0)
        body to Base64.getEncoder().encodeToString(original)
    }
    fun image(): SaitoImageMint? = originals().filter { !it.first.has("request") && it.first.getAsJsonObject("data")?.has("image") == true }
        .map { SaitoImageMint.restore(it.second) }.distinctBy { it.transaction.id }.also { require(it.size <= 1) }.singleOrNull()
    fun delist(): SaitoStoreDelist? {
        val product = requireNotNull(SaitoStore.readListing(SaitoStore.readCatalogue(listing.get("page").toString()).items.single(), listing.getAsJsonObject("listing").get("envelope").asString))
        return originals().filter { it.first.get("request")?.asString == "delist-asset" }.map { SaitoStoreDelist.restore(it.second, product) }
            .distinctBy { it.transaction.id }.also { require(it.size <= 1) }.singleOrNull()
    }
    fun archive(criteria: JsonObject): String {
        val image = image()
        val envelope = when {
            criteria.get("sig")?.asString == listing.getAsJsonObject("listing").get("id").asString -> listing.getAsJsonObject("listing").get("envelope").asString
            image != null && criteria.get("field4")?.asString == image.nftId -> image.transaction.envelope()
            criteria.get("field4")?.asString == source.get("id").asString -> source.getAsJsonObject("key").get("envelope").asString
            else -> null
        }
        return JsonArray().apply { envelope?.let { add(JsonObject().apply { addProperty("tx", it) }) } }.toString()
    }
    @Synchronized private fun block(delist: SaitoStoreDelist): Pair<String, ByteArray> {
        included?.takeIf { it.first == delist.transaction.id }?.let { return it.second to it.third }
        val wire = Base64.getDecoder().decode(delist.transaction.encoded())
        val header = Base64.getDecoder().decode(removal.getAsJsonObject("block").get("fullBase64").asString).copyOf(389)
        blockTime = System.currentTimeMillis(); ByteBuffer.wrap(header).putLong(12, blockTime)
        val message = wire.copyOfRange(447, wire.size)
        val preimage = ByteBuffer.allocate(16 + 6 * 43 + message.size).putLong(ByteBuffer.wrap(wire).getLong(80))
        repeat(6) { i -> val p = 93 + i * 59; preimage.put(wire, p, 41).put(wire, p + 57, 2) }
        preimage.putInt(1).putInt(8).put(message); digest(preimage.array()).copyInto(header, 85)
        val signedHeader = header.copyOfRange(4, 117) + header.copyOfRange(181, 213) + header.copyOfRange(221, 285)
        creator.sign(signedHeader).hexBytes().copyInto(header, 117)
        val hash = digest(header.copyOfRange(20, 52) + digest(signedHeader)).hex()
        val assigned = wire.copyOf()
        repeat(3) { i -> val p = 93 + (3 + i) * 59; ByteBuffer.wrap(assigned).putLong(p + 41, 21).putLong(p + 49, 0); assigned[p + 57] = i.toByte() }
        val bytes = header + assigned
        SaitoFullBlock.parse(bytes).also { require(it.hash == hash && it.storeDelistOrdinal(delist) == java.math.BigInteger.ZERO) }
        included = Triple(delist.transaction.id, hash, bytes); return hash to bytes
    }
    fun response(target: String): MockResponse {
        if (target == "/options") return MockResponse(body = "{\"consensus\":{\"genesis_period\":10000000}}")
        val delist = delist()?.takeIf { confirmDelist.get() }
        if (delist != null) {
            val actual = block(delist)
            if (target == "/block/${actual.first}") return MockResponse.Builder().body(okio.Buffer().write(actual.second)).build()
            if (target == "/balance/$owner") return MockResponse(body = "$blockTime-21-${actual.first}.snap\n" + rows(actual.second.copyOfRange(389, actual.second.size), listOf(1, 0, 2), 21))
        }
        if (target != "/balance/$owner") return MockResponse(code = 404)
        val now = System.currentTimeMillis()
        val image = image()?.takeIf { confirmImage.get() }
        if (image != null) return MockResponse(body = "$now-2000004-${"ab".repeat(32)}.snap\n" + rows(Base64.getDecoder().decode(image.transaction.encoded()), listOf(3, 1, 0, 2), 2000004))
        if (imageCreation) return MockResponse(body = "$now-2000003-${"ab".repeat(32)}.snap\n$owner 2000001 3 0 200000000 0\n")
        if (prelisted) return MockResponse(body = "$now-20-${"cd".repeat(32)}.snap\n$owner 10 2 0 200000000 0\n")
        return MockResponse(body = source.get("snapshot").asString.replaceBefore('-', now.toString()))
    }
    private fun rows(wire: ByteArray, indexes: List<Int>, height: Long): String {
        val inputs = ByteBuffer.wrap(wire).getInt(0)
        return indexes.joinToString("\n", postfix = "\n") { i ->
            val p = 93 + (inputs + i) * 59
            "${base58(wire.copyOfRange(p, p + 33))} $height 0 $i ${java.math.BigInteger(1, wire.copyOfRange(p + 33, p + 41))} ${wire[p + 58].toInt() and 255}"
        }
    }
    private fun base58(bytes: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var number = java.math.BigInteger(1, bytes)
        val encoded = StringBuilder()
        while (number.signum() > 0) {
            val divided = number.divideAndRemainder(java.math.BigInteger.valueOf(58))
            encoded.append(alphabet[divided[1].toInt()]); number = divided[0]
        }
        repeat(bytes.takeWhile { it == 0.toByte() }.size) { encoded.append('1') }
        return encoded.reverse().toString()
    }
    private fun digest(bytes: ByteArray) = ByteArray(32).also { result -> Blake3Digest(256).apply { update(bytes, 0, bytes.size); doFinal(result, 0) } }
    private fun String.hexBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
}
