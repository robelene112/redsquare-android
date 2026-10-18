package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.notifications.PushGatewayBinding
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/** Actual Settings choices, recreation and recipient-signed HTTP binding; no Google SDK operation. */
abstract class ChatPushConsentFixture {
    @get:Rule val ui = createEmptyComposeRule()

    protected fun consentJourney(capacity: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(!BuildConfig.PUSH_CONFIGURED)
        val wallet = SaitoWallet.create()
        val author = SaitoWallet.create()
        val group = SaitoWallet.create().publicKey
        val initialGroups = listOf(group) + if (capacity) List(99) { SaitoWallet.create().publicKey } else emptyList()
        val creations = initialGroups.map { GroupChat.create(author, it, "Owned background interest",
            listOf(wallet.publicKey), System.currentTimeMillis()) }
        val capturePrefix = if (capacity) "group-capacity" else "private-background-consent"
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val previousJson = previous.exportJson()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("chatpush.${wallet.publicKey}.",
            "socialpush.${wallet.publicKey}.", "push.binding.", "groupchat.owner.${wallet.publicKey}."), wallet.publicKey)
        val active = AtomicReference<JsonObject?>()
        val expected = AtomicReference("group-chats,private-chats")
        val expectedGroups = AtomicReference(initialGroups.sorted())
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
                            if ("group-chats" in expected.get().split(',')) {
                                require(body.getAsJsonArray("groups").map { it.asString } == expectedGroups.get())
                            } else require(!body.has("groups"))
                            val intent = "Saito push bind v1\n$origin/v1/subscriptions/bind\n$nonce\n" +
                                "${body.get("subscription").asString}\n${body.get("installation").asString}\n" +
                                "${sha256(fid)}\n${wallet.publicKey}\nowner\n${expected.get()}\n" +
                                "${body.get("expiresAt").asLong}\n${body.get("revocationHash").asString}" +
                                if ("group-chats" in expected.get().split(',')) "\n${expectedGroups.get().joinToString(",")}" else ""
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
        var stopped = false
        fun requireStopped() { check(stopped && activeMainActivities().isEmpty()) }
        try {
            retained.prepare()
            wallets.replace(previous, wallet); backends.save(SaitoBackend.OWNER)
            val groups = GroupChatStore(context, wallet, SaitoBackend.OWNER)
            creations.forEach { groups.receive(it.envelope()) }
            val retainedMessage = if (capacity) GroupChat.message(author,
                GroupChat.readCreation(creations.first().envelope()),
                "Keep this group history through subscription capacity changes", System.currentTimeMillis()) else null
            if (retainedMessage != null) {
                groups.receive(retainedMessage.envelope())
                groups.saveDraft(group, "Keep this unsent draft through subscription capacity changes")
            }
            fun assertRetainedGroup() {
                val saved = requireNotNull(groups.load(group))
                assertEquals(ChatGroupRole.MEMBER, saved.group.memberRoles[wallet.publicKey])
                assertEquals(ChatGroupRole.ADMIN, saved.group.memberRoles[author.publicKey])
                assertEquals(1, saved.messages.size)
                assertEquals(requireNotNull(retainedMessage).id, saved.messages.single().id)
                assertEquals("Keep this group history through subscription capacity changes", saved.messages.single().text)
                assertEquals("Keep this unsent draft through subscription capacity changes", saved.draft)
            }
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
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use scenarioUse@{ scenario ->
                    try {
                        click("Settings")
                        choice("Chat alerts when closed", false)
                        choice("Replies and mentions", false)
                        reconcile(null)
                        toggle("Chat alerts when closed", true)
                        reconcile("group-chats,private-chats")
                        val subscription = active.get()!!.get("subscription").asString
                        if (capacity) {
                            toggle("Replies and mentions", true)
                            reconcile("group-chats,mentions,private-chats,replies")
                            val extraGroup = SaitoWallet.create().publicKey
                            groups.receive(GroupChat.create(author, extraGroup, "One more known group",
                                listOf(wallet.publicKey), System.currentTimeMillis()).envelope())
                            expectedGroups.set(emptyList())
                            reconcile("mentions,private-chats,replies")
                            assertRetainedGroup()
                            val notice = "New group alerts when closed are paused above 100 active groups on this server. Your other alerts keep their settings."
                            ui.waitUntil(10_000) {
                                try { ui.onNodeWithText(notice).assertExists(); true } catch (_: AssertionError) { false }
                            }
                            choice("Chat alerts when closed", true); choice("Replies and mentions", true)
                            assertEquals(101, groups.entries().size)
                            assertEquals(wallet.exportJson(), wallets.loadExisting().exportJson())
                            assertEquals(SaitoBackend.OWNER, backends.load())
                            assertEquals(subscription, active.get()!!.get("subscription").asString)
                            ui.onNodeWithText(notice).performScrollTo().assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "chat-alert-evidence/group-capacity-paused.png"))
                            groups.receive(GroupChat.leave(wallet, extraGroup, System.currentTimeMillis()).envelope())
                            expectedGroups.set(initialGroups.sorted())
                            reconcile("group-chats,mentions,private-chats,replies")
                            ui.waitUntil(10_000) {
                                try { ui.onNodeWithText(notice).assertDoesNotExist(); true } catch (_: AssertionError) { false }
                            }
                            assertRetainedGroup()
                            assertEquals(ChatGroupRole.LEFT, groups.load(extraGroup)!!.group.memberRoles[wallet.publicKey])
                            assertEquals(subscription, active.get()!!.get("subscription").asString)
                            captureNativeScreen(File(context.cacheDir, "chat-alert-evidence/group-capacity-resumed.png"))
                            toggle("Chat alerts when closed", false); reconcile("mentions,replies")
                            toggle("Replies and mentions", false); reconcile(null)
                            peer.assertHealthy()
                            assertTrue(peer.transactions.isEmpty() && peer.reports.isEmpty() && peer.relayedMessages.isEmpty())
                            return@scenarioUse
                        }
                        click("Done")
                        scenario.recreate()
                        click("Settings")
                        choice("Chat alerts when closed", true)
                        reconcile("group-chats,private-chats")
                        toggle("Replies and mentions", true)
                        reconcile("group-chats,mentions,private-chats,replies")
                        toggle("Chat alerts when closed", false)
                        reconcile("mentions,replies")
                        toggle("Chat alerts when closed", true)
                        toggle("Replies and mentions", false)
                        reconcile("group-chats,private-chats")
                        assertEquals(subscription, active.get()!!.get("subscription").asString)
                        val known = requireNotNull(groups.load(group)).group
                        groups.receive(GroupChat.removeMember(author, known, wallet.publicKey, System.currentTimeMillis()).envelope())
                        expectedGroups.set(emptyList())
                        reconcile("group-chats,private-chats")
                        assertEquals(ChatGroupRole.REMOVED, groups.load(group)!!.group.memberRoles[wallet.publicKey])
                        captureNativeScreen(File(context.cacheDir, "chat-alert-evidence/$capturePrefix.png"))
                        toggle("Chat alerts when closed", false)
                        reconcile(null)
                        peer.assertHealthy()
                    } catch (caught: Throwable) {
                        try { captureNativeScreen(File(context.cacheDir, "chat-alert-evidence/$capturePrefix-failure.png")) }
                        catch (diagnostic: Throwable) { caught.addSuppressed(diagnostic) }
                        throw caught
                    }
                }
            }
        } catch (caught: Throwable) { primary = caught; throw caught }
        finally {
            cleanupAfterTest(primary,
                {
                    val instrumentation = InstrumentationRegistry.getInstrumentation()
                    val activities = activeMainActivities()
                    instrumentation.runOnMainSync { activities.forEach { it.finish() } }
                    instrumentation.waitForIdleSync()
                    ui.waitUntil(5_000) { activeMainActivities().isEmpty() }
                    stopped = true
                }, { gateway.close() },
                { requireStopped(); retained.restoreFiles() }, { requireStopped(); retained.restorePreferences() },
                { requireStopped(); wallets.replace(wallets.loadExisting(), previous); check(wallets.loadExisting().exportJson() == previousJson) },
                { requireStopped(); backends.save(previousBackend); check(backends.load() == previousBackend) })
        }
    }

    private fun activeMainActivities(): List<MainActivity> {
        var activities = emptyList<MainActivity>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            activities = Stage.values().filter { it != Stage.DESTROYED }
                .flatMap { monitor.getActivitiesInStage(it) }.filterIsInstance<MainActivity>().distinct()
        }
        return activities
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
