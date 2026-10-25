package nl.davidengelen.redsquare.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.media.GiphyAttribution
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.ui.FrameIconButton
import nl.davidengelen.redsquare.ui.AppDialogWindow
import kotlin.math.roundToInt

/** One modal focus context with the same image pipeline as its originating attachment. */
@Composable
internal fun ImageViewer(images: List<String>, initialIndex: Int, backend: SaitoBackend,
    credits: Map<String, GiphyAttribution>, onOpenSource: (() -> Unit) -> Unit, onClose: () -> Unit) {
    var index by rememberSaveable(images, initialIndex) { mutableIntStateOf(initialIndex) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        AppDialogWindow(styleSystemBars = true)
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val landscape = maxWidth > maxHeight
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (landscape) ViewerControl(R.drawable.ic_back, R.string.image_previous, index > 0) { index-- }
                        Text(stringResource(R.string.image_position, index + 1, images.size),
                            Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
                            style = MaterialTheme.typography.titleMedium)
                        if (landscape) ViewerControl(R.drawable.ic_forward, R.string.image_next, index < images.lastIndex) { index++ }
                        ViewerControl(R.drawable.ic_close, R.string.image_close, onClick = onClose)
                    }
                    key(index) {
                        val appearance = remember { Animatable(0f) }
                        LaunchedEffect(Unit) { appearance.animateTo(1f, tween(160)) }
                        var scale by rememberSaveable { mutableFloatStateOf(1f) }
                        var offset by remember { mutableStateOf(Offset.Zero) }
                        var size by remember { mutableStateOf(IntSize.Zero) }
                        var focused by remember { mutableStateOf(false) }
                        var moveMenu by remember { mutableStateOf(false) }
                        fun move(delta: Offset) {
                            val maxX = size.width * (scale - 1f) / 2
                            val maxY = size.height * (scale - 1f) / 2
                            offset = Offset((offset.x + delta.x).coerceIn(-maxX, maxX), (offset.y + delta.y).coerceIn(-maxY, maxY))
                        }
                        fun zoom(value: Float) {
                            scale = value.coerceIn(1f, 5f)
                            if (scale == 1f) offset = Offset.Zero else move(Offset.Zero)
                        }
                        fun reset() { scale = 1f; offset = Offset.Zero }
                        val transform = rememberTransformableState { factor, pan, _ -> zoom(scale * factor); move(pan) }
                        val motion = if (transform.isTransformInProgress) snap<Float>() else tween<Float>(160)
                        val shownScale by animateFloatAsState(scale, motion, label = "Image zoom")
                        val shownX by animateFloatAsState(offset.x, motion, label = "Image horizontal position")
                        val shownY by animateFloatAsState(offset.y, motion, label = "Image vertical position")
                        val zoomIn = stringResource(R.string.image_zoom_in)
                        val zoomOut = stringResource(R.string.image_zoom_out)
                        val resetLabel = stringResource(R.string.image_zoom_reset)
                        val description = stringResource(R.string.image_fullscreen_position, index + 1, images.size)
                        val percentage = (scale * 100).roundToInt()
                        val zoomDescription = stringResource(R.string.image_zoom_state, percentage)
                        val left = stringResource(R.string.image_move_left)
                        val right = stringResource(R.string.image_move_right)
                        val up = stringResource(R.string.image_move_up)
                        val down = stringResource(R.string.image_move_down)
                        val movements = listOf(left to Offset(size.width / 4f, 0f), right to Offset(-size.width / 4f, 0f),
                            up to Offset(0f, size.height / 4f), down to Offset(0f, -size.height / 4f))
                        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()
                            .onSizeChanged { size = it; move(Offset.Zero) }
                            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier)
                            .semantics {
                                contentDescription = description
                                stateDescription = zoomDescription
                                role = Role.Image
                                progressBarRangeInfo = ProgressBarRangeInfo(scale, 1f..5f, 7)
                                setProgress { zoom(it); true }
                                customActions = buildList {
                                    if (scale < 5f) add(CustomAccessibilityAction(zoomIn) { zoom(scale + .5f); true })
                                    if (scale > 1f) {
                                        add(CustomAccessibilityAction(zoomOut) { zoom(scale - .5f); true })
                                        add(CustomAccessibilityAction(resetLabel) { reset(); true })
                                        movements.forEach { (label, delta) ->
                                            add(CustomAccessibilityAction(label) { move(delta); true })
                                        }
                                    }
                                }
                            }
                            .onFocusChanged { focused = it.isFocused }.focusable().transformable(transform)) {
                            PostImage(images[index], backend, null, Modifier.fillMaxSize().graphicsLayer {
                                alpha = appearance.value
                                scaleX = shownScale; scaleY = shownScale; translationX = shownX; translationY = shownY
                            }, androidx.compose.ui.layout.ContentScale.Fit)
                        }
                        Column(Modifier.graphicsLayer { alpha = appearance.value }.fillMaxWidth().heightIn(max = 80.dp).verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)) {
                            GifCredits(images[index], credits[images[index]], onOpenSource)
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            ViewerControl(R.drawable.ic_zoom_out, R.string.image_zoom_out, scale > 1f) { zoom(scale - .5f) }
                            Text(stringResource(R.string.image_zoom_percent, percentage),
                                Modifier.padding(horizontal = 8.dp).semantics { stateDescription = zoomDescription },
                                style = MaterialTheme.typography.labelLarge)
                            ViewerControl(R.drawable.ic_zoom_in, R.string.image_zoom_in, scale < 5f) { zoom(scale + .5f) }
                            ViewerControl(R.drawable.ic_zoom_reset, R.string.image_zoom_reset,
                                scale != 1f || offset != Offset.Zero) { reset() }
                            Box {
                                ViewerControl(R.drawable.ic_more, R.string.image_move, scale > 1f) { moveMenu = true }
                                DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                                    for ((label, delta) in movements) {
                                        DropdownMenuItem(text = { Text(label) }, onClick = { move(delta); moveMenu = false })
                                    }
                                }
                            }
                        }
                    }
                    if (!landscape && images.size > 1) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        ViewerControl(R.drawable.ic_back, R.string.image_previous, index > 0) { index-- }
                        ViewerControl(R.drawable.ic_forward, R.string.image_next, index < images.lastIndex) { index++ }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerControl(icon: Int, label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    val text = stringResource(label)
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } }, state = rememberTooltipState()) {
        FrameIconButton(icon, text, enabled, onClick)
    }
}
