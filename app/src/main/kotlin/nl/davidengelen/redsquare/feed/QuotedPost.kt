package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import nl.davidengelen.redsquare.saito.FeedPost

/** One original level, attributed only after independent signature verification. */
@Composable
internal fun QuotedPost(original: FeedPost) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            Text("Anon-${original.author.take(6)}", style = MaterialTheme.typography.labelMedium)
            Text(original.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
