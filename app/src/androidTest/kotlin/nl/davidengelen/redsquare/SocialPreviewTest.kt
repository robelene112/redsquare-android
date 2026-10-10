package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

class SocialPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("social.") || it.name.startsWith("feed.") ||
                it.name.startsWith("conversation.") || it.name.startsWith("profile.")
        }?.forEach { check(it.delete()) }
    }

    @Test fun aVerifiedLikeAndServedCountStayCoherentAcrossFeedThreadAndProfile() {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-feed-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        fun countedRoot(count: Int): JsonObject = root.deepCopy().apply {
            val envelope = JsonParser.parseString(get("tx").asString).asJsonObject
            envelope.addProperty("opt", Base64.getEncoder().encodeToString("{\"num_likes\":$count,\"num_retweets\":2}".toByteArray()))
            addProperty("tx", envelope.toString())
        }
        val served = AtomicReference(countedRoot(4))
        val liked = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val candidate = postingTransaction(frame)
            val action = SaitoEnvelope.decode(candidate.envelope())
            assertEquals("RedSquare", action.body.get("module").asString)
            assertEquals("like tweet", action.body.get("request").asString)
            assertEquals(root.get("sig").asString, action.body.getAsJsonObject("data").get("signature").asString)
            assertEquals(listOf(action.author, root.get("field2").asString).distinct(), action.recipients)
            liked.set(JsonObject().apply {
                addProperty("sig", action.id); addProperty("field1", "RedSquareLike")
                addProperty("field2", action.author); addProperty("field4", root.get("sig").asString)
                addProperty("created_at", action.createdAt); addProperty("tx", candidate.envelope())
            })
            served.set(countedRoot(5))
        }, archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquareLike") {
                liked.get()?.takeIf { !criteria.has("sig") || criteria.get("sig").asString == it.get("sig").asString }
                    ?.let { "[$it]" } ?: "[]"
            } else "[${served.get()}]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitFor("Native Red Square reference post")
                    waitFor("4 likes")
                    ui.onNodeWithText("Like").performClick()
                    waitFor("Liked")
                    waitFor("5 likes")
                    assertEquals(1, peer.transactions.size)
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    waitFor("Conversation")
                    ui.onNodeWithText("Liked").assertIsDisplayed()
                    ui.onNodeWithText("5 likes").assertIsDisplayed()
                    ui.onNodeWithText("Back").performClick()
                    waitFor("New post")
                    ui.onNodeWithText(root.get("field2").asString).performClick()
                    waitFor("Profile")
                    waitFor("Liked")
                    ui.onNodeWithText("5 likes").assertIsDisplayed()
                    assertEquals(1, peer.transactions.size)
                    served.set(countedRoot(9))
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Refresh").performClick()
                    waitFor("9 likes")
                    ui.onNodeWithText("Liked").assertIsDisplayed()
                    assertEquals(1, peer.transactions.size)
                    peer.assertHealthy()
                    captureFeedEvidence("social-evidence/verified-like-across-views.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("social-evidence/like-first-failure.png")
                    throw failure
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
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitFor("Native Red Square reference post")
                    waitFor("2 reposts")
                    ui.onNodeWithText("Repost").performClick()
                    waitFor("Reposted")
                    ui.onNodeWithText("2 reposts").assertIsDisplayed()
                    assertEquals(1, peer.transactions.size)
                    ui.onNodeWithText("Native Red Square reference post").performClick()
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

    @Test fun uncertainLikeRetryAfterReopeningChecksTheOriginalActionWithoutRebroadcast() {
        recoverUncertainAction("like tweet", "RedSquareLike", "Like", "Retry like", "Liked")
    }

    @Test fun unavailableFreshCountsDoNotTurnAVerifiedLikeIntoAnImpossibleRetry() {
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
            when {
                criteria.get("field1").asString == "RedSquareLike" -> indexed.get()?.let { "[$it]" } ?: "[]"
                criteria.has("sig") -> "not an archive response"
                else -> "[$root]"
            }
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitFor("Native Red Square reference post")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Like") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Like").performClick()
                    waitFor("Liked")
                    waitFor("Could not refresh counts. Refresh this view to try again.")
                    ui.onNodeWithText("Could not finish this action. Retry checks the original action before sending it again.").assertDoesNotExist()
                    ui.onNodeWithText("Liked").assertIsNotEnabled()
                    assertEquals(1, peer.transactions.size)
                    peer.assertHealthy()
                    captureFeedEvidence("social-evidence/verified-like-count-refresh-warning.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("social-evidence/count-refresh-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun uncertainRepostRetryAfterReopeningChecksTheOriginalActionWithoutRebroadcast() {
        recoverUncertainAction("retweet", "RedSquareRetweet", "Repost", "Retry repost", "Reposted")
    }

    @Test fun replacingTheWalletHidesItsPendingLikeAndRestoringItRecoversTheOriginal() {
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
        }).apply { disconnectAfterTransaction = true }
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java).putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            val store = AndroidWalletStore(context)
            ActivityScenario.launch<MainActivity>(intent).use {
                waitFor("Native Red Square reference post")
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Like") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Like").performClick()
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Retry like") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            }
            val original = store.loadExisting()
            val replacement = nl.davidengelen.redsquare.saito.SaitoWallet.create()
            try {
                store.replace(original, replacement)
                peer.disconnectAfterTransaction = false
                ActivityScenario.launch<MainActivity>(intent).use {
                    waitFor("Native Red Square reference post")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Like") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Retry like").assertDoesNotExist()
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(replacement.publicKey, displayedWalletAddress(ui))
                    assertEquals(1, peer.transactions.size)
                }
                store.replace(store.loadExisting(), original)
                ActivityScenario.launch<MainActivity>(intent).use {
                    waitFor("Native Red Square reference post")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Retry like") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Retry like").performClick()
                    waitFor("Liked")
                    assertEquals(1, peer.transactions.size)
                    assertEquals(original.publicKey, indexed.get()!!.get("field2").asString)
                    peer.assertHealthy()
                    captureFeedEvidence("social-evidence/restored-wallet-original-like.png")
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
            val intent = Intent(context, MainActivity::class.java).putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            ActivityScenario.launch<MainActivity>(intent).use {
                try {
                    waitFor("Native Red Square reference post")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText(initial) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText(initial).performClick()
                    waitFor("Pending. Availability on this server has not been verified.")
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
                    ui.onNodeWithText(retry).performClick()
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

    @Test fun failedSavingPreventsTransmissionAndRepairedRetryKeepsTheOriginalWallet() {
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
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                waitFor("Native Red Square reference post")
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Like") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Wallet").performClick()
                val author = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
                val blocked = java.io.File(context.noBackupFilesDir, "social.owner.$author.${root.get("sig").asString}.like.v1")
                check(blocked.mkdir())
                val obstacle = java.io.File(blocked, "retain-public-save-fault").apply { writeText("public test fault") }
                try {
                    ui.onNodeWithText("Like").performClick()
                    waitFor("Could not finish this action. Retry checks the original action before sending it again.")
                    assertTrue(peer.transactions.isEmpty())
                } finally { check(obstacle.delete()); check(blocked.delete()) }
                ui.onNodeWithText("Retry like").performClick()
                waitFor("Liked")
                assertEquals(1, peer.transactions.size)
                val action = SaitoEnvelope.decode(postingTransaction(peer.transactions.single()).envelope())
                assertEquals(author, action.author)
                assertEquals(root.get("sig").asString, action.body.getAsJsonObject("data").get("signature").asString)
                peer.assertHealthy()
                captureFeedEvidence("social-evidence/repaired-saving-retains-wallet-and-target.png")
            }
        }
    }

    @Test fun pendingLikeStaysOnItsOriginalBackendAndDoesNotFollowAServerSwitch() {
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
        val firstOwner = owner.start()
        val originalPort = firstOwner.port
        try {
            official.start().use { officialServer ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:$originalPort/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")).use {
                    waitFor("Native Red Square reference post")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Like") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    firstOwner.close()
                    ui.onNodeWithText("Like").performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Retry like") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Settings").performClick()
                    waitFor("saito.io")
                    ui.onNodeWithText("saito.io").performClick()
                    waitFor("Server: saito.io")
                    waitFor("Native Red Square reference post")
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Like") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Retry like").assertDoesNotExist()
                    assertTrue(official.transactions.isEmpty())
                    assertTrue(owner.transactions.isEmpty())
                    owner.start(originalPort).use {
                        ui.onNodeWithText("Settings").performClick()
                        waitFor("saito.davidengelen.nl")
                        ui.onNodeWithText("saito.davidengelen.nl").performClick()
                        waitFor("Server: saito.davidengelen.nl")
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Retry like") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText("Retry like").performClick()
                        waitFor("Liked")
                        assertEquals(1, owner.transactions.size)
                        assertTrue(official.transactions.isEmpty())
                        owner.assertHealthy(); official.assertHealthy()
                        captureFeedEvidence("social-evidence/pending-like-retains-original-backend.png")
                    }
                }
            }
        } finally { firstOwner.close() }
    }

    private fun waitFor(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
}
