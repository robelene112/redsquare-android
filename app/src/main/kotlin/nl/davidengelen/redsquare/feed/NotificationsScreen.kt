package nl.davidengelen.redsquare.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.NotificationPermissionPrompt
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.moderation.LocalPersonalControls
import nl.davidengelen.redsquare.moderation.PersonalControlsStatus
import nl.davidengelen.redsquare.registry.registeredIdentity
import nl.davidengelen.redsquare.saito.*

@Composable
internal fun NotificationsScreen(wallet: SaitoWallet?, endpoint: String, knownPosts: List<FeedPost>,
    modifier: Modifier, onBack: () -> Unit, onPost: (FeedPost) -> Unit, onIdentity: (String) -> Unit) {
    val personal = LocalPersonalControls.current
    var events by remember(wallet?.publicKey, endpoint) { mutableStateOf<List<ProfileLike>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var ignored by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    LaunchedEffect(wallet, endpoint, refresh) {
        if (wallet == null) return@LaunchedEffect
        loading = true
        try {
            val page = withContext(Dispatchers.IO) {
                val criteria = JsonObject().apply {
                    addProperty("request", "load"); addProperty("field1", "RedSquareLike")
                    addProperty("field3", wallet.publicKey); addProperty("flagged_ne", 1)
                    addProperty("limit", 100); addProperty("created_earlier_than", System.currentTimeMillis() + 1)
                }
                val request = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                SaitoPeer(wallet, endpoint).use { peer ->
                    RedSquareLikes.readIncoming(peer.request(request.toString().toByteArray(Charsets.UTF_8))
                        .toString(Charsets.UTF_8), wallet.publicKey)
                }
            }
            events = page.likes.sortedByDescending { it.createdAt }
            ignored = page.ignored
            failed = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    Column(modifier) {
        Row {
            TextButton(onClick = onBack) { Text(stringResource(R.string.browse_back)) }
            Text(stringResource(R.string.notifications_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = wallet != null && !loading, onClick = { refresh++ }) { Text(stringResource(R.string.feed_refresh)) }
        }
        PersonalControlsStatus(personal)
        if (loading) Text(stringResource(R.string.notifications_loading))
        if (failed) Text(stringResource(R.string.notifications_read_error))
        if (ignored > 0) Text(stringResource(R.string.notifications_unverified))
        LazyColumn(Modifier.weight(1f)) {
            items(events.filter { personal?.hidesPosts(it.author) == false }, key = { it.id }) { event ->
                // These posts came from the signed-content reader; archive counters are not events.
                val target = knownPosts.firstOrNull { it.id == event.target && it.author == wallet?.publicKey }
                if (target != null) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(registeredIdentity(event.author).name, Modifier.clickable { onIdentity(event.author) })
                        Text(event.author, Modifier.clickable { onIdentity(event.author) }, style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(R.string.notifications_like_own), Modifier.clickable { onPost(target) }
                            .padding(vertical = 8.dp))
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    NotificationPermissionPrompt()
}
