package nl.davidengelen.redsquare.saito

data class ProfileLike(val id: String, val author: String, val target: String, val createdAt: Long, val envelope: String)
data class ArchiveLikePage(val likes: List<ProfileLike>, val ignored: Int, val oldestCreatedAt: Long?, val rowCount: Int)

object RedSquareLikes {
    fun decode(envelope: String): ProfileLike {
        val signed = SaitoEnvelope.decode(envelope)
        require(signed.body.get("module").asString == "RedSquare" && signed.body.get("request").asString == "like tweet")
        val target = signed.body.getAsJsonObject("data").get("signature").asJsonPrimitive
        require(target.isString && target.asString.matches(Regex("[0-9a-f]{128}")))
        return ProfileLike(signed.id, signed.author, target.asString, signed.createdAt, envelope)
    }

    fun readArchive(response: String, author: String): ArchiveLikePage {
        val page = readArchiveRows(response, ::decode, { it.author == author }) { it.id }
        return ArchiveLikePage(page.entries, page.ignored, page.oldestCreatedAt, page.rowCount)
    }

    /** Incoming signed actors; callers verify target ownership before saying "your post". */
    fun readIncoming(response: String, owner: String): ArchiveLikePage {
        val page = readArchiveRows(response, ::decode, { it.author != owner }) { it.id }
        return ArchiveLikePage(page.entries, page.ignored, page.oldestCreatedAt, page.rowCount)
    }
}
