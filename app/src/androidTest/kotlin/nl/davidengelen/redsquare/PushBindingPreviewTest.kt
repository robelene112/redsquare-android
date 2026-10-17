package nl.davidengelen.redsquare

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.notifications.PushGatewayBinding
import nl.davidengelen.redsquare.notifications.SocialPushSettings
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Device journal and current identity over actual HTTP; synthetic FIDs, no Firebase SDK operation. */
class PushBindingPreviewTest {
    @Test fun bindingFollowsDurableWalletBackendAndConsentWithoutRetainingTheOutgoingWallet() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(!BuildConfig.PUSH_CONFIGURED) { "The owned binding fixture requires an unconfigured Firebase build" }
        val first = SaitoWallet.create()
        val second = SaitoWallet.create()
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val previousJson = previous.exportJson()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val originalScope = ChatFixtureState(context,
            listOf("socialpush.${first.publicKey}.", "push.binding."), first.publicKey)
        val replacementScope = ChatFixtureState(context, listOf("socialpush.${second.publicKey}."), second.publicKey)
        val expectedOwner = AtomicReference(first.publicKey)
        val expectedFid = AtomicReference("c-native-original-fid")
        val active = AtomicReference<JsonObject?>()
        val failure = AtomicReference<Throwable?>()
        val binds = AtomicInteger()
        val revocations = AtomicInteger()
        val origin = "https://push.fixture.invalid"
        val nonce = "de".repeat(32)
        val gateway = mockwebserver3.MockWebServer().apply {
            dispatcher = object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest): mockwebserver3.MockResponse = try {
                    require(request.method == "POST")
                    val body = JsonParser.parseString(requireNotNull(request.body).utf8()).asJsonObject
                    require(body.get("version").asInt == 1)
                    when (request.url.encodedPath) {
                        "/v1/challenges" -> {
                            require(body.keySet() == setOf("version", "owner") && body.get("owner").asString == expectedOwner.get())
                            mockwebserver3.MockResponse.Builder().code(201).body(JsonObject().apply {
                                addProperty("version", 1); addProperty("nonce", nonce)
                                addProperty("expiresAt", System.currentTimeMillis() + 300_000)
                            }.toString()).build()
                        }
                        "/v1/subscriptions/bind" -> {
                            require(body.keySet() == setOf("version", "nonce", "subscription", "installation", "fid",
                                "owner", "backend", "categories", "expiresAt", "revocationHash"))
                            require(body.get("owner").asString == expectedOwner.get() && body.get("fid").asString == expectedFid.get())
                            require(body.get("nonce").asString == nonce && body.get("categories").asString == "mentions,replies")
                            val intent = "Saito push bind v1\n$origin/v1/subscriptions/bind\n$nonce\n" +
                                "${body.get("subscription").asString}\n${body.get("installation").asString}\n" +
                                "${sha256(expectedFid.get())}\n${expectedOwner.get()}\n${body.get("backend").asString}\n" +
                                "mentions,replies\n${body.get("expiresAt").asLong}\n${body.get("revocationHash").asString}"
                            require(SaitoWallet.verify(intent.toByteArray(Charsets.UTF_8),
                                requireNotNull(request.headers["X-Saito-Signature"]), expectedOwner.get()))
                            active.set(body); binds.incrementAndGet()
                            mockwebserver3.MockResponse.Builder().code(201).body(JsonObject().apply {
                                addProperty("version", 1); addProperty("subscription", body.get("subscription").asString)
                                addProperty("expiresAt", body.get("expiresAt").asLong)
                            }.toString()).build()
                        }
                        "/v1/subscriptions/revoke" -> {
                            require(body.keySet() == setOf("version", "subscription", "revocationSecret"))
                            require(request.headers["X-Saito-Signature"] == null)
                            val current = requireNotNull(active.get())
                            require(body.get("subscription").asString == current.get("subscription").asString)
                            require(sha256(body.get("revocationSecret").asString) == current.get("revocationHash").asString)
                            active.set(null); revocations.incrementAndGet()
                            mockwebserver3.MockResponse.Builder().code(200).body("{\"version\":1,\"active\":false}").build()
                        }
                        else -> error("Unexpected controlled gateway route")
                    }
                } catch (caught: Throwable) {
                    failure.set(caught); mockwebserver3.MockResponse.Builder().code(500).build()
                }
            }
        }
        var phase = "prepare"
        var primary: Throwable? = null
        try {
            originalScope.prepare(); replacementScope.prepare()
            wallets.replace(previous, first); backends.save(SaitoBackend.OWNER)
            SocialPushSettings(context, first.publicKey).setEnabled(true)
            gateway.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            fun reconcile(fid: String?) = runBlocking(Dispatchers.IO) {
                PushGatewayBinding.reconcile(context, fid, origin, "http://127.0.0.1:${gateway.port}")
                failure.get()?.let { throw AssertionError("Owned gateway rejected binding", it) }
            }
            phase = "initial-binding"
            reconcile(expectedFid.get())
            assertNotNull("Android must bind using its existing wallet", active.get())
            assertEquals(first.publicKey, active.get()!!.get("owner").asString)
            assertEquals("owner", active.get()!!.get("backend").asString)
            val originalId = active.get()!!.get("subscription").asString
            phase = "wallet-replacement"
            wallets.replace(first, second); expectedOwner.set(second.publicKey)
            reconcile("d-native-replacement-fid") // Replacement has no consent yet.
            assertNull(active.get()); assertEquals(1, revocations.get())
            SocialPushSettings(context, second.publicKey).setEnabled(true)
            backends.save(SaitoBackend.OFFICIAL); expectedFid.set("d-native-replacement-fid")
            reconcile(expectedFid.get())
            val replacementId = active.get()!!.get("subscription").asString
            assertNotEquals(originalId, replacementId)
            assertEquals(second.publicKey, active.get()!!.get("owner").asString)
            assertEquals("official", active.get()!!.get("backend").asString)
            phase = "backend-and-identifier-change"
            backends.save(SaitoBackend.OWNER); expectedFid.set("e-native-refreshed-fid")
            reconcile(expectedFid.get())
            assertEquals(replacementId, active.get()!!.get("subscription").asString)
            assertEquals("owner", active.get()!!.get("backend").asString)
            assertEquals(1, revocations.get())
            phase = "opt-out"
            SocialPushSettings(context, second.publicKey).setEnabled(false)
            reconcile(null)
            assertNull(active.get()); assertEquals(2, revocations.get()); assertEquals(3, binds.get())
            assertEquals(second.publicKey, wallets.loadExisting().publicKey)
            assertEquals(SaitoBackend.OWNER, backends.load())
            phase = "passed"
        } catch (caught: Throwable) { primary = caught; throw caught }
        finally {
            var cleanupFailure: Throwable? = null
            val cleanup = listOf<() -> Unit>(
                { File(context.cacheDir, "push-binding-evidence").apply { mkdirs() }.resolve("result.json").writeText(
                    JsonObject().apply { addProperty("phase", phase); addProperty("binds", binds.get())
                        addProperty("revocations", revocations.get()); addProperty("sdk", Build.VERSION.SDK_INT) }.toString()) },
                { gateway.close() }, { originalScope.restoreFiles() }, { replacementScope.restoreFiles() },
                { originalScope.restorePreferences() }, { replacementScope.restorePreferences() },
                { wallets.replace(wallets.loadExisting(), previous); check(wallets.loadExisting().exportJson() == previousJson) },
                { backends.save(previousBackend); check(backends.load() == previousBackend) })
            cleanup.forEach { action -> try { action() } catch (caught: Throwable) {
                if (cleanupFailure == null) cleanupFailure = caught else cleanupFailure!!.addSuppressed(caught)
            } }
            cleanupFailure?.let { if (primary != null) primary!!.addSuppressed(it) else throw it }
        }
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
