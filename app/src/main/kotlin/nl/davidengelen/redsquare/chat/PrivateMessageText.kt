package nl.davidengelen.redsquare.chat

import android.text.Html
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import nl.davidengelen.redsquare.saito.SaitoWallet

// Saito Input prefixes replies with a sanitized blockquote whose href is sender + timestamp.
// Interpret only that prefix. The typed body and the retained signed message stay literal.
private val replyPrefix = Regex("""\A<blockquote href="([1-9A-HJ-NP-Za-km-z]{44,60})">(.*?)</blockquote>""",
    RegexOption.DOT_MATCHES_ALL)

@Composable
internal fun PrivateMessageText(original: String) {
    val reply = remember(original) {
        replyPrefix.find(original)?.takeIf { match ->
            val reference = match.groupValues[1]
            // Base58 keys have variable length and can end in digits. Try both valid lengths.
            listOf(43, 44).any { length ->
                val timestamp = reference.drop(length)
                SaitoWallet.isPublicKey(reference.take(length)) && timestamp.matches(Regex("[0-9]{1,16}")) &&
                    timestamp.toLongOrNull()?.let { it in 1..9_007_199_254_740_991L } == true
            }
        }
    }
    Column {
        if (reply != null) {
            val quoted = remember(reply) {
                Html.fromHtml(reply.groupValues[2], Html.FROM_HTML_MODE_LEGACY).toString().trimEnd('\n')
            }
            Text(quoted, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (reply == null) original else original.substring(reply.range.last + 1),
            style = MaterialTheme.typography.bodyLarge)
    }
}
