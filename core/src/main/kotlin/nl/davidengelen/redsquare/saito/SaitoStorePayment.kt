package nl.davidengelen.redsquare.saito

import java.math.BigInteger

/** Immutable funded Store payment admitted by its direct or provider-specific reader. */
sealed interface SaitoStorePayment {
    val transaction: SaitoTransaction
    val buyer: String
    val store: String
    val escrow: String
    val nftId: String
    val quantity: BigInteger
    val amount: BigInteger
    val inputs: List<SaitoBalanceSnapshot.Slip>
}
