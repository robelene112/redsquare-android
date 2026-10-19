package nl.davidengelen.redsquare

import android.app.Application
import nl.davidengelen.redsquare.notifications.PushTransport

class RedSquareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        nl.davidengelen.redsquare.ui.installSystemMotion()
        // Also runs before a cold messaging-service entry, without creating a wallet.
        PushTransport.initialize(this)
    }
}
