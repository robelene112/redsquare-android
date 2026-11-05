package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Published client-bootstrap block metadata, not consensus proof or spendable wallet state. */
class SaitoNodeBlock private constructor(val height: Long, val timestamp: Long, val hash: String) {
    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
        private val unsigned = Regex("[0-9]{1,16}")
        private val blockHash = Regex("[0-9a-fA-F]{64}")

        fun parse(bytes: ByteArray, nowMillis: Long): SaitoNodeBlock {
            require(bytes.size in 1..MAX_BYTES && nowMillis > 0)
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            val root = JsonParser.parseString(text)
            require(root.isJsonObject)
            val value = requireNotNull(root.asJsonObject.get("blockchain"))
            require(value.isJsonObject)
            val chain = value.asJsonObject
            fun number(name: String): Long {
                val item = requireNotNull(chain.get(name))
                require(item.isJsonPrimitive && item.asJsonPrimitive.isNumber && item.asString.matches(unsigned))
                return requireNotNull(item.asString.toLongOrNull()).also { require(it in 1..MAX_SAFE_INTEGER) }
            }
            val height = number("last_block_id")
            val timestamp = number("last_timestamp")
            require(timestamp <= nowMillis + 60_000L)
            val hashValue = requireNotNull(chain.get("last_block_hash"))
            require(hashValue.isJsonPrimitive && hashValue.asJsonPrimitive.isString)
            val hash = hashValue.asString
            require(hash.matches(blockHash) && hash.any { it != '0' })
            return SaitoNodeBlock(height, timestamp, hash.lowercase())
        }
    }
}
