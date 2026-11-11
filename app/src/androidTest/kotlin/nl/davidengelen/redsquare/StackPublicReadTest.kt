package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test

/** Explicit read-only approved public peers, using a disposable identity and no stored owner state. */
class StackPublicReadTest {
    @Test fun approvedPublicNodesReturnAuthenticStackHistoryOrAnExplicitEmptyLibrary() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val folder = File(instrumentation.targetContext.cacheDir, "stack-public-evidence").apply { mkdirs() }
        val observations = JsonArray()
        try {
            for (backend in SaitoBackend.entries) {
                val page = withContext(Dispatchers.IO) {
                    SaitoPeer(SaitoWallet.create(), backend.peerUrl).use { peer ->
                        SaitoStack.readArchive(peer.request(SaitoStack.archiveRequest(System.currentTimeMillis())
                            .toByteArray()).toString(Charsets.UTF_8))
                    }
                }
                if (backend == SaitoBackend.OFFICIAL) assertTrue("Official Stack must return genuine readable articles", page.articles.isNotEmpty())
                assertTrue(page.rowCount == 0 || page.articles.isNotEmpty())
                page.articles.forEach { article -> assertEquals(article.author, SaitoEnvelope.decode(article.envelope).author) }
                observations.add(JsonObject().apply {
                    addProperty("backend", backend.id); addProperty("rows", page.rowCount)
                    addProperty("authenticatedArticles", page.articles.size); addProperty("ignored", page.ignored)
                    addProperty("checkedAt", System.currentTimeMillis()); addProperty("writes", 0)
                })
            }
        } finally { File(folder, "readiness.json").writeText(observations.toString()) }
    }
}
