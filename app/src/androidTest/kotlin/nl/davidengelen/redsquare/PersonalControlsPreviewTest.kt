package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PersonalControlsPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }

    @Before fun prepareAvailableHistory() {
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        val prefixes = listOf("feed.", "conversation.", "profile.", "following.", "followingfeed.", "search.",
            "quoteoriginal.", "authorposts.", "personal.")
        context.noBackupFilesDir.listFiles()?.filter { file -> prefixes.any(file.name::startsWith) }
            ?.forEach { check(it.delete()) }
    }

    @After fun clearPersonalChoices() {
        context.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("personal.") }
            ?.forEach { check(it.delete()) }
    }

    @Test fun mutingSurvivesBackendChangesAndOfflineReopeningAndUnmutingRestoresCachedPosts() {
        val reference = readReference("native-following-reference.json")
        val root = reference.getAsJsonObject("root")
        val other = reference.getAsJsonObject("otherRoot")
        val newer = readReference("native-profile-paging-reference.json").getAsJsonArray("recentPosts")[0].asJsonObject
        val author = root.get("field2").asString
        fun peer(post: JsonObject) = ControlledFeedPeer("[$post,$other]", archiveResponse = { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            when (criteria.get("field2")?.asString) {
                null -> "[$post,$other]"
                author -> "[$post]"
                other.get("field2").asString -> "[$other]"
                else -> error("Unexpected author")
            }
        })
        val owner = peer(root)
        val official = peer(newer)
        owner.start().use { ownerServer ->
            official.start().use { officialServer ->
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")
                var address = ""
                withOpenApp(intent, "mute-first-failure") {
                    waitForEnabled("Refresh")
                    showPost("Native Red Square reference post")
                    ui.onNodeWithText("Wallet").performClick()
                    address = displayedWalletAddress(ui)
                    ui.onNodeWithText("Done").performClick()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText(author))
                    ui.onNodeWithText(author).assertIsDisplayed().performClick()
                    waitFor("Profile")
                    waitFor("Mute account")
                    muteAccount()
                    ui.onNodeWithText("Back").performClick()
                    showPost("Another account reference post")
                    assertPostAbsent("Native Red Square reference post", "Another account reference post")
                    ui.onNodeWithText("Settings").performClick()
                    waitFor("saito.io")
                    ui.onNodeWithText("saito.io").performClick()
                    waitFor("Server: saito.io")
                    waitForEnabled("Refresh")
                    showPost("Another account reference post")
                    assertPostAbsent("Profile newer post 20", "Another account reference post")
                    captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/muted-official.png"), "Another account reference post")
                }
                owner.assertHealthy()
                official.assertHealthy()
                ownerServer.close()
                officialServer.close()
                withOpenApp(intent, "mute-offline-failure") {
                    waitFor("Server: saito.io")
                    waitFor("Server unavailable")
                    showPost("Another account reference post")
                    assertPostAbsent("Profile newer post 20", "Another account reference post")
                    ui.onNodeWithText("Settings").performClick()
                    waitFor("Muted and blocked accounts")
                    ui.onNodeWithText("Muted and blocked accounts").performClick()
                    waitFor(author)
                    waitFor("Unmute account")
                    captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/muted-account-offline.png"), author)
                    ui.onNodeWithText("Unmute account").performClick()
                    waitFor("No muted or blocked accounts.")
                    ui.onNodeWithText("Done").performClick()
                    showPost("Profile newer post 20")
                    captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/unmuted-cached-post.png"), "Profile newer post 20")
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(address, displayedWalletAddress(ui))
                    ui.onNodeWithText("Done").performClick()
                }
                assertTrue(owner.clientKeys.isNotEmpty() && official.clientKeys.isNotEmpty())
                assertTrue((owner.clientKeys + official.clientKeys).all { it == address })
                assertTrue(owner.transactions.isEmpty() && official.transactions.isEmpty())
                owner.assertHealthy()
                official.assertHealthy()
            }
        }
    }

    @Test fun unavailableWalletPreservesAnonymousReadingButDoesNotBypassSavedPersonalChoices() {
        val reference = readReference("native-following-reference.json")
        val root = reference.getAsJsonObject("root")
        val other = reference.getAsJsonObject("otherRoot")
        val author = root.get("field2").asString
        val peer = ControlledFeedPeer("[$root,$other]", archiveResponse = { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            when (criteria.get("field2")?.asString) {
                null -> "[$root,$other]"
                author -> "[$root]"
                else -> error("Unexpected author")
            }
        })
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            var address = ""
            withOpenApp(intent, "anonymous-seed-failure") {
                waitForEnabled("Refresh")
                showPost("Native Red Square reference post")
                ui.onNodeWithText("Wallet").performClick()
                address = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
            }
            // Fault only the existing OS directory-sync seam. The saved wallet bytes stay intact.
            val unavailable = Intent(intent).putExtra("walletCommitSyncUnavailable", true)
            ActivityScenario.launch<MainActivity>(unavailable).use { activity ->
                withFailureCapture({ captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/anonymous-reading-failure.png")) }) {
                    waitForWalletRecovery(ui)
                    showPost("Native Red Square reference post")
                    captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/anonymous-cached-reading.png"), "Native Red Square reference post")
                    ui.onNodeWithText("Wallet").performClick()
                    ui.onNodeWithText("Wallet unavailable. Close this window and try again.").assertIsDisplayed()
                    ui.onNodeWithContentDescription("Wallet address").assertDoesNotExist()
                    ui.onNodeWithText("Done").performClick()
                    activity.onActivity { it.intent.removeExtra("walletCommitSyncUnavailable") }
                    ui.onNodeWithText("Retry").performClick()
                    waitForEnabled("Refresh")
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(address, displayedWalletAddress(ui))
                    ui.onNodeWithText("Done").performClick()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText(author))
                    ui.onNodeWithText(author).performClick()
                    waitFor("Mute account")
                    muteAccount()
                }
            }
            ActivityScenario.launch<MainActivity>(unavailable).use { activity ->
                withFailureCapture({ captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/unknown-owner-policy-failure.png")) }) {
                    waitForWalletRecovery(ui)
                    waitFor("Recover your wallet to apply saved personal controls. Cached posts stay hidden until your identity is available.")
                    ui.onAllNodesWithText("Native Red Square reference post").assertCountEquals(0)
                    ui.onAllNodesWithText("Another account reference post").assertCountEquals(0)
                    ui.onAllNodes(hasScrollAction()).assertCountEquals(0)
                    captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/unknown-owner-policy-gate.png"))
                    activity.onActivity { it.intent.removeExtra("walletCommitSyncUnavailable") }
                    ui.onNodeWithText("Retry").performClick()
                    waitForEnabled("Refresh")
                    showPost("Another account reference post")
                    assertPostAbsent("Native Red Square reference post", "Another account reference post")
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(address, displayedWalletAddress(ui))
                    ui.onNodeWithText("Done").performClick()
                }
            }
            assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == address })
            assertTrue(peer.transactions.isEmpty())
            peer.assertHealthy()
        }
    }

    @Test fun unblockingKeepsAnIntentionalMuteAcrossBackendChangesAndOfflineReopening() {
        val reference = readReference("native-following-reference.json")
        val root = reference.getAsJsonObject("root")
        val other = reference.getAsJsonObject("otherRoot")
        val author = root.get("field2").asString
        val peer = ControlledFeedPeer("[$root,$other]", archiveResponse = { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            when (criteria.get("field2")?.asString) {
                null -> "[$root,$other]"
                author -> "[$root]"
                else -> error("Unexpected author")
            }
        })
        peer.start().use { server ->
            // Both approved backend sessions use a controlled binary transport here.
            // The earlier Mute journey independently verifies different server histories.
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            var address = ""
            withOpenApp(intent, "independent-block-first-failure") {
                waitForEnabled("Refresh")
                showPost("Native Red Square reference post")
                ui.onNodeWithText("Wallet").performClick()
                address = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
                ui.onNode(hasScrollAction()).performScrollToNode(hasText(author))
                ui.onNodeWithText(author).performClick()
                waitFor("Mute account")
                muteAccount()
                waitFor("Block account")
                ui.onNodeWithText("Block account").performClick()
                waitFor("Block hides this account's posts, social notifications and incoming Chat messages.")
                ui.onNodeWithText("Block", substring = false).performClick()
                waitFor("Unblock account")
                // Block must hide social content independently of Mute.
                ui.onNodeWithText("Unmute account").performClick()
                waitFor("Mute account")
                ui.onNodeWithText("Back").performClick()
                showPost("Another account reference post")
                assertPostAbsent("Native Red Square reference post", "Another account reference post")
                ui.onNodeWithText("Settings").performClick()
                ui.onNodeWithText("Muted and blocked accounts").performClick()
                waitFor(author)
                waitFor("Unblock account")
                ui.onAllNodesWithText("Unmute account").assertCountEquals(0)
                captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/blocked-without-mute.png"), "Unblock account")
                muteAccount()
                ui.onNodeWithText("Done").performClick()
                ui.onNodeWithText("Settings").performClick()
                waitFor("saito.io")
                ui.onNodeWithText("saito.io").performClick()
                waitFor("Server: saito.io")
                waitForEnabled("Refresh")
                showPost("Another account reference post")
                assertPostAbsent("Native Red Square reference post", "Another account reference post")
            }
            peer.assertHealthy()
            server.close()
            withOpenApp(intent, "independent-block-offline-failure") {
                waitFor("Server: saito.io")
                waitFor("Server unavailable")
                showPost("Another account reference post")
                ui.onNodeWithText("Settings").performClick()
                ui.onNodeWithText("Muted and blocked accounts").performClick()
                waitFor(author)
                waitFor("Unblock account")
                waitFor("Unmute account")
                captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/muted-and-blocked-offline.png"), author)
                ui.onNodeWithText("Unblock account").performClick()
                ui.waitUntil(15_000) { ui.onAllNodesWithText("Unblock account").fetchSemanticsNodes().isEmpty() }
                ui.onNodeWithText("Unmute account").assertIsDisplayed()
                captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/unblocked-still-muted.png"), "Unmute account")
                ui.onNodeWithText("Done").performClick()
                assertPostAbsent("Native Red Square reference post", "Another account reference post")
            }
            withOpenApp(intent, "independent-block-reopen-failure") {
                waitFor("Server unavailable")
                showPost("Another account reference post")
                assertPostAbsent("Native Red Square reference post", "Another account reference post")
                ui.onNodeWithText("Settings").performClick()
                ui.onNodeWithText("Muted and blocked accounts").performClick()
                waitFor(author)
                waitFor("Unmute account")
                ui.onAllNodesWithText("Unblock account").assertCountEquals(0)
                ui.onNodeWithText("Unmute account").performClick()
                waitFor("No muted or blocked accounts.")
                ui.onNodeWithText("Done").performClick()
                showPost("Native Red Square reference post")
                ui.onNodeWithText("Wallet").performClick()
                assertEquals(address, displayedWalletAddress(ui))
                ui.onNodeWithText("Done").performClick()
            }
            assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == address })
            assertTrue(peer.transactions.isEmpty())
            peer.assertHealthy()
        }
    }

    @Test fun mutedContentStaysHiddenAcrossProfileThreadQuotesSearchAndOfflineReopening() {
        val thread = readReference("native-thread-reference.json")
        val root = thread.getAsJsonObject("root")
        val first = thread.getAsJsonObject("firstReply")
        val nested = thread.getAsJsonObject("nestedReply")
        val other = readReference("native-following-reference.json").getAsJsonObject("otherRoot")
        val quote = readReference("native-quote-reference.json").getAsJsonObject("embedded").deepCopy().apply {
            addProperty("field1", "RedSquare"); addProperty("field4", ""); addProperty("field5", get("sig").asString)
        }
        val author = root.get("field2").asString
        val rows = listOf(root, first, nested, other, quote)
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            check(criteria.get("field1").asString in listOf("RedSquare", "RedSquareRetweet"))
            if (criteria.has("flagged_ne")) check(criteria.get("flagged_ne").asInt == 1)
            rows.filter { row ->
                (!criteria.has("sig") || criteria.get("sig").asString == row.get("sig").asString) &&
                    (!criteria.has("field2") || criteria.get("field2").asString == row.get("field2").asString) &&
                    (!criteria.has("field5") || criteria.get("field5").asString == row.get("field5").asString) &&
                    (!criteria.has("field4") || criteria.get("field4").asString == row.get("field4").asString)
            }.joinToString(prefix = "[", postfix = "]")
        })
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            var address = ""
            withOpenApp(intent, "mounted-views-failure") {
                waitForEnabled("Refresh")
                ui.onNodeWithText("Wallet").performClick()
                address = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
                showStandalonePost("Embedded quote commentary")
                val original = hasText("Native Red Square reference post") and
                    hasAnyAncestor(hasContentDescription("Quoted original"))
                ui.waitUntil(15_000) {
                    ui.onAllNodes(original, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                }
                ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(original)
                ui.onNode(original, useUnmergedTree = true).assertIsDisplayed()
                captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/quote-original-before-mute.png"), "Native Red Square reference post")
                showStandalonePost("Native Red Square reference post").performClick()
                waitFor("Conversation")
                waitForEnabled("Refresh")
                showPost("Independent Saito reply")
                showPost("Native Red Square reference post")
                val authorLink = hasClickAction() and hasAnySibling(hasText("Native Red Square reference post"))
                // The root is the first chronological card. Nested replies may share its author.
                ui.onAllNodes(authorLink, useUnmergedTree = true)[0].assertIsDisplayed().performClick()
                waitFor("Profile")
                waitFor(author)
                waitFor("Mute account")
                muteAccount()
                assertPostAbsent("Native Red Square reference post", "Unmute account")
                waitFor("Posts from muted or blocked accounts are hidden.")
                captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/profile-personal-filter.png"), "Unmute account")
                ui.onNodeWithText("Back").performClick()
                waitFor("Conversation")
                showPost("Independent Saito reply")
                assertPostAbsent("Native Red Square reference post", "Independent Saito reply")
                assertPostAbsent("Nested Saito reply", "Independent Saito reply")
                waitFor("This post is hidden by your personal controls.")
                captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/thread-personal-filter.png"), "Independent Saito reply")
                ui.onNodeWithText("Back").performClick()
                showPost("Embedded quote commentary")
                waitFor("Quoted original hidden by your personal controls.")
                assertPostAbsent("Native Red Square reference post", "Embedded quote commentary")
                captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/quote-personal-filter.png"), "Embedded quote commentary")
                ui.onNodeWithText("Search").performClick()
                ui.onNode(hasSetTextAction()).performTextInput("reference")
                waitForEnabled("Search history")
                ui.onNodeWithText("Search history").performClick()
                waitForEnabled("Search history")
                showPost("Another account reference post")
                assertPostAbsent("Native Red Square reference post", "Another account reference post")
                captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/search-personal-filter.png"), "Another account reference post")
                ui.onNode(hasSetTextAction()).performTextReplacement("commentary")
                ui.onNodeWithText("Search history").performClick()
                waitForEnabled("Search history")
                showPost("Embedded quote commentary")
                waitFor("Quoted original hidden by your personal controls.")
                assertPostAbsent("Native Red Square reference post", "Embedded quote commentary")
                ui.onNode(hasSetTextAction()).performTextReplacement(author)
                ui.onNodeWithText("Search history").performClick()
                waitForEnabled("Search history")
                // A matching editable query alone is not a discovered person.
                val person = hasText(author) and !hasSetTextAction()
                ui.onNode(hasScrollAction() and !hasSetTextAction()).performScrollToNode(person)
                ui.onNode(person).assertIsDisplayed().performClick()
                waitFor("Profile")
                waitFor("Unmute account")
                assertPostAbsent("Native Red Square reference post", "Unmute account")
            }
            peer.assertHealthy()
            server.close()
            withOpenApp(intent, "mounted-views-offline-failure") {
                waitFor("Server unavailable")
                showPost("Embedded quote commentary")
                waitFor("Quoted original hidden by your personal controls.")
                assertPostAbsent("Native Red Square reference post", "Embedded quote commentary")
                captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/quote-personal-offline.png"), "Embedded quote commentary")
                ui.onNodeWithText("Settings").performClick()
                ui.onNodeWithText("Muted and blocked accounts").performClick()
                waitFor("Unmute account")
                ui.onNodeWithText("Unmute account").performClick()
                waitFor("No muted or blocked accounts.")
                ui.onNodeWithText("Done").performClick()
                showStandalonePost("Native Red Square reference post")
                ui.onNodeWithText("Wallet").performClick()
                assertEquals(address, displayedWalletAddress(ui))
                ui.onNodeWithText("Done").performClick()
            }
            assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == address })
            assertTrue(peer.transactions.isEmpty())
            peer.assertHealthy()
        }
    }

    @Test fun aLateFailedUnmuteRetainsSavedFilteringAfterReopeningAndAllowsExplicitRepair() {
        val reference = readReference("native-following-reference.json")
        val root = reference.getAsJsonObject("root")
        val other = reference.getAsJsonObject("otherRoot")
        val author = root.get("field2").asString
        val peer = ControlledFeedPeer("[$root,$other]", archiveResponse = { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            when (criteria.get("field2")?.asString) {
                null -> "[$root,$other]"
                author -> "[$root]"
                else -> error("Unexpected author")
            }
        })
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            var address = ""
            withOpenApp(intent, "failed-save-seed-failure") {
                waitForEnabled("Refresh")
                showPost("Native Red Square reference post")
                ui.onNodeWithText("Wallet").performClick()
                address = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
                ui.onNode(hasScrollAction()).performScrollToNode(hasText(author))
                ui.onNodeWithText(author).assertIsDisplayed().performClick()
                waitFor("Profile")
                muteAccount()
                ui.onNodeWithText("Back").performClick()
                showPost("Another account reference post")
                assertPostAbsent("Native Red Square reference post", "Another account reference post")
            }
            // Fault the OS directory sync after AtomicFile finishes the active write.
            // Reading, wallet storage and later recovery use the ordinary filesystem.
            withOpenApp(Intent(intent).putExtra("personalCommitSyncUnavailable", true), "late-save-failure") {
                waitForEnabled("Refresh")
                showPost("Another account reference post")
                assertPostAbsent("Native Red Square reference post", "Another account reference post")
                ui.onNodeWithText("Settings").performClick()
                ui.onNodeWithText("Muted and blocked accounts").performClick()
                waitFor("Unmute account")
                ui.onNodeWithText("Unmute account").performClick()
                waitFor("Your change could not be saved. Try again.")
                waitForEnabled("Unmute account")
                captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/late-save-retained-mute.png"), "Your change could not be saved. Try again.")
            }
            withOpenApp(intent, "late-save-reopening-failure") {
                waitForEnabled("Refresh")
                showPost("Another account reference post")
                assertPostAbsent("Native Red Square reference post", "Another account reference post")
                ui.onNodeWithText("Settings").performClick()
                ui.onNodeWithText("Muted and blocked accounts").performClick()
                waitFor(author)
                waitFor("Unmute account")
                captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/late-save-reopened-mute.png"), "Unmute account")
                ui.onNodeWithText("Unmute account").performClick()
                waitFor("No muted or blocked accounts.")
                ui.onNodeWithText("Done").performClick()
                showPost("Native Red Square reference post")
                captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/late-save-repaired-post.png"), "Native Red Square reference post")
                ui.onNodeWithText("Wallet").performClick()
                assertEquals(address, displayedWalletAddress(ui))
                ui.onNodeWithText("Done").performClick()
            }
            assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == address })
            assertTrue(peer.transactions.isEmpty())
            peer.assertHealthy()
        }
    }

    @Test fun failedAnchorRemovalAndRecreationStillRestoresMuteAfterStorageRepair() {
        val reference = readReference("native-following-reference.json")
        val root = reference.getAsJsonObject("root")
        val other = reference.getAsJsonObject("otherRoot")
        val author = root.get("field2").asString
        val peer = ControlledFeedPeer("[$root,$other]", archiveResponse = { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            when (criteria.get("field2")?.asString) {
                null -> "[$root,$other]"
                author -> "[$root]"
                else -> error("Unexpected author")
            }
        })
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            var address = ""
            var primary: Throwable? = null
            try {
                withOpenApp(intent, "removal-seed-failure") {
                    waitForEnabled("Refresh")
                    showPost("Native Red Square reference post")
                    ui.onNodeWithText("Wallet").performClick()
                    address = displayedWalletAddress(ui)
                    ui.onNodeWithText("Done").performClick()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText(author))
                    ui.onNodeWithText(author).assertIsDisplayed().performClick()
                    waitFor("Profile")
                    muteAccount()
                    ui.onNodeWithText("Back").performClick()
                    showPost("Another account reference post")
                    assertPostAbsent("Native Red Square reference post", "Another account reference post")
                }
                withOpenApp(Intent(intent).putExtra("personalRecoverySyncUnavailable", true), "removal-uncertain-failure") {
                    waitForEnabled("Refresh")
                    ui.onNodeWithText("Settings").performClick()
                    ui.onNodeWithText("Muted and blocked accounts").performClick()
                    waitFor("Unmute account")
                    ui.onNodeWithText("Unmute account").performClick()
                    waitFor("Personal controls could not be opened. Hidden content stays hidden. Retry to recover your saved choices.")
                    ui.onNodeWithText("Unmute account").assertIsNotEnabled()
                    captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/removal-uncertain-policy.png"), "Retry")
                    ui.onNodeWithText("Done").performClick()
                    waitFor("Personal controls could not be opened. Hidden content stays hidden. Retry to recover your saved choices.")
                    ui.onAllNodes(hasScrollAction()).assertCountEquals(0)
                    ui.onAllNodesWithText("Native Red Square reference post").assertCountEquals(0)
                }
                // Repair only the controlled recovery-path obstruction, never active policy or wallet bytes.
                repairPersonalObstruction(address)
                withOpenApp(intent, "removal-reopening-failure") {
                    waitForEnabled("Refresh")
                    showPost("Another account reference post")
                    assertPostAbsent("Native Red Square reference post", "Another account reference post")
                    ui.onNodeWithText("Settings").performClick()
                    ui.onNodeWithText("Muted and blocked accounts").performClick()
                    waitFor(author)
                    waitFor("Unmute account")
                    captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/removal-reopened-policy.png"), "Unmute account")
                    ui.onNodeWithText("Unmute account").performClick()
                    waitFor("No muted or blocked accounts.")
                    ui.onNodeWithText("Done").performClick()
                    showPost("Native Red Square reference post")
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(address, displayedWalletAddress(ui))
                    ui.onNodeWithText("Done").performClick()
                }
                assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == address })
                assertTrue(peer.transactions.isEmpty())
                peer.assertHealthy()
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                try { if (address.isNotEmpty()) repairPersonalObstruction(address) }
                catch (cleanup: Throwable) { if (primary != null) primary.addSuppressed(cleanup) else throw cleanup }
            }
        }
    }

    private fun repairPersonalObstruction(owner: String) {
        val obstruction = File(context.noBackupFilesDir, "personal.$owner.recovery.v1")
        if (!obstruction.isDirectory) return
        val marker = File(obstruction, "controlled-unavailable")
        check(obstruction.listFiles()?.toList() == listOf(marker) && marker.isFile && marker.length() == 0L)
        check(marker.delete() && obstruction.delete())
    }

    private fun muteAccount() {
        waitFor("Mute account")
        ui.onNodeWithText("Mute account").performClick()
        waitFor("Mute hides this account's posts and social notifications. Chat messages remain available.")
        ui.onNodeWithText("Mute", substring = false).performClick()
        waitFor("Unmute account")
    }

    private fun showStandalonePost(text: String): SemanticsNodeInteraction {
        val standalone = hasText(text) and !hasAnyAncestor(hasContentDescription("Quoted original"))
        ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(standalone)
        return ui.onNode(standalone, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun withOpenApp(intent: Intent, failureName: String, block: () -> Unit) {
        ActivityScenario.launch<MainActivity>(intent).use {
            withFailureCapture({ captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/$failureName.png")) }, block)
        }
    }

    private fun readReference(name: String): JsonObject =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }

    private fun waitFor(text: String) {
        ui.waitUntil(15_000) {
            ui.onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun waitForEnabled(text: String) {
        ui.waitUntil(15_000) {
            ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun showPost(text: String) {
        ui.waitUntil(15_000) { ui.onAllNodes(hasScrollAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        ui.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        ui.onNodeWithText(text).assertIsDisplayed()
    }

    private fun assertPostAbsent(text: String, returnTo: String) {
        val list = ui.onNode(hasScrollAction())
        val failure = assertThrows(AssertionError::class.java) { list.performScrollToNode(hasText(text)) }
        assertTrue("The complete list must be searched", failure.message?.contains("No node found that matches") == true)
        list.performScrollToNode(hasText(returnTo))
        ui.onNodeWithText(returnTo).assertIsDisplayed()
    }
}
