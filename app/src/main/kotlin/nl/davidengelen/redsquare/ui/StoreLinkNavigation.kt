package nl.davidengelen.redsquare.ui

import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.StoreLink

/** A temporary Store read retains its exact module origin, including an existing linked article. */
internal data class StoreLinkEntry(val target: StoreLink, val owner: String, val savedBackend: SaitoBackend,
    val origin: AppModule, val originStack: StackLinkEntry?, val originStackScene: String? = null, val originStore: StoreLinkEntry? = null,
    val scene: String = java.util.UUID.randomUUID().toString()) : java.io.Serializable {
    val depth: Int get() = 1 + (originStore?.depth ?: 0)
    val stateKey: String get() = "$owner:${savedBackend.id}:STORE:link:$scene"
    fun retainedSceneKeys(): Set<String> = buildSet {
        var entry: StoreLinkEntry? = this@StoreLinkEntry
        while (entry != null) {
            add(entry.stateKey)
            val origin = entry.originStack
            val stackScene = entry.originStackScene
            if (origin != null && stackScene != null) add(linkedStackStateKey(origin.owner, origin.savedBackend, stackScene))
            entry = entry.originStore
        }
    }
}

internal fun linkedStackStateKey(owner: String, backend: SaitoBackend, scene: String): String =
    "$owner:${backend.id}:STACK:link:$scene"
