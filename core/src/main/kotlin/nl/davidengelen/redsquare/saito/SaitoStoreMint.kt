package nl.davidengelen.redsquare.saito

import com.google.gson.JsonObject
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Base64

/** One exact public Store mint, with optional immutable Vault rental terms. */
class SaitoStoreMint private constructor(val transaction: SaitoTransaction, val author: String, val nftId: String,
    val title: String, val description: String, val image: String, inputs: List<SaitoBalanceSnapshot.Slip>, val rental: StoreRentalSource? = null, val quantity: BigInteger = BigInteger.ONE) {
    val inputs: List<SaitoBalanceSnapshot.Slip> = java.util.Collections.unmodifiableList(inputs.toList())
    val deposit: BigInteger get() = SaitoVaultMint.DEPOSIT * quantity
    val type: String get() = if (rental == null) "image" else "store-nft-rental"
    companion object {
        const val MAX_IMAGE_BYTES = 512 * 1024
        fun create(wallet: SaitoWallet, inputs: List<SaitoBalanceSnapshot.Slip>, title: String, description: String,
            image: String, timestamp: Long): SaitoStoreMint {
            require(SaitoFundingInputs.checked(inputs) >= SaitoVaultMint.DEPOSIT && timestamp >= 0)
            validate(title, description, image)
            val first = inputs.first()
            val id = ByteBuffer.allocate(33).putLong(first.block.toLong()).putLong(first.ordinal.toLong()).put(first.index.toByte()).put("image".toByteArray()).array().hex()
            val body = JsonObject().apply {
                add("data", JsonObject().apply { addProperty("image", image) }); addProperty("title", title); addProperty("description", description)
            }
            return restore(SaitoTransaction.createVaultMint(wallet, body, timestamp, inputs, id, SaitoVaultMint.DEPOSIT).encoded())
        }
        fun createRental(wallet: SaitoWallet, inputs: List<SaitoBalanceSnapshot.Slip>, key: VaultKey,
            hours: Int, quantity: BigInteger, title: String, description: String, image: String, timestamp: Long): SaitoStoreMint {
            val rental = StoreRentalSource.fromKey(wallet, key, hours)
            require(quantity.signum() > 0 && quantity <= BigInteger("9007199254740991") && timestamp >= 0)
            val deposit = SaitoVaultMint.DEPOSIT * quantity
            require(SaitoFundingInputs.checked(inputs) >= deposit)
            validate(title, description, image)
            val first = inputs.first()
            val id = ByteBuffer.allocate(33).putLong(first.block.toLong()).putLong(first.ordinal.toLong()).put(first.index.toByte()).put("store-nft-rental".toByteArray()).array().hex()
            val body = JsonObject().apply { add("data", rental.data(image)); addProperty("title", title); addProperty("description", description) }
            return restore(SaitoTransaction.createVaultMint(wallet, body, timestamp, inputs, id, deposit, quantity).encoded())
        }
        fun restore(encoded: String): SaitoStoreMint {
            require(encoded.length <= 2 * 1024 * 1024)
            val bytes = Base64.getDecoder().decode(encoded)
            require(Base64.getEncoder().encodeToString(bytes) == encoded && bytes.size >= 330)
            val header = ByteBuffer.wrap(bytes); val inputs = header.getInt(0); val outputs = header.getInt(4)
            require(inputs in 1..254 && outputs in 3..4 && header.getInt(12) == 0 && header.getInt(88) == 1 && bytes[92] == 8.toByte() &&
                header.getLong(80) >= 0 && header.getInt(8) in 1..SaitoTransaction.MAX_MESSAGE_BYTES && bytes.size == 93 + 59 * (inputs + outputs) + header.getInt(8))
            val transaction = SaitoTransaction.fromVaultMintBytes(bytes)
            val signed = SaitoEnvelope.decodeBound(transaction.envelope())
            fun row(index: Int): SaitoNft.Row {
                val p = 93 + index * 59
                return SaitoNft.Row(Base58.encode(bytes.copyOfRange(p, p + 33)), SaitoFundingInputs.slip(bytes, p))
            }
            val funding = (0 until inputs).map { row(it).also { value -> require(value.publicKey == signed.author) }.position }
            val total = SaitoFundingInputs.checked(funding)
            require(total >= SaitoVaultMint.DEPOSIT)
            val first = funding.first()
            val nft = SaitoNft.fromSnapshot(row(inputs + 1), row(inputs), row(inputs + 2))
            require(nft.type in setOf("image", "store-nft-rental"))
            val quantity = nft.quantity.also { require(it.signum() > 0 && it <= BigInteger("9007199254740991")) }
            require(nft.type != "image" || quantity == BigInteger.ONE)
            val deposit = SaitoVaultMint.DEPOSIT * quantity
            require(total >= deposit)
            val id = ByteBuffer.allocate(33).putLong(first.block.toLong()).putLong(first.ordinal.toLong()).put(first.index.toByte()).put(nft.type.toByteArray()).array().hex()
            val zero = SaitoBalanceSnapshot.Slip(BigInteger.ZERO, BigInteger.ZERO, 0, BigInteger.ZERO, 0)
            require(row(inputs).publicKey == signed.author && row(inputs).position == zero.copy(amount = quantity, type = 9) &&
                row(inputs + 1).publicKey == signed.author && row(inputs + 1).position == zero.copy(index = 1, amount = deposit) &&
                Base58.decode(row(inputs + 2).publicKey).hex() == id && row(inputs + 2).position == zero.copy(index = 2, type = 9))
            val change = total - deposit
            if (change.signum() == 0) require(outputs == 3) else require(outputs == 4 && row(inputs + 3).publicKey == signed.author && row(inputs + 3).position == zero.copy(index = 3, amount = change))
            val body = signed.body
            require(body.keySet() == setOf("data", "title", "description"))
            val data = body.getAsJsonObject("data")
            val rental = if (nft.type == "store-nft-rental") StoreRentalSource.read(signed.author, data) else null
            require(data.keySet() == if (rental == null) setOf("image") else rental.data(data.get("image").asString).keySet())
            val title = body.get("title").asString; val description = body.get("description").asString; val image = body.getAsJsonObject("data").get("image").asString
            validate(title, description, image)
            return SaitoStoreMint(transaction, signed.author, id, title, description, image, funding, rental, quantity)
        }
        private fun validate(title: String, description: String, image: String) {
            require(title.isNotBlank() && title.length <= 10_000 && description.length <= 200_000 && image.length <= MAX_IMAGE_BYTES * 4 / 3 + 128 &&
                image.startsWith("data:image/") && RedSquareImages.safeSource(image) == image)
            val bytes = Base64.getDecoder().decode(image.substringAfter(','))
            require(bytes.size in 1..MAX_IMAGE_BYTES && Base64.getEncoder().encodeToString(bytes) == image.substringAfter(','))
        }
        private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
