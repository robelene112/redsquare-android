package nl.davidengelen.redsquare.chat

private const val VISUAL_GROUP_GAP_MILLIS = 5 * 60 * 1000L

internal data class ChatVisualGroup<T>(val headerKey: String, val senderPublicKey: String, val messages: List<T>)

/** Inputs are admitted signed history, in its existing order. Records are never merged or sorted. */
internal fun <T> chatVisualGroups(messages: List<T>, id: (T) -> String, author: (T) -> String,
    createdAt: (T) -> Long): List<ChatVisualGroup<T>> {
    val groups = mutableListOf<MutableList<T>>()
    for (message in messages) {
        val previous = groups.lastOrNull()?.lastOrNull()
        val continues = previous != null && author(previous) == author(message) &&
            createdAt(message) >= createdAt(previous) &&
            createdAt(message) - createdAt(previous) in 0..VISUAL_GROUP_GAP_MILLIS
        if (continues) groups.last().add(message) else groups.add(mutableListOf(message))
    }
    return groups.map { group ->
        ChatVisualGroup("sender:${id(group.first())}", author(group.first()), group.toList())
    }
}
