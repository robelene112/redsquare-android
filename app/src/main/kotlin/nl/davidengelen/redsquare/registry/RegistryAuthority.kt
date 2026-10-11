package nl.davidengelen.redsquare.registry

import android.content.Intent
import android.os.Build
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.saito.SaitoRegistry
import java.net.URI

/** Synthetic authority exists only for an explicit debug emulator/loopback fixture. */
internal object RegistryAuthority {
    fun fromIntent(intent: Intent, endpoint: String): String {
        if (!BuildConfig.DEBUG || Build.HARDWARE !in listOf("ranchu", "goldfish") ||
            !intent.getBooleanExtra("usernameFixtureRegistrar", false)) return SaitoRegistry.REGISTRAR
        val fixture = try {
            val uri = URI(endpoint)
            uri.scheme == "ws" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
                uri.path == "/wsopen" && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null
        } catch (_: Exception) { false }
        return if (fixture) "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU" else SaitoRegistry.REGISTRAR
    }
}
