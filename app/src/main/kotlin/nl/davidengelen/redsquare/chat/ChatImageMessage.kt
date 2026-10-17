package nl.davidengelen.redsquare.chat

import nl.davidengelen.redsquare.saito.RedSquareImages

// Chat's upload callback sends one img.img-prev, never an arbitrary HTML document.
private val imageMessage = Regex("""\A<img class="img-prev" src="([^"<>\r\n]+)">(?:\n<div>(.*)</div>)?\z""", RegexOption.DOT_MATCHES_ALL)

internal data class ChatPhoto(val source: String, val text: String)

internal fun chatPhoto(message: String): ChatPhoto? {
    val match = imageMessage.matchEntire(message) ?: return null
    val source = RedSquareImages.safeSource(unescapePhotoText(match.groupValues[1])) ?: return null
    val escaped = match.groupValues[2]
    val text = unescapePhotoText(escaped)
    if (escapePhotoText(text) != escaped) return null // Interpret only canonical escaped text, never arbitrary markup.
    return ChatPhoto(source, text)
}

internal fun composeChatPhoto(source: String, text: String): String {
    require(RedSquareImages.safeSource(source) == source)
    return "<img class=\"img-prev\" src=\"${escapePhotoText(source)}\">" + if (text.isEmpty()) "" else "\n<div>${escapePhotoText(text)}</div>"
}

/** Preserve the existing reply prefix and caption; Chat accepts one attachment. */
internal fun composeChatGif(message: String, source: String): String? {
    val reply = privateReply(message)
    val body = reply?.body ?: message
    if (chatPhoto(body) != null) return null
    return (reply?.prefix ?: "") + composeChatPhoto(source, body)
}

private fun escapePhotoText(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    .replace("\"", "&quot;").replace("'", "&#39;")

private fun unescapePhotoText(text: String) = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
    .replace("&#39;", "'").replace("&amp;", "&")
