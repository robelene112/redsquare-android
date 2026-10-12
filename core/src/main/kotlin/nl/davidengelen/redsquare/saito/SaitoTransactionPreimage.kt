package nl.davidengelen.redsquare.saito

import java.nio.ByteBuffer

/** Shared raw signature shape. Callers retain their framing, type and trust checks. */
internal fun saitoTransactionPreimage(wire: ByteArray, message: ByteArray): ByteArray {
    require(wire.size >= 93)
    val header = ByteBuffer.wrap(wire)
    val inputs = header.getInt(0)
    val outputs = header.getInt(4)
    require(inputs in 0..255 && outputs in 0..255)
    val slipCount = inputs + outputs
    require(wire.size >= 93 + 59 * slipCount)
    val preimage = ByteBuffer.allocate(Math.addExact(16 + 43 * slipCount, message.size))
    preimage.put(wire, 80, 8)
    repeat(slipCount) { index ->
        val offset = 93 + index * 59
        preimage.put(wire, offset, 41)
        preimage.put(wire, offset + 57, 2)
    }
    preimage.put(wire, 88, 4)
    preimage.putInt(wire[92].toInt() and 255)
    preimage.put(message)
    return preimage.array()
}
