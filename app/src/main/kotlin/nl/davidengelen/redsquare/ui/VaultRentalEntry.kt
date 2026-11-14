package nl.davidengelen.redsquare.ui

import nl.davidengelen.redsquare.saito.SaitoBackend

/** Temporary rental navigation keeps the saved node and original Store scene separate. */
internal data class VaultRentalEntry(val owner: String, val savedBackend: SaitoBackend, val node: SaitoBackend,
    val fileId: String, val orderId: String, val originStore: StoreLinkEntry?) : java.io.Serializable {
    init { require(fileId.matches(Regex("[0-9a-f]{128}")) && orderId.matches(Regex("[0-9a-f]{128}"))) }
}
