package nl.davidengelen.redsquare.spaces

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64

/** Application-owned community operations. Call from a serialized IO worker.
 * Signed originals and replacement key state commit before transport is called. */
class SpaceClient(private val wallet: SaitoWallet, private val node: String, private val service: String,
    private val store: SpaceClientStore, private val clock: Clock,
    private val exchange: suspend (SaitoTransaction) -> String) {
    init { require(node.isNotBlank() && node.length <= 2048 && SaitoWallet.isPublicKey(service)) }

    data class Community(val id: String, val name: String, val channel: String, val joined: Boolean,
        val canInvite: Boolean, val pending: Boolean, val canSend: Boolean)
    data class Message(val id: String, val author: String, val createdAt: Long, val text: String?,
        val pending: Boolean, val attempted: Boolean, val missingKeys: Boolean) {
        override fun toString() = "Private community message"
    }
    data class Invitation(val link: String, val confirmed: Boolean) {
        override fun toString() = "Private community invitation"
    }
    fun communities(): List<Community> = store.transaction {
        if (get("identity") == null) return@transaction emptyList()
        owned()
        page("communities/").map { row -> communityFromStore(row.value.toString(Charsets.UTF_8)) }
    }
    fun community(space: String): Community = store.transaction { owned(); communityFromStore(space) }
    private fun SpaceClientStore.Transaction.communityFromStore(space: String): Community {
        val state = state(space)
        val scope = SpaceCrypto.Scope.fromJson(state.getAsJsonObject("scope"))
        return Community(space, state.text("name"), scope.channelId, state.get("joined").asBoolean,
            journal(space).snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN),
            page("$space/pending/", limit = 1).isNotEmpty(), state.get("joined").asBoolean && !state.has("control_pending"))
    }
    fun draft(space: String): String = store.transaction { owned(); state(space); get("$space/draft")?.toString(Charsets.UTF_8).orEmpty() }
    fun saveDraft(space: String, text: String) = store.transaction {
        owned(); state(space); require(text.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
        put("$space/draft", text.toByteArray(Charsets.UTF_8))
    }
    fun invitation(space: String): Invitation? = store.transaction {
        owned()
        val state = state(space)
        val link = state.get("invitation")?.asString ?: return@transaction null
        val invitation = SpaceInvitation.parse(link)
        Invitation(link, invitation.invitationId in journal(space).snapshot.invitations)
    }
    data class InvitationPreview(val spaceId: String, val name: String)
    private data class Preview(val invitation: SpaceInvitation, val name: String, val controls: List<String>, val journal: SpaceJournal)

    fun invite(space: String): String = store.transaction {
        owned()
        val state = state(space)
        val journal = journal(space)
        require(journal.snapshot.members[wallet.publicKey] in setOf(SpaceRole.OWNER, SpaceRole.ADMIN))
        require(!state.has("control_pending")) { "A community change is awaiting confirmation" }
        val scope = SpaceCrypto.Scope.fromJson(state.getAsJsonObject("scope"))
        val bearer = SaitoWallet.create()
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        val operation = id()
        val original = event(space, "invite", operation) {
            addProperty("control_id", journal.snapshot.controlId); addProperty("invite_key", bearer.publicKey)
            addProperty("expires_at", Math.addExact(clock.millis(), 604_800_000))
            addProperty("preview", SpaceInvitation.encryptPreview(scope, key, operation, state.text("name")))
        }
        val link = SpaceInvitation(node, scope, original.id, bearer, key).link()
        queue(space, state, original)
        state.addProperty("invitation", link)
        put(stateKey(space), bytes(state))
        link
    }

    /** Inspecting a valid invitation generates no MLS device or membership. */
    suspend fun preview(link: String): InvitationPreview = loadPreview(link).let { InvitationPreview(it.invitation.scope.spaceId, it.name) }

    suspend fun join(link: String): String {
        val preview = loadPreview(link)
        val invitation = preview.invitation
        val scope = invitation.scope
        require(wallet.publicKey !in preview.journal.snapshot.members) { "This identity is already a member; restore its community keys" }
        val device = SpaceCrypto.device(wallet.publicKey, id())
        val keyPackage = device.keyPackage(scope, wallet)
        val operation = id()
        val original = event(scope.spaceId, "join", operation) {
            addProperty("channel_id", scope.channelId); addProperty("control_id", invitation.invitationId)
            addProperty("invite_id", invitation.invitationId)
            add("key_package", JsonObject().apply {
                addProperty("wallet", wallet.publicKey); addProperty("device", keyPackage.deviceId)
                addProperty("package", keyPackage.packageBytes); addProperty("signer", keyPackage.signer)
                addProperty("signature", keyPackage.walletSignature)
            })
            addProperty("invite_proof", invitation.joinProof(wallet.publicKey, operation, keyPackage.walletSignature))
        }
        store.transaction {
            owned(initialize = true)
            require(get(stateKey(scope.spaceId)) == null) { "This community already has saved work" }
            for (receipt in preview.controls) {
                val position = SaitoEnvelope.decode(receipt).body.number("position")
                put("${scope.spaceId}/control/${positionKey(position)}", receipt.toByteArray(Charsets.UTF_8))
            }
            val state = JsonObject().apply {
                add("scope", scope.json()); addProperty("name", preview.name); addProperty("crypto", encode(device.save()))
                addProperty("joined", false); addProperty("next_outbox", 0); addProperty("content_cursor", 0)
                addProperty("joining_invitation", link)
            }
            queue(scope.spaceId, state, original)
        }
        return scope.spaceId
    }

    /** Valid explicit join requests are admitted by the existing owner/admin.
     * Both MLS branches and the original commit are durable before transport. */
    suspend fun admitPending(space: String) {
        val state = store.transaction { owned(); state(space) }
        if (!state.get("joined").asBoolean) return
        val role = store.transaction { journal(space).snapshot.members[wallet.publicKey] }
        if (role !in setOf(SpaceRole.OWNER, SpaceRole.ADMIN)) return
        val page = readPage(JsonObject().apply {
            addProperty("space_id", space); addProperty("stream", "join"); addProperty("cursor", 0)
        })
        val request = page.getAsJsonArray("items").firstOrNull()?.asString ?: return
        store.transaction {
            owned()
            if (page("$space/pending/", limit = 1).isNotEmpty()) return@transaction
            val journal = journal(space)
            val received = SaitoEnvelope.decode(request)
            require(received.author == service && received.body.text("stream") == "join" && received.body.text("space_id") == space &&
                received.body.text("module") == "Spaces" && received.body.text("request") == "receipt" && received.body.number("version") == 1L)
            val join = journal.verifyJoin(received.body.text("event"), clock.millis())
            val current = state(space)
            val crypto = SpaceCrypto.restore(decode(current.text("crypto")))
            val admission = crypto.admit(join.keyPackage)
            val original = event(space, "admit") {
                addProperty("control_id", journal.snapshot.controlId); addProperty("channel_id", join.channelId)
                addProperty("epoch", admission.session.epoch); addProperty("join", join.envelope)
                addProperty("welcome", admission.welcome); addProperty("commit", admission.commit)
                add("devices", devices(admission.bindings))
            }
            queue(space, current, original, admission.session, admission.afterRejection)
        }
    }

    private suspend fun loadPreview(link: String): Preview {
        val invitation = SpaceInvitation.parse(link)
        require(invitation.node == node && invitation.scope.serviceKey == service) { "Invitation belongs to a different community node" }
        val journal = SpaceJournal(invitation.scope.spaceId, service)
        val controls = mutableListOf<String>()
        var retainedBytes = 0L
        while (true) {
            val before = journal.snapshot.nextPosition
            val page = readPage(JsonObject().apply {
                addProperty("space_id", invitation.scope.spaceId); addProperty("stream", "invitation")
                addProperty("invite_id", invitation.invitationId); addProperty("cursor", before)
            }, invitation)
            for (value in page.getAsJsonArray("items")) {
                val receipt = value.asString
                retainedBytes += receipt.toByteArray(Charsets.UTF_8).size
                require(retainedBytes <= 16L * 1024 * 1024) { "Invitation history exceeds this client's preview capacity" }
                journal.receive(receipt); controls += receipt
            }
            require(journal.snapshot.nextPosition == page.number("next_cursor"))
            if (journal.snapshot.nextPosition == before) break
        }
        val authorized = requireNotNull(journal.snapshot.invitations[invitation.invitationId])
        require(authorized.expiresAt > clock.millis() && invitation.scope.channelId in journal.snapshot.channels)
        val original = controls.asSequence().map { SaitoEnvelope.decode(SaitoEnvelope.decode(it).body.text("event")) }
            .first { it.id == invitation.invitationId }
        val name = invitation.preview(original.body.text("preview"), original.body.text("operation_id"))
        return Preview(invitation, name, controls, journal)
    }

    fun create(name: String): String {
        require(name.isNotBlank() && name.toByteArray(Charsets.UTF_8).size <= 256)
        val scope = SpaceCrypto.Scope(service, id(), id())
        val crypto = SpaceCrypto.device(wallet.publicKey, id()).create(scope)
        // Metadata uses the same authenticated archive envelope, with a separate
        // kind and fixed genesis predecessor. It is never sent as plaintext.
        val metadata = crypto.publish(wallet, "metadata", id(), "0".repeat(128), name, clock.millis())
        val original = event(scope.spaceId, "create") {
            addProperty("visibility", "private"); addProperty("channel_id", scope.channelId)
            addProperty("key_generation", scope.generation); addProperty("epoch", 0)
            addProperty("metadata", encode(metadata.envelope().toByteArray(Charsets.UTF_8)))
            add("devices", devices(crypto.bindings))
        }
        store.transaction {
            owned(initialize = true)
            val state = JsonObject().apply {
                add("scope", scope.json()); addProperty("name", name); addProperty("crypto", encode(crypto.save()))
                addProperty("joined", false); addProperty("next_outbox", 0); addProperty("content_cursor", 0)
            }
            require(get(stateKey(scope.spaceId)) == null)
            queue(scope.spaceId, state, original)
        }
        return scope.spaceId
    }

    /** A local durable queue is not a remote sent/encrypted confirmation. */
    fun send(space: String, text: String): String = store.transaction {
        owned()
        val state = state(space)
        require(state.get("joined").asBoolean && !state.has("control_pending")) { "Community admission or key change is not confirmed" }
        val crypto = SpaceCrypto.restore(decode(state.text("crypto")))
        val control = requireNotNull(journal(space).snapshot.controlId)
        val original = crypto.publish(wallet, "message", id(), control, text, clock.millis())
        queue(space, state, original)
        if (get("$space/draft")?.toString(Charsets.UTF_8) == text) remove("$space/draft")
        original.id
    }

    /** On an uncertain response keep the exact signed original for reconciliation. */
    suspend fun flush(space: String) {
        val prefix = "$space/pending/"
        repeat(50) {
            val item = store.transaction {
                owned(); state(space)
                val row = page(prefix, limit = 1).firstOrNull() ?: return@transaction null
                val prepared = json(row.value).apply { addProperty("attempted", true) }
                val value = bytes(prepared)
                put(row.key, value)
                SpaceClientStore.Entry(row.key, value)
            } ?: return
            val transaction = SaitoTransaction.restore(json(item.value).text("original"))
            val result = exchange(transaction)
            store.transaction {
                owned()
                require(get(item.key)?.contentEquals(item.value) == true) { "Community operation changed during delivery" }
                val received = SaitoEnvelope.decode(result)
                require(received.body.text("event") == transaction.envelope()) { "Community service returned a different original" }
                accept(space, result)
                remove(item.key)
                remove("$space/operation/${transaction.id}")
            }
        }
    }

    suspend fun refresh(space: String) {
        val before = store.transaction { owned(); state(space) }
        val invitation = before.get("joining_invitation")?.asString?.let(SpaceInvitation::parse)
        val controlCursor = store.transaction { journal(space).snapshot.nextPosition }
        val controls = readPage(JsonObject().apply {
            addProperty("space_id", space); addProperty("stream", if (invitation == null) "control" else "invitation")
            addProperty("cursor", controlCursor)
            invitation?.let { addProperty("invite_id", it.invitationId) }
        }, invitation)
        store.transaction {
            owned(); controls.getAsJsonArray("items").forEach { accept(space, it.asString) }
            require(journal(space).snapshot.nextPosition == controls.number("next_cursor"))
        }
        val scope = store.transaction { owned(); state(space) }
        if (!scope.get("joined").asBoolean) return
        val channel = scope.getAsJsonObject("scope").text("channel")
        val cursor = scope.number("content_cursor")
        val content = readPage(JsonObject().apply {
            addProperty("space_id", space); addProperty("stream", "content")
            addProperty("channel_id", channel); addProperty("cursor", cursor)
        })
        store.transaction {
            owned()
            val current = state(space)
            require(current.number("content_cursor") == cursor) { "Community read changed while receiving its page" }
            var next = cursor
            for (value in content.getAsJsonArray("items")) {
                val receipt = SaitoEnvelope.decode(value.asString)
                require(receipt.body.number("position") == next)
                accept(space, value.asString)
                next = Math.addExact(next, 1)
            }
            require(next == content.number("next_cursor"))
            val updated = state(space) // History grants may have advanced its keys.
            updated.addProperty("content_cursor", next)
            put(stateKey(space), bytes(updated))
        }
    }

    /** Bounded local page, including explicit unavailable keys and durable
     * queued originals. The UI never infers receipt from an optimistic row. */
    fun timeline(space: String, from: Long? = null, limit: Int = 50): List<Message> = store.transaction {
        owned(); require(limit in 1..50)
        val state = state(space)
        val crypto = SpaceCrypto.restore(decode(state.text("crypto")))
        val prefix = "$space/content/"
        val last = state.get("last_content")?.asLong ?: 0
        val start = from ?: (last - limit + 1).coerceAtLeast(0)
        fun message(envelope: String, pending: Boolean, attempted: Boolean): Message? {
            val original = SaitoEnvelope.decode(envelope)
            if (original.body.text("kind") != "content" || original.body.text("content_kind") != "message") return null
            var missing = false
            val text = try { crypto.read(envelope).text }
                catch (_: SpaceCrypto.MissingKeys) { missing = true; null }
                catch (_: java.security.GeneralSecurityException) { null }
            return Message(original.id, original.author, original.createdAt, text, pending, attempted, missing)
        }
        val confirmed = page(prefix, prefix + positionKey(start), limit).mapNotNull { row ->
            message(originalEnvelope(row.value), pending = false, attempted = true)
        }
        val pending = page("$space/pending/", limit = limit).mapNotNull { row ->
            val saved = json(row.value)
            message(SaitoTransaction.restore(saved.text("original")).envelope(), pending = true, attempted = saved.get("attempted").asBoolean)
        }
        (confirmed + pending).distinctBy { it.id }
    }
    fun messages(space: String): List<String> = timeline(space).mapNotNull { it.text }

    private suspend fun readPage(scope: JsonObject, invitation: SpaceInvitation? = null): JsonObject {
        val requestId = id()
        fun request(kind: String, nonce: String? = null) = SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", kind); addProperty("version", 1)
            addProperty("request_id", requestId); add("scope", scope.deepCopy())
            nonce?.let {
                addProperty("nonce", it)
                invitation?.let { invite -> addProperty("invite_proof", invite.proof(wallet.publicKey, nonce)) }
            }
        }, clock.millis(), listOf(service))
        fun verified(envelope: String, kind: String): JsonObject {
            val signed = SaitoEnvelope.decode(envelope)
            val body = signed.body
            require(signed.author == service && signed.recipients == listOf(service, wallet.publicKey).distinct())
            require(body.text("module") == "Spaces" && body.number("version") == 1L && body.text("request") == kind)
            require(body.text("request_id") == requestId && body.text("caller") == wallet.publicKey && body.getAsJsonObject("scope") == scope)
            return body
        }
        val challenge = verified(exchange(request("challenge")), "challenge")
        require(challenge.number("expires_at") > clock.millis()) { "Community read challenge expired" }
        val nonce = challenge.text("nonce").also { require(it.matches(ID)) }
        val result = verified(exchange(request("read", nonce)), "page")
        require(result.text("nonce") == nonce && result.getAsJsonArray("items").size() <= 50)
        return result
    }

    private fun SpaceClientStore.Transaction.accept(space: String, envelope: String) {
        val receipt = SaitoEnvelope.decode(envelope)
        val body = receipt.body
        require(receipt.author == service && body.text("module") == "Spaces" && body.number("version") == 1L &&
            body.text("request") == "receipt" && body.text("space_id") == space)
        val original = SaitoEnvelope.decode(body.text("event"))
        val state = state(space)
        when (body.text("stream")) {
            "control" -> {
                val journal = journal(space)
                journal.receive(envelope)
                val position = body.number("position")
                val key = "$space/control/${positionKey(position)}"
                val existing = get(key)
                if (existing != null) require(SaitoEnvelope.decode(existing.toString(Charsets.UTF_8)).body.text("event") == body.text("event"))
                else {
                    when (original.body.text("kind")) {
                        "create" -> {
                            if (original.author == wallet.publicKey) {
                                val keys = SpaceCrypto.restore(decode(state.text("crypto")))
                                require(journal.snapshot.channels.values.single().devices == keys.bindings)
                                state.addProperty("joined", true)
                            }
                        }
                        "invite" -> Unit
                        "admit" -> {
                            val scope = SpaceCrypto.Scope.fromJson(state.getAsJsonObject("scope"))
                            val channel = requireNotNull(journal.snapshot.channels[scope.channelId])
                            val current = SpaceCrypto.restore(decode(state.text("crypto")))
                            val join = SaitoEnvelope.decode(original.body.text("join"))
                            val pointer = get("$space/operation/${original.id}")?.toString(Charsets.UTF_8)
                            val pending = pointer?.let { get(it) }?.let(::json)
                            val next = when {
                                pending?.has("candidate") == true -> SpaceCrypto.restore(decode(pending.text("candidate")))
                                join.author == wallet.publicKey && !state.get("joined").asBoolean ->
                                    current.join(scope, original.body.text("welcome"), channel.devices)
                                state.get("joined").asBoolean -> current.receiveCommit(original.body.text("commit"),
                                    current.bindings.keys.single { it.split(':')[1] == original.author }, channel.devices)
                                else -> null
                            }
                            if (next != null) {
                                require(next.epoch == channel.epoch && next.bindings == channel.devices)
                                state.addProperty("crypto", encode(next.save())); state.addProperty("joined", true)
                                state.remove("joining_invitation")
                                if (pending?.has("candidate") == true) {
                                    val target = channel.devices.keys.single { it.split(':')[1] == join.author }
                                    if (state.get("control_pending")?.asString == original.id) state.remove("control_pending")
                                    val history = next.history(wallet, target, id(), original.id, clock.millis())
                                    state.addProperty("crypto", encode(history.session.save()))
                                    queue(space, state, history.transaction)
                                }
                            }
                        }
                        else -> throw IllegalArgumentException("Unsupported community control")
                    }
                    if (state.get("control_pending")?.asString == original.id) state.remove("control_pending")
                    put(key, envelope.toByteArray(Charsets.UTF_8))
                    put(stateKey(space), bytes(state))
                }
            }
            "join" -> {
                require(original.author == wallet.publicKey && original.body.text("kind") == "join")
                require(!state.get("joined").asBoolean)
            }
            "content" -> {
                val scope = SpaceCrypto.Scope.fromJson(state.getAsJsonObject("scope"))
                require(body.text("channel_id") == scope.channelId)
                val control = original.body.text("control_id")
                val historic = journal(space, through = control)
                require(historic.snapshot.controlId == control)
                historic.verifyContent(body.text("event"))
                val key = "$space/content/${positionKey(body.number("position"))}"
                val existing = get(key)
                if (existing != null) require(json(existing).text("event") == body.text("event"))
                else {
                    if (original.body.text("kind") == "history") {
                        val keys = SpaceCrypto.restore(decode(state.text("crypto")))
                        if (original.body.text("recipient_credential") == keys.credential) {
                            state.addProperty("crypto", encode(keys.acceptHistory(body.text("event")).save()))
                            put(stateKey(space), bytes(state))
                        }
                    }
                    put(key, bytes(body))
                    state.addProperty("last_content", maxOf(state.get("last_content")?.asLong ?: 0, body.number("position")))
                    put(stateKey(space), bytes(state))
                }
            }
            else -> throw IllegalArgumentException("Unexpected community receipt stream")
        }
    }

    private fun SpaceClientStore.Transaction.journal(space: String, through: String? = null): SpaceJournal {
        val journal = SpaceJournal(space, service)
        val prefix = "$space/control/"
        var cursor = prefix
        while (true) {
            val rows = page(prefix, cursor)
            for (row in rows) {
                journal.receive(row.value.toString(Charsets.UTF_8))
                if (journal.snapshot.controlId == through) return journal
            }
            if (rows.size < 50) return journal
            cursor = rows.last().key + '\u0000'
        }
    }

    private fun SpaceClientStore.Transaction.queue(space: String, state: JsonObject, original: SaitoTransaction,
        candidate: SpaceCrypto? = null, rejected: SpaceCrypto? = null) {
        val kind = SaitoEnvelope.decode(original.envelope()).body.text("kind")
        if (kind in setOf("create", "invite", "admit")) {
            require(!state.has("control_pending")) { "A community control is still awaiting confirmation" }
            state.addProperty("control_pending", original.id)
        }
        val sequence = state.number("next_outbox")
        val key = "$space/pending/${positionKey(sequence)}"
        put(key, bytes(JsonObject().apply {
            addProperty("original", original.encoded()); addProperty("attempted", false)
            candidate?.let { addProperty("candidate", encode(it.save())) }
            rejected?.let { addProperty("rejected", encode(it.save())) }
        }))
        put("$space/operation/${original.id}", key.toByteArray(Charsets.UTF_8))
        state.addProperty("next_outbox", Math.addExact(sequence, 1))
        put(stateKey(space), bytes(state))
        put("communities/$space", space.toByteArray(Charsets.UTF_8))
    }
    private fun SpaceClientStore.Transaction.state(space: String): JsonObject {
        require(space.matches(ID))
        return json(requireNotNull(get(stateKey(space))) { "Community is not stored for this account and node" })
    }
    private fun SpaceClientStore.Transaction.owned(initialize: Boolean = false) {
        val expected = JsonObject().apply {
            addProperty("version", 1); addProperty("owner", wallet.publicKey)
            addProperty("node", node); addProperty("service", service)
        }
        val stored = get("identity")
        if (stored == null) { require(initialize); put("identity", bytes(expected)) }
        else require(json(stored) == expected) { "Community storage belongs to another account or node" }
    }
    private fun event(space: String, kind: String, operation: String = id(), extra: JsonObject.() -> Unit): SaitoTransaction =
        SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
            addProperty("kind", kind); addProperty("space_id", space); addProperty("operation_id", operation); extra()
        }, clock.millis(), listOf(service))

    private fun originalEnvelope(body: ByteArray) = json(body).text("event")
    private fun stateKey(space: String) = "$space/state"
    private fun positionKey(value: Long) = value.also { require(it >= 0) }.toString().padStart(20, '0')
    private fun devices(values: Map<String, String>) = JsonObject().apply { values.forEach { (key, value) -> addProperty(key, value) } }
    private fun id() = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun encode(value: ByteArray) = Base64.getEncoder().encodeToString(value)
    private fun decode(value: String) = Base64.getDecoder().decode(value)
    private fun bytes(value: JsonObject) = value.toString().toByteArray(Charsets.UTF_8)
    private fun json(value: ByteArray) = GSON.fromJson(value.toString(Charsets.UTF_8), JsonObject::class.java)
    private fun JsonObject.text(name: String): String = get(name).also {
        require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isString)
    }.asString
    private fun JsonObject.number(name: String): Long = get(name).also {
        require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isNumber && it.asString.matches(Regex("0|[1-9][0-9]{0,18}")))
    }.asLong.also { require(it >= 0) }
    private companion object {
        val ID = Regex("[0-9a-f]{64}")
        val GSON = GsonBuilder().setStrictness(Strictness.STRICT).create()
    }
}
