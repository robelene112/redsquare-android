package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64
import org.bouncycastle.crypto.digests.Blake3Digest

/** A Store-authenticated escrow spend, separate from selected-chain inclusion and current ownership. */
class SaitoStoreReceipt private constructor(val signature: String, val kind: Kind,
    val paymentInput: SaitoBalanceSnapshot.Slip, val asset: SaitoNft?, val reason: String?, val encoded: String) {
    enum class Kind { FULFILLMENT, REFUND }
    companion object {
        fun read(purchase: SaitoStorePurchase, encoded: String): SaitoStoreReceipt {
            require(encoded.length <= 2 * 1024 * 1024)
            val wire = Base64.getDecoder().decode(encoded)
            require(wire.size >= 211)
            val header = ByteBuffer.wrap(wire)
            val inputs = header.getInt(0); val outputs = header.getInt(4); val length = header.getInt(8)
            val hops = header.getInt(12)
            require(inputs in 1..255 && outputs in 1..255 && hops in 0..1024 && length in 1..SaitoTransaction.MAX_MESSAGE_BYTES)
            val start = 93 + 59 * (inputs + outputs)
            require(wire.size.toLong() == start.toLong() + length + 130L * hops && header.getInt(88) == 1)
            val message = wire.copyOfRange(start, start + length)
            val signature = wire.copyOfRange(16, 80).hex()
            require(SaitoWallet.verify(saitoTransactionPreimage(wire, message), signature, purchase.store))
            fun row(index: Int): SaitoNft.Row {
                val offset = 93 + index * 59
                return SaitoNft.Row(Base58.encode(wire.copyOfRange(offset, offset + 33)), SaitoBalanceSnapshot.Slip(
                    BigInteger(1, wire.copyOfRange(offset + 41, offset + 49)), BigInteger(1, wire.copyOfRange(offset + 49, offset + 57)),
                    wire[offset + 57].toInt() and 255, BigInteger(1, wire.copyOfRange(offset + 33, offset + 41)), wire[offset + 58].toInt() and 255))
            }
            val escrow = row(0)
            require(escrow.publicKey == purchase.escrow && escrow.position.amount == purchase.amount &&
                escrow.position.block.signum() > 0 && escrow.position.index == 0 && escrow.position.type == 0)
            val body = JsonParser.parseString(message.toString(Charsets.UTF_8)).asJsonObject
            require(body.get("module").asString == "Store")
            val witnesses = body.getAsJsonArray("access_scripts")
            val p2shInputs = (0 until inputs).filter { index -> row(index).let {
                it.position.type != 9 && Base58.decode(it.publicKey)[0] == 0.toByte()
            } }
            require(witnesses.size() == p2shInputs.size && p2shInputs.first() == 0)
            val outputBytes = (inputs until inputs + outputs).fold(ByteArray(0)) { bytes, index ->
                val offset = 93 + index * 59
                bytes + wire.copyOfRange(offset, offset + 41) + wire.copyOfRange(offset + 57, offset + 59)
            }
            val digest = ByteArray(32).also { result -> Blake3Digest(256).apply { update(outputBytes, 0, outputBytes.size); doFinal(result, 0) } }.hex()
            val witness = SaitoAccessScript.parse(witnesses[0].asString)
            val original = SaitoEnvelope.decode(purchase.transaction.envelope()).body.get("access_hash").asString
            require(witness.hash == original)
            val signatures = JsonParser.parseString(witness.source).asJsonObject.getAsJsonObject("witness").getAsJsonArray("signatures")
            require(signatures.size() == 1 && SaitoWallet.verify((escrow.utxoKey() + "|" + digest).toByteArray(), signatures[0].asString, purchase.store))
            return when (body.get("request").asString) {
                "list-asset" -> {
                    require(wire[92] == 8.toByte() && outputs >= 3)
                    val sale = body.getAsJsonObject("fulfill_sale")
                    require(sale.get("sale_signature").asString == purchase.transaction.id && sale.get("buyer").asString == purchase.author &&
                        sale.get("quantity").asString.toBigInteger() == purchase.quantity)
                    val asset = SaitoNft.fromSnapshot(row(inputs + 1), row(inputs), row(inputs + 2))
                    require(asset.owner == purchase.author && asset.id == purchase.nftId && asset.quantity == purchase.quantity)
                    require((1 until inputs - 2).any { index ->
                        row(index).position.type == 9 && row(index + 2).position.type == 9 && row(index).publicKey == asset.creator &&
                            Base58.decode(row(index + 2).publicKey).hex() == asset.id
                    })
                    SaitoStoreReceipt(signature, Kind.FULFILLMENT, escrow.position, asset, null, encoded)
                }
                "order-refund" -> {
                    require(wire[92] == 0.toByte() && inputs == 1 && outputs == 1)
                    require(body.get("order_tx_sig").asString == purchase.transaction.id && body.get("payment_tx_sig").asString == purchase.transaction.id &&
                        body.get("payment_output_index").asInt == 0 && body.get("payment_amount").asString.toBigInteger() == purchase.amount &&
                        body.get("buyer").asString == purchase.author && body.get("refund").asString == purchase.author)
                    val output = row(inputs)
                    require(output.publicKey == purchase.author && output.position.amount == purchase.amount && output.position.type == 0 && output.position.index == 0)
                    val reason = body.get("reason").asString.also { require(it.length in 1..2000) }
                    SaitoStoreReceipt(signature, Kind.REFUND, escrow.position, null, reason, encoded)
                }
                else -> throw IllegalArgumentException("Unsupported Store receipt")
            }
        }
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
