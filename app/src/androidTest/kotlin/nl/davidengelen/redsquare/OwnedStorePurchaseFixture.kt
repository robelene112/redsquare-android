package nl.davidengelen.redsquare

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mockwebserver3.MockResponse
import nl.davidengelen.redsquare.saito.*
import org.bouncycastle.crypto.digests.Blake3Digest
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64

/** Controlled Store observations. Independent official/WASM vectors separately verify these wire contracts. */
internal class OwnedStorePurchaseFixture(val reference: JsonObject) {
    enum class Phase { WAITING, PAID, DELIVERED, SETTLED_WITHOUT_ASSET, REFUNDED }
    @Volatile var phase = Phase.WAITING
    @Volatile var funded = true
    @Volatile var wrongCreator = false
    @Volatile var blockReadGate: java.util.concurrent.CountDownLatch? = null
    val blockedReads = java.util.concurrent.atomic.AtomicInteger()
    val completedHeldReplies = java.util.concurrent.atomic.AtomicInteger()
    val holdNextBlock = java.util.concurrent.atomic.AtomicBoolean(true)
    @Volatile var heldReplyStartedAt = 0L
    lateinit var peer: ControlledFeedPeer
    private val signer = SaitoWallet.fromPrivateKey("0".repeat(63) + "3") // Published unsafe fixture only.
    private var version: String? = null
    private var observations = emptyMap<String, ByteArray>()
    private var snapshot = ""
    fun candidate(): SaitoStorePurchase? {
        val candidates = peer.transactions.map { frame ->
            val wire = frame.copyOfRange(1, frame.size); val h = ByteBuffer.wrap(wire)
            val original = wire.copyOf(93 + 59 * (h.getInt(0) + h.getInt(4)) + h.getInt(8))
            ByteBuffer.wrap(original).putInt(12, 0)
            SaitoStorePurchase.restore(Base64.getEncoder().encodeToString(original)).also {
                check(it.author == reference.get("buyer").asString && it.store == reference.get("store").asString &&
                    it.nftId == reference.get("nftId").asString && it.unitPrice == BigInteger("250000000"))
            }
        }
        check(candidates.map { it.transaction.encoded() }.distinct().size <= 1) { "An original-order retry created another payment" }
        return candidates.firstOrNull()
    }
    @Synchronized fun response(target: String): MockResponse {
        if (target == "/options") return MockResponse(body = "{\"consensus\":{\"genesis_period\":100000}}")
        val candidate = candidate()
        if (candidate == null || phase == Phase.WAITING) {
            if (target != "/balance/${reference.get("buyer").asString}") return MockResponse(code = 404)
            val row = if (funded) "${reference.get("buyer").asString} 10 2 0 1000000000 0\n" else ""
            return MockResponse(body = "${System.currentTimeMillis()}-20-${"ab".repeat(32)}.snap\n$row")
        }
        val next = candidate.transaction.id + phase.name + wrongCreator
        if (version != next) {
            val time = System.currentTimeMillis()
            val payment = block(Base64.getDecoder().decode(candidate.transaction.encoded()), 21, "ab".repeat(32), time - 1)
            val blocks = mutableMapOf(payment)
            val tip = if (phase == Phase.PAID) payment else {
                val template = reference.getAsJsonObject("settlement").getAsJsonObject(if (phase == Phase.REFUNDED) "refund" else "fulfillment")
                val wire = Base64.getDecoder().decode(template.get("networkBase64").asString)
                val h = ByteBuffer.wrap(wire); val n = h.getInt(0); val m = h.getInt(4); val offset = 93 + (n + m) * 59
                if (wrongCreator && phase != Phase.REFUNDED) {
                    val replacement = decode58(reference.get("buyer").asString)
                    replacement.copyInto(wire, 93 + 59) // Bound creator metadata in the consumed listing.
                    replacement.copyInto(wire, 93 + n * 59) // Matching, but wrong creator in the delivered tuple.
                }
                val body = JsonParser.parseString(wire.copyOfRange(offset, wire.size).decodeToString()).asJsonObject
                if (phase == Phase.REFUNDED) { body.addProperty("order_tx_sig", candidate.transaction.id); body.addProperty("payment_tx_sig", candidate.transaction.id) }
                else body.getAsJsonObject("fulfill_sale").addProperty("sale_signature", candidate.transaction.id)
                // Witnesses bind the actual output vector and original escrow coordinates.
                val outputBytes = (n until n + m).fold(ByteArray(0)) { bytes, index ->
                    val o = 93 + index * 59; bytes + wire.copyOfRange(o, o + 41) + wire.copyOfRange(o + 57, o + 59)
                }
                val auth = digest(outputBytes).hex()
                val scripts = body.getAsJsonArray("access_scripts")
                val inputIndices = (0 until n).filter { index -> val o = 93 + index * 59; wire[o] == 0.toByte() && wire[o + 58] != 9.toByte() }
                inputIndices.forEachIndexed { index, row ->
                    val o = 93 + row * 59
                    val utxo = wire.copyOfRange(o, o + 33) + wire.copyOfRange(o + 41, o + 57) +
                        wire.copyOfRange(o + 57, o + 58) + wire.copyOfRange(o + 33, o + 41) + wire.copyOfRange(o + 58, o + 59)
                    val script = JsonParser.parseString(scripts[index].asString).asJsonObject
                    script.add("witness", JsonObject().apply { add("signatures", com.google.gson.JsonArray().apply { add(signer.sign((utxo.hex() + "|" + auth).toByteArray())) }) })
                    scripts.set(index, com.google.gson.JsonPrimitive(script.toString()))
                }
                val message = body.toString().toByteArray()
                val updated = wire.copyOf(offset) + message
                ByteBuffer.wrap(updated).putInt(8, message.size)
                signTransaction(updated)
                block(updated, 22, payment.first, time).also { blocks[it.first] = it.second }
            }
            val height = if (phase == Phase.PAID) 21 else 22
            val rows = if (phase == Phase.DELIVERED) {
                val wire = tip.second.copyOfRange(389, tip.second.size); val n = ByteBuffer.wrap(wire).getInt(0)
                listOf(1, 0, 2).joinToString("") { index ->
                    val o = 93 + (n + index) * 59
                    "${encode58(wire.copyOfRange(o, o + 33))} $height 0 $index ${BigInteger(1, wire.copyOfRange(o + 33, o + 41))} ${wire[o + 58].toInt()}\n"
                }
            } else ""
            snapshot = "${if (phase == Phase.PAID) time - 1 else time}-$height-${tip.first}.snap\n$rows"
            observations = blocks; version = next
        }
        if (target == "/balance/${reference.get("buyer").asString}") return MockResponse(body = snapshot)
        observations[target.removePrefix("/block/")]?.let { return MockResponse.Builder().body(okio.Buffer().write(it)).build() }
        return MockResponse(code = 404)
    }
    private fun block(original: ByteArray, height: Long, parent: String, time: Long): Pair<String, ByteArray> {
        val wire = original.copyOf(); val h = ByteBuffer.wrap(wire); val n = h.getInt(0); val m = h.getInt(4)
        val header = ByteArray(389); ByteBuffer.wrap(header).putInt(0, 1).putLong(4, height).putLong(12, time)
        parent.hexBytes().copyInto(header, 20); decode58(signer.publicKey).copyInto(header, 52)
        digest(preimage(wire)).copyInto(header, 85)
        val preimage = header.copyOfRange(4, 117) + header.copyOfRange(181, 213) + header.copyOfRange(221, 285)
        signer.sign(preimage).hexBytes().copyInto(header, 117)
        repeat(m) { index -> val o = 93 + (n + index) * 59; ByteBuffer.wrap(wire).putLong(o + 41, height).putLong(o + 49, 0); wire[o + 57] = index.toByte() }
        return digest(parent.hexBytes() + digest(preimage)).hex() to (header + wire)
    }
    private fun preimage(wire: ByteArray): ByteArray {
        val h = ByteBuffer.wrap(wire); val count = h.getInt(0) + h.getInt(4); val offset = 93 + count * 59
        val out = ByteBuffer.allocate(16 + 43 * count + h.getInt(8)).putLong(h.getLong(80))
        repeat(count) { index -> val o = 93 + index * 59; out.put(wire, o, 41).put(wire, o + 57, 2) }
        return out.putInt(h.getInt(88)).putInt(wire[92].toInt()).put(wire, offset, h.getInt(8)).array()
    }
    private fun signTransaction(wire: ByteArray) { signer.sign(preimage(wire)).hexBytes().copyInto(wire, 16) }
    private fun digest(bytes: ByteArray) = ByteArray(32).also { Blake3Digest(256).apply { update(bytes, 0, bytes.size); doFinal(it, 0) } }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun String.hexBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private fun encode58(bytes: ByteArray): String {
        var value = BigInteger(1, bytes); val text = StringBuilder()
        while (value.signum() > 0) { val parts = value.divideAndRemainder(BigInteger.valueOf(58)); text.append(alphabet[parts[1].toInt()]); value = parts[0] }
        repeat(bytes.takeWhile { it == 0.toByte() }.size) { text.append('1') }
        return text.reverse().toString()
    }
    private fun decode58(text: String): ByteArray {
        var value = BigInteger.ZERO
        text.forEach { value = value * BigInteger.valueOf(58) + BigInteger.valueOf(alphabet.indexOf(it).toLong()) }
        val bytes = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(text.takeWhile { it == '1' }.length) + bytes
    }

}
