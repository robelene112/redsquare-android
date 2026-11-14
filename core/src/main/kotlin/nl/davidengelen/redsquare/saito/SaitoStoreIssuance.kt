package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64

/** Provider-funded Saito leg of one retained external payment. It does not prove chain inclusion. */
class SaitoStoreIssuance private constructor(override val transaction: SaitoTransaction,
    val carrier: SaitoStoreCarrier, val invoice: SaitoStoreCrypto.Invoice, val author: String,
    override val amount: BigInteger, inputs: List<SaitoBalanceSnapshot.Slip>, val networkFee: BigInteger,
    val externalHash: String) : SaitoStorePayment {
    override val buyer: String get() = carrier.buyer
    override val store: String get() = carrier.store
    override val escrow: String get() = carrier.escrow
    override val nftId: String get() = carrier.nftId
    override val quantity: BigInteger get() = carrier.quantity
    override val inputs: List<SaitoBalanceSnapshot.Slip> = java.util.Collections.unmodifiableList(inputs.toList())

    companion object {
        data class Discovery(val payment: SaitoStoreIssuance?, val cursor: SaitoStoreChain.Cursor?)

        fun restore(carrier: SaitoStoreCarrier, invoice: SaitoStoreCrypto.Invoice, external: String, encoded: String): SaitoStoreIssuance {
            require(encoded.length <= 2 * 1024 * 1024 && external.isNotBlank() && external.length <= 2048 && external.none(Char::isISOControl))
            val wire = Base64.getDecoder().decode(encoded)
            require(Base64.getEncoder().encodeToString(wire) == encoded)
            return readOriginal(carrier, invoice, external, wire)
        }

        /** Retaining a signed original does not itself establish selected-chain inclusion or ownership. */
        fun discover(carrier: SaitoStoreCarrier, invoice: SaitoStoreCrypto.Invoice, external: String,
            snapshot: SaitoBalanceSnapshot, cursor: SaitoStoreChain.Cursor? = null,
            fetchBlock: (String) -> ByteArray?): Discovery {
            require(snapshot.publicKey == carrier.buyer && invoice.carrierSignature == carrier.transaction.id)
            require(external.isNotBlank() && external.length <= 2048 && external.none(Char::isISOControl))
            var received = 0L
            fun scan(start: SaitoStoreChain.Cursor, fresh: Boolean): Discovery {
                var next = start
                try {
                    repeat(16) { index ->
                        if (next.height.signum() <= 0) return Discovery(null, null)
                        val bytes = fetchBlock(next.hash) ?: return Discovery(null, next)
                        received += bytes.size
                        if (received > 32L * 1024 * 1024) return Discovery(null, next)
                        val block = SaitoFullBlock.parse(bytes)
                        if (block.hash != next.hash || block.height != next.height ||
                            (fresh && index == 0 && block.timestamp != BigInteger.valueOf(snapshot.timestamp))) return Discovery(null, next)
                        block.storeIssuance(carrier, invoice, external)?.let { return Discovery(it, null) }
                        next = SaitoStoreChain.Cursor(next.height - BigInteger.ONE, block.previousHash)
                    }
                } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
                catch (_: Exception) { return Discovery(null, next) }
                return Discovery(null, next.takeIf { it.height.signum() > 0 })
            }
            val older = cursor?.takeIf { it.height < snapshot.height }?.let { scan(it, false) }
            if (older?.payment != null) return older
            val recent = scan(SaitoStoreChain.Cursor(snapshot.height, snapshot.blockHash), true)
            return if (recent.payment != null) recent else Discovery(null, if (older != null) older.cursor else recent.cursor)
        }

        fun read(carrier: SaitoStoreCarrier, invoice: SaitoStoreCrypto.Invoice, response: String): SaitoStoreIssuance {
            require(response.toByteArray().size <= 2 * 1024 * 1024)
            val status = JsonParser.parseString(response).asJsonObject
            require(status.get("found").asJsonPrimitive.isBoolean && status.get("found").asBoolean)
            val current = SaitoStoreCrypto.readInvoice(carrier, invoice.currency, invoice.provider, response)
            require(current.id == invoice.id && current.carrierSignature == invoice.carrierSignature &&
                current.address == invoice.address && current.mixinId == invoice.mixinId && current.amount.compareTo(invoice.amount) == 0 &&
                current.issuedSaito.compareTo(invoice.issuedSaito) == 0 && current.issuer == invoice.issuer && current.expiresAt == invoice.expiresAt)
            // A later provider failure cannot erase an already signed original payment.
            // Authenticity and selected-chain observation, rather than this report, govern it.
            val external = status.get("inbound_payment_uuid").asString.also { require(it.isNotBlank() && it.length <= 2048 && it.none(Char::isISOControl)) }
            val original = SaitoEnvelope.originalNetworkBytes(status.get("outbound_tx").asString)
            val result = readOriginal(carrier, invoice, external, original)
            require(status.get("outbound_tx_sig").asString == result.transaction.id)
            return result
        }

        internal fun readOriginal(carrier: SaitoStoreCarrier, invoice: SaitoStoreCrypto.Invoice, external: String,
            original: ByteArray): SaitoStoreIssuance {
            val header = ByteBuffer.wrap(original)
            val count = header.getInt(0)
            require(count in 2..255 && header.getInt(4) == 2 && header.getInt(12) == 0 && header.getInt(88) == 1 && original[92] == 0.toByte() &&
                header.getLong(80) >= 0 && header.getInt(8) in 1..SaitoTransaction.MAX_MESSAGE_BYTES && original.size == 93 + 59 * (count + 2) + header.getInt(8))
            val transaction = SaitoTransaction.fromFundedBytes(original)
            val signed = SaitoEnvelope.decode(transaction.envelope())
            require(signed.author == invoice.issuer && carrier.transaction.id == invoice.carrierSignature)
            val body = SaitoEnvelope.decode(carrier.transaction.envelope()).body.deepCopy().apply {
                addProperty("inbound_payment_uuid", external); addProperty("transfer_uuid", invoice.id)
            }
            require(signed.body == body)
            require(original.copyOfRange(126, 152).all { it == 0.toByte() })
            val inputs = (1 until count).map { index ->
                val offset = 93 + 59 * index
                require(Base58.encode(original.copyOfRange(offset, offset + 33)) == signed.author)
                SaitoFundingInputs.slip(original, offset)
            }
            val total = SaitoFundingInputs.checked(inputs)
            val offset = 93 + count * 59
            val payment = SaitoFundingInputs.slip(original, offset)
            val change = SaitoFundingInputs.slip(original, offset + 59)
            val zero = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, BigInteger.ZERO, 0)
            require(Base58.encode(original.copyOfRange(offset, offset + 33)) == carrier.escrow &&
                Base58.encode(original.copyOfRange(offset + 59, offset + 92)) == signed.author &&
                payment == zero.copy(amount = invoice.issuedSaito.movePointRight(8).toBigIntegerExact()) &&
                change == zero.copy(index = 1, amount = change.amount))
            val fee = total - payment.amount - change.amount
            require(payment.amount.signum() > 0 && fee.signum() >= 0)
            return SaitoStoreIssuance(transaction, carrier, invoice, signed.author, payment.amount, inputs, fee, external)
        }
    }
}
