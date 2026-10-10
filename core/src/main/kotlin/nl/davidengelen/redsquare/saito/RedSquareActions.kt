package nl.davidengelen.redsquare.saito

enum class RedSquareActionKind(val request: String, val archiveKind: String, val storageId: String) {
    LIKE("like tweet", "RedSquareLike", "like"),
    REPOST("retweet", "RedSquareRetweet", "repost"),
}

data class RedSquareAction(val id: String, val author: String, val target: String,
    val kind: RedSquareActionKind, val envelope: String)

object RedSquareActions {
    fun decode(envelope: String): RedSquareAction {
        val signed = SaitoEnvelope.decode(envelope)
        require(signed.body.get("module").asString == "RedSquare")
        val kind = RedSquareActionKind.entries.single { it.request == signed.body.get("request").asString }
        val target = signed.body.getAsJsonObject("data").get("signature").asJsonPrimitive
        require(target.isString && target.asString.matches(Regex("[0-9a-f]{128}")))
        return RedSquareAction(signed.id, signed.author, target.asString, kind, envelope)
    }

    fun readArchive(response: String, author: String, kind: RedSquareActionKind): List<RedSquareAction> =
        readArchiveRows(response, ::decode, { it.author == author && it.kind == kind }, { it.id }).entries
}
