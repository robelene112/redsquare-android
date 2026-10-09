package nl.davidengelen.redsquare.feed

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nl.davidengelen.redsquare.R

@Composable
internal fun FollowingStatus(following: FollowingState?) {
    if (following?.error != true) return
    Text(stringResource(R.string.following_error), style = MaterialTheme.typography.bodySmall)
    if (!following.ready) TextButton(enabled = !following.loading, onClick = following::retry) {
        Text(stringResource(R.string.retry))
    }
}
