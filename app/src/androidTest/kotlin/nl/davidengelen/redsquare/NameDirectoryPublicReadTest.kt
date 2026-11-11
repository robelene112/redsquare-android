package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.registry.RegistryLookup
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test

/** Read-only public comparison for the exact two identities reported by the owner. */
class NameDirectoryPublicReadTest {
    @Test fun approvedNodesExposeTheReportedPublicNamesWithoutRegisteringOrChangingIdentity() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val expected = mapOf(
            "oT3f7KeqeeG6VRBVvWKJpDNtigRUMYXUgtu3ZjNrNUrZ" to "digirok@saito",
            "i6LwT9rFjrsNgExzLeSghGqjHhaNwwjeDrsLr2LkTUk8" to "CryptoKen@saito",
        )
        val folder = File(context.cacheDir, "name-directory-public-evidence").apply { mkdirs() }
        val observations = JsonArray()
        try {
            for (backend in SaitoBackend.entries) {
                val names = withContext(Dispatchers.IO) {
                    SaitoPeer(SaitoWallet.create(), backend.peerUrl).use { peer ->
                        RegistryLookup(peer, SaitoRegistry.REGISTRAR).hints(expected.keys)
                    }
                }
                observations.add(JsonObject().apply {
                    addProperty("backend", backend.id); addProperty("checkedAt", System.currentTimeMillis())
                    addProperty("readOnly", true); addProperty("ownershipVerified", false)
                    add("displayNames", JsonObject().apply { names.forEach { (key, name) -> addProperty(key, name) } })
                })
                if (backend == SaitoBackend.OFFICIAL)
                    assertEquals("The public directory must resolve both reported accounts", expected, names)
                // An empty owner-node index is the reproduced provider limitation, not fabricated data.
                assertTrue(names.keys.all { it in expected })
            }
        } finally { File(folder, "readiness.json").writeText(observations.toString()) }
    }
}
