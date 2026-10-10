package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.RedSquareActionKind
import java.util.Base64

/** Counts are unsigned observations supplied with a verified post, never local increments. */
private fun servedCount(post: FeedPost, field: String): Long? = try {
    val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    val outer = gson.fromJson(post.envelope, JsonObject::class.java)
    val encoded = outer.get("opt").asString
    require(encoded.length <= 128 * 1024)
    val options = gson.fromJson(Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8), JsonObject::class.java)
    val value = options.get(field).asJsonPrimitive
    require(value.isNumber)
    value.asBigDecimal.longValueExact().also { require(it >= 0) }
} catch (_: Exception) { null }

@Composable
internal fun SocialActions(post: FeedPost, state: SocialState?) {
    RedSquareActionKind.entries.forEach { kind ->
        SocialAction(post, state, kind)
    }
}

@Composable
private fun SocialAction(post: FeedPost, state: SocialState?, kind: RedSquareActionKind) {
    LaunchedEffect(state, post.id, kind) { state?.load(post, kind) }
    val status = state?.actions?.get(PostActionKey(post.id, kind))
    val observed = state?.observedPosts?.get(post.id) ?: post
    val count = remember(observed.envelope, kind) {
        servedCount(observed, if (kind == RedSquareActionKind.LIKE) "num_likes" else "num_retweets")
    }
    val labels = if (kind == RedSquareActionKind.LIKE)
        listOf(R.string.social_like, R.string.social_liked, R.string.social_retry_like, R.string.social_like_count)
    else listOf(R.string.social_repost, R.string.social_reposted, R.string.social_retry_repost, R.string.social_repost_count)
    Row {
        TextButton(enabled = status?.ready == true && !status.busy && !status.available,
            onClick = { state?.send(post, kind) }) {
            Text(stringResource(when {
                status?.available == true -> labels[1]
                status?.candidate != null -> labels[2]
                else -> labels[0]
            }))
        }
        count?.let { Text(stringResource(labels[3], it)) }
    }
    if (status?.candidate != null && !status.available) Text(stringResource(R.string.social_pending))
    if (status?.countsUnavailable == true) Text(stringResource(R.string.social_counts_unavailable))
    if (status?.failed == true) {
        Text(stringResource(R.string.social_error))
        if (!status.ready) TextButton(onClick = { state?.retryLoad(post, kind) }) { Text(stringResource(R.string.retry)) }
    }
}
