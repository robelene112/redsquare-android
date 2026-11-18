package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.feed.PostDraftStore
import nl.davidengelen.redsquare.feed.ProfileDraftStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Host-separated processes recover the latest editing without a final Save or picker callback. */
class UnfinishedTextProcessTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val mode get() = InstrumentationRegistry.getArguments().getString("mode") ?: "post"
    private val profile get() = mode.startsWith("profile")
    private val separated get() = mode.endsWith("-separated")
    private val body get() = if (mode == "post") "Unfinished private post 120, not explicitly saved" else "Unfinished private $mode text 120, never published"
    private val savedText get() = if (mode.endsWith("-saved")) "An explicitly saved $mode before later edits 120" else ""
    private val reference by lazy {
        val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-following-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root") }
        RedSquareFeed.readArchive("[$root]").posts.single()
    }
    private val checkpoint get() = File(context.cacheDir, "unfinished-text-checkpoint.json")
    private val originals get() = File(context.noBackupFilesDir, "unfinished-text-originals")
    private val prefixes = listOf("postdraft.", "profiledraft.", "editrecovery.", "feed.", "conversation.", "profile.", "profilemeta.", "profiledetails.", "avatarprofiles.", "displaynames.")
    private fun names() = (listOf("wallet.v1", "wallet.v1.bak", "wallet.v1.new", "wallet.recovery.v1",
        "wallet.recovery.v1.bak", "wallet.recovery.v1.new") + context.noBackupFilesDir.listFiles().orEmpty()
        .filter { it.isFile && prefixes.any(it.name::startsWith) }.map { it.name }).toSet()
    private fun preferences() = listOf("name-directory" to "enabled", "notification-permission" to "offered",
        "notification-permission" to "social-offered:${wallet.publicKey}", "wallet-backup-reminders" to "hidden:${wallet.publicKey}")
    private fun read() = JsonParser.parseString(checkpoint.readText()).asJsonObject
    private fun write(row: JsonObject) = checkpoint.outputStream().use { it.write(row.toString().toByteArray()); it.fd.sync() }
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "unfinished-text-evidence/$mode/$name"))
    private val editor get() = hasSetTextAction() and isEnabled()
    private fun draft() = PostDraftStore(context, wallet.publicKey, AndroidBackendStore(context).load(),
        parent = reference.takeIf { mode.startsWith("reply") }, quote = reference.takeIf { mode.startsWith("quote") })
    private fun assertSeparatedOriginal(row: JsonObject) {
        if (!separated) return
        val id = row.get("originalSignature").asString
        val saved = PostDraftStore.recovered(context, wallet.publicKey, AndroidBackendStore(context).load(), id).load()
        assertEquals(row.get("originalDigest").asString, digest(requireNotNull(saved.transaction).encoded()))
        assertFalse(saved.available)
        assertFalse(draft().hasSubmission(id))
        assertEquals(1, saved.images.size)
        val post = RedSquareFeed.decode(saved.transaction.envelope())
        assertEquals(if (mode.startsWith("reply")) reference.id else "", post.parentId)
        assertEquals(if (mode.startsWith("quote")) reference.id else null, post.quoted?.id)
    }
    private fun assertExplicitDraft() {
        if (profile) {
            val saved = ProfileDraftStore(context, wallet.publicKey, AndroidBackendStore(context).load()).load()
            if (savedText.isEmpty()) assertEquals(0, saved.changes.size())
            else assertEquals(savedText, saved.changes.get("description").asString)
        }
        else assertEquals(savedText, draft().load().text)
    }
    private fun protectedText(): String? {
        val snapshot = unfinishedTextCheckpoint(context) ?: return null
        assertFalse("Recovery bytes do not contain the native plaintext", snapshot.encrypted.toString(Charsets.ISO_8859_1).contains(body))
        return snapshot.text
    }

    private fun exerciseStorageFailure() {
        ui.waitUntil(5000) { protectedText() == body }
        val original = requireNotNull(unfinishedTextCheckpoint(context))
        val latest = "$body, latest edit after storage failed"
        val warning = context.getString(R.string.unfinished_text_recovery_failed)
        var primary: Throwable? = null
        try {
            synchronized(SaitoLocalState.lock) { check(original.file.delete()); check(original.file.mkdir()) }
            ui.onNode(editor).performTextReplacement(latest)
            ui.waitUntil(5000) { ui.onAllNodesWithText(warning).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNode(editor).assertEditableTextEquals(latest)
            ui.onNodeWithText(warning).assertIsDisplayed()
            ui.onNodeWithText("Saved on this device").assertDoesNotExist()
            assertExplicitDraft()
            capture("storage-failure-current-text.png")
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                synchronized(SaitoLocalState.lock) {
                    check(original.file.isDirectory && original.file.delete())
                    original.file.writeBytes(original.encrypted)
                    assertTrue(original.encrypted.contentEquals(original.file.readBytes()))
                }
            })
        }
        assertEquals("The previous protected work survives the failed write", body, protectedText())
        ui.onNodeWithText("Retry").assertIsDisplayed().assertIsEnabled().performClick()
        ui.waitUntil(5000) { protectedText() == latest }
        ui.waitUntil(5000) { ui.onAllNodesWithText(warning).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
        ui.onNode(editor).assertEditableTextEquals(latest)
        assertExplicitDraft()
        capture("storage-repaired-latest-text.png")
        File(context.cacheDir, "unfinished-text-evidence/$mode/storage-retry.json").writeText(JsonObject().apply {
            addProperty("previousProtectedText", body); addProperty("repairedProtectedText", protectedText())
            addProperty("explicitDraftText", draft().load().text); addProperty("priorEncryptedBytesPreserved", true)
        }.toString())
        ui.onNode(editor).performTextReplacement(body)
        ui.waitUntil(5000) { protectedText() == body }
    }

    private fun editWithPeer(block: (ControlledFeedPeer) -> Unit) {
        val rows = if ((mode.startsWith("reply") || mode.startsWith("quote"))) {
            val root = InstrumentationRegistry.getInstrumentation().context.assets.open("native-following-reference.json")
                .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("root") }
            "[$root]"
        } else "[]"
        val peer = ControlledFeedPeer(rows, profileArchiveResponse = { "[]" },
            archiveResponse = { criteria -> if (criteria.get("field1").asString == "RedSquare") rows else "[]" })
        peer.start().use { server ->
            val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                withFailureCapture({ capture("failure.png") }) {
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and hasClickAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    when {
                        profile -> {
                            ui.onNode(hasText("Your profile") and hasClickAction()).performClick()
                            ui.waitUntil(15_000) { ui.onAllNodes(hasText("Edit profile") and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                            ui.onNode(hasText("Edit profile") and hasClickAction()).performClick()
                        }
                        (mode.startsWith("reply") || mode.startsWith("quote")) -> {
                            ui.clickVisiblePostBody(reference.text)
                            ui.waitUntil(10_000) { ui.onNodeWithText("Conversation").isDisplayed() }
                            if (mode.startsWith("reply")) ui.replyToPost(reference.id) else ui.clickVisiblePostContent("Quote")
                        }
                        else -> ui.onNode(hasText("New post") and hasClickAction()).performClick()
                    }
                    if (separated && read().get("phase").asString == "prepared") {
                        ui.waitUntil(10_000) { ui.onAllNodes(hasText("Start another draft") and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                        ui.onNodeWithText("Start another draft").performScrollTo().performClick()
                    }
                    ui.waitUntil(10_000) { ui.onAllNodes(editor).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                    ui.onNode(editor).performScrollTo()
                    block(peer)
                }
            }
        }
    }

    @Test fun backgroundWithUnfinishedTextThenTerminateTheActualProcess() {
        check(!checkpoint.exists() && !originals.exists())
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val backend = AndroidBackendStore(context).load()
        val row = JsonObject().apply {
            addProperty("phase", "prepared"); addProperty("pid", Process.myPid()); addProperty("backend", backend.id)
            addProperty("mode", mode)
            addProperty("originalWalletDigest", digest(previous.exportJson())); addProperty("walletDigest", digest(wallet.exportJson()))
            add("savedFiles", snapshotProcessFixtureFiles(context, originals, names()))
            add("preferences", JsonArray().apply { preferences().forEach { (name, key) ->
                val pref = context.getSharedPreferences(name, 0)
                add(JsonObject().apply { addProperty("name", name); addProperty("key", key)
                    if (pref.contains(key)) addProperty("value", pref.getBoolean(key, false)) })
            } })
        }
        write(row)
        synchronized(SaitoLocalState.lock) {
            names().filterNot { it.startsWith("wallet.") }.forEach { name ->
                val file = File(context.noBackupFilesDir, name); check(!file.exists() || file.delete())
            }
        }
        wallets.replace(previous, wallet)
        preferences().forEach { (name, key) -> check(context.getSharedPreferences(name, 0).edit().putBoolean(key, name != "name-directory").commit()) }
        if (separated) {
            val image = InstrumentationRegistry.getInstrumentation().context.assets.open("native-images-reference.json")
                .reader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("images").get("red").asString }
            val parent = reference.takeIf { mode.startsWith("reply") }
            val quote = reference.takeIf { mode.startsWith("quote") }
            val text = "Retain this earlier uncertain $mode original"
            val recipients = (parent ?: quote)?.let { (listOf(it.author) + it.recipients).distinct() }.orEmpty()
            val original = SaitoTransaction.create(wallet, RedSquarePosts.message(text, listOf(image), parent, quote),
                1_700_000_000_000L, recipients)
            draft().save(PostDraftStore.Saved(text, original, images = listOf(image)))
            row.addProperty("originalSignature", original.id)
            row.addProperty("originalDigest", digest(original.encoded()))
            write(row)
        }
        editWithPeer { peer ->
            assertSeparatedOriginal(row)
            if (savedText.isNotEmpty()) {
                ui.onNode(editor).performTextReplacement(savedText)
                ui.onNodeWithText("Save draft").performClick()
                val reopen = hasText(if (profile) "Edit profile" else "New post") and hasClickAction()
                ui.waitUntil(10_000) { ui.onNode(reopen).isDisplayed() }
                ui.onNode(reopen).performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(editor).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNode(editor).assertEditableTextEquals(savedText)
            }
            ui.onNode(editor).performTextReplacement(body)
            ui.onNode(editor).assertEditableTextEquals(body)
            if (!profile) ui.onNodeWithText("Unsaved changes").assertExists()
            assertExplicitDraft()
            if (mode == "storage-failure") exerciseStorageFailure()
            if (mode == "continuous") {
                ui.waitUntil(5000) { protectedText() == body }
                val action = requireNotNull(ui.onNode(editor).fetchSemanticsNode().config[SemanticsActions.SetText].action)
                val start = android.os.SystemClock.elapsedRealtime()
                var updates = 0
                while (android.os.SystemClock.elapsedRealtime() - start < 1800) {
                    val text = "Continuous 120 edit ${updates++}, still typing"
                    InstrumentationRegistry.getInstrumentation().runOnMainSync { check(action(AnnotatedString(text))) }
                    Thread.sleep(40)
                }
                // Read immediately, before any final idle interval can hide a starvation defect.
                val protected = protectedText()
                assertTrue("Normal typing checkpoints progress before any pause: $protected", protected?.startsWith("Continuous 120 edit ") == true)
                File(context.cacheDir, "unfinished-text-evidence/$mode/continuous-checkpoint.json").apply { parentFile!!.mkdirs() }.writeText(JsonObject().apply {
                    addProperty("updates", updates); addProperty("elapsedMillis", android.os.SystemClock.elapsedRealtime() - start)
                    addProperty("checkpointText", protected)
                }.toString())
                ui.onNode(editor).performTextReplacement(body)
                ui.onNode(editor).assertEditableTextEquals(body)
            }
            capture("unfinished-before-background.png")
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome()
            Thread.sleep(800)
            assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
            row.addProperty("phase", "pending"); write(row)
            Process.killProcess(Process.myPid())
            error("Expected actual process termination")
        }
    }

    @Test fun coldProcessRestoresTheExactUnfinishedTextWithoutSavingOrPublishing() {
        val row = read(); assertEquals("pending", row.get("phase").asString)
        assertEquals(mode, row.get("mode").asString)
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        assertEquals(row.get("walletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(row.get("backend").asString, AndroidBackendStore(context).load().id)
        editWithPeer { peer ->
            assertSeparatedOriginal(row)
            ui.onNode(editor).assertEditableTextEquals(body)
            if (!profile) {
                ui.onNodeWithText("Unsaved changes").assertExists()
                ui.onNodeWithText("Saved on this device").assertDoesNotExist()
            }
            assertExplicitDraft()
            assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
            capture("unfinished-cold-restored.png")
            row.addProperty("phase", "recovered"); row.addProperty("recoveredPid", Process.myPid()); write(row)
        }
    }

    @Test fun restoreOriginalEncryptedInstallationFiles() {
        if (!checkpoint.exists()) return
        val row = read()
        var filesRestored = false
        var preferencesRestored = false
        var walletRestored = false
        cleanupAfterTest(null, {
            if (row.has("originalSignature")) androidx.work.WorkManager.getInstance(context)
                .cancelUniqueWork("post:${row.get("originalSignature").asString}").result.get(5, java.util.concurrent.TimeUnit.SECONDS)
        }, {
            restoreProcessFixtureFiles(context, originals, names() + row.getAsJsonArray("savedFiles").map { it.asString }, row.getAsJsonArray("savedFiles"))
            filesRestored = true
        }, {
            val operations: List<() -> Unit> = row.getAsJsonArray("preferences").map { entry -> {
                val pref = entry.asJsonObject; val name = pref.get("name").asString; val key = pref.get("key").asString
                check(name to key in preferences())
                check(context.getSharedPreferences(name, 0).edit().apply {
                    if (pref.has("value")) putBoolean(key, pref.get("value").asBoolean) else remove(key)
                }.commit())
            } }
            cleanupAfterTest(null, *operations.toTypedArray())
            preferencesRestored = true
        }, {
            assertEquals(row.get("originalWalletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
            walletRestored = true
        }, {
            // Keep recovery snapshots if any restoration failed.
            if (filesRestored && preferencesRestored && walletRestored) {
                check(originals.deleteRecursively()); check(checkpoint.delete())
            }
        })
    }
}
