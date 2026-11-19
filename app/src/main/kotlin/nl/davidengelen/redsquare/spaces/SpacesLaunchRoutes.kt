package nl.davidengelen.redsquare.spaces

import android.net.Uri

/** Routing only. Cryptographic invitation validation happens off main and
 * following a link never performs membership or key activation. */
internal object SpacesLaunchRoutes {
    fun accepts(uri: Uri?): Boolean = uri != null && uri.toString().length <= 8192 &&
        uri.scheme == "saito" && uri.host == "spaces" && uri.path == "/join" &&
        uri.query == null && uri.userInfo == null && uri.port == -1 && !uri.fragment.isNullOrEmpty()
}
