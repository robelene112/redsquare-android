package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import nl.davidengelen.redsquare.chat.composeChatPhoto
import nl.davidengelen.redsquare.chat.ChatWorkStore
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Real IME and physical Send at the agreed Android/controlled-peer boundary. */
class ChatKeyboardPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun privateChatCanSendAMultilineDraftWithTheSystemKeyboardOpen() = exercise(group = false)
    @Test fun groupChatCanSendAMultilineDraftWithTheSystemKeyboardOpen() = exercise(group = true)

    @Test fun privateChatCanSendAPhotoCaptionWithTheSystemKeyboardOpen() = exercise(group = false, photo = true)
    @Test fun groupChatCanSendAPhotoCaptionWithTheSystemKeyboardOpen() = exercise(group = true, photo = true)

    @Test fun privateChatKeepsSendReachableForALongRecoveredReplyWithAPhoto() =
        exercise(group=false,photo=true,longReply=true)
    @Test fun groupChatKeepsSendReachableForALongRecoveredReplyWithAPhoto() =
        exercise(group=true,photo=true,longReply=true)

    @Test fun privateChatKeepsFirstUseSendReachableWithLargerText() = exercise(group=false,largeFont=true)
    @Test fun groupChatKeepsFirstUseSendReachableWithLargerText() = exercise(group=true,largeFont=true)
    @Test fun privateChatKeepsLongReplyPhotoSendReachableWithLargerText() =
        exercise(group=false,photo=true,longReply=true,largeFont=true)
    @Test fun groupChatKeepsLongReplyPhotoSendReachableWithLargerText() =
        exercise(group=true,photo=true,longReply=true,largeFont=true)

    private fun exercise(group: Boolean, photo: Boolean = false, longReply: Boolean = false, largeFont:Boolean=false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val kind = (if (group) "group" else "private") + (if (photo) "-photo" else "-text") + (if(longReply) "-long-reply" else "") + (if(largeFont) "-font130" else "")
        val fontDevice=UiDevice.getInstance(instrumentation)
        val priorFontScale=context.resources.configuration.fontScale
        val priorFontSetting=if(largeFont)fontDevice.executeShellCommand("settings get system font_scale").trim() else null
        if(largeFont)check(priorFontSetting=="null"||
            (requireNotNull(priorFontSetting).matches(Regex("""[0-9]+([.][0-9]+)?"""))&&
                priorFontSetting.toFloatOrNull()?.let{it.isFinite()&&it>0f}==true))
        val contact = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
        val identity = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val groupId = SaitoWallet.fromPrivateKey("0".repeat(63) + "3").publicKey
        val groupName = "Keyboard test group"
        val wallet = if (group) identity.withNamedChatGroup(groupId, groupName)
            else identity.withChatContact(contact.publicKey)
        val wallets = AndroidWalletStore(context)
        val original = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val originalBackend = backends.load()
        val prefixes = SaitoBackend.entries.flatMap { backend -> listOf("chat.${backend.id}.${wallet.publicKey}.",
            "chatwork.${backend.id}.${wallet.publicKey}.", "chatreactions.${backend.id}.${wallet.publicKey}.",
            "groupchat.${backend.id}.${wallet.publicKey}.") } + "personal.${wallet.publicKey}."
        val retained = ChatFixtureState(context, prefixes, wallet.publicKey)
        val reads = AtomicInteger()
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = {
            reads.incrementAndGet(); "[]"
        })
        val draft = "A message with the keyboard open 😀\nSecond line\nThird line"
        val photoSource = if (photo) instrumentation.context.assets.open("native-images-reference.json").bufferedReader()
            .use { com.google.gson.JsonParser.parseReader(it).asJsonObject.getAsJsonObject("images").get("red").asString }
            else null
        val replyPrefix=if(longReply) """<blockquote href="${contact.publicKey}1700000123400">Recovered quote line01.<br>Recovered quote line02.<br>Recovered quote line03.<br>Recovered quote line04.<br>Recovered quote line05.<br>Recovered quote line06.<br>Recovered quote line07.<br>Recovered quote line08.<br>Recovered quote line09.<br>Recovered quote line10.<br>Recovered quote line11.<br>Recovered quote line12.</blockquote>""" else ""
        val messageBody = photoSource?.let { composeChatPhoto(it, draft) } ?: draft
        val composed = replyPrefix + messageBody
        if(longReply) {
            val parsed=requireNotNull(nl.davidengelen.redsquare.chat.privateReply(composed))
            check(parsed.prefix==replyPrefix&&parsed.body==messageBody&&parsed.author==contact.publicKey&&
                parsed.reference==contact.publicKey+"1700000123400"&&parsed.quote.lines().size==12&&
                parsed.quote.endsWith("Recovered quote line12."))
        }
        val folder = File(context.cacheDir, "chat-keyboard-evidence")
        var primary: Throwable? = null
        try {
            // The finally cleanup below is established before this emulator-only mutation.
            if(largeFont) {
                fontDevice.executeShellCommand("settings put system font_scale 1.3")
                awaitFontScale(context,1.3f)
                folder.mkdirs()
                File(folder,"$kind-font-scale.json").writeText(com.google.gson.JsonObject().apply {
                    addProperty("expected",1.3f);addProperty("observed",context.resources.configuration.fontScale)
                    addProperty("priorResource",priorFontScale);addProperty("priorSetting",priorFontSetting)
                    addProperty("priorSettingMatchesResource",priorFontSetting?.toFloatOrNull()?.let{kotlin.math.abs(it-priorFontScale)<0.01f})
                }.toString())
            }
            retained.prepare()
            // Exercise first-use text composition without dismissing its backup reminder.
            // Photo cases keep the established reminder-dismissed recovered-draft setup.
            if (!photo) check(context.getSharedPreferences("wallet-backup-reminders", 0)
                .edit().putBoolean("hidden:${wallet.publicKey}", false).commit())
            wallets.replace(original, wallet)
            backends.save(SaitoBackend.OWNER)
            if (group) GroupChatStore(context, wallet, SaitoBackend.OWNER).receive(
                GroupChat.create(contact, groupId, groupName, listOf(wallet.publicKey), 1700000123400).envelope())
            // Seed a recovered attachment through the protected draft API. Real picker
            // selection/preparation is covered by ChatImagesPreviewTest, not bypassed here.
            if (photo||largeFont) {
                if (group) GroupChatStore(context, wallet, SaitoBackend.OWNER).saveDraft(groupId, composed)
                else ChatWorkStore(context, wallet, SaitoBackend.OWNER, contact.publicKey).saveDraft(composed)
            }
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ captureNativeScreen(File(folder, "$kind-failure.png")) }) {
                        val chat = hasText("Chat") and hasClickAction() and isEnabled()
                        ui.waitUntil(20_000) { displayed(chat) }
                        ui.onNode(chat).performClick()
                        val groups = hasText("Groups") and hasClickAction() and isEnabled()
                        ui.waitUntil(20_000) { displayed(groups) }
                        if (group) ui.onNode(groups).performClick()
                        val row = hasClickAction() and isEnabled() and hasAnyDescendant(
                            hasText(if (group) groupName else contact.publicKey))
                        ui.waitUntil(20_000) { displayed(row, true) }
                        ui.onNode(row, useUnmergedTree = true).performClick()
                        val editor = hasSetTextAction() and isEnabled()
                        if(longReply||largeFont) {
                            ui.onNodeWithText(if(group)groupName else contact.publicKey).assertIsDisplayed()
                            ui.waitUntil(20_000) { reads.get()>0&&
                                (if(group)GroupChatStore(context,wallet,SaitoBackend.OWNER).load(groupId)?.draft
                                 else ChatWorkStore(context,wallet,SaitoBackend.OWNER,contact.publicKey).load().draft)==composed }
                            physicalSend(scenario,File(folder,"$kind-physical-send-closed.png"))
                        }
                        ui.waitUntil(20_000) { displayed(editor) && reads.get() > 0 }
                        if(longReply) ui.onNodeWithContentDescription("Chat composition preview")
                            .performScrollToNode(hasContentDescription("Image 1 of 1"))
                        if (photo) ui.waitUntil(20_000) {
                            try {
                                val bitmap = ui.onNodeWithContentDescription("Image 1 of 1")
                                    .assertIsDisplayed().captureToImage().asAndroidBitmap()
                                bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) == 0xffdc1e28.toInt()
                            } catch (_: AssertionError) { false }
                        }
                        ui.onNode(editor).performTextReplacement(draft)
                        ui.onNode(editor).performClick()
                        val imeBottom = AtomicInteger()
                        val statusTop = AtomicInteger()
                        val device = UiDevice.getInstance(instrumentation)
                        val exactDraft = editor and SemanticsMatcher("Exact multiline draft") {
                            it.config.getOrNull(SemanticsProperties.EditableText)?.text == draft
                        }
                        fun observeKeyboard(): Boolean {
                            scenario.onActivity { activity ->
                                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                                statusTop.set(insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0)
                                imeBottom.set(if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true)
                                    insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0)
                            }
                            return imeBottom.get() > 0
                        }
                        fun readySend(): android.graphics.Rect {
                            ui.waitUntil(10_000) { observeKeyboard() }
                            ui.waitUntil(10_000) { displayed(exactDraft) }
                            ui.onNode(exactDraft).assertIsDisplayed()
                            val send = hasText("Send") and hasClickAction() and isEnabled()
                            ui.waitUntil(10_000) { displayed(send) }
                            ui.onNode(send).assertIsDisplayed()
                            val bounds = physicalSend(scenario,File(folder,"$kind-physical-send-ime.png"),requireIme=true)
                            val privacy = requireNotNull(device.findObject(androidx.test.uiautomator.By.text(
                                context.getString(if (group) R.string.group_unencrypted else R.string.chat_encrypted)))).visibleBounds
                            check(privacy.height() > 0 && privacy.top >= statusTop.get()) {
                                "Chat privacy indication must remain below the status bar: $privacy"
                            }
                            return bounds
                        }
                        fun restoredChrome() {
                            ui.waitUntil(10_000) { displayed(hasText("Wallet") and hasClickAction()) }
                            if (!photo) {
                                ui.onNodeWithText(context.getString(R.string.wallet_backup_reminder)).assertDoesNotExist()
                                check(!context.getSharedPreferences("wallet-backup-reminders", 0)
                                    .getBoolean("hidden:${wallet.publicKey}", true))
                            }
                            if (group) {
                                val members=hasText("Members") and hasClickAction()
                                ui.withConversationControl("Members") {
                                    ui.waitUntil(10_000) { displayed(members) }
                                    assertIsDisplayed()
                                }
                            }
                        }
                        readySend()
                        captureNativeScreen(File(folder, "$kind-keyboard-open.png"))
                        // Ordinary Back should dismiss the IME, preserve the exact draft,
                        // and restore conversation controls while backup guidance stays on Home.
                        val injectedBack = device.pressBack()
                        println("Back injection reported $injectedBack; observing keyboard dismissal")
                        ui.waitUntil(10_000) { !observeKeyboard() }
                        restoredChrome()
                        ui.onNode(exactDraft).assertIsDisplayed()
                        if(longReply) ui.onNodeWithContentDescription("Chat composition preview")
                            .performScrollToNode(hasContentDescription("Image 1 of 1"))
                        if (photo) ui.onNodeWithContentDescription("Image 1 of 1").assertIsDisplayed()
                        if(longReply)check((if(group)GroupChatStore(context,wallet,SaitoBackend.OWNER).load(groupId)?.draft
                            else ChatWorkStore(context,wallet,SaitoBackend.OWNER,contact.publicKey).load().draft)==composed)
                        check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                        captureNativeScreen(File(folder, "$kind-keyboard-dismissed-unsent.png"))
                        // Tap the editor's trailing padding rather than an existing
                        // selection/cursor handle, which can open the editing toolbar.
                        val editorBounds = requireNotNull(device.findObject(
                            androidx.test.uiautomator.By.clazz("android.widget.EditText"))).visibleBounds
                        check(editorBounds.width() > 0 && editorBounds.height() > 0)
                        check(device.click(editorBounds.right - minOf(8, editorBounds.width() / 2),
                            editorBounds.bottom - minOf(8, editorBounds.height() / 2)))
                        val bounds = readySend()
                        check(observeKeyboard())
                        check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                        check(device.click(bounds.centerX(), bounds.centerY()))
                        ui.waitUntil(20_000) { peer.transactions.size == 1 && peer.relayedMessages.size == 1 }
                        val sent = postingTransaction(peer.transactions.single())
                        check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single()) == sent.envelope())
                        val text = if (group) GroupChat.readMessage(requireNotNull(
                            GroupChatStore(context, wallet, SaitoBackend.OWNER).load(groupId)).group, sent.envelope()).text
                            else PrivateChat.read(wallet, sent.envelope(), contact.publicKey).text
                        check(text == composed)
                        ui.waitUntil(20_000) { displayed(editor and SemanticsMatcher("Empty sent draft") {
                            it.config.getOrNull(SemanticsProperties.EditableText)?.text == ""
                        }) }
                        ui.waitUntil(10_000) { !observeKeyboard() }
                        restoredChrome()
                        captureNativeScreen(File(folder, "$kind-explicitly-sent.png"))
                        check(wallets.loadExisting().exportJson() == wallet.exportJson() && backends.load() == SaitoBackend.OWNER)
                        check(peer.clientKeys.isNotEmpty() && peer.clientKeys.all { it == wallet.publicKey })
                        check(peer.transactions.size == 1 && peer.relayedMessages.size == 1 && peer.reports.isEmpty())
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, { retained.restoreFiles() },
                { wallets.replace(wallets.loadOrCreate(), original) }, { backends.save(originalBackend) },
                { retained.restorePreferences() },
                { if(largeFont) {
                    fontDevice.executeShellCommand(if(priorFontSetting=="null")"settings delete system font_scale"
                        else "settings put system font_scale $priorFontSetting")
                    awaitFontScale(context,priorFontScale)
                    val restoredSetting=fontDevice.executeShellCommand("settings get system font_scale").trim()
                    // Android10 may persist its implicit default while applying configuration.
                    // Preserve an explicit prior setting exactly; absent default1.0 has two valid forms.
                    val implicitDefault=android.os.Build.VERSION.SDK_INT==29&&priorFontSetting=="null"&&
                        priorFontScale==1f&&restoredSetting=="1.0"
                    folder.mkdirs()
                    File(folder,"$kind-font-restore.json").writeText(com.google.gson.JsonObject().apply {
                        addProperty("expectedSetting",priorFontSetting);addProperty("observedSetting",restoredSetting)
                        addProperty("expectedResource",priorFontScale)
                        addProperty("observedResource",context.resources.configuration.fontScale)
                    }.toString())
                    check(restoredSetting==priorFontSetting||implicitDefault) {
                        "The original font setting or Android10 implicit default must be restored: expected $priorFontSetting, observed $restoredSetting"
                    }
                } })
        }
    }

    private fun awaitFontScale(context:android.content.Context,expected:Float) {
        val until=android.os.SystemClock.elapsedRealtime()+10_000
        while(kotlin.math.abs(context.resources.configuration.fontScale-expected)>=0.01f&&
            android.os.SystemClock.elapsedRealtime()<until)android.os.SystemClock.sleep(50)
        check(kotlin.math.abs(context.resources.configuration.fontScale-expected)<0.01f) {
            "Expected actual rendered-resource font scale $expected, observed ${context.resources.configuration.fontScale}"
        }
    }

    private fun physicalSend(scenario:ActivityScenario<MainActivity>,capture:File,requireIme:Boolean=false):android.graphics.Rect {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val device=UiDevice.getInstance(instrumentation)
        captureNativeScreen(capture)
        var target=requireNotNull(device.findObject(androidx.test.uiautomator.By.text("Send"))) {
            "Send must have a physical target beneath retained composition"
        }
        while(!target.isClickable)target=requireNotNull(target.parent){"Send has no clickable ancestor"}
        val bounds=target.visibleBounds
        // Capture and native lookup can span an IME transition. Read current insets
        // alongside the target instead of comparing it with the earlier readiness sample.
        var top=0;var bottom=0;var imeVisible=false
        scenario.onActivity { activity->
            val insets=ViewCompat.getRootWindowInsets(activity.window.decorView)
            top=insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
            bottom=insets?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())?.bottom ?: 0
            imeVisible=insets?.isVisible(WindowInsetsCompat.Type.ime())==true
        }
        val minimum=kotlin.math.round(48*instrumentation.targetContext.resources.displayMetrics.density).toInt()
        runCatching {
            File(capture.parentFile,"${capture.nameWithoutExtension}-bounds.json").writeText(com.google.gson.JsonObject().apply {
                addProperty("bounds",bounds.toShortString());addProperty("minimum",minimum)
                addProperty("topInset",top);addProperty("bottomInset",bottom)
                addProperty("imeVisible",imeVisible);addProperty("imeRequired",requireIme)
                addProperty("displayHeight",device.displayHeight)
            }.toString())
        }
        check(!requireIme||imeVisible){"The real system keyboard must remain visible for the Send measurement"}
        check(bounds.width()>0&&bounds.height()>=minimum&&bounds.top>=top&&bounds.bottom<=device.displayHeight-bottom) {
            "Full Send target must stay inside the usable screen: $bounds, minimum=$minimum, barsOrIme=$top/$bottom"
        }
        return bounds
    }

    private fun displayed(target: SemanticsMatcher, unmerged: Boolean = false) =
        try { ui.onNode(target, useUnmergedTree = unmerged).assertIsDisplayed(); true }
        catch (_: AssertionError) { false }
}
