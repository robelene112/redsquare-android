package nl.davidengelen.redsquare.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.RedSquareActionKind
import java.util.Base64
import nl.davidengelen.redsquare.policy.LocalPublishingAgreement

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
@OptIn(ExperimentalLayoutApi::class)
internal fun SocialActions(post: FeedPost, state: SocialState?, onQuote: ((FeedPost) -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    RedSquareActionKind.entries.forEach { kind ->
        SocialAction(post, state, kind, onQuote)
    }
    }
    val feedback = state?.actions?.get(PostActionKey(post.id, RedSquareActionKind.LIKE))?.rateFeedback ?: 0L
    AnimatedVisibility(feedback != 0L,
        enter = expandVertically(tween(180)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(180)) + fadeOut(tween(180))) {
        Text(stringResource(R.string.social_like_rate), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    }
}

@Composable
private fun SocialAction(post: FeedPost, state: SocialState?, kind: RedSquareActionKind, onQuote: ((FeedPost) -> Unit)?) {
    val publishing = LocalPublishingAgreement.current
    val globallyBusy = state?.busy(post, kind) == true
    LaunchedEffect(state, post.id, kind, globallyBusy) { state?.load(post, kind, refresh = !globallyBusy) }
    val status = state?.actions?.get(PostActionKey(post.id, kind))
    val feedback = status?.rateFeedback ?: 0L
    LaunchedEffect(state, post.id, kind, feedback) {
        if (feedback != 0L) {
            delay(2000)
            state?.clearRateFeedback(post, kind, feedback)
        }
    }
    val observed = state?.observedPosts?.get(post.id) ?: post
    val count = remember(observed.envelope, kind) {
        servedCount(observed, if (kind == RedSquareActionKind.LIKE) "num_likes" else "num_retweets")
    }
    val labels = if (kind == RedSquareActionKind.LIKE)
        listOf(R.string.social_like, R.string.social_liked, R.string.social_retry_like, R.plurals.social_like_count)
    else listOf(R.string.social_repost, R.string.social_reposted, R.string.social_retry_repost, R.plurals.social_repost_count)
    val enabled = status?.ready == true && !status.busy && !globallyBusy && status.blockedBy == null &&
        (!status.available || kind == RedSquareActionKind.LIKE)
    val likeAgain = if (enabled && kind == RedSquareActionKind.LIKE && status?.available == true)
        stringResource(R.string.social_like_again_description) else null
    Column {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        TextButton(modifier = Modifier.semantics { likeAgain?.let { stateDescription = it } },
            contentPadding = PaddingValues(horizontal = 4.dp), enabled = enabled,
            onClick = { state?.send(post, kind, publishing::beforePublishing) }) {
            Text(stringResource(when {
                status?.available == true -> labels[1]
                status?.candidate != null -> labels[2]
                else -> labels[0]
            }))
        }
        count?.let { Text(pluralStringResource(labels[3], if (it == 1L) 1 else 2, it)) }
        if (kind == RedSquareActionKind.REPOST && onQuote != null) TextButton(contentPadding = PaddingValues(horizontal = 4.dp), enabled = state != null, onClick = { onQuote(post) }) {
            Text(stringResource(R.string.social_quote))
        }
    }
    status?.blockedBy?.let { Text(stringResource(R.string.social_like_other_node, it.domain)) }
    if (status?.candidate != null && !status.available) Text(stringResource(R.string.social_pending))
    if (status?.countsUnavailable == true) Text(stringResource(R.string.social_counts_unavailable))
    if (status?.failed == true) {
        Text(stringResource(R.string.social_error))
        if (!status.ready) TextButton(onClick = { state?.retryLoad(post, kind) }) { Text(stringResource(R.string.retry)) }
    }
    }
}
