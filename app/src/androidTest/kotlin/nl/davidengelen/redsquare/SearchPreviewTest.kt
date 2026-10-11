package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class SearchPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun knownAuthorsAndVerifiedNamesRemainSearchableByPrefixAfterReopeningOffline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            listOf("feed.", "conversation.", "profile.", "search.", "authorposts.").any(it.name::startsWith)
        }?.forEach { check(it.delete()) { "Could not reset public search fixtures" } }
        val thread = instrumentation.context.assets.open("native-thread-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val names = instrumentation.context.assets.open("native-registry-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("rows") }
        val author = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU"
        val named = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        listOf("", ".bak", ".new").forEach { suffix ->
            File(context.noBackupFilesDir, "namemap.$named.v1$suffix").takeIf { it.exists() }?.let { check(it.delete()) }
        }
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            when {
                criteria.has("field4") -> "[]"
                criteria.has("field2") -> {
                    check(criteria.get("field2").asString in setOf(author, named))
                    "[]"
                }
                else -> {
                    check(criteria.keySet() == setOf("request", "field1", "flagged_ne", "limit", "created_earlier_than"))
                    "[${thread.get("root")},${thread.get("firstReply")}]"
                }
            }
        }, registryResponse = { criteria ->
            if (criteria.get("request").asString == "registry namecheck") {
                check(criteria.get("identifier").asString == "NativeCase7@saito")
                names.toString()
            } else com.google.gson.JsonObject().apply {
                if (criteria.getAsJsonArray("keys").any { it.asString == named }) addProperty(named, "NativeCase7@saito")
            }.toString()
        })
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("usernameFixtureRegistrar", true)
            var owner = ""
            ActivityScenario.launch<MainActivity>(intent).use {
                withFailureCapture({ captureNativeScreen(File(context.cacheDir, "search-evidence/people-prefix-online-failure.png")) }) {
                    waitForEnabled("New post")
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    ui.onNodeWithText("Wallet").performClick()
                    owner = displayedWalletAddress(ui)
                    ui.onNodeWithText("Done").performClick()
                    ui.onNodeWithText("Search").performClick()
                    ui.onNode(hasSetTextAction()).performTextInput("Independent")
                    waitForEnabled("Search history")
                    ui.onNodeWithText("Search history").performClick()
                    waitForEnabled("Search history")
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                    ui.onNode(hasSetTextAction()).performTextReplacement("pncSjs")
                    ui.onNodeWithText("Search history").performClick()
                    waitForEnabled("Search history")
                    ui.onNode(hasText(author) and hasClickAction() and !hasSetTextAction()).assertIsDisplayed()
                    captureNativeScreen(File(context.cacheDir, "search-evidence/people-author-prefix.png"), author)
                    ui.onNode(hasSetTextAction()).performTextReplacement(named)
                    ui.onNodeWithText("Search history").performClick()
                    waitForEnabled("Search history")
                    ui.onNode(hasText(named) and hasClickAction() and !hasSetTextAction()).performClick()
                    waitForText("Profile")
                    waitForText("NativeCase7@saito")
                    ui.onNodeWithText(named).assertIsDisplayed()
                    ui.waitUntil(20_000) {
                        ui.onAllNodes((hasText("Follow") or hasText("Unfollow")) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                    if (ui.onAllNodes(hasText("Unfollow") and isEnabled()).fetchSemanticsNodes().isNotEmpty())
                        ui.onNodeWithText("Unfollow").performClick()
                    waitForEnabled("Follow")
                    ui.onNodeWithText("Follow").performClick()
                    waitForEnabled("Unfollow")
                    ui.onNodeWithText("Back").performClick()
                    ui.onNode(hasSetTextAction()).performTextReplacement("NativeCase7")
                    ui.onNodeWithText("Search history").performClick()
                    waitForEnabled("Search history")
                    waitForText("NativeCase7@saito")
                    ui.onNode(hasText(named) and hasClickAction() and !hasSetTextAction()).assertIsDisplayed()
                    assertTrue(peer.registryQueries.any { it.get("request").asString == "registry namecheck" &&
                        it.get("identifier").asString == "NativeCase7@saito" })
                    peer.assertHealthy()
                }
            }
            server.close()
            // Model unavailable public post history. Keep the real Follow choice
            // and verified public name cache; the wallet is never cleared.
            context.noBackupFilesDir.listFiles()?.filter {
                listOf("feed.", "conversation.", "profile.", "search.", "authorposts.").any(it.name::startsWith)
            }?.forEach { check(it.delete()) { "Could not reset public history fixture" } }
            ActivityScenario.launch<MainActivity>(intent).use {
                withFailureCapture({ captureNativeScreen(File(context.cacheDir, "search-evidence/people-prefix-offline-failure.png")) }) {
                    waitForEnabled("New post")
                    ui.onNodeWithText("Search").performClick()
                    ui.onNode(hasSetTextAction()).performTextInput("NativeCase7")
                    waitForEnabled("Search history")
                    ui.onNodeWithText("Search history").performClick()
                    waitForText("Some history could not be searched. Available results are still shown. Try again.")
                    waitForText("NativeCase7@saito")
                    ui.onNode(hasText(named) and hasClickAction() and !hasSetTextAction()).assertIsDisplayed()
                    ui.onNodeWithText("Server: saito.davidengelen.nl").assertIsDisplayed()
                    captureNativeScreen(File(context.cacheDir, "search-evidence/people-offline-name-prefix.png"), named)
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(owner, displayedWalletAddress(ui))
                    assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == owner })
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun anExactPublicKeyOpensItsProfileButAnInvalidCurvePointDoesNotBecomeAPerson() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            listOf("feed.", "conversation.", "profile.", "search.", "authorposts.").any(it.name::startsWith)
        }?.forEach { check(it.delete()) { "Could not reset public search fixtures" } }
        // Independent published scalar1 key. The invalid key encodes 02 followed
        // by 32 FF bytes: its x coordinate is outside the secp256k1 field.
        val person = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        val invalid = "tgx7VNFoP9DJiFMFgXXtafQZkUvyEdDHT9ryamHJYrjp"
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            if (criteria.has("field2")) check(criteria.get("field2").asString == person)
            "[]"
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                withFailureCapture({ captureNativeScreen(File(context.cacheDir, "search-evidence/people-key-failure.png")) }) {
                    waitForEnabled("New post")
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    ui.onNodeWithText("Wallet").performClick()
                    val owner = displayedWalletAddress(ui)
                    ui.onNodeWithText("Done").performClick()
                    ui.onNodeWithText("Search").performClick()
                    ui.onNode(hasSetTextAction()).performTextInput(person)
                    waitForEnabled("Search history")
                    ui.onNodeWithText("Search history").performClick()
                    waitForEnabled("Search history")
                    // The editable query itself is never proof of a result.
                    val result = hasText(person) and hasClickAction() and !hasSetTextAction()
                    ui.onNode(result).assertIsDisplayed().performClick()
                    waitForText("Profile")
                    ui.onNodeWithText(person).assertIsDisplayed()
                    ui.onNodeWithText("Back").performClick()
                    ui.onNode(result).assertIsDisplayed()
                    scenario.recreate()
                    ui.waitUntil(20_000) { ui.onAllNodes(result).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(result).assertIsDisplayed()
                    ui.onNode(hasSetTextAction()).assertTextContains(person)
                    captureNativeScreen(File(context.cacheDir, "search-evidence/people-key-recreated.png"), person)
                    ui.onNode(hasSetTextAction()).performTextReplacement(invalid)
                    waitForEnabled("Search history")
                    ui.onNodeWithText("Search history").performClick()
                    waitForEnabled("Search history")
                    ui.onAllNodes(hasText(invalid) and !hasSetTextAction()).assertCountEquals(0)
                    ui.onAllNodes(result).assertCountEquals(0)
                    ui.onNodeWithText("No matching posts in the history searched so far.").assertIsDisplayed()
                    captureNativeScreen(File(context.cacheDir, "search-evidence/people-invalid-key.png"))
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(owner, displayedWalletAddress(ui))
                    assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == owner })
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun anExactSignedUsernameOpensTheCorrectProfileAndRetainsTheSearch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            listOf("feed.", "conversation.", "profile.", "search.", "authorposts.").any(it.name::startsWith)
        }?.forEach { check(it.delete()) { "Could not reset public search fixtures" } }
        val rows = instrumentation.context.assets.open("native-registry-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("rows") }
        val name = "NativeCase7@saito"
        val person = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            when {
                criteria.has("field4") -> "[]"
                criteria.has("field2") -> {
                    check(criteria.get("field2").asString == person)
                    "[]"
                }
                else -> {
                    check(criteria.keySet() == setOf("request", "field1", "flagged_ne", "limit", "created_earlier_than"))
                    check(criteria.get("limit").asInt == 100 && criteria.get("flagged_ne").asInt == 1)
                    "[]"
                }
            }
        }, registryResponse = { criteria ->
            if (criteria.get("request").asString == "registry namecheck") {
                check(criteria.get("identifier").asString == name)
                rows.toString()
            } else "{}" // Unsigned hints cannot supply the result.
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("usernameFixtureRegistrar", true)).use { scenario ->
                withFailureCapture({ captureNativeScreen(File(context.cacheDir, "search-evidence/people-name-failure.png")) }) {
                    waitForEnabled("New post")
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    ui.onNodeWithText("Wallet").performClick()
                    val owner = displayedWalletAddress(ui)
                    ui.onNodeWithText("Done").performClick()
                    ui.onNodeWithText("Search").performClick()
                    ui.onNode(hasSetTextAction()).performTextInput(name)
                    waitForEnabled("Search history")
                    ui.onNodeWithText("Search history").performClick()
                    waitForEnabled("Search history")
                    ui.onNodeWithText(person).assertIsDisplayed()
                    assertTrue("Exact lookup must use a registrar-signed namecheck", peer.registryQueries.any {
                        it.get("request").asString == "registry namecheck" && it.get("identifier").asString == name
                    })
                    ui.onNodeWithText(person).performClick()
                    waitForText("Profile")
                    ui.onNodeWithText(person).assertIsDisplayed()
                    ui.onNodeWithText("Back").performClick()
                    ui.onNode(hasSetTextAction()).assertTextContains(name)
                    ui.onNodeWithText(person).assertIsDisplayed()
                    scenario.recreate()
                    waitForText(person)
                    ui.onNode(hasSetTextAction()).assertTextContains(name)
                    ui.onNodeWithText(person).assertIsDisplayed()
                    captureNativeScreen(File(context.cacheDir, "search-evidence/people-name-recreated.png"), person)
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(owner, displayedWalletAddress(ui))
                    assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == owner })
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun reopeningOfflineKeepsSearchHistoryIsolatedBetweenSelectedServers() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            listOf("feed.", "conversation.", "profile.", "search.", "authorposts.").any(it.name::startsWith)
        }?.forEach { check(it.delete()) { "Could not reset public search fixtures" } }
        val reply = instrumentation.context.assets.open("native-thread-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("firstReply") }
        val owner = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            if (criteria.has("field4")) "[]" else {
                check(criteria.keySet() == setOf("request", "field1", "flagged_ne", "limit", "created_earlier_than"))
                check(criteria.get("limit").asInt == 100 && criteria.get("flagged_ne").asInt == 1)
                "[$reply]"
            }
        })
        val official = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            if (!criteria.has("field4")) {
                check(criteria.keySet() == setOf("request", "field1", "flagged_ne", "limit", "created_earlier_than"))
                check(criteria.get("limit").asInt == 100 && criteria.get("flagged_ne").asInt == 1)
            }
            "[]"
        })
        owner.start().use { ownerServer ->
            official.start().use { officialServer ->
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")
                var address = ""
                ActivityScenario.launch<MainActivity>(intent).use {
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir, "search-evidence/cache-online-failure.png")) }) {
                        waitForEnabled("New post")
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                        ui.onNodeWithText("Wallet").performClick()
                        address = displayedWalletAddress(ui)
                        ui.onNodeWithText("Done").performClick()
                        ui.onNodeWithText("Search").performClick()
                        ui.onNode(hasSetTextAction()).performTextInput("Independent")
                        ui.onNodeWithText("Search history").performClick()
                        waitForText("Independent Saito reply")
                        waitForEnabled("Search history")
                    }
                }
                ownerServer.close()
                ActivityScenario.launch<MainActivity>(intent).use {
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir, "search-evidence/cache-offline-failure.png")) }) {
                        waitForEnabled("New post")
                        ui.onNodeWithText("Search").performClick()
                        ui.onNode(hasSetTextAction()).performTextInput("Independent")
                        ui.onNodeWithText("Search history").performClick()
                        waitForText("Some history could not be searched. Available results are still shown. Try again.")
                        ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                        ui.onAllNodesWithText("No matching posts in the history searched so far.").assertCountEquals(0)
                        ui.onNodeWithText("Server: saito.davidengelen.nl").assertIsDisplayed()
                        assertTrue("No automatic fallback is permitted", official.clientKeys.isEmpty())
                        captureNativeScreen(File(context.cacheDir, "search-evidence/cache-offline-retained.png"), "Independent Saito reply")
                        chooseServer("saito.io")
                        waitForText("Server: saito.io")
                        ui.onNodeWithText("Search").performClick()
                        ui.onNode(hasSetTextAction()).performTextInput("Independent")
                        ui.onNodeWithText("Search history").performClick()
                        waitForText("No matching posts in the history searched so far.")
                        ui.onAllNodesWithText("Independent Saito reply").assertCountEquals(0)
                        captureNativeScreen(File(context.cacheDir, "search-evidence/cache-official-isolated.png"))
                        chooseServer("saito.davidengelen.nl")
                        waitForText("Server: saito.davidengelen.nl")
                        ui.onNode(hasSetTextAction()).assertTextContains("Independent")
                        ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                        ui.onNodeWithText("Wallet").performClick()
                        assertEquals(address, displayedWalletAddress(ui))
                        assertTrue(owner.clientKeys.isNotEmpty() && official.clientKeys.isNotEmpty())
                        assertTrue((owner.clientKeys + official.clientKeys).all { it == address })
                        assertTrue(owner.transactions.isEmpty() && official.transactions.isEmpty())
                        owner.assertHealthy()
                        official.assertHealthy()
                    }
                }
            }
        }
    }

    @Test fun partialHistoryCanBeRetriedAndPagedWithoutLosingVerifiedResults() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            listOf("feed.", "conversation.", "profile.", "search.", "authorposts.").any(it.name::startsWith)
        }?.forEach { check(it.delete()) { "Could not reset public search fixtures" } }
        val reference = instrumentation.context.assets.open("native-search-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val firstPage = reference.getAsJsonArray("firstPage")
        val older = reference.getAsJsonObject("older")
        val original = reference.getAsJsonObject("original")
        val quote = reference.getAsJsonObject("currentQuote")
        val postReads = AtomicInteger()
        val quoteReads = AtomicInteger()
        val quoteAvailable = java.util.concurrent.atomic.AtomicBoolean(false)
        val peer = ControlledFeedPeer("[]", quoteArchiveResponse = { criteria ->
            if (criteria.get("limit").asInt != 100) "[]" else {
                check(criteria.keySet() == setOf("request", "field1", "flagged_ne", "limit", "created_earlier_than"))
                quoteReads.incrementAndGet()
                if (quoteAvailable.get()) "[$quote]" else "{\"err\":\"Controlled unavailable quote history\"}"
            }
        }, archiveResponse = { criteria ->
            check(criteria.get("field1").asString == "RedSquare")
            when {
                criteria.has("field4") -> "[]"
                criteria.has("sig") -> {
                    check(criteria.get("sig").asString == original.get("sig").asString)
                    "[$original]"
                }
                else -> {
                    check(criteria.keySet() == setOf("request", "field1", "flagged_ne", "limit", "created_earlier_than"))
                    check(criteria.get("flagged_ne").asInt == 1 && criteria.get("limit").asInt == 100)
                    postReads.incrementAndGet()
                    val before = criteria.get("created_earlier_than").asLong
                    when {
                        before > 1700000100100 -> firstPage.toString()
                        before == 1700000100001 -> "[$older]"
                        else -> error("Unexpected ordinary search cursor: $before")
                    }
                }
            }
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                withFailureCapture({ captureNativeScreen(File(context.cacheDir, "search-evidence/partial-history-failure.png")) }) {
                    waitForEnabled("New post")
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    ui.onNodeWithText("Wallet").performClick()
                    val owner = displayedWalletAddress(ui)
                    ui.onNodeWithText("Done").performClick()
                    ui.onNodeWithText("Search").performClick()
                    ui.onNode(hasSetTextAction()).performTextInput("Available")
                    ui.onNodeWithText("Search history").performClick()
                    waitForText("Some history could not be searched. Available results are still shown. Try again.")
                    ui.onNodeWithText("Available newest result").assertIsDisplayed()
                    ui.onAllNodesWithText("No matching posts in the history searched so far.").assertCountEquals(0)
                    captureNativeScreen(File(context.cacheDir, "search-evidence/partial-history-retained.png"), "Available newest result")
                    scenario.recreate()
                    waitForText("Available newest result")
                    assertEquals("Recreation must not repeat a completed read", 1, postReads.get())
                    quoteAvailable.set(true)
                    ui.onNodeWithText("Retry search").assertIsEnabled().performClick()
                    waitForEnabled("Load more")
                    ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(hasText("Available quote result"))
                    ui.onNodeWithText("Available quote result").assertIsDisplayed()
                    assertEquals("Retry must not repeat the successful ordinary stream", 1, postReads.get())
                    assertEquals(2, quoteReads.get())
                    ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(hasText("Available newest result"))
                    ui.onNodeWithText("Available newest result").assertIsDisplayed()
                    ui.onNodeWithText("Load more").performClick()
                    ui.waitUntil(20_000) {
                        postReads.get() == 2 && ui.onAllNodes(hasText("Search history") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                    }
                    ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(hasText("Available older result"))
                    ui.onNodeWithText("Available older result").assertIsDisplayed()
                    assertEquals(2, postReads.get())
                    assertEquals("An exhausted quote stream must not be loaded again", 2, quoteReads.get())
                    ui.onNodeWithText("Load more").assertIsNotEnabled()
                    ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(hasText("Available newest result"))
                    ui.onNodeWithText("Available newest result").assertIsDisplayed()
                    ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(hasText("Available older result"))
                    ui.onNodeWithText("Available older result").assertIsDisplayed()
                    captureNativeScreen(File(context.cacheDir, "search-evidence/older-history-result.png"), "Available older result")
                    ui.onNode(hasSetTextAction()).performTextReplacement("No matching phrase")
                    ui.onNodeWithText("Search history").performClick()
                    waitForText("No matching posts in the history searched so far.")
                    ui.onAllNodesWithText("Some history could not be searched. Available results are still shown. Try again.").assertCountEquals(0)
                    ui.onNodeWithText("Only available history is searched. Older posts and people may be missing.").assertIsDisplayed()
                    ui.onNodeWithText("Back").performClick()
                    waitForEnabled("New post")
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(owner, displayedWalletAddress(ui))
                    assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == owner })
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun anArchivedReplyCanBeFoundAndExploredWithoutLosingTheSearchOrWallet() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        context.noBackupFilesDir.listFiles()?.filter {
            listOf("feed.", "conversation.", "profile.", "search.", "authorposts.").any(it.name::startsWith)
        }?.forEach { check(it.delete()) { "Could not reset public search fixtures" } }
        val reference = instrumentation.context.assets.open("native-thread-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val root = reference.getAsJsonObject("root")
        val reply = reference.getAsJsonObject("firstReply")
        val author = reply.get("field2").asString
        val searches = AtomicInteger()
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            check(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1)
            when {
                criteria.has("field4") -> "[]" // General feed has no matching reply.
                criteria.has("field5") -> {
                    check(criteria.get("field5").asString == root.get("sig").asString)
                    "[$root,$reply]"
                }
                criteria.has("field2") -> {
                    check(criteria.get("field2").asString == author)
                    "[$reply]"
                }
                else -> {
                    check(criteria.keySet() == setOf("request", "field1", "flagged_ne", "limit", "created_earlier_than"))
                    check(criteria.get("limit").asInt == 100 && criteria.get("created_earlier_than").asLong > 0)
                    searches.incrementAndGet()
                    "[$root,$reply]"
                }
            }
        })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                withFailureCapture({ captureNativeScreen(File(context.cacheDir, "search-evidence/reply-first-failure.png")) }) {
                    waitForEnabled("New post")
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    ui.onNodeWithText("Wallet").performClick()
                    val original = displayedWalletAddress(ui)
                    ui.onNodeWithText("Done").performClick()
                    ui.onAllNodesWithText("Independent Saito reply").assertCountEquals(0)
                    ui.onNodeWithText("Search").assertIsDisplayed().performClick()
                    ui.onNode(hasSetTextAction()).performTextInput("Independent")
                    ui.onNodeWithText("Search history").assertIsEnabled().performClick()
                    waitForText("Independent Saito reply")
                    assertTrue("Search must read the selected backend beyond its root feed", searches.get() > 0)
                    ui.onNodeWithText("Only available history is searched. Older posts and people may be missing.").assertIsDisplayed()
                    ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(hasText("Independent Saito reply"))
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed().performClick()
                    waitForEnabled("Reply")
                    ui.onNodeWithText("Conversation").assertIsDisplayed()
                    ui.onNode(hasText("Anon-${author.take(6)}") and hasParent(hasAnyChild(hasText("Independent Saito reply"))),
                        useUnmergedTree = true).performClick()
                    waitForText("Profile")
                    ui.onNodeWithText(author).assertIsDisplayed()
                    ui.onNodeWithText("Back").performClick()
                    waitForText("Conversation")
                    ui.onNodeWithText("Back").performClick()
                    waitForText("Independent Saito reply")
                    ui.onNode(hasSetTextAction()).assertTextContains("Independent")
                    scenario.recreate()
                    waitForText("Independent Saito reply")
                    ui.onNode(hasSetTextAction()).assertTextContains("Independent")
                    ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(hasText("Independent Saito reply"))
                    ui.onNodeWithText("Independent Saito reply").assertIsDisplayed()
                    captureNativeScreen(File(context.cacheDir, "search-evidence/reply-search-recreated.png"), "Independent Saito reply")
                    ui.onNodeWithText("Back").performClick()
                    waitForEnabled("New post")
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(original, displayedWalletAddress(ui))
                    assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == original })
                    assertTrue("Search and navigation must never send a transaction", peer.transactions.isEmpty())
                    peer.assertHealthy()
                }
            }
        }
    }

    private fun chooseServer(domain: String) {
        ui.onNodeWithText("Settings").performClick()
        waitForText(domain)
        ui.onNodeWithText(domain).performClick()
    }

    private fun waitForText(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForEnabled(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
}
