package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.FeedPost

@Composable
internal fun QuoteContent(post: FeedPost, state: QuoteState?) {
    if (post.quoteTarget.isEmpty()) return
    LaunchedEffect(post.quoteTarget, state) { if (post.quoted == null) state?.load(post.quoteTarget) }
    val status = state?.originals?.get(post.quoteTarget)
    val original = post.quoted ?: status?.post
    if (status?.cacheWarning == true) Text(stringResource(R.string.quote_cache_warning))
    if (original != null) QuotedPost(original)
    else {
        Text(stringResource(when {
            status?.loading == true -> R.string.quote_loading
            status?.failed == true -> R.string.quote_failed
            else -> R.string.quote_unavailable
        }))
        if (state != null && status?.loading != true) TextButton(onClick = { state.retry(post.quoteTarget) }) {
            Text(stringResource(R.string.retry))
        }
    }
}

/** One original level, attributed only after independent signature verification. */
@Composable
internal fun QuotedPost(original: FeedPost) {
    val label = stringResource(R.string.quote_original)
    OutlinedCard(Modifier.fillMaxWidth().semantics { contentDescription = label }) {
        Column {
            Text("Anon-${original.author.take(6)}", style = MaterialTheme.typography.labelMedium)
            Text(original.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
