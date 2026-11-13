package nl.davidengelen.redsquare.vault

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import nl.davidengelen.redsquare.R

/** A prompt is owned by the visible review; no device authorization is saved on disk. */
internal class VaultUploadAuthorization(private val activity: Activity, private val session: VaultUploadSession) {
    private var cancellation: CancellationSignal? = null
    private var activeToken: Long? = null
    private var closed = false
    fun request() {
        if (closed || activeToken != null) return
        session.requestAuthorization { record, token ->
            if (closed) { session.authorizationCancelled(token); return@requestAuthorization }
            val signal = CancellationSignal()
            cancellation = signal; activeToken = token
            try {
                val builder = BiometricPrompt.Builder(activity).setTitle(activity.getString(R.string.vault_authorize_upload))
                if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                else { @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true) }
                builder.build().authenticate(signal, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (activeToken != token) return
                        activeToken = null; cancellation = null; session.send(record, token)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (activeToken != token) return
                        activeToken = null; cancellation = null; session.authorizationCancelled(token)
                    }
                })
            } catch (_: Exception) { close() }
        }
    }
    fun close() {
        closed = true
        val token = activeToken; activeToken = null
        cancellation?.cancel(); cancellation = null
        token?.let(session::authorizationCancelled)
    }
}
