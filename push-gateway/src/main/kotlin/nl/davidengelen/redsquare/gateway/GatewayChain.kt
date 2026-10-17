package nl.davidengelen.redsquare.gateway

import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoFullBlock
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoWire
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Read-only chain references and full blocks. Peer tips are hints, not processed cursors.
 * Fetch blocks individually to bound memory. The caller checks retained ancestry, records gaps
 * and commits candidate processing before advancing its cursor. Close after use/failure.
 */
class GatewayChain(private val identity: SaitoWallet, val backend: SaitoBackend, localOrigin: String? = null) : Closeable {
    data class Cursor(val height: BigInteger, val hash: String, val forkId: String) {
        init { require(height.signum() >= 0 && height.bitLength() <= 64 && hash.matches(HASH) && forkId.matches(HASH)) }
    }
    data class Reference(val height: BigInteger, val hash: String, val timestamp: BigInteger) {
        init { require(height.signum() > 0 && height.bitLength() <= 64 && hash.matches(HASH) &&
            timestamp.signum() > 0 && timestamp.bitLength() <= 64) }
    }
    data class Range(val tip: Cursor, val ancestorHeight: BigInteger, val ancestorHash: String,
        val references: List<Reference>)
    private val closed = AtomicBoolean(false)
    private val download = AtomicReference<Call?>()
    internal val peer = GatewayPeer(identity, backend, localOrigin)
    private val origin = localOrigin?.removeSuffix("/") ?: backend.webUrl

    /** Missing retained references remain visible as height gaps; empty does not itself mean caught up. */
    fun range(cursor: Cursor? = null): Range {
        if (closed.get()) throw IOException("Chain reader closed")
        val known = cursor ?: Cursor(BigInteger.ZERO, "0".repeat(64), "0".repeat(64))
        val body = peer.chainRequest(SaitoWire.blockchainRequest(identity.publicKey, known.height, known.hash, known.forkId))
        return try {
            require(body.size >= 196)
            val count = ByteBuffer.wrap(body, 192, 4).int
            require(count in 0..128 && body.size == 196 + 53 * count)
            val tip = Cursor(u64(body, 0), hex(body, 8), hex(body, 40))
            val ancestorHeight = u64(body, 72)
            val ancestorHash = hex(body, 80)
            require(ancestorHeight <= tip.height && (ancestorHeight.signum() == 0) == (ancestorHash == ZERO_HASH))
            require((tip.height.signum() == 0) == (tip.hash == ZERO_HASH))
            val references = (0 until count).map { index ->
                val offset = 196 + 53 * index
                // Rust accepts any nonzero golden flag; this reader explicitly supports the serializer's0/1 only.
                require((body[offset + 52].toInt() and 0xff) <= 1)
                Reference(u64(body, offset), hex(body, offset + 8), u64(body, offset + 40)).also {
                    require(it.height <= tip.height && it.height > ancestorHeight && it.hash != ZERO_HASH)
                }
            }
            require(references.zipWithNext().all { (first, next) -> next.height > first.height })
            if (references.isEmpty()) {
                require(u64(body, 112) == tip.height && hex(body, 120) == tip.hash &&
                    u64(body, 152) == tip.height && hex(body, 160) == tip.hash)
            } else {
                require(references.last().height != tip.height || references.last().hash == tip.hash)
                require(u64(body, 112) == references.first().height && hex(body, 120) == references.first().hash &&
                    u64(body, 152) == references.last().height && hex(body, 160) == references.last().hash)
            }
            Range(tip, ancestorHeight, ancestorHash, references)
        } catch (failure: IllegalArgumentException) { throw IOException("Unsupported chain response", failure) }
    }

    /** Creator/header/Merkle and exact reference identity, not consensus admission or finality. */
    fun block(reference: Reference): SaitoFullBlock {
        if (closed.get()) throw IOException("Chain reader closed")
        val call = client.newCall(Request.Builder().url("$origin/block/${reference.hash}").build())
        if (!download.compareAndSet(null, call)) throw IOException("Full block download already active")
        try {
            if (closed.get()) throw IOException("Chain reader closed")
            return call.execute().use { response ->
                if (response.code != 200 || response.body.contentLength() > SaitoFullBlock.MAX_BYTES) {
                    throw IOException("Full block unavailable")
                }
                val bytes = response.body.byteStream().readNBytes(SaitoFullBlock.MAX_BYTES + 1)
                if (bytes.size > SaitoFullBlock.MAX_BYTES) throw IOException("Full block too large")
                try {
                    SaitoFullBlock.parse(bytes).also {
                        require(it.height == reference.height && it.hash == reference.hash && it.timestamp == reference.timestamp)
                    }
                } catch (failure: IllegalArgumentException) { throw IOException("Unsupported full block", failure) }
            }
        } finally { download.compareAndSet(call, null) }
    }

    override fun close() { closed.set(true); peer.close(); download.getAndSet(null)?.cancel() }

    companion object {
        private val HASH = Regex("[0-9a-f]{64}")
        private val ZERO_HASH = "0".repeat(64)
        private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
        private fun u64(body: ByteArray, offset: Int) = BigInteger(1, body.copyOfRange(offset, offset + 8))
        private fun hex(body: ByteArray, offset: Int) = body.copyOfRange(offset, offset + 32).joinToString("") { "%02x".format(it) }
    }
}
