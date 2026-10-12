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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class NotificationsPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private enum class Mode { LIKE, REPLY, MENTION, USERNAME, REPOST }

    @Test fun anIncomingSignedLikeOpensItsOriginalPostAndVerifiedActorProfile() {
        withNotifications("incoming-like", mode = Mode.LIKE) { actor ->
            waitFor("Liked your post")
            ui.onNodeWithText(actor).assertIsDisplayed()
            ui.onNodeWithText("Liked your post").performClick()
            waitFor("Conversation")
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Liked your post")
            ui.onNodeWithText(actor).performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun anIncomingSignedReplyOpensItsConversationAndVerifiedActorProfile() {
        withNotifications("incoming-reply", mode = Mode.REPLY) { actor ->
            waitFor("Replied to your post")
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/incoming-reply.png"), "Replied to your post")
            ui.onNodeWithText("Replied to your post").assertIsDisplayed().performClick()
            waitFor("Conversation")
            waitFor("Reply notification reference")
            ui.onNodeWithText("Reply notification reference").assertIsDisplayed()
            waitFor("Native Red Square reference post")
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Replied to your post")
            // Both rows have this signer. The newer reply is the first displayed row.
            ui.onAllNodesWithText(actor).assertCountEquals(2)[0].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun anIncomingSignedMentionOpensItsPostAndVerifiedActorProfile() {
        withNotifications("incoming-mention", mode = Mode.MENTION) { actor ->
            waitFor("Mentioned you")
            ui.onAllNodesWithText("Mentioned you").assertCountEquals(1)
            ui.onAllNodesWithText("Replied to your post").assertCountEquals(0)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/incoming-mention.png"), "Mentioned you")
            ui.onNodeWithText("Mentioned you").assertIsDisplayed().performClick()
            waitFor("Conversation")
            waitFor("Hello @jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR, mention reference")
            ui.onNodeWithText("Hello @jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR, mention reference").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Mentioned you")
            ui.onAllNodesWithText(actor).assertCountEquals(2)[0].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun anIncomingVerifiedUsernameMentionOpensItsPostAndActorProfile() {
        withNotifications("incoming-username-mention", mode = Mode.USERNAME) { actor ->
            waitFor("Mentioned you")
            ui.onAllNodesWithText("Mentioned you").assertCountEquals(1)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/incoming-username-mention.png"), "Mentioned you")
            ui.onNodeWithText("Mentioned you").assertIsDisplayed().performClick()
            waitFor("Conversation")
            waitFor("Hello @NativeCase7@saito, username mention reference")
            ui.onNodeWithText("Hello @NativeCase7@saito, username mention reference").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Mentioned you")
            ui.onAllNodesWithText(actor).assertCountEquals(2)[0].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    @Test fun anIncomingSignedRepostOpensItsOriginalAndVerifiedActorProfile() {
        withNotifications("incoming-repost", mode = Mode.REPOST) { actor ->
            waitFor("Reposted your post")
            ui.onAllNodesWithText("Reposted your post").assertCountEquals(1)
            captureNativeScreen(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "notification-evidence/incoming-repost.png"), "Reposted your post")
            ui.onNodeWithText("Reposted your post").assertIsDisplayed().performClick()
            waitFor("Conversation")
            ui.onNodeWithText("Native Red Square reference post").assertIsDisplayed()
            ui.onNodeWithText("Back").performClick()
            waitFor("Reposted your post")
            ui.onAllNodesWithText(actor).assertCountEquals(2)[0].assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    private fun withNotifications(evidenceName: String, mode: Mode, journey: (String) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val fixture = instrumentation.context.assets.open("native-notification-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val root = fixture.getAsJsonObject("root")
        val owner = "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR"
        val actor = "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU"
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only.
        val store = AndroidWalletStore(context)
        val prior = store.loadOrCreate()
        val priorJson = prior.exportJson()
        val priorBackend = AndroidBackendStore(context).load()
        val permission = context.getSharedPreferences("notification-permission", 0)
        val priorOffer: Boolean? = if (permission.contains("offered")) permission.getBoolean("offered", false) else null
        val registry = instrumentation.context.assets.open("native-registry-reference.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val nameBase = "namemap.$owner.v1"
        fun nameFiles() = context.noBackupFilesDir.listFiles().orEmpty()
            .filter { it.name == nameBase || it.name.startsWith("$nameBase.") }
        var priorNames: Map<String, ByteArray>? = null
        val likeReads = AtomicInteger()
        val replyReads = AtomicInteger()
        val repostReads = AtomicInteger()
        val peer = ControlledFeedPeer("[$root]", quoteArchiveResponse = { criteria ->
            require(criteria.get("field3").asString == owner)
            require(criteria.get("flagged_ne").asInt == 1)
            require(criteria.get("limit").asInt in 1..100)
            repostReads.incrementAndGet()
            if (mode == Mode.REPOST) "[${fixture.get("incomingRepost")},${fixture.get("parentedQuote")}]" else "[]"
        }, registryResponse = { criteria ->
            if (mode != Mode.USERNAME) {
                if (criteria.get("request").asString == "registry namecheck") "{\"err\":\"no response\"}" else "{}"
            } else when (criteria.get("request").asString) {
                "registry query" -> if (criteria.getAsJsonArray("keys").any { it.asString == owner })
                    "{\"$owner\":\"NativeCase7@saito\"}" else "{}"
                "registry namecheck" -> if (criteria.get("identifier").asString == "NativeCase7@saito") registry.get("rows").toString() else "[]"
                else -> error("Unexpected Registry fixture request")
            }
        }, archiveResponse = { criteria ->
            when (criteria.get("field1")?.asString) {
                "RedSquareLike" -> if (criteria.get("field3")?.asString == owner) {
                    require(criteria.get("flagged_ne").asInt == 1)
                    require(criteria.get("limit").asInt in 1..100)
                    likeReads.incrementAndGet()
                    "[${fixture.get("incomingLike")}]"
                } else "[]"
                "RedSquare" -> when {
                    criteria.get("field3")?.asString == owner -> {
                        require(criteria.get("flagged_ne").asInt == 1)
                        require(criteria.get("limit").asInt in 1..100)
                        replyReads.incrementAndGet()
                        when (mode) {
                            Mode.REPLY -> "[${fixture.get("incomingReply")},${fixture.get("parentedQuote")}]"
                            Mode.MENTION -> "[${fixture.get("incomingMention")},${fixture.get("addressedPost")}]"
                            Mode.USERNAME -> "[${fixture.get("usernameMention")}]"
                            Mode.LIKE, Mode.REPOST -> "[]"
                        }
                    }
                    criteria.get("field5")?.asString == fixture.getAsJsonObject("usernameMention").get("sig").asString ->
                        "[${fixture.get("usernameMention")}]"
                    criteria.get("field5")?.asString == fixture.getAsJsonObject("incomingMention").get("sig").asString ->
                        "[${fixture.get("incomingMention")}]"
                    criteria.get("field5")?.asString == root.get("sig").asString ->
                        if (mode == Mode.REPLY) "[$root,${fixture.get("incomingReply")}]" else "[$root]"
                    criteria.get("field2")?.asString in listOf(null, owner) -> "[$root]"
                    else -> "[]"
                }
                else -> "[]"
            }
        })
        var primary: Throwable? = null
        try {
            if (mode == Mode.USERNAME) synchronized(nl.davidengelen.redsquare.saito.SaitoLocalState.lock) {
                val captured = nameFiles().associate { file ->
                    check(file.isFile)
                    file.name to file.readBytes()
                }
                // Record ownership before the first mutation so a partial failure restores the snapshot.
                priorNames = captured
                captured.keys.forEach { check(File(context.noBackupFilesDir, it).delete()) }
            }
            if (prior.publicKey != owner) store.replace(prior, wallet)
            AndroidBackendStore(context).save(SaitoBackend.OWNER)
            check(permission.edit().remove("offered").commit())
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("usernameFixtureRegistrar", mode == Mode.USERNAME)).use {
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                        "notification-evidence/$evidenceName-first-failure.png")) }) {
                        waitFor("Native Red Square reference post")
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                        peer.assertRequestObserved()
                        if (mode == Mode.USERNAME) waitFor("NativeCase7@saito")
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty())
                            ui.onNodeWithText("Later").performClick()
                        ui.onAllNodesWithText("Not now").assertCountEquals(0)
                        waitFor("Notifications")
                        ui.onNodeWithText("Notifications").assertIsDisplayed().performClick()
                        waitFor("Not now")
                        ui.onNodeWithText("Not now").assertIsDisplayed().performClick()
                        if (mode == Mode.LIKE) {
                            waitFor("Liked your post")
                            captureNativeScreen(File(context.cacheDir, "notification-evidence/incoming-like.png"), "Liked your post")
                        }
                        journey(actor)
                        peer.assertHealthy()
                        assertTrue("Incoming likes must be read for this wallet", likeReads.get() > 0)
                        if (mode in listOf(Mode.REPLY, Mode.MENTION, Mode.USERNAME)) assertTrue("Incoming posts must be read for this wallet", replyReads.get() > 0)
                        if (mode == Mode.REPOST) assertTrue("Incoming reposts must be read for this wallet", repostReads.get() > 0)
                        if (mode == Mode.USERNAME) assertTrue("A label hint alone must not be ownership evidence",
                            peer.registryQueries.any { it.get("request").asString == "registry namecheck" &&
                                it.get("identifier").asString == "NativeCase7@saito" })
                        assertTrue(peer.clientKeys.isNotEmpty())
                        assertTrue(peer.clientKeys.all { it == owner })
                        assertTrue("Reading notifications must not publish a transaction", peer.transactions.isEmpty())
                        assertTrue(peer.reports.isEmpty())
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                val active = store.loadExisting()
                check(active.exportJson() == priorJson || active.exportJson() == wallet.exportJson())
                if (active.exportJson() != priorJson) store.replace(active, prior)
                check(store.loadExisting().exportJson() == priorJson)
            }, { AndroidBackendStore(context).save(priorBackend) }, {
                val edit = permission.edit()
                if (priorOffer == null) edit.remove("offered") else edit.putBoolean("offered", priorOffer)
                check(edit.commit())
                check(if (priorOffer == null) !permission.contains("offered") else permission.getBoolean("offered", !priorOffer) == priorOffer)
            }, {
                priorNames?.let { saved ->
                    synchronized(nl.davidengelen.redsquare.saito.SaitoLocalState.lock) {
                        nameFiles().forEach { check(it.isFile && it.delete()) }
                        saved.forEach { (name, bytes) -> File(context.noBackupFilesDir, name).writeBytes(bytes) }
                        check(nameFiles().map { it.name }.toSet() == saved.keys)
                        nameFiles().forEach { check(it.readBytes().contentEquals(saved.getValue(it.name))) }
                    }
                }
            })
        }
    }

    private fun waitFor(text: String) {
        ui.waitUntil(15_000) { ui.onAllNodesWithText(text)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
}
