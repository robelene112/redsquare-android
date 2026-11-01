package nl.davidengelen.redsquare.feed

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
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

/** Parses unsigned Like/repost counts supplied with a verified post. */
internal fun servedCount(post: FeedPost, field: String): Long? = try {
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
        RedSquareActionKind.entries.filter { it != RedSquareActionKind.LIKE }
            .forEach { kind -> SocialActionStatus(post, state, kind, author) }
    }
}

@Composable
private fun SocialAction(post: FeedPost, state: SocialState?, kind: RedSquareActionKind, author: String) {
    val publishing = LocalPublishingAgreement.current
    val globallyBusy = state?.busy(post, kind) == true
    LaunchedEffect(state, post.id, kind, globallyBusy) { state?.load(post, kind, refresh = !globallyBusy) }
    val status = state?.actions?.get(PostActionKey(post.id, kind))
    val feedback = status?.tapFeedback ?: 0L
    val heartScale = remember(post.id, kind) { Animatable(1f) }
    var heartFilled by remember(post.id, kind) { mutableStateOf(false) }
    LaunchedEffect(feedback) {
        if (feedback != 0L && kind == RedSquareActionKind.LIKE) {
            heartFilled = true
            heartScale.animateTo(1.38f, animationSpec = spring(dampingRatio = 0.42f, stiffness = 700f))
            heartScale.animateTo(1f, animationSpec = spring(dampingRatio = 0.58f, stiffness = 430f))
            heartFilled = false
        }
    }
    val observed = state?.observedPosts?.get(post.id) ?: post
    val served = remember(observed.envelope, kind) {
        servedCount(observed, if (kind == RedSquareActionKind.LIKE) "num_likes" else "num_retweets")
    }
    val like = kind == RedSquareActionKind.LIKE
    val count = if (like) state?.displayLikeCount(post.id, served) ?: served else served
    val enabled = if (like) state != null else status?.ready == true && !status.busy && !globallyBusy &&
        !status.available
    val label = if (like) "" else stringResource(when {
        status?.available == true -> R.string.social_reposted
        status?.candidate != null -> R.string.social_retry_repost
        else -> R.string.social_repost
    })
    val countLabel = count?.let {
        pluralStringResource(if (like) R.plurals.social_like_count else R.plurals.social_repost_count,
            if (it == 1L) 1 else 2, it)
    }
    val description = stringResource(if (kind == RedSquareActionKind.LIKE)
        R.string.post_like_for else R.string.post_repost_for, author)
    val retrying = !like && status?.candidate != null && !status.available
    SocialControl(label, if (like && heartFilled) R.drawable.ic_like_filled else
        if (like) R.drawable.ic_like else R.drawable.ic_repost,
        if (retrying) stringResource(R.string.post_retry_action, description) else description,
        countLabel, enabled, available = !like && status?.available == true,
        stateHint = if (like) null else status?.let { if (it.available) label else null },
        iconScale = heartScale.value, feedbackActive = like && heartFilled) {
        state?.send(post, kind, publishing::beforePublishing)
    }
}

/** Stable full-width feedback cannot push one action away from its associated count. */
@Composable
private fun SocialActionStatus(post: FeedPost, state: SocialState?, kind: RedSquareActionKind, author: String) {
    val status = state?.actions?.get(PostActionKey(post.id, kind))
    val action = stringResource(R.string.social_repost)
    val pending = status?.candidate != null && !status.available
    val summary = when {
        status?.busy == true -> stringResource(R.string.social_in_progress, action)
        pending -> stringResource(R.string.social_action_pending, action)
        status?.countsUnavailable == true -> stringResource(R.string.social_action_counts_unavailable, action)
        status?.failed == true -> stringResource(R.string.social_action_unavailable, action)
        else -> null
    }
    val details = buildList {
        add(stringResource(R.string.post_repost_for, author))
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
@OptIn(ExperimentalAnimationApi::class)
private fun SocialControl(label: String, icon: Int, description: String, count: String? = null,
    enabled: Boolean, available: Boolean = false, stateHint: String? = null, iconScale: Float = 1f,
    feedbackActive: Boolean = false,
    onClick: () -> Unit) {
    val countStyle = MaterialTheme.typography.labelSmall
    val countHeight = with(LocalDensity.current) { countStyle.lineHeight.toDp() }
    val color = if (available || feedbackActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    TextButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
            contentDescription = description
            listOfNotNull(stateHint, count).joinToString(". ").takeIf { it.isNotEmpty() }?.let { stateDescription = it }
        }, contentPadding = PaddingValues(horizontal = AppSpacing.small, vertical = 4.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color,
            disabledContentColor = if (available) color else color.copy(alpha = 0.38f))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(icon), contentDescription = null, tint = color,
                    modifier = Modifier.size(20.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium)
            }
            Box(Modifier.heightIn(min = countHeight)) {
                if (count != null) AnimatedContent(targetState = count,
                    transitionSpec = {
                        val duration = AppSpacing.transitionMillis / 2
                        (slideInVertically(animationSpec = tween(duration)) { it / 2 } +
                            fadeIn(animationSpec = tween(duration))) togetherWith
                            (slideOutVertically(animationSpec = tween(duration)) { -it / 2 } +
                                fadeOut(animationSpec = tween(duration)))
                    }, label = "socialCount") { animatedCount ->
                    Text(animatedCount, style = countStyle)
                }
            }
        }
    }
}
