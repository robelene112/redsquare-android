package nl.davidengelen.redsquare.feed

import android.content.Context
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Public event IDs only. Enough markers for all five bounded in-memory activity kinds. */
internal class NotificationReadStore(context: Context, backend: SaitoBackend, owner: String) {
    init { require(SaitoWallet.isPublicKey(owner)) }
    private val storage = PublicStringStore(context, "notificationread.${backend.id}.$owner.v1", 2000)

    fun load(): Set<String> = valid(storage.load()).toSet()

    fun mark(ids: List<String>): Set<String> {
        require(ids.size <= 2000 && ids.all { it.matches(signature) })
        storage.update { previous -> (ids + valid(previous)).distinct() }
        return load()
    }

    private fun valid(saved: PublicStringStore.Loaded): List<String> {
        check(!saved.unreadable && saved.values.all { it.matches(signature) }) { "Unreadable notification read status" }
        return saved.values
    }

    private companion object { val signature = Regex("[0-9a-f]{128}") }
}
