package nl.davidengelen.redsquare.spaces

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Read only by the owned, opt-in profileable fixture build. */
internal object SpacesScaleMeasurement {
    private data class Input(val message: String, val emoji: String, val uptimeNanos: Long)
    private data class Reaction(val sequence: Int, val message: String, val emoji: String, val selected: Boolean,
        val input: Long, val handler: Long)
    private val sequence = AtomicInteger()
    private val input = AtomicReference<Input?>()
    private val pending = AtomicReference<Reaction?>()
    private const val tag = "SpacesScaleProof"
    private val hotRecord = Regex("""General • Benchmark Space 001 • record (\d+)\.""")

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
        view.viewTreeObserver.registerFrameCommitCallback {
            val drawnAt = System.nanoTime()
            Trace.endAsyncSection("Spaces fixture reaction", event.sequence)
            Log.i(tag, "reaction-frame ${event.sequence} ${event.input} ${event.handler} $drawnAt")
        }
    }

    fun rowGeometry(view: View, body: String?, bounds: Rect) {
        val record = body?.let(hotRecord::matchEntire)?.groupValues?.get(1) ?: return
        if (!view.isHardwareAccelerated || !view.viewTreeObserver.isAlive) return
        view.viewTreeObserver.registerFrameCommitCallback {
            Log.i(tag, "anchor-frame ${SystemClock.elapsedRealtimeNanos()} $record ${bounds.top} ${bounds.bottom}")
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

    fun projected() {
        Log.i(tag, "projected ${SystemClock.elapsedRealtimeNanos()}")
    }
}
