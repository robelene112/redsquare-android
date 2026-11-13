package android.util

/** Host transport tests do not use Android logging. */
object Log { fun w(tag: String, message: String): Int = 0 }
