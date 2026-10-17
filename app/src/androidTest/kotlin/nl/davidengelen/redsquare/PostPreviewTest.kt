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

    @Test fun aLegacyEncryptedTextDraftCanBeOpenedSavedAndRestoredWithoutPublishing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val owner = AndroidWalletStore(context).loadOrCreate().publicKey
        val text = "Legacy local draft 🌱\nStill unsent"
        // Independently reproduce the original v1 raw-text format, before JSON drafts.
        // The disposable app/emulator guards and draft-only reset above apply here too.
        val alias = "redsquare.postdraft.v1"
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keyStore.getKey(alias, null) as? javax.crypto.SecretKey ?: run {
            check(context.noBackupFilesDir.listFiles()?.none { it.name.startsWith("postdraft.") } == true)
            javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
                init(android.security.keystore.KeyGenParameterSpec.Builder(alias,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
        }
        val filename = "postdraft.owner.$owner.v1"
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(javax.crypto.Cipher.ENCRYPT_MODE, key)
            updateAAD("${context.packageName}:$filename".toByteArray(Charsets.UTF_8))
        }
        java.io.FileOutputStream(java.io.File(context.noBackupFilesDir, filename)).use {
            it.write(byteArrayOf(1) + cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
            it.fd.sync()
        }
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val peer = ControlledFeedPeer("[$root]")
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            ActivityScenario.launch<MainActivity>(intent).use {
                try {
                    waitForReferencePost()
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).assertTextEquals(text)
                    ui.onNodeWithText("Save draft").performClick()
                    ui.waitUntil(10_000) {
                        ui.onAllNodes(hasText("New post") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } catch (failure: Throwable) {
                    try { captureFeedEvidence("posting-evidence/legacy-draft-first-failure.png") }
                    catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                }
            }
            ActivityScenario.launch<MainActivity>(intent).use {
                try {
                    waitForReferencePost()
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).assertTextEquals(text)
                    captureFeedEvidence("posting-evidence/restored-legacy-draft.png")
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } catch (failure: Throwable) {
                    try { captureFeedEvidence("posting-evidence/legacy-draft-restoration-failure.png") }
                    catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                }
            }
        }
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
            val transaction = postingTransaction(frame)
            val envelope = transaction.envelope()
            val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(envelope)
            org.junit.Assert.assertEquals(parent.get("sig").asString, post.parentId)
            org.junit.Assert.assertEquals(root.get("sig").asString, post.threadId)
            org.junit.Assert.assertEquals(listOf(post.author, parent.get("field2").asString, root.get("field2").asString), post.recipients)
            indexed.set(postingArchiveRow(transaction))
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitForReferencePost()
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    waitForVisiblePost("Independent recipient reply")
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
                    // The compact typing layout restores global navigation after IME dismissal.
                    scenario.onActivity { activity ->
                        activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                            .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
                    }
                    ui.waitUntil(10_000) { ui.onNodeWithText("Settings").isDisplayed() }
                    ui.onNode(hasSetTextAction()).assertTextEquals("Unfinished owner draft")
                    assertTrue(owner.transactions.isEmpty() && official.transactions.isEmpty())
                    ui.onNodeWithText("Settings").performClick()
                    waitFor("saito.io")
                    ui.onNodeWithText("saito.io").performScrollTo().performClick()
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
                    ui.onNodeWithText("saito.davidengelen.nl").performScrollTo().performClick()
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

    @Test fun unfinishedCompositionSurvivesActivityRecreationBeforeAnySaveOrNavigation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val peer = ControlledFeedPeer("[$root]")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                waitForReferencePost()
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).performTextReplacement("Keep this unsaved composition")
                scenario.recreate()
                ui.waitUntil(10_000) {
                    ui.onAllNodes(hasSetTextAction() and hasText("Keep this unsaved composition")).fetchSemanticsNodes().isNotEmpty()
                }
                ui.onNode(hasSetTextAction()).assertTextEquals("Keep this unsaved composition").assertIsEnabled()
                assertTrue(peer.transactions.isEmpty())
                peer.assertHealthy()
                captureFeedEvidence("posting-evidence/unsaved-composition-recreation.png")
            }
        }
    }

    @Test fun failedDraftSavingPreventsBackendNavigationAndRetainsEditableWork() = failedDraftSaving(false)

    @Test fun failedImageDraftSavingKeepsItsPixelsAndServerUntilStorageRecovers() = failedDraftSaving(true)

    private fun failedDraftSaving(withImage: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val owner = ControlledFeedPeer("[$root]")
        val official = ControlledFeedPeer("[$root]")
        owner.start().use { ownerServer -> official.start().use { officialServer ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")).use { scenario ->
                waitForReferencePost()
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).performTextReplacement("Retain work when saving fails")
                if (withImage) ui.attachSyntheticImage()
                // Platform keyboard dismissal keeps the unchanged server label in view.
                scenario.onActivity { activity ->
                    activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
                }
                val obstacle = java.io.File(context.noBackupFilesDir, "postdraft.owner.$address.v1")
                if (withImage) check(obstacle.delete()) // Obstruct only this synthetic draft after preparation.
                check(obstacle.mkdir())
                val child = java.io.File(obstacle, "controlled-obstacle")
                child.writeText("Synthetic storage obstruction")
                withImageFixtureCleanup({
                    var cleanupFailure: Throwable? = null
                    for (entry in listOf(child, obstacle)) {
                        try { check(entry.delete()) }
                        catch (problem: Throwable) {
                            val previous = cleanupFailure
                            if (previous == null) cleanupFailure = problem else previous.addSuppressed(problem)
                        }
                    }
                    cleanupFailure?.let { throw it }
                }) {
                    try {
                        if (withImage) {
                            val pickerActions = listOf(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT,
                                "android.provider.action.PICK_IMAGES", "androidx.activity.result.contract.action.PICK_IMAGES",
                                "com.google.android.gms.provider.action.PICK_IMAGES")
                            androidx.test.espresso.intent.Intents.init()
                            try {
                                // Any mistaken external launch returns cancellation rather than
                                // hiding the failed-save assertions behind another Android window.
                                androidx.test.espresso.intent.Intents.intending(org.hamcrest.CoreMatchers.anyOf(
                                    pickerActions.map { androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(it) }))
                                    .respondWith(android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null))
                                ui.clickImagePicker()
                                waitFor("Could not load or confirm the saved draft. Your current text remains here. Try again.")
                                assertTrue("A failed draft save must not open an external picker",
                                    androidx.test.espresso.intent.Intents.getIntents().none { it.action in pickerActions })
                                ui.onNode(hasSetTextAction()).assertTextEquals("Retain work when saving fails").assertIsEnabled()
                                ui.assertSyntheticRedImage()
                                assertTrue(owner.transactions.isEmpty() && official.transactions.isEmpty())
                            } finally { androidx.test.espresso.intent.Intents.release() }
                        }
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("saito.io").performScrollTo().performClick()
                        waitFor("Could not load or confirm the saved draft. Your current text remains here. Try again.")
                        ui.waitUntil(10_000) { ui.onNodeWithText("Server: saito.davidengelen.nl").isDisplayed() }
                        ui.onNodeWithText("Server: saito.davidengelen.nl").assertIsDisplayed()
                        ui.onNode(hasSetTextAction()).assertTextEquals("Retain work when saving fails").assertIsEnabled()
                        if (withImage) ui.assertSyntheticRedImage()
                        assertTrue(owner.transactions.isEmpty() && official.transactions.isEmpty())
                        captureFeedEvidence("posting-evidence/failed-save-keeps-server-and-text.png")
                    } catch (failure: Throwable) {
                        captureFeedEvidence("posting-evidence/failed-save-navigation-failure.png")
                        throw failure
                    }
                }
                ui.onNodeWithText("Settings").performClick()
                ui.onNodeWithText("saito.io").performScrollTo().performClick()
                waitFor("Server: saito.io")
                waitFor("New post")
                ui.onNodeWithText("Settings").performClick()
                ui.onNodeWithText("saito.davidengelen.nl").performScrollTo().performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and hasText("Retain work when saving fails")).fetchSemanticsNodes().isNotEmpty() }
                scenario.recreate()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and hasText("Retain work when saving fails")).fetchSemanticsNodes().isNotEmpty() }
                if (withImage) ui.assertSyntheticRedImage()
                assertTrue(owner.transactions.isEmpty() && official.transactions.isEmpty())
                owner.assertHealthy(); official.assertHealthy()
            }
        } }
    }

    @Test fun damagedEncryptedDraftRemainsUntouchedUntilStorageIsRepairedAndRetried() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val peer = ControlledFeedPeer("[$root]")
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                waitForReferencePost()
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).performTextReplacement("Recover the encrypted draft")
                ui.onNodeWithText("Save draft").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
                val file = java.io.File(context.noBackupFilesDir, "postdraft.owner.$address.v1")
                val original = file.readBytes()
                val damaged = original.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
                file.writeBytes(damaged)
                try {
                    ui.onNodeWithText("New post").performClick()
                    waitFor("Could not load or confirm the saved draft. Your current text remains here. Try again.")
                    ui.onNodeWithText("Save draft").assertIsNotEnabled()
                    ui.onNodeWithText("Send").assertIsNotEnabled()
                    assertTrue(file.readBytes().contentEquals(damaged))
                    assertTrue(peer.transactions.isEmpty())
                    captureFeedEvidence("posting-evidence/damaged-draft-recovery.png")
                    file.writeBytes(original)
                    ui.onNodeWithText("Retry").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and hasText("Recover the encrypted draft")).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).assertTextEquals("Recover the encrypted draft").assertIsEnabled()
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } finally { file.writeBytes(original); original.fill(0); damaged.fill(0) }
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
            val transaction = postingTransaction(frame)
            received.add(transaction.id)
            if (received.size == 2) {
                org.junit.Assert.assertEquals(received[0], received[1])
                indexed.set(postingArchiveRow(transaction))
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

    @Test fun replyKeepsItsSelectedParentThreadAndTextThroughActivityRecreation() {
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
            val transaction = postingTransaction(frame)
            val envelope = transaction.envelope()
            val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(envelope)
            org.junit.Assert.assertEquals(root.get("sig").asString, post.parentId)
            org.junit.Assert.assertEquals(root.get("sig").asString, post.threadId)
            org.junit.Assert.assertEquals("Reply from the native composer", post.text)
            indexed.set(postingArchiveRow(transaction))
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                try {
                    waitForReferencePost()
                    ui.onNodeWithText("Native Red Square reference post").performClick()
                    waitFor("Conversation")
                    ui.onNodeWithText("Reply").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement("Reply from the native composer")
                    scenario.recreate()
                    ui.waitUntil(10_000) {
                        ui.onAllNodes(hasSetTextAction() and hasText("Reply from the native composer")).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onNodeWithText("Replying to Anon-${root.get("field2").asString.take(6)}").assertIsDisplayed()
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

    @Test fun invalidUnicodeNeverPublishesOrOverwritesTheLastSavedDraft() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val peer = ControlledFeedPeer("[$root]")
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            ActivityScenario.launch<MainActivity>(intent).use {
                waitForReferencePost()
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).performTextReplacement("Last recoverable valid draft")
                ui.onNodeWithText("Save draft").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                val invalid = "Keep \uD800 as entered"
                ui.onNode(hasSetTextAction()).performTextReplacement(invalid)
                ui.onNodeWithText("Send").performClick()
                waitFor("This post is too large or contains invalid text. Shorten it or remove an image, then try again. Your draft is still here.")
                ui.onNode(hasSetTextAction()).assertTextEquals(invalid).assertIsEnabled()
                assertTrue(peer.transactions.isEmpty())
                peer.assertHealthy()
                captureFeedEvidence("posting-evidence/invalid-text-retained.png")
                ui.onNodeWithText("Save draft").performClick()
                waitFor("Could not load or confirm the saved draft. Your current text remains here. Try again.")
                ui.onNode(hasSetTextAction()).assertTextEquals(invalid)
            }
            // A new activity has no previous ViewModel edits to hide a disk overwrite.
            ActivityScenario.launch<MainActivity>(intent).use {
                waitForReferencePost()
                ui.onNodeWithText("New post").performClick()
                try {
                    ui.waitUntil(10_000) {
                        ui.onAllNodes(hasSetTextAction() and hasText("Last recoverable valid draft")).fetchSemanticsNodes().isNotEmpty()
                    }
                } catch (failure: Throwable) {
                    captureFeedEvidence("posting-evidence/invalid-save-cold-recovery-failure.png")
                    throw failure
                }
                ui.onNode(hasSetTextAction()).assertTextEquals("Last recoverable valid draft")
                assertTrue(peer.transactions.isEmpty())
                peer.assertHealthy()
                captureFeedEvidence("posting-evidence/invalid-save-keeps-last-good-draft.png")
            }
        }
    }

    @Test fun startingAnotherDraftRequiresConfirmationAndDoesNotClaimToRemoveTheUncertainPost() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val peer = ControlledFeedPeer("[$root]", archiveResponse = { criteria ->
            require(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            if (criteria.has("sig")) "[]" else {
                require(criteria.get("field4").asString == "")
                "[$root]"
            }
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                waitForReferencePost()
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).performTextReplacement("A post whose availability remains uncertain")
                ui.onNodeWithText("Send").performClick()
                waitFor("Submitted. Waiting for this server.")
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Back") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Start another draft").performClick()
                waitFor("Clear this local posting record?")
                ui.onNodeWithText("This post may already have reached the server. Clearing removes its saved text and retry record from this composer. It does not remove any published post or copies elsewhere.").assertIsDisplayed()
                ui.onNodeWithText("Keep this post").performClick()
                ui.onNodeWithText("A post whose availability remains uncertain").assertIsNotEnabled()
                org.junit.Assert.assertEquals(1, peer.transactions.size)
                ui.onNodeWithText("Start another draft").performClick()
                ui.onNodeWithText("Clear local record").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).assert(hasText(""))
                scenario.recreate()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).assert(hasText(""))
                org.junit.Assert.assertEquals(1, peer.transactions.size)
                peer.assertHealthy()
                captureFeedEvidence("posting-evidence/confirmed-clear-starts-unsent-draft.png")
            }
        }
    }

    @Test fun walletReplacementExplainsLocalWorkAndNeverPublishesTheOutgoingWalletDraft() = walletDraftReplacement(false)

    @Test fun walletReplacementIsolatesAndRestoresPreparedImageDraftWithoutItsSource() = walletDraftReplacement(true)

    private fun walletDraftReplacement(withImage: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val balances = mockwebserver3.MockWebServer()
        balances.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest) = mockwebserver3.MockResponse.Builder()
                .body("${System.currentTimeMillis()}-1234-${"a".repeat(64)}.snap\n").build()
        }
        balances.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
        val store = AndroidWalletStore(context)
        var previous: nl.davidengelen.redsquare.saito.SaitoWallet? = null
        val peer = ControlledFeedPeer("[$root]")
        try { peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("balanceApiUrl", "http://127.0.0.1:${balances.port}/balance/")
            ActivityScenario.launch<MainActivity>(intent).use {
                waitForReferencePost()
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).performTextReplacement("Draft belonging to the outgoing wallet")
                if (withImage) ui.attachSyntheticImage()
                ui.onNodeWithText("Save draft").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Wallet").performClick()
                previous = store.loadExisting()
                org.junit.Assert.assertNotEquals("jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR", displayedWalletAddress(ui))
                ui.onNodeWithText("Import wallet").performClick()
                chooseReferenceImport()
                waitFor("Server reports zero SAITO")
                ui.onNodeWithText("Local drafts and posting records stay on this device with your current wallet. They are unavailable while another wallet is active and are not included in wallet exports. A post may already have reached its server even if availability is unconfirmed.")
                    .performScrollTo().assertIsDisplayed()
                ui.onNodeWithText("Skip backup").performScrollTo().performClick()
                ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
                ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsEnabled().performClick()
                waitFor("Wallet imported")
                org.junit.Assert.assertEquals("jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR", displayedWalletAddress(ui))
                ui.onNodeWithText("Done").performClick()
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).assert(hasText(""))
                if (withImage) ui.onAllNodesWithContentDescription("Image 1 of 1").assertCountEquals(0)
                assertTrue(peer.transactions.isEmpty())
                captureFeedEvidence("posting-evidence/replacement-wallet-has-separate-draft.png")
            }
            // Restore the original isolated test identity without exporting private material.
            store.replace(store.loadExisting(), requireNotNull(previous))
            ActivityScenario.launch<MainActivity>(intent).use {
                waitForReferencePost()
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and hasText("Draft belonging to the outgoing wallet")).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).assertTextEquals("Draft belonging to the outgoing wallet")
                if (withImage) ui.assertSyntheticRedImage()
                assertTrue(peer.transactions.isEmpty())
                peer.assertHealthy()
                captureFeedEvidence("posting-evidence/outgoing-draft-retained-after-wallet-restore.png")
            }
        } } finally {
            previous?.let { original ->
                val current = store.loadExisting()
                if (current.publicKey != original.publicKey) store.replace(current, original)
            }
            balances.close()
        }
    }

    @Test fun walletReplacementPausesQueuedWorkAndRestoringItsOwnerResumesTheOriginalIdentity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val port = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val indexed = java.util.concurrent.atomic.AtomicReference<com.google.gson.JsonObject?>()
        val peer = ControlledFeedPeer("[]", onTransaction = { frame -> indexed.set(postingArchiveRow(postingTransaction(frame))) },
            archiveResponse = { criteria ->
                val row = indexed.get()
                if (criteria.has("sig") && criteria.get("sig").asString != row?.get("sig")?.asString) "[]"
                else row?.let { "[$it]" } ?: "[]"
            })
        val balances = mockwebserver3.MockWebServer()
        balances.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest) = mockwebserver3.MockResponse.Builder()
                .body("${System.currentTimeMillis()}-1234-${"a".repeat(64)}.snap\n").build()
        }
        balances.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
        val walletStore = AndroidWalletStore(context)
        var original: nl.davidengelen.redsquare.saito.SaitoWallet? = null
        var serving: mockwebserver3.MockWebServer? = null
        try {
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:$port/wsopen")
                .putExtra("balanceApiUrl", "http://127.0.0.1:${balances.port}/balance/")
            ActivityScenario.launch<MainActivity>(intent).use {
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                original = walletStore.loadExisting()
                org.junit.Assert.assertNotEquals("jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR", original!!.publicKey)
                ui.onNodeWithText("New post").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNode(hasSetTextAction()).performTextReplacement("Queued work belongs to the outgoing wallet")
                ui.onNodeWithText("Send").performClick()
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("Cancel queued post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("Back").performClick()
                ui.onNodeWithText("Wallet").performClick()
                ui.onNodeWithText("Import wallet").performClick()
                chooseReferenceImport()
                waitFor("Server reports zero SAITO")
                ui.onNodeWithText("Skip backup").performScrollTo().performClick()
                ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
                ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsEnabled().performClick()
                waitFor("Wallet imported")
                org.junit.Assert.assertEquals("jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR", displayedWalletAddress(ui))
                ui.onNodeWithText("Done").performClick()
                ui.onNodeWithText("Queued posts").performClick()
                waitFor("No queued posts")
                ui.onNodeWithText("Back").performClick()
                serving = peer.start(port)
                ui.waitUntil(15_000) { peer.requests.get() > 0 }
                android.os.SystemClock.sleep(7_000)
                assertTrue("A replacement identity must not send the outgoing wallet's queue", peer.transactions.isEmpty())
                captureFeedEvidence("post-queue-evidence/replacement-wallet-has-no-outgoing-queue.png")
            }
            walletStore.replace(walletStore.loadExisting(), requireNotNull(original))
            // A fresh activity reloads the restored test identity from durable storage.
            ActivityScenario.launch<MainActivity>(intent).use {
                        ui.waitUntil(60_000) { peer.transactions.isNotEmpty() }
                        val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(postingTransaction(peer.transactions.single()).envelope())
                        org.junit.Assert.assertEquals(requireNotNull(original).publicKey, post.author)
                        org.junit.Assert.assertEquals("Queued work belongs to the outgoing wallet", post.text)
                        ui.onNodeWithText("Wallet").performClick()
                        org.junit.Assert.assertEquals(requireNotNull(original).publicKey, displayedWalletAddress(ui))
                        ui.onNodeWithText("Done").performClick()
                        peer.assertHealthy()
                        captureFeedEvidence("post-queue-evidence/restored-wallet-resumes-original-queue.png")
            }
        } finally {
            original?.let { previous ->
                val current = walletStore.loadExisting()
                if (current.publicKey != previous.publicKey) walletStore.replace(current, previous)
            }
            serving?.close()
            balances.close()
        }
    }

    private fun chooseReferenceImport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        withFailureCapture({ captureNativeScreen(java.io.File(instrumentation.targetContext.cacheDir,
            "posting-evidence/import-picker-failure.png")) }) {
            val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
            val size = device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test stat -c %s files/wallet-test-documents/reference-import.json")
                .trim().toLongOrNull()
            check(size != null && size > 0) { "Reference import fixture was not provisioned" }
            chooseWalletImport(ui, "reference-import.json", "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR")
        }
    }

    @Test fun longerMultilingualPostsPreserveTheirOriginalTextOnBothApprovedBackends() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val root = instrumentation.context.assets.open("native-following-reference.json").bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root")
        }
        val text = "  Multilingual: 日本語 🙂 \"quoted\" \\ path\n".repeat(25)
        fun peerFor(backend: SaitoBackend): ControlledFeedPeer {
            val indexed = java.util.concurrent.atomic.AtomicReference<com.google.gson.JsonObject?>()
            return ControlledFeedPeer("[$root]", archiveResponse = { criteria ->
                val row = indexed.get()
                if (criteria.has("sig")) {
                    if (row?.get("sig")?.asString == criteria.get("sig").asString) "[$row]" else "[]"
                } else "[$root]"
            }, onTransaction = { frame ->
                val transaction = postingTransaction(frame)
                val envelope = transaction.envelope()
                val post = nl.davidengelen.redsquare.saito.RedSquareFeed.decode(envelope)
                org.junit.Assert.assertEquals(text + backend.id, post.text)
                org.junit.Assert.assertEquals(listOf(post.author), post.recipients)
                indexed.set(postingArchiveRow(transaction))
            })
        }
        val owner = peerFor(SaitoBackend.OWNER)
        val official = peerFor(SaitoBackend.OFFICIAL)
        owner.start().use { ownerServer -> official.start().use { officialServer ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")).use {
                for (backend in SaitoBackend.entries) {
                    if (backend == SaitoBackend.OFFICIAL) {
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText(backend.domain).performClick()
                        waitFor("Server: ${backend.domain}")
                    }
                    waitForReferencePost()
                    ui.onNodeWithText("New post").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement(text + backend.id)
                    ui.onNodeWithText("Send").performClick()
                    waitFor("Available on this server.")
                    captureFeedEvidence("posting-evidence/long-multilingual-${backend.id}.png")
                    backFromComposer()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
                }
                org.junit.Assert.assertEquals(1, owner.transactions.size)
                org.junit.Assert.assertEquals(1, official.transactions.size)
                owner.assertHealthy(); official.assertHealthy()
            }
        } }
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
            val transaction = postingTransaction(frame)
            indexed.set(postingArchiveRow(transaction))
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
