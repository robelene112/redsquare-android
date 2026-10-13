package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.Strictness
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.davidengelen.redsquare.saito.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Opt-in native service/history reads only. Never submits or relays a message. */
class PublicPrivateChatReadTest {
    @Test fun bothApprovedBackendsSupportAuthenticatedPrivateHistoryReads() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(InstrumentationRegistry.getArguments().getString("publicChatRead") == "1")
        val original = AndroidWalletStore(context).loadOrCreate().exportJson()
        // Ephemeral probe identities keep the installation identity out of public Chat discovery.
        val wallet = SaitoWallet.create()
        val contact = SaitoWallet.create().publicKey
        val report = JsonObject().apply {
            addProperty("native", true); addProperty("readOnly", true)
            addProperty("group", PrivateChat.conversationId(wallet.publicKey, contact))
        }
        val backends = JsonArray().also { report.add("backends", it) }
        val destination = File(context.cacheDir, "public-chat-evidence/readiness.json")
        destination.parentFile!!.mkdirs()
        var primary: Throwable? = null
        try {
            withTimeout(60_000) {
                for (backend in SaitoBackend.entries) {
                    val result = JsonObject().apply {
                        addProperty("backend", backend.id); addProperty("observedAt", System.currentTimeMillis())
                    }.also(backends::add)
                    SaitoPeer(wallet, backend.peerUrl).use { peer ->
                        val services = peer.services()
                        result.addProperty("authenticated", true)
                        result.add("services", JsonArray().apply { services.sorted().forEach(::add) })
                        require(setOf("archive", "relay", "chat").all { it in services })
                        val criteria = JsonObject().apply {
                            addProperty("request", "load")
                            addProperty("field3", report.get("group").asString)
                            addProperty("limit", 50)
                            addProperty("created_earlier_than", System.currentTimeMillis())
                        }
                        val request = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
                        val bytes = peer.request(request.toString().toByteArray(Charsets.UTF_8))
                        require(bytes.size <= 4 * 1024 * 1024)
                        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                        val rows = GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(text, JsonArray::class.java)
                        requireNotNull(rows)
                        require(rows.isEmpty) // A fresh random pair, no message has ever been submitted.
                        result.addProperty("archiveResponded", true)
                        result.addProperty("rows", rows.size())
                    }
                }
            }
            check(AndroidWalletStore(context).loadExisting().exportJson() == original)
            report.addProperty("completed", true)
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            try { destination.writeText(report.toString()) }
            catch (capture: Throwable) { if (primary != null) primary.addSuppressed(capture) else throw capture }
        }
    }
}
