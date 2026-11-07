package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Base64
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap

class SocialPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("social.") || it.name.startsWith("socialrate.") || it.name.startsWith("feed.") ||
                it.name.startsWith("conversation.") || it.name.startsWith("profile.")
        }?.forEach { check(it.delete()) }
    }

    @Test fun repeatedLikesSendImmediatelyWithoutArchiveConfirmationOrPendingUi() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        fun countedRoot(count: Int): JsonObject = root.deepCopy().apply {
            val envelope = JsonParser.parseString(get("tx").asString).asJsonObject
            envelope.addProperty("opt", Base64.getEncoder().encodeToString("{\"num_likes\":$count,\"num_retweets\":2}".toByteArray()))
            addProperty("tx", envelope.toString())
        }
        val indexedLikes = ConcurrentHashMap<String, JsonObject>()
        val likeArchiveReads = AtomicInteger()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val action = SaitoEnvelope.decode(candidate.envelope())
            assertEquals("RedSquare", action.body.get("module").asString)
            assertEquals("like tweet", action.body.get("request").asString)
            assertEquals(root.get("sig").asString, action.body.getAsJsonObject("data").get("signature").asString)
            assertEquals(listOf(action.author, root.get("field2").asString).distinct(), action.recipients)
            indexedLikes[candidate.id] = JsonObject().apply {
                addProperty("sig", candidate.id); addProperty("field1", "RedSquareLike")
                addProperty("field2", action.author); addProperty("field4", root.get("sig").asString)
                addProperty("created_at", action.createdAt); addProperty("tx", candidate.envelope())
            }
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareLike") {
                likeArchiveReads.incrementAndGet()
                indexedLikes[criteria.get("sig").asString]?.let { "[$it]" } ?: "[]"
            } else "[${countedRoot(4 + indexedLikes.size)}]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                withFailureCapture({ captureNativeScreen(java.io.File(context.cacheDir,
                    "social-evidence/like-first-failure.png")) }) {
                    waitFor("Native Red Square reference post")
                    waitFor("4 likes")
                    ui.mainClock.autoAdvance = false
                    try {
                        ui.onNode(likePostAction()).performTouchInput { click() }
                        ui.mainClock.advanceTimeBy(72)
                        ui.onNodeWithText("5 likes").assertIsDisplayed()
                        captureNativeScreen(java.io.File(context.cacheDir,
                            "social-evidence/like-feedback-during-tap.png"))
                    } finally { ui.mainClock.autoAdvance = true }
                    ui.clickVisiblePostContent("Like")
                    ui.clickVisiblePostContent("Like")
                    ui.waitUntil(20_000) { peer.transactions.size == 3 }
                    val sent = peer.transactions.map { postingTransaction(it) }
                    assertEquals(3, sent.map { it.id }.distinct().size)
                    assertTrue(sent.all { SaitoEnvelope.decode(it.envelope()).body.getAsJsonObject("data")
                        .get("signature").asString == root.get("sig").asString })
                    ui.onNode(likePostAction()).assertIsDisplayed().assertIsEnabled()
                    waitFor("7 likes")
                    ui.onNodeWithText("7 likes").assertIsDisplayed()
                    ui.onNodeWithText("Liked").assertDoesNotExist()
                    ui.onNodeWithText("Like pending").assertDoesNotExist()
                    ui.onNodeWithText("Retry like").assertDoesNotExist()
                    ui.waitUntil(20_000) { likeArchiveReads.get() >= 3 }
                    assertEquals("Each exact Like receipt is checked in the background", 3, likeArchiveReads.get())
                    peer.assertHealthy()
                    captureNativeScreen(java.io.File(context.cacheDir,
                        "social-evidence/repeated-like-without-confirmation.png"))
                }
            }
        }
    }

    @Test fun anOrdinaryRepostPreservesItsTargetAndDoesNotInventAServerCountIncrease() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val envelope = JsonParser.parseString(root.get("tx").asString).asJsonObject
        envelope.addProperty("opt", Base64.getEncoder().encodeToString("{\"num_likes\":4,\"num_retweets\":2}".toByteArray()))
        root.addProperty("tx", envelope.toString())
        val reposted = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val action = SaitoEnvelope.decode(candidate.envelope())
            assertEquals("RedSquare", action.body.get("module").asString)
            assertEquals("retweet", action.body.get("request").asString)
            val data = action.body.getAsJsonObject("data")
            assertEquals(setOf("signature"), data.keySet())
            assertEquals(root.get("sig").asString, data.get("signature").asString)
            assertEquals(listOf(action.author, root.get("field2").asString).distinct(), action.recipients)
            reposted.set(JsonObject().apply {
                addProperty("sig", action.id); addProperty("field1", "RedSquareRetweet")
                addProperty("field2", action.author); addProperty("field4", root.get("sig").asString)
                addProperty("created_at", action.createdAt); addProperty("tx", candidate.envelope())
            })
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareRetweet") {
                reposted.get()?.takeIf { !criteria.has("sig") || criteria.get("sig").asString == it.get("sig").asString }
                    ?.let { "[$it]" } ?: "[]"
            } else "[$root]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitFor("Native Red Square reference post")
                    waitFor("2 reposts")
                    ui.clickVisiblePostContent("Repost")
                    waitFor("Reposted")
                    ui.onNodeWithText("2 reposts").assertIsDisplayed()
                    assertEquals(1, peer.transactions.size)
                    ui.clickVisiblePostContent("Native Red Square reference post")
                    waitFor("Conversation")
                    ui.onNodeWithText("Reposted").assertIsDisplayed()
                    ui.onNodeWithText("2 reposts").assertIsDisplayed()
                    assertEquals(1, peer.transactions.size)
                    peer.assertHealthy()
                    captureFeedEvidence("social-evidence/verified-ordinary-repost-with-served-count.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("social-evidence/repost-first-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun uncertainRepostRetryAfterReopeningChecksTheOriginalActionWithoutRebroadcast() {
        recoverUncertainAction("retweet", "RedSquareRetweet", "Repost", "Retry repost", "Reposted")
    }

    @Test fun replacingTheWalletKeepsSentLikeIdentityIsolatedAndAllowsANewTapAfterRestore() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val indexed = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val action = SaitoEnvelope.decode(candidate.envelope())
            indexed.set(JsonObject().apply {
                addProperty("sig", action.id); addProperty("field1", "RedSquareLike")
                addProperty("field2", action.author); addProperty("field4", root.get("sig").asString)
                addProperty("created_at", action.createdAt); addProperty("tx", candidate.envelope())
            })
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareLike") indexed.get()?.let { "[$it]" } ?: "[]" else "[$root]"
        })
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/").putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            val store = AndroidWalletStore(context)
            var firstAction: SaitoTransaction? = null
            ActivityScenario.launch<MainActivity>(intent).use {
                waitFor("Native Red Square reference post")
                ui.waitUntil(20_000) { ui.onAllNodes(likePostAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.clickVisiblePostContent("Like")
                ui.waitUntil(20_000) { peer.transactions.size == 1 }
                firstAction = postingTransaction(peer.transactions.single())
                ui.onNode(likePostAction()).assertIsEnabled()
            }
            val original = store.loadExisting()
            val replacement = nl.davidengelen.redsquare.saito.SaitoWallet.create()
            try {
                store.replace(original, replacement)
                ActivityScenario.launch<MainActivity>(intent).use {
                    waitFor("Native Red Square reference post")
                    ui.waitUntil(20_000) { ui.onAllNodes(likePostAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Like pending").assertDoesNotExist()
                    ui.openWallet()
                    assertEquals(replacement.publicKey, displayedWalletAddress(ui))
                    assertEquals(1, peer.transactions.size)
                }
                store.replace(store.loadExisting(), original)
                ActivityScenario.launch<MainActivity>(intent).use {
                    waitFor("Native Red Square reference post")
                    ui.waitUntil(20_000) { ui.onAllNodes(likePostAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.clickVisiblePostContent("Like")
                    ui.waitUntil(20_000) { peer.transactions.size == 2 }
                    val secondAction = postingTransaction(peer.transactions.last())
                    assertNotEquals("A new tap uses a new signed action", requireNotNull(firstAction).id, secondAction.id)
                    assertEquals(original.publicKey, indexed.get()!!.get("field2").asString)
                    ui.onNode(likePostAction()).assertIsEnabled()
                    peer.assertHealthy()
                    captureFeedEvidence("social-evidence/restored-wallet-can-like-again.png")
                }
            } finally {
                val current = store.loadExisting()
                if (current.publicKey != original.publicKey) store.replace(current, original)
            }
        }
    }

    private fun recoverUncertainAction(request: String, archiveKind: String, initial: String, retry: String, available: String) {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val indexed = AtomicReference<JsonObject?>()
        val exactQueries = java.util.concurrent.atomic.AtomicInteger()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val action = SaitoEnvelope.decode(candidate.envelope())
            assertEquals(request, action.body.get("request").asString)
            assertEquals(root.get("sig").asString, action.body.getAsJsonObject("data").get("signature").asString)
            indexed.set(JsonObject().apply {
                addProperty("sig", action.id); addProperty("field1", archiveKind)
                addProperty("field2", action.author); addProperty("field4", root.get("sig").asString)
                addProperty("created_at", action.createdAt); addProperty("tx", candidate.envelope())
            })
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == archiveKind) {
                if (criteria.has("sig")) exactQueries.incrementAndGet()
                indexed.get()?.takeIf { !criteria.has("sig") || criteria.get("sig").asString == it.get("sig").asString }
                    ?.let { "[$it]" } ?: "[]"
            } else "[$root]"
        }).apply { disconnectAfterTransaction = true }
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/").putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            ActivityScenario.launch<MainActivity>(intent).use {
                try {
                    waitFor("Native Red Square reference post")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText(initial) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.clickVisiblePostContent(initial)
                    waitFor("$initial pending")
                    assertActionDetails("$initial pending", "Pending. Availability on this node has not been verified.")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText(retry) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    assertEquals(1, peer.transactions.size)
                } catch (failure: Throwable) {
                    captureFeedEvidence("social-evidence/uncertain-$archiveKind-preparation-failure.png")
                    throw failure
                }
            }
            val original = postingTransaction(peer.transactions.single()).id
            peer.disconnectAfterTransaction = false
            ActivityScenario.launch<MainActivity>(intent).use {
                try {
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    waitFor("Native Red Square reference post")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText(retry) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.clickVisiblePostContent(retry)
                    waitFor(available)
                    assertEquals(1, peer.transactions.size)
                    assertEquals(original, indexed.get()!!.get("sig").asString)
                    assertTrue("Retry must query the original signed action", exactQueries.get() >= 2)
                    peer.assertHealthy()
                    captureFeedEvidence("social-evidence/recovered-$archiveKind-without-rebroadcast.png")
                    val proof = java.io.File(context.cacheDir, "social-evidence/native-$archiveKind-frame.base64")
                    proof.parentFile?.mkdirs()
                    proof.writeText(Base64.getEncoder().encodeToString(peer.transactions.single()))
                } catch (failure: Throwable) {
                    captureFeedEvidence("social-evidence/uncertain-$archiveKind-recovery-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun failedSavingStaysOutOfTheUiAndTheNextTapSendsAfterStorageIsRepaired() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val indexed = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val action = SaitoEnvelope.decode(candidate.envelope())
            indexed.set(JsonObject().apply {
                addProperty("sig", action.id); addProperty("created_at", action.createdAt)
                addProperty("field1", "RedSquareLike"); addProperty("field2", action.author)
                addProperty("field4", root.get("sig").asString); addProperty("tx", candidate.envelope())
            })
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareLike") indexed.get()?.let { "[$it]" } ?: "[]" else "[$root]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                waitFor("Native Red Square reference post")
                ui.waitUntil(20_000) { ui.onAllNodes(likePostAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.openWallet()
                val author = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
                val blocked = java.io.File(context.noBackupFilesDir, "social.owner.$author.${root.get("sig").asString}.like.v1")
                check(blocked.mkdir())
                val obstacle = java.io.File(blocked, "retain-public-save-fault").apply { writeText("public test fault") }
                try {
                    withFailureCapture({ captureNativeScreen(java.io.File(context.cacheDir, "social-evidence/action-status-failure.png")) }) {
                        ui.clickVisiblePostContent("Like")
                        SystemClock.sleep(500)
                        assertTrue(peer.transactions.isEmpty())
                        ui.onNode(likePostAction()).assertIsEnabled()
                        ui.onNodeWithText("Like pending").assertDoesNotExist()
                        ui.onNodeWithText("Like unavailable").assertDoesNotExist()
                    }
                } finally { check(obstacle.delete()); check(blocked.delete()) }
                ui.clickVisiblePostContent("Like")
                ui.waitUntil(20_000) { peer.transactions.size == 1 }
                assertEquals(1, peer.transactions.size)
                val action = SaitoEnvelope.decode(postingTransaction(peer.transactions.single()).envelope())
                assertEquals(author, action.author)
                assertEquals(root.get("sig").asString, action.body.getAsJsonObject("data").get("signature").asString)
                peer.assertHealthy()
                captureFeedEvidence("social-evidence/repaired-saving-retains-wallet-and-target.png")
            }
        }
    }

    @Test fun likesUseTheSelectedBackendAndAnUnsentCandidateStaysWithItsOrigin() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val indexed = AtomicReference<JsonObject?>()
        val owner = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val action = SaitoEnvelope.decode(candidate.envelope())
            indexed.set(JsonObject().apply {
                addProperty("sig", action.id); addProperty("created_at", action.createdAt)
                addProperty("field1", "RedSquareLike"); addProperty("field2", action.author)
                addProperty("field4", root.get("sig").asString); addProperty("tx", candidate.envelope())
            })
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareLike") indexed.get()?.let { "[$it]" } ?: "[]" else "[$root]"
        })
        val official = ControlledFeedPeer("[$root]")
        // Use an explicit port for both listeners so MockWebServer enables reuse before bind.
        val originalPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val firstOwner = owner.start(originalPort)
        try {
            official.start().use { officialServer ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:$originalPort/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")).use {
                    waitFor("Native Red Square reference post")
                    ui.waitUntil(20_000) { ui.onAllNodes(likePostAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    firstOwner.close()
                    ui.clickVisiblePostContent("Like")
                    SystemClock.sleep(500)
                    assertTrue(owner.transactions.isEmpty())
                    ui.onNode(likePostAction()).assertIsEnabled()
                    ui.onNodeWithText("Like pending").assertDoesNotExist()
                    ui.openSettings()
                    ui.onNodeWithText("Node").performScrollTo().performClick()
                    waitFor("saito.io")
                    ui.onNodeWithText("saito.io").performScrollTo().performClick()
                    waitFor("Node: saito.io")
                    waitFor("Native Red Square reference post")
                    ui.clickVisiblePostContent("Like")
                    ui.waitUntil(20_000) { official.transactions.size == 1 }
                    ui.onNode(likePostAction()).assertIsEnabled()
                    ui.onNodeWithText("Like pending").assertDoesNotExist()
                    assertEquals(1, official.transactions.size)
                    assertTrue(owner.transactions.isEmpty())
                    owner.start(originalPort).use {
                        ui.openSettings()
                        ui.onNodeWithText("Node").performScrollTo().performClick()
                        waitFor("saito.davidengelen.nl")
                        ui.onNodeWithText("saito.davidengelen.nl").performScrollTo().performClick()
                        waitFor("Node: saito.davidengelen.nl")
                        waitFor("Native Red Square reference post")
                        ui.clickVisiblePostContent("Like")
                        ui.waitUntil(20_000) { owner.transactions.size == 2 }
                        assertEquals(1, official.transactions.size)
                        val ownerIds = owner.transactions.map(::postingTransaction).map { it.id }
                        assertEquals(2, ownerIds.distinct().size)
                        ui.onNode(likePostAction()).assertIsEnabled()
                        ui.onNodeWithText("Like pending").assertDoesNotExist()
                        owner.assertHealthy(); official.assertHealthy()
                        captureFeedEvidence("social-evidence/backend-like-attempts-stay-local.png")
                    }
                }
            }
        } finally { firstOwner.close() }
    }

    private fun waitFor(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }

    private fun assertActionDetails(summary: String, explanation: String) {
        val details = hasContentDescription("Details: $summary") and hasClickAction()
        ui.onNode(hasVerticalScrollAction()).performScrollToNode(details)
        ui.onNode(details).assertIsDisplayed().assertIsEnabled().performClick()
        ui.onNodeWithText(explanation).assertIsDisplayed()
        ui.onNodeWithText("Done").performClick()
    }
}
