package nl.davidengelen.redsquare.feed

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.FeedPost
import nl.davidengelen.redsquare.saito.RedSquareVideo
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.YouTubeVideo

@Composable
internal fun PostVideo(post: FeedPost, backend: SaitoBackend) {
    val video = remember(post.text, post.link) { RedSquareVideo.youtube(post.text, post.link) } ?: return
    var open by remember(post.id, video, backend) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.video_provider), Modifier.padding(vertical = 12.dp))
            TextButton(onClick = { open = true }) { Text(stringResource(R.string.video_watch)) }
        }
    }
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.video_provider), Modifier.padding(vertical = 12.dp))
                    TextButton(onClick = { open = false }) { Text(stringResource(R.string.video_close)) }
                }
                VideoPlayer(video, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun VideoPlayer(video: YouTubeVideo, modifier: Modifier) {
    val context = LocalContext.current
    var attempt by remember(video) { mutableIntStateOf(0) }
    var status by remember(video, attempt) { mutableStateOf(VideoStatus.LOADING) }
    var externalUnavailable by remember(video) { mutableStateOf(false) }
    Column(modifier) {
        Text(stringResource(status.message))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            when {
                maxWidth < 200.dp || maxHeight < 200.dp -> Text(stringResource(R.string.video_small_window))
                status.failed -> TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry)) }
                else -> EmbeddedVideo(video, attempt, Modifier.fillMaxSize()) { status = it }
            }
        }
        TextButton(onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                externalUnavailable = false
            } catch (_: ActivityNotFoundException) { externalUnavailable = true }
        }) { Text(stringResource(R.string.video_open_external)) }
        if (externalUnavailable) Text(stringResource(R.string.video_external_unavailable))
    }
}

@Composable
private fun EmbeddedVideo(video: YouTubeVideo, attempt: Int, modifier: Modifier, report: (VideoStatus) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val fixture = LocalVideoFixture.current
    val currentReport by rememberUpdatedState(report)
    var status by remember(video, attempt, fixture) { mutableStateOf(VideoStatus.LOADING) }
    val browser = remember(video, attempt, fixture) {
        VideoBrowser(context, video, fixture) { status = it; currentReport(it) }
    }
    DisposableEffect(browser, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> browser.pause()
                Lifecycle.Event.ON_RESUME -> browser.resume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); browser.close() }
    }
    LaunchedEffect(browser, lifecycle) {
        currentReport(VideoStatus.LOADING)
        var loadingTime = 0L
        var previous = android.os.SystemClock.elapsedRealtime()
        while (!browser.closed) {
            delay(500)
            val now = android.os.SystemClock.elapsedRealtime()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                if (status == VideoStatus.LOADING) loadingTime += now - previous
                browser.readStatus()?.let { status = it; currentReport(it) }
                if (status == VideoStatus.LOADING && loadingTime >= 20_000) {
                    status = VideoStatus.NETWORK_FAILURE
                    currentReport(status)
                }
            }
            previous = now
        }
    }
    AndroidView(factory = { browser.view }, modifier = modifier)
}
