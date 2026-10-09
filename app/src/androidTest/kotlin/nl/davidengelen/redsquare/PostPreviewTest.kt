package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class PostPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Before fun prepareIsolatedDraftFixtures() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".dev"))
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        // Clear only synthetic draft records between cases. Never touch wallet keys.
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("postdraft.") }
            ?.forEach { check(it.delete()) }
    }

    @Test fun replyingToAnExistingReplyKeepsTheOriginalThreadAndInheritedRecipientOrder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val reference = instrumentation.context.assets.open("native-posting-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("reply")
        }
        val parent = com.google.gson.JsonObject().apply {
            addProperty("sig", reference.get("id").asString); addProperty("created_at", reference.get("timestamp").asLong)
            addProperty("field1", "RedSquare"); addProperty("field2", reference.get("author").asString)
            addProperty("field4", root.get("sig").asString); addProperty("field5", root.get("sig").asString)
            addProperty("tx", reference.get("envelope").asString)
        }
        val indexed = java.util.concurrent.atomic.AtomicReference<com.google.gson.JsonObject?>()
        val peer = ControlledFeedPeer("[$root]", archiveResponse = { criteria ->
            val reply = indexed.get()
            when {
                criteria.has("sig") -> if (reply?.get("sig")?.asString == criteria.get("sig").asString) "[$reply]" else "[]"
                criteria.has("field5") -> if (reply == null) "[$root,$parent]" else "[$root,$parent,$reply]"
                else -> "[$root]"
            }
        }, onTransaction = { frame ->
            val bytes = frame.copyOfRange(1, frame.size - 130)
            java.nio.ByteBuffer.wrap(bytes).putInt(12, 0)
            val transaction = nl.davidengelen.redsquare.saito.SaitoTransaction.restore(java.util.Base64.getEncoder().encodeToString(bytes))
            val envelope = transaction.envelope()
            val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(envelope)
            org.junit.Assert.assertEquals(parent.get("sig").asString, post.parentId)
            org.junit.Assert.assertEquals(root.get("sig").asString, post.threadId)
            org.junit.Assert.assertEquals(listOf(post.author, parent.get("field2").asString, root.get("field2").asString), post.recipients)
            indexed.set(com.google.gson.JsonObject().apply {
                addProperty("sig", post.id); addProperty("created_at", post.createdAt)
                addProperty("field1", "RedSquare"); addProperty("field2", post.author)
                addProperty("field4", post.parentId); addProperty("field5", post.threadId)
                addProperty("tx", envelope)
            })
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitForReferencePost()
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    waitFor("Independent recipient reply")
                    ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Independent recipient reply"))
                    ui.onNodeWithText("Independent recipient reply").performClick()
                    ui.onNodeWithText("Reply").performClick()
                    waitFor("Replying to Anon-${parent.get("field2").asString.take(6)}")
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement("Nested native reply")
                    ui.onNodeWithText("Send").performClick()
                    waitFor("Available on this server.")
                    peer.assertHealthy()
                    captureFeedEvidence("posting-evidence/nested-reply-correct-parent.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("posting-evidence/nested-reply-first-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun unfinishedCompositionSurvivesBackendNavigationAndActivityRecreationWithoutPublishing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val owner = ControlledFeedPeer("[$root]")
        val official = ControlledFeedPeer("[$root]")
        owner.start().use { ownerServer ->
            official.start().use { officialServer ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")).use { scenario ->
                    waitForReferencePost()
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement("Unfinished owner draft")
                    ui.onNodeWithText("Settings").performClick()
                    waitFor("saito.io")
                    ui.onNodeWithText("saito.io").performClick()
                    waitFor("Server: saito.io")
                    waitFor("New post")
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).assert(hasText(""))
                    ui.onNode(hasSetTextAction()).performTextReplacement("Separate official draft")
                    ui.onNodeWithText("Save draft").performClick()
                    waitFor("New post")
                    ui.onNodeWithText("Settings").performClick()
                    waitFor("saito.davidengelen.nl")
                    ui.onNodeWithText("saito.davidengelen.nl").performClick()
                    waitFor("Server: saito.davidengelen.nl")
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.waitUntil(10_000) {
                        ui.onAllNodes(hasSetTextAction() and hasText("Unfinished owner draft")).fetchSemanticsNodes().isNotEmpty()
                    }
                    scenario.recreate()
                    ui.waitUntil(10_000) {
                        ui.onAllNodes(hasSetTextAction() and hasText("Unfinished owner draft")).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onNode(hasSetTextAction()).assertTextEquals("Unfinished owner draft")
                    assertTrue(owner.transactions.isEmpty() && official.transactions.isEmpty())
                    owner.assertHealthy(); official.assertHealthy()
                    captureFeedEvidence("posting-evidence/unfinished-draft-backend-recreation.png")
                }
            }
        }
    }

    @Test fun recoveredPendingPostRetriesItsOriginalSignatureAndClearsOnlyAfterVerifiedAvailability() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val received = java.util.concurrent.CopyOnWriteArrayList<String>()
        val indexed = java.util.concurrent.atomic.AtomicReference<com.google.gson.JsonObject?>()
        val peer = ControlledFeedPeer("[$root]", archiveResponse = { criteria ->
            val row = indexed.get()
            if (criteria.has("sig")) {
                if (row?.get("sig")?.asString == criteria.get("sig").asString) "[$row]" else "[]"
            } else if (row == null) "[$root]" else "[$row,$root]"
        }, onTransaction = { frame ->
            val bytes = frame.copyOfRange(1, frame.size - 130)
            java.nio.ByteBuffer.wrap(bytes).putInt(12, 0)
            val transaction = nl.davidengelen.redsquare.saito.SaitoTransaction.restore(java.util.Base64.getEncoder().encodeToString(bytes))
            received.add(transaction.id)
            if (received.size == 2) {
                org.junit.Assert.assertEquals(received[0], received[1])
                val envelope = transaction.envelope()
                val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(envelope)
                indexed.set(com.google.gson.JsonObject().apply {
                    addProperty("sig", post.id); addProperty("created_at", post.createdAt)
                    addProperty("field1", "RedSquare"); addProperty("field2", post.author)
                    addProperty("field4", ""); addProperty("field5", post.threadId)
                    addProperty("tx", envelope)
                })
            }
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                waitForReferencePost()
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).performTextReplacement("Retry the original signed post")
                ui.onNodeWithText("Send").performClick()
                waitFor("Submitted. Waiting for this server.")
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Back") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                backFromComposer()
                ui.waitUntil(10_000) { ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
                scenario.recreate()
                waitForReferencePost()
                ui.onNodeWithText("New post").performClick()
                waitFor("Send requested. Availability on this server is not confirmed.")
                ui.onNodeWithText("Retry the original signed post").assertTextEquals("Retry the original signed post").assertIsNotEnabled()
                org.junit.Assert.assertEquals(1, received.size)
                ui.onNodeWithText("Retry").performClick()
                waitFor("Available on this server.")
                org.junit.Assert.assertEquals(2, received.size)
                peer.assertHealthy()
                captureFeedEvidence("posting-evidence/retried-original-pending-post.png")
                backFromComposer()
                ui.waitUntil(10_000) { ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).assert(hasText(""))
            }
        }
    }

    @Test fun replyUsesTheSelectedParentThreadAndIncludesTheParentAuthor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val indexed = java.util.concurrent.atomic.AtomicReference<com.google.gson.JsonObject?>()
        val peer = ControlledFeedPeer("[$root]", archiveResponse = { criteria ->
            val reply = indexed.get()
            when {
                criteria.has("sig") -> if (reply?.get("sig")?.asString == criteria.get("sig").asString) "[$reply]" else "[]"
                criteria.has("field5") -> if (reply == null) "[$root]" else "[$root,$reply]"
                else -> "[$root]"
            }
        }, onTransaction = { frame ->
            val bytes = frame.copyOfRange(1, frame.size - 130)
            val header = java.nio.ByteBuffer.wrap(bytes)
            check(header.getInt(0) == 1 && header.getInt(4) == 2)
            val parentPoint = "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
            org.junit.Assert.assertEquals(parentPoint, bytes.copyOfRange(211, 244).joinToString("") { "%02x".format(it) })
            header.putInt(12, 0)
            val transaction = nl.davidengelen.redsquare.saito.SaitoTransaction.restore(java.util.Base64.getEncoder().encodeToString(bytes))
            val envelope = transaction.envelope()
            val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(envelope)
            org.junit.Assert.assertEquals(root.get("sig").asString, post.parentId)
            org.junit.Assert.assertEquals(root.get("sig").asString, post.threadId)
            org.junit.Assert.assertEquals("Reply from the native composer", post.text)
            indexed.set(com.google.gson.JsonObject().apply {
                addProperty("sig", post.id); addProperty("created_at", post.createdAt)
                addProperty("field1", "RedSquare"); addProperty("field2", post.author)
                addProperty("field4", post.parentId); addProperty("field5", post.threadId)
                addProperty("tx", envelope)
            })
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitForReferencePost()
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    waitFor("Conversation")
                    ui.onNodeWithText("Reply").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement("Reply from the native composer")
                    ui.onNodeWithText("Send").performClick()
                    waitFor("Available on this server.")
                    backFromComposer()
                    waitFor("Conversation")
                    waitForVisiblePost("Reply from the native composer")
                    peer.assertHealthy()
                    captureFeedEvidence("posting-evidence/published-reply-in-thread.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("posting-evidence/reply-first-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun matchingSignedArchiveObservationMakesThePublishedPostAvailableInTheFeed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".dev"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val indexed = java.util.concurrent.atomic.AtomicReference<com.google.gson.JsonObject?>()
        val peer = ControlledFeedPeer("[$root]", archiveResponse = { criteria ->
            val current = indexed.get()
            if (criteria.has("sig")) {
                if (current?.get("sig")?.asString == criteria.get("sig").asString) "[$current]" else "[]"
            } else if (current != null) "[$current,$root]" else "[$root]"
        }, onTransaction = { frame ->
            val bytes = frame.copyOfRange(1, frame.size - 130)
            java.nio.ByteBuffer.wrap(bytes).putInt(12, 0)
            val transaction = nl.davidengelen.redsquare.saito.SaitoTransaction.restore(java.util.Base64.getEncoder().encodeToString(bytes))
            val envelope = transaction.envelope()
            val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(envelope)
            indexed.set(com.google.gson.JsonObject().apply {
                addProperty("sig", post.id); addProperty("created_at", post.createdAt)
                addProperty("field1", "RedSquare"); addProperty("field2", post.author)
                addProperty("field4", ""); addProperty("field5", post.threadId)
                addProperty("tx", envelope)
            })
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitForReferencePost()
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement("Observed native publication")
                    ui.onNodeWithText("Send").performClick()
                    waitFor("Available on this server.")
                    captureFeedEvidence("posting-evidence/observed-publication.png")
                    backFromComposer()
                    waitFor("New post")
                    ui.waitUntil(10_000) { ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
                    waitForVisiblePost("Observed native publication")
                    peer.assertHealthy()
                    captureFeedEvidence("posting-evidence/published-post-in-feed.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("posting-evidence/observation-first-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun savedDraftSurvivesReopeningAndRemainsUnsent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".dev"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val peer = ControlledFeedPeer("[$root]")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                try {
                    waitForReferencePost()
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement("Draft kept until Send")
                    ui.onNodeWithText("Save draft").performClick()
                    waitForReferencePost()
                    scenario.recreate()
                    waitForReferencePost()
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.waitUntil(10_000) {
                        ui.onAllNodes(hasSetTextAction() and hasText("Draft kept until Send")).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onNode(hasSetTextAction()).assertTextEquals("Draft kept until Send")
                    peer.assertHealthy()
                    assertTrue("Saving or reopening a draft must not publish it", peer.transactions.isEmpty())
                    captureFeedEvidence("posting-evidence/recovered-unsent-draft.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("posting-evidence/draft-first-failure.png")
                    throw failure
                }
            }
        }
    }

    private fun waitForReferencePost() {
        ui.waitUntil(15_000) {
            ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() &&
                ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()
        }
        waitForVisiblePost("Native Red Square reference post")
    }

    private fun waitForVisiblePost(text: String) {
        ui.waitUntil(15_000) {
            try {
                ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
                ui.onNodeWithText(text).assertIsDisplayed()
                true
            } catch (_: AssertionError) { false }
        }
    }

    private fun backFromComposer() {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Back") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Back").performClick()
    }

    private fun waitFor(text: String) = ui.waitUntil(15_000) {
        ui.onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }

    @Test fun explicitSendBroadcastsAZeroPaymentPostWithoutInventingAcknowledgement() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".dev"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val peer = ControlledFeedPeer("[$root]") { criteria ->
            if (criteria.has("sig")) "[]" else "[$root]"
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitForReferencePost()
                    ui.onNodeWithText("Wallet").performClick()
                    val author = displayedWalletAddress(ui)
                    ui.onNodeWithText("Done").performClick()
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement("Explicit unfunded native post")
                    ui.onNodeWithText("Send").performClick()
                    waitFor("Submitted. Waiting for this server.")
                    ui.waitUntil(10_000) { peer.transactions.isNotEmpty() }
                    val frame = peer.transactions.single()
                    check(frame[0] == 4.toByte())
                    val transaction = frame.copyOfRange(1, frame.size)
                    val header = java.nio.ByteBuffer.wrap(transaction)
                    val inputs = header.int
                    val outputs = header.int
                    val length = header.int
                    val hops = header.int
                    check(inputs == 1 && outputs == 1 && hops == 1)
                    check(transaction.size == 93 + 59 * (inputs + outputs) + length + 130)
                    val messageOffset = 93 + 59 * (inputs + outputs)
                    val message = transaction.copyOfRange(messageOffset, messageOffset + length)
                    val detached = transaction.copyOfRange(0, messageOffset) + transaction.copyOfRange(messageOffset + length, transaction.size)
                    java.nio.ByteBuffer.wrap(detached).putInt(8, 0)
                    val encoder = java.util.Base64.getEncoder()
                    val envelope = com.google.gson.JsonObject().apply {
                        addProperty("t", encoder.encodeToString(detached))
                        addProperty("m", encoder.encodeToString(message))
                        addProperty("opt", encoder.encodeToString("{}".toByteArray()))
                    }.toString()
                    val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(envelope)
                    org.junit.Assert.assertEquals(author, post.author)
                    org.junit.Assert.assertEquals("Explicit unfunded native post", post.text)
                    check(post.parentId.isEmpty() && post.threadId == post.id)
                    repeat(inputs + outputs) { index ->
                        check(java.nio.ByteBuffer.wrap(transaction).getLong(93 + index * 59 + 33) == 0L)
                    }
                    peer.assertHealthy()
                    captureFeedEvidence("posting-evidence/submitted-awaiting-server.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("posting-evidence/send-first-failure.png")
                    throw failure
                }
            }
        }
    }
}
