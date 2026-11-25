package nl.davidengelen.redsquare.spaces

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.view.View
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Read only by the owned, opt-in profileable fixture build. */
internal object SpacesScaleMeasurement {
    private data class Reaction(val sequence: Int, val message: String, val emoji: String, val selected: Boolean, val start: Long)
    private val sequence = AtomicInteger()
    private val pending = AtomicReference<Reaction?>()
    private const val tag = "SpacesScaleProof"

    fun reactionPressed(message: SpaceClient.Message, emoji: String) {
        val wasSelected = message.reactions.any { it.emoji == emoji && it.selected }
        val event = Reaction(sequence.incrementAndGet(), message.id, emoji, !wasSelected, SystemClock.elapsedRealtimeNanos())
        if (!pending.compareAndSet(null, event)) {
            Log.i(tag, "reaction-overlap ${event.sequence}")
            return
        }
        Trace.beginAsyncSection("Spaces fixture reaction", event.sequence)
        Log.i(tag, "reaction-tap ${event.sequence} ${event.start}")
    }

    fun reactionDrawn(message: SpaceClient.Message, view: View) {
        val event = pending.get() ?: return
        if (message.id != event.message || !view.isHardwareAccelerated || !view.viewTreeObserver.isAlive ||
            message.reactions.any { it.emoji == event.emoji && it.selected } != event.selected) return
        if (!pending.compareAndSet(event, null)) return
        view.viewTreeObserver.registerFrameCommitCallback {
            val drawnAt = SystemClock.elapsedRealtimeNanos()
            Trace.endAsyncSection("Spaces fixture reaction", event.sequence)
            Log.i(tag, "reaction-frame ${event.sequence} ${event.start} $drawnAt")
        }
    }

    fun visible(visible: Boolean, ticker: Boolean) {
        Log.i(tag, "foreground ${SystemClock.elapsedRealtimeNanos()} $visible $ticker")
    }
}
