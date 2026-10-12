package nl.davidengelen.redsquare

/** Attempt every owned cleanup, preserving the original failure and all secondary failures. */
internal fun cleanupAfterTest(primary: Throwable?, vararg operations: () -> Unit) {
    var failure = primary
    for (operation in operations) {
        try { operation() }
        catch (cleanup: Throwable) {
            val earlier = failure
            if (earlier == null) failure = cleanup else if (earlier !== cleanup) earlier.addSuppressed(cleanup)
        }
    }
    if (primary == null) failure?.let { throw it }
}
