package nl.davidengelen.redsquare.saito

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.Strictness

data class ProfileLike(val id: String, val author: String, val target: String, val createdAt: Long, val envelope: String)
data class ArchiveLikePage(val likes: List<ProfileLike>, val ignored: Int)

object RedSquareLikes {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun decode(envelope: String): ProfileLike {
        val signed = SaitoEnvelope.decode(envelope)
        require(signed.body.get("module").asString == "RedSquare" && signed.body.get("request").asString == "like tweet")
        val target = signed.body.getAsJsonObject("data").get("signature").asJsonPrimitive
        require(target.isString && target.asString.matches(Regex("[0-9a-f]{128}")))
        return ProfileLike(signed.id, signed.author, target.asString, signed.createdAt, envelope)
    }

    fun readArchive(response: String, author: String): ArchiveLikePage {
        require(response.toByteArray(Charsets.UTF_8).size <= 16 * 1024 * 1024)
        val rows = gson.fromJson(response, JsonArray::class.java)
            ?: throw IllegalArgumentException("Missing likes response")
        require(rows.size() <= 100)
        var ignored = 0
        val likes = rows.mapNotNull { row ->
            try { decode(row.asJsonObject.get("tx").asString).takeIf { it.author == author }
                .also { if (it == null) ignored++ } }
            catch (_: Exception) { ignored++; null }
        }
        return ArchiveLikePage(likes.distinctBy { it.id }, ignored)
    }
}
