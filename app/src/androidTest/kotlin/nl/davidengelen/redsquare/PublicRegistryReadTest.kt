package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.Strictness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoRegistry
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Explicit opt-in public application reads. No registration or transaction submission. */
class PublicRegistryReadTest {
    @Test fun bothApprovedBackendsReportRegistryReadinessWithoutRegistering() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(InstrumentationRegistry.getArguments().getString("publicRegistryRead") == "1")
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val report = JsonObject().apply {
            addProperty("native", true)
            addProperty("readOnly", true)
            addProperty("wallet", wallet.publicKey)
            addProperty("registrar", SaitoRegistry.REGISTRAR)
        }
        val backends = JsonArray()
        report.add("backends", backends)
        val destination = File(context.cacheDir, "public-registry-evidence/readiness.json")
        destination.parentFile!!.mkdirs()
        var primary: Throwable? = null
        try {
            // Eight bounded peer operations can each take 12 seconds.
            withTimeout(110_000) {
                for (backend in SaitoBackend.entries) {
                    val result = JsonObject().apply {
                        addProperty("backend", backend.id)
                        addProperty("observedAt", System.currentTimeMillis())
                    }
                    backends.add(result)
                    try {
                        SaitoPeer(wallet, backend.peerUrl).use { peer ->
                            peer.authenticate()
                            result.addProperty("authenticated", true)
                            suspend fun read(data: JsonObject): String {
                                val request = JsonObject().apply { addProperty("request", "registry query"); add("data", data) }
                                val bytes = peer.request(request.toString().toByteArray(Charsets.UTF_8))
                                require(bytes.size <= 64 * 1024)
                                return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                            }
                            val hints = GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(read(JsonObject().apply {
                                addProperty("request", "registry query")
                                add("keys", JsonArray().apply { add(wallet.publicKey) })
                            }), JsonObject::class.java)
                            requireNotNull(hints)
                            require(hints.keySet().all { it == wallet.publicKey })
                            require(hints.entrySet().all { (_, value) -> value.isJsonPrimitive && value.asJsonPrimitive.isString &&
                                value.asString.matches(Regex("[A-Za-z0-9]{1,45}@saito")) })
                            result.addProperty("keyMapResponded", true)
                            result.addProperty("hintCount", hints.size())
                            val checks = JsonArray()
                            result.add("namechecks", checks)
                            val names = listOf("NativeAndroidProbe${System.currentTimeMillis()}@saito", "Saitoverse@saito")
                            for (identifier in names) {
                                val check = JsonObject().apply { addProperty("identifier", identifier) }
                                checks.add(check)
                                try {
                                    val text = read(JsonObject().apply {
                                        addProperty("request", "registry namecheck"); addProperty("identifier", identifier)
                                    })
                                    check.addProperty("responseBytes", text.toByteArray(Charsets.UTF_8).size)
                                    val record = SaitoRegistry.readNamecheck(text, identifier)
                                    check.addProperty("outcome", when { record == null -> "available"; record.active -> "verified-active"; else -> "verified-inactive" })
                                    record?.let { check.addProperty("owner", it.publicKey) }
                                } catch (_: TimeoutCancellationException) {
                                    currentCoroutineContext().ensureActive()
                                    check.addProperty("outcome", "unavailable")
                                    check.addProperty("reason", "request-timeout")
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) {
                                    check.addProperty("outcome", "unavailable")
                                    check.addProperty("reason", "unusable-response")
                                }
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                        currentCoroutineContext().ensureActive()
                        result.addProperty("connectionOutcome", "unavailable")
                        result.addProperty("reason", "request-timeout")
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        result.addProperty("connectionOutcome", "unavailable")
                        result.addProperty("reason", "unusable-response")
                    }
                }
            }
            check(AndroidWalletStore(context).loadExisting().publicKey == wallet.publicKey)
            report.addProperty("completed", true)
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            try { destination.writeText(report.toString()) }
            catch (capture: Throwable) { if (primary != null) primary.addSuppressed(capture) else throw capture }
        }
    }
}
