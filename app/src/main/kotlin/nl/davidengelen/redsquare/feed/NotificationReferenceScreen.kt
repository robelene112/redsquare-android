package nl.davidengelen.redsquare.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet

/** Retain the signed activity while an independently verified original is unavailable. */
@Composable
internal fun NotificationReferenceScreen(wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend,
    target: String, originals: QuoteState, modifier: Modifier, onBack: () -> Unit,
    onIdentity: (String) -> Unit, onPost: (FeedPost) -> Unit, social: SocialState?,
    onQuote: (FeedPost) -> Unit) {
    LaunchedEffect(target, originals) { originals.load(target, refresh = true) }
    val status = originals.originals[target]
    val post = status?.post
    if (post != null) {
        ConversationScreen(wallet, endpoint, backend, post, modifier, onBack, onIdentity,
            onPost, social, originals, onQuote)
        return
    }
    BackHandler(onBack = onBack)
    Column(modifier) {
        Row {
            TextButton(onClick = onBack) { Text(stringResource(R.string.browse_back)) }
            Text(stringResource(R.string.notifications_target_title), style = MaterialTheme.typography.titleLarge)
        }
        Text(stringResource(when {
            status == null || status.loading -> R.string.quote_loading
            status.failed -> R.string.quote_failed
            else -> R.string.notifications_target_unavailable
        }))
        if (status?.cacheWarning == true) Text(stringResource(R.string.quote_cache_warning))
        if (status != null && !status.loading) TextButton(onClick = { originals.retry(target) }) {
            Text(stringResource(R.string.retry))
        }
    }
}
