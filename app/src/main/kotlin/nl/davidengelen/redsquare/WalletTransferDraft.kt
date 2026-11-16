package nl.davidengelen.redsquare

import nl.davidengelen.redsquare.saito.SaitoBackend

/** Editable input only. A review, signed candidate or authentication grant is never restored here. */
internal data class WalletTransferDraft(
    val owner: String,
    val backend: SaitoBackend,
    val recipient: String = "",
    val amount: String = "",
    val fee: String = "0",
    val memo: String = "",
) : java.io.Serializable
