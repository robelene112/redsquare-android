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
import java.util.Base64

/** Counts are unsigned observations supplied with a verified post, never local increments. */
private fun servedLikeCount(post: FeedPost): Long? = try {
    val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    val outer = gson.fromJson(post.envelope, JsonObject::class.java)
    val encoded = outer.get("opt").asString
    require(encoded.length <= 128 * 1024)
    val options = gson.fromJson(Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8), JsonObject::class.java)
    val value = options.get("num_likes").asJsonPrimitive
    require(value.isNumber)
    value.asBigDecimal.longValueExact().also { require(it >= 0) }
} catch (_: Exception) { null }

@Composable
internal fun SocialActions(post: FeedPost, state: SocialState?) {
    LaunchedEffect(state, post.id) { state?.load(post) }
    val status = state?.likes?.get(post.id)
    val observed = state?.observedPosts?.get(post.id) ?: post
    val count = remember(observed.envelope) { servedLikeCount(observed) }
    Row {
        TextButton(enabled = status?.ready == true && !status.busy && !status.available,
            onClick = { state?.like(post) }) {
            Text(stringResource(when {
                status?.available == true -> R.string.social_liked
                status?.candidate != null -> R.string.social_retry_like
                else -> R.string.social_like
            }))
        }
        count?.let { Text(stringResource(R.string.social_like_count, it)) }
    }
    if (status?.candidate != null && !status.available) Text(stringResource(R.string.social_pending))
    if (status?.failed == true) {
        Text(stringResource(R.string.social_error))
        if (!status.ready) TextButton(onClick = { state?.retryLoad(post) }) { Text(stringResource(R.string.retry)) }
    }
}
