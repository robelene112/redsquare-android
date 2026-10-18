package nl.davidengelen.redsquare

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*

/** Concrete private and group boundaries share interruption mechanics, not their storage or message formats. */
sealed class ProcessChatConversation(val kind: String, val wakeByte: String,
    val ledgerFamily: String, val wakeSegment: String, val text: String, val legacyText: String) {
    val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe synthetic identities.
    val sender = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    abstract val historyName: String
    abstract fun incoming(text: String, timestamp: Long): SaitoTransaction
    abstract fun seedLegacy(context: Context, message: SaitoTransaction)
    abstract fun assertHistory(context: Context, checkpoint: JsonObject)
}

class PrivateProcessChat : ProcessChatConversation("private", "49", "chatpush", "wake",
    "Recover this retained private message after an actual process interruption",
    "Keep this older cached message through the private receipt update") {
    override val historyName get() = "chat.owner.${wallet.publicKey}.${PrivateChat.conversationId(wallet.publicKey, sender.publicKey)}.v1"
    override fun incoming(text: String, timestamp: Long) = PrivateChat.create(sender, wallet.publicKey, text, timestamp)

    override fun seedLegacy(context: Context, message: SaitoTransaction) {
        // Exact pre-pending-metadata array format from1f6406e7.
        EncryptedLocalFile(context, historyName, "redsquare.chat.v1", "chat.").save(
            EncryptedLocalFile.Contents(1, JsonArray().apply { add(message.envelope()) }.toString()))
        assertEquals(legacyText, ChatHistoryStore(context, wallet, SaitoBackend.OWNER, sender.publicKey)
            .load().entries.single().message.text)
        assertTrue(sender.publicKey in ChatHistoryStore.contacts(context, wallet, SaitoBackend.OWNER).accounts)
    }

    override fun assertHistory(context: Context, checkpoint: JsonObject) {
        val entries = ChatHistoryStore(context, wallet, SaitoBackend.OWNER, sender.publicKey).load().entries
        assertEquals(2, entries.size)
        val older = entries.single { it.message.id == checkpoint.get("legacySignature").asString }
        assertEquals(legacyText, older.message.text); assertEquals(sender.publicKey, older.message.author)
        val entry = entries.single { it.message.id == checkpoint.get("signature").asString }
        assertEquals(checkpoint.get("signature").asString, entry.message.id)
        assertEquals(text, entry.message.text); assertEquals(sender.publicKey, entry.message.author)
        assertTrue(sender.publicKey in ChatHistoryStore.contacts(context, wallet, SaitoBackend.OWNER).accounts)
    }
}

class GroupProcessChat : ProcessChatConversation("group", "74", "groupchatpush", "group-wake",
    "Recover this retained group message after an actual process interruption",
    "Keep this older cached message through the group receipt update") {
    private val groupKey = SaitoWallet.fromPrivateKey("0".repeat(63) + "9").publicKey
    private val name = "Retained process recovery group"
    private val draft = "Keep this unsent group draft through process recovery"
    private val creation = GroupChat.create(sender, groupKey, name, listOf(wallet.publicKey), System.currentTimeMillis() - 2_000)
    private val group = GroupChat.readCreation(creation.envelope())
    override val historyName get() = "groupchat.owner.${wallet.publicKey}.$groupKey.v1"
    override fun incoming(text: String, timestamp: Long) = GroupChat.message(sender, group, text, timestamp).also {
        assertFalse(wallet.publicKey in SaitoEnvelope.decode(it.envelope()).recipients)
    }

    override fun seedLegacy(context: Context, message: SaitoTransaction) {
        // Pre47 protected record shape fromd6a64a25, intentionally without pendingAlerts.
        val record = JsonObject().apply {
            addProperty("creation", creation.envelope()); add("controls", JsonArray())
            add("events", JsonArray().apply { add(message.envelope()) })
            add("candidate", JsonNull.INSTANCE); addProperty("status", "RECEIVED")
            addProperty("draft", draft); addProperty("automaticMessage", false); add("stopped", JsonArray())
        }
        EncryptedLocalFile(context, historyName, "redsquare.groupchat.v1", "groupchat.").save(
            EncryptedLocalFile.Contents(1, record.toString()))
        assertEquals(legacyText, GroupChatStore(context, wallet, SaitoBackend.OWNER).load(groupKey)!!.messages.single().text)
    }

    override fun assertHistory(context: Context, checkpoint: JsonObject) {
        val saved = requireNotNull(GroupChatStore(context, wallet, SaitoBackend.OWNER).load(groupKey))
        assertEquals(name, saved.group.name); assertEquals(draft, saved.draft)
        assertEquals(ChatGroupRole.MEMBER, saved.group.memberRoles[wallet.publicKey])
        assertEquals(ChatGroupRole.ADMIN, saved.group.memberRoles[sender.publicKey])
        assertEquals(GroupChatStore.Status.RECEIVED, saved.status); assertNull(saved.candidate)
        assertEquals(2, saved.messages.size)
        val older = saved.messages.single { it.id == checkpoint.get("legacySignature").asString }
        assertEquals(legacyText, older.text); assertEquals(sender.publicKey, older.author)
        val entry = saved.messages.single { it.id == checkpoint.get("signature").asString }
        assertEquals(text, entry.text); assertEquals(sender.publicKey, entry.author)
    }
}
