package nl.davidengelen.redsquare.chat

import android.text.Html
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import nl.davidengelen.redsquare.saito.PrivateChatMessage
import nl.davidengelen.redsquare.saito.SaitoWallet

// Interpret only Saito's validated leading reply wrapper. Typed text stays literal.
private val replyTimestamps = 0L..9_007_199_254_740_991L
private val replyPrefix = Regex("""\A<blockquote href="([0-9A-HJ-NP-Za-km-z]{44,60})">(.*?)</blockquote>""",
    RegexOption.DOT_MATCHES_ALL)

internal data class PrivateReply(val prefix: String, val reference: String, val author: String,
    val quote: String, val body: String)

internal fun privateReply(original: String): PrivateReply? {
    val match = replyPrefix.find(original) ?: return null
    val reference = match.groupValues[1]
    val author = listOf(43, 44).firstNotNullOfOrNull { length ->
        val key = reference.take(length)
        val timestamp = reference.drop(length)
        key.takeIf { SaitoWallet.isPublicKey(key) && timestamp.matches(Regex("[0-9]{1,16}")) &&
            timestamp.toLongOrNull()?.let { it in replyTimestamps } == true }
    } ?: return null
    return PrivateReply(match.value, reference, author,
        Html.fromHtml(match.groupValues[2], Html.FROM_HTML_MODE_LEGACY).toString().trimEnd('\n'),
        original.substring(match.range.last + 1))
}

internal fun privateMessageBody(original: String): String = privateReply(original)?.body ?: original

internal fun privateReplyPrefix(message: PrivateChatMessage): String? {
    if (message.createdAt !in replyTimestamps) return null
    var quote = privateMessageBody(message.text)
    if (quote.length > 30) {
        var suffix = quote.takeLast(30)
        // Do not produce malformed UTF16 when clipping an emoji at the excerpt boundary.
        if (suffix.first().isLowSurrogate()) suffix = suffix.drop(1)
        quote = "...$suffix"
    }
    val escaped = quote.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return "<blockquote href=\"${message.author}${message.createdAt}\">$escaped</blockquote>"
}

@Composable
internal fun PrivateMessageText(original: String, onQuote: ((String) -> Unit)? = null) {
    val reply = remember(original) { privateReply(original) }
    Column {
        if (reply != null) {
            Text(reply.quote, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (onQuote == null) Modifier else Modifier.clickable { onQuote(reply.reference) })
        }
        Text(reply?.body ?: original, style = MaterialTheme.typography.bodyLarge)
    }
}
