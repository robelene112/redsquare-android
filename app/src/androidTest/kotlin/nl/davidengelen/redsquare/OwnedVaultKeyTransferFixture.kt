package nl.davidengelen.redsquare

import com.google.gson.JsonObject
import mockwebserver3.MockResponse
import nl.davidengelen.redsquare.saito.SaitoNftTransfer
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.bouncycastle.crypto.digests.Blake3Digest
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/** Owned selected-node observation. Core tests separately match the original factory/block against WASM. */
internal class OwnedVaultKeyTransferFixture(private val source: JsonObject, private val reference: JsonObject) {
    val confirm = AtomicBoolean(true)
    private var peer: ControlledFeedPeer? = null
    private val creator = SaitoWallet.fromPrivateKey("0".repeat(63) + "3") // Published unsafe fixture only.
    private var observed: Triple<String, String, ByteArray>? = null
    private var observedAt = 0L
    fun connect(value: ControlledFeedPeer) { peer = value }

    fun candidate(): SaitoNftTransfer? {
        val frames = peer?.transactions.orEmpty()
        val candidates = frames.map { frame ->
            require(frame.first() == 4.toByte())
            val wire = frame.copyOfRange(1, frame.size)
            val header = ByteBuffer.wrap(wire)
            require(wire[92] == 8.toByte())
            val original = wire.copyOf(93 + 59 * (header.getInt(0) + header.getInt(4)) + header.getInt(8))
            ByteBuffer.wrap(original).putInt(12, 0)
            SaitoNftTransfer.restore(Base64.getEncoder().encodeToString(original)).also { transfer ->
                require(transfer.author == source.get("owner").asString)
                require(transfer.recipient == reference.get("recipient").asString)
                require(transfer.holding.id == source.get("id").asString)
                require(transfer.holding.witnesses == source.getAsJsonObject("script").getAsJsonObject("witness")
                    .let { listOf(it.get("utxokey1").asString, it.get("utxokey2").asString, it.get("utxokey3").asString) })
                require(transfer.key.fileId == source.getAsJsonObject("file").get("id").asString)
            }
        }
        require(candidates.map { it.transaction.encoded() }.distinct().size <= 1) { "A retry changed the original transfer" }
        return candidates.firstOrNull()
    }

    @Synchronized private fun block(transfer: SaitoNftTransfer): Pair<String, ByteArray> {
        observed?.takeIf { it.first == transfer.transaction.id }?.let { return it.second to it.third }
        val original = Base64.getDecoder().decode(transfer.transaction.encoded())
        val header = Base64.getDecoder().decode(reference.getAsJsonObject("block").get("fullBase64").asString).copyOf(389)
        observedAt = System.currentTimeMillis()
        ByteBuffer.wrap(header).putLong(12, observedAt)
        // Re-leaf the received signed payload, retaining the independently assigned
        // block layout/creator. This retiming is fixture preparation, not consensus admission.
        val messageOffset = 93 + 6 * 59
        val body = original.copyOfRange(messageOffset, original.size)
        val preimage = ByteBuffer.allocate(16 + 6 * 43 + body.size).putLong(ByteBuffer.wrap(original).getLong(80))
        repeat(6) { index ->
            val offset = 93 + index * 59
            preimage.put(original, offset, 41).put(original, offset + 57, 2)
        }
        preimage.putInt(1).putInt(8).put(body)
        digest(preimage.array()).copyInto(header, 85)
        val blockPreimage = header.copyOfRange(4, 117) + header.copyOfRange(181, 213) + header.copyOfRange(221, 285)
        creator.sign(blockPreimage).hexBytes().copyInto(header, 117)
        val hash = digest(header.copyOfRange(20, 52) + digest(blockPreimage)).joinToString("") { "%02x".format(it) }
        val included = original.copyOf()
        repeat(3) { index ->
            val offset = 93 + (3 + index) * 59
            ByteBuffer.wrap(included).putLong(offset + 41, 2000003).putLong(offset + 49, 0)
            included[offset + 57] = index.toByte()
        }
        val bytes = header + included
        observed = Triple(transfer.transaction.id, hash, bytes)
        return hash to bytes
    }

    fun response(target: String): MockResponse {
        val transfer = candidate()?.takeIf { confirm.get() }
        if (target == "/balance/${source.get("owner").asString}") {
            if (transfer == null) return MockResponse(body = source.get("snapshot").asString
                .replaceBefore('-', System.currentTimeMillis().toString()))
            val block = block(transfer)
            val ordinary = source.get("snapshot").asString.lines().drop(1).first()
            return MockResponse(body = "$observedAt-2000003-${block.first}.snap\n$ordinary\n")
        }
        if (transfer != null) {
            val block = block(transfer)
            if (target == "/block/${block.first}") return MockResponse.Builder().body(okio.Buffer().write(block.second)).build()
        }
        return MockResponse(code = 404)
    }
    private fun digest(bytes: ByteArray) = ByteArray(32).also {
        Blake3Digest(256).apply { update(bytes, 0, bytes.size); doFinal(it, 0) }
    }
    private fun String.hexBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
