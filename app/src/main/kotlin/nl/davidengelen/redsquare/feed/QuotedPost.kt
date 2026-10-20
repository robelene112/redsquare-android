package nl.davidengelen.redsquare.feed

import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.registry.registeredIdentity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.ui.AppSpacing

@Composable
internal fun QuoteContent(post: FeedPost, state: QuoteState?, backend: SaitoBackend) {
    if (post.quoteTarget.isEmpty()) return
    val revisions by AndroidPostRevisions.changes.collectAsState()
    LaunchedEffect(post.quoteTarget, state, post.quoted, revisions) { state?.load(post.quoteTarget, post.quoted, refresh = true) }
    val status = state?.originals?.get(post.quoteTarget)
    val original = if (state == null) post.quoted else status?.post
    if (status?.cacheWarning == true) Text(stringResource(R.string.quote_cache_warning))
    if (original != null) QuotedPost(original, backend)
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
internal fun QuotedPost(original: FeedPost, backend: SaitoBackend) {
    val personal = LocalPersonalControls.current
    val label = stringResource(R.string.quote_original)
    OutlinedCard(Modifier.fillMaxWidth().semantics { contentDescription = label }) {
        Column(Modifier.padding(AppSpacing.small), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (personal?.hidesPosts(original.author) != false) {
                Text(stringResource(R.string.personal_quote_hidden), style = MaterialTheme.typography.bodySmall)
            } else {
                Text(registeredIdentity(original.author).name, style = MaterialTheme.typography.labelMedium)
                Text(original.text, style = MaterialTheme.typography.bodyMedium)
                PostImages(original.images, backend)
                PostVideo(original, backend)
            }
        }
    }
}
