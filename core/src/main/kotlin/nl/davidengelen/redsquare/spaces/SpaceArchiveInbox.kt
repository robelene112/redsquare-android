package nl.davidengelen.redsquare.spaces

import nl.davidengelen.redsquare.saito.SaitoWallet

/** Device-protected staging for imported archive keys before their service is
 * known on this installation. A service consumes only its own exact scopes. */
class SpaceArchiveInbox(private val owner: String, private val store: SpaceClientStore) {
    init { require(SaitoWallet.isPublicKey(owner)) }

    fun retain(keys: List<SpaceHistoryKeys>) {
        if (keys.isEmpty()) return
        require(keys.size <= AccountRecoveryBackup.MAX_HISTORY_SCOPES && keys.all { it.owner == owner })
        store.transaction {
            checkOwner(initialize = true)
            var count = get("archive-count")?.decodeToString()?.toInt() ?: 0
            require(count in 0..AccountRecoveryBackup.MAX_HISTORY_SCOPES)
            for (archive in keys) {
                val scope = archive.scope
                val key = "archive/${scope.serviceKey}/${scope.spaceId}/${scope.channelId}/${scope.generation}"
                val previous = get(key)?.let { SpaceHistoryKeys.restore(it, owner) }
                if (previous == null) {
                    require(count < AccountRecoveryBackup.MAX_HISTORY_SCOPES)
                    count++
                }
                put(key, (previous?.merge(archive) ?: archive).save())
            }
            put("archive-count", count.toString().encodeToByteArray())
        }
    }

    fun records(service: String? = null): List<SpaceHistoryKeys> {
        require(service == null || SaitoWallet.isPublicKey(service))
        return store.transaction {
            if (get("owner") == null) return@transaction emptyList()
            checkOwner()
            val prefix = if (service == null) "archive/" else "archive/$service/"
            buildList {
                var cursor = prefix
                while (true) {
                    val rows = page(prefix, cursor)
                    rows.forEach { add(SpaceHistoryKeys.restore(it.value, owner)) }
                    require(size <= AccountRecoveryBackup.MAX_HISTORY_SCOPES) { "Account backup has too many history scopes" }
                    if (rows.size < 50) break
                    cursor = rows.last().key + '\u0000'
                }
            }
        }
    }

    private fun SpaceClientStore.Transaction.checkOwner(initialize: Boolean = false) {
        val identity = get("owner")?.decodeToString(throwOnInvalidSequence = true)
        if (identity == null) {
            require(initialize)
            put("owner", owner.encodeToByteArray())
        } else require(identity == owner)
    }
}
