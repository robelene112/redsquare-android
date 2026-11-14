package nl.davidengelen.redsquare.ui

import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.StoreLink

/** A temporary Store read retains its exact module origin, including an existing linked article. */
internal data class StoreLinkEntry(val target: StoreLink, val owner: String, val savedBackend: SaitoBackend,
    val origin: AppModule, val originStack: StackLinkEntry?, val originStore: StoreLinkEntry? = null,
    val scene: String = java.util.UUID.randomUUID().toString()) : java.io.Serializable {
    val depth: Int get() = 1 + (originStore?.depth ?: 0)
}
