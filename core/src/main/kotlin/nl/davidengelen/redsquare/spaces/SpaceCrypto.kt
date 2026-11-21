package nl.davidengelen.redsquare.spaces

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.SignedSaitoMessage
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Immutable keys. Commit returned state together with the original outbound
 * transaction before permitting transmission. Crypto and IO belong off main. */
class SpaceCrypto private constructor(private val native: JsonObject, val scope: Scope?,
    private val archiveKeys: Map<Long, ByteArray>) {
    data class Scope(val serviceKey: String, val spaceId: String, val channelId: String,
        val generation: String = "0".repeat(64)) {
        init { require(SaitoWallet.isPublicKey(serviceKey) && spaceId.matches(ID) && channelId.matches(ID) && generation.matches(ID)) }
        val groupId get() = "SaitoSpaces/v1/$serviceKey/$spaceId/$channelId/$generation"
        internal fun json() = JsonObject().apply {
            addProperty("service", serviceKey); addProperty("space", spaceId)
            addProperty("channel", channelId); addProperty("generation", generation)
        }
        companion object {
            internal fun fromJson(value: JsonObject): Scope {
                require(value.keySet() == setOf("service", "space", "channel", "generation"))
                fun field(name: String): String = value.get(name).also {
                    require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isString)
                }.asString
                return Scope(field("service"), field("space"), field("channel"), field("generation"))
            }
        }
    }
    data class BoundPackage(val scope: Scope, val walletKey: String, val deviceId: String,
        val packageBytes: String, val signer: String, val walletSignature: String) {
        val credential get() = identity(walletKey, deviceId)
        internal fun signedBytes() = "SaitoSpacesKeyPackage/v1:${scope.groupId}:$credential:$signer:${sha(decode(packageBytes))}".toByteArray(Charsets.UTF_8)
        fun verify() {
            require(SaitoWallet.isPublicKey(walletKey) && deviceId.matches(ID))
            require(decode(packageBytes).size in 1..65_536 && decode(signer).size == 32)
            require(SaitoWallet.verify(signedBytes(), walletSignature, walletKey)) { "Space device binding is not authenticated" }
        }
    }
    /** Persist both branches with the original control. Select afterRejection
     * only after the original operation is definitively rejected. */
    data class Admission(val session: SpaceCrypto, val welcome: String, val bindings: Map<String, String>,
        val commit: String, val afterRejection: SpaceCrypto)
    data class KeyChange(val session: SpaceCrypto, val commit: String, val bindings: Map<String, String>,
        val afterRejection: SpaceCrypto)
    /** Persist the advanced sender before transmission, including when a later
     * service rejection means this message is not admitted to history. */
    data class History(val session: SpaceCrypto, val transaction: SaitoTransaction, val nextEpoch: Long?)
    data class PlainRecord(val author: String, val text: String) {
        override fun toString() = "Private Space record"
    }
    class MissingKeys : IllegalStateException("History keys are not available on this device")
    val credential: String get() = native.text("identity")
    val epoch: Long get() = native.number("epoch")
    val bindings: Map<String, String> get() = native.getAsJsonObject("member_keys")?.entrySet()?.associate {
        it.key to it.value.asString
    }?.let(Collections::unmodifiableMap) ?: emptyMap()

    fun create(scope: Scope): SpaceCrypto {
        require(this.scope == null)
        return advance(step("create") { addProperty("group", scope.groupId) }, scope)
    }
    fun keyPackage(scope: Scope, wallet: SaitoWallet): BoundPackage {
        require(this.scope == null && wallet.publicKey == walletKey(credential))
        val unsigned = BoundPackage(scope, wallet.publicKey, credential.substringAfterLast(':'),
            native.text("key_package"), native.text("signer"), "")
        return unsigned.copy(walletSignature = wallet.sign(unsigned.signedBytes()))
    }
    fun admit(member: BoundPackage): Admission {
        require(member.scope == requireNotNull(scope) && member.credential !in bindings)
        member.verify()
        val response = step("add") { addProperty("key_package", member.packageBytes) }
        val next = advance(response, scope)
        require(next.epoch == Math.addExact(epoch, 1) && next.bindings == bindings + (member.credential to member.signer)) {
            "MLS admission does not match its wallet binding"
        }
        return Admission(next, response.text("welcome"), next.bindings, response.text("wire"), cancelled(response))
    }
    fun join(scope: Scope, welcome: String, bindings: Map<String, String>): SpaceCrypto {
        require(this.scope == null && bindings[credential] == native.text("signer"))
        val next = advance(step("join") { addProperty("wire", welcome) }, scope)
        require(next.bindings == bindings) { "MLS membership differs from accepted device bindings" }
        return next
    }
    fun remove(memberCredential: String): KeyChange {
        require(memberCredential in bindings && memberCredential != credential)
        val response = step("remove") { addProperty("identity", memberCredential) }
        val next = advance(response, requireNotNull(scope))
        require(next.epoch == Math.addExact(epoch, 1) && next.bindings == bindings - memberCredential)
        return KeyChange(next, response.text("wire"), next.bindings, cancelled(response))
    }
    /** The application first admits the signed control and its actor/role. */
    fun receiveCommit(wire: String, actorCredential: String, expectedBindings: Map<String, String>): SpaceCrypto {
        require(actorCredential in bindings)
        val response = step("receive") { addProperty("wire", wire) }
        require(!response.has("text") && response.text("sender") == actorCredential)
        val next = advance(response, requireNotNull(scope))
        require(next.epoch == Math.addExact(epoch, 1) && next.bindings == expectedBindings)
        return next
    }
    @JvmOverloads fun publish(wallet: SaitoWallet, kind: String, operationId: String, controlId: String,
        text: String, timestamp: Long, targetChannelId: String? = null, targetCategoryId: String? = null): SaitoTransaction =
        publishRecord(wallet, kind, operationId, controlId, text, timestamp, targetChannelId = targetChannelId, targetCategoryId = targetCategoryId)

    fun publishMessageEdit(wallet: SaitoWallet, operationId: String, controlId: String, text: String,
        timestamp: Long, targetMessageId: String, targetPosition: Long, threadRootId: String? = null): SaitoTransaction =
        publishRecord(wallet, "message-edit", operationId, controlId, text, timestamp,
            targetMessageId = targetMessageId, targetPosition = targetPosition, threadRootId = threadRootId)

    fun publishMessageReaction(wallet: SaitoWallet, operationId: String, controlId: String, selection: String,
        timestamp: Long, targetMessageId: String, targetPosition: Long, threadRootId: String? = null): SaitoTransaction =
        publishRecord(wallet, "message-reaction", operationId, controlId, selection, timestamp,
            targetMessageId = targetMessageId, targetPosition = targetPosition, threadRootId = threadRootId)

    fun publishMessagePin(wallet: SaitoWallet, operationId: String, controlId: String, pinned: Boolean,
        timestamp: Long, targetMessageId: String, targetPosition: Long, threadRootId: String? = null): SaitoTransaction =
        publishRecord(wallet, "message-pin", operationId, controlId, if (pinned) "pinned" else "unpinned", timestamp,
            targetMessageId = targetMessageId, targetPosition = targetPosition, threadRootId = threadRootId)

    fun publishThreadReply(wallet: SaitoWallet, operationId: String, controlId: String, text: String,
        timestamp: Long, parentId: String, parentPosition: Long): SaitoTransaction =
        publishRecord(wallet, "thread-reply", operationId, controlId, text, timestamp,
            targetMessageId = parentId, targetPosition = parentPosition)

    fun publishMessageDeletion(wallet: SaitoWallet, operationId: String, controlId: String,
        timestamp: Long, targetMessageId: String, targetPosition: Long, threadRootId: String? = null): SaitoTransaction =
        publishRecord(wallet, "message-delete", operationId, controlId, "deleted", timestamp,
            targetMessageId = targetMessageId, targetPosition = targetPosition, threadRootId = threadRootId)

    private fun publishRecord(wallet: SaitoWallet, kind: String, operationId: String, controlId: String,
        text: String, timestamp: Long, recipient: String? = null, targetChannelId: String? = null, targetCategoryId: String? = null,
        targetMessageId: String? = null, targetPosition: Long? = null, threadRootId: String? = null): SaitoTransaction {
        require(text.isNotEmpty() && text.toByteArray(Charsets.UTF_8).size <= 64 * 1024 && kind.matches(Regex("[a-z][a-z-]{0,31}")))
        require((kind == "category-profile") == (targetCategoryId != null))
        require((kind in setOf("message-edit", "message-delete", "thread-reply", "message-reaction", "message-pin")) == (targetMessageId != null && targetPosition != null))
        val header = header(wallet, "content", operationId, controlId).apply {
            addProperty("content_kind", kind)
            recipient?.let { addProperty("recipient_credential", it) }
            targetCategoryId?.let { require(it.matches(ID)); addProperty("target_category_id", it) }
            targetChannelId?.let {
                require(kind == "channel-profile-edit" && it.matches(ID))
                addProperty("target_channel_id", it)
            }
            threadRootId?.let {
                require(kind in setOf("message-edit", "message-delete", "message-reaction", "message-pin") && it.matches(SIGNATURE))
                addProperty("thread_root_id", it)
            }
            targetMessageId?.let {
                require(it.matches(SIGNATURE) && requireNotNull(targetPosition) >= 0)
                addProperty("target_message_id", it); addProperty("target_position", targetPosition)
            }
        }
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(requireNotNull(archiveKeys[epoch]), "AES"), GCMParameterSpec(128, nonce))
            updateAAD(archiveAad(requireNotNull(scope), header, wallet.publicKey))
        }
        header.addProperty("ciphertext", encode(nonce + cipher.doFinal(text.toByteArray(Charsets.UTF_8))))
        return SaitoTransaction.create(wallet, header, timestamp, listOf(requireNotNull(scope).serviceKey))
    }
    /** Authenticate before decryption. Historical permission is independently
     * evaluated at the application journal before it admits this record. */
    /** Readmission creates a fresh sender but must retain already received history. */
    internal fun withRetainedHistory(previous: SpaceCrypto): SpaceCrypto {
        require(scope != null && scope == previous.scope && walletKey(credential) == walletKey(previous.credential))
        require(previous.archiveKeys.keys.all { it <= epoch })
        previous.archiveKeys.forEach { (oldEpoch, key) ->
            archiveKeys[oldEpoch]?.let { known -> require(MessageDigest.isEqual(known, key)) { "Conflicting history key" } }
        }
        return SpaceCrypto(native, scope, Collections.unmodifiableMap(previous.archiveKeys + archiveKeys))
    }

    fun read(envelope: String): PlainRecord = readArchived(requireNotNull(scope), archiveKeys, envelope)
    /** Bounded grants contain archive keys, never another device's MLS state. */
    fun history(wallet: SaitoWallet, target: String, operationId: String, controlId: String,
        timestamp: Long, fromEpoch: Long = 0): History {
        require(target in bindings && target != credential && fromEpoch >= 0)
        val selected = archiveKeys.keys.filter { it >= fromEpoch }.sorted().take(129)
        val plaintext = JsonObject().apply {
            addProperty("version", 1); addProperty("kind", "history-keys")
            addProperty("group", requireNotNull(scope).groupId); addProperty("target", target)
            add("keys", JsonObject().apply { selected.take(128).forEach { addProperty(it.toString(), encode(requireNotNull(archiveKeys[it]))) } })
        }
        val header = header(wallet, "history", operationId, controlId).apply { addProperty("recipient_credential", target) }
        val response = step("send") { addProperty("text", plaintext.toString()) }
        header.addProperty("wire", response.text("wire"))
        val transaction = SaitoTransaction.create(wallet, header, timestamp, listOf(requireNotNull(scope).serviceKey))
        return History(advance(response, scope), transaction, selected.getOrNull(128))
    }
    /** Historical grants use the already established archive AEAD. Their
     * wrapping key is retained independently of live MLS application ratchets,
     * so another admission cannot make a durable grant unreadable. Exclude the
     * wrapping epoch itself: the recipient already obtained that exporter. */
    fun archiveHistory(wallet: SaitoWallet, target: String, operationId: String, controlId: String,
        timestamp: Long, fromEpoch: Long = 0): History {
        require(target in bindings && target != credential && fromEpoch >= 0)
        val selected = archiveKeys.keys.filter { it >= fromEpoch && it < epoch }.sorted().take(129)
        val payload = JsonObject().apply {
            addProperty("version", 1); addProperty("kind", "history-keys")
            addProperty("group", requireNotNull(scope).groupId); addProperty("target", target)
            add("keys", JsonObject().apply { selected.take(128).forEach { addProperty(it.toString(), encode(requireNotNull(archiveKeys[it]))) } })
        }
        val original = publishRecord(wallet, "history-keys", operationId, controlId, payload.toString(), timestamp, target)
        return History(this, original, selected.getOrNull(128))
    }
    fun acceptArchiveHistory(envelope: String): SpaceCrypto {
        val signed = authenticated(envelope, "content")
        require(signed.body.text("content_kind") == "history-keys" && signed.body.text("recipient_credential") == credential)
        val grantEpoch = signed.body.number("epoch").also { require(it in 1..epoch) }
        val payload = parse(read(envelope).text)
        return SpaceCrypto(native, scope, Collections.unmodifiableMap(importHistory(payload, grantEpoch - 1)))
    }

    fun acceptHistory(envelope: String): SpaceCrypto {
        val signed = authenticated(envelope, "history")
        require(signed.body.text("recipient_credential") == credential)
        val response = step("receive") { addProperty("wire", signed.body.text("wire")) }
        val sender = response.text("sender")
        require(sender in bindings && walletKey(sender) == signed.author)
        val payload = parse(response.text("text"))
        val next = advance(response, scope, importHistory(payload, epoch))
        require(next.bindings == bindings) { "A history grant cannot change membership" }
        return next
    }
    private fun importHistory(payload: JsonObject, maximumEpoch: Long): MutableMap<Long, ByteArray> {
        require(payload.number("version") == 1L && payload.text("kind") == "history-keys" &&
            payload.text("group") == requireNotNull(scope).groupId && payload.text("target") == credential)
        val keys = payload.getAsJsonObject("keys")
        require(keys.size() <= 128)
        val restored = archiveKeys.toMutableMap()
        for ((epochText, value) in keys.entrySet()) {
            require(epochText.matches(Regex("0|[1-9][0-9]{0,18}")) && value.isJsonPrimitive && value.asJsonPrimitive.isString)
            val oldEpoch = epochText.toLong()
            require(oldEpoch in 0..maximumEpoch)
            val key = decode(value.asString).also { require(it.size == 32) }
            restored[oldEpoch]?.let { known -> require(MessageDigest.isEqual(known, key)) { "Conflicting history key" } }
            restored[oldEpoch] = key
        }
        return restored
    }
    /** Secret bytes for the repository's authenticated at-rest transaction. */
    fun historyBackup(): ByteArray = SpaceHistoryKeys(walletKey(credential), requireNotNull(scope), archiveKeys).save()
    fun save(): ByteArray = JsonObject().apply {
        addProperty("version", 2); add("native", native.deepCopy())
        scope?.let { add("scope", it.json()) }
        add("archive_keys", JsonObject().apply { archiveKeys.toSortedMap().forEach { (epoch, key) -> addProperty(epoch.toString(), encode(key)) } })
    }.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_SNAPSHOT) }
    override fun toString(): String = "SpaceCrypto(retained keys)"

    private fun step(op: String, body: JsonObject.() -> Unit = {}): JsonObject = parse(NativeSpaceMls.exchange(
        JsonObject().apply { addProperty("op", op); addProperty("state", native.text("state")); body() }.toString()))
    private fun cancelled(response: JsonObject): SpaceCrypto {
        val result = parse(NativeSpaceMls.exchange(JsonObject().apply {
            addProperty("op", "cancel"); addProperty("state", response.text("pending_state"))
        }.toString()))
        return advance(result, requireNotNull(scope)).also { require(it.epoch == epoch && it.bindings == bindings) }
    }
    private fun advance(response: JsonObject, targetScope: Scope?, keys: Map<Long, ByteArray> = archiveKeys): SpaceCrypto {
        require(response.number("version") == 1L && response.text("identity") == credential && response.text("signer") == native.text("signer"))
        val nextKeys = keys.toMutableMap()
        if (targetScope != null) {
            require(response.text("group") == targetScope.groupId)
            val key = decode(response.text("archive_key")).also { require(it.size == 32) }
            val epoch = response.number("epoch")
            nextKeys[epoch]?.let { known -> require(MessageDigest.isEqual(known, key)) { "MLS epoch key changed without an epoch change" } }
            nextKeys[epoch] = key
        }
        require(nextKeys.size <= 16_384) { "Channel key storage capacity reached" }
        val retained = response.deepCopy().apply { listOf("archive_key", "text", "wire", "welcome", "pending_state").forEach { remove(it) } }
        if (targetScope == null && !retained.has("key_package") && native.has("key_package")) {
            retained.addProperty("key_package", native.text("key_package"))
        }
        return SpaceCrypto(retained, targetScope, nextKeys)
    }
    private fun header(wallet: SaitoWallet, kind: String, operationId: String, controlId: String): JsonObject {
        require(wallet.publicKey == walletKey(credential) && operationId.matches(ID) && controlId.matches(SIGNATURE))
        val scope = requireNotNull(scope)
        return JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
            addProperty("space_id", scope.spaceId); addProperty("channel_id", scope.channelId); addProperty("key_generation", scope.generation)
            addProperty("operation_id", operationId); addProperty("control_id", controlId)
            addProperty("kind", kind); addProperty("epoch", epoch)
        }
    }
    private fun authenticated(envelope: String, kind: String): SignedSaitoMessage =
        authenticate(requireNotNull(scope), envelope, kind)

    companion object {
        internal fun readArchived(scope: Scope, archiveKeys: Map<Long, ByteArray>, envelope: String): PlainRecord {
            val signed = authenticate(scope, envelope, "content")
            val key = archiveKeys[signed.body.number("epoch")] ?: throw MissingKeys()
            val bytes = decode(signed.body.text("ciphertext"))
            require(bytes.size in 29..(64 * 1024 + 28))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                updateAAD(archiveAad(scope, signed.body, signed.author))
            }
            val plaintext = cipher.doFinal(bytes.copyOfRange(12, bytes.size))
            return try { PlainRecord(signed.author, utf8(plaintext)) } finally { plaintext.fill(0) }
        }
        private fun authenticate(scope: Scope, envelope: String, kind: String): SignedSaitoMessage {
            val signed = SaitoEnvelope.decode(envelope)
            val body = signed.body
            require(body.text("module") == "Spaces" && body.text("request") == "event" && body.number("version") == 1L)
            require(body.text("space_id") == scope.spaceId && body.text("channel_id") == scope.channelId && body.text("kind") == kind)
            require(body.text("key_generation") == scope.generation)
            require(body.text("operation_id").matches(ID) && body.text("control_id").matches(SIGNATURE))
            require(signed.recipients == listOf(signed.author, scope.serviceKey).distinct())
            body.number("epoch")
            return signed
        }
        private fun archiveAad(scope: Scope, body: JsonObject, actor: String): ByteArray {
            val fields = listOf("SaitoSpacesRecord/v1", scope.groupId, actor, body.text("operation_id"),
                body.text("control_id"), body.number("epoch").toString(), body.text("content_kind"))
            // Ordinary archive ciphertext keeps its original AAD unchanged.
            val target = when (body.text("content_kind")) {
                "history-keys" -> listOf(body.text("recipient_credential"))
                "category-profile" -> listOf(body.text("target_category_id").also { require(it.matches(ID)) })
                "channel-profile-edit" -> if (body.has("target_channel_id"))
                    listOf(body.text("target_channel_id").also { require(it.matches(ID)) }) else emptyList()
                "message-edit", "message-delete", "thread-reply", "message-reaction", "message-pin" -> listOf(body.text("target_message_id").also { require(it.matches(SIGNATURE)) },
                    body.number("target_position").also { require(it >= 0) }.toString())
                else -> emptyList()
            }
            val thread = body.get("thread_root_id")?.asString?.also { require(it.matches(SIGNATURE)) }
            return (fields + target + if (thread == null) emptyList() else listOf("thread", thread)).joinToString(":").toByteArray(Charsets.UTF_8)
        }

        private val ID = Regex("[0-9a-f]{64}")
        private val SIGNATURE = Regex("[0-9a-f]{128}")
        private const val MAX_SNAPSHOT = 5 * 1024 * 1024
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
        private fun identity(wallet: String, device: String) = "SaitoSpaces/v1:$wallet:$device"
        private fun walletKey(credential: String): String {
            val parts = credential.split(':')
            require(parts.size == 3 && parts[0] == "SaitoSpaces/v1" && SaitoWallet.isPublicKey(parts[1]) && parts[2].matches(ID))
            return parts[1]
        }
        private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
        private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
        private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        private fun parse(text: String): JsonObject = gson.fromJson(text, JsonObject::class.java)
        private fun utf8(bytes: ByteArray) = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        private fun JsonObject.text(name: String): String {
            val value = get(name)
            require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
            return value.asString
        }
        private fun JsonObject.number(name: String): Long {
            val value = get(name)
            require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asString.matches(Regex("0|[1-9][0-9]{0,18}")))
            return value.asString.toLong().also { require(it >= 0) }
        }
        fun device(walletKey: String, deviceId: String): SpaceCrypto {
            require(SaitoWallet.isPublicKey(walletKey) && deviceId.matches(ID))
            val response = parse(NativeSpaceMls.exchange(JsonObject().apply {
                addProperty("op", "new"); addProperty("identity", identity(walletKey, deviceId))
            }.toString()))
            require(response.number("version") == 1L && response.text("identity") == identity(walletKey, deviceId))
            return SpaceCrypto(response, null, emptyMap())
        }
        fun restore(snapshot: ByteArray): SpaceCrypto {
            require(snapshot.size in 1..MAX_SNAPSHOT)
            val stored = parse(utf8(snapshot))
            require(stored.number("version") == 2L) { "Unsupported Space key snapshot version" }
            val native = stored.getAsJsonObject("native")
            walletKey(native.text("identity"))
            val scope = stored.getAsJsonObject("scope")?.let(Scope::fromJson)
            val keys = stored.getAsJsonObject("archive_keys").entrySet().associate { (epoch, value) ->
                require(epoch.matches(Regex("0|[1-9][0-9]{0,18}")) && value.isJsonPrimitive && value.asJsonPrimitive.isString)
                epoch.toLong() to decode(value.asString).also { require(it.size == 32) }
            }
            val current = SpaceCrypto(native, scope, keys)
            return current.advance(current.step("status"), scope)
        }
    }
}
