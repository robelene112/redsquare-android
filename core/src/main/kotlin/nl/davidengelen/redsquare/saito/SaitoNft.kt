package nl.davidengelen.redsquare.saito

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** An unspent NFT tuple reported by the selected node, not independent consensus proof. */
class SaitoNft private constructor(val id: String, val creator: String, val owner: String,
    val quantity: BigInteger, val deposit: BigInteger, val type: String, witnesses: List<String>) {
    val witnesses: List<String> = java.util.Collections.unmodifiableList(witnesses.toList())

    internal data class Row(val publicKey: String, val position: SaitoBalanceSnapshot.Slip) {
        fun utxoKey(): String = ByteBuffer.allocate(59).put(Base58.decode(publicKey))
            .putLong(position.block.toLong()).putLong(position.ordinal.toLong()).put(position.index.toByte())
            .putLong(position.amount.toLong()).put(position.type.toByte()).array().joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** Snapshot order is owner middle, creator before, metadata after, matching the Rust wallet. */
        internal fun fromSnapshot(middle: Row, before: Row, after: Row): SaitoNft {
            val owner = middle.position
            require(owner.type in setOf(0, 1) && owner.index in 1..254)
            require(SaitoWallet.isPublicKey(middle.publicKey) && SaitoWallet.isPublicKey(before.publicKey))
            require(before.position.type == 9 && after.position.type == 9 && before.position.amount.signum() > 0 &&
                after.position.amount == BigInteger.ZERO)
            require(before.position.block == owner.block && after.position.block == owner.block &&
                before.position.ordinal == owner.ordinal && after.position.ordinal == owner.ordinal &&
                before.position.index == owner.index - 1 && after.position.index == owner.index + 1)
            val metadata = Base58.decode(after.publicKey).also { require(it.size == 33) }
            val typeBytes = metadata.copyOfRange(17, 33).dropLastWhile { it == 0.toByte() }.toByteArray()
            val type = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(typeBytes)).toString()
            require(type.isNotBlank() && type.none { it.isISOControl() })
            return SaitoNft(metadata.joinToString("") { "%02x".format(it) }, before.publicKey, middle.publicKey,
                before.position.amount, owner.amount, type, listOf(before.utxoKey(), middle.utxoKey(), after.utxoKey()))
        }
    }
}
