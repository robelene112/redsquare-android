package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.ControlledFeedPeer
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.*
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Actual admission, removal and promotion. No copied MLS sender or injected UI state. */
internal class SpacesPrivateReadinessFixture(ownerStore: SpaceClientStore, holderStore: SpaceClientStore,
    private val peer: ControlledFeedPeer, private val root: Path, private val service: SaitoWallet,
    private val clock: Clock, private val remote: SpacesServiceStore) {
    private val ownerWallet = wallet(3)
    private val holderWallet = wallet(4)
    private val actor = wallet(5)
    private val allowed = wallet(6)
    private val grant = wallet(7)
    private val retry = wallet(8)
    private val owner = client(ownerWallet, ownerStore)
    private val holder = client(holderWallet, holderStore)
    private lateinit var space: String
    private lateinit var channel: String
    private val fixtures = JsonObject()
    private val controls = ConcurrentSkipListMap<Long, String>()
    private val rejected = CopyOnWriteArrayList<String>()
    private val rejectNext = AtomicBoolean()
    private var promoted = false
    val names = mapOf(ownerWallet.publicKey to "ReadinessOwner@saito", holderWallet.publicKey to "KeyHolder@saito",
        actor.publicKey to "WaitingAdmin@saito", allowed.publicKey to "AllowedMember@saito",
        grant.publicKey to "InvitedMember@saito", retry.publicKey to "RetryMember@saito")

    private fun wallet(number: Int) = SaitoWallet.fromPrivateKey(number.toString(16).padStart(64, '0'))
    private fun client(wallet: SaitoWallet, store: SpaceClientStore) =
        SpaceClient(wallet, "https://saito.davidengelen.nl", service.publicKey, store, clock) { deliver(it) }
    private fun deliver(original: SaitoTransaction): String = remote.exchange(original.envelope()).also { response ->
        val signed = SaitoEnvelope.decode(response)
        require(signed.author == service.publicKey)
        val body = signed.body
        if (body.get("stream")?.asString == "control") {
            require(body.get("event").asString == original.envelope())
            val position = body.get("position").asLong
            controls.putIfAbsent(position, response)?.let { require(it == response) }
        }
        if (body.get("stream")?.asString == "rejection") {
            require(body.get("event").asString == original.envelope())
            rejected.addIfAbsent(response)
        }
    }

    /** The competing control is a real independently signed owner operation. */
    fun exchangeFromPeer(original: SaitoTransaction): String = runBlocking {
        val body = SaitoEnvelope.decode(original.envelope()).body
        if (body.get("kind")?.asString == "grant-channel-access" && body.get("member")?.asString == retry.publicKey &&
            rejectNext.compareAndSet(true, false)) {
            owner.refresh(space)
            owner.setListed(space, !owner.profile(space).listed)
            owner.flush(space)
        }
        deliver(original)
    }

    suspend fun seed() {
        space = owner.create(SpaceProfile("Private Readiness Lab", "A promoted administrator waiting for a real new admission.",
            visibility = SpaceVisibility.PUBLIC), listOf(SpaceChannelProfile("general")))
        owner.flush(space)
        repeat(18) { owner.send(space, "Readiness General $it") }; owner.flush(space)
        holder.openPublic(space); holder.joinPublic(space); holder.flush(space)
        for (participant in listOf(actor, allowed, grant, retry)) {
            SqliteClientFixture(root.resolve("readiness-${participant.publicKey}.sqlite").toString()).use { store ->
                val member = client(participant, store)
                member.openPublic(space); member.joinPublic(space); member.flush(space)
            }
        }
        owner.refresh(space); owner.setAdministrator(space, holderWallet.publicKey, true); owner.flush(space)
        holder.refresh(space)
        channel = holder.createPrivateChannel(space, SpaceChannelProfile("readiness-room"), setOf(actor.publicKey, allowed.publicKey))
        holder.flush(space)
        fixtures.addProperty("space", space); fixtures.addProperty("general", owner.community(space).channel)
        fixtures.addProperty("private", channel); fixtures.addProperty("privateName", "readiness-room")
        fixtures.addProperty("link", owner.publicLink(space))
        for ((key, value) in mapOf("owner" to ownerWallet, "holder" to holderWallet, "actor" to actor,
            "allowed" to allowed, "grant" to grant, "retry" to retry)) {
            fixtures.addProperty(key, value.publicKey); fixtures.addProperty(key + "Name", names.getValue(value.publicKey))
        }
    }

    suspend fun command(path: String, body: JsonObject): JsonObject? = when (path) {
        "/readiness-fixtures" -> fixtures.deepCopy()
        "/readiness-status" -> status()
        "/readiness-admit-initial" -> {
            require(!promoted && body.size() == 0)
            holder.admitPending(space); holder.flush(space); status()
        }
        "/readiness-revoke-and-promote" -> {
            require(!promoted && status().getAsJsonArray("privateMessages").any { it.asString == "This device cached the private channel" })
            holder.revokeChannelAccess(space, channel, actor.publicKey); holder.flush(space)
            holder.send(space, "Future private content needs the new keys", channel); holder.flush(space)
            owner.refresh(space); owner.setAdministrator(space, actor.publicKey, true); owner.flush(space)
            promoted = true; status()
        }
        "/readiness-reject-next-grant" -> {
            require(promoted && rejectNext.compareAndSet(false, true))
            JsonObject().apply { addProperty("armed", true) }
        }
        "/readiness-reset-retry" -> {
            require(promoted && !rejectNext.get())
            holder.refresh(space)
            holder.revokeChannelAccess(space, channel, retry.publicKey); holder.flush(space)
            status()
        }
        else -> null
    }

    private suspend fun status(): JsonObject {
        holder.refresh(space, channel)
        val events = controls.values.map { receipt -> SaitoEnvelope.decode(SaitoEnvelope.decode(receipt).body.get("event").asString) }
        val devices = events.last { it.body.get("channel_id")?.asString == channel && it.body.has("devices") }.body.getAsJsonObject("devices")
        val permitted = mutableSetOf<String>()
        for (event in events) if (event.body.get("channel_id")?.asString == channel) {
            when (event.body.get("kind")?.asString) {
                "create-channel" -> permitted.addAll(event.body.getAsJsonArray("allowed_members").map { it.asString })
                "grant-channel-access" -> permitted.add(event.body.get("member").asString)
                "revoke-channel-access" -> permitted.remove(event.body.get("member").asString)
            }
        }
        return JsonObject().apply {
            addProperty("space", space); addProperty("channel", channel); addProperty("promoted", promoted)
            add("members", JsonObject().apply { holder.members(space).forEach { addProperty(it.publicKey, it.role.name) } })
            add("keyHolders", JsonArray().apply { devices.keySet().map { it.split(':')[1] }.distinct().sorted().forEach(::add) })
            add("allowedMembers", JsonArray().apply { permitted.sorted().forEach(::add) })
            add("privateMessages", JsonArray().apply { holder.messages(space, channel).forEach(::add) })
            add("joinRequests", JsonArray().apply { peer.spacesRequests.toList().forEach { original ->
                val event = SaitoEnvelope.decode(original.envelope())
                if (event.author == actor.publicKey && event.body.get("kind")?.asString == "join-channel") add(original.id)
            } })
            add("originals", JsonArray().apply { peer.spacesRequests.toList().forEach { original ->
                val event = SaitoEnvelope.decode(original.envelope()); val body = event.body
                if (event.author == actor.publicKey && body.get("kind")?.asString == "grant-channel-access") add(JsonObject().apply {
                    addProperty("id", original.id); addProperty("actor", event.author); addProperty("space", body.get("space_id").asString)
                    addProperty("channel", body.get("channel_id").asString); addProperty("member", body.get("member").asString)
                    addProperty("sha256", MessageDigest.getInstance("SHA-256").digest(original.envelope().toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it.toInt() and 255) })
                })
            } })
            add("rejections", JsonArray().apply { rejected.forEach { receipt ->
                val body = SaitoEnvelope.decode(receipt).body
                val original = SaitoEnvelope.decode(body.get("event").asString)
                add(JsonObject().apply { addProperty("id", original.id); addProperty("receipt", receipt) })
            } })
        }
    }
}
