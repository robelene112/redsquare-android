package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.util.Base64

enum class AuthorActionKind(val request: String, val storageId: String) {
    EDIT("edit tweet", "edit"), DELETE("delete tweet", "delete")
}

data class AuthorAction(val id: String, val author: String, val target: String, val kind: AuthorActionKind,
    val text: String, val createdAt: Long, val envelope: String)

/** An author command is authenticated intent; application or removal requires a separate observation. */
object RedSquareAuthorActions {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun decode(envelope: String): AuthorAction {
        val signed = SaitoEnvelope.decode(envelope)
        require(signed.body.get("module").asString == "RedSquare")
        val kind = AuthorActionKind.entries.single { it.request == signed.body.get("request").asString }
        val data = signed.body.getAsJsonObject("data")
        val target = data.get("tweet_id").asJsonPrimitive
        require(target.isString && target.asString.matches(Regex("[0-9a-f]{128}")))
        val text = if (kind == AuthorActionKind.EDIT) data.get("text").asJsonPrimitive.let {
            require(it.isString); it.asString
        } else ""
        return AuthorAction(signed.id, signed.author, target.asString, kind, text, signed.createdAt, envelope)
    }

    /** Unsigned optional metadata only locates the signed edit. It does not supply authority or ordering. */
    fun edit(original: SignedSaitoMessage, envelope: String): AuthorAction? = try {
        val outer = gson.fromJson(envelope, JsonObject::class.java)
        val options = gson.fromJson(Base64.getDecoder().decode(outer.get("opt").asString).toString(Charsets.UTF_8), JsonObject::class.java)
        val nested = options.get("update_tx").asJsonPrimitive
        require(nested.isString)
        decode(nested.asString).takeIf { it.kind == AuthorActionKind.EDIT && it.author == original.author &&
            it.target == original.id && it.createdAt > 0 }
    } catch (_: Exception) { null }
}
