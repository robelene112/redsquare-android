package nl.davidengelen.redsquare.ui

/** Optional debug-only observations. Never changes Compose state or the Android window. */
internal object ChromeTrace {
    private val events = java.util.ArrayDeque<String>()
    @Synchronized fun record(message: String) {
        if (!nl.davidengelen.redsquare.BuildConfig.DEBUG) return
        if (events.size >= 100) events.removeFirst()
        events.addLast("${android.os.SystemClock.elapsedRealtime()} $message")
    }
    @Synchronized fun clear() { events.clear() }
    @Synchronized fun dump(): String = events.joinToString("\n")
}
