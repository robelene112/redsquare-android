package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

class PublishingAuthorPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun editingRequiresAgreementButRemovingThePostRemainsAvailableWithUnapprovedPolicies() {
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val originalText = "Available controlled author's original post"
        val editedText = "Keep my author's edited draft 😀 until deliberate publication"
        val original = SaitoTransaction.create(wallet, RedSquarePosts.message(originalText),
            System.currentTimeMillis() - 60_000, emptyList())
        val retained = ChatFixtureState(context, listOf("feed.", "conversation.", "profile.",
            "postdraft.owner.${wallet.publicKey}", "authorposts.owner.${original.id}"), wallet.publicKey)
        val fixture = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previousPolicy = fixture.takeIf { it.exists() }?.readBytes()
        val revision = "owned-author-${java.util.UUID.randomUUID()}"
        val current = AtomicReference<JsonObject?>(postingArchiveRow(original))
        val deletion = AtomicReference<JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val transaction = postingTransaction(frame)
            val signed = SaitoEnvelope.decode(transaction.envelope())
            assertEquals(wallet.publicKey, signed.author)
            assertEquals(listOf(wallet.publicKey), signed.recipients)
            assertEquals("RedSquare", signed.body.get("module").asString)
            assertEquals(original.id, signed.body.getAsJsonObject("data").get("tweet_id").asString)
            when (signed.body.get("request").asString) {
                "edit tweet" -> {
                    assertEquals(editedText, signed.body.getAsJsonObject("data").get("text").asString)
                    current.set(requireNotNull(current.get()).deepCopy().apply {
                        val envelope = JsonParser.parseString(get("tx").asString).asJsonObject
                        val options = JsonObject().apply {
                            addProperty("update_tx", transaction.envelope()); addProperty("edit_ts", signed.createdAt)
                        }
                        envelope.addProperty("opt", Base64.getEncoder().encodeToString(options.toString().toByteArray(Charsets.UTF_8)))
                        addProperty("tx", envelope.toString())
                    })
                }
                "delete tweet" -> {
                    deletion.set(JsonObject().apply {
                        addProperty("sig", signed.id); addProperty("created_at", signed.createdAt)
                        addProperty("field1", "RedSquare"); addProperty("field2", signed.author)
                        addProperty("tx", transaction.envelope())
                    })
                    current.set(null)
                }
                else -> error("Unexpected author publication")
            }
        }, archiveResponse = { criteria ->
            if (criteria.get("field1")?.asString != "RedSquare") "[]"
            else listOfNotNull(current.get(), deletion.get()).filter { row ->
                (!criteria.has("sig") || criteria.get("sig").asString == row.get("sig").asString) &&
                    (!criteria.has("field2") || criteria.get("field2").asString == row.get("field2").asString) &&
                    (!criteria.has("field5") || criteria.get("field5").asString == row.get("field5")?.asString)
            }.joinToString(prefix = "[", postfix = "]")
        })
        var primary: Throwable? = null
        try {
            retained.prepare()
            backends.save(SaitoBackend.OWNER)
            fixture.writeText("""{"revision":"$revision","ready":true,"enforce":true,
                "terms":"Controlled author terms. No real publisher approval.",
                "community":"Controlled author rules. Editing needs your separate choice."}""")
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ capture("author-first-failure.png") }) {
                        waitFor(originalText)
                        ui.clickVisiblePostContent(originalText)
                        waitFor("Conversation")
                        click("Edit")
                        waitFor("Edit post")
                        ui.waitUntil(20_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(hasSetTextAction()).performTextReplacement(editedText)
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        repeat(2) { attempt ->
                            click("Save changes")
                            waitFor("Review publishing terms")
                            assertTrue(peer.transactions.isEmpty())
                            if (attempt == 0) {
                                click("Cancel")
                                ui.onNode(hasSetTextAction()).assertIsEnabled().assert(
                                    SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(editedText)))
                                capture("author-cancel-keeps-editable-text.png")
                            }
                        }
                        click("Accept")
                        waitFor("Agreement saved. New content stays unsent. Queued messages may resume.")
                        assertTrue("Agreement does not publish the edit", peer.transactions.isEmpty())
                        click("Save changes")
                        waitFor("Edit available on this node.")
                        assertEquals(1, peer.transactions.size)
                        capture("author-edit-deliberately-available-after-agreement.png")
                        fixture.writeText("""{"revision":"$revision-unready","ready":false,"enforce":true}""")
                        click("Back")
                        waitFor("Conversation")
                        waitFor(editedText)
                        click("Delete")
                        waitFor("Delete post")
                        ui.onNodeWithText("Remove this post from normal views on the selected node and this device? Historical and independent copies may remain.").assertIsDisplayed()
                        click("Confirm delete")
                        waitFor("Post removed on this node.")
                        assertEquals(2, peer.transactions.size)
                        ui.onNodeWithText("Publishing terms are being prepared").assertDoesNotExist()
                        ui.onNodeWithText("Review publishing terms").assertDoesNotExist()
                        assertTrue(wallet.exportJson() == AndroidWalletStore(context).loadExisting().exportJson())
                        assertEquals(SaitoBackend.OWNER, backends.load())
                        capture("author-post-removal-without-content-agreement.png")
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() }, { backends.save(previousBackend) },
                { retained.restorePreferences() },
                { if (previousPolicy == null) check(!fixture.exists() || fixture.delete()) else fixture.writeBytes(previousPolicy) })
        }
    }

    private fun click(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().size == 1 }
        ui.onNodeWithText(text).performClick()
    }
    private fun waitFor(text: String) = ui.waitUntil(20_000) { ui.onNodeWithText(text).isDisplayed() }
    private fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "agreement-author-evidence/$name"))
}
