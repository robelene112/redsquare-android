package nl.davidengelen.redsquare.stack

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import nl.davidengelen.redsquare.ui.ReadingHeader
import nl.davidengelen.redsquare.ui.SingleOwnerReveal
import nl.davidengelen.redsquare.ui.rememberReadingHeaderState
import nl.davidengelen.redsquare.ui.rememberReadingAnchorModifier
import nl.davidengelen.redsquare.ui.readingNavigation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.ui.AppSpacing
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.ModuleDestination
import nl.davidengelen.redsquare.ui.ModuleNavigation

private enum class StackDestination(override val label: Int, override val icon: Int) : ModuleDestination {
    DISCOVER(R.string.stack_discover, R.drawable.ic_globe),
    FOLLOWING(R.string.following_title, R.drawable.ic_bookmark),
    ARTICLES(R.string.stack_my_articles, R.drawable.ic_article);
    override val caption get() = label
}

/** Explicitly isolated presentation review. No wallet, publication, payment or archive simulation. */
@Composable
internal fun StackReviewScreen(modifier: Modifier, chrome: @Composable (Boolean) -> Unit) {
    var destination by rememberSaveable { mutableStateOf(StackDestination.DISCOVER) }
    var article by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val reading = rememberLazyListState()
    val header = rememberReadingHeaderState(enabled = true, list = if (article) reading else list)
    val listAnchor = rememberReadingAnchorModifier(list, enabled = !header.inMotion, relativeToRoot = true)
    val articleAnchor = rememberReadingAnchorModifier(reading, enabled = !header.inMotion, relativeToRoot = true)
    BackHandler(article) { article = false }
    Column(modifier.nestedScroll(header.connection).readingNavigation(header)) {
        ReadingHeader(header) { chrome(false) }
        Surface(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screen),
            shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.stack_review), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.stack_review_detail), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SingleOwnerReveal(destination to article, Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                if (article) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        FrameIconButton(R.drawable.ic_back, stringResource(R.string.browse_back)) { article = false }
                        Text(stringResource(R.string.stack_article_preview), style = MaterialTheme.typography.titleLarge)
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().then(articleAnchor), state = reading,
                        contentPadding = PaddingValues(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        item { Text(stringResource(R.string.stack_sample_title), style = MaterialTheme.typography.headlineMedium) }
                        item { NetworkIllustration() }
                        item { Text(stringResource(R.string.stack_sample_article), style = MaterialTheme.typography.bodyLarge) }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().then(listAnchor), state = list,
                        contentPadding = PaddingValues(AppSpacing.screen), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        item { Text(stringResource(destination.label), style = MaterialTheme.typography.headlineMedium) }
                        item { Text(stringResource(when (destination) {
                            StackDestination.DISCOVER -> R.string.stack_discover_intro
                            StackDestination.FOLLOWING -> R.string.stack_following_intro
                            StackDestination.ARTICLES -> R.string.stack_articles_intro
                        }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        item {
                            Card(onClick = { article = true }, shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                NetworkIllustration()
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.stack_sample_title), style = MaterialTheme.typography.titleLarge)
                                    Text(stringResource(R.string.stack_sample_summary), style = MaterialTheme.typography.bodyMedium)
                                    Text(stringResource(R.string.stack_sample_metadata), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        ModuleNavigation(StackDestination.entries, destination, enabled = true) { destination = it; article = false }
    }
}

@Composable
private fun NetworkIllustration() {
    val line = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.primaryContainer
    Canvas(Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(16.dp))) {
        drawRect(background)
        val points = listOf(.10f to .72f, .31f to .27f, .50f to .61f, .75f to .30f, .90f to .74f)
            .map { (x, y) -> Offset(size.width * x, size.height * y) }
        points.zipWithNext().forEach { (a, b) -> drawLine(line.copy(alpha = .4f), a, b, 2.dp.toPx()) }
        drawLine(line.copy(alpha = .25f), points.first(), points[3], 1.dp.toPx())
        points.forEach { point ->
            drawCircle(line.copy(alpha = .08f), 24.dp.toPx(), point)
            drawCircle(line.copy(alpha = .7f), 5.dp.toPx(), point)
        }
    }
}
