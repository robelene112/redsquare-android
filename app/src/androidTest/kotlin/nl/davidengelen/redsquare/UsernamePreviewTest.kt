package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoRegistry
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.InetAddress
import mockwebserver3.MockWebServer
import mockwebserver3.MockResponse

class UsernamePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun prepare() {
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        context.noBackupFilesDir.listFiles()?.filter {
            it.name.startsWith("namerequest.") || it.name.startsWith("namemap.")
        }?.forEach { check(it.delete()) { "Could not reset emulator username fixtures" } }
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
    }

    @Test fun aVerifiedOtherAccountNameIsSharedByFeedProfileConversationAndReply() = withRegistryWallet(
        SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe reader fixture; name belongs to scalar1.
    ) { reader ->
        val registry = instrumentation.context.assets.open("native-registry-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val post = instrumentation.context.assets.open("native-posting-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val transaction = nl.davidengelen.redsquare.saito.SaitoTransaction.restore(post.get("networkBase64").asString)
        val row = postingArchiveRow(transaction)
        val author = post.get("author").asString
        assertNotEquals(reader.publicKey, author)
        val text = post.getAsJsonObject("body").getAsJsonObject("data").get("text").asString
        val peer = discoveryPeer(author, "[$row]") { registry.getAsJsonArray("rows").toString() }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("usernameFixtureRegistrar", true)).use {
                withFailureCapture({ capture("username-shared-name-failure.png") }) {
                    waitForEnabled("New post")
                    peer.assertRequestObserved()
                    waitForEnabled("Refresh") // Archive observation precedes signed post parsing and publication.
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    ui.onNode(hasScrollAction()).performScrollToNode(hasText(text))
                    val targetAuthor = hasText("NativeCase7@saito") and hasParent(hasAnyChild(hasText(text)))
                    ui.waitUntil(20_000) { ui.onAllNodes(targetAuthor, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(targetAuthor, useUnmergedTree = true).assertIsDisplayed()
                    assertTrue(peer.registryQueries.any { it.get("request").asString == "registry query" &&
                        it.getAsJsonArray("keys").any { key -> key.asString == author } })
                    assertTrue(peer.registryQueries.any { it.get("request").asString == "registry namecheck" })
                    capture("username-shared-feed.png", "NativeCase7@saito")
                    ui.onNode(targetAuthor, useUnmergedTree = true).performClick()
                    waitForText("Profile")
                    ui.onNodeWithText(author).assertIsDisplayed()
                    ui.onNode(hasText("NativeCase7@saito") and hasAnySibling(hasText(author)), useUnmergedTree = true).assertIsDisplayed()
                    ui.onNodeWithText("Back").performClick()
                    waitForText(text)
                    ui.clickVisiblePostContent(text)
                    waitForEnabled("Reply")
                    ui.onNode(targetAuthor, useUnmergedTree = true).assertIsDisplayed()
                    capture("username-shared-conversation.png", "NativeCase7@saito")
                    ui.onNodeWithText("Reply").performClick()
                    waitForText("Replying to NativeCase7@saito")
                    ui.onNodeWithText("Replying to NativeCase7@saito").assertIsDisplayed()
                    ui.onNodeWithText("Back").performClick()
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(reader.publicKey, displayedWalletAddress(ui))
                    assertTrue(peer.clientKeys.all { it == reader.publicKey })
                    assertTrue("Name display must not register or publish a post", peer.transactions.isEmpty())
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun settingsDiscoversAnExistingUsernameWithoutOpeningTheFeedOrProfile() = withRegistryWallet { fixture ->
        val reference = instrumentation.context.assets.open("native-registry-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val peer = discoveryPeer(fixture.publicKey) { reference.getAsJsonArray("rows").toString() }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                // Isolate Settings discovery from names already looked up by cached feed rows.
                .putExtra("feedDisabled", true)
                .putExtra("usernameFixtureRegistrar", true)).use {
                withFailureCapture({ capture("username-settings-discovery-failure.png") }) {
                    waitForEnabled("Settings")
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    ui.onNodeWithText("Settings").performClick()
                    ui.onNodeWithText("Saito username").performClick()
                    waitForText("This username is registered to your wallet.")
                    ui.onNodeWithText("NativeCase7@saito").assertIsDisplayed()
                    ui.onAllNodesWithText("Register name").assertCountEquals(0)
                    ui.onAllNodesWithText("Retry registration").assertCountEquals(0)
                    assertTrue(peer.registryQueries.any { it.get("request").asString == "registry query" &&
                        it.getAsJsonArray("keys").any { key -> key.asString == fixture.publicKey } })
                    assertTrue(peer.registryQueries.any { it.get("request").asString == "registry namecheck" &&
                        it.get("identifier").asString == "NativeCase7@saito" })
                    capture("username-settings-discovered.png", "NativeCase7@saito")
                    ui.onNodeWithText("Back").performClick()
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(fixture.publicKey, displayedWalletAddress(ui))
                    assertTrue(peer.clientKeys.all { it == fixture.publicKey })
                    assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                }
            }
        }
    }

    @Test fun importingAWalletDiscoversItsExistingNameWithoutRegisteringAgain() = withRegistryWallet(
        SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    ) { outgoing ->
        val incoming = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        val filename = "00-username-named-import.json"
        val device = UiDevice.getInstance(instrumentation)
        check(device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test stat -c %s files/wallet-test-documents/$filename")
            .trim().toLongOrNull()?.let { it > 0 } == true)
        val reference = instrumentation.context.assets.open("native-registry-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val peer = discoveryPeer(incoming) { reference.getAsJsonArray("rows").toString() }
        val balanceServer = zeroBalanceServer()
        var primary: Throwable? = null
        try {
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedDisabled", true)
                    .putExtra("usernameFixtureRegistrar", true)
                    .putExtra("balanceApiUrl", "http://127.0.0.1:${balanceServer.port}/balance/")).use { scenario ->
                    withFailureCapture({ capture("username-named-import-failure.png") }) {
                        waitForEnabled("Wallet")
                        ui.onNodeWithText("Wallet").performClick()
                        assertEquals(outgoing.publicKey, displayedWalletAddress(ui))
                        assertTrue("No feed/profile may seed the incoming name before import", peer.registryQueries.isEmpty())
                        ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                        chooseWalletImport(ui, filename, incoming)
                        waitForText("Server reports zero SAITO")
                        ui.onNodeWithText("Skip backup").performScrollTo().performClick()
                        ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
                        ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsEnabled().performClick()
                        waitForText("Wallet imported")
                        assertEquals(incoming, displayedWalletAddress(ui))
                        ui.onNodeWithText("Done").performClick()
                        fun discoverInSettings() {
                            waitForEnabled("Settings")
                            ui.onNodeWithText("Settings").performClick()
                            ui.onNodeWithText("Saito username").performClick()
                            waitForText("This username is registered to your wallet.")
                            ui.onNodeWithText("NativeCase7@saito").assertIsDisplayed()
                            ui.onAllNodesWithText("Register name").assertCountEquals(0)
                            ui.onAllNodesWithText("Retry registration").assertCountEquals(0)
                        }
                        discoverInSettings()
                        assertTrue(peer.registryQueries.any { it.get("request").asString == "registry query" &&
                            it.getAsJsonArray("keys").any { key -> key.asString == incoming } })
                        fun namechecks() = peer.registryQueries.count { it.get("request").asString == "registry namecheck" &&
                            it.get("identifier")?.asString == "NativeCase7@saito" }
                        val checks = namechecks()
                        assertTrue("Imported name must be verified, not just copied from a hint", checks > 0)
                        capture("username-named-import.png", "NativeCase7@saito")
                        scenario.recreate()
                        discoverInSettings()
                        assertTrue(namechecks() > checks)
                        ui.onNodeWithText("Back").performClick()
                        ui.onNodeWithText("Wallet").performClick()
                        assertEquals(incoming, displayedWalletAddress(ui))
                        assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == incoming })
                        assertTrue("Import and name recovery must never register again", peer.transactions.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                val store = AndroidWalletStore(context)
                val active = store.loadExisting()
                check(active.publicKey == outgoing.publicKey || active.publicKey == incoming)
                if (active.publicKey == incoming) store.replace(active, outgoing)
            }, { balanceServer.close() }, {
                check(device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test rm files/wallet-test-documents/$filename").isBlank())
            })
        }
    }

    @Test fun aQuotedOriginalUsesItsVerifiedNameWithoutNamingTheQuoteAuthor() = withRegistryWallet(
        SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    ) { reader ->
        val registry = instrumentation.context.assets.open("native-registry-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val quotes = instrumentation.context.assets.open("native-quote-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val original = quotes.getAsJsonObject("original")
        val current = quotes.getAsJsonObject("current").deepCopy().apply {
            addProperty("field1", "RedSquareRetweet")
            addProperty("field4", original.get("sig").asString)
        }
        val author = original.get("field2").asString
        assertNotEquals(reader.publicKey, author)
        val peer = ControlledFeedPeer("[]", quoteArchiveResponse = { "[$current]" },
            registryResponse = nameDiscoveryResponse(author) { registry.getAsJsonArray("rows").toString() },
            archiveResponse = { criteria ->
                when {
                    criteria.get("field1").asString == "RedSquareRetweet" -> "[$current]"
                    criteria.get("sig")?.asString == original.get("sig").asString -> "[$original]"
                    else -> "[]"
                }
            })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("usernameFixtureRegistrar", true)).use {
                withFailureCapture({ capture("username-quoted-name-failure.png") }) {
                    waitForEnabled("Refresh")
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    ui.onNode(hasScrollAction(), useUnmergedTree = true)
                        .performScrollToNode(hasText("Current commentary retweet"))
                    val targetQuote = hasAnyAncestor(hasAnyChild(hasText("Current commentary retweet")))
                    val quotedName = hasText("NativeCase7@saito") and hasAnyAncestor(hasContentDescription("Quoted original")) and targetQuote
                    ui.waitUntil(20_000) { ui.onAllNodes(quotedName, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                    ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(quotedName)
                    ui.onNode(quotedName, useUnmergedTree = true).assertIsDisplayed()
                    val quotedText = hasText("Native Red Square reference post") and hasAnyAncestor(hasContentDescription("Quoted original")) and targetQuote
                    ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(quotedText)
                    ui.onNode(quotedText, useUnmergedTree = true).assertIsDisplayed()
                    ui.onNode(hasText("Anon-${reader.publicKey.take(6)}") and
                        hasParent(hasAnyChild(hasText("Current commentary retweet"))), useUnmergedTree = true).assertExists()
                    ui.onNode(hasScrollAction(), useUnmergedTree = true).performScrollToNode(quotedName)
                    ui.onNode(quotedName, useUnmergedTree = true).assertIsDisplayed()
                    capture("username-quoted-name.png", "NativeCase7@saito")
                    assertTrue(peer.registryQueries.any { it.get("request").asString == "registry namecheck" &&
                        it.get("identifier")?.asString == "NativeCase7@saito" })
                    assertTrue(peer.clientKeys.all { it == reader.publicKey })
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun anExistingWalletDiscoversItsVerifiedNameWithoutRegisteringAgain() = withRegistryWallet { fixture ->
        val reference = instrumentation.context.assets.open("native-registry-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val peer = discoveryPeer(fixture.publicKey) { reference.getAsJsonArray("rows").toString() }
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("usernameFixtureRegistrar", true)).use {
                withFailureCapture({ capture("username-discovery-failure.png") }) {
                    waitForEnabled("Your profile")
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                    ui.onNodeWithText("Your profile").performClick()
                    waitForText("NativeCase7@saito")
                    ui.onNode(hasText("NativeCase7@saito") and hasAnySibling(hasText(fixture.publicKey)), useUnmergedTree = true).assertIsDisplayed()
                    ui.onNodeWithText(fixture.publicKey).assertIsDisplayed()
                    assertTrue("Discovery must observe a key-map hint", peer.registryQueries.any { it.get("request").asString == "registry query" && it.getAsJsonArray("keys").any { key -> key.asString == fixture.publicKey } })
                    assertTrue("A hint alone is insufficient: verify its signed ownership", peer.registryQueries.any { it.get("request").asString == "registry namecheck" })
                    assertTrue("Discovering an existing name must never register it again", peer.transactions.isEmpty())
                    capture("username-discovered-profile.png", "NativeCase7@saito")
                    ui.onNodeWithText("Back").performClick()
                    openUsername()
                    waitForText("This username is registered to your wallet.")
                    ui.onNodeWithText("NativeCase7@saito").assertIsDisplayed()
                    ui.onAllNodesWithText("Register name").assertCountEquals(0)
                    ui.onAllNodesWithText("Retry registration").assertCountEquals(0)
                    ui.onNodeWithText("Back").performClick()
                    waitForEnabled("New post")
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(fixture.publicKey, displayedWalletAddress(ui))
                    assertTrue(peer.clientKeys.all { it == fixture.publicKey })
                    assertTrue(peer.transactions.isEmpty())
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun aVerifiedProfileNameRemainsVisibleWhenItsLocalCacheCannotBeSaved() = withRegistryWallet { fixture ->
        val reference = instrumentation.context.assets.open("native-registry-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val response = java.util.concurrent.atomic.AtomicReference(reference.getAsJsonArray("rows").toString())
        val peer = discoveryPeer(fixture.publicKey) { response.get() }
        val obstruction = File(context.noBackupFilesDir, "namemap.${fixture.publicKey}.v1")
        check(!obstruction.exists() && obstruction.mkdir())
        var primary: Throwable? = null
        try {
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("usernameFixtureRegistrar", true)).use { scenario ->
                    withFailureCapture({ capture("username-name-save-failure.png") }) {
                        waitForEnabled("Your profile")
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                        ui.onNodeWithText("Your profile").performClick()
                        ui.waitUntil(20_000) { peer.registryQueries.any { it.get("request").asString == "registry namecheck" } }
                        assertTrue("Fixture must obstruct only the local public-name cache", obstruction.isDirectory)
                        waitForText("NativeCase7@saito")
                        val warning = "Name verified, but could not be saved on this device."
                        waitForText(warning)
                        ui.onNode(hasText("NativeCase7@saito") and hasAnySibling(hasText(fixture.publicKey)), useUnmergedTree = true).assertIsDisplayed()
                        ui.onNodeWithText(warning).assertIsDisplayed()
                        ui.onNodeWithText(fixture.publicKey).assertIsDisplayed()
                        assertTrue(peer.transactions.isEmpty())
                        capture("username-name-save-error.png", warning)
                        check(obstruction.delete())
                        val checks = peer.registryQueries.count { it.get("request").asString == "registry namecheck" }
                        ui.onNodeWithText("Back").performClick()
                        waitForEnabled("Your profile")
                        ui.onNodeWithText("Your profile").performClick()
                        waitForText("NativeCase7@saito")
                        ui.waitUntil(20_000) {
                            peer.registryQueries.count { it.get("request").asString == "registry namecheck" } > checks
                        }
                        // A shared label retains its previous warning until the fresh proof/save settles.
                        ui.waitUntil(20_000) { ui.onAllNodesWithText(warning).fetchSemanticsNodes().isEmpty() }
                        ui.onNode(hasText("NativeCase7@saito") and hasAnySibling(hasText(fixture.publicKey)), useUnmergedTree = true).assertIsDisplayed()
                        ui.onAllNodesWithText(warning).assertCountEquals(0)
                        capture("username-name-save-recovered.png", "NativeCase7@saito")
                        response.set("{\"err\":\"no response\"}")
                        scenario.recreate()
                        waitForText("Profile") // The selected public profile survives Activity recreation.
                        waitForText("NativeCase7@saito")
                        ui.onNodeWithText(fixture.publicKey).assertIsDisplayed()
                        ui.onAllNodesWithText(warning).assertCountEquals(0)
                        capture("username-name-save-cached.png", "NativeCase7@saito")
                        assertTrue(peer.clientKeys.all { it == fixture.publicKey })
                        assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            try { if (obstruction.isDirectory) check(obstruction.delete()) }
            catch (cleanup: Throwable) { if (primary != null) primary.addSuppressed(cleanup) else throw cleanup }
        }
    }

    @Test fun anUnsavedVerifiedOwnNameIsNeverOfferedForRegistrationAgain() = withRegistryWallet { fixture ->
        val reference = instrumentation.context.assets.open("native-registry-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val response = java.util.concurrent.atomic.AtomicReference(reference.getAsJsonArray("rows").toString())
        val peer = ControlledFeedPeer("[]", registryResponse = { criteria ->
            if (criteria.get("request").asString == "registry query") "{}" else response.get()
        })
        val obstruction = File(context.noBackupFilesDir, "namemap.${fixture.publicKey}.v1")
        var primary: Throwable? = null
        try {
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedDisabled", true)
                    .putExtra("usernameFixtureRegistrar", true)).use {
                    withFailureCapture({ capture("username-unsaved-own-name-failure.png") }) {
                        waitForEnabled("Settings")
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("Saito username").performClick()
                        waitForEnabled("Check name")
                        check(!obstruction.exists() && obstruction.mkdir())
                        ui.onNode(hasSetTextAction()).performTextReplacement("NativeCase7")
                        ui.onNodeWithText("Check name").performClick()
                        waitForText("This username is registered to your wallet.")
                        val warning = "Your username was verified but could not be saved. Recovery on this device is not yet verified."
                        waitForText(warning)
                        ui.onNodeWithText("NativeCase7@saito").assertIsDisplayed()
                        response.set("[]")
                        val checks = peer.registryQueries.size
                        ui.onNodeWithText("Check name").performClick()
                        waitForEnabled("Check name")
                        assertTrue(peer.registryQueries.size > checks)
                        ui.onAllNodesWithText("Register name").assertCountEquals(0)
                        ui.onNodeWithText(warning).assertIsDisplayed()
                        ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("NativeCase7")))
                            .assertIsNotEnabled()
                        ui.onAllNodesWithText("This username is registered to your wallet.").assertCountEquals(0)
                        assertTrue(peer.transactions.isEmpty())
                        check(obstruction.delete())
                        response.set(reference.getAsJsonArray("rows").toString())
                        ui.onNodeWithText("Check name").performClick()
                        waitForText("This username is registered to your wallet.")
                        ui.onAllNodesWithText(warning).assertCountEquals(0)
                        ui.onAllNodesWithText("Register name").assertCountEquals(0)
                        assertTrue(peer.transactions.isEmpty())
                        assertEquals(fixture.publicKey, AndroidWalletStore(context).loadExisting().publicKey)
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { if (obstruction.isDirectory) check(obstruction.delete()) }) }
    }

    @Test fun anUnavailableUsernameCheckDoesNotBlockPostingOrChangeTheWallet() {
        val peer = ControlledFeedPeer("[]", registryResponse = response@ { criteria ->
            if (criteria.get("request").asString == "registry query") return@response "{}"
            assertEquals("registry namecheck", criteria.get("request").asString)
            assertEquals("NativeCase7@saito", criteria.get("identifier").asString)
            "{\"err\":\"no response\"}"
        })
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            ActivityScenario.launch<MainActivity>(intent).use {
                withFailureCapture({ capture("username-unavailable-failure.png") }) {
                    waitForEnabled("New post")
                    peer.assertRequestObserved()
                    val original = peer.clientKeys.first()
                    if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) {
                        ui.onNodeWithText("Later").performClick()
                    }
                    ui.onNodeWithText("Settings").performClick()
                    ui.onNodeWithText("Saito username").assertIsEnabled().performClick()
                    waitForEnabled("Check name")
                    ui.onNodeWithText("Choose a Saito username").assertIsDisplayed()
                    ui.onNode(hasSetTextAction()).performTextReplacement("NativeCase7")
                    ui.onNodeWithText("Check name").performClick()
                    val error = "Username could not be checked. Try again."
                    ui.waitUntil(20_000) { ui.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }
                    val device = UiDevice.getInstance(instrumentation)
                    if (device.hasObject(By.pkg("com.android.inputmethod.latin"))) {
                        check(device.pressBack()) // Hide the positively observed emulator IME, preserving this screen.
                        check(device.wait(Until.gone(By.pkg("com.android.inputmethod.latin")), 5_000))
                    }
                    ui.onNodeWithText(error).performScrollTo().assertIsDisplayed()
                    assertEquals("The native flow must actually check with Registry", 1, peer.registryQueries.count { it.get("request").asString == "registry namecheck" })
                    capture("username-unavailable.png", error)
                    ui.onNodeWithText("Back").assertIsEnabled().performClick()
                    waitForEnabled("New post")
                    ui.onNodeWithText("Wallet").performClick()
                    assertEquals(original, displayedWalletAddress(ui))
                    ui.onNodeWithContentDescription("Wallet address").assertIsDisplayed()
                    assertTrue("A name check must not publish a transaction", peer.transactions.isEmpty())
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun aPendingRegistrationSurvivesReopeningAndRetriesTheExactSignedRequest() {
        val peer = ControlledFeedPeer("[]", registryResponse = response@ { criteria ->
            if (criteria.get("request").asString == "registry query") return@response "{}"
            assertEquals("registry namecheck", criteria.get("request").asString)
            assertEquals("NativeCase7@saito", criteria.get("identifier").asString)
            "[]"
        })
        val pending = "Registration requested. Confirmation is still pending."
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                withFailureCapture({ capture("username-pending-failure.png") }) {
                    openUsername()
                    ui.onNode(hasSetTextAction()).performTextReplacement("NativeCase7")
                    ui.onNodeWithText("Check name").performClick()
                    waitForText("This username is currently available. Checking does not reserve it.")
                    assertTrue(peer.transactions.isEmpty())
                    ui.onNodeWithText("Register name").assertIsEnabled().performClick()
                    waitForEnabled("Retry registration")
                    ui.onNodeWithText(pending).assertIsDisplayed()
                    assertEquals(1, peer.transactions.size)
                    val transaction = postingTransaction(peer.transactions.single())
                    val signed = SaitoEnvelope.decode(transaction.envelope())
                    val original = peer.clientKeys.first()
                    assertEquals(original, signed.author)
                    assertEquals(listOf(SaitoRegistry.REGISTRAR, original), signed.recipients)
                    assertEquals(setOf("module", "request", "identifier"), signed.body.keySet())
                    assertEquals("Registry", signed.body.get("module").asString)
                    assertEquals("register", signed.body.get("request").asString)
                    assertEquals("NativeCase7@saito", signed.body.get("identifier").asString)
                    val frozenName = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("NativeCase7"))
                    ui.onNode(frozenName).assertIsNotEnabled()
                    capture("username-pending.png", pending)
                    File(context.cacheDir, "username-evidence/username-native-frame.json").writeText(JsonObject().apply {
                        addProperty("native", true)
                        addProperty("frame", java.util.Base64.getEncoder().encodeToString(peer.transactions.single()))
                        addProperty("envelope", transaction.envelope())
                    }.toString())
                    scenario.recreate()
                    openUsername()
                    waitForEnabled("Retry registration")
                    ui.onNodeWithText(pending).assertIsDisplayed()
                    ui.onNode(frozenName).assertIsNotEnabled()
                    assertEquals("Reopening must only reconcile, never broadcast", 1, peer.transactions.size)
                    capture("username-pending-reopened.png", pending)
                    ui.onNodeWithText("Retry registration").performClick()
                    waitForEnabled("Retry registration")
                    ui.onNodeWithText(pending).assertIsDisplayed()
                    assertEquals(2, peer.transactions.size)
                    assertArrayEquals(peer.transactions.first(), peer.transactions.last())
                    assertTrue(peer.clientKeys.all { it == original })
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun aRegistrarSignedNameIsDisplayedAndRetainedWhenLaterChecksAreUnavailable() = withRegistryWallet { fixture ->
        val reference = instrumentation.context.assets.open("native-registry-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val proof = reference.getAsJsonArray("rows").toString()
        val unavailable = java.util.concurrent.atomic.AtomicBoolean(false)
        val peer = ControlledFeedPeer("[]", registryResponse = response@ { criteria ->
            if (criteria.get("request").asString == "registry query") return@response "{}"
            assertEquals("registry namecheck", criteria.get("request").asString)
            assertEquals("NativeCase7@saito", criteria.get("identifier").asString)
            if (unavailable.get()) "{\"err\":\"no response\"}" else proof
        })
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                // Explicit scalar2 authority applies only to this isolated emulator fixture.
                .putExtra("usernameFixtureRegistrar", true)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                withFailureCapture({ capture("username-confirmation-failure.png") }) {
                    openUsername()
                    waitForEnabled("Check name")
                    ui.onNode(hasSetTextAction()).performTextReplacement("NativeCase7")
                    ui.onNodeWithText("Check name").performClick()
                    waitForText("This username is registered to your wallet.")
                    ui.onNodeWithText("NativeCase7@saito").assertIsDisplayed()
                    ui.onAllNodesWithText("Register name").assertCountEquals(0)
                    ui.onAllNodesWithText("Retry registration").assertCountEquals(0)
                    assertTrue(peer.transactions.isEmpty())
                    capture("username-confirmed.png", "NativeCase7@saito")
                    fun observedChecks() = peer.registryQueries.count {
                        it.get("request").asString == "registry namecheck" &&
                            it.get("identifier")?.asString == "NativeCase7@saito"
                    }
                    val checksBeforeRecovery = observedChecks()
                    assertTrue("Signed ownership must follow an actual namecheck", checksBeforeRecovery > 0)
                    unavailable.set(true)
                    scenario.recreate()
                    openUsername()
                    waitForText("Username could not be checked. Try again.")
                    assertTrue("Recovery must actually observe the unavailable Registry response",
                        observedChecks() > checksBeforeRecovery)
                    ui.onNodeWithText("Saved username: NativeCase7@saito").assertIsDisplayed()
                    ui.onAllNodesWithText("This username is registered to your wallet.").assertCountEquals(0)
                    ui.onAllNodesWithText("Register name").assertCountEquals(0)
                    ui.onAllNodesWithText("Retry registration").assertCountEquals(0)
                    capture("username-confirmed-cached.png", "Saved username: NativeCase7@saito")
                    ui.onNodeWithText("Back").performClick()
                    waitForEnabled("New post")
                    ui.onNodeWithText("Wallet").performClick()
                    ui.onNode(hasText(fixture.publicKey) and hasContentDescription("Wallet address")).assertIsDisplayed()
                    assertTrue(peer.transactions.isEmpty()); peer.assertHealthy()
                }
            }
        }
    }

    @Test fun losingARegistrationRaceNeverNamesTheWrongWalletOrResendsItsRequest() = withRegistryWallet(
        SaitoWallet.fromPrivateKey("0".repeat(63) + "3") // Distinct from both proof owner and synthetic registrar.
    ) { fixture ->
        val reference = instrumentation.context.assets.open("native-registry-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val registered = java.util.concurrent.atomic.AtomicBoolean(false)
        val peer = ControlledFeedPeer("[]", onTransaction = { registered.set(true) }, registryResponse = response@ { criteria ->
            if (criteria.get("request").asString == "registry query") return@response "{}"
            if (criteria.get("identifier").asString == "NativeCase7@saito" && registered.get())
                reference.getAsJsonArray("rows").toString() else "[]"
        }, archiveResponse = { "[]" })
        peer.start().use { server ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("usernameFixtureRegistrar", true)).use { scenario ->
                withFailureCapture({ capture("username-race-failure.png") }) {
                    openUsername()
                    ui.onNode(hasSetTextAction()).performTextReplacement("NativeCase7")
                    ui.onNodeWithText("Check name").performClick()
                    waitForText("This username is currently available. Checking does not reserve it.")
                    assertTrue(peer.transactions.isEmpty())
                    ui.onNodeWithText("Register name").performClick()
                    val taken = "This username belongs to another wallet."
                    fun reopenTaken() {
                        waitForEnabled("Settings")
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("Saito username").performClick()
                        waitForText(taken)
                        ui.onNodeWithText("Retry registration").assertIsNotEnabled()
                    }
                    waitForText(taken)
                    ui.onNodeWithText(taken).performScrollTo().assertIsDisplayed()
                    ui.onNodeWithText("Retry registration").assertIsNotEnabled()
                    ui.onAllNodesWithText("This username is registered to your wallet.").assertCountEquals(0)
                    assertEquals(1, peer.transactions.size)
                    val signed = SaitoEnvelope.decode(postingTransaction(peer.transactions.single()).envelope())
                    assertEquals(fixture.publicKey, signed.author)
                    assertEquals("NativeCase7@saito", signed.body.get("identifier").asString)
                    capture("username-race.png", taken)
                    val checks = peer.registryQueries.size
                    scenario.recreate()
                    // The retained request is now taken, so its Retry control must remain disabled.
                    reopenTaken()
                    assertTrue(peer.registryQueries.size > checks)
                    assertEquals("Read-only recovery must not resend a losing request", 1, peer.transactions.size)
                    ui.onNodeWithText("Back").performClick()
                    waitForEnabled("Your profile")
                    ui.onNodeWithText("Your profile").performClick()
                    waitForText(fixture.publicKey)
                    ui.onNode(hasText("Anon-${fixture.publicKey.take(6)}") and hasAnySibling(hasText(fixture.publicKey)),
                        useUnmergedTree = true).assertIsDisplayed()
                    ui.onAllNodesWithText("NativeCase7@saito").assertCountEquals(0)
                    assertEquals(1, peer.transactions.size)
                    assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == fixture.publicKey })
                    ui.onNodeWithText("Back").performClick()
                    reopenTaken()
                    ui.onNodeWithText("Choose another name").assertIsEnabled().performClick()
                    ui.onNodeWithText("Cancel").performClick()
                    ui.onNodeWithText(taken).assertExists()
                    scenario.recreate()
                    reopenTaken()
                    assertEquals(1, peer.transactions.size)
                    ui.onNodeWithText("Choose another name").performClick()
                    ui.onNodeWithText("Stop retries and choose").performClick()
                    waitForEnabled("Check name")
                    ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))).assertIsEnabled()
                    scenario.recreate()
                    openUsername()
                    ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))).assertIsEnabled()
                    assertEquals("Retirement and reopening must never send", 1, peer.transactions.size)
                    capture("username-retired-reopened.png", "Check name")
                    val requestFile = File(context.noBackupFilesDir, "namerequest.owner.${fixture.publicKey}.v1")
                    val retiredBytes = requestFile.readBytes()
                    check(retiredBytes.first() == 2.toByte())
                    var primary: Throwable? = null
                    try {
                        // Only the external version byte changes. Retirement must stay authenticated.
                        requestFile.writeBytes(retiredBytes.copyOf().apply { this[0] = 1 })
                        scenario.recreate()
                        waitForEnabled("Settings")
                        ui.onNodeWithText("Settings").performClick()
                        ui.onNodeWithText("Saito username").performClick()
                        val unreadable = "Saved username request could not be opened. Try again."
                        waitForText(unreadable)
                        ui.onNodeWithText("Check name").assertIsNotEnabled()
                        ui.onAllNodesWithText("Register name").assertCountEquals(0)
                        ui.onAllNodesWithText("Retry registration").assertCountEquals(0)
                        assertEquals("A damaged retirement marker must not revive its request", 1, peer.transactions.size)
                        capture("username-retired-unreadable.png", unreadable)
                    } catch (failure: Throwable) { primary = failure; throw failure }
                    finally { cleanupAfterTest(primary, { requestFile.writeBytes(retiredBytes) }) }
                    ui.onNodeWithText("Retry", useUnmergedTree = true).performClick()
                    waitForEnabled("Check name")
                    ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))).assertIsEnabled()
                    assertEquals("Repair only reopens the retired form, never sends", 1, peer.transactions.size)
                    ui.onNode(hasSetTextAction()).performTextReplacement("NativeOther9")
                    ui.onNodeWithText("Check name").performClick()
                    waitForText("This username is currently available. Checking does not reserve it.")
                    assertEquals("Choosing and checking a different name must not register it", 1, peer.transactions.size)
                    ui.onNodeWithText("Register name").performClick()
                    waitForEnabled("Retry registration")
                    assertEquals(2, peer.transactions.size)
                    assertFalse(peer.transactions.first().contentEquals(peer.transactions.last()))
                    val replacement = SaitoEnvelope.decode(postingTransaction(peer.transactions.last()).envelope())
                    assertEquals(fixture.publicKey, replacement.author)
                    assertEquals("NativeOther9@saito", replacement.body.get("identifier").asString)
                    capture("username-new-candidate.png", "Registration requested. Confirmation is still pending.")
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun aRequestIsSavedBeforeSendingAndUnreadableRecoveryNeverReplaysIt() = withRegistryWallet { fixture ->
        val peer = ControlledFeedPeer("[]", registryResponse = { criteria ->
            if (criteria.get("request").asString == "registry query") "{}" else "[]"
        })
        val requestFile = File(context.noBackupFilesDir, "namerequest.owner.${fixture.publicKey}.v1")
        var originalBytes: ByteArray? = null
        var primary: Throwable? = null
        fun openSettingsName() {
            waitForEnabled("Settings")
            if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
            ui.onNodeWithText("Settings").performClick()
            ui.onNodeWithText("Saito username").performClick()
        }
        try {
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedDisabled", true)).use { scenario ->
                    withFailureCapture({ capture("username-request-storage-failure.png") }) {
                        openSettingsName()
                        waitForEnabled("Check name")
                        ui.onNode(hasSetTextAction()).performTextReplacement("NativeCase7")
                        ui.onNodeWithText("Check name").performClick()
                        waitForText("This username is currently available. Checking does not reserve it.")
                        check(!requestFile.exists() && requestFile.mkdir())
                        ui.onNodeWithText("Register name").performClick()
                        val error = "Username request could not be saved. Nothing new was sent. Try again."
                        waitForText(error)
                        ui.onNodeWithText(error).performScrollTo().assertIsDisplayed()
                        assertTrue("Unpersisted registration must never reach the peer", peer.transactions.isEmpty())
                        ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("NativeCase7")))
                            .assertIsNotEnabled()
                        capture("username-request-save-error.png", error)
                        check(requestFile.delete())
                        ui.onNodeWithText("Retry registration").performScrollTo().assertIsEnabled().performClick()
                        waitForEnabled("Retry registration")
                        waitForText("Registration requested. Confirmation is still pending.")
                        assertEquals(1, peer.transactions.size)
                        assertEquals(fixture.publicKey, SaitoEnvelope.decode(postingTransaction(peer.transactions.single()).envelope()).author)
                        check(requestFile.isFile)
                        originalBytes = requestFile.readBytes()
                        // The external encrypted-file boundary becomes unreadable between app sessions.
                        requestFile.writeBytes(byteArrayOf(0, 1, 2))
                        val checks = peer.registryQueries.size
                        scenario.recreate()
                        openSettingsName()
                        val unreadable = "Saved username request could not be opened. Try again."
                        waitForText(unreadable)
                        ui.onNodeWithText("Check name").assertIsNotEnabled()
                        ui.onAllNodesWithText("Register name").assertCountEquals(0)
                        ui.onAllNodesWithText("Retry registration").assertCountEquals(0)
                        assertEquals("Unreadable work must fail closed before lookup or signing", checks, peer.registryQueries.size)
                        assertEquals(1, peer.transactions.size)
                        capture("username-request-unreadable.png", unreadable)
                        requestFile.writeBytes(requireNotNull(originalBytes))
                        ui.onNodeWithText("Retry", useUnmergedTree = true).performClick()
                        waitForEnabled("Retry registration")
                        assertTrue(peer.registryQueries.size > checks)
                        assertEquals("Repair only reconciles the original request", 1, peer.transactions.size)
                        ui.onNodeWithText("Retry registration").performClick()
                        waitForEnabled("Retry registration")
                        assertEquals(2, peer.transactions.size)
                        assertArrayEquals(peer.transactions.first(), peer.transactions.last())
                        assertEquals(fixture.publicKey, AndroidWalletStore(context).loadExisting().publicKey)
                        assertTrue(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == fixture.publicKey })
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                if (requestFile.isDirectory) check(requestFile.delete())
                originalBytes?.let { requestFile.writeBytes(it) }
            })
        }
    }

    @Test fun aConfirmedRegistrationIsNotResentAndNamesTheOwnProfile() = withRegistryWallet { fixture ->
        val reference = instrumentation.context.assets.open("native-registry-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val proof = reference.getAsJsonArray("rows").toString()
        val registered = java.util.concurrent.atomic.AtomicBoolean(false)
        val missing = java.util.concurrent.atomic.AtomicBoolean(false)
        val peer = ControlledFeedPeer("[]", onTransaction = { frame ->
            val signed = SaitoEnvelope.decode(postingTransaction(frame).envelope())
            assertEquals(fixture.publicKey, signed.author)
            assertEquals(listOf(reference.get("registrar").asString, fixture.publicKey), signed.recipients)
            assertEquals("Registry", signed.body.get("module").asString)
            assertEquals("register", signed.body.get("request").asString)
            assertEquals("NativeCase7@saito", signed.body.get("identifier").asString)
            registered.set(true)
        }, registryResponse = response@ { criteria ->
            if (criteria.get("request").asString == "registry query") return@response "{}"
            assertEquals("registry namecheck", criteria.get("request").asString)
            assertEquals("NativeCase7@saito", criteria.get("identifier").asString)
            if (registered.get() && !missing.get()) proof else "[]"
        }, archiveResponse = { "[]" })
        peer.start().use { server ->
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("usernameFixtureRegistrar", true)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                withFailureCapture({ capture("username-profile-failure.png") }) {
                    openUsername()
                    waitForEnabled("Check name")
                    ui.onNode(hasSetTextAction()).performTextReplacement("NativeCase7")
                    ui.onNodeWithText("Check name").performClick()
                    waitForText("This username is currently available. Checking does not reserve it.")
                    assertTrue("Availability checking must never publish", peer.transactions.isEmpty())
                    ui.onNodeWithText("Register name").performClick()
                    waitForText("This username is registered to your wallet.")
                    ui.onNodeWithText("NativeCase7@saito").assertIsDisplayed()
                    assertEquals(1, peer.transactions.size)
                    capture("username-registered.png", "NativeCase7@saito")
                    File(context.cacheDir, "username-evidence/username-registration-native-frame.json").writeText(JsonObject().apply {
                        addProperty("native", true)
                        addProperty("frame", java.util.Base64.getEncoder().encodeToString(peer.transactions.single()))
                        addProperty("envelope", postingTransaction(peer.transactions.single()).envelope())
                    }.toString())
                    missing.set(true)
                    val checks = peer.registryQueries.count { it.get("request").asString == "registry namecheck" }
                    scenario.recreate()
                    openUsername()
                    waitForText("Your saved username was not found on this server. It has been kept on this device.")
                    assertTrue(peer.registryQueries.count { it.get("request").asString == "registry namecheck" } > checks)
                    ui.onNodeWithText("Saved username: NativeCase7@saito").assertIsDisplayed()
                    ui.onAllNodesWithText("Retry registration").assertCountEquals(0)
                    ui.onAllNodesWithText("Register name").assertCountEquals(0)
                    assertEquals("Confirmed work must never be resent", 1, peer.transactions.size)
                    ui.onNodeWithText("Back").performClick()
                    waitForEnabled("Your profile")
                    ui.onNodeWithText("Your profile").performClick()
                    waitForText("NativeCase7@saito")
                    ui.onNode(hasText("NativeCase7@saito") and hasAnySibling(hasText(fixture.publicKey)), useUnmergedTree = true).assertIsDisplayed()
                    ui.onNodeWithText(fixture.publicKey).assertIsDisplayed()
                    ui.onAllNodesWithText("Anon-${fixture.publicKey.take(6)}").assertCountEquals(0)
                    capture("username-profile.png", "NativeCase7@saito")
                    assertEquals(1, peer.transactions.size)
                    assertTrue(peer.clientKeys.all { it == fixture.publicKey })
                    peer.assertHealthy()
                }
            }
        }
    }

    @Test fun walletReplacementKeepsTheVerifiedNameWithItsOutgoingIdentity() = withRegistryWallet { fixture ->
        val reference = instrumentation.context.assets.open("native-registry-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val proof = reference.getAsJsonArray("rows").toString()
        val incoming = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Another public unsafe fixture only.
        assertNotEquals(fixture.publicKey, incoming.publicKey)
        val filename = "00-username-import.json"
        val device = UiDevice.getInstance(instrumentation)
        check(device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test stat -c %s files/wallet-test-documents/00-username-import.json")
            .trim().toLongOrNull()?.let { it > 0 } == true) { "Username import fixture was not provisioned" }
        val peer = ControlledFeedPeer("[]", registryResponse = response@ { criteria ->
            if (criteria.get("request").asString == "registry query") return@response "{}"
            assertEquals("registry namecheck", criteria.get("request").asString)
            assertEquals("NativeCase7@saito", criteria.get("identifier").asString)
            proof
        }, archiveResponse = { "[]" })
        val balanceServer = zeroBalanceServer()
        var primary: Throwable? = null
        try {
            peer.start().use { server ->
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("usernameFixtureRegistrar", true)
                    .putExtra("balanceApiUrl", "http://127.0.0.1:${balanceServer.port}/balance/")
                ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                    withFailureCapture({ capture("username-import-failure.png") }) {
                        openUsername()
                        waitForEnabled("Check name")
                        ui.onNode(hasSetTextAction()).performTextReplacement("NativeCase7")
                        ui.onNodeWithText("Check name").performClick()
                        waitForText("This username is registered to your wallet.")
                        ui.onNodeWithText("NativeCase7@saito").assertIsDisplayed()
                        assertTrue(peer.registryQueries.isNotEmpty())
                        assertTrue(peer.transactions.isEmpty())
                        ui.onNodeWithText("Back").performClick()
                        waitForEnabled("New post")
                        ui.onNodeWithText("Wallet").performClick()
                        assertEquals(fixture.publicKey, displayedWalletAddress(ui))
                        ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                        chooseWalletImport(ui, filename, incoming.publicKey)
                        val selected = context.getString(R.string.wallet_import_address)
                        ui.waitUntil(15_000) { ui.onAllNodesWithContentDescription(selected).fetchSemanticsNodes().isNotEmpty() }
                        ui.onNodeWithContentDescription(selected).assertTextEquals(incoming.publicKey)
                        ui.onNodeWithText("Saved name: NativeCase7@saito").performScrollTo().assertIsDisplayed()
                        ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().assertIsOff()
                        ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
                        capture("username-import-preview.png", "Saved name: NativeCase7@saito")
                        assertEquals(fixture.publicKey, displayedWalletAddress(ui))
                        ui.onNodeWithText("Done").performClick()
                        waitForEnabled("New post")
                        ui.onNodeWithText("Your profile").performClick()
                        waitForText("NativeCase7@saito")
                        ui.onNodeWithText(fixture.publicKey).assertIsDisplayed()
                        fun incomingLookups() = peer.registryQueries.count {
                            it.get("request").asString == "registry query" &&
                                it.getAsJsonArray("keys").any { key -> key.asString == incoming.publicKey }
                        }
                        val incomingLookupsBeforeReplacement = incomingLookups()
                        // Cancelled preview kept the outgoing identity. Now perform the real replacement.
                        ui.onNodeWithText("Wallet").performClick()
                        ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                        chooseWalletImport(ui, filename, incoming.publicKey)
                        waitForText("Server reports zero SAITO")
                        ui.onNodeWithText("Skip backup").performScrollTo().performClick()
                        ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
                        ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsEnabled().performClick()
                        waitForText("Wallet imported")
                        assertEquals(incoming.publicKey, displayedWalletAddress(ui))
                        ui.onNodeWithText("Done").performClick()
                        waitForEnabled("Your profile")
                        ui.onNodeWithText("Your profile").performClick()
                        waitForText(incoming.publicKey)
                        ui.onNode(hasText("Anon-${incoming.publicKey.take(6)}") and hasAnySibling(hasText(incoming.publicKey)),
                            useUnmergedTree = true).assertIsDisplayed()
                        ui.onAllNodesWithText("NativeCase7@saito").assertCountEquals(0)
                        capture("username-import-replaced-profile.png", incoming.publicKey)
                        ui.onNodeWithText("Back").performClick()
                        openUsername()
                        ui.onAllNodesWithText("NativeCase7@saito").assertCountEquals(0)
                        ui.onAllNodesWithText("Retry registration").assertCountEquals(0)
                        ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
                            .assertIsEnabled()
                        assertTrue("Imported identity must actually query its own key",
                            incomingLookups() > incomingLookupsBeforeReplacement)
                        assertTrue(peer.clientKeys.contains(incoming.publicKey))
                        scenario.recreate()
                        openUsername()
                        ui.onAllNodesWithText("NativeCase7@saito").assertCountEquals(0)
                        ui.onAllNodesWithText("Retry registration").assertCountEquals(0)
                        ui.onNodeWithText("Back").performClick()
                        ui.onNodeWithText("Wallet").performClick()
                        assertEquals(incoming.publicKey, displayedWalletAddress(ui))
                        capture("username-import-replaced-recreated.png", incoming.publicKey)
                        assertTrue("Replacement must never register or replay outgoing work", peer.transactions.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                val store = AndroidWalletStore(context)
                val active = store.loadExisting()
                check(active.publicKey == fixture.publicKey || active.publicKey == incoming.publicKey)
                if (active.publicKey == incoming.publicKey) store.replace(active, fixture)
            }, { balanceServer.close() }, {
                check(device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test rm files/wallet-test-documents/00-username-import.json").isBlank()) {
                    "Could not clear the public username import fixture"
                }
            })
        }
    }

    @Test fun aPendingNameStaysWithItsWalletAndBackendThroughActualReplacement() = withRegistryWallet { fixture ->
        // Another module's work must not satisfy the Registry-specific warning assertion.
        check(SaitoBackend.entries.all { backend ->
            nl.davidengelen.redsquare.feed.PostDraftStore.authorWork(context, fixture.publicKey, backend).isEmpty() &&
                !nl.davidengelen.redsquare.feed.ProfileDraftStore(context, fixture.publicKey, backend).load().hasWork
        }) { "Unrelated public-fixture work would hide a missing Registry import warning" }
        val device = UiDevice.getInstance(instrumentation)
        val filename = "00-username-pending-import.json"
        check(device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test stat -c %s files/wallet-test-documents/00-username-pending-import.json")
            .trim().toLongOrNull()?.let { it > 0 } == true) { "Pending username import fixture was not provisioned" }
        val incoming = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU" // Independent public scalar2 identity.
        assertNotEquals(fixture.publicKey, incoming)
        val ownerPeer = ControlledFeedPeer("[]", registryResponse = response@ { criteria ->
            if (criteria.get("request").asString == "registry query") return@response "{}"
            assertEquals("registry namecheck", criteria.get("request").asString)
            assertEquals("NativeCase7@saito", criteria.get("identifier").asString)
            "[]"
        })
        val officialPeer = ControlledFeedPeer("[]")
        val pending = "Registration requested. Confirmation is still pending."
        val balanceServer = zeroBalanceServer()
        var primary: Throwable? = null
        try {
            ownerPeer.start().use { ownerServer ->
                officialPeer.start().use { officialServer ->
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedPeerUrl", "ws://127.0.0.1:${ownerServer.port}/wsopen")
                        .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${officialServer.port}/wsopen")
                        .putExtra("balanceApiUrl", "http://127.0.0.1:${balanceServer.port}/balance/")).use { scenario ->
                        withFailureCapture({ capture("username-pending-import-failure.png") }) {
                            openUsername()
                            waitForEnabled("Check name")
                            ui.onNode(hasSetTextAction()).performTextReplacement("NativeCase7")
                            ui.onNodeWithText("Check name").performScrollTo().performClick()
                            waitForText("This username is currently available. Checking does not reserve it.")
                            assertTrue(ownerPeer.transactions.isEmpty())
                            ui.onNodeWithText("Register name").performScrollTo().performClick()
                            waitForEnabled("Retry registration")
                            ui.onNodeWithText(pending).performScrollTo().assertIsDisplayed()
                            assertEquals(1, ownerPeer.transactions.size)
                            val original = ownerPeer.transactions.single().copyOf()
                            val signed = SaitoEnvelope.decode(postingTransaction(original).envelope())
                            assertEquals(fixture.publicKey, signed.author)
                            assertEquals(listOf(SaitoRegistry.REGISTRAR, fixture.publicKey), signed.recipients)
                            assertEquals("NativeCase7@saito", signed.body.get("identifier").asString)
                            ui.onNodeWithText("Settings").performClick()
                            ui.onNodeWithText("saito.io").performClick()
                            waitForText("Server: saito.io")
                            assertEquals(SaitoBackend.OFFICIAL, AndroidBackendStore(context).load())
                            waitForEnabled("Check name")
                            ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
                                .assertIsEnabled()
                            ui.onAllNodesWithText("Retry registration").assertCountEquals(0)
                            assertTrue("Switching must not send the owner's request to the other backend", officialPeer.transactions.isEmpty())
                            assertEquals(1, ownerPeer.transactions.size)
                            ui.onNodeWithText("Back").performClick()
                            waitForEnabled("New post")
                            officialPeer.assertRequestObserved()
                            ui.onNodeWithText("Wallet").performClick()
                            assertEquals(fixture.publicKey, displayedWalletAddress(ui))
                            ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                            chooseWalletImport(ui, filename, incoming)
                            ui.onNodeWithText("Pending work belongs to your current wallet").performScrollTo().assertIsDisplayed()
                            ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().assertIsOff()
                            ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
                            capture("username-pending-import-preview.png", "Pending work belongs to your current wallet")
                            assertEquals(fixture.publicKey, displayedWalletAddress(ui))
                            ui.onNodeWithText("Done").performClick()
                            val ownerChecksBeforeReturning = ownerPeer.registryQueries.count {
                                it.get("request").asString == "registry namecheck" &&
                                    it.get("identifier")?.asString == "NativeCase7@saito"
                            }
                            ui.onNodeWithText("Settings").performClick()
                            ui.onNodeWithText("saito.davidengelen.nl").performClick()
                            waitForText("Server: saito.davidengelen.nl")
                            openUsername()
                            waitForEnabled("Retry registration")
                            assertEquals(SaitoBackend.OWNER, AndroidBackendStore(context).load())
                            assertTrue("Returning must actually recheck the saved request on its original backend",
                                ownerPeer.registryQueries.count {
                                    it.get("request").asString == "registry namecheck" &&
                                        it.get("identifier")?.asString == "NativeCase7@saito"
                                } > ownerChecksBeforeReturning)
                            ui.onNodeWithText(pending).performScrollTo().assertIsDisplayed()
                            assertEquals("Returning must reconcile without automatic publication", 1, ownerPeer.transactions.size)
                            assertTrue(officialPeer.transactions.isEmpty())
                            ui.onNodeWithText("Retry registration").performScrollTo().performClick()
                            waitForEnabled("Retry registration")
                            assertEquals(2, ownerPeer.transactions.size)
                            assertArrayEquals(original, ownerPeer.transactions.last())
                            assertTrue(ownerPeer.clientKeys.all { it == fixture.publicKey })
                            assertTrue(officialPeer.clientKeys.all { it == fixture.publicKey })
                            // Replace only after the original-backend exact-retry proof above.
                            ui.onNodeWithText("Wallet").performClick()
                            ui.onNodeWithText("Import wallet").performScrollTo().performClick()
                            chooseWalletImport(ui, filename, incoming)
                            ui.onNodeWithText("Pending work belongs to your current wallet").performScrollTo().assertIsDisplayed()
                            waitForText("Server reports zero SAITO")
                            ui.onNodeWithText("Skip backup").performScrollTo().performClick()
                            ui.onNodeWithContentDescription("Replacement risks acknowledged").performScrollTo().performClick()
                            ui.onNodeWithText("Confirm replacement").performScrollTo().assertIsEnabled().performClick()
                            waitForText("Wallet imported")
                            assertEquals(incoming, displayedWalletAddress(ui))
                            ui.onNodeWithText("Done").performClick()
                            // Username navigation remains open across wallet replacement.
                            waitForEnabled("Check name")
                            ui.onAllNodesWithText("Retry registration").assertCountEquals(0)
                            ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
                                .assertIsEnabled()
                            ui.onNodeWithText("Settings").performClick()
                            ui.onNodeWithText("saito.io").performClick()
                            waitForText("Server: saito.io")
                            waitForEnabled("Check name")
                            ui.onAllNodesWithText("Retry registration").assertCountEquals(0)
                            scenario.recreate()
                            openUsername()
                            ui.onAllNodesWithText("Retry registration").assertCountEquals(0)
                            ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
                                .assertIsEnabled()
                            capture("username-pending-replaced.png", "Choose a Saito username")
                            assertEquals(SaitoBackend.OFFICIAL, AndroidBackendStore(context).load())
                            ui.onNodeWithText("Back").performClick()
                            ui.onNodeWithText("Wallet").performClick()
                            assertEquals(incoming, displayedWalletAddress(ui))
                            assertTrue(ownerPeer.clientKeys.contains(incoming))
                            assertTrue(officialPeer.clientKeys.contains(incoming))
                            assertEquals("Replacement must never replay the outgoing candidate", 2, ownerPeer.transactions.size)
                            assertArrayEquals(original, ownerPeer.transactions.last())
                            assertTrue(officialPeer.transactions.isEmpty())
                            ownerPeer.assertHealthy(); officialPeer.assertHealthy()
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                val store = AndroidWalletStore(context)
                val active = store.loadExisting()
                check(active.publicKey == fixture.publicKey || active.publicKey == incoming)
                if (active.publicKey == incoming) store.replace(active, fixture)
            }, { balanceServer.close() }, {
                check(device.executeShellCommand("run-as nl.davidengelen.redsquare.dev.test rm files/wallet-test-documents/00-username-pending-import.json").isBlank()) {
                    "Could not clear the public pending-name import fixture"
                }
            })
        }
    }

    private fun zeroBalanceServer() = MockWebServer().apply {
        dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest) = MockResponse.Builder()
                .body("${System.currentTimeMillis()}-1234-${"a".repeat(64)}.snap\n").build()
        }
        start(InetAddress.getByName("127.0.0.1"), 0)
    }

    private fun nameDiscoveryResponse(publicKey: String, proof: () -> String): (JsonObject) -> String = { criteria ->
        when (criteria.get("request").asString) {
            "registry query" -> {
                val requested = criteria.getAsJsonArray("keys").map { it.asString }
                JsonObject().apply {
                    if (publicKey in requested) addProperty(publicKey, "NativeCase7@saito")
                }.toString()
            }
            "registry namecheck" -> {
                assertEquals("NativeCase7@saito", criteria.get("identifier").asString)
                proof()
            }
            else -> error("Unexpected discovery request")
        }
    }

    private fun discoveryPeer(publicKey: String, rows: String = "[]", proof: () -> String) = ControlledFeedPeer(rows,
        registryResponse = nameDiscoveryResponse(publicKey, proof), archiveResponse = { criteria ->
            if (criteria.get("field1").asString == "RedSquare" && !criteria.has("field2")) rows else "[]"
        })

    private fun withRegistryWallet(fixture: SaitoWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1"), block: (SaitoWallet) -> Unit) {
        val walletStore = AndroidWalletStore(context)
        val original = walletStore.loadOrCreate()
        if (original.publicKey != fixture.publicKey) walletStore.replace(original, fixture)
        var primary: Throwable? = null
        try { block(fixture) }
        catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            try {
                val active = walletStore.loadExisting()
                check(active.publicKey == fixture.publicKey)
                if (original.publicKey != fixture.publicKey) walletStore.replace(active, original)
            } catch (cleanup: Throwable) { if (primary != null) primary.addSuppressed(cleanup) else throw cleanup }
        }
    }

    private fun openUsername() {
        waitForEnabled("New post")
        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("Later").performClick()
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Saito username").assertIsEnabled().performClick()
        waitForEnabled("Back")
        ui.waitUntil(20_000) {
            ui.onAllNodes((hasText("Check name") or hasText("Retry registration")) and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForEnabled(text: String) {
        ui.waitUntil(20_000) { ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String, expectedText: String? = null) =
        captureNativeScreen(File(context.cacheDir, "username-evidence/$name"), expectedText)
}
