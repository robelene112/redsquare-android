package nl.davidengelen.redsquare.store

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/** Platform prompt adapter. Each operation retains its own identity, lifetime and send authority. */
internal fun requestStoreDeviceAuthentication(activity: Activity, title: Int, signal: CancellationSignal,
    onAuthorized: () -> Unit, onError: () -> Unit) {
    val builder = BiometricPrompt.Builder(activity).setTitle(activity.getString(title))
    if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
    else { @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true) }
    builder.build().authenticate(signal, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onAuthorized()
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError()
    })
}
