package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import mockwebserver3.Dispatcher
import mockwebserver3.RecordedRequest
import okio.Buffer
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Color
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Native Chat and owned provider/Saito endpoints only; no live provider credentials. */
class ChatGiphyPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private enum class GifFlow { FAILURE, SEND, QUEUED_CANCEL, REPLY }

    @Test fun privateGifFailureCanRetryAndCancelWithoutLosingTheDraft() = exercise(GifFlow.FAILURE)

    @Test fun aSelectedPrivateGifKeepsCreditsAndCaptionThroughReopeningAndExplicitSend() = exercise(GifFlow.SEND)

    @Test fun aSelectedGroupGifKeepsCreditsAndCaptionThroughReopeningAndExplicitSend() = exercise(GifFlow.SEND, group = true)

    @Test fun anOfficialPrivateGifCanBeQueuedReopenedCancelledAndExplicitlySent() =
        exercise(GifFlow.QUEUED_CANCEL, backend = SaitoBackend.OFFICIAL)

    @Test fun anOfficialGroupGifCanBeQueuedReopenedCancelledAndExplicitlySent() =
        exercise(GifFlow.QUEUED_CANCEL, group = true, backend = SaitoBackend.OFFICIAL)

    @Test fun aPrivateReplyKeepsItsQuoteWhenAGifIsSelectedReopenedAndSent() = exercise(GifFlow.REPLY)

    private fun exercise(flow: GifFlow, group: Boolean = false, backend: SaitoBackend = SaitoBackend.OWNER) {
        val selection = flow != GifFlow.FAILURE
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val reference = instrumentation.context.assets.open("native-${if (group) "group" else "private"}-chat-image-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val contact = reference.get(if (group) "author" else "sender").asString
        val groupId = reference.get("groupId").asString
        val groupName = "Compatible GIF group"
        val identity = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture.
        val wallet = if (group) identity.withNamedChatGroup(groupId, groupName) else identity.withChatContact(contact)
        val wallets = AndroidWalletStore(context); val original = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.flatMap { backend -> listOf("chat.${backend.id}.${wallet.publicKey}.",
            "chatwork.${backend.id}.${wallet.publicKey}.", "chatreactions.${backend.id}.${wallet.publicKey}.",
            "groupchat.${backend.id}.${wallet.publicKey}.") } +
            "personal.${wallet.publicKey}."
        val retained = ChatFixtureState(context, prefixes + "giphycredits.${wallet.publicKey}.", wallet.publicKey)
        val kind = if (group) "group" else "private"
        val prefix = "$kind-${backend.id}-${flow.name.lowercase()}"
        val reads = AtomicInteger()
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { criteria ->
            if (criteria.get("field3")?.asString == reference.get("groupId").asString) reads.incrementAndGet()
            "[]"
        })
        val draft = "Keep my Chat draft 😀 <&> \"quoted\"\nA second line"
        var primary: Throwable? = null
        try {
            retained.prepare(); wallets.replace(original, wallet); backends.save(backend)
            if (group) {
                val admin = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture.
                val creation = GroupChat.create(admin, groupId, groupName, listOf(wallet.publicKey), 1700000123400)
                nl.davidengelen.redsquare.chat.GroupChatStore(context, wallet, backend).receive(creation.envelope())
            }
            val quote = "Earlier & quoted text"
            val replyPrefix = if (flow == GifFlow.REPLY) {
                val sender = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").withChatContact(wallet.publicKey)
                val incoming = PrivateChat.create(sender, wallet.publicKey, quote, 1700000123457)
                nl.davidengelen.redsquare.chat.ChatHistoryStore(context, wallet, backend, contact).save(listOf(incoming.envelope()))
                "<blockquote href=\"${contact}1700000123457\">Earlier &amp; quoted text</blockquote>"
            } else ""
            MockWebServer().use { provider ->
                val paths = java.util.concurrent.ConcurrentLinkedQueue<String>()
                provider.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
                val gifPath = "/media/giphy.gif?token=owned%2Bonly&keep=1"
                val source = "http://127.0.0.1:${provider.port}$gifPath"
                if (selection) {
                    val gif = instrumentation.context.assets.open("owned-giphy-animation.gif").use { it.readBytes() }
                    val thumbnail = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
                        .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("images").get("red").asString }
                    provider.dispatcher = object : Dispatcher() {
                        override fun dispatch(request: RecordedRequest): MockResponse {
                            paths.add(request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: ""))
                            return when (request.url.encodedPath) {
                                "/v1/gifs/trending" -> MockResponse.Builder().body("""
                                    {"meta":{"status":200},"data":[{"id":"owned-chat-motion","title":"Owned Chat motion",
                                    "images":{"original":{"url":"$source"},
                                    "fixed_width_small":{"url":"http://127.0.0.1:${provider.port}/media/thumbnail.png"}},
                                    "user":{"display_name":"Owned Chat artist","profile_url":"https://example.org/chat-artist"},
                                    "source_post_url":"https://example.org/chat-source"}]}
                                """.trimIndent()).build()
                                "/media/thumbnail.png" -> MockResponse.Builder().setHeader("Content-Type", "image/png")
                                    .body(Buffer().write(android.util.Base64.decode(thumbnail.substringAfter(','), android.util.Base64.NO_WRAP))).build()
                                "/media/giphy.gif" -> MockResponse.Builder().setHeader("Content-Type", "image/gif").body(Buffer().write(gif)).build()
                                else -> MockResponse.Builder().code(404).build()
                            }
                        }
                    }
                } else repeat(2) { provider.enqueue(MockResponse.Builder().code(503).build()) }
                peer.start().use { server ->
                    val launchIntent = Intent(context, MainActivity::class.java)
                        .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                        .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                        .putExtra("giphyChatFixtureUrl", "http://127.0.0.1:${provider.port}")
                    ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
                        withFailureCapture({ capture("$prefix-failure") }) {
                            fun open(queued: Boolean = false) {
                                val chat = hasText("Chat") and hasClickAction() and isEnabled()
                                ui.waitUntil(20_000) { displayed(hasText("Groups")) || displayed(chat) }
                                if (!displayed(hasText("Groups"))) ui.onNode(chat).performClick()
                                val row = hasClickAction() and isEnabled() and hasAnyDescendant(hasText(if (group) groupName else contact))
                                if (group) {
                                    val groups = hasText("Groups") and hasClickAction() and isEnabled()
                                    ui.waitUntil(20_000) { displayed(groups) || displayed(row, unmerged = true) }
                                    if (displayed(groups)) ui.onNode(groups).performClick()
                                }
                                ui.waitUntil(20_000) { displayed(row, unmerged = true) }
                                val before = reads.get()
                                ui.onNode(row, useUnmergedTree = true).performClick()
                                ui.waitUntil(20_000) { if (queued) displayed(hasText("Queued"))
                                    else displayed(hasSetTextAction() and isEnabled()) && reads.get() > before }
                            }
                            fun assertDraft() {
                                val target = hasSetTextAction() and isEnabled() and SemanticsMatcher("Exact authored Chat text") {
                                    it.config.getOrNull(SemanticsProperties.EditableText)?.text == draft
                                }
                                ui.waitUntil(20_000) { displayed(target) }
                                ui.onNode(target).assertIsDisplayed()
                                if (flow == GifFlow.REPLY) {
                                    ui.onNodeWithText("Remove reply").performScrollTo().assertIsDisplayed()
                                }
                            }
                            open()
                            if (flow == GifFlow.REPLY) {
                                val reply = hasText("Reply") and hasClickAction() and isEnabled()
                                ui.withMessageAction("Reply") {
                                    ui.waitUntil(20_000) { displayed(reply) }
                                    ui.onNode(reply).performClick()
                                }
                                ui.onNodeWithText("Remove reply").assertIsDisplayed()
                            }
                            ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(draft)
                            androidx.test.espresso.Espresso.closeSoftKeyboard()
                            assertEquals(0, provider.requestCount)
                            val preview = hasContentDescription(context.getString(R.string.chat_composition_preview))
                            val add = hasText("Add GIF") and hasClickAction() and isEnabled() and hasAnyAncestor(preview)
                            if (flow == GifFlow.REPLY) captureLayout("$prefix-before-gif-scroll")
                            if (!displayed(add)) ui.onNode(add).performScrollTo()
                            ui.waitUntil(10_000) { displayed(add) }
                            ui.onNode(add).assertIsDisplayed()
                            if (flow == GifFlow.REPLY) captureLayout("$prefix-after-gif-scroll")
                            ui.onNode(add).performClick()
                            if (selection) {
                                val use = hasText("Use GIF") and hasClickAction() and isEnabled()
                                ui.waitUntil(10_000) { ui.onAllNodes(use).fetchSemanticsNodes().size == 1 }
                                ui.onNode(use).performScrollTo().assertIsDisplayed().performClick()
                                ui.waitUntil(10_000) { ui.onAllNodesWithText("Close GIF search").fetchSemanticsNodes().isEmpty() }
                                assertDraft(); assertAnimation()
                                check(paths.contains(gifPath))
                                capture("$prefix-selected")
                                fun credits(name: String) {
                                    for (label in listOf("Powered by GIPHY", "By Owned Chat artist", "Source")) {
                                        ui.waitUntil(10_000) {
                                            if (ui.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()) false
                                            else {
                                                if (!displayed(hasText(label))) {
                                                    try { ui.onNodeWithText(label).performScrollTo() }
                                                    catch (_: AssertionError) { /* History/credits can relayout during scrolling. */ }
                                                }
                                                displayed(hasText(label))
                                            }
                                        }
                                        ui.onNodeWithText(label).assertIsDisplayed()
                                    }
                                    capture(name)
                                }
                                credits("$prefix-selected-credits")
                                val expected = replyPrefix + "<img class=\"img-prev\" src=\"${source.replace("&", "&amp;")}\">\n" +
                                    "<div>Keep my Chat draft 😀 &lt;&amp;&gt; &quot;quoted&quot;\nA second line</div>"
                                val savedDraft = if (group) nl.davidengelen.redsquare.chat.GroupChatStore(context, wallet,
                                    backend).load(groupId)?.draft else nl.davidengelen.redsquare.chat.ChatWorkStore(
                                    context, wallet, backend, contact).load().draft
                                check(savedDraft == expected)
                                check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                                scenario.close()
                                ActivityScenario.launch<MainActivity>(launchIntent).use { fresh ->
                                    withFailureCapture({ capture("$prefix-fresh-failure") }) {
                                        paths.clear(); open(); assertDraft(); assertAnimation()
                                        check(paths.contains(gifPath))
                                        credits("$prefix-fresh-credits")
                                        if (flow == GifFlow.QUEUED_CANCEL) {
                                            fun queuedOriginal(): SaitoTransaction = if (group) {
                                                val queued = requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context, wallet, backend).load(groupId))
                                                check(queued.gifAttribution?.creator == "Owned Chat artist")
                                                requireNotNull(queued.queuedMessage).candidate.also {
                                                    check(GroupChat.readMessage(queued.group, it.envelope()).text == expected)
                                                }
                                            } else {
                                                val queued = nl.davidengelen.redsquare.chat.ChatWorkStore(context, wallet, backend, contact).load()
                                                check(queued.status == nl.davidengelen.redsquare.chat.ChatWorkStore.Status.QUEUED)
                                                check(queued.gifAttribution?.creator == "Owned Chat artist")
                                                requireNotNull(queued.transaction).also {
                                                    check(PrivateChat.read(wallet, it.envelope(), contact).text == expected)
                                                }
                                            }
                                            peer.rejectNewConnections = true; peer.disconnectAuthenticatedSockets()
                                            ui.waitUntil(20_000) { peer.authenticatedConnectionIds.isEmpty() }
                                            ui.onNode(hasText("Send") and hasClickAction() and isEnabled()).performClick()
                                            ui.waitUntil(20_000) { displayed(hasText("Queued")) }
                                            val original = queuedOriginal()
                                            assertAnimation(); credits("$prefix-queued-credits")
                                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                                            fresh.recreate(); open(queued = true)
                                            check(queuedOriginal().envelope() == original.envelope())
                                            val cancel = hasText("Cancel queued message") and hasClickAction() and isEnabled()
                                            ui.waitUntil(20_000) { displayed(cancel) }; ui.onNode(cancel).performClick()
                                            assertDraft(); assertAnimation(); credits("$prefix-restored-after-cancel")
                                            if (group) {
                                                check(requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context, wallet, backend).load(groupId)).let {
                                                    it.queuedMessage == null && it.draft == expected &&
                                                        it.gifAttribution?.creator == "Owned Chat artist" &&
                                                        it.messages.none { message -> message.text == expected }
                                                })
                                            } else {
                                                check(nl.davidengelen.redsquare.chat.ChatWorkStore(context, wallet, backend, contact).load().let {
                                                    it.transaction == null && it.draft == expected && it.gifAttribution?.creator == "Owned Chat artist"
                                                })
                                            }
                                            peer.rejectNewConnections = false
                                            fresh.recreate(); open(); assertDraft(); assertAnimation()
                                            credits("$prefix-online-unsent")
                                            val quietUntil = android.os.SystemClock.elapsedRealtime() + 2_000
                                            ui.waitUntil(3_000) { android.os.SystemClock.elapsedRealtime() >= quietUntil }
                                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                                        }
                                        ui.onNode(hasText("Send") and hasClickAction() and isEnabled()).performClick()
                                        ui.waitUntil(20_000) { peer.transactions.size == 1 && peer.relayedMessages.size == 1 }
                                        val sent = postingTransaction(peer.transactions.single())
                                        check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single()) == sent.envelope())
                                        check(peer.relayRecipients.single() == listOf(wallet.publicKey, contact))
                                        if (group) {
                                            val current = requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context, wallet, backend).load(groupId))
                                            val delivered = GroupChat.readMessage(current.group, sent.envelope())
                                            check(delivered.author == wallet.publicKey && delivered.text == expected)
                                        } else {
                                            val delivered = PrivateChat.read(wallet, sent.envelope(), contact)
                                            check(delivered.author == wallet.publicKey && delivered.text == expected)
                                        }
                                        val caption = hasText(draft) and !hasSetTextAction()
                                        ui.waitUntil(20_000) {
                                            if (!displayed(caption)) {
                                                try { ui.onNode(hasContentDescription(if (group) "Group messages" else "Private messages")).performScrollToNode(caption) }
                                                catch (_: AssertionError) { /* Receipt can precede history publication. */ }
                                            }
                                            displayed(caption)
                                        }
                                        credits("$prefix-sent-credits")
                                        val output = "native-$kind-${backend.id}-gif" + if (flow == GifFlow.REPLY) "-reply" else ""
                                        File(context.cacheDir, "chat-giphy-evidence/$output-envelope.json").writeText(sent.envelope())
                                        File(context.cacheDir, "chat-giphy-evidence/$output-source.txt").writeText(source + "\n")
                                        peer.assertHealthy()
                                        check(wallets.loadExisting().exportJson() == wallet.exportJson() && backends.load() == backend)
                                        check(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == wallet.publicKey })
                                        check(peer.transactions.size == 1 && peer.relayedMessages.size == 1 && peer.reports.isEmpty())
                                    }
                                }
                                return@withFailureCapture
                            }
                            val error = hasText("GIF search is unavailable. Try again.")
                            ui.waitUntil(10_000) { provider.requestCount == 1 && displayed(error) }
                            ui.onNodeWithText("Powered by GIPHY").assertIsDisplayed()
                            capture("private-provider-failure")
                            ui.onNodeWithText("Retry").performClick()
                            ui.waitUntil(10_000) { provider.requestCount == 2 && displayed(error) }
                            repeat(2) {
                                val request = requireNotNull(provider.takeRequest(2, TimeUnit.SECONDS))
                                assertEquals("GET", request.method)
                                assertEquals("/v1/gifs/trending", request.url.encodedPath)
                                assertEquals("owned-test-only", request.url.queryParameter("api_key"))
                            }
                            ui.onNodeWithText("Close GIF search").performClick()
                            assertDraft()
                            ui.onNode(hasText("Back") and hasClickAction()).performClick()
                            scenario.recreate(); open(); assertDraft()
                            assertEquals(2, provider.requestCount)
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty() && peer.reports.isEmpty())
                            check(wallets.loadExisting().exportJson() == wallet.exportJson() && backends.load() == backend)
                            check(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == wallet.publicKey })
                            peer.assertHealthy(); capture("private-draft-restored")
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() },
                { wallets.replace(wallets.loadOrCreate(), original) }, { backends.save(originalBackend) },
                { retained.restorePreferences() })
        }
    }

    private fun assertAnimation() {
        val image = hasContentDescription("Image 1 of 1")
        ui.waitUntil(10_000) { ui.onAllNodes(image).fetchSemanticsNodes().size == 1 }
        if (!displayed(image)) ui.onNode(image).performScrollTo()
        ui.onNode(image).assertIsDisplayed()
        var red = false; var blue = false
        ui.waitUntil(10_000) {
            val bitmap = ui.onNode(image).captureToImage().asAndroidBitmap()
            val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            red = red || (kotlin.math.abs(Color.red(pixel) - 220) <= 5 &&
                kotlin.math.abs(Color.green(pixel) - 30) <= 5 && kotlin.math.abs(Color.blue(pixel) - 40) <= 5)
            blue = blue || (kotlin.math.abs(Color.red(pixel) - 20) <= 5 &&
                kotlin.math.abs(Color.green(pixel) - 60) <= 5 && kotlin.math.abs(Color.blue(pixel) - 220) <= 5)
            red && blue
        }
    }

    private fun displayed(target: SemanticsMatcher, unmerged: Boolean = false) =
        try { ui.onNode(target, useUnmergedTree = unmerged).assertIsDisplayed(); true } catch (_: AssertionError) { false }
    private fun captureLayout(name: String) {
        capture(name)
        File(context.cacheDir, "chat-giphy-evidence/$name.semantics.txt").writeText(
            ui.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE))
    }
    private fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "chat-giphy-evidence/$name.png"))
}
