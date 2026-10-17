package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.notifications.PushGatewayBinding
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/** Actual Settings choices, recreation and recipient-signed HTTP binding; no Google SDK operation. */
class ChatPushConsentPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun backgroundChatConsentPersistsAndEitherFeatureCanKeepTheSharedSubscription() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(!BuildConfig.PUSH_CONFIGURED)
        val wallet = SaitoWallet.create()
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val previousJson = previous.exportJson()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("chatpush.${wallet.publicKey}.",
            "socialpush.${wallet.publicKey}.", "push.binding."), wallet.publicKey)
        val active = AtomicReference<JsonObject?>()
        val expected = AtomicReference("private-chats")
        val failure = AtomicReference<Throwable?>()
        val origin = "https://push.fixture.invalid"
        val nonce = "47".repeat(32)
        val fid = "owned-chat-consent-fid"
        val gateway = mockwebserver3.MockWebServer().apply {
            dispatcher = object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest): mockwebserver3.MockResponse = try {
                    val body = JsonParser.parseString(requireNotNull(request.body).utf8()).asJsonObject
                    require(request.method == "POST" && body.get("version").asInt == 1)
                    when (request.url.encodedPath) {
                        "/v1/challenges" -> {
                            require(body.get("owner").asString == wallet.publicKey)
                            mockwebserver3.MockResponse.Builder().code(201).body(JsonObject().apply {
                                addProperty("version", 1); addProperty("nonce", nonce)
                                addProperty("expiresAt", System.currentTimeMillis() + 300_000)
                            }.toString()).build()
                        }
                        "/v1/subscriptions/bind" -> {
                            require(body.get("owner").asString == wallet.publicKey && body.get("fid").asString == fid)
                            require(body.get("backend").asString == "owner" && body.get("nonce").asString == nonce)
                            require(body.get("categories").asString == expected.get())
                            val intent = "Saito push bind v1\n$origin/v1/subscriptions/bind\n$nonce\n" +
                                "${body.get("subscription").asString}\n${body.get("installation").asString}\n" +
                                "${sha256(fid)}\n${wallet.publicKey}\nowner\n${expected.get()}\n" +
                                "${body.get("expiresAt").asLong}\n${body.get("revocationHash").asString}"
                            require(SaitoWallet.verify(intent.toByteArray(),
                                requireNotNull(request.headers["X-Saito-Signature"]), wallet.publicKey))
                            active.set(body)
                            mockwebserver3.MockResponse.Builder().code(201).body(JsonObject().apply {
                                addProperty("version", 1); addProperty("subscription", body.get("subscription").asString)
                                addProperty("expiresAt", body.get("expiresAt").asLong)
                            }.toString()).build()
                        }
                        "/v1/subscriptions/revoke" -> {
                            val current = requireNotNull(active.get())
                            require(body.get("subscription").asString == current.get("subscription").asString)
                            require(sha256(body.get("revocationSecret").asString) == current.get("revocationHash").asString)
                            active.set(null)
                            mockwebserver3.MockResponse.Builder().body("{\"version\":1,\"active\":false}").build()
                        }
                        else -> error("Unexpected owned gateway route")
                    }
                } catch (caught: Throwable) {
                    failure.set(caught); mockwebserver3.MockResponse.Builder().code(500).build()
                }
            }
        }
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(previous, wallet); backends.save(SaitoBackend.OWNER)
            gateway.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            fun reconcile(categories: String?) {
                expected.set(categories.orEmpty())
                runBlocking(Dispatchers.IO) {
                    PushGatewayBinding.reconcile(context, fid, origin, "http://127.0.0.1:${gateway.port}")
                }
                failure.get()?.let { throw AssertionError("Owned Chat consent binding failed", it) }
                assertEquals(categories, active.get()?.get("categories")?.asString)
            }
            val peer = ControlledFeedPeer("[]")
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use { scenario ->
                    try {
                        click("Settings")
                        choice("Chat alerts when closed", false)
                        choice("Replies and mentions", false)
                        reconcile(null)
                        toggle("Chat alerts when closed", true)
                        reconcile("private-chats")
                        val subscription = active.get()!!.get("subscription").asString
                        click("Done")
                        scenario.recreate()
                        click("Settings")
                        choice("Chat alerts when closed", true)
                        reconcile("private-chats")
                        toggle("Replies and mentions", true)
                        reconcile("mentions,private-chats,replies")
                        toggle("Chat alerts when closed", false)
                        reconcile("mentions,replies")
                        toggle("Chat alerts when closed", true)
                        toggle("Replies and mentions", false)
                        reconcile("private-chats")
                        assertEquals(subscription, active.get()!!.get("subscription").asString)
                        captureNativeScreen(File(context.cacheDir, "chat-alert-evidence/private-background-consent.png"))
                        toggle("Chat alerts when closed", false)
                        reconcile(null)
                        peer.assertHealthy()
                    } catch (caught: Throwable) {
                        captureNativeScreen(File(context.cacheDir, "chat-alert-evidence/private-background-consent-failure.png"))
                        throw caught
                    }
                }
            }
        } catch (caught: Throwable) { primary = caught; throw caught }
        finally {
            cleanupAfterTest(primary, { gateway.close() }, { retained.restoreFiles() }, { retained.restorePreferences() },
                { wallets.replace(wallets.loadExisting(), previous); check(wallets.loadExisting().exportJson() == previousJson) },
                { backends.save(previousBackend); check(backends.load() == previousBackend) })
        }
    }

    private fun click(text: String) {
        val target = hasText(text) and hasClickAction() and isEnabled()
        ui.waitUntil(20_000) { try { ui.onNode(target).assertExists(); true } catch (_: AssertionError) { false } }
        val visible = try { ui.onNode(target).assertIsDisplayed(); true } catch (_: AssertionError) { false }
        if (!visible) ui.onNode(target).performScrollTo()
        ui.onNode(target).performClick()
    }

    private fun choice(text: String, enabled: Boolean) {
        val target = hasText(text) and isToggleable()
        ui.waitUntil(20_000) {
            try {
                ui.onNode(target).assertExists()
                if (enabled) ui.onNode(target).assertIsOn() else ui.onNode(target).assertIsOff()
                ui.onNode(target).assertIsEnabled()
                true
            } catch (_: AssertionError) { false }
        }
        ui.onNode(target).performScrollTo().assertIsDisplayed()
    }

    private fun toggle(text: String, enabled: Boolean) { click(text); choice(text, enabled) }
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
