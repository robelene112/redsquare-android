package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.chat.*
import nl.davidengelen.redsquare.feed.PostDraftStore
import nl.davidengelen.redsquare.feed.PostQueueScheduling
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Migration-style persisted intent, actual WorkManager, then native review/cancel/resume. */
internal class PublishingQueueFixture(private val ui: ComposeTestRule, private val kind: Kind) {
    enum class Kind(val capture: String) { POST("post"), PRIVATE("private"), GROUP("group") }
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    fun exercise(cancelQueued: Boolean) {
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
        val key = SaitoWallet.fromPrivateKey("0".repeat(63) + "9") // Public unsafe group fixture only.
        val groupName = "Queued agreement group"
        val wallet = when (kind) {
            Kind.POST -> original
            Kind.PRIVATE -> original.withChatContact(member.publicKey)
            Kind.GROUP -> original.withNamedChatGroup(key.publicKey, groupName, key)
        }
        assertTrue("This fixture must not schedule existing Posts work", PostDraftStore.pendingDelivery(context, wallet.publicKey, SaitoBackend.OWNER).isEmpty())
        assertTrue("This fixture must not schedule existing Chat work", ChatQueueScheduling.pendingIds(context, wallet, SaitoBackend.OWNER).isEmpty())
        val retained = ChatFixtureState(context, listOf("feed.", "conversation.",
            "postdraft.owner.${wallet.publicKey}", "chatwork.owner.${wallet.publicKey}.",
            "chat.owner.${wallet.publicKey}.", "chatreactions.owner.${wallet.publicKey}.",
            "groupchat.owner.${wallet.publicKey}.", "personal.${wallet.publicKey}."), wallet.publicKey)
        val fixture = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previousPolicy = fixture.takeIf { it.exists() }?.readBytes()
        val revision = "owned-queue-${UUID.randomUUID()}"
        val label = "${kind.capture}-${if (cancelQueued) "cancel" else "resume"}"
        val text = "Keep this ${kind.capture} queued message 😀 ${UUID.randomUUID()}"
        val creation = GroupChat.create(wallet, key.publicKey, groupName, listOf(member.publicKey), 1700000123400)
        val group = GroupChat.readCreation(creation.envelope())
        val candidate = when (kind) {
            Kind.POST -> SaitoTransaction.create(wallet, RedSquarePosts.message(text), System.currentTimeMillis(), emptyList())
            Kind.PRIVATE -> PrivateChat.create(wallet, member.publicKey, text, System.currentTimeMillis())
            Kind.GROUP -> GroupChat.message(wallet, group, text, System.currentTimeMillis())
        }
        val posts = PostDraftStore(context, wallet.publicKey, SaitoBackend.OWNER)
        val privateWork = ChatWorkStore(context, wallet, SaitoBackend.OWNER, member.publicKey)
        val groups = GroupChatStore(context, wallet, SaitoBackend.OWNER)
        val indexed = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", onTransaction = { frame ->
            assertEquals("Resume must keep the original signature and body", candidate.envelope(), postingTransaction(frame).envelope())
            if (kind == Kind.POST) indexed.set(postingArchiveRow(candidate))
        }, relayResponse = { signed ->
            assertTrue("Posts must not use the Chat relay", kind != Kind.POST)
            assertEquals(candidate.envelope(), SaitoEnvelope.fromSignedJson(signed))
            "{\"err\":\"\",\"success\":1}"
        }, archiveResponse = { criteria ->
            val row = indexed.get()
            if (kind == Kind.POST && row != null && criteria.get("field1")?.asString == "RedSquare" &&
                (!criteria.has("sig") || criteria.get("sig").asString == candidate.id)) "[$row]" else "[]"
        })
        val manager = WorkManager.getInstance(context)
        val workName = if (kind == Kind.POST) "post:${candidate.id}" else "chat:owner:${wallet.publicKey}:${candidate.id}"
        var workerId: UUID? = null
        fun info(): WorkInfo? = manager.getWorkInfosForUniqueWork(workName).get(5, TimeUnit.SECONDS).singleOrNull()
        fun assertQueued() {
            when (kind) {
                Kind.POST -> posts.load().let { assertTrue(it.queued); assertEquals(candidate.envelope(), it.transaction?.envelope()) }
                Kind.PRIVATE -> privateWork.load().let {
                    assertEquals(ChatWorkStore.Status.QUEUED, it.status)
                    assertEquals(candidate.envelope(), it.transaction?.envelope())
                    assertFalse(ChatHistoryStore(context, wallet, SaitoBackend.OWNER, member.publicKey).containsOriginal(candidate.id))
                }
                Kind.GROUP -> requireNotNull(groups.load(key.publicKey)).let {
                    assertEquals(candidate.envelope(), it.queuedMessage?.candidate?.envelope())
                    assertTrue(it.messages.none { message -> message.id == candidate.id })
                }
            }
            assertTrue(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
        }
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(original, wallet)
            backends.save(SaitoBackend.OWNER)
            // A useful preview can already contain explicit queued intent before final terms exist.
            // This creates local fixture input only, never an acceptance record or network publication.
            fixture.writeText("""{"enforce":false}""")
            when (kind) {
                Kind.POST -> posts.save(PostDraftStore.Saved(text, candidate, queued = true))
                Kind.PRIVATE -> { privateWork.saveDraft(text); privateWork.prepare(candidate, queued = true) }
                Kind.GROUP -> { groups.receive(creation.envelope()); groups.saveDraft(key.publicKey, text); groups.prepareMessage(candidate, queued = true) }
            }
            fixture.writeText("""{"revision":"$revision","ready":true,"enforce":true,
                "terms":"Controlled current queue terms. No real publisher approval.",
                "community":"Controlled queue rules. Keep the original message and cancellation."}""")
            peer.start().use { server ->
                withFailureCapture({ capture("$label-first-failure.png") }) {
                    val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                    instrumentation.runOnMainSync {
                        for (stage in listOf(Stage.RESUMED, Stage.STARTED))
                            assertTrue(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).none { it is MainActivity })
                    }
                    if (kind == Kind.POST) PostQueueScheduling.enqueue(context, wallet.publicKey, SaitoBackend.OWNER, endpoint)
                    else ChatQueueScheduling.enqueue(context, wallet, SaitoBackend.OWNER, endpoint)
                    val deadline = SystemClock.elapsedRealtime() + 40_000
                    var pending: WorkInfo? = null
                    while (pending == null && SystemClock.elapsedRealtime() < deadline) {
                        val current = info()
                        if (current != null) {
                            if (workerId == null) workerId = current.id
                            assertEquals(workerId, current.id)
                            assertFalse("Unaccepted intent must remain retryable", current.state.isFinished)
                            if (current.state == WorkInfo.State.ENQUEUED && current.runAttemptCount >= 1 &&
                                peer.servedChallenges.get() > 0) pending = current
                        }
                        if (pending == null) SystemClock.sleep(50)
                    }
                    val paused = requireNotNull(pending) { "Actual headless worker did not retain the unaccepted intent" }
                    assertQueued()
                    assertTrue(wallet.exportJson() == wallets.loadExisting().exportJson())
                    File(context.cacheDir, "agreement-${kind.capture}-queue-evidence/$label-worker-paused.json").apply {
                        parentFile!!.mkdirs()
                        writeText(JsonObject().apply {
                            addProperty("worker", paused.id.toString()); addProperty("attempts", paused.runAttemptCount)
                            addProperty("state", paused.state.name); addProperty("transaction", candidate.id)
                            addProperty("owner", wallet.publicKey); addProperty("backend", "owner")
                        }.toString())
                    }
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("feedPeerUrl", endpoint)).use {
                        when (kind) {
                            Kind.POST -> click("New post")
                            Kind.PRIVATE -> {
                                click("Chat")
                                val row = hasClickAction() and isEnabled() and hasAnyDescendant(hasText(member.publicKey))
                                ui.waitUntil(20_000) { ui.onAllNodes(row, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
                                ui.onNode(row, useUnmergedTree = true).performClick()
                            }
                            Kind.GROUP -> { click("Chat"); click("Groups"); click(groupName) }
                        }
                        waitFor("Queued")
                        waitFor("Sending is paused until you review the current publishing terms.")
                        touch("Review terms")
                        waitFor("Review publishing terms")
                        ui.onNodeWithText("Accepting does not publish a new draft. Messages you already queued may resume.").performScrollTo().assertIsDisplayed()
                        touch("Cancel")
                        val cancelLabel = if (kind == Kind.POST) "Cancel queued post" else "Cancel queued message"
                        ui.onNodeWithText(cancelLabel).assertIsEnabled().assertIsDisplayed()
                        assertQueued()
                        assertEquals(workerId, info()?.id)
                        capture("$label-review-cancel-keeps-queued-original.png")
                        if (cancelQueued) {
                            touch(cancelLabel)
                            ui.waitUntil(20_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().size == 1 }
                            ui.onNode(hasSetTextAction()).assert(
                                SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text)))
                            when (kind) {
                                Kind.POST -> assertNull(posts.load().transaction)
                                Kind.PRIVATE -> assertNull(privateWork.load().transaction)
                                Kind.GROUP -> requireNotNull(groups.load(key.publicKey)).let {
                                    assertNull(it.queuedMessage); assertTrue(it.messages.none { message -> message.id == candidate.id })
                                }
                            }
                            assertTrue(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            capture("$label-queue-cancel-restores-editable-draft.png")
                        } else {
                            touch("Review terms")
                            waitFor("Review publishing terms")
                            touch("Accept")
                            waitFor(if (kind == Kind.POST) "Available on this server." else "Forwarded. Delivery is not confirmed.")
                            assertEquals(1, peer.transactions.size)
                            assertEquals(if (kind == Kind.POST) 0 else 1, peer.relayedMessages.size)
                            capture("$label-original-resumes-after-deliberate-agreement.png")
                        }
                        assertEquals(workerId, info()?.id)
                        assertTrue(wallet.exportJson() == wallets.loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, backends.load())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary,
                { manager.cancelUniqueWork(workName).result.get(5, TimeUnit.SECONDS) },
                { retained.restoreFiles() }, { wallets.replace(wallets.loadExisting(), original) },
                { backends.save(previousBackend) }, { retained.restorePreferences() },
                { if (previousPolicy == null) check(!fixture.exists() || fixture.delete()) else fixture.writeBytes(previousPolicy) })
        }
    }

    private fun click(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().size == 1 }
        ui.onNodeWithText(text).performClick()
    }
    private fun touch(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().size == 1 }
        ui.onNodeWithText(text).assertIsDisplayed().performTouchInput { click() }
    }
    private fun waitFor(text: String) = ui.waitUntil(20_000) { ui.onNodeWithText(text).isDisplayed() }
    private fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "agreement-${kind.capture}-queue-evidence/$name"))
}
