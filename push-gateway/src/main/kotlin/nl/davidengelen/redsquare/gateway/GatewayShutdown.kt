package nl.davidengelen.redsquare.gateway

/** Attempt every owned cleanup stage; restore interruption after bounded waits have been attempted. */
internal fun finishCleanup(vararg actions: () -> Unit) {
    var failure: Throwable? = null
    var interrupted = Thread.interrupted()
    for (action in actions) {
        try { action() } catch (caught: Throwable) {
            if (caught is InterruptedException) interrupted = true
            if (failure == null) failure = caught else failure.addSuppressed(caught)
        }
        if (Thread.interrupted()) interrupted = true
    }
    if (interrupted) Thread.currentThread().interrupt()
    failure?.let { throw it }
}
