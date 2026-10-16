package nl.davidengelen.redsquare.feed

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

/** Preserve the mounted composer before changing its wallet or backend. */
internal class PostNavigation {
    var saveCurrent by mutableStateOf<(suspend () -> Boolean)?>(null)
    var guardedCurrent: (suspend (suspend () -> Boolean) -> Boolean)? = null
    var transitioning by mutableStateOf(false)
        private set
    suspend fun save(): Boolean = saveCurrent?.invoke() ?: true

    /** Legacy forms retain their screen until they provide a guard spanning save and commit. */
    suspend fun navigate(commit: suspend () -> Boolean): Boolean {
        if (transitioning) return false
        transitioning = true
        try {
            return if (saveCurrent == null) commit() else guardedCurrent?.invoke(commit) ?: false
        } finally { transitioning = false }
    }
}

internal val LocalPostNavigation = staticCompositionLocalOf<PostNavigation?> { null }
