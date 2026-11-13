package nl.davidengelen.redsquare

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mockwebserver3.MockResponse
import nl.davidengelen.redsquare.saito.*
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Disposable public protocol model. Mint shape/identity comes from the independent WASM wallet reference. */
internal class OwnedVaultMintFixture(private val reference: JsonObject, private val owner: String,
    val confirm: AtomicBoolean, private val prior: SaitoVaultMint.Candidate? = null) : AutoCloseable {
    private var peer: ControlledFeedPeer? = null
    private val workers = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    private val files = ConcurrentHashMap<String, String>()
    val rejectUpload = AtomicBoolean(false)
    @Volatile var availableNolan: Long = 200_000_000
    @Volatile var uploadGate: CountDownLatch? = null
    private val id = reference.get("nftId").asString
    private val scriptHash = SaitoEnvelope.decode(reference.getAsJsonObject("file").get("envelope").asString).body.get("access_hash").asString

    fun connect(value: ControlledFeedPeer) {
        peer = value
        value.vaultUploadResponseExecutor = workers
        value.vaultUploadResponse = { signed ->
            require(signed.author == owner)
            val envelope = signed.body.get("data").asString
            val file = SaitoEnvelope.decode(envelope)
            require(file.author == owner && file.body.get("access_hash").asString == scriptHash)
            val candidate = requireNotNull(candidate())
            require(candidate.nftId == id && candidate.fileId == file.id)
            uploadGate?.let { check(it.await(90, TimeUnit.SECONDS)) }
            if (rejectUpload.get()) "{\"status\":\"err\",\"err\":\"Controlled Vault refusal\"}" else {
                files[file.id] = envelope
                "{\"status\":\"success\",\"err\":\"\"}"
            }
        }
        value.vaultResponse = { signed ->
            require(signed.author == owner && confirm.get() && candidate() != null)
            val data = signed.body.getAsJsonObject("data")
            require(data.get("access_hash").asString == scriptHash)
            val script = JsonParser.parseString(data.get("access_script").asString).asJsonObject
            require(script.get("op").asString == "CHECKOWNNFT" && script.get("nftid").asString == id)
            val file = files[data.getAsJsonObject("data").get("file_id").asString]
            JsonObject().apply {
                addProperty("status", if (file != null) "success" else "err")
                add("txs", JsonArray().apply { file?.let { add(it) } })
            }.toString()
        }
    }

    fun candidate(): SaitoVaultMint.Candidate? = peer?.transactions?.firstNotNullOfOrNull { frame ->
        if (frame.size < 94 || frame[93] != 8.toByte()) null else {
            val wire = frame.copyOfRange(1, frame.size)
            val header = ByteBuffer.wrap(wire)
            val end = 93 + 59 * (header.getInt(0) + header.getInt(4)) + header.getInt(8)
            val original = wire.copyOfRange(0, end)
            ByteBuffer.wrap(original).putInt(12, 0)
            SaitoVaultMint.readCandidate(Base64.getEncoder().encodeToString(original)).also {
                require(it.author == owner && it.nftId == id)
                require(it.inputs.single().amount == BigInteger("200000000"))
            }
        }
    } ?: prior?.also { require(it.author == owner && it.nftId == id) }

    fun snapshot(): String {
        val minted = confirm.get() && candidate() != null
        val rows = if (!minted) "$owner 2000001 3 0 $availableNolan 0\n" else buildString {
            append("$owner 2000004 8 1 100000000 0\n")
            append("$owner 2000004 8 0 1 9\n")
            append("${base58(id.chunked(2).map { it.toInt(16).toByte() }.toByteArray())} 2000004 8 2 0 9\n")
            append("$owner 2000004 8 3 100000000 0\n")
        }
        return "${System.currentTimeMillis()}-2000005-${"ab".repeat(32)}.snap\n$rows"
    }
    fun response(target: String): MockResponse = when (target) {
        "/options" -> MockResponse(body = "{\"consensus\":{\"genesis_period\":10000000}}")
        "/balance/$owner" -> MockResponse(body = snapshot())
        else -> MockResponse(code = 404)
    }
    fun archive(criteria: JsonObject): String {
        val candidate = candidate()?.takeIf { confirm.get() && criteria.get("field4")?.asString == id } ?: return "[]"
        return JsonArray().apply { add(JsonObject().apply { addProperty("tx", candidate.transaction.envelope()) }) }.toString()
    }
    fun storedContents(): ByteArray {
        val envelope = requireNotNull(files[candidate()?.fileId])
        val uri = SaitoEnvelope.decode(envelope).body.getAsJsonObject("data").get("file").asString
        return Base64.getDecoder().decode(uri.substringAfter(','))
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        uploadGate?.countDown(); workers.shutdownNow()
        check(workers.awaitTermination(10, TimeUnit.SECONDS))
    }
    private fun base58(bytes: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var number = BigInteger(1, bytes)
        val encoded = StringBuilder()
        while (number.signum() > 0) {
            val divided = number.divideAndRemainder(BigInteger.valueOf(58))
            encoded.append(alphabet[divided[1].toInt()]); number = divided[0]
        }
        repeat(bytes.takeWhile { it == 0.toByte() }.size) { encoded.append('1') }
        return encoded.reverse().toString()
    }
}
