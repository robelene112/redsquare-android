package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.SaitoWallet

enum class SpaceActivityKind { MENTION, REPLY, EVERYONE, REACTION, MESSAGE }

data class SpaceActivityGroup(val id: String, val target: SpaceDestination, val kind: SpaceActivityKind,
    val latestActor: String, val actorCount: Long, val acceptedAt: Long, val position: Long, val unread: Boolean) {
    override fun toString() = "Space activity group"
}

data class SpaceActivityPage(val groups: List<SpaceActivityGroup>, val next: String?)

internal class SpaceActivityIndex(private val store: SpaceClientStore.Transaction, private val node: String, private val authority: String) {
    private val indexPrefix = "activity/recent/"

    fun observe(address: SpaceDestination, kind: SpaceActivityKind, actor: String, acceptedAt: Long, position: Long) {
        require(address.community.node == node && address.community.serviceKey == authority)
        require(SaitoWallet.isPublicKey(actor) && position >= 0 && acceptedAt >= 0)
        val id = keyOf(address, kind)
        val actorKey = "activity/actors/$id/$actor"
        val previousActor = store.get(actorKey)?.toString(Charsets.UTF_8)?.toLong()
        if (previousActor != null && previousActor >= position) return
        val old = group(id)
        val count = (old?.actorCount ?: 0) + if (previousActor == null) 1 else 0
        val updated = if (old == null || position > old.position) {
            old?.let { store.remove(orderKey(it)) }
            SpaceActivityGroup(id, address, kind, actor, count, acceptedAt, position, true)
        } else old.copy(actorCount = count)
        store.put(actorKey, position.toString().toByteArray(Charsets.UTF_8))
        store.put(groupKey(id), encode(updated))
        store.put(orderKey(updated), id.toByteArray(Charsets.UTF_8))
    }

    fun page(after: String?, limit: Int, accessible: (SpaceActivityGroup) -> Boolean): SpaceActivityPage {
        require(limit in 1..20 && (after == null || after.startsWith(indexPrefix) && after.length <= 512))
        val rows = store.page(indexPrefix, after?.plus('\u0000') ?: indexPrefix, 61)
        val groups = ArrayList<SpaceActivityGroup>(limit)
        var examined = 0
        for (row in rows.take(60)) {
            examined++
            val group = requireNotNull(group(row.value.toString(Charsets.UTF_8)))
            if (accessible(group)) groups.add(group)
            if (groups.size == limit) break
        }
        return SpaceActivityPage(groups, if (examined < rows.size) rows[examined - 1].key else null)
    }

    fun markSeen(id: String, through: Long) {
        val current = group(id) ?: return
        require(through in 0..current.position)
        val seenKey = "activity/seen/$id"
        val previous = store.get(seenKey)?.toString(Charsets.UTF_8)?.toLong() ?: -1L
        if (previous < through) store.put(seenKey, through.toString().toByteArray(Charsets.UTF_8))
    }

    fun markDestinationSeen(address: SpaceDestination) {
        if (address.record == null || address.kind != SpaceDestination.Kind.MESSAGE) return
        for (kind in SpaceActivityKind.entries) {
            val current = group(keyOf(address, kind)) ?: continue
            if (current.target == address) markSeen(current.id, current.position)
        }
    }

    fun wasNotified(group: SpaceActivityGroup): Boolean = (store.get("activity/notified/${group.id}")
        ?.toString(Charsets.UTF_8)?.toLong() ?: -1L) >= group.position

    fun markNotified(group: SpaceActivityGroup) {
        if (!wasNotified(group)) store.put("activity/notified/${group.id}", group.position.toString().toByteArray(Charsets.UTF_8))
    }

    private fun keyOf(address: SpaceDestination, kind: SpaceActivityKind): String {
        val record = requireNotNull(address.record)
        val anchor = if (kind == SpaceActivityKind.REPLY) address.thread ?: record else record
        return "${address.community.spaceId}/${address.channel}/${kind.name}/$anchor"
    }

    private fun groupKey(id: String) = "activity/groups/$id"

    private fun orderKey(group: SpaceActivityGroup) = indexPrefix +
        (Long.MAX_VALUE - group.acceptedAt).toString().padStart(20, '0') + "/" +
        (Long.MAX_VALUE - group.position).toString().padStart(20, '0') + "/" + group.id

    private fun group(id: String): SpaceActivityGroup? {
        val saved = store.get(groupKey(id)) ?: return null
        val body = JsonParser.parseString(saved.toString(Charsets.UTF_8)).asJsonObject
        val position = body.get("position").asLong
        val through = store.get("activity/seen/$id")?.toString(Charsets.UTF_8)?.toLong() ?: -1L
        val address = SpaceDestination(
            SpacePublicReference(node, authority, body.get("space").asString),
            body.get("channel").asString,
            SpaceDestination.Kind.MESSAGE,
            body.get("record").asString,
            body.get("thread")?.asString
        )
        return SpaceActivityGroup(id, address, SpaceActivityKind.valueOf(body.get("kind").asString),
            body.get("actor").asString, body.get("count").asLong, body.get("accepted").asLong, position, position > through)
    }

    private fun encode(group: SpaceActivityGroup) = JsonObject().apply {
        addProperty("space", group.target.community.spaceId)
        addProperty("channel", group.target.channel)
        addProperty("kind", group.kind.name)
        addProperty("record", group.target.record)
        group.target.thread?.let { addProperty("thread", it) }
        addProperty("actor", group.latestActor)
        addProperty("count", group.actorCount)
        addProperty("accepted", group.acceptedAt)
        addProperty("position", group.position)
    }.toString().toByteArray(Charsets.UTF_8)
}
