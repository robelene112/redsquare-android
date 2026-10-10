package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class FollowingPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Before fun preparePublicState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".dev"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("feed.") || it.name.startsWith("conversation.") || it.name.startsWith("profile.") ||
                it.name.startsWith("following.") || it.name.startsWith("followingfeed.")
        }?.forEach { check(it.delete()) }
    }

    @Test fun followingChoicesSurviveBackendSwitchAndOfflineRestartWithSeparateHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = readReference("native-following-reference.json")
        val paging = readReference("native-profile-paging-reference.json")
        val root = fixture.getAsJsonObject("root")
        val other = fixture.getAsJsonObject("otherRoot")
        val newer = paging.getAsJsonArray("recentPosts")[0].asJsonObject
        val author = root.get("field2").asString
        fun controlledPeer(post: com.google.gson.JsonObject) = ControlledFeedPeer("[$post,$other]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            if (criteria.has("field2")) {
                check(criteria.get("field2").asString == author)
                "[$post]"
            } else "[$post,$other]"
        }
        val owner = controlledPeer(root)
        val official = controlledPeer(newer)
        owner.start().use { ownerServer ->
            official.start().use { officialServer ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")).use { scenario ->
                    try {
                        waitFor("Native Red Square reference post")
                        openFeedProfile(author)
                        waitFor("Follow")
                        ui.onNodeWithText("Follow").performClick()
                        waitFor("Unfollow")
                        ui.onNodeWithText("Back").performClick()
                        ui.onNodeWithText("Following").performClick()
                        waitFor("Native Red Square reference post")
                        ui.onNodeWithText("Settings").performClick()
                        waitFor("saito.io")
                        ui.onNodeWithText("saito.io").performClick()
                        waitFor("Profile newer post 20")
                        ui.onNodeWithText("Following").performClick()
                        waitFor("Profile newer post 20")
                        assertPostAbsent("Another account reference post", "Profile newer post 20")
                        assertPostAbsent("Native Red Square reference post", "Profile newer post 20")
                        openFeedProfile(author)
                        waitFor("Unfollow")
                        ui.onNodeWithText("Back").performClick()
                        waitFor("Refresh")
                        ui.waitUntil(10_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                        owner.assertHealthy()
                        official.assertHealthy()
                        officialServer.close()
                        scenario.recreate()
                        waitFor("Server: saito.io")
                        waitFor("Profile newer post 20")
                        waitFor("Server unavailable")
                        assertPostAbsent("Another account reference post", "Profile newer post 20")
                        assertPostAbsent("Native Red Square reference post", "Profile newer post 20")
                        captureFeedEvidence("following-evidence/official-following-after-offline-restart.png")
                        ui.onNodeWithText("Settings").performClick()
                        waitFor("saito.davidengelen.nl")
                        ui.onNodeWithText("saito.davidengelen.nl").performClick()
                        ui.onNodeWithText("Following").performClick()
                        waitFor("Native Red Square reference post")
                        assertPostAbsent("Profile newer post 20", "Native Red Square reference post")
                        assertPostAbsent("Another account reference post", "Native Red Square reference post")
                        openFeedProfile(author)
                        waitFor("Unfollow")
                        owner.assertHealthy()
                    } catch (failure: Throwable) {
                        captureFeedEvidence("following-evidence/backend-following-first-failure.png")
                        throw failure
                    }
                }
            }
        }
    }

    @Test fun failedFollowSaveKeepsTheActionUnconfirmedAndAllowsRetry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = readReference("native-following-reference.json")
        val root = fixture.getAsJsonObject("root")
        val author = root.get("field2").asString
        val peer = ControlledFeedPeer("[$root]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            if (criteria.has("field2")) check(criteria.get("field2").asString == author)
            "[$root]"
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                waitFor("Native Red Square reference post")
                ui.onNodeWithText("Wallet").performClick()
                val address = displayedWalletAddress(ui)
                ui.onNodeWithText("Done").performClick()
                val blocked = java.io.File(context.noBackupFilesDir, "following.$address.v1")
                check(blocked.mkdir())
                val obstacle = java.io.File(blocked, "public-write-fault").apply { writeText("synthetic test only") }
                try {
                    openFeedProfile(author)
                    waitFor("Follow")
                    ui.onNodeWithText("Follow").performClick()
                    waitFor(context.getString(R.string.following_error))
                    ui.onNodeWithText("Unfollow").assertDoesNotExist()
                    ui.onNodeWithText("Follow").assertIsEnabled()
                    captureFeedEvidence("following-evidence/unconfirmed-follow-write-failure.png")
                    check(obstacle.delete())
                    check(blocked.delete())
                    ui.onNodeWithText("Follow").performClick()
                    waitFor("Unfollow")
                    ui.onNodeWithText(context.getString(R.string.following_error)).assertDoesNotExist()
                    ui.onNodeWithText("Back").performClick()
                    ui.onNodeWithText("Following").performClick()
                    waitFor("Native Red Square reference post")
                    peer.assertHealthy()
                    captureFeedEvidence("following-evidence/follow-write-retry.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("following-evidence/follow-write-first-failure.png")
                    throw failure
                } finally {
                    if (obstacle.exists()) check(obstacle.delete())
                    if (blocked.isDirectory) check(blocked.delete())
                }
            }
        }
    }

    @Test fun followingLoadsAuthorHistoryBeyondTheGeneralPageAndRejectsOtherSigners() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = readReference("native-following-reference.json")
        val paging = readReference("native-profile-paging-reference.json")
        val root = fixture.getAsJsonObject("root")
        val other = fixture.getAsJsonObject("otherRoot")
        val author = root.get("field2").asString
        val newer = paging.getAsJsonArray("recentPosts")[0]
        val misleading = other.deepCopy().apply { addProperty("field2", author) }
        val peer = ControlledFeedPeer("[$root,$other]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            when {
                criteria.has("field2") -> {
                    check(criteria.get("field2").asString == author)
                    "[$newer,$misleading]"
                }
                else -> "[$root,$other]"
            }
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitFor("Native Red Square reference post")
                    assertPostAbsent("Profile newer post 20", "Native Red Square reference post")
                    openFeedProfile(author)
                    waitFor("Follow")
                    ui.onNodeWithText("Follow").performClick()
                    waitFor("Unfollow")
                    ui.onNodeWithText("Back").performClick()
                    ui.onNodeWithText("Following").performClick()
                    waitFor("Profile newer post 20")
                    ui.onNodeWithText("Profile newer post 20").assertIsDisplayed()
                    assertPostAbsent("Another account reference post", "Profile newer post 20")
                    peer.assertHealthy()
                    captureFeedEvidence("following-evidence/available-author-history.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("following-evidence/author-history-first-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun changingWalletKeepsFollowChoicesWithTheirOriginalIdentity() {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) { "Isolated emulator required" }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = readReference("native-following-reference.json")
        val root = fixture.getAsJsonObject("root")
        val author = root.get("field2").asString
        val alternatePublicKey = fixture.getAsJsonObject("otherRoot").get("field2").asString
        // Public unsafe scalar2 fixture. Never fund or use on a real phone.
        val alternate = SaitoWallet.fromBackup("""{"wallet":{"version":5.677,"publicKey":"$alternatePublicKey","privateKey":"${"0".repeat(63)}2"}}""")
        val peer = ControlledFeedPeer("[$root]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            if (criteria.has("field2")) check(criteria.get("field2").asString == author)
            "[$root]"
        }
        peer.start().use { server ->
            fun withApp(action: () -> Unit) {
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    try { action() }
                    catch (failure: Throwable) {
                        captureFeedEvidence("following-evidence/identity-following-first-failure.png")
                        throw failure
                    }
                }
            }
            val store = AndroidWalletStore(context)
            lateinit var original: SaitoWallet
            var alternateActive = false
            try {
                withApp {
                    waitFor("Native Red Square reference post")
                    openFeedProfile(author)
                    waitFor("Follow")
                    ui.onNodeWithText("Follow").performClick()
                    waitFor("Unfollow")
                }
                original = store.loadExisting()
                // Filesystem fixture between cold launches, not an import-UI claim.
                store.replace(original, alternate)
                alternateActive = true
                withApp {
                    waitFor("Native Red Square reference post")
                    ui.onNodeWithText("Wallet").performClick()
                    org.junit.Assert.assertEquals(alternatePublicKey, displayedWalletAddress(ui))
                    ui.onNodeWithText("Done").performClick()
                    ui.onNodeWithText("Following").performClick()
                    waitFor("Follow accounts to see their posts here.")
                    assertPostAbsent("Native Red Square reference post")
                    ui.onNodeWithText("All posts").performClick()
                    waitFor("Native Red Square reference post")
                    openFeedProfile(author)
                    waitFor("Follow")
                    ui.onNodeWithText("Unfollow").assertDoesNotExist()
                    captureFeedEvidence("following-evidence/replacement-wallet-separate-following.png")
                }
                store.replace(alternate, original)
                alternateActive = false
                withApp {
                    waitFor("Native Red Square reference post")
                    ui.onNodeWithText("Following").performClick()
                    waitFor("Native Red Square reference post")
                    openFeedProfile(author)
                    waitFor("Unfollow")
                    peer.assertHealthy()
                    captureFeedEvidence("following-evidence/original-wallet-following-restored.png")
                }
            } finally {
                if (alternateActive) store.replace(alternate, original)
            }
        }
    }

    @Test fun newerUnrelatedGeneralCacheCannotHideAvailableFollowedAuthorPosts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = readReference("native-following-retention-reference.json")
        val root = fixture.getAsJsonObject("root")
        val author = root.get("field2").asString
        val peer = ControlledFeedPeer("[$root]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            if (criteria.has("field2")) check(criteria.get("field2").asString == author)
            "[$root]"
        }
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            ActivityScenario.launch<MainActivity>(intent).use {
                waitFor("Native Red Square reference post")
                openFeedProfile(author)
                waitFor("Follow")
                ui.onNodeWithText("Follow").performClick()
                waitFor("Unfollow")
                ui.onNodeWithText("Back").performClick()
                ui.waitUntil(10_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            }
            // Public filesystem fixture: a legitimate full cache, all newer and unfollowed.
            val cache = com.google.gson.JsonArray().apply {
                fixture.getAsJsonArray("newerUnrelated").forEach { add(it.asJsonObject.get("tx").asString) }
            }
            java.io.File(context.noBackupFilesDir, "feed.owner.v1").writeText(cache.toString())
            ActivityScenario.launch<MainActivity>(intent).use {
                try {
                    waitFor("Newer unrelated cached post 100")
                    ui.onNodeWithText("Following").performClick()
                    waitFor("Native Red Square reference post")
                    ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
                    assertPostAbsent("Newer unrelated cached post 100", "Native Red Square reference post")
                    peer.assertHealthy()
                    captureFeedEvidence("following-evidence/followed-post-with-full-general-cache.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("following-evidence/retention-first-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun restoredFollowChoiceStorageCanBeRetriedWithoutRestarting() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = readReference("native-following-reference.json")
        val root = fixture.getAsJsonObject("root")
        val author = root.get("field2").asString
        val peer = ControlledFeedPeer("[$root]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            if (criteria.has("field2")) check(criteria.get("field2").asString == author)
            "[$root]"
        }
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            lateinit var choices: java.io.File
            ActivityScenario.launch<MainActivity>(intent).use {
                waitFor("Native Red Square reference post")
                ui.onNodeWithText("Wallet").performClick()
                choices = java.io.File(context.noBackupFilesDir, "following.${displayedWalletAddress(ui)}.v1")
                ui.onNodeWithText("Done").performClick()
                openFeedProfile(author)
                waitFor("Follow")
                ui.onNodeWithText("Follow").performClick()
                waitFor("Unfollow")
            }
            val savedPublicChoices = choices.readBytes()
            check(choices.delete() && choices.mkdir())
            val obstacle = java.io.File(choices, "public-read-fault").apply { writeText("synthetic test only") }
            try {
                ActivityScenario.launch<MainActivity>(intent).use {
                    try {
                        waitFor("Native Red Square reference post")
                        ui.onNodeWithText("Following").performClick()
                        waitFor(context.getString(R.string.following_error))
                        check(obstacle.delete() && choices.delete())
                        choices.writeBytes(savedPublicChoices)
                        ui.onNodeWithText("Retry").performClick()
                        waitFor("Native Red Square reference post")
                        ui.onNodeWithText(context.getString(R.string.following_error)).assertDoesNotExist()
                        ui.onNodeWithText("Loading follow choices").assertDoesNotExist()
                        openFeedProfile(author)
                        waitFor("Unfollow")
                        peer.assertHealthy()
                        captureFeedEvidence("following-evidence/follow-choice-read-retry.png")
                    } catch (failure: Throwable) {
                        captureFeedEvidence("following-evidence/choice-read-first-failure.png")
                        throw failure
                    }
                }
            } finally {
                if (obstacle.exists()) check(obstacle.delete())
                if (choices.isDirectory) check(choices.delete())
                if (!choices.exists()) choices.writeBytes(savedPublicChoices)
            }
        }
    }

    @Test fun validFollowedAuthorResultsRemainAvailableWhenAnotherAuthorFails() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = readReference("native-following-reference.json")
        val paging = readReference("native-profile-paging-reference.json")
        val root = fixture.getAsJsonObject("root")
        val other = fixture.getAsJsonObject("otherRoot")
        val newer = paging.getAsJsonArray("recentPosts")[0].asJsonObject
        val firstAuthor = root.get("field2").asString
        val secondAuthor = other.get("field2").asString
        check(firstAuthor < secondAuthor)
        val peer = ControlledFeedPeer("[$root,$other]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            when (criteria.get("field2")?.asString) {
                firstAuthor -> if (criteria.has("field4")) "[$newer]" else "[$root]"
                secondAuthor -> if (criteria.has("field4")) "{" else "[$other]"
                null -> "[$root,$other]"
                else -> error("Unexpected archive author")
            }
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                try {
                    waitFor("Native Red Square reference post")
                    for (author in listOf(firstAuthor, secondAuthor)) {
                        openFeedProfile(author)
                        waitFor("Follow")
                        ui.onNodeWithText("Follow").performClick()
                        waitFor("Unfollow")
                        ui.onNodeWithText("Back").performClick()
                    }
                    ui.onNodeWithText("Following").performClick()
                    waitFor("Profile newer post 20")
                    waitFor("Server unavailable")
                    peer.assertHealthy()
                    captureFeedEvidence("following-evidence/partial-author-results.png")
                    server.close()
                    scenario.recreate()
                    waitFor("Profile newer post 20")
                    waitFor("Server unavailable")
                    ui.onNodeWithText("Profile newer post 20").assertIsDisplayed()
                    captureFeedEvidence("following-evidence/partial-author-results-cached.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("following-evidence/partial-author-first-failure.png")
                    throw failure
                }
            }
        }
    }

    @Test fun followingAnAccountRestrictsTheFeedAndUnfollowingRemovesItsPosts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = readReference("native-following-reference.json")
        val root = fixture.getAsJsonObject("root")
        val other = fixture.getAsJsonObject("otherRoot")
        val author = root.get("field2").asString
        val peer = ControlledFeedPeer("[$root,$other]") { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            if (criteria.has("field2")) {
                check(criteria.get("field2").asString == author)
                "[$root]"
            } else "[$root,$other]"
        }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                try {
                    waitFor("Native Red Square reference post")
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText("Another account reference post"))
                    ui.onNodeWithText("Another account reference post").assertIsDisplayed()
                    openFeedProfile(author)
                    waitFor("Profile")
                    ui.onNodeWithText("Follow").performClick()
                    waitFor("Unfollow")
                    ui.onNodeWithText("Back").performClick()
                    ui.onNodeWithText("Following").performClick()
                    waitFor("Native Red Square reference post")
                    assertPostAbsent("Another account reference post", "Native Red Square reference post")
                    openFeedProfile(author)
                    waitFor("Unfollow")
                    ui.onNodeWithText("Unfollow").performClick()
                    waitFor("Follow")
                    ui.onNodeWithText("Back").performClick()
                    waitFor("Follow accounts to see their posts here.")
                    assertPostAbsent("Native Red Square reference post")
                    peer.assertHealthy()
                    captureFeedEvidence("following-evidence/unfollowed-empty-feed.png")
                } catch (failure: Throwable) {
                    captureFeedEvidence("following-evidence/following-first-failure.png")
                    throw failure
                }
            }
        }
    }

    private fun openFeedProfile(author: String) {
        // A visible post body does not guarantee its author link is in the viewport.
        ui.onNode(hasScrollAction()).performScrollToNode(hasText(author))
        ui.onNodeWithText(author).assertIsDisplayed().performClick()
        waitFor("Profile")
    }

    private fun readReference(name: String): com.google.gson.JsonObject =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject
        }

    private fun assertPostAbsent(text: String, returnTo: String? = null) {
        val list = ui.onNode(hasScrollAction())
        val failure = org.junit.Assert.assertThrows(AssertionError::class.java) {
            list.performScrollToNode(hasText(text))
        }
        org.junit.Assert.assertTrue("The complete list search must finish without this post",
            failure.message?.contains("No node found that matches") == true)
        returnTo?.let { list.performScrollToNode(hasText(it)) }
    }

    private fun waitFor(text: String) {
        if (text in setOf("Native Red Square reference post", "Profile newer post 20")) {
            ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            ui.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        }
        ui.waitUntil(15_000) {
            ui.onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }
}
