package nl.davidengelen.redsquare.spaces

import android.content.Intent
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Read only by the owned, opt-in profileable fixture build. */
internal object SpacesScaleMeasurement {
    private data class Input(val message: String, val emoji: String, val uptimeNanos: Long)
    private data class Reaction(val sequence: Int, val message: String, val emoji: String, val selected: Boolean,
        val input: Long, val handler: Long, val feedbackRegistered: AtomicBoolean = AtomicBoolean())
    private val sequence = AtomicInteger()
    private val input = AtomicReference<Input?>()
    private val pending = AtomicReference<Reaction?>()
    private const val tag = "SpacesScaleProof"
    private val hotRecord = Regex("""^General • Benchmark Space 001 • record (\d+)\.""")
    private const val anchorAction = "nl.davidengelen.redsquare.performance.OBSERVE_ANCHOR"
    private val visibleRows = mutableMapOf<Int, AnchorRow>()
    private var anchorProbe: AnchorProbe? = null
    private val watchedReply = AtomicReference<String?>()
    val scrollObserver = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            anchorProbe?.let { if (it.record == 0) it.scrollDisplacement += consumed.y.toDouble() }
            return Offset.Zero
        }
    }

    class AnchorRow(val record: Int) {
        var coordinates: LayoutCoordinates? = null
    }

    fun anchorRow(body: String?): AnchorRow? = body?.let(hotRecord::find)?.groupValues?.get(1)?.toIntOrNull()?.let(::AnchorRow)

    fun attachAnchorRow(row: AnchorRow) { visibleRows[row.record] = row }

    fun detachAnchorRow(row: AnchorRow) { if (visibleRows[row.record] === row) visibleRows.remove(row.record) }

    fun scrollViewportDrawn() { anchorProbe?.viewportDrawn() }

    private class AnchorProbe(val trial: Int, val record: Int, val view: View) : ViewTreeObserver.OnPreDrawListener {
        private class DrawnScroll(var geometry: String)
        private var sequence = 0
        private var currentDraw: DrawnScroll? = null
        private var lastDrawnScroll: String? = null
        var checkpoint = false
        var scrollDisplacement = 0.0

        private fun scrollGeometry(): String {
            val positions = visibleRows.values.mapNotNull { row ->
                row.coordinates?.takeIf { bounds(row) != null }?.let { row.record to it.positionInWindow().y }
            }.sortedBy { it.second }
            return "$scrollDisplacement " + positions.joinToString(",") { "${it.first}:${it.second}" }.ifEmpty { "missing" }
        }

        fun viewportDrawn() {
            if (record != 0) return
            // Lazy placement can finish within Compose's drawing pass after
            // ViewRoot's pre-draw callback. Sample the positions actually drawn.
            val actual = scrollGeometry()
            lastDrawnScroll = actual
            currentDraw?.geometry = actual
        }

        override fun onPreDraw(): Boolean {
            val observer = view.viewTreeObserver
            if (!view.isHardwareAccelerated || !observer.isAlive || !view.isShown) return true
            val frame = ++sequence
            // A synchronous marker is nested in the Choreographer frame that
            // measured these bounds, so the host can join its exact vsync ID.
            Trace.beginSection("SpacesAnchor $trial $frame")
            try {
                val mode = if (record == 0) "scroll-frame" else "anchor-frame"
                val scrolling = if (record == 0) DrawnScroll(lastDrawnScroll ?: scrollGeometry()) else null
                currentDraw = scrolling
                val anchored = visibleRows[record]?.let(::bounds)
                    ?.let { "${it.left} ${it.top} ${it.right} ${it.bottom}" } ?: "missing"
                val started = SystemClock.elapsedRealtimeNanos()
                val last = checkpoint
                checkpoint = false
                observer.registerFrameCommitCallback {
                    val geometry = scrolling?.geometry ?: anchored
                    Log.i(tag, "$mode $trial $frame $started ${SystemClock.elapsedRealtimeNanos()} $last $geometry")
                }
            } finally {
                Trace.endSection()
            }
            return true
        }

        private fun bounds(row: AnchorRow) = row.coordinates?.takeIf { it.isAttached }?.boundsInWindow()
            ?.takeUnless { it.isEmpty }
    }

    fun anchorIntent(intent: Intent, view: View): Boolean {
        if (intent.action != anchorAction) return false
        when (intent.getStringExtra("operation")) {
            "arm" -> {
                val trial = intent.getIntExtra("trial", -1)
                val record = intent.getIntExtra("record", -1)
                if (trial in 0..5 && record in 1..10_000 || trial == 6 && record == 0) {
                    stopAnchorProbe(view)
                    AnchorProbe(trial, record, view).also {
                        anchorProbe = it
                        view.viewTreeObserver.addOnPreDrawListener(it)
                    }
                    view.invalidate()
                }
            }
            "receipt" -> intent.getStringExtra("reply")?.takeIf { it.length == 128 }
                ?.let(watchedReply::set)
            "checkpoint" -> anchorProbe?.takeIf { it.view === view }?.let { it.checkpoint = true; view.invalidate() }
            "stop" -> stopAnchorProbe(view)
        }
        return true
    }

    fun stopAnchorProbe(view: View) {
        val probe = anchorProbe?.takeIf { it.view === view } ?: return
        view.viewTreeObserver.removeOnPreDrawListener(probe)
        watchedReply.set(null)
        anchorProbe = null
    }

    fun expectedReply(): String? = watchedReply.get()

    fun projectedReply(id: String) {
        if (watchedReply.compareAndSet(id, null)) Log.i(tag, "reply-projected ${SystemClock.elapsedRealtimeNanos()} $id")
    }

    fun inputObserver(message: String, emoji: String): Modifier = Modifier.pointerInput(message, emoji) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val released = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
            // Android stamps ACTION_UP before Compose handles the gesture.
            input.set(Input(message, emoji, released.uptimeMillis * 1_000_000L))
        }
    }

    fun reactionPressed(message: SpaceClient.Message, emoji: String) {
        val touch = input.getAndSet(null)?.takeIf { it.message == message.id && it.emoji == emoji } ?: return
        val wasSelected = message.reactions.any { it.emoji == emoji && it.selected }
        val event = Reaction(sequence.incrementAndGet(), message.id, emoji, !wasSelected, touch.uptimeNanos, System.nanoTime())
        if (!pending.compareAndSet(null, event)) {
            Log.i(tag, "reaction-overlap ${event.sequence}")
            return
        }
        Trace.beginAsyncSection("Spaces fixture reaction", event.sequence)
        Log.i(tag, "reaction-tap ${event.sequence} ${event.input} ${event.handler}")
    }

    fun reactionDrawn(message: SpaceClient.Message, view: View) {
        val event = pending.get() ?: return
        if (message.id != event.message || !view.isHardwareAccelerated || !view.viewTreeObserver.isAlive ||
            message.reactions.any { it.emoji == event.emoji && it.selected } != event.selected) return
        if (!pending.compareAndSet(event, null)) return
        registerFeedback(event, view, "selected")
        view.viewTreeObserver.registerFrameCommitCallback {
            val drawnAt = System.nanoTime()
            Trace.endAsyncSection("Spaces fixture reaction", event.sequence)
            Log.i(tag, "reaction-frame ${event.sequence} ${event.input} ${event.handler} $drawnAt")
        }
    }

    fun reactionPendingDrawn(message: String, emoji: String, view: View) {
        val event = pending.get()?.takeIf { it.message == message && it.emoji == emoji } ?: return
        registerFeedback(event, view, "saving")
    }

    private fun registerFeedback(event: Reaction, view: View, state: String) {
        if (!view.isShown || !view.isHardwareAccelerated || !view.viewTreeObserver.isAlive ||
            !event.feedbackRegistered.compareAndSet(false, true)) return
        view.viewTreeObserver.registerFrameCommitCallback {
            Log.i(tag, "reaction-feedback-frame ${event.sequence} ${event.input} ${event.handler} ${System.nanoTime()} $state")
        }
    }

    fun visible(visible: Boolean, ticker: Boolean, accountInstance: Int, activeJobs: Int, subscribers: Int) {
        Log.i(tag, "foreground ${SystemClock.elapsedRealtimeNanos()} $visible $ticker $accountInstance $activeJobs $subscribers")
    }

    fun idle(accountInstance: Int, activeJobs: Int, subscribers: Int) {
        Log.i(tag, "idle ${SystemClock.elapsedRealtimeNanos()} $accountInstance $activeJobs $subscribers")
    }

    fun received(request: String) {
        Log.i(tag, "received ${SystemClock.elapsedRealtimeNanos()} $request")
    }

    fun scrolling(moving: Boolean) {
        Log.i(tag, "user-scroll ${SystemClock.elapsedRealtimeNanos()} $moving")
    }
}
