package nl.davidengelen.redsquare

import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class VaultUploadLargePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun theFullFourMiBFileReachesVaultUnchangedAfterNativeReviewAndAuthentication() = withVaultDeviceCredential { device ->
        withVaultReadingFixture("large-upload", captureFolder = "vault-upload-large-evidence", retainTransfers = true, minting = true) { scene ->
            val name = "vault-four-mib.bin"
            val source = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/$name")
            scene.context.contentResolver.call(source, "prepare", name, Bundle().apply { putByteArray("bytes", byteArrayOf()) })
            val expected = ByteArray(4 * 1024 * 1024) { ((it * 31 + 17) and 255).toByte() }
            // Stream through the public Android provider; do not put4MiB into a Binder Bundle.
            requireNotNull(scene.context.contentResolver.openOutputStream(source, "wt")).use { output ->
                var offset = 0
                while (offset < expected.size) {
                    val count = minOf(64 * 1024, expected.size - offset)
                    output.write(expected, offset, count); offset += count
                }
                output.flush()
            }
            ui.reviewPreparedVaultFile(scene, name, source)
            enterRealSystemPin(device)
            ui.waitUntil(60_000) { ui.onNodeWithText("Upload complete").isDisplayed() }
            assertEquals(1, scene.peer.transactions.size)
            assertEquals(1, scene.peer.vaultUploads.size)
            val received = requireNotNull(scene.mint).storedContents()
            assertArrayEquals(expected, received)
            File(scene.evidence, "native-upload-bytes.json").writeText(com.google.gson.JsonObject().apply {
                addProperty("bytes", received.size)
                addProperty("sha256", MessageDigest.getInstance("SHA-256").digest(received).joinToString("") { "%02x".format(it) })
            }.toString())
            captureNativeScreen(File(scene.evidence, "four-mib-file-accepted-owned-key.png"))
            ui.onNodeWithText("Back to files").performScrollTo().performClick()
            ui.waitUntil(15_000) { ui.onNodeWithText(name).isDisplayed() }
        }
    }
}
