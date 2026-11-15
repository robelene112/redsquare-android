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

/** Host-separated processes exercise unfinished native editing without a Save or picker callback. */
class UnfinishedTextProcessTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }
    private val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val mode get() = InstrumentationRegistry.getArguments().getString("mode") ?: "post"
    private val profile get() = mode == "profile"
    private val body get() = if (mode == "post") "Unfinished private post 120, not explicitly saved" else "Unfinished private $mode text 120, never published"
    private val savedText get() = if (mode == "post-saved") "An explicitly saved post before later edits 120" else ""
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
        parent = reference.takeIf { mode == "reply" }, quote = reference.takeIf { mode == "quote" })
    private fun assertExplicitDraft() {
        if (profile) assertEquals(0, ProfileDraftStore(context, wallet.publicKey, AndroidBackendStore(context).load()).load().changes.size())
        else assertEquals(savedText, draft().load().text)
    }
    private fun protectedText(): String? = synchronized(SaitoLocalState.lock) {
        val files = context.noBackupFilesDir.listFiles().orEmpty().filter { it.name.startsWith("editrecovery.") && it.name.endsWith(".v1") }
        if (files.isEmpty()) return@synchronized null
        assertEquals("Only this fixture's current recovery checkpoint exists", 1, files.size)
        val file = files.single()
        assertFalse("Recovery bytes do not contain the native plaintext", file.readBytes().toString(Charsets.ISO_8859_1).contains(body))
        val contents = EncryptedLocalFile(context, file.name, "redsquare.editrecovery.v1", "editrecovery.").load() ?: return@synchronized null
        JsonParser.parseString(contents.text).asJsonObject.get("text")?.asString
    }

    private fun editWithPeer(block: (ControlledFeedPeer) -> Unit) {
        val rows = if (mode in listOf("reply", "quote")) {
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
                        mode in listOf("reply", "quote") -> {
                            ui.clickVisiblePostBody(reference.text)
                            ui.waitUntil(10_000) { ui.onNodeWithText("Conversation").isDisplayed() }
                            if (mode == "reply") ui.replyToPost(reference.id) else ui.clickVisiblePostContent("Quote")
                        }
                        else -> ui.onNode(hasText("New post") and hasClickAction()).performClick()
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
        editWithPeer { peer ->
            if (savedText.isNotEmpty()) {
                ui.onNode(editor).performTextReplacement(savedText)
                ui.onNodeWithText("Save draft").performClick()
                ui.waitUntil(10_000) { ui.onNode(hasText("New post") and hasClickAction()).isDisplayed() }
                ui.onNode(hasText("New post") and hasClickAction()).performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(editor).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                ui.onNode(editor).assertEditableTextEquals(savedText)
            }
            ui.onNode(editor).performTextReplacement(body)
            ui.onNode(editor).assertEditableTextEquals(body)
            if (!profile) ui.onNodeWithText("Unsaved changes").assertExists()
            assertExplicitDraft()
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
        assertNotEquals(row.get("pid").asInt, Process.myPid())
        assertEquals(row.get("walletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(row.get("backend").asString, AndroidBackendStore(context).load().id)
        editWithPeer { peer ->
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
        restoreProcessFixtureFiles(context, originals, names() + row.getAsJsonArray("savedFiles").map { it.asString }, row.getAsJsonArray("savedFiles"))
        row.getAsJsonArray("preferences").forEach { entry ->
            val pref = entry.asJsonObject; val name = pref.get("name").asString; val key = pref.get("key").asString
            check(name to key in preferences())
            check(context.getSharedPreferences(name, 0).edit().apply {
                if (pref.has("value")) putBoolean(key, pref.get("value").asBoolean) else remove(key)
            }.commit())
        }
        assertEquals(row.get("originalWalletDigest").asString, digest(AndroidWalletStore(context).loadExisting().exportJson()))
        check(originals.deleteRecursively()); check(checkpoint.delete())
    }
}
