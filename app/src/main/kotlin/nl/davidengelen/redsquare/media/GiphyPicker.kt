package nl.davidengelen.redsquare.media

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import nl.davidengelen.redsquare.R

/** Search lives only in the visible picker; the parent keeps authored work. */
@Composable
internal fun GiphyPicker(onClose: () -> Unit, onSelect: (GiphyItem) -> Unit,
    selectionEnabled: Boolean, selectionError: Boolean) {
    val configuration = LocalGiphyConfiguration.current
    val provider = remember(configuration) { GiphyProvider(configuration) }
    var query by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var limited by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<GiphyItem>>(emptyList()) }
    val tooLong = query.codePointCount(0, query.length) > 50
    LaunchedEffect(provider, query, attempt) {
        results = emptyList()
        failed = false
        limited = false
        loading = false
        if (!configuration.configured || tooLong) return@LaunchedEffect
        loading = true
        try {
            if (query.isNotBlank()) delay(400)
            results = provider.search(query)
            loading = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            failed = true
            limited = (failure as? GiphyUnavailable)?.rateLimited == true
            loading = false
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding().padding(12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.giphy_title), Modifier.weight(1f).padding(vertical = 12.dp),
                        style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text(stringResource(R.string.giphy_close)) }
                }
                if (configuration.configured) OutlinedTextField(value = query, onValueChange = { query = it },
                    label = { Text(stringResource(R.string.giphy_query)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text(stringResource(R.string.giphy_brand), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                if (selectionError) Text(stringResource(R.string.image_prepare_failed))
                when {
                    !configuration.configured -> Text(stringResource(R.string.giphy_not_available))
                    tooLong -> Text(stringResource(R.string.giphy_query_limit))
                    loading -> Text(stringResource(R.string.giphy_loading))
                    failed -> {
                        Text(stringResource(if (limited) R.string.giphy_busy else R.string.giphy_failed))
                        TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry)) }
                    }
                    results.isEmpty() -> Text(stringResource(R.string.giphy_empty))
                    else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(results, key = { index, item -> "$index:${item.id}" }) { _, item ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(item.title)
                                    GiphyImage(item.thumbnailUrl, stringResource(R.string.giphy_preview, item.title),
                                        Modifier.fillMaxWidth().height(160.dp))
                                    GiphyCredits(item.attribution)
                                    TextButton(enabled = selectionEnabled, onClick = { onSelect(item) }) {
                                        Text(stringResource(R.string.giphy_choose))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GiphyCredits(item: GiphyAttribution, onNavigate: (() -> Unit) -> Unit = { it() }) {
    val context = LocalContext.current
    var unavailable by remember(item) { mutableStateOf(false) }
    fun open(url: String) = onNavigate {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); unavailable = false }
        catch (_: ActivityNotFoundException) { unavailable = true }
    }
    item.creator?.let { creator ->
        val label = stringResource(R.string.giphy_creator, creator)
        if (item.creatorUrl != null) TextButton(onClick = { open(item.creatorUrl) }) { Text(label) }
        else Text(label, style = MaterialTheme.typography.bodySmall)
    }
    item.sourceUrl?.let { source ->
        TextButton(onClick = { open(source) }) { Text(stringResource(R.string.giphy_source)) }
    }
    if (unavailable) Text(stringResource(R.string.giphy_source_unavailable))
}
