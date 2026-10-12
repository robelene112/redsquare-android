package nl.davidengelen.redsquare.saito

enum class RedSquareActionKind(val request: String, val archiveKind: String, val storageId: String) {
    LIKE("like tweet", "RedSquareLike", "like"),
    REPOST("retweet", "RedSquareRetweet", "repost"),
}

data class RedSquareAction(val id: String, val author: String, val target: String,
    val kind: RedSquareActionKind, val envelope: String, val createdAt: Long, val recipients: List<String>)

data class ArchiveActionPage(val actions: List<RedSquareAction>, val ignored: Int,
    val oldestCreatedAt: Long?, val rowCount: Int)

object RedSquareActions {
    fun decode(envelope: String): RedSquareAction {
        val signed = SaitoEnvelope.decode(envelope)
        require(signed.body.get("module").asString == "RedSquare")
        val kind = RedSquareActionKind.entries.single { it.request == signed.body.get("request").asString }
        val target = signed.body.getAsJsonObject("data").get("signature").asJsonPrimitive
        require(target.isString && target.asString.matches(Regex("[0-9a-f]{128}")))
        return RedSquareAction(signed.id, signed.author, target.asString, kind, envelope, signed.createdAt, signed.recipients)
    }

    fun readIncomingReposts(response: String, owner: String): ArchiveActionPage {
        data class Row(val action: RedSquareAction, val ordinary: Boolean)
        val page = readArchiveRows(response, { envelope ->
            val action = decode(envelope)
            require(action.kind == RedSquareActionKind.REPOST)
            val data = SaitoEnvelope.decode(envelope).body.getAsJsonObject("data")
            val text = data.get("text")?.takeUnless { it.isJsonNull }
            val images = data.get("images")?.takeUnless { it.isJsonNull }
            require(text == null || text.isJsonPrimitive && text.asJsonPrimitive.isString)
            require(images == null || images.isJsonArray)
            Row(action, text?.asString.orEmpty().isBlank() && (images == null || images.asJsonArray.isEmpty))
        }, { true }, { it.action.id })
        return ArchiveActionPage(page.entries.filter { it.ordinary && it.action.author != owner }.map { it.action },
            page.ignored, page.oldestCreatedAt, page.rowCount)
    }

    fun readArchive(response: String, author: String, kind: RedSquareActionKind): List<RedSquareAction> =
        readArchiveRows(response, ::decode, { it.author == author && it.kind == kind }, { it.id }).entries
}
