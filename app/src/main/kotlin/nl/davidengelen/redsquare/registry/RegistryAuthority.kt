package nl.davidengelen.redsquare.registry

import android.content.Intent
import android.os.Build
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.saito.SaitoRegistry
import nl.davidengelen.redsquare.saito.SaitoBackend
import java.net.URI

/** Synthetic authority exists only for an explicit debug emulator/loopback fixture. */
internal object RegistryAuthority {
    /** Name-directory reads never change the selected content node or registration authority. */
    fun directoryPeer(intent: Intent, endpoint: String): String? {
        if (SaitoBackend.entries.any { it.peerUrl == endpoint })
            return SaitoBackend.OFFICIAL.peerUrl.takeUnless { it == endpoint }
        if (!BuildConfig.DEBUG || Build.HARDWARE !in listOf("ranchu", "goldfish") || !loopback(endpoint)) return null
        return intent.getStringExtra("nameDirectoryPeerUrl")?.takeIf(::loopback)
    }

    fun fromIntent(intent: Intent, endpoint: String): String {
        return forPeer(endpoint, intent.getBooleanExtra("usernameFixtureRegistrar", false))
    }

    fun forPeer(endpoint: String, fixtureRegistrar: Boolean = false): String {
        if (!BuildConfig.DEBUG || Build.HARDWARE !in listOf("ranchu", "goldfish") ||
            !fixtureRegistrar) return SaitoRegistry.REGISTRAR
        return if (loopback(endpoint)) "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU" else SaitoRegistry.REGISTRAR
    }

    private fun loopback(endpoint: String): Boolean = try {
            val uri = URI(endpoint)
            uri.scheme == "ws" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
                uri.path == "/wsopen" && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null
        } catch (_: Exception) { false }
}
