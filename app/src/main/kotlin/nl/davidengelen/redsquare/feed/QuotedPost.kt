package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.FeedPost

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
