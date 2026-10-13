package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import nl.davidengelen.redsquare.PrivateChatHistoryFixture.HistoryCase
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class PrivateChatPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val history = PrivateChatHistoryFixture(ui)

    @Test fun firstChatOffersOptionalAlertsAndSkippingKeepsChatAvailable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        if (android.os.Build.VERSION.SDK_INT >= 33) check(context.checkSelfPermission(
            android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_DENIED)
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val original = wallet.exportJson()
        val preferences = listOf("wallet-backup-reminders" to "hidden:${wallet.publicKey}",
            "notification-permission" to "offered").map { (name, key) ->
            val prefs = context.getSharedPreferences(name, 0)
            Triple(prefs, key, if (prefs.contains(key)) prefs.getBoolean(key, false) else null)
        }
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" })
        var primary: Throwable? = null
        try {
            preferences.forEach { (prefs, key, _) -> check(prefs.edit().putBoolean(key, key != "offered").commit()) }
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir,
                        "private-chat-evidence/first-chat-permission-failure.png")) }) {
                        history.waitFor("Chat")
                        ui.onAllNodesWithText("Not now").assertCountEquals(0)
                        ui.onNodeWithText("Chat").assertIsDisplayed().performClick()
                        history.waitFor("Android notification alerts")
                        captureNativeScreen(File(context.cacheDir, "private-chat-evidence/first-chat-permission-offer.png"),
                            "Android notification alerts")
                        ui.onNodeWithText("Not now").assertIsDisplayed().performClick()
                        ui.waitUntil(15_000) { context.getSharedPreferences("notification-permission", 0).getBoolean("offered", false) }
                        history.waitFor("Private messages are encrypted.")
                        ui.onAllNodesWithText("Not now").assertCountEquals(0)
                        captureNativeScreen(File(context.cacheDir, "private-chat-evidence/first-chat-permission-skipped.png"),
                            "Private messages are encrypted.")
                        scenario.recreate()
                        history.waitFor("Private messages are encrypted.")
                        ui.onAllNodesWithText("Not now").assertCountEquals(0)
                        captureNativeScreen(File(context.cacheDir, "private-chat-evidence/first-chat-permission-recreated.png"),
                            "Private messages are encrypted.")
                        check(AndroidWalletStore(context).loadOrCreate().exportJson() == original)
                        check(peer.transactions.isEmpty() && peer.reports.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            val restoration = preferences.map { (prefs, key, previous) -> {
                check(prefs.edit().apply {
                    if (previous == null) remove(key) else putBoolean(key, previous)
                }.commit())
            } } + listOf<() -> Unit>({ check(AndroidWalletStore(context).loadOrCreate().exportJson() == original) })
            cleanupAfterTest(primary, *restoration.toTypedArray())
        }
    }


    @Test fun anImportedContactOpensItsAvailableAuthenticatedPrivateHistory() = history.exerciseHistory(HistoryCase.AVAILABLE)

    @Test fun failedLocalSavingDoesNotLoseReadableHistoryOnRefresh() = history.exerciseHistory(HistoryCase.STORAGE_FAILURE)

    @Test fun aLivePrivateRelayMessageRemainsReadableAfterReopening() = history.exerciseHistory(HistoryCase.LIVE)

    @Test fun aNativePrivateSendPropagatesAndRelaysTheSameEncryptedOriginal() = history.exerciseHistory(HistoryCase.SEND)

    @Test fun anInterruptedPrivateSendRemainsSavedWithoutAutomaticRebroadcast() = history.exerciseHistory(HistoryCase.SEND_INTERRUPTED)

    @Test fun aFreshWalletCanOpenAndSendToANewPrivateContact() = history.exerciseHistory(HistoryCase.NEW_SEND)

    @Test fun anImportedConversationIdRecoversAvailablePrivateHistoryWithoutAContactKeyEntry() = history.exerciseHistory(HistoryCase.IMPORTED_ID)

    @Test fun oneUnavailableImportedConversationDoesNotHideAnotherRecoveredPrivateConversation() = history.exerciseHistory(HistoryCase.IMPORTED_PARTIAL)

    @Test fun aNewIncomingPrivateConversationAppearsWhileTheChatListIsOpen() = history.exerciseHistory(HistoryCase.LIST_LIVE)

    @Test fun aKeyOnlyImportKeepsReadableCachedMessagesAlongsideMissingExchangedKeys() = history.exerciseHistory(HistoryCase.PARTIAL_KEYS)

    @Test fun unreadableAvailableHistoryIsExplainedEvenWhenLocalSavingFails() = history.exerciseHistory(HistoryCase.STORAGE_PARTIAL)

    @Test fun anotherPrivateContactIsRetainedWhileADifferentConversationIsOpen() = history.exerciseHistory(HistoryCase.OTHER_LIVE)

    @Test fun anExistingClientMentionedPrivateMessageRemainsReadableAfterReopening() = history.exerciseHistory(HistoryCase.MENTIONED)

    @Test fun importedOwnMentionedMessagesRecoverTheOriginalPrivateCounterparty() = history.exerciseHistory(HistoryCase.MENTIONED_OUTGOING)

    @Test fun accountMutingKeepsAvailablePrivateMessagesReadable() = history.exerciseHistory(HistoryCase.MUTED)

    @Test fun accountBlockingHidesIncomingMessagesWithoutDeletingRecoverableHistory() = history.exerciseHistory(HistoryCase.BLOCKED)

    @Test fun anUncertainPrivateMessageRetriesTheSavedOriginalOnlyAfterAnExplicitTap() = history.exerciseHistory(HistoryCase.UNKNOWN_RETRY)

    @Test fun walletReplacementExplainsUncertainChatWorkBeforeConfirmation() = history.exerciseHistory(HistoryCase.PENDING_IMPORT)

    @Test fun leavingAConversationPreservesItsUnsentDraftAndDoesNotRestoreErasedText() = history.exerciseHistory(HistoryCase.DRAFT_NAVIGATION)

    @Test fun olderAvailablePrivateMessagesCanBeLoadedAndReadAfterServerHistoryDisappears() = history.exerciseHistory(HistoryCase.OLDER_HISTORY)

    @Test fun anExistingWebPrivateReplyCanBeReadAndCopiedWithoutChangingItsTypedBody() = history.exerciseHistory(HistoryCase.QUOTED_REPLY)

    @Test fun ordinaryTypedPrivateMarkupRemainsLiteralAfterReopening() = history.exerciseHistory(HistoryCase.LITERAL_MARKUP)

    @Test fun unreadableRecentPrivateMessagesDoNotPreventLoadingReadableOlderHistory() = history.exerciseHistory(HistoryCase.UNREADABLE_RECENT_HISTORY)

}
