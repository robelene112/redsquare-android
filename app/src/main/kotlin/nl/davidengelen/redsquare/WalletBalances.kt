package nl.davidengelen.redsquare

import android.content.Intent
import nl.davidengelen.redsquare.saito.ImportBalance
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoBalanceSnapshot
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SaitoPaymentChain
import nl.davidengelen.redsquare.saito.TransferStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit
import com.google.gson.JsonParser
import java.math.BigInteger

/** Public, bounded selected-host lookup. No private options or fallback peer. */
class WalletBalances private constructor(private val endpoint: (SaitoBackend) -> String) {
    private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    fun assess(backend: SaitoBackend, publicKey: String): ImportBalance =
        read(backend, publicKey)?.let { if (it.total.signum() == 0) ImportBalance.ZERO else ImportBalance.POSITIVE }
            ?: ImportBalance.UNKNOWN

    fun read(backend: SaitoBackend, publicKey: String): SaitoBalanceSnapshot? = try {
        require(SaitoWallet.isPublicKey(publicKey))
        fetch(endpoint(backend) + publicKey)?.let {
            SaitoBalanceSnapshot.parse(it, publicKey, System.currentTimeMillis())
        }
    } catch (_: Exception) { null }

    /** Read only the selected host's public consensus retention setting. */
    fun funding(backend: SaitoBackend, publicKey: String): Funding? = try {
        val options = requireNotNull(fetch(endpoint(backend).removeSuffix("balance/") + "options"))
        val value = JsonParser.parseString(options.toString(Charsets.UTF_8)).asJsonObject
            .getAsJsonObject("consensus").get("genesis_period")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        require(value.asString.matches(Regex("[0-9]{1,20}")))
        val period = BigInteger(value.asString)
        require(period.signum() > 0 && period.bitLength() <= 64)
        Funding(requireNotNull(read(backend, publicKey)), period)
    } catch (_: Exception) { null }

    data class Funding(val snapshot: SaitoBalanceSnapshot, val genesisPeriod: BigInteger) {
        val eligible: List<SaitoBalanceSnapshot.Slip> get() {
            val minimum = if (snapshot.height > genesisPeriod) snapshot.height - genesisPeriod + BigInteger.valueOf(2)
                else BigInteger.ONE
            return snapshot.slips.filter { it.block >= minimum && it.amount.signum() > 0 && it.type in setOf(0, 1, 5, 7) }
        }
    }

    data class TransferObservation(val included: Map<String, SaitoPaymentChain.Inclusion> = emptyMap(),
        val reorganized: Set<String> = emptySet())

    /** Exact inclusion or covered displacement, sharing the same bounded selected-host reads. */
    fun observeTransfers(backend: SaitoBackend, snapshot: SaitoBalanceSnapshot,
        records: List<TransferStore.Pending>): TransferObservation {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        val blocks = mutableMapOf<String, ByteArray?>()
        var received = 0
        val result = mutableMapOf<String, SaitoPaymentChain.Inclusion>()
        val reorganized = mutableSetOf<String>()
        fun readBlock(hash: String): ByteArray? {
                val remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                return if (remainingMillis <= 0 || !hash.matches(Regex("[0-9a-f]{64}"))) null
                else if (blocks.containsKey(hash)) blocks[hash]
                else {
                    val limit = minOf(8 * 1024 * 1024, 32 * 1024 * 1024 - received)
                    val bytes = if (limit <= 0) null else try {
                        fetch(endpoint(backend).removeSuffix("balance/") + "block/" + hash,
                            limit, minOf(10_000, remainingMillis))
                    } catch (_: Exception) { null }
                    received += bytes?.size ?: 0
                    blocks[hash] = bytes
                    bytes
                }
        }
        for (record in records) {
            val payment = record.payment
            if (record.backend != backend || payment.author != snapshot.publicKey || System.nanoTime() >= deadline) continue
            val inclusion = SaitoPaymentChain.observe(payment, snapshot, ::readBlock)
            if (System.nanoTime() >= deadline) continue
            if (inclusion != null) result[payment.transaction.id] = inclusion
            else record.lastInclusion?.let { previous ->
                if (SaitoPaymentChain.check(previous, snapshot, ::readBlock) == SaitoPaymentChain.PriorStatus.REORGANIZED &&
                    System.nanoTime() < deadline) reorganized.add(payment.transaction.id)
            }
        }
        return TransferObservation(result, reorganized)
    }

    private fun fetch(url: String, maximumBytes: Int = ImportBalance.MAX_SNAPSHOT_BYTES,
        timeoutMillis: Long = 10_000): ByteArray? {
        val request = Request.Builder().url(url)
            .header("Cache-Control", "no-cache, no-store").build()
        val call = client.newCall(request)
        call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
        return call.execute().use { response ->
            if (!response.isSuccessful) return@use null
            val buffer = ByteArray(maximumBytes + 1)
            val count = response.body.byteStream().use { stream ->
                var size = 0
                while (size < buffer.size) {
                    val read = stream.read(buffer, size, buffer.size - size)
                    if (read < 0) break
                    require(read > 0)
                    size += read
                }
                size
            }
            if (count > maximumBytes) null else buffer.copyOf(count)
        }
    }

    companion object {
        fun fromIntent(intent: Intent) = WalletBalances { backend ->
            val fixture = if (BuildConfig.DEBUG) intent.getStringExtra("balanceApiUrl") else null
            if (fixture == null) "${backend.webUrl}/balance/" else try {
                val uri = URI(fixture)
                if (uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
                    uri.path == "/balance/" && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) fixture
                else "http://127.0.0.1:1/balance/"
            } catch (_: Exception) { "http://127.0.0.1:1/balance/" }
        }
    }
}
