package nl.davidengelen.redsquare.spaces

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Bearer material stays in the URI fragment, never in an HTTP request URL.
 * The capability wallet is an invitation key, never the user's Saito wallet. */
internal class SpaceInvitation(val node: String, val scope: SpaceCrypto.Scope, val invitationId: String,
    private val capability: SaitoWallet, private val previewKey: ByteArray) {
    init { require(node.isNotBlank() && node.length <= 2048 && invitationId.matches(Regex("[0-9a-f]{128}")) && previewKey.size == 32) }
    fun proof(caller: String, nonce: String): String = capability.sign(
        "SaitoSpacesPreview/v1:${scope.serviceKey}:${scope.spaceId}:$invitationId:$caller:$nonce".toByteArray(Charsets.UTF_8))
    fun joinProof(caller: String, operation: String, binding: String): String = capability.sign(
        "SaitoSpacesJoin/v1:${scope.groupId}:$invitationId:$caller:$operation:$binding".toByteArray(Charsets.UTF_8))
    fun preview(ciphertext: String, operation: String): String = decryptPreview(scope, previewKey, operation, ciphertext)
    fun link(): String {
        val value = JsonObject().apply {
            addProperty("version", 1); addProperty("node", node); add("scope", scope.json())
            addProperty("invitation", invitationId)
            addProperty("capability", GSON.fromJson(capability.exportJson(), JsonObject::class.java).getAsJsonObject("wallet").get("privateKey").asString)
            addProperty("preview_key", Base64.getEncoder().encodeToString(previewKey))
        }
        return "saito://spaces/join#" + Base64.getUrlEncoder().withoutPadding().encodeToString(value.toString().toByteArray(Charsets.UTF_8))
    }
    override fun toString() = "Private community invitation"
    companion object {
        private val GSON = GsonBuilder().setStrictness(Strictness.STRICT).create()
        fun parse(link: String): SpaceInvitation {
            require(link.length in 1..8192)
            val uri = URI(link)
            require(uri.scheme == "saito" && uri.host == "spaces" && uri.path == "/join" && uri.rawQuery == null && uri.userInfo == null && uri.port == -1)
            val text = Base64.getUrlDecoder().decode(requireNotNull(uri.rawFragment)).toString(Charsets.UTF_8)
            val value = GSON.fromJson(text, JsonObject::class.java)
            require(value.keySet() == setOf("version", "node", "scope", "invitation", "capability", "preview_key") && value.get("version").toString() == "1")
            return SpaceInvitation(value.get("node").asString, SpaceCrypto.Scope.fromJson(value.getAsJsonObject("scope")),
                value.get("invitation").asString, SaitoWallet.fromPrivateKey(value.get("capability").asString),
                Base64.getDecoder().decode(value.get("preview_key").asString))
        }
        fun encryptPreview(scope: SpaceCrypto.Scope, key: ByteArray, operation: String, name: String): String {
            require(key.size == 32 && name.isNotBlank() && name.toByteArray(Charsets.UTF_8).size <= 256)
            val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
            return Base64.getEncoder().encodeToString(nonce + Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(aad(scope, operation)); doFinal(name.toByteArray(Charsets.UTF_8))
            })
        }
        private fun decryptPreview(scope: SpaceCrypto.Scope, key: ByteArray, operation: String, encoded: String): String {
            val bytes = Base64.getDecoder().decode(encoded).also { require(it.size in 29..284) }
            return Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                updateAAD(aad(scope, operation)); doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
            }.also { require(it.isNotBlank()) }
        }
        private fun aad(scope: SpaceCrypto.Scope, operation: String): ByteArray {
            require(operation.matches(Regex("[0-9a-f]{64}")))
            return "SaitoSpacesPreview/v1:${scope.groupId}:$operation".toByteArray(Charsets.UTF_8)
        }
    }
}
