package nl.davidengelen.redsquare

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.ui.MediaResultDrawObservation

/** Read native rendering alongside the original recorder, without requesting any frames. */
internal class MediaArrivalTiming(activity: ActivityScenario<MainActivity>) : AutoCloseable {
    private val lock = Any()
    private val draws = JsonArray()
    private val frames = JsonArray()
    private var droppedDraws = 0
    private var droppedMetrics = 0
    private val thread = HandlerThread("media-arrival-timing").apply { start() }
    private lateinit var window: Window
    private var registered = false
    private val previous = MediaResultDrawObservation.listener
    private val drawListener = MediaResultDrawObservation.Listener { event, identity, monotonic, elapsed ->
        synchronized(lock) {
            if (draws.size() >= 1024) droppedDraws++ else draws.add(JsonObject().apply {
                addProperty("event", event); addProperty("targetIdentity", identity)
                addProperty("monotonicNanos", monotonic); addProperty("elapsedNanos", elapsed)
            })
        }
    }
    private val frameListener = Window.OnFrameMetricsAvailableListener { _, metric, dropped ->
        synchronized(lock) {
            droppedMetrics += dropped
            if (frames.size() >= 512) droppedMetrics++ else frames.add(JsonObject().apply {
                addProperty("monotonicNanos", System.nanoTime())
                addProperty("elapsedNanos", android.os.SystemClock.elapsedRealtimeNanos())
                addProperty("intendedVsyncNanos", metric.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP))
                addProperty("vsyncNanos", metric.getMetric(FrameMetrics.VSYNC_TIMESTAMP))
                addProperty("totalNanos", metric.getMetric(FrameMetrics.TOTAL_DURATION))
                addProperty("unknownDelayNanos", metric.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION))
                addProperty("drawNanos", metric.getMetric(FrameMetrics.DRAW_DURATION))
                addProperty("syncNanos", metric.getMetric(FrameMetrics.SYNC_DURATION))
                addProperty("commandsNanos", metric.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION))
                addProperty("firstDraw", metric.getMetric(FrameMetrics.FIRST_DRAW_FRAME))
            })
        }
    }

    init {
        try {
            activity.onActivity {
                window = it.window
                window.addOnFrameMetricsAvailableListener(frameListener, Handler(thread.looper))
                registered = true
                MediaResultDrawObservation.listener = drawListener
            }
        } catch (failure: Throwable) {
            cleanupAfterTest(failure, { close() })
            throw failure
        }
    }

    override fun close() {
        cleanupAfterTest(null, {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                if (MediaResultDrawObservation.listener === drawListener)
                    MediaResultDrawObservation.listener = previous
            }
        }, {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                if (registered) window.removeOnFrameMetricsAvailableListener(frameListener)
                registered = false
            }
        }, { thread.quitSafely() }, {
            thread.join(1_000)
            check(!thread.isAlive) { "Owned media timing observer did not stop" }
        })
    }

    fun observations(): JsonObject = synchronized(lock) {
        JsonObject().apply {
            add("contentDraws", draws.deepCopy()); add("windowFrames", frames.deepCopy())
            addProperty("droppedDraws", droppedDraws); addProperty("droppedMetrics", droppedMetrics)
            addProperty("qualification", "Diagnostic native timing only. Composition is not animation start; window commit is not guaranteed first target-containing presentation. Alpha is not measured, identity hashes can differ for equal targets, observations may drop and observer overhead can affect timing. No forced draws, alternate clock, screenshots during recording or changed motion assertions.")
        }
    }
}
