package nl.davidengelen.redsquare.feed

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.SaitoBackend

internal data class ActivityPreviews(val posts: Map<String, FeedPost>, val loading: Boolean, val unreadable: Boolean)
private data class PreviewRequest(val posts: List<FeedPost>, val revision: Long)
private data class ResolvedPreviews(val request: PreviewRequest, val applied: AndroidPostRevisions.Applied)

/** Withhold old preview content immediately when learned author proofs change. */
@Composable
internal fun rememberActivityPreviews(posts: List<FeedPost>, backend: SaitoBackend): ActivityPreviews {
    val context = LocalContext.current.applicationContext
    val revision by AndroidPostRevisions.changes.collectAsState()
    val revisions = remember(context, backend) { AndroidPostRevisions(context, backend) }
    val request = remember(posts, revision) { PreviewRequest(posts, revision) }
    var resolved by remember(backend) { mutableStateOf<ResolvedPreviews?>(null) }
    LaunchedEffect(request, revisions) {
        val applied = withContext(Dispatchers.IO) {
            try {
                posts.forEach(revisions::observe)
                revisions.apply(posts)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { AndroidPostRevisions.Applied(emptyList(), unreadable = true) }
        }
        resolved = ResolvedPreviews(request, applied)
    }
    val current = resolved?.takeIf { it.request == request }?.applied
    return ActivityPreviews(current?.posts?.associateBy { it.id }.orEmpty(), current == null, current?.unreadable == true)
}
