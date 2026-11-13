package nl.davidengelen.redsquare

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

internal fun ComposeTestRule.reviewVaultFile(scene: VaultReadingScene, name: String, bytes: ByteArray) {
    val source = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/$name")
    scene.context.contentResolver.call(source, "prepare", name, Bundle().apply { putByteArray("bytes", bytes) })
    Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(android.app.Instrumentation.ActivityResult(
        Activity.RESULT_OK, Intent().setData(source).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
    openNativeVault()
    waitUntil(5_000) { onNodeWithText("Upload file").isDisplayed() }
    onNodeWithText("Upload file").performClick()
    waitUntil(15_000) { onNodeWithText("Review upload").isDisplayed() }
    onNodeWithText(name).assertIsDisplayed()
    onNodeWithText("1 SAITO").assertIsDisplayed()
    onNodeWithText("Create key and upload").performScrollTo().assertIsEnabled().performClick()
}

internal fun withVaultDeviceCredential(block: (UiDevice) -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    check(!context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
    val device = UiDevice.getInstance(instrumentation)
    device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
    try { block(device) }
    finally { device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0") }
}
