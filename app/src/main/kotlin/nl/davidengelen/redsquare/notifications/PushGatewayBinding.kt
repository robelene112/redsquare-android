package nl.davidengelen.redsquare.notifications

import android.content.Context

/** Device binding adapter, supplied through app configuration rather than push payloads. */
internal object PushGatewayBinding {
    suspend fun reconcile(context: Context, fid: String?, origin: String, localEndpoint: String? = null) = Unit
}
