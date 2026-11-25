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

    fun reconcile(address: SpaceDestination, source: String, kind: SpaceActivityKind?, actor: String, acceptedAt: Long, position: Long) {
        require(address.community.node == node && address.community.serviceKey == authority)
        require(SaitoWallet.isPublicKey(actor) && position >= 0 && acceptedAt >= 0)
        val slot = "activity/source/${address.community.spaceId}/${address.channel}/$source"
        val previous = store.get(slot)?.let(::json)
        val revision = previous?.get("revision")?.asLong ?: -1
        if (revision >= position) return
        val previousId = previous?.get("group")?.asString
        val groupId = kind?.let { keyOf(address, it) }
        if (groupId == null && previous == null) return
        val observedAt = if (groupId != null && groupId == previousId) requireNotNull(previous).get("position").asLong else position
        if (previousId != null && previousId != groupId) removeObservation(previousId, actor, requireNotNull(previous).get("position").asLong, source)
        if (groupId != null && groupId != previousId) {
            val current = group(groupId)
            val actorKey = actorKey(groupId, actor)
            val actorEvents = count(actorKey)
            store.put(actorKey, (actorEvents + 1).toString().toByteArray(Charsets.UTF_8))
            val actorCount = (current?.actorCount ?: 0) + if (actorEvents == 0L) 1 else 0
            val observation = SpaceActivityGroup(groupId, address, requireNotNull(kind), actor, 0, acceptedAt, position, true)
            store.put(eventKey(groupId, position, source), encode(observation))
            saveGroup(current, (if (current == null || position > current.position) observation else current).copy(actorCount = actorCount))
        }
        store.put(slot, JsonObject().apply {
            addProperty("revision", position)
            if (groupId != null) {
                addProperty("group", groupId)
                addProperty("position", observedAt)
            }
        }.toString().toByteArray(Charsets.UTF_8))
    }

    private fun removeObservation(id: String, actor: String, position: Long, source: String) {
        val current = requireNotNull(group(id))
        val actorKey = actorKey(id, actor)
        val remaining = count(actorKey) - 1
        require(remaining >= 0)
        if (remaining == 0L) store.remove(actorKey) else store.put(actorKey, remaining.toString().toByteArray(Charsets.UTF_8))
        store.remove(eventKey(id, position, source))
        val count = current.actorCount - if (remaining == 0L) 1 else 0
        val latest = if (current.position != position) current else store.page(eventPrefix(id), limit = 1).firstOrNull()
            ?.let { decode(id, json(it.value)) }
        saveGroup(current, latest?.copy(actorCount = count))
    }

    private fun saveGroup(old: SpaceActivityGroup?, updated: SpaceActivityGroup?) {
        old?.let { store.remove(orderKey(it)) }
        if (updated == null) old?.let { store.remove(groupKey(it.id)) }
        else {
            store.put(groupKey(updated.id), encode(updated))
            store.put(orderKey(updated), updated.id.toByteArray(Charsets.UTF_8))
        }
    }

    private fun eventPrefix(id: String) = "activity/events/$id/"
    private fun eventKey(id: String, position: Long, source: String) =
        eventPrefix(id) + ((Long.MAX_VALUE - position).toString().padStart(20, '0')) + "/$source"
    private fun actorKey(id: String, actor: String) = "activity/actors/$id/$actor"
    private fun count(key: String) = store.get(key)?.toString(Charsets.UTF_8)?.toLong() ?: 0L
    private fun json(bytes: ByteArray) = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject

    fun page(after: String?, limit: Int, since: Long?, accessible: (SpaceActivityGroup) -> Boolean): SpaceActivityPage {
        require(limit in 1..20 && (after == null || after.startsWith(indexPrefix) && after.length <= 512))
        val rows = store.page(indexPrefix, after?.plus('\u0000') ?: indexPrefix, 61)
        val groups = ArrayList<SpaceActivityGroup>(limit)
        var examined = 0
        for (row in rows.take(60)) {
            examined++
            val group = requireNotNull(group(row.value.toString(Charsets.UTF_8)))
            if (since != null && group.acceptedAt < since) return SpaceActivityPage(groups, null)
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
        return store.get(groupKey(id))?.let { decode(id, json(it)) }
    }

    private fun decode(id: String, body: JsonObject): SpaceActivityGroup {
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
