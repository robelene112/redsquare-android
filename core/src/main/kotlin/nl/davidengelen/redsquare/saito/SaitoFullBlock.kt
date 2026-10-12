package nl.davidengelen.redsquare.saito

import java.math.BigInteger
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64
import org.bouncycastle.crypto.digests.Blake3Digest

/** Verified full-block content, not consensus admission or selected-chain membership. */
class SaitoFullBlock private constructor(val height: BigInteger, val timestamp: BigInteger,
    val hash: String, val previousHash: String, private val transactions: List<ByteArray>) {
    /** Routing may change; the original signed payment and every original input must match. */
    fun contains(payment: SaitoPayment): Boolean {
        val candidate = Base64.getDecoder().decode(payment.transaction.encoded())
        return transactions.withIndex().any { (ordinal, wire) ->
            if (wire[92] != 0.toByte() || u32(wire, 88) != 1L ||
                !wire.copyOfRange(16, 80).contentEquals(candidate.copyOfRange(16, 80))) return@any false
            val inputs = u32(wire, 0).toInt()
            val outputs = u32(wire, 4).toInt()
            if (outputs != 2) return@any false
            val outputOffset = 93 + inputs * 59
            for (index in 0 until outputs) {
                val offset = outputOffset + index * 59
                if (u64(wire, offset + 41) != height || u64(wire, offset + 49) != BigInteger.valueOf(ordinal.toLong()) ||
                    wire[offset + 57].toInt() != index) return@any false
            }
            val unrouted = wire.copyOf(93 + 59 * (inputs + outputs) + u32(wire, 8).toInt())
            ByteBuffer.wrap(unrouted).putInt(12, 0)
            for (index in 0 until outputs) {
                val offset = outputOffset + index * 59
                unrouted.fill(0, offset + 41, offset + 57)
            }
            if (!unrouted.contentEquals(candidate)) return@any false
            // Verify the actual normalized block payment through the strict public boundary.
            SaitoPayment.restore(Base64.getEncoder().encodeToString(unrouted))
                .transaction.encoded() == payment.transaction.encoded()
        }
    }

    companion object {
        // Local resource bounds. Unsupported representations must remain unknown to callers.
        const val MAX_BYTES = 8 * 1024 * 1024
        private const val MAX_LEAVES = 65_536

        fun parse(bytes: ByteArray): SaitoFullBlock {
            require(bytes.size in 389..MAX_BYTES)
            require(bytes.copyOfRange(213, 221).contentEquals(bytes.copyOfRange(245, 253)))
            val height = u64(bytes, 4)
            val timestamp = u64(bytes, 12)
            require(height.signum() > 0 && timestamp.signum() > 0)
            val creator = Base58.encode(bytes.copyOfRange(52, 85))
            require(SaitoWallet.isPublicKey(creator))
            val preimage = bytes.copyOfRange(4, 117) + bytes.copyOfRange(181, 213) + bytes.copyOfRange(221, 285)
            require(SaitoWallet.verify(preimage, hex(bytes.copyOfRange(117, 181)), creator))
            val parent = bytes.copyOfRange(20, 52)
            val hash = hex(digest(parent + digest(preimage)))
            val count = u32(bytes, 0)
            require(count <= (bytes.size - 389) / 93)
            val transactions = ArrayList<ByteArray>()
            val leaves = ArrayList<ByteArray>()
            var offset = 389
            repeat(count.toInt()) {
                require(bytes.size - offset >= 93)
                val inputs = u32(bytes, offset)
                val outputs = u32(bytes, offset + 4)
                require(inputs <= 255 && outputs <= 255)
                val length = 93L + 59L * (inputs + outputs) + u32(bytes, offset + 8) + 130L * u32(bytes, offset + 12)
                require(length <= bytes.size - offset)
                val wire = bytes.copyOfRange(offset, offset + length.toInt())
                val type = wire[92].toInt() and 255
                require(type in 0..8 && type != 5) { "Unsupported full-block transaction representation" }
                val replacements = u32(wire, 88).coerceAtLeast(1)
                require(replacements <= MAX_LEAVES - leaves.size)
                val leaf = transactionLeaf(wire, (inputs + outputs).toInt(), type)
                repeat(replacements.toInt()) { leaves.add(leaf) }
                transactions.add(wire)
                offset += length.toInt()
            }
            require(offset == bytes.size)
            require(merkleRoot(leaves).contentEquals(bytes.copyOfRange(85, 117)))
            return SaitoFullBlock(height, timestamp, hash, hex(parent), transactions)
        }

        private fun transactionLeaf(wire: ByteArray, slipCount: Int, type: Int): ByteArray {
            val preimage = ByteArrayOutputStream()
            preimage.write(wire, 80, 8)
            repeat(slipCount) { index ->
                val offset = 93 + index * 59
                require((wire[offset + 58].toInt() and 255) in 0..10)
                preimage.write(wire, offset, 41)
                preimage.write(wire, offset + 57, 2)
            }
            preimage.write(wire, 88, 4)
            preimage.write(byteArrayOf(0, 0, 0, type.toByte()))
            preimage.write(wire, 93 + slipCount * 59, u32(wire, 8).toInt())
            return digest(preimage.toByteArray())
        }

        private fun merkleRoot(leaves: List<ByteArray>): ByteArray {
            if (leaves.isEmpty()) return ByteArray(32)
            var level = leaves
            while (level.size > 1) level = level.chunked(2).map { pair ->
                if (pair.size == 1) pair[0] else digest(pair[0] + pair[1])
            }
            return level.single()
        }

        private fun u32(bytes: ByteArray, offset: Int): Long = ByteBuffer.wrap(bytes).getInt(offset).toLong() and 0xffff_ffffL
        private fun u64(bytes: ByteArray, offset: Int): BigInteger = BigInteger(1, bytes.copyOfRange(offset, offset + 8))
        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        private fun digest(bytes: ByteArray): ByteArray = ByteArray(32).also { result ->
            Blake3Digest(256).apply { update(bytes, 0, bytes.size); doFinal(result, 0) }
        }
    }
}
