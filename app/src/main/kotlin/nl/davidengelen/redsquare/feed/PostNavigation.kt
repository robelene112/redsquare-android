package nl.davidengelen.redsquare.feed

import androidx.compose.runtime.staticCompositionLocalOf

/** Preserve the mounted composer before changing its wallet or backend. */
internal class PostNavigation {
    var saveCurrent: (suspend () -> Boolean)? = null
    suspend fun save(): Boolean = saveCurrent?.invoke() ?: true
}

internal val LocalPostNavigation = staticCompositionLocalOf<PostNavigation?> { null }
