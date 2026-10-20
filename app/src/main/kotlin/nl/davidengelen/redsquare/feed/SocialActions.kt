package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.InlineStatus
import nl.davidengelen.redsquare.ui.StatusTone

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
internal fun SocialActions(post: FeedPost, state: SocialState?, author: String, onQuote: ((FeedPost) -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RedSquareActionKind.entries.forEach { kind -> SocialAction(post, state, kind, author) }
            if (onQuote != null) SocialControl(stringResource(R.string.social_quote), R.drawable.ic_quote,
                stringResource(R.string.post_quote_for, author), enabled = state != null, onClick = { onQuote(post) })
        }
        RedSquareActionKind.entries.forEach { kind -> SocialActionStatus(post, state, kind, author) }
        val feedback = state?.actions?.get(PostActionKey(post.id, RedSquareActionKind.LIKE))?.rateFeedback ?: 0L
        InlineStatus(if (feedback != 0L) stringResource(R.string.social_like_rate) else null, StatusTone.WARNING)
    }
}

@Composable
private fun SocialAction(post: FeedPost, state: SocialState?, kind: RedSquareActionKind, author: String) {
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
    val label = stringResource(when {
        status?.available == true -> labels[1]
        status?.candidate != null -> labels[2]
        else -> labels[0]
    })
    val countLabel = count?.let { pluralStringResource(labels[3], if (it == 1L) 1 else 2, it) }
    val description = stringResource(if (kind == RedSquareActionKind.LIKE)
        R.string.post_like_for else R.string.post_repost_for, author)
    val retrying = status?.candidate != null && !status.available
    SocialControl(label, if (kind == RedSquareActionKind.LIKE) R.drawable.ic_like else R.drawable.ic_repost,
        if (retrying) stringResource(R.string.post_retry_action, description) else description,
        countLabel, enabled, status?.available == true,
        listOfNotNull(if (status?.available == true) label else null, likeAgain).joinToString(". ").ifEmpty { null }) {
        state?.send(post, kind, publishing::beforePublishing)
    }
}

/** Stable full-width feedback cannot push one action away from its associated count. */
@Composable
private fun SocialActionStatus(post: FeedPost, state: SocialState?, kind: RedSquareActionKind, author: String) {
    val status = state?.actions?.get(PostActionKey(post.id, kind))
    val action = stringResource(if (kind == RedSquareActionKind.LIKE) R.string.social_like else R.string.social_repost)
    val blocked = status?.blockedBy
    val pending = status?.candidate != null && !status.available
    val summary = when {
        blocked != null -> stringResource(R.string.social_pending_elsewhere, blocked.domain)
        status?.busy == true -> stringResource(R.string.social_in_progress, action)
        pending -> stringResource(R.string.social_action_pending, action)
        status?.countsUnavailable == true -> stringResource(R.string.social_action_counts_unavailable, action)
        status?.failed == true -> stringResource(R.string.social_action_unavailable, action)
        else -> null
    }
    val details = buildList {
        add(stringResource(if (kind == RedSquareActionKind.LIKE) R.string.post_like_for else R.string.post_repost_for, author))
        if (blocked != null) add(stringResource(R.string.social_like_other_node, blocked.domain))
        if (pending) add(stringResource(R.string.social_pending))
        if (status?.countsUnavailable == true) add(stringResource(R.string.social_counts_unavailable))
        if (status?.failed == true) add(stringResource(R.string.social_error))
    }
    val retryLoad = status?.failed == true && !status.ready
    InlineStatus(summary, when {
        status?.busy == true -> StatusTone.WORKING
        status?.failed == true && !pending -> StatusTone.ERROR
        else -> StatusTone.WARNING
    }, details = details, actionLabel = if (retryLoad) stringResource(R.string.retry) else null,
        onAction = { state?.retryLoad(post, kind) })
}

/** The count shares its action's visual target and accessibility state. Missing counts stay absent. */
@Composable
private fun SocialControl(label: String, icon: Int, description: String, count: String? = null,
    enabled: Boolean, available: Boolean = false, stateHint: String? = null, onClick: () -> Unit) {
    val countStyle = MaterialTheme.typography.labelSmall
    val countHeight = with(LocalDensity.current) { countStyle.lineHeight.toDp() }
    val color = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    TextButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
            contentDescription = description
            listOfNotNull(stateHint, count).joinToString(". ").takeIf { it.isNotEmpty() }?.let { stateDescription = it }
        }, contentPadding = PaddingValues(horizontal = AppSpacing.small, vertical = 4.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color,
            disabledContentColor = if (available) color else color.copy(alpha = 0.38f))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
            Box(Modifier.heightIn(min = countHeight)) {
                if (count != null) Text(count, style = countStyle)
            }
        }
    }
}
