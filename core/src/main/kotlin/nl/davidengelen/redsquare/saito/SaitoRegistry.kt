package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.Strictness
import org.bouncycastle.crypto.ec.CustomNamedCurves
import java.math.BigInteger

/** Registrar-signed ownership, with a separate unsigned server chain-state hint. */
data class SaitoRegisteredName(val identifier: String, val publicKey: String, val proof: String,
    val active: Boolean)

object SaitoRegistry {
    const val REGISTRAR = "sFQuGQ6teVaHs6AAtBBWeDwtGMCGCPNyhTJqYDXCvvVK"
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun registration(wallet: SaitoWallet, name: String, timestamp: Long, registrar: String = REGISTRAR): SaitoTransaction {
        require(name.matches(Regex("[A-Za-z0-9]{1,45}")))
        val body = JsonObject().apply {
            addProperty("module", "Registry")
            addProperty("request", "register")
            addProperty("identifier", "$name@saito")
        }
        return SaitoTransaction.createTo(wallet, body, timestamp, registrar)
    }

    /** Null means a successful empty namecheck. Invalid/unavailable replies throw. */
    fun readNamecheck(response: String, identifier: String, registrar: String = REGISTRAR): SaitoRegisteredName? {
        require(identifier.matches(Regex("[A-Za-z0-9]{1,45}@saito")))
        require(response.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
        val rows = gson.fromJson(response, JsonArray::class.java)
            ?: throw IllegalArgumentException("Missing registry response")
        require(rows.size() <= 1)
        if (rows.isEmpty) return null
        val row = rows[0].asJsonObject
        require(string(row, "identifier") == identifier)
        val owner = string(row, "publickey")
        val point = Base58.decode(owner)
        require(point.size == 33 && point[0] in 2.toByte()..3.toByte())
        CustomNamedCurves.getByName("secp256k1").curve.decodePoint(point)
        val bid = row.get("bid").let { value ->
            require(value.isJsonPrimitive && (value.asJsonPrimitive.isNumber || value.asJsonPrimitive.isString))
            value.asBigDecimal.toBigIntegerExact().also {
                require(it.signum() >= 0 && it <= BigInteger("9007199254740991"))
            }
        }
        val blockHash = string(row, "bsh")
        require(blockHash.matches(Regex("[a-fA-F0-9]{64}")))
        require(string(row, "signer") == registrar)
        val signature = string(row, "sig")
        require(SaitoWallet.verify((identifier + owner + bid + blockHash).toByteArray(Charsets.UTF_8), signature, registrar))
        val lc = row.get("lc")
        require(lc.isJsonPrimitive && lc.asJsonPrimitive.isNumber && lc.asString in listOf("0", "1"))
        return SaitoRegisteredName(identifier, owner, row.toString(), lc.asInt == 1)
    }

    private fun string(row: JsonObject, field: String): String {
        val value = row.get(field)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }
}
