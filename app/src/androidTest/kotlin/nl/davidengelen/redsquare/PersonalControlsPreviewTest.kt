package nl.davidengelen.redsquare

import android.content.Intent
import android.app.KeyguardManager
import android.os.Build
import android.provider.DocumentsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.io.File
import java.net.InetAddress
import java.util.UUID
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
                    ui.clickVisiblePostProfile("Anon-${author.take(6)}")
                    waitFor("Profile")
                    waitFor("Mute account")
                    muteAccount()
                    ui.onNodeWithText("Back").performClick()
                    showPost("Another account reference post")
                    assertPostAbsent("Native Red Square reference post", "Another account reference post")
                    ui.onNodeWithText("Settings").performClick()
                    waitFor("saito.io")
                    ui.onNodeWithText("saito.io").performScrollTo().performClick()
                    waitFor("Node: saito.io")
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
                    waitFor("Node: saito.io")
                    waitFor("Node unavailable")
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
                    ui.clickVisiblePostProfile("Anon-${author.take(6)}")
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
                ui.clickVisiblePostProfile("Anon-${author.take(6)}")
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
                ui.onNodeWithText("saito.io").performScrollTo().performClick()
                waitFor("Node: saito.io")
                waitForEnabled("Refresh")
                showPost("Another account reference post")
                assertPostAbsent("Native Red Square reference post", "Another account reference post")
            }
            peer.assertHealthy()
            server.close()
            withOpenApp(intent, "independent-block-offline-failure") {
                waitFor("Node: saito.io")
                waitFor("Node unavailable")
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
                waitFor("Node unavailable")
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
                val authorLink = hasContentDescription("View profile for Anon-${author.take(6)}") and
                    hasAnyAncestor(hasText("Native Red Square reference post") and hasClickAction())
                // Associate the account with this root card, even when replies share its author.
                ui.onNode(authorLink).assertIsDisplayed().performClick()
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
                waitFor("Node unavailable")
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
                ui.clickVisiblePostProfile("Anon-${author.take(6)}")
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
                    ui.clickVisiblePostProfile("Anon-${author.take(6)}")
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
                    ui.onAllNodesWithText("Unmute account").assertCountEquals(0)
                    waitFor("Retry")
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

    @Test fun unreadableSavedChoicesHidePostsUntilRepairedAndRetried() {
        val reference = readReference("native-following-reference.json")
        val root = reference.getAsJsonObject("root")
        val other = reference.getAsJsonObject("otherRoot")
        val author = root.get("field2").asString
        val peer = ControlledFeedPeer("[$root,$other]", archiveResponse = { criteria ->
            check(criteria.get("field1").asString == "RedSquare")
            if (criteria.get("field2")?.asString == author) "[$root]" else "[$root,$other]"
        })
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            var address = ""
            withOpenApp(intent, "corrupt-policy-preparation-failure") {
                waitForEnabled("Refresh")
                showPost("Native Red Square reference post")
                ui.onNodeWithText("Wallet").performClick()
                address = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
                ui.clickVisiblePostProfile("Anon-${author.take(6)}")
                waitFor("Profile")
                muteAccount()
                ui.onNodeWithText("Back").performClick()
                showPost("Another account reference post")
                assertPostAbsent("Native Red Square reference post", "Another account reference post")
            }
            val file = File(context.noBackupFilesDir, "personal.$address.v1")
            check(file.isFile && file.length() in 29..(5 * 1024 * 1024 + 30))
            check(listOf(".bak", ".new").none { File(file.path + it).exists() })
            val saved = file.readBytes() // Encrypted bytes remain bounded in emulator memory only.
            var repaired = false
            var primary: Throwable? = null
            try {
                val damaged = saved.copyOf()
                damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
                try { file.outputStream().use { it.write(damaged); it.fd.sync() } }
                finally { damaged.fill(0) }
                withOpenApp(intent, "corrupt-policy-recovery-failure") {
                    waitFor("Personal controls could not be opened. Hidden content stays hidden. Retry to recover your saved choices.")
                    ui.onAllNodes(hasScrollAction()).assertCountEquals(0)
                    ui.onAllNodesWithText("Native Red Square reference post").assertCountEquals(0)
                    ui.onAllNodesWithText("Another account reference post").assertCountEquals(0)
                    captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/corrupt-policy-withheld.png"),
                        "Personal controls could not be opened. Hidden content stays hidden. Retry to recover your saved choices.")
                    file.outputStream().use { it.write(saved); it.fd.sync() }
                    repaired = true
                    ui.onNodeWithText("Retry").performClick()
                    showPost("Another account reference post")
                    assertPostAbsent("Native Red Square reference post", "Another account reference post")
                    ui.onNodeWithText("Settings").performClick()
                    waitFor("Muted and blocked accounts")
                    ui.onNodeWithText("Muted and blocked accounts").performClick()
                    waitForEnabled("Unmute account")
                    ui.onNodeWithText(author).assertExists()
                    captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/corrupt-policy-repaired-mute.png"), author)
                    ui.onNodeWithText("Unmute account").performClick()
                    waitFor("No muted or blocked accounts.")
                    ui.onNodeWithText("Done").performClick()
                    showPost("Native Red Square reference post")
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(address, displayedWalletAddress(ui))
                    ui.onNodeWithText("Done").performClick()
                    assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == address })
                    assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                }
            } catch (failure: Throwable) { primary = failure; throw failure }
            finally {
                try { if (!repaired) file.outputStream().use { it.write(saved); it.fd.sync() } }
                catch (cleanup: Throwable) {
                    val earlier = primary
                    if (earlier == null) throw cleanup else earlier.addSuppressed(cleanup)
                } finally { saved.fill(0) }
            }
        }
    }

    @Test fun realWalletReplacementIsolatesChoicesAndRestoringTheWalletRecoversThem() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(keyguard.isDeviceSecure)
        var originalAddress = ""
        // This may be the first test on a fresh focused installation. Let the
        // actual first-launch UI create its wallet before inspecting saved options.
        withOpenApp(Intent(context, MainActivity::class.java).putExtra("feedDisabled", true),
            "personal-identity-bootstrap-failure") {
            waitFor("Wallet")
            ui.onNodeWithText("Wallet").performClick()
            originalAddress = displayedWalletAddress(ui)
            ui.onNodeWithText("Done").performClick()
        }
        val store = AndroidWalletStore(context)
        val original = store.loadExisting()
        assertEquals(originalAddress, original.publicKey)
        val scalar = if (original.publicKey == "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR") "2" else "1"
        val incoming = SaitoWallet.fromPrivateKey("0".repeat(63) + scalar) // Public unsafe fixture only.
        assertNotEquals(original.publicKey, incoming.publicKey)
        val incomingFile = "00-personal-import-$scalar.json"
        check(device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test stat -c %s files/wallet-test-documents/$incomingFile")
            .trim().toLongOrNull()?.let { it > 0 } == true) { "Personal identity fixture was not provisioned" }
        val backupFile = "00-personal-original-${UUID.randomUUID()}.json"
        val backupUri = DocumentsContract.buildDocumentUri(ExportTestDocumentsProvider.AUTHORITY, backupFile)
        val reference = readReference("native-following-reference.json")
        val root = reference.getAsJsonObject("root")
        val other = reference.getAsJsonObject("otherRoot")
        val author = root.get("field2").asString
        val peer = ControlledFeedPeer("[$root,$other]", archiveResponse = { criteria ->
            check(criteria.get("field1").asString == "RedSquare")
            if (criteria.get("field2")?.asString == author) "[$root]" else "[$root,$other]"
        })
        val balanceServer = MockWebServer().apply {
            dispatcher = object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest) = MockResponse.Builder()
                    .body("${System.currentTimeMillis()}-1234-${"a".repeat(64)}.snap\n").build()
            }
            start(InetAddress.getByName("127.0.0.1"), 0)
        }
        var primary: Throwable? = null
        try {
            device.executeShellCommand("locksettings set-pin --user 0 $EXPORT_TEST_PIN")
            ui.waitUntil(10_000) { keyguard.isDeviceSecure }
            peer.start().use { server ->
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("balanceApiUrl", "http://127.0.0.1:${balanceServer.port}/balance/")
                withOpenApp(intent, "personal-identity-replacement-failure") {
                    waitForEnabled("Refresh")
                    showPost("Native Red Square reference post")
                    ui.clickVisiblePostProfile("Anon-${author.take(6)}")
                    waitFor("Profile")
                    muteAccount()
                    ui.onNodeWithText("Back").performClick()
                    showPost("Another account reference post")
                    assertPostAbsent("Native Red Square reference post", "Another account reference post")
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(original.publicKey, displayedWalletAddress(ui))
                    ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                    chooseWalletImport(ui, incomingFile, incoming.publicKey)
                    ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().assertIsOff()
                    ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
                    ui.onNodeWithText("Back up current wallet").performScrollTo().performClick()
                    chooseTestBackup(device, backupFile)
                    enterRealSystemPin(device)
                    waitFor("Current wallet backed up for this replacement")
                    ui.onNodeWithText("Current wallet backed up for this replacement").performScrollTo().assertIsDisplayed()
                    ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
                    // Compare full options in bounded memory; assertion failures never print private JSON.
                    val bytes = requireNotNull(context.contentResolver.openInputStream(backupUri)).use {
                        val buffer = ByteArray(SaitoWallet.MAX_BACKUP_BYTES + 1)
                        try {
                            var size = 0
                            while (size < buffer.size) {
                                val count = it.read(buffer, size, buffer.size - size)
                                if (count < 0) break
                                check(count > 0); size += count
                            }
                            check(size in 1..SaitoWallet.MAX_BACKUP_BYTES)
                            buffer.copyOf(size)
                        } finally { buffer.fill(0) }
                    }
                    try { assertTrue("Authenticated backup must retain all outgoing options",
                        JsonParser.parseString(original.exportJson()) == JsonParser.parseString(bytes.toString(Charsets.UTF_8))) }
                    finally { bytes.fill(0) }
                    captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/identity-backed-up-preview.png"),
                        "Current wallet backed up for this replacement")
                    ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
                    ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsEnabled().performClick()
                    waitFor("Wallet imported")
                    assertEquals(incoming.publicKey, displayedWalletAddress(ui))
                    assertEquals("retain-this-opaque-option", JsonParser.parseString(store.loadExisting().exportJson())
                        .asJsonObject.getAsJsonObject("personalFixtureExtension").get("opaque").asString)
                    ui.onNodeWithText("Done").performClick()
                    showPost("Native Red Square reference post")
                    ui.onNodeWithText("Settings").performClick()
                    waitFor("Muted and blocked accounts")
                    ui.onNodeWithText("Muted and blocked accounts").performClick()
                    waitFor("No muted or blocked accounts.")
                    captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/identity-incoming-empty-choices.png"),
                        "No muted or blocked accounts.")
                    ui.onNodeWithText("Done").performClick()
                }
                withOpenApp(intent, "personal-identity-restoration-failure") {
                    waitForEnabled("Refresh")
                    showPost("Native Red Square reference post")
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(incoming.publicKey, displayedWalletAddress(ui))
                    captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/identity-incoming-reopened.png"),
                        incoming.publicKey)
                    ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                    chooseWalletImport(ui, backupFile, original.publicKey)
                    waitFor("Node reports zero SAITO")
                    ui.onNodeWithText("Skip backup").performScrollTo().performClick()
                    ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().assertIsOff().performClick()
                    ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsEnabled().performClick()
                    waitFor("Wallet imported")
                    assertEquals(original.publicKey, displayedWalletAddress(ui))
                    assertTrue("Restored wallet must retain all original options",
                        JsonParser.parseString(original.exportJson()) == JsonParser.parseString(store.loadExisting().exportJson()))
                    ui.onNodeWithText("Done").performClick()
                    showPost("Another account reference post")
                    assertPostAbsent("Native Red Square reference post", "Another account reference post")
                    ui.onNodeWithText("Settings").performClick()
                    waitFor("Muted and blocked accounts")
                    ui.onNodeWithText("Muted and blocked accounts").performClick()
                    waitForEnabled("Unmute account")
                    ui.onNodeWithText(author).assertExists()
                    captureNativeScreen(File(context.cacheDir, "personal-controls-evidence/identity-original-restored-mute.png"), author)
                    ui.onNodeWithText("Done").performClick()
                }
                withOpenApp(intent, "personal-identity-final-reopen-failure") {
                    waitForEnabled("Refresh")
                    showPost("Another account reference post")
                    assertPostAbsent("Native Red Square reference post", "Another account reference post")
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(original.publicKey, displayedWalletAddress(ui))
                    ui.onNodeWithText("Done").performClick()
                }
                assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                assertTrue(peer.clientKeys.contains(original.publicKey) && peer.clientKeys.contains(incoming.publicKey))
                assertTrue(peer.clientKeys.all { it == original.publicKey || it == incoming.publicKey })
                assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupPersonalIdentity(primary, {
                val active = store.loadExisting()
                check(active.publicKey == original.publicKey || active.publicKey == incoming.publicKey)
                // Emergency fixture cleanup only; successful restoration is proved through real SAF above.
                if (active.publicKey == incoming.publicKey) store.replace(active, original)
            }, {
                device.executeShellCommand("locksettings clear --old $EXPORT_TEST_PIN --user 0")
                ui.waitUntil(10_000) { !keyguard.isDeviceSecure }
            }, {
                check(device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test rm -f files/wallet-test-documents/$backupFile files/wallet-test-documents/00-personal-import-1.json files/wallet-test-documents/00-personal-import-2.json").isBlank())
            }, { balanceServer.close() })
        }
    }

    private fun cleanupPersonalIdentity(primary: Throwable?, vararg operations: () -> Unit) {
        var failure = primary
        for (operation in operations) {
            try { operation() }
            catch (cleanup: Throwable) {
                val earlier = failure
                if (earlier == null) failure = cleanup else if (earlier !== cleanup) earlier.addSuppressed(cleanup)
            }
        }
        if (primary == null) failure?.let { throw it }
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
