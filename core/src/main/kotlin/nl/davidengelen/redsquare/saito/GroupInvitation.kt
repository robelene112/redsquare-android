package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** Unsigned link claims. Parsing never grants membership or selects a backend. */
class GroupInvitation internal constructor(val id: String, val name: String, val sender: String, val admin: String) {
    fun encoded(): String {
        val claims = JsonObject().apply {
            addProperty("id",id);addProperty("name",name);addProperty("sender",sender);addProperty("admin",admin)
        }
        return Base64.getEncoder().encodeToString(claims.toString().toByteArray(Charsets.UTF_8))
    }

    fun link(backend: SaitoBackend): String = backend.webUrl + "/chat?chat_id=" + encoded()

    /** Retained authority determines routing; the inviter claim never grants a role. */
    fun usingKnownGroup(group:NamedChatGroup):GroupInvitation {
        require(id==group.id)
        val knownAdmin=group.memberRoles.entries.firstOrNull{it.value==ChatGroupRole.ADMIN}?.key
            ?: group.creationEnvelope?.let{SaitoEnvelope.decode(it).author}
            ?: requireNotNull(group.confirmedInvitation).admin
        return GroupInvitation(id,group.name,sender,knownAdmin)
    }

    class Invalid : IllegalArgumentException("Group invitation could not be read")

    companion object {
        private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

        fun fromGroup(group: NamedChatGroup, sender: String): GroupInvitation {
            require(group.memberRoles[sender]?.active == true)
            val admin = requireNotNull(group.memberRoles.entries.firstOrNull { it.value == ChatGroupRole.ADMIN }?.key)
            return GroupInvitation(group.id,group.name,sender,admin)
        }

        fun parse(text: String): GroupInvitation = try {
            read(text)
        } catch (_: Exception) {
            // Parser diagnostics can include pasted content. Expose only a stable static error.
            throw Invalid()
        }

        private fun read(text: String): GroupInvitation {
            require(text.length <= 2 * SaitoTransaction.MAX_MESSAGE_BYTES)
            val input = text.trim()
            val encoded = if (input.startsWith("https://", ignoreCase = true)) {
                val uri = URI(input)
                require(uri.host != null && uri.rawUserInfo == null && uri.path in listOf("/chat", "/chat/"))
                val values = uri.rawQuery.orEmpty().split('&').filter { it.startsWith("chat_id=") }
                require(values.size == 1)
                // The existing client appends raw Base64, so '+' is data, not a form-encoded space.
                URLDecoder.decode(values.single().substringAfter('=').replace("+", "%2B"), Charsets.UTF_8.name())
            } else input
            val bytes = Base64.getDecoder().decode(encoded)
            require(bytes.size in 1..SaitoTransaction.MAX_MESSAGE_BYTES)
            val json = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            val data = gson.fromJson(json, JsonObject::class.java)
            fun field(name: String): String {
                val value = data.get(name)
                require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
                return value.asString
            }
            val id = field("id")
            val name = field("name")
            val sender = field("sender")
            val admin = field("admin")
            require(listOf(id, sender, admin).all(SaitoWallet::isPublicKey))
            require(id != sender && id != admin && name.isNotBlank() && Charsets.UTF_8.newEncoder().canEncode(name))
            return GroupInvitation(id, name, sender, admin)
        }
    }
}
