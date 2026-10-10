package nl.davidengelen.redsquare.saito

import org.bouncycastle.crypto.digests.Blake3Digest
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Saito's identicon.js2.3.3 palette and mirrored5x5 geometry, without executing SVG. */
data class SaitoIdenticon(val foreground: Int, val cells: List<Boolean>) {
    companion object {
        const val BACKGROUND: Int = -986896 // Opaque RGB240,240,240.

        fun forAddress(address: String): SaitoIdenticon {
            require(address.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}")))
            val bytes = address.toByteArray(Charsets.UTF_8)
            val hash = ByteArray(32).also { result ->
                Blake3Digest(256).apply { update(bytes, 0, bytes.size); doFinal(result, 0) }
            }.joinToString("") { "%02x".format(it) }
            val hue = hash.takeLast(7).toLong(16).toDouble() / 0xfffffff * 6
            val chroma = (1 - abs(2 * 0.4 - 1)) * 0.6
            val secondary = chroma * (1 - abs(hue % 2 - 1))
            val components = when (floor(hue).toInt() % 6) {
                0 -> listOf(chroma, secondary, 0.0)
                1 -> listOf(secondary, chroma, 0.0)
                2 -> listOf(0.0, chroma, secondary)
                3 -> listOf(0.0, secondary, chroma)
                4 -> listOf(secondary, 0.0, chroma)
                else -> listOf(chroma, 0.0, secondary)
            }.map { ((it + 0.4 - chroma / 2) * 255).roundToInt() }
            val foreground = (255 shl 24) or (components[0] shl 16) or (components[1] shl 8) or components[2]
            val cells = (0 until 25).map { position ->
                val row = position / 5
                val index = when (position % 5) { 2 -> row; 1, 3 -> 5 + row; else -> 10 + row }
                hash[index].digitToInt(16) % 2 == 0
            }
            return SaitoIdenticon(foreground, cells)
        }
    }
}
