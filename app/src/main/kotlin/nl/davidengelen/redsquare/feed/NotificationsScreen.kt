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

private data class NotificationRow(val id: String, val author: String, val createdAt: Long,
    val post: FeedPost, val caption: Int)

@Composable
internal fun NotificationsScreen(wallet: SaitoWallet?, endpoint: String, knownPosts: List<FeedPost>,
    modifier: Modifier, onBack: () -> Unit, onPost: (FeedPost) -> Unit, onIdentity: (String) -> Unit) {
    val personal = LocalPersonalControls.current
    var events by remember(wallet?.publicKey, endpoint) { mutableStateOf<List<ProfileLike>>(emptyList()) }
    var replies by remember(wallet?.publicKey, endpoint) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var mentions by remember(wallet?.publicKey, endpoint) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var ignored by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    LaunchedEffect(wallet, endpoint, refresh) {
        if (wallet == null) return@LaunchedEffect
        loading = true
        try {
            val (likePage, replyPage, mentionPage) = withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    suspend fun read(kind: String): String {
                        val criteria = JsonObject().apply {
                            addProperty("request", "load"); addProperty("field1", kind)
                            addProperty("field3", wallet.publicKey); addProperty("flagged_ne", 1)
                            addProperty("limit", 100); addProperty("created_earlier_than", System.currentTimeMillis() + 1)
                        }
                        val request = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                        return peer.request(request.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8)
                    }
                    val likes = RedSquareLikes.readIncoming(read("RedSquareLike"), wallet.publicKey)
                    val posts = read("RedSquare")
                    Triple(likes, RedSquareFeed.readReplies(posts), RedSquareFeed.readMentions(posts, wallet.publicKey))
                }
            }
            events = likePage.likes
            replies = replyPage.posts.filter { it.author != wallet.publicKey && it.parentId.isNotEmpty() }
            mentions = mentionPage.posts
            ignored = likePage.ignored + replyPage.ignored
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
        val rows = (events.mapNotNull { event ->
            knownPosts.firstOrNull { it.id == event.target && it.author == wallet?.publicKey }?.let { target ->
                NotificationRow(event.id, event.author, event.createdAt, target, R.string.notifications_like_own)
            }
        } + replies.filter { reply ->
            knownPosts.any { it.id == reply.parentId && it.author == wallet?.publicKey }
        }.map { reply -> NotificationRow(reply.id, reply.author, reply.createdAt, reply, R.string.notifications_reply_own) } +
            mentions.map { mention -> NotificationRow(mention.id, mention.author, mention.createdAt, mention, R.string.notifications_mention) })
            .distinctBy { it.id }
        LazyColumn(Modifier.weight(1f)) {
            items(rows.filter { personal?.hidesPosts(it.author) == false }.sortedByDescending { it.createdAt }, key = { it.id }) { event ->
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text(registeredIdentity(event.author).name, Modifier.clickable { onIdentity(event.author) })
                    Text(event.author, Modifier.clickable { onIdentity(event.author) }, style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(event.caption), Modifier.clickable { onPost(event.post) }
                        .padding(vertical = 8.dp))
                    HorizontalDivider()
                }
            }
        }
    }
    NotificationPermissionPrompt()
}
