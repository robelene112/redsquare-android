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

    @Test fun anIncomingSignedLikeOpensItsOriginalPostAndVerifiedActorProfile() {
        withNotifications("incoming-like", includeReply = false) { actor ->
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
        withNotifications("incoming-reply", includeReply = true) { actor ->
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
            ui.onNode(hasText(actor) and hasAnySibling(hasText("Replied to your post")),
                useUnmergedTree = true).assertIsDisplayed().performClick()
            waitFor("Profile")
            ui.onNodeWithText(actor).assertIsDisplayed()
        }
    }

    private fun withNotifications(evidenceName: String, includeReply: Boolean, journey: (String) -> Unit) {
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
        val likeReads = AtomicInteger()
        val replyReads = AtomicInteger()
        val peer = ControlledFeedPeer("[$root]", archiveResponse = { criteria ->
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
                        if (includeReply) "[${fixture.get("incomingReply")}]" else "[]"
                    }
                    criteria.get("field5")?.asString == root.get("sig").asString ->
                        if (includeReply) "[$root,${fixture.get("incomingReply")}]" else "[$root]"
                    criteria.get("field2")?.asString in listOf(null, owner) -> "[$root]"
                    else -> "[]"
                }
                else -> "[]"
            }
        })
        var primary: Throwable? = null
        try {
            if (prior.publicKey != owner) store.replace(prior, wallet)
            AndroidBackendStore(context).save(SaitoBackend.OWNER)
            check(permission.edit().remove("offered").commit())
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use {
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                        "notification-evidence/$evidenceName-first-failure.png")) }) {
                        waitFor("Native Red Square reference post")
                        ui.waitUntil(20_000) { ui.onAllNodes(hasText("Refresh") and isEnabled())
                            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                        peer.assertRequestObserved()
                        if (ui.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty())
                            ui.onNodeWithText("Later").performClick()
                        ui.onAllNodesWithText("Not now").assertCountEquals(0)
                        waitFor("Notifications")
                        ui.onNodeWithText("Notifications").assertIsDisplayed().performClick()
                        waitFor("Not now")
                        ui.onNodeWithText("Not now").assertIsDisplayed().performClick()
                        if (!includeReply) {
                            waitFor("Liked your post")
                            captureNativeScreen(File(context.cacheDir, "notification-evidence/incoming-like.png"), "Liked your post")
                        }
                        journey(actor)
                        peer.assertHealthy()
                        assertTrue("Incoming likes must be read for this wallet", likeReads.get() > 0)
                        if (includeReply) assertTrue("Incoming replies must be read for this wallet", replyReads.get() > 0)
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
            })
        }
    }

    private fun waitFor(text: String) {
        ui.waitUntil(15_000) { ui.onAllNodesWithText(text)
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
}
