package nl.davidengelen.redsquare.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.UriHandler
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.StackLink

/** The URL source is temporary reading context; the saved node and wallet scope stay explicit. */
internal data class StackLinkEntry(val target: StackLink, val owner: String, val savedBackend: SaitoBackend) : java.io.Serializable

/** Deliberate website actions bypass native reading, including publication and Open original. */
internal val LocalExternalUriHandler = staticCompositionLocalOf<UriHandler?> { null }
