package nl.davidengelen.redsquare

import android.content.Intent
import android.system.Os
import android.system.OsConstants
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProfileEditPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun dismissTypingForWallet(scenario: ActivityScenario<MainActivity>, text: String) {
        scenario.onActivity { activity ->
            activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        ui.waitUntil(10_000) { ui.onNodeWithText("Wallet").isDisplayed() }
        ui.onNode(hasSetTextAction()).assertTextEquals(text)
    }

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("profilemeta.") || it.name.startsWith("profiledraft.")
        }?.forEach {
            check(it.delete()) { "Could not reset emulator profile fixtures" }
        }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    @Test fun aLateDraftSaveFailureCanBeRetriedInTheSameEditorAfterStorageRecovers() {
        val owner = AndroidWalletStore(context).loadOrCreate().publicKey
        val directory = context.noBackupFilesDir
        val original = Os.stat(directory.path)
        check(original.st_uid == android.os.Process.myUid())
        val permissions = original.st_mode and 0xfff
        val draft = File(directory, "profiledraft.${SaitoBackend.OWNER.id}.$owner.v1")
        val initial = "My original unsent profile draft"
        val edited = "My recovered profile draft 🌱"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val peer = ControlledFeedPeer("[]", profileArchiveResponse = { "[]" }, archiveResponse = { "[]" })
        fun openOwnEditor() {
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
            ui.onNodeWithText("Your profile").performClick()
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("Edit profile").performClick()
            ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        }
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            ActivityScenario.launch<MainActivity>(intent).use {
                captureFailure("profile-late-save-retry-failure.png") {
                    openOwnEditor()
                    ui.onNode(hasSetTextAction()).performTextReplacement(initial)
                    ui.onNodeWithText("Save draft").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    check(draft.isFile && draft.length() > 0)
                    val prior = digest.digest(draft.readBytes())
                    ui.onNodeWithText("Edit profile").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement(edited)
                    var primary: Throwable? = null
                    try {
                        // Known files remain readable/writable; only the later directory O_RDONLY open fails.
                        Os.chmod(directory.path, OsConstants.S_IWUSR or OsConstants.S_IXUSR)
                        ui.onNodeWithText("Save draft").performClick()
                        val error = context.getString(R.string.profile_draft_error)
                        ui.waitUntil(10_000) { ui.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText(error).assertIsDisplayed()
                        ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                            SemanticsProperties.EditableText, AnnotatedString(edited))).assertIsEnabled()
                        ui.onNodeWithText("Save draft").assertIsEnabled()
                        // A generic pre-write failure is insufficient: the owned synthetic record changed.
                        assertFalse(prior.contentEquals(digest.digest(draft.readBytes())))
                        assertTrue(peer.transactions.isEmpty())
                        capture("profile-late-save-failed.png", error)
                    } catch (failure: Throwable) { primary = failure; throw failure }
                    finally {
                        try { Os.chmod(directory.path, permissions) }
                        catch (cleanup: Throwable) { if (primary != null) primary.addSuppressed(cleanup) else throw cleanup }
                    }
                    ui.onNodeWithText("Save draft").assertIsEnabled().performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onAllNodes(hasSetTextAction()).assertCountEquals(0)
                    ui.onAllNodesWithText(context.getString(R.string.profile_draft_error)).assertCountEquals(0)
                    assertEquals(owner, AndroidWalletStore(context).loadExisting().publicKey)
                    assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                }
            }
            ActivityScenario.launch<MainActivity>(intent).use {
                captureFailure("profile-late-save-recovery-failure.png") {
                    openOwnEditor()
                    ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
                        SemanticsProperties.EditableText, AnnotatedString(edited))).assertIsEnabled()
                    capture("profile-late-save-recovered.png", edited)
                    assertEquals(owner, AndroidWalletStore(context).loadExisting().publicKey)
                    assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                    assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                }
            }
        }
    }

    @Test fun viewingAProfileShowsItsVerifiedBioAndGeneratedSaitoAvatar() {
        val feed = instrumentation.context.assets.open("native-feed-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val profiles = instrumentation.context.assets.open("native-profile-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val author = profiles.getAsJsonObject("snapshot").get("field2").asString
        assertEquals(author, feed.get("field2").asString)
        val peer = ControlledFeedPeer("[$feed]", profileArchiveResponse = { criteria ->
            assertEquals(author, criteria.get("field2").asString)
            assertEquals(100, criteria.get("limit").asInt)
            JsonArray().apply {
                add(profiles.getAsJsonObject("newerLegacy")); add(profiles.getAsJsonObject("snapshot"))
            }.toString()
        }, archiveResponse = { criteria ->
            assertEquals("RedSquare", criteria.get("field1").asString)
            assertEquals(20, criteria.get("limit").asInt)
            assertEquals(1, criteria.get("flagged_ne").asInt)
            if (criteria.has("field2")) assertEquals(author, criteria.get("field2").asString)
            else assertEquals("", criteria.get("field4").asString)
            "[$feed]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    ui.waitUntil(20_000) { peer.requests.get() >= 2 &&
                        ui.onAllNodesWithText(context.getString(R.string.feed_loading)).fetchSemanticsNodes().isEmpty() &&
                        ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    val post = hasText("Native Red Square reference post")
                    ui.onNode(hasScrollAction()).performScrollToNode(post)
                    val identity = hasText(author) and hasAnyAncestor(hasAnyChild(post))
                    ui.onNode(identity).assertIsDisplayed().performClick()
                    ui.waitUntil(20_000) { ui.onAllNodesWithText("Signed bio 🌱\nSecond line").fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Signed bio 🌱\nSecond line").assertIsDisplayed()
                    ui.onNodeWithContentDescription("Generated profile picture").assertIsDisplayed()
                    ui.onAllNodesWithText("Later legacy bio").assertCountEquals(0)
                    capture("verified-profile.png")
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } catch (failure: Throwable) {
                    try { capture("first-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                }
            }
        }
    }

    @Test fun profileHistoryRemainsReachableWithAFullMultilineBio() {
        val feed = instrumentation.context.assets.open("native-feed-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val row = instrumentation.context.assets.open("native-profile-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("longBio") }
        val author = row.get("field2").asString
        val bio = (1..40).joinToString("\n") { "Public biography line $it" }
        val peer = ControlledFeedPeer("[$feed]", profileArchiveResponse = { "[$row]" }, archiveResponse = { criteria ->
            assertEquals("RedSquare", criteria.get("field1").asString)
            if (criteria.has("field2")) assertEquals(author, criteria.get("field2").asString)
            "[$feed]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    ui.waitUntil(20_000) { peer.requests.get() >= 2 &&
                        ui.onAllNodesWithText(context.getString(R.string.feed_loading)).fetchSemanticsNodes().isEmpty() &&
                        ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    val post = hasText("Native Red Square reference post")
                    ui.onNode(hasScrollAction()).performScrollToNode(post)
                    ui.onNode(hasText(author) and hasAnyAncestor(hasAnyChild(post))).performClick()
                    ui.waitUntil(20_000) { ui.onAllNodesWithText(bio).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText(bio).assertExists()
                    capture("long-bio-before-scroll.png")
                    scrollUntilDisplayed("Posts")
                    ui.onNodeWithText("Posts").assertIsDisplayed()
                    ui.onNodeWithText("Replies").assertIsDisplayed()
                    ui.onNodeWithText("Likes").assertIsDisplayed()
                    scrollUntilDisplayed("Native Red Square reference post")
                    ui.onNode(post).assertIsDisplayed()
                    capture("long-bio-reachable-history.png")
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } catch (failure: Throwable) {
                    try { capture("long-bio-first-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                }
            }
        }
    }

    @Test fun cachedProfileBioSurvivesAFreshActivityWhenSelectedBackendIsUnavailable() {
        val feed = instrumentation.context.assets.open("native-feed-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow") }
        val row = instrumentation.context.assets.open("native-profile-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("snapshot") }
        val author = row.get("field2").asString
        val bio = "Signed bio 🌱\nSecond line"
        val peer = ControlledFeedPeer("[$feed]", profileArchiveResponse = { criteria ->
            assertEquals(author, criteria.get("field2").asString)
            "[$row]"
        }, archiveResponse = { criteria ->
            assertEquals("RedSquare", criteria.get("field1").asString)
            if (criteria.has("field2")) assertEquals(author, criteria.get("field2").asString)
            "[$feed]"
        })
        val server = peer.start()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
        server.use {
            ActivityScenario.launch<MainActivity>(intent).use {
                try {
                    ui.waitUntil(20_000) { peer.requests.get() >= 2 &&
                        ui.onAllNodesWithText(context.getString(R.string.feed_loading)).fetchSemanticsNodes().isEmpty() &&
                        ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    openReferenceAuthor(author)
                    ui.waitUntil(20_000) { ui.onAllNodesWithText(bio).fetchSemanticsNodes().isNotEmpty() &&
                        ui.onAllNodesWithText(context.getString(R.string.profile_details_loading)).fetchSemanticsNodes().isEmpty() &&
                        ui.onAllNodesWithText(context.getString(R.string.profile_loading)).fetchSemanticsNodes().isEmpty() }
                    ui.onNodeWithText(bio).assertIsDisplayed()
                    ui.onNodeWithText("Refresh").assertIsEnabled()
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    peer.assertHealthy()
                    assertTrue(peer.transactions.isEmpty())
                } catch (failure: Throwable) {
                    try { capture("cache-online-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                }
            }
        }
        ActivityScenario.launch<MainActivity>(intent).use {
            try {
                ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                openReferenceAuthor(author)
                ui.waitUntil(25_000) {
                    ui.onAllNodesWithText(context.getString(R.string.profile_details_error)).fetchSemanticsNodes().isNotEmpty()
                }
                ui.onNodeWithText(bio).assertIsDisplayed()
                ui.onNodeWithContentDescription("Generated profile picture").assertIsDisplayed()
                assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                capture("cached-profile-offline.png")
            } catch (failure: Throwable) {
                try { capture("cache-offline-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                throw failure
            }
        }
    }

    @Test fun aSavedOwnBioDraftReturnsAfterAFreshActivityWithoutPublishing() {
        val bio = "A private draft 🌱\nNot published yet"
        val peer = ControlledFeedPeer("[]", profileArchiveResponse = { "[]" }, archiveResponse = { "[]" })
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            ActivityScenario.launch<MainActivity>(intent).use {
                try {
                    ui.waitUntil(20_000) { peer.requests.get() >= 2 &&
                        ui.onAllNodesWithText(context.getString(R.string.feed_loading)).fetchSemanticsNodes().isEmpty() &&
                        ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    ui.onNodeWithText("Your profile").assertIsDisplayed().performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Edit profile").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement(bio)
                    ui.onNodeWithText("Save draft").performClick()
                    ui.waitUntil(10_000) {
                        ui.onAllNodes(hasText("Edit profile") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onAllNodesWithText(bio).assertCountEquals(0)
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } catch (failure: Throwable) {
                    try { capture("bio-draft-first-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                }
            }
            ActivityScenario.launch<MainActivity>(intent).use {
                try {
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Your profile").performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Edit profile").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).assert(
                        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(bio)))
                    ui.onNodeWithText("Save draft").assertIsEnabled()
                    capture("restored-bio-draft.png")
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } catch (failure: Throwable) {
                    try { capture("bio-draft-restoration-failure.png") } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                }
            }
        }
    }

    @Test fun walletImportPreviewWarnsAboutAFreshlyTypedBioBeforeAnySaveOrReplacement() {
        val filename = "00-profile-import.json"
        val device = UiDevice.getInstance(instrumentation)
        check(device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test stat -c %s files/wallet-test-documents/00-profile-import.json")
            .trim().toLongOrNull()?.let { it > 0 } == true) { "Profile import fixture was not provisioned" }
        val store = AndroidWalletStore(context)
        val original = store.loadOrCreate()
        val outgoing = store.replace(original, SaitoWallet.create())
        val incoming = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR" // Independently pinned public scalar1 fixture.
        val bio = "Fresh unsaved bio 🌱\nKeep with my original wallet"
        var primary: Throwable? = null
        try {
            assertNotEquals(outgoing.publicKey, incoming)
            val peer = ControlledFeedPeer("[]", profileArchiveResponse = { "[]" }, archiveResponse = { "[]" })
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")).use { scenario ->
                    try {
                        ui.waitUntil(20_000) { peer.requests.get() >= 2 &&
                            ui.onAllNodesWithText(context.getString(R.string.feed_loading)).fetchSemanticsNodes().isEmpty() &&
                            ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                        ui.onNodeWithText("Your profile").performClick()
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithText("Edit profile").performClick()
                        ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNode(hasSetTextAction()).performTextReplacement(bio)
                        dismissTypingForWallet(scenario, bio)
                        ui.onNodeWithText("Wallet").performClick()
                        assertEquals(outgoing.publicKey, displayedWalletAddress(ui))
                        ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                        chooseWalletImport(ui, filename, incoming)
                        val selected = context.getString(R.string.wallet_import_address)
                        ui.waitUntil(15_000) { ui.onAllNodesWithContentDescription(selected).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithContentDescription(selected).assertTextEquals(incoming)
                        ui.onNodeWithText(context.getString(R.string.wallet_import_pending_work)).performScrollTo().assertIsDisplayed()
                        ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
                        capture("unsaved-bio-import-preview.png")
                        assertEquals(outgoing.publicKey, displayedWalletAddress(ui))
                        ui.onNodeWithText("Done").performClick()
                        ui.onNode(hasSetTextAction()).assert(
                            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(bio)))
                        assertTrue(peer.transactions.isEmpty())
                        peer.assertHealthy()
                    } catch (failure: Throwable) {
                        try { capture("unsaved-bio-import-preview-failure.png") }
                        catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                        throw failure
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            try {
                val active = store.loadExisting()
                if (active.publicKey != original.publicKey) {
                    check(active.publicKey == outgoing.publicKey)
                    store.replace(active, original)
                }
            } catch (cleanup: Throwable) {
                if (primary != null) primary.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    @Test fun failedBioSavingKeepsWalletImportClosedAndCanBeRetriedWithoutLosingWork() {
        val device = UiDevice.getInstance(instrumentation)
        check(device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test stat -c %s files/wallet-test-documents/00-profile-import.json")
            .trim().toLongOrNull()?.let { it > 0 } == true) { "Profile import fixture was not provisioned" }
        val owner = AndroidWalletStore(context).loadOrCreate().publicKey
        val initial = "Previously saved bio"
        val edited = "Keep this newer bio 🌱\nA failed save must not start import"
        val draft = File(context.noBackupFilesDir, "profiledraft.owner.$owner.v1")
        val preserved = File(context.cacheDir, "profile-import-preserved-draft")
        check(!preserved.exists()) { "Previous storage fixture was not restored" }
        val peer = ControlledFeedPeer("[]", profileArchiveResponse = { "[]" }, archiveResponse = { "[]" })
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                var primary: Throwable? = null
                try {
                    ui.waitUntil(20_000) { peer.requests.get() >= 2 &&
                        ui.onAllNodesWithText(context.getString(R.string.feed_loading)).fetchSemanticsNodes().isEmpty() &&
                        ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    ui.onNodeWithText("Your profile").performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Edit profile").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).performTextReplacement(initial)
                    ui.onNodeWithText("Save draft").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Edit profile").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).assert(
                        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(initial)))
                    ui.onNode(hasSetTextAction()).performTextReplacement(edited)
                    // Obstruct the actual AtomicFile base after loading. Preserve the previous encrypted file.
                    check(draft.isFile && draft.renameTo(preserved))
                    check(draft.mkdir())
                    dismissTypingForWallet(scenario, edited)
                    ui.onNodeWithText("Wallet").performClick()
                    ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                    val failed = context.getString(R.string.wallet_import_work_save_failed)
                    ui.waitUntil(10_000) { ui.onAllNodesWithText(failed).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText(failed).performScrollTo().assertIsDisplayed()
                    ui.onNodeWithText("Import wallet").assertIsEnabled()
                    assertEquals(context.packageName, device.currentPackageName)
                    ui.onAllNodesWithContentDescription(context.getString(R.string.wallet_import_address)).assertCountEquals(0)
                    assertEquals(owner, displayedWalletAddress(ui))
                    capture("failed-bio-save-import-blocked.png")
                    ui.onNodeWithText("Done").performClick()
                    ui.onNode(hasSetTextAction()).assert(
                        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(edited)))
                    check(draft.delete() && preserved.renameTo(draft))
                    ui.onNodeWithText("Wallet").performClick()
                    ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                    chooseWalletImport(ui, "00-profile-import.json", "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR")
                    val selected = context.getString(R.string.wallet_import_address)
                    ui.waitUntil(15_000) {
                        ui.onAllNodesWithContentDescription(selected).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                    }
                    ui.onNodeWithContentDescription(selected).assertTextEquals("jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR")
                    assertEquals(owner, displayedWalletAddress(ui))
                    ui.onNodeWithText("Done").performClick()
                    ui.onNode(hasSetTextAction()).assert(
                        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(edited)))
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } catch (failure: Throwable) {
                    primary = failure
                    try { capture("failed-bio-save-import-failure.png") }
                    catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                } finally {
                    try {
                        if (preserved.exists()) {
                            check(!draft.exists() || draft.isDirectory && draft.delete())
                            check(preserved.renameTo(draft))
                        }
                    } catch (cleanup: Throwable) {
                        if (primary != null) primary.addSuppressed(cleanup) else throw cleanup
                    }
                }
            }
            ActivityScenario.launch<MainActivity>(intent).use {
                try {
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("New post") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Your profile").performClick()
                    ui.waitUntil(20_000) { ui.onAllNodes(hasText("Edit profile") and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNodeWithText("Edit profile").performClick()
                    ui.waitUntil(10_000) { ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasSetTextAction()).assert(
                        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(edited)))
                    assertEquals(owner, AndroidWalletStore(context).loadExisting().publicKey)
                    capture("failed-bio-save-import-recovered.png")
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                } catch (failure: Throwable) {
                    try { capture("failed-bio-save-import-recovery-failure.png") }
                    catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                    throw failure
                }
            }
        }
    }

    private fun openReferenceAuthor(author: String) {
        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
        val post = hasText("Native Red Square reference post")
        val identity = hasText(author) and hasAnyAncestor(hasAnyChild(post))
        ui.waitUntil(15_000) {
            try {
                if (!ui.onNode(post).isDisplayed()) ui.onNode(hasScrollAction()).performScrollToNode(post)
                ui.onNode(identity).assertIsDisplayed()
                true
            } catch (_: AssertionError) { false }
        }
        ui.onNode(identity).performClick()
    }

    private fun scrollUntilDisplayed(text: String) {
        repeat(10) {
            if (ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() && ui.onNodeWithText(text).isDisplayed()) return
            ui.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        }
        ui.onNodeWithText(text).assertIsDisplayed()
    }

    private fun captureFailure(name: String, block: () -> Unit) {
        withFailureCapture({ capture(name) }, block)
    }

    private fun capture(name: String, expectedText: String? = null) {
        ui.waitForIdle()
        captureNativeScreen(File(context.cacheDir, "profile-edit-evidence/$name"), expectedText)
    }
}
