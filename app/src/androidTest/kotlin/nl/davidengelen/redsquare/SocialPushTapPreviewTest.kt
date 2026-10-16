package nl.davidengelen.redsquare

import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.chat.ChatWorkStore
import nl.davidengelen.redsquare.chat.composeChatPhoto
import nl.davidengelen.redsquare.feed.PostDraftStore
import nl.davidengelen.redsquare.registry.RegisteredNameStore
import nl.davidengelen.redsquare.registry.RegistryAuthority
import nl.davidengelen.redsquare.notifications.SocialPush
import nl.davidengelen.redsquare.notifications.SocialPushResult
import nl.davidengelen.redsquare.notifications.SocialPushSettings
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual system tap, mounted composer and independent signed Archive, without outgoing writes. */
class SocialPushTapPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun tappingAReplyPreservesTheLatestUnsentPostAndPhotoAndOpensTheExactConversation() = tapJourney()

    @Test fun aReplyTapOpensWithoutAnActivityAndIsConsumedAcrossScreenRecreation() = tapJourney(Journey.COLD_REPLY)

    @Test fun aPublicKeyMentionShowsAMentionAlertAndOpensItsPost() = tapJourney(Journey.COLD_MENTION)

    @Test fun theReplyAndMentionAlertChoiceCanBeEnabledAndDisabledInSettings() = tapJourney(Journey.SETTINGS_REPLY)

    @Test fun androidNotificationCategoriesCanBeOpenedWithoutLosingTheLatestPost() = tapJourney(Journey.ANDROID_SETTINGS_REPLY)

    @Test fun enablingSocialAlertsWorksEvenAfterTheGeneralAndroidPromptWasOffered() = tapJourney(Journey.ONBOARDING_REPLY)

    @Test fun aReplyTapPreservesTheLatestUnsentPrivateChatAndPhoto() = tapJourney(Journey.PRIVATE_CHAT_REPLY)

    @Test fun aReplyTapPreservesTheLatestUnsentGroupChatAndPhoto() = tapJourney(Journey.GROUP_CHAT_REPLY)

    @Test fun aVerifiedUsernameMentionShowsAMentionAlertAndOpensItsPost() = tapJourney(Journey.COLD_USERNAME_MENTION)

    private enum class Journey { WARM_REPLY, COLD_REPLY, COLD_MENTION, SETTINGS_REPLY, ANDROID_SETTINGS_REPLY, ONBOARDING_REPLY, PRIVATE_CHAT_REPLY, GROUP_CHAT_REPLY, COLD_USERNAME_MENTION }

    private data class TapTarget(val fixtureKey: String, val signature: String, val text: String)

    private fun tapJourney(journey: Journey = Journey.WARM_REPLY) {
        val username = journey == Journey.COLD_USERNAME_MENTION
        val mention = journey == Journey.COLD_MENTION || username
        val cold = journey == Journey.COLD_REPLY || mention
        val settings = journey == Journey.SETTINGS_REPLY
        val onboarding = journey == Journey.ONBOARDING_REPLY
        val privateChat = journey == Journey.PRIVATE_CHAT_REPLY
        val groupChat = journey == Journey.GROUP_CHAT_REPLY
        val chatDraft = privateChat || groupChat
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(activeMainActivities().isEmpty()) { "The tap fixture requires no pre-existing MainActivity" }
        val fixture = instrumentation.context.assets.open("native-notification-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val images = instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { JsonParser.parseReader(it).asJsonObject }
        val photo = images.getAsJsonObject("images").get("red").asString
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey // Published unsafe fixture only.
        val identity = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only.
        val groupId = SaitoWallet.fromPrivateKey("0".repeat(63) + "3").publicKey // Published unsafe fixture only.
        val groupName = "Notification draft group"
        val wallet = when {
            privateChat -> identity.withChatContact(contact)
            groupChat -> identity.withNamedChatGroup(groupId, groupName)
            else -> identity
        }
        val owner = wallet.publicKey
        val root = fixture.getAsJsonObject("root")
        val expected = when {
            username -> TapTarget("usernameMention",
                "d632b334c6e66178f524e621c4b1740f93945e319725e3570d338ca12c7c759053600197a800b9d52ad37f00e3c43f4c487d9e051b587b4cf1f7dca28ffdebac",
                "Hello @NativeCase7@saito, username mention reference")
            mention -> TapTarget("incomingMention",
                "58d6074e3fabb5391b8523626775eb94c475a4c8d4a3d06fd91e28b1afc8058e68621d17676e499258742523ff2f9170829d0e93699cf8c51546601dfb9fb7b1",
                "Hello @jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR, mention reference")
            else -> TapTarget("incomingReply",
                "4626eafbb90d66fd650de2a943864198e3daeae0b8876b4c224625b4a438113508e44287fee31d34d117356565d67036537c65dbcc66d8589ea300e2fb414526",
                "Reply notification reference")
        }
        val target = fixture.getAsJsonObject(expected.fixtureKey)
        val signature = expected.signature
        val targetText = expected.text
        val alertText = if (mention) "Mentioned you" else "Replied to your post"
        val freshReply = fixture.getAsJsonObject("parentIndexedReply")
        val freshSignature = "1991d496005db8cb234ddd8482f1b8cc01cb75a9d38fd3ff904f000144e7a6bd22da22068460a5b0f7d5f9d524ae3ac8e2a4a531bad7a2ce46f10149d2abc64b"
        assertEquals(signature, target.get("sig").asString)
        assertEquals(freshSignature, freshReply.get("sig").asString)
        val peer = ControlledFeedPeer("[$root]", advertisedServices = if (chatDraft) "archive||;relay||" else null, archiveResponse = { criteria ->
            when {
                criteria.has("sig") -> when (criteria.get("sig").asString) {
                    signature -> "[$target]"
                    freshSignature -> "[$freshReply]"
                    root.get("sig").asString -> "[$root]"
                    else -> "[]"
                }
                criteria.get("field1")?.asString == "RedSquare" -> "[$root]"
                else -> "[]"
            }
        })
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val previousJson = previous.exportJson()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("socialpush.$owner.", "personal.$owner.",
            "postdraft.owner.$owner") + (if (username) listOf("namemap.$owner.") else emptyList()) +
            (if (chatDraft) SaitoBackend.entries.flatMap { backend ->
                listOf("chat.${backend.id}.$owner.", "chatwork.${backend.id}.$owner.",
                    "chatreactions.${backend.id}.$owner.", "groupchat.${backend.id}.$owner.")
            } else emptyList()), owner)
        val manager = context.getSystemService(NotificationManager::class.java)
        val priorAlerts = manager.activeNotifications.map { it.tag to it.id }.toSet()
        val device = UiDevice.getInstance(instrumentation)
        val latest = if (cold) "Earlier saved text" else "Latest unsent post 🌱\nKeep the photo and this last edit"
        val capturePrefix = when (journey) {
            Journey.COLD_MENTION -> "tap-mention"
            Journey.COLD_USERNAME_MENTION -> "tap-username"
            Journey.COLD_REPLY -> "tap-cold"
            Journey.WARM_REPLY -> "tap-post"
            Journey.SETTINGS_REPLY -> "tap-settings"
            Journey.ANDROID_SETTINGS_REPLY -> "tap-android"
            Journey.ONBOARDING_REPLY -> "tap-onboarding"
            Journey.PRIVATE_CHAT_REPLY -> "tap-private"
            Journey.GROUP_CHAT_REPLY -> "tap-group"
        }
        var primary: Throwable? = null
        try {
            retained.prepare()
            wallets.replace(previous, wallet)
            backends.save(SaitoBackend.OWNER)
            if (!settings && !onboarding) SocialPushSettings(context, owner).setEnabled(true)
            // The encrypted draft supplies the photo; the warm journey edits its latest text in the UI.
            PostDraftStore(context, owner, SaitoBackend.OWNER).save(
                PostDraftStore.Saved("Earlier saved text", images = listOf(photo)))
            if (privateChat) ChatWorkStore(context, wallet, SaitoBackend.OWNER, contact)
                .saveDraft(composeChatPhoto(photo, "Earlier saved text"))
            if (groupChat) {
                val admin = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Published unsafe fixture only.
                val creation = GroupChat.create(admin, groupId, groupName, listOf(owner), 1700000123400)
                GroupChatStore(context, wallet, SaitoBackend.OWNER).apply {
                    receive(creation.envelope())
                    saveDraft(groupId, composeChatPhoto(photo, "Earlier saved text"))
                }
            }
            fun assertSavedChat() {
                if (privateChat) {
                    val saved = ChatWorkStore(context, wallet, SaitoBackend.OWNER, contact).load()
                    assertEquals(composeChatPhoto(photo, latest), saved.draft)
                    assertNull(saved.transaction)
                    assertEquals(ChatWorkStore.Status.NONE, saved.status)
                } else {
                    val saved = requireNotNull(GroupChatStore(context, wallet, SaitoBackend.OWNER).load(groupId))
                    assertEquals(composeChatPhoto(photo, latest), saved.draft)
                    assertNull(saved.candidate)
                    assertNull(saved.queuedMessage)
                    assertFalse(saved.hasPendingAction)
                    assertEquals(GroupChatStore.Status.RECEIVED, saved.status)
                    assertTrue(saved.messages.isEmpty())
                }
            }
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                if (username) {
                    val registry = instrumentation.context.assets.open("native-registry-reference.json").bufferedReader()
                        .use { JsonParser.parseReader(it).asJsonObject }
                    val registrar = RegistryAuthority.forPeer(endpoint, fixtureRegistrar = true)
                    assertEquals(registry.get("registrar").asString, registrar)
                    val name = requireNotNull(SaitoRegistry.readNamecheck(registry.getAsJsonArray("rows").toString(),
                        "NativeCase7@saito", registrar))
                    assertEquals(owner, name.publicKey)
                    RegisteredNameStore(context, owner, registrar).apply {
                        save(name, SaitoBackend.OWNER)
                        assertEquals(name, requireNotNull(load()).name)
                    }
                }
                // The cold case has no MainActivity. Instrumentation/fixture transport remains alive.
                val scenario = if (cold) null else ActivityScenario.launch<MainActivity>(
                    Intent(context, MainActivity::class.java).putExtra("feedPeerUrl", endpoint))
                scenario.use {
                    withFailureCapture({
                        captureNativeScreen(File(context.cacheDir, "social-push-evidence/$capturePrefix-failure.png"))
                    }) {
                        if (cold) check(activeMainActivities().isEmpty())
                        else {
                            if (onboarding) {
                                assertTrue(context.getSharedPreferences("notification-permission", 0).getBoolean("offered", false))
                                if (android.os.Build.VERSION.SDK_INT >= 33) assertEquals(
                                    android.content.pm.PackageManager.PERMISSION_GRANTED,
                                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS))
                                val notifications = hasText("Notifications") and hasClickAction() and isEnabled()
                                waitFor(notifications)
                                ui.waitUntil(15_000) { ui.onNode(notifications).isDisplayed() }
                                ui.onNode(notifications).assertIsDisplayed().performClick()
                                waitForNotificationsScreen()
                                waitFor(hasText("Android notification alerts"))
                                captureNativeScreen(File(context.cacheDir, "social-push-evidence/tap-onboarding-offer.png"), "Enable alerts")
                                ui.onNodeWithText("Enable alerts").performClick()
                                if (android.os.Build.VERSION.SDK_INT < 33) {
                                    ui.waitUntil(15_000) { device.hasObject(By.pkg("com.android.settings")) }
                                    captureNativeScreen(File(context.cacheDir, "social-push-evidence/tap-onboarding-system.png"))
                                    device.pressBack()
                                }
                                waitForNotificationsScreen()
                                waitFor(hasText("Refresh") and isEnabled())
                                ui.waitUntil(15_000) {
                                    ui.onAllNodesWithText("Not now").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
                                }
                                recreateCurrentActivity()
                                waitForNotificationsScreen()
                                waitFor(hasText("Refresh") and isEnabled())
                                val offeredAgainUntil = android.os.SystemClock.elapsedRealtime() + 2_000
                                do {
                                    ui.onAllNodesWithText("Not now").assertCountEquals(0)
                                    android.os.SystemClock.sleep(50)
                                } while (android.os.SystemClock.elapsedRealtime() < offeredAgainUntil)
                                ui.onNodeWithText("Back").performClick()
                                openAlertChoice().assertIsOn()
                                captureNativeScreen(File(context.cacheDir, "social-push-evidence/tap-onboarding-enabled.png"), "Replies and mentions")
                                ui.onNodeWithText("Done").performClick()
                            }
                            if (settings) {
                                openAlertChoice().assertIsOff()
                                captureNativeScreen(File(context.cacheDir, "social-push-evidence/tap-settings-initial.png"), "Replies and mentions")
                                ui.onNode(alertChoice()).performClick()
                                waitFor(alertChoice() and isOn())
                                ui.onNodeWithText("Done").performClick()
                                recreateCurrentActivity()
                                openAlertChoice().assertIsOn()
                                captureNativeScreen(File(context.cacheDir, "social-push-evidence/tap-settings-enabled.png"), "Replies and mentions")
                                ui.onNodeWithText("Done").performClick()
                            }
                            if (chatDraft) openChat(if (groupChat) groupName else contact, groupChat) else {
                                waitFor(hasText("New post") and hasClickAction() and isEnabled())
                                ui.onNode(hasText("New post") and hasClickAction()).performClick()
                            }
                            waitFor(hasSetTextAction() and isEnabled())
                            assertEditorText("Earlier saved text")
                            draftPhoto(chatDraft)
                            ui.onNode(hasSetTextAction()).performTextReplacement(latest)
                            if (chatDraft) {
                                assertEditorText(latest)
                                captureNativeScreen(File(context.cacheDir, "social-push-evidence/$capturePrefix-edited.png"))
                            }
                            if (journey == Journey.ANDROID_SETTINGS_REPLY) {
                                visitAndroidCategories(device, context.packageName, context.cacheDir) {
                                    val retainedDraft = PostDraftStore(context, owner, SaitoBackend.OWNER).load()
                                    assertEquals(latest, retainedDraft.text)
                                    assertEquals(listOf(photo), retainedDraft.images)
                                    assertNull(retainedDraft.transaction)
                                    assertFalse(retainedDraft.queued)
                                }
                                recreateCurrentActivity()
                                waitFor(hasSetTextAction() and isEnabled())
                                assertEditorText(latest)
                                draftPhoto(chatDraft)
                                captureNativeScreen(File(context.cacheDir, "social-push-evidence/tap-android-returned.png"))
                            }
                        }
                        val payload = JsonObject().apply {
                            addProperty("version", 1); addProperty("wallet", owner)
                            addProperty("backend", "owner"); addProperty("signature", signature)
                        }.toString()
                        assertEquals(SocialPushResult.PUBLISHED, runBlocking(Dispatchers.IO) {
                            SocialPush.receive(context, payload, endpoint, 1700000001000, fixtureRegistrar = username)
                        })
                        val published = manager.activeNotifications.single { (it.tag to it.id) !in priorAlerts }
                        assertEquals(if (mention) "mentions" else "replies", published.notification.channelId)
                        assertTrue(device.openNotification())
                        val alert = requireNotNull(device.wait(Until.findObject(By.text(alertText)), 10_000))
                        captureNativeScreen(File(context.cacheDir, "social-push-evidence/$capturePrefix-alert.png"), alertText)
                        alert.click()
                        waitFor(hasText("Conversation"))
                        waitFor(hasText(targetText))
                        ui.onNodeWithText(targetText).assertIsDisplayed()
                        captureNativeScreen(File(context.cacheDir, "social-push-evidence/$capturePrefix-conversation.png"), targetText)
                        if (chatDraft) assertSavedChat()
                        if (cold) {
                            recreateCurrentActivity()
                            waitFor(hasText("Conversation"))
                            waitFor(hasText(targetText))
                            ui.onNodeWithText(targetText).assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir, "social-push-evidence/$capturePrefix-recreated.png"), targetText)
                        }
                        ui.onNodeWithText("Back").performClick()
                        waitFor(hasText("New post") and hasClickAction() and isEnabled())
                        if (cold) {
                            recreateCurrentActivity()
                            waitFor(hasText("New post") and hasClickAction() and isEnabled())
                            val until = android.os.SystemClock.elapsedRealtime() + 2_000
                            do {
                                ui.onAllNodesWithText("Conversation").assertCountEquals(0)
                                android.os.SystemClock.sleep(50)
                            } while (android.os.SystemClock.elapsedRealtime() < until)
                            captureNativeScreen(File(context.cacheDir, "social-push-evidence/$capturePrefix-consumed.png"))
                        }
                        if (chatDraft) {
                            assertSavedChat()
                            recreateCurrentActivity()
                            openChat(if (groupChat) groupName else contact, groupChat)
                        } else ui.onNode(hasText("New post") and hasClickAction()).performClick()
                        waitFor(hasSetTextAction() and isEnabled())
                        assertEditorText(latest)
                        val bitmap = draftPhoto(chatDraft).captureToImage().asAndroidBitmap()
                        assertEquals(0xffdc1e28.toInt(), bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
                        captureNativeScreen(File(context.cacheDir, "social-push-evidence/$capturePrefix-draft-restored.png"))
                        val saved = PostDraftStore(context, owner, SaitoBackend.OWNER).load()
                        assertEquals(if (chatDraft) "Earlier saved text" else latest, saved.text)
                        assertEquals(listOf(photo), saved.images)
                        assertNull(saved.transaction)
                        assertFalse(saved.queued)
                        if (settings) {
                            openAlertChoice().assertIsOn().performClick()
                            waitFor(alertChoice() and isOff())
                            captureNativeScreen(File(context.cacheDir, "social-push-evidence/tap-settings-disabled.png"), "Replies and mentions")
                            ui.onNodeWithText("Done").performClick()
                            val fresh = JsonObject().apply {
                                addProperty("version", 1); addProperty("wallet", owner)
                                addProperty("backend", "owner"); addProperty("signature", freshSignature)
                            }.toString()
                            assertEquals(SocialPushResult.SUPPRESSED, runBlocking(Dispatchers.IO) {
                                SocialPush.receive(context, fresh, endpoint, 1700000001001)
                            })
                            val until = android.os.SystemClock.elapsedRealtime() + 2_000
                            do {
                                assertTrue("A different unread reply must stay silent after alerts are disabled",
                                    manager.activeNotifications.none { (it.tag to it.id) !in priorAlerts })
                                android.os.SystemClock.sleep(50)
                            } while (android.os.SystemClock.elapsedRealtime() < until)
                        }
                        check(wallets.loadExisting().exportJson() == wallet.exportJson()) { "Active fixture wallet changed" }
                        assertEquals(SaitoBackend.OWNER, backends.load())
                        assertTrue(peer.transactions.isEmpty() && peer.reports.isEmpty() && peer.relayedMessages.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            var activitiesStopped = false
            fun requireStopped() = check(activitiesStopped) { "Installation restoration deferred because a fixture Activity remains active" }
            val cleanup = listOf<() -> Unit>(
                { finishFixtureActivities(); activitiesStopped = true },
                { manager.activeNotifications.filter { (it.tag to it.id) !in priorAlerts }.forEach { manager.cancel(it.tag, it.id) } },
                { requireStopped(); retained.restoreFiles() }, { requireStopped(); retained.restorePreferences() },
                { requireStopped(); wallets.replace(wallets.loadExisting(), previous); check(wallets.loadExisting().exportJson() == previousJson) },
                { requireStopped(); backends.save(previousBackend); check(backends.load() == previousBackend) })
            var cleanupFailure: Throwable? = null
            cleanup.forEach { action -> try { action() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure!!.addSuppressed(failure)
            } }
            cleanupFailure?.let { if (primary != null) primary!!.addSuppressed(it) else throw it }
        }
    }

    private fun draftPhoto(chatDraft: Boolean): SemanticsNodeInteraction {
        val image = ui.onNodeWithContentDescription("Image 1 of 1")
        if (chatDraft) ui.waitUntil(15_000) { image.isDisplayed() }
        return (if (chatDraft) image else image.performScrollTo()).assertIsDisplayed()
    }

    private fun assertEditorText(text: String) {
        ui.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
            SemanticsProperties.EditableText, AnnotatedString(text)))
    }

    private fun openChat(label: String, group: Boolean = false) {
        hideKeyboard()
        val chat = hasText("Chat") and hasClickAction() and isEnabled()
        waitFor(chat)
        ui.waitUntil(15_000) { ui.onNode(chat).isDisplayed() }
        ui.onNode(chat).assertIsDisplayed().performClick()
        if (group) {
            val groups = hasText("Groups") and hasClickAction() and isEnabled()
            waitFor(groups)
            ui.waitUntil(15_000) { ui.onNode(groups).isDisplayed() }
            ui.onNode(groups).assertIsDisplayed().performClick()
        }
        val row = hasClickAction() and isEnabled() and hasAnyDescendant(hasText(label))
        ui.waitUntil(15_000) { ui.onNode(row, useUnmergedTree = true).isDisplayed() }
        ui.onNode(row, useUnmergedTree = true).assertIsDisplayed().performClick()
        waitFor(hasSetTextAction() and isEnabled())
    }

    private fun alertChoice() = hasText("Replies and mentions") and isToggleable()

    private fun waitForNotificationsScreen() {
        val heading = hasText("Notifications") and !hasClickAction()
        waitFor(heading)
        ui.waitUntil(15_000) { ui.onNode(heading).isDisplayed() }
    }

    private fun visitAndroidCategories(device: UiDevice, appPackage: String, cache: File, verifySavedWork: () -> Unit) {
        openSettings()
        val shortcut = hasText("Android notification settings") and hasClickAction() and isEnabled()
        waitFor(shortcut)
        ui.onNode(shortcut).performScrollTo().performClick()
        // Saving the draft and opening Settings resume on the Compose test dispatcher.
        // Keep its clock advancing while observing the external Activity.
        ui.waitUntil(10_000) { device.hasObject(By.pkg("com.android.settings")) }
        ui.waitUntil(10_000) {
            device.hasObject(By.pkg("com.android.settings").text("Red Square")) ||
                device.hasObject(By.pkg("com.android.settings").desc("Red Square"))
        }
        // Android 17 initially collapses the app's categories under Other.
        if (!device.hasObject(By.text("Replies"))) {
            device.findObject(By.res("com.android.settings", "expand_icon"))?.click()
        }
        for ((label, name) in listOf("Replies" to "replies", "Mentions" to "mentions")) {
            if (!device.hasObject(By.text(label))) {
                assertTrue("Android must offer the $label category",
                    UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(label))
            }
            assertTrue(device.hasObject(By.text(label)))
            captureNativeScreen(File(cache, "social-push-evidence/tap-android-$name.png"), label)
        }
        assertEquals("com.android.settings", device.currentPackageName)
        verifySavedWork()
        device.pressBack()
        ui.waitUntil(10_000) { device.currentPackageName == appPackage }
    }

    private fun openAlertChoice(): SemanticsNodeInteraction {
        openSettings()
        waitFor(alertChoice() and isEnabled())
        return ui.onNode(alertChoice()).performScrollTo()
    }

    private fun hideKeyboard() {
        val activity = activeMainActivities().single()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
    }

    private fun openSettings() {
        hideKeyboard()
        val button = hasText("Settings") and hasClickAction() and isEnabled()
        waitFor(button)
        ui.waitUntil(15_000) { ui.onNode(button).isDisplayed() }
        ui.onNode(button).assertIsDisplayed().performClick()
        val title = hasText("Settings") and hasAnyAncestor(isDialog())
        waitFor(title)
        ui.waitUntil(15_000) { ui.onNode(title).isDisplayed() }
    }

    private fun recreateCurrentActivity() {
        val previous = activeMainActivities().single()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { previous.recreate() }
        ui.waitUntil(15_000) {
            activeMainActivities().singleOrNull()?.let { it !== previous } == true
        }
    }

    private fun activeMainActivities(): List<MainActivity> {
        var result = emptyList<MainActivity>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            result = Stage.values().filter { it != Stage.DESTROYED }
                .flatMap { monitor.getActivitiesInStage(it) }.filterIsInstance<MainActivity>().distinct()
        }
        return result
    }

    private fun finishFixtureActivities() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activities = activeMainActivities()
        instrumentation.runOnMainSync { activities.forEach { it.finish() } }
        instrumentation.waitForIdleSync()
        ui.waitUntil(5_000) { activeMainActivities().isEmpty() }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        ui.waitUntil(15_000) { ui.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }
}
