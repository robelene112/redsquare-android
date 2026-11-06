package nl.davidengelen.redsquare.ui

import nl.davidengelen.redsquare.BuildConfig

/** Optional debug observation of real rendering. It neither drives state nor changes the clock. */
internal object RevealDrawObservation {
    fun interface Listener {
        fun observe(event: String, targetType: String, targetIdentity: Int, opacity: Float,
            motionScale: Float, monotonicNanos: Long)
    }

    @Volatile var listener: Listener? = null

    fun record(event: String, target: Any?, opacity: Float, motionScale: Float) {
        if (!BuildConfig.DEBUG) return
        val current = listener ?: return
        try {
            current.observe(event, target?.javaClass?.simpleName.orEmpty(), System.identityHashCode(target),
                opacity, motionScale, System.nanoTime())
        } catch (_: Throwable) {
            // An optional diagnostic must never replace a rendering result or failure.
        }
    }
}
