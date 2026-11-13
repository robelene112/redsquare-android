package nl.davidengelen.redsquare.saito

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.bouncycastle.crypto.digests.Blake3Digest
import java.io.StringReader
import java.math.BigDecimal
import java.math.BigInteger
import com.fasterxml.jackson.core.io.schubfach.DoubleToDecimal

/** A bounded script commitment, never evidence that its access conditions are satisfied. */
class SaitoAccessScript private constructor(val source: String, val hash: String) {
    companion object {
        fun parse(source: String): SaitoAccessScript {
            require(source.toByteArray(Charsets.UTF_8).size <= 64 * 1024) { "Access script is too large" }
            val json = try {
                JsonReader(StringReader(source)).use { reader ->
                    reader.strictness = Strictness.STRICT
                    JsonParser.parseReader(reader).also { require(reader.peek() == JsonToken.END_DOCUMENT) }
                }
            } catch (_: Exception) { throw IllegalArgumentException("Invalid access script JSON") }
            require(json.isJsonObject && json.asJsonObject.get("op")?.let {
                it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank() && it.asString.length <= 64
            } == true) { "An access script needs an opcode" }
            var nodes = 0
            fun canonical(value: JsonElement, depth: Int): String {
                require(depth <= 32 && ++nodes <= 4096) { "Access script is too complex" }
                return when {
                    value.isJsonNull -> "null"
                    value.isJsonArray -> value.asJsonArray.joinToString(prefix = "[", postfix = "]", separator = ",") {
                        canonical(it, depth + 1)
                    }
                    value.isJsonObject -> value.asJsonObject.keySet().filter { it != "witness" }.sortedWith(codePointOrder)
                        .joinToString(prefix = "{", postfix = "}", separator = ",") {
                            quote(it) + ":" + canonical(value.asJsonObject.get(it), depth + 1)
                        }
                    value.asJsonPrimitive.isString -> quote(value.asString)
                    value.asJsonPrimitive.isNumber -> number(value.asString)
                    else -> value.toString()
                }
            }
            val bytes = canonical(json, 0).toByteArray(Charsets.UTF_8)
            val digest = ByteArray(32)
            Blake3Digest(256).apply { update(bytes, 0, bytes.size); doFinal(digest, 0) }
            return SaitoAccessScript(source, digest.joinToString("") { "%02x".format(it) })
        }

        private val integer = Regex("-?(0|[1-9][0-9]*)")
        private val minSigned = BigInteger.valueOf(Long.MIN_VALUE)
        private val maxUnsigned = BigInteger.ONE.shiftLeft(64) - BigInteger.ONE

        private fun number(raw: String): String {
            // serde_json retains i64/u64 integers, while overflow, exponents,
            // decimals and negative zero enter its floating-point representation.
            if (raw.length <= 21 && integer.matches(raw) && raw != "-0") {
                val value = raw.toBigInteger()
                if (value >= minSigned && value <= maxUnsigned) return value.toString()
            }
            val value = raw.toDouble()
            require(value.isFinite()) { "Access script number is outside the supported JSON range" }
            if (value == 0.0) return if (java.lang.Double.doubleToRawLongBits(value) < 0) "-0.0" else "0.0"
            val decimal = BigDecimal(DoubleToDecimal.toString(value)).stripTrailingZeros()
            val exponent = decimal.precision() - decimal.scale() - 1
            if (exponent in -5..15) return decimal.toPlainString() + if (decimal.scale() <= 0) ".0" else ""
            val digits = decimal.unscaledValue().abs().toString()
            return buildString {
                if (decimal.signum() < 0) append('-')
                append(digits[0])
                if (digits.length > 1) { append('.'); append(digits.substring(1)) }
                append('e'); append(exponent)
            }
        }

        // Rust sorts UTF-8 strings by scalar value, which differs from UTF-16 order
        // when an editor uses supplementary characters in object keys.
        private val codePointOrder = Comparator<String> { first, second ->
            var a = 0; var b = 0; var result = 0
            while (a < first.length && b < second.length) {
                val left = first.codePointAt(a); val right = second.codePointAt(b)
                if (left != right) { result = left.compareTo(right); break }
                a += Character.charCount(left); b += Character.charCount(right)
            }
            if (result != 0) result else (first.length - a).compareTo(second.length - b)
        }

        // serde_json preserves Unicode and HTML characters instead of HTML-safe or
        // JavaScript-specific escaping. Only JSON's required escapes affect bytes.
        private fun quote(value: String): String = buildString {
            append('"')
            var offset = 0
            while (offset < value.length) {
                val point = value.codePointAt(offset)
                require(point !in 0xd800..0xdfff) { "Invalid Unicode in access script" }
                when (point) {
                    0x22 -> append("\\\"")
                    0x5c -> append("\\\\")
                    8 -> append("\\b")
                    12 -> append("\\f")
                    10 -> append("\\n")
                    13 -> append("\\r")
                    9 -> append("\\t")
                    in 0..31 -> append("\\u%04x".format(point))
                    else -> appendCodePoint(point)
                }
                offset += Character.charCount(point)
            }
            append('"')
        }
    }
}
