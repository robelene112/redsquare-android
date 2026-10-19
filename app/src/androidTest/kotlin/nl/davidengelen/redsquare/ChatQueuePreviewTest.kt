package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.*
import java.io.File
import org.junit.Rule
import org.junit.Test

class ChatQueuePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun anOfflinePrivateMessageRemainsCancellableAfterReopening() = exercisePrivateQueue(PrivateQueueCase.CANCEL)

    @Test fun anOfflinePrivateMessageResumesOnItsOriginalSignatureWhileBackgrounded() = exercisePrivateQueue(PrivateQueueCase.BACKGROUND)

    @Test fun aQueuedPrivateMessageWaitsForItsOriginalBackendWithoutRerouting() = exercisePrivateQueue(PrivateQueueCase.BACKEND)

    @Test fun replacingTheWalletPausesItsQueuedMessageUntilTheOriginalIdentityReturns() = exercisePrivateQueue(PrivateQueueCase.IDENTITY)

    @Test fun anArchivedQueuedOriginalReconcilesWithoutAnotherBroadcastOrClaimedDelivery() = exercisePrivateQueue(PrivateQueueCase.ARCHIVED)

    @Test fun aWrongArchivedOriginalIsRefusedAndACompletedRetryClearsItsOldError() = exercisePrivateQueue(PrivateQueueCase.WRONG_ARCHIVE)

    @Test fun failedQueueSavingKeepsTheDraftAndNeverBecomesAutomaticAfterRepair() = exercisePrivateQueue(PrivateQueueCase.STORAGE)

    @Test fun anOfflineGroupMessageCanBeCancelledAfterReopeningWithoutEnteringHistoryOrSending() = exerciseGroupQueue(GroupQueueCase.CANCEL)

    @Test fun anOfflineGroupMessageResumesItsOriginalWhileBackgrounded() = exerciseGroupQueue(GroupQueueCase.BACKGROUND)

    @Test fun aQueuedGroupMessageCannotSendAfterItsAuthorIsRemovedAndCanStillBeCancelled() = exerciseGroupQueue(GroupQueueCase.REMOVED)

    @Test fun anArchivedGroupOriginalReconcilesWithoutAnotherRawOrRelay() = exerciseGroupQueue(GroupQueueCase.ARCHIVED)

    @Test fun failedGroupQueueSavingKeepsTheDraftAndNeverSendsAfterRepair() = exerciseGroupQueue(GroupQueueCase.STORAGE)

    @Test fun aQueuedGroupOriginalWaitsForItsIdentityAndSelectedBackendWithoutRerouting() = exerciseGroupQueue(GroupQueueCase.SCOPE)

    private enum class GroupQueueCase { CANCEL, BACKGROUND, REMOVED, ARCHIVED, STORAGE, SCOPE }
    private fun exerciseGroupQueue(case:GroupQueueCase) {
        val cancel=case==GroupQueueCase.CANCEL
        val removed=case==GroupQueueCase.REMOVED
        val archived=case==GroupQueueCase.ARCHIVED
        val scoped=case==GroupQueueCase.SCOPE
        val label=case.name.lowercase()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallets=AndroidWalletStore(context)
        val original=wallets.loadOrCreate()
        val backends=AndroidBackendStore(context)
        val backend=backends.load()
        val author=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixture only.
        val member=SaitoWallet.fromPrivateKey("0".repeat(63)+"2").publicKey
        val key=SaitoWallet.fromPrivateKey("0".repeat(63)+"9") // Public unsafe group fixture only.
        val name="Offline group queue"
        val wallet=author.withNamedChatGroup(key.publicKey,name,key)
        val replacement=SaitoWallet.fromPrivateKey("0".repeat(63)+"3").withNamedChatGroup(key.publicKey,name) // Public unsafe fixture only.
        val prefix="groupchat.owner.${wallet.publicKey}."
        val prefixes=listOf(wallet,replacement).flatMap{identity->
            SaitoBackend.entries.map{"groupchat.${it.id}.${identity.publicKey}."}+listOf("personal.${identity.publicKey}.","groupchatpush.${identity.publicKey}.")
        }
        val retained=synchronized(SaitoLocalState.lock) {
            context.noBackupFilesDir.listFiles()!!.filter{file->prefixes.any(file.name::startsWith)}.associate{check(it.isFile);it.name to it.readBytes()}
        }
        val prefs=context.getSharedPreferences("notification-permission",0)
        val offered=if(prefs.contains("offered"))prefs.getBoolean("offered",false) else null
        val backupPrefs=context.getSharedPreferences("wallet-backup-reminders",0)
        val backupKey="hidden:${wallet.publicKey}"
        val previousBackupHidden=if(backupPrefs.contains(backupKey))backupPrefs.getBoolean(backupKey,false) else null
        val text="Keep my offline group message 😀"
        var primary:Throwable?=null
        try {
            synchronized(SaitoLocalState.lock){retained.keys.forEach{check(File(context.noBackupFilesDir,it).delete())}}
            check(prefs.edit().putBoolean("offered",true).commit())
            if(cancel)check(backupPrefs.edit().putBoolean(backupKey,false).commit())
            wallets.replace(original,wallet);backends.save(SaitoBackend.OWNER)
            val store=nl.davidengelen.redsquare.chat.GroupChatStore(context,wallet,SaitoBackend.OWNER)
            val creation=GroupChat.create(wallet,key.publicKey,name,listOf(member),1700000123400)
            store.prepareCreation(creation,key)
            store.record(key.publicKey,creation.id,nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
            if(removed)store.receive(GroupChat.promote(wallet,requireNotNull(store.load(key.publicKey)).group,member,1700000123401).envelope())
            val alternate=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={"[]"})
            alternate.start().use{alternateServer->
            // Choose the offline port only after the live alternate is already bound.
            val port=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use{it.localPort}
            fun checkpoint(stage:String) {
                val loaded=runCatching{store.load(key.publicKey)}
                val saved=loaded.getOrNull()
                val record=com.google.gson.JsonObject().apply {
                    addProperty("stage",stage);addProperty("ownerPort",port);addProperty("alternatePort",alternateServer.port)
                    addProperty("backend",backends.load().id);addProperty("storeReadable",loaded.isSuccess)
                    addProperty("status",saved?.status?.name);addProperty("queuedId",saved?.queuedMessage?.candidate?.id)
                    addProperty("candidateId",saved?.candidate?.id);addProperty("automatic",saved?.automaticMessage)
                    addProperty("alternateRequests",alternate.requests.get());addProperty("alternateRawCount",alternate.transactions.size)
                    addProperty("alternateRelayCount",alternate.relayedMessages.size)
                    add("historyIds",com.google.gson.JsonArray().apply{saved?.messages?.take(8)?.forEach{add(it.id)}})
                    add("alternateClients",com.google.gson.JsonArray().apply{alternate.clientKeys.distinct().take(8).forEach{add(it)}})
                    add("alternateRawIds",com.google.gson.JsonArray().apply{alternate.transactions.take(8).forEach{frame->
                        add(runCatching{postingTransaction(frame).id}.getOrNull())}})
                }
                File(context.cacheDir,"chat-queue-evidence/group-$label-$stage-checkpoint.json")
                    .apply{parentFile!!.mkdirs();writeText(record.toString())}
            }
            checkpoint("bound")
            check(port!=alternateServer.port){"Offline owner port was reused by the alternate peer"}
            ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)
                .putExtra("feedPeerUrl","ws://127.0.0.1:$port/wsopen")
                .putExtra("feedOfficialPeerUrl","ws://127.0.0.1:${alternateServer.port}/wsopen")).use{scenario->
                withFailureCapture({
                    try{checkpoint("failure")}finally{
                        captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/group-$label-failure.png"))}
                }) {
                    fun open(){
                        val chat=hasText("Chat") and hasClickAction() and isEnabled()
                        ui.waitUntil(20_000){displayed(hasText("Groups"))||displayed(chat)}
                        if(!displayed(hasText("Groups")))ui.onNode(chat).performClick()
                        clickWhenReady("Groups")
                        clickWhenReady(name)
                        ui.waitUntil(20_000){displayed(hasText("Message"))||displayed(hasText("Queued"))}
                    }
                    open()
                    ui.waitUntil(20_000){displayed(hasSetTextAction() and isEnabled())}
                    ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(text)
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    if(case==GroupQueueCase.STORAGE) {
                        ui.waitUntil(20_000){try{store.load(key.publicKey)?.draft==text}catch(_:Exception){false}}
                        val file=File(context.noBackupFilesDir,"$prefix${key.publicKey}.v1")
                        withQueueStorageFailureOnSend(file) {
                            waitFor(context.getString(R.string.group_error))
                            ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
                                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(text)))
                            ui.onAllNodesWithText("Queued").assertCountEquals(0)
                            captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/group-storage-refused.png"))
                        }
                        val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={"[]"})
                        peer.start(port).use {
                            scenario.recreate();open()
                            ui.waitUntil(20_000){displayed(hasSetTextAction() and isEnabled())}
                            ui.onNode(hasSetTextAction() and isEnabled()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(text)))
                            ui.waitUntil(20_000){peer.requests.get()>0}
                            android.os.SystemClock.sleep(6_000)
                            check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
                            val saved=requireNotNull(store.load(key.publicKey))
                            check(saved.draft==text&&saved.queuedMessage==null&&!saved.automaticMessage&&saved.messages.isEmpty())
                            check(saved.candidate?.envelope()==creation.envelope()&&saved.status==nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
                            check(saved.creationKey?.exportJson()==key.exportJson())
                            check(wallets.loadExisting().exportJson()==wallet.exportJson()&&backends.load()==SaitoBackend.OWNER)
                            peer.assertHealthy()
                            captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/group-storage-restored-draft.png"))
                        }
                        return@withFailureCapture
                    }
                    clickWhenReady("Send")
                    waitFor("Queued")
                    captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/group-$label-queued.png"))
                    if(cancel) {
                        val warnings=listOf(R.string.group_history_error,R.string.group_live_error)
                        val actionError=hasText(context.getString(R.string.group_error)) or
                            hasText(context.getString(R.string.chat_automatic_send_error))
                        ui.waitUntil(30_000){warnings.all{resource->
                            ui.onAllNodesWithText(context.getString(resource)).fetchSemanticsNodes().isNotEmpty()}&&
                            ui.onAllNodes(actionError).fetchSemanticsNodes().isNotEmpty()}
                        ui.onNodeWithText(context.getString(R.string.wallet_backup_reminder)).assertIsDisplayed()
                        // Reachability applies even while the existing safety guard disables cancellation.
                        physicalChatAction(scenario,"three-warnings")
                        val queuedBeforeScroll=requireNotNull(requireNotNull(store.load(key.publicKey)).queuedMessage).candidate.envelope()
                        val status=ui.onNodeWithContentDescription("Group status and actions")
                        listOf("action" to actionError,
                            "history" to hasText(context.getString(R.string.group_history_error)),
                            "live" to hasText(context.getString(R.string.group_live_error))).forEach { (name,warning)->
                            status.performScrollToNode(warning)
                            ui.onNode(warning).assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/group-cancel-warning-$name.png"))
                        }
                        status.performScrollToNode(hasText("Members") and hasClickAction())
                        scrollChatRegionToEnd("Group status and actions","group-cancel-status-scroll.json")
                        val members=physicalChatAction(scenario,"members-action",action="Members")
                        val device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        check(device.click(members.centerX(),members.centerY()))
                        val member=hasText(wallet.publicKey) and hasAnyAncestor(isDialog())
                        ui.waitUntil(10_000){displayed(member)}
                        ui.onNode(member).assertIsDisplayed()
                        captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/group-cancel-members-open.png"))
                        ui.onNode(hasText("Done") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
                        ui.waitUntil(10_000){ui.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()}
                        val afterScroll=requireNotNull(store.load(key.publicKey))
                        check(afterScroll.queuedMessage?.candidate?.envelope()==queuedBeforeScroll&&
                            afterScroll.candidate?.envelope()==creation.envelope()&&afterScroll.messages.isEmpty())
                        check(alternate.transactions.isEmpty()&&alternate.relayedMessages.isEmpty())
                    }
                    checkpoint("before-recreation")
                    scenario.recreate();open();waitFor("Queued")
                    val queued=requireNotNull(requireNotNull(store.load(key.publicKey)).queuedMessage).candidate
                    if(cancel) {
                        val warnings=listOf(R.string.group_history_error,R.string.group_live_error)
                        ui.waitUntil(30_000){warnings.all{resource->
                            ui.onAllNodesWithText(context.getString(resource)).fetchSemanticsNodes().isNotEmpty()}}
                        ui.onNodeWithText("Cancel queued message").assertIsEnabled()
                        val target=physicalChatAction(scenario,"reopened")
                        val device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        check(device.click(target.centerX(),target.centerY()))
                        captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/group-cancel-viewport-tapped.png"))
                        ui.waitUntil(15_000){displayed(hasSetTextAction() and isEnabled())}
                        ui.onNode(hasSetTextAction() and isEnabled()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(text)))
                        ui.onAllNodes(hasText(text) and !hasSetTextAction()).assertCountEquals(0)
                        val saved=requireNotNull(store.load(key.publicKey))
                        check(saved.draft==text && saved.messages.isEmpty())
                        check(saved.candidate?.envelope()==creation.envelope() && saved.status==nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
                        check(saved.creationKey?.exportJson()==key.exportJson())
                    } else if(removed) {
                        val other=SaitoWallet.fromPrivateKey("0".repeat(63)+"2") // Public unsafe fixture only.
                        val body=com.google.gson.JsonObject().apply {
                            addProperty("module","Chat");addProperty("request","chat remove")
                            addProperty("group_id",key.publicKey);addProperty("member_id",wallet.publicKey)
                        }
                        store.receive(SaitoTransaction.create(other,body,1700000123402,listOf(key.publicKey)).envelope())
                        scenario.recreate();open();waitFor("Queued")
                        check(requireNotNull(store.load(key.publicKey)).group.memberRoles[wallet.publicKey]==ChatGroupRole.REMOVED)
                    } else if(scoped) {
                        wallets.replace(wallet,replacement);scenario.recreate()
                    } else scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                    val indexed=java.util.concurrent.atomic.AtomicReference<String?>()
                    val archiveChecks=java.util.concurrent.atomic.AtomicInteger()
                    val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={criteria->
                        if(archived&&criteria.has("sig")) {
                            check(criteria.get("sig").asString==queued.id&&criteria.get("field3").asString==key.publicKey)
                            archiveChecks.incrementAndGet()
                            indexed.get()?.let{original->com.google.gson.JsonArray().apply{add(com.google.gson.JsonObject().apply{addProperty("tx",original)})}.toString()} ?: "[]"
                        } else "[]"
                    },onTransaction={frame->if(archived)indexed.set(postingTransaction(frame).envelope())})
                    peer.disconnectAfterTransaction=archived
                    peer.start(port).use{
                        if(scoped) {
                            ui.waitUntil(20_000){peer.clientKeys.contains(replacement.publicKey)}
                            android.os.SystemClock.sleep(7_000)
                            check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
                            check(wallets.loadExisting().exportJson()==replacement.exportJson()&&backends.load()==SaitoBackend.OWNER)
                            check(requireNotNull(store.load(key.publicKey)).queuedMessage?.candidate?.envelope()==queued.envelope())
                            check(nl.davidengelen.redsquare.chat.GroupChatStore.hasWork(context,wallet,SaitoBackend.OWNER))
                            captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/group-scope-identity-paused.png"))
                            ui.onNodeWithText("Settings").performClick();waitFor("saito.io")
                            ui.onNodeWithText("saito.io").performScrollTo().performClick()
                            ui.waitUntil(20_000){alternate.clientKeys.contains(replacement.publicKey)}
                            wallets.replace(replacement,wallet);scenario.recreate()
                            ui.waitUntil(20_000){alternate.clientKeys.contains(wallet.publicKey)}
                            android.os.SystemClock.sleep(7_000)
                            check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty()&&alternate.transactions.isEmpty()&&alternate.relayedMessages.isEmpty())
                            check(wallets.loadExisting().exportJson()==wallet.exportJson()&&backends.load()==SaitoBackend.OFFICIAL)
                            check(requireNotNull(store.load(key.publicKey)).queuedMessage?.candidate?.envelope()==queued.envelope())
                            captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/group-scope-backend-paused.png"))
                            ui.onNodeWithText("Settings").performClick();waitFor("saito.davidengelen.nl")
                            ui.onNodeWithText("saito.davidengelen.nl").performScrollTo().performClick();open()
                        }
                        if(cancel) {
                            ui.waitUntil(20_000){peer.requests.get()>0}
                            android.os.SystemClock.sleep(6_000)
                            check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
                        } else if(removed) {
                            ui.waitUntil(20_000){peer.requests.get()>0}
                            android.os.SystemClock.sleep(6_000)
                            check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
                            check(requireNotNull(store.load(key.publicKey)).queuedMessage?.candidate?.envelope()==queued.envelope())
                            ui.onNodeWithText("Cancel queued message").assertIsEnabled().performClick()
                            ui.waitUntil(15_000){ui.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(text))).fetchSemanticsNodes().size==1}
                            ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
                                .assertIsNotEnabled().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(text)))
                            val saved=requireNotNull(store.load(key.publicKey))
                            check(saved.draft==text&&saved.messages.isEmpty()&&saved.creationKey?.exportJson()==key.exportJson())
                            check(saved.candidate?.envelope()==creation.envelope()&&saved.status==nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
                            check(!saved.automaticMessage&&saved.group.memberRoles[wallet.publicKey]==ChatGroupRole.REMOVED)
                            check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
                        } else if(archived) {
                            ui.waitUntil(60_000){peer.transactions.isNotEmpty()&&indexed.get()!=null}
                            check(indexed.get()==queued.envelope())
                            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                            ui.waitUntil(60_000){
                                check(peer.transactions.size==1){"Group archived original was broadcast again"}
                                store.load(key.publicKey)?.status==nl.davidengelen.redsquare.chat.GroupChatStore.Status.AVAILABLE
                            }
                            waitFor("Available on this node. Delivery is not confirmed.")
                            val saved=requireNotNull(store.load(key.publicKey))
                            check(saved.candidate?.envelope()==queued.envelope()&&saved.messages.single().id==queued.id)
                            check(saved.creationKey?.exportJson()==key.exportJson()&&archiveChecks.get()>0&&peer.relayedMessages.isEmpty())
                            scenario.recreate();open();waitFor("Available on this node. Delivery is not confirmed.")
                            check(peer.transactions.size==1&&peer.relayedMessages.isEmpty())
                        } else {
                            ui.waitUntil(60_000){peer.relayedMessages.isNotEmpty()&&store.load(key.publicKey)?.status==nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED}
                            check(postingTransaction(peer.transactions.single()).envelope()==queued.envelope())
                            check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single())==queued.envelope())
                            org.junit.Assert.assertEquals(listOf(wallet.publicKey,member),peer.relayRecipients.single())
                            check(GroupChat.readMessage(GroupChat.readCreation(creation.envelope()),queued.envelope()).text==text)
                            val saved=requireNotNull(store.load(key.publicKey))
                            check(saved.candidate?.envelope()==queued.envelope()&&saved.queuedMessage==null)
                            check(saved.messages.single().id==queued.id&&saved.creationKey?.exportJson()==key.exportJson())
                            check(saved.status==nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
                            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                            waitFor("Forwarded. Delivery is not confirmed.")
                            check(peer.transactions.size==1&&peer.relayedMessages.size==1)
                        }
                        check(wallets.loadExisting().exportJson()==wallet.exportJson()&&backends.load()==SaitoBackend.OWNER)
                        check(alternate.transactions.isEmpty()&&alternate.relayedMessages.isEmpty())
                        peer.assertHealthy();alternate.assertHealthy()
                        captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/group-$label-result.png"))
                    }
                }
            }
            }
        }catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,
            {synchronized(SaitoLocalState.lock){context.noBackupFilesDir.listFiles()!!.filter{file->prefixes.any(file.name::startsWith)}.forEach{check(it.isFile&&it.delete())};retained.forEach{(name,bytes)->File(context.noBackupFilesDir,name).writeBytes(bytes)}}},
            {wallets.replace(wallets.loadExisting(),original);backends.save(backend)},
            {if(cancel){val edit=backupPrefs.edit();if(previousBackupHidden==null)edit.remove(backupKey) else edit.putBoolean(backupKey,previousBackupHidden);check(edit.commit())}},
            {val edit=prefs.edit();if(offered==null)edit.remove("offered") else edit.putBoolean("offered",offered);check(edit.commit())})}
    }

    private fun physicalChatAction(scenario:ActivityScenario<MainActivity>,stage:String,kind:String="group",action:String="Cancel queued message"):android.graphics.Rect {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val device=androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        var top=0;var bottom=0
        scenario.onActivity { activity->
            val bars=androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            top=bars?.top ?: 0;bottom=bars?.bottom ?: 0
        }
        val observed=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(action)),5_000L)
        captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/$kind-cancel-viewport-$stage.png"))
        var target=requireNotNull(observed) {
            "Chat action must have a physical target beneath the offline warnings: $action"
        }
        while(!target.isClickable)target=requireNotNull(target.parent){"Chat action has no clickable ancestor: $action"}
        val bounds=target.visibleBounds
        val minimumHeight=kotlin.math.round(48*context.resources.displayMetrics.density).toInt()
        check(bounds.width()>0&&bounds.height()>=minimumHeight&&bounds.top>=top&&bounds.bottom<=device.displayHeight-bottom) {
            "Full Chat action target must stay inside the usable screen: $bounds, minimum=$minimumHeight, bars=$top/$bottom"
        }
        return bounds
    }

    private fun scrollChatRegionToEnd(label:String,checkpoint:String) {
        val preview=ui.onNodeWithContentDescription(label)
        fun range():Pair<Float,Float> {
            val axis=preview.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            return ui.runOnIdle { axis.value() to axis.maxValue() }
        }
        ui.waitUntil(5_000){val (value,max)=range();value.isFinite()&&max.isFinite()&&max>0f}
        val until=android.os.SystemClock.elapsedRealtime()+8_000
        var position=range()
        val initial=position
        while(position.second-position.first>1f&&android.os.SystemClock.elapsedRealtime()<until) {
            val step=minOf(position.second-position.first,maxOf(1f,preview.fetchSemanticsNode().boundsInRoot.height*0.8f))
            preview.performSemanticsAction(SemanticsActions.ScrollBy){scroll->check(scroll(0f,step))}
            position=range()
        }
        check(position.second-position.first<=1f){"Chat content must scroll to its finite end: $label $position"}
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"chat-queue-evidence/$checkpoint")
            .writeText(com.google.gson.JsonObject().apply {
                addProperty("label",label);addProperty("initial",initial.first);addProperty("initialMax",initial.second)
                addProperty("end",position.first);addProperty("endMax",position.second)
            }.toString())
    }

    private enum class PrivateQueueCase { CANCEL, BACKGROUND, BACKEND, IDENTITY, ARCHIVED, WRONG_ARCHIVE, STORAGE }
    private fun exercisePrivateQueue(case:PrivateQueueCase) {
        val cancel=case==PrivateQueueCase.CANCEL
        val switchBackend=case==PrivateQueueCase.BACKEND
        val switchIdentity=case==PrivateQueueCase.IDENTITY
        val wrongArchive=case==PrivateQueueCase.WRONG_ARCHIVE
        val archived=case==PrivateQueueCase.ARCHIVED||wrongArchive
        val label=case.name.lowercase()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val contact=SaitoWallet.fromPrivateKey("0".repeat(63)+"2").publicKey // Public unsafe fixture only.
        val wallet=SaitoWallet.fromPrivateKey("0".repeat(63)+"1").withChatContact(contact)
        val replacement=SaitoWallet.fromPrivateKey("0".repeat(63)+"3").withChatContact(contact) // Public unsafe fixture only.
        val wallets=AndroidWalletStore(context)
        val original=wallets.loadOrCreate()
        val backends=AndroidBackendStore(context)
        val backend=backends.load()
        val prefixes=listOf(wallet,replacement).flatMap{identity->
            SaitoBackend.entries.flatMap{listOf("chat.${it.id}.${identity.publicKey}.",
                "chatwork.${it.id}.${identity.publicKey}.")}+"personal.${identity.publicKey}."
        }
        val saved=context.noBackupFilesDir.listFiles()!!.filter{file->prefixes.any(file.name::startsWith)}
            .associate{check(it.isFile);it.name to it.readBytes()}
        val prefs=context.getSharedPreferences("notification-permission",0)
        val offered=if(prefs.contains("offered"))prefs.getBoolean("offered",false) else null
        val backupPrefs=context.getSharedPreferences("wallet-backup-reminders",0)
        val backupKey="hidden:${wallet.publicKey}"
        val previousBackupHidden=if(backupPrefs.contains(backupKey))backupPrefs.getBoolean(backupKey,false) else null
        val text=if(cancel) """Keep my offline private message 😀
Line 02 stays in the saved message.
Line 03 stays in the saved message.
Line 04 stays in the saved message.
Line 05 stays in the saved message.
Line 06 stays in the saved message.
Line 07 stays in the saved message.
Line 08 stays in the saved message.
Line 09 stays in the saved message.
Line 10 stays in the saved message.
Line 11 stays in the saved message.
Line 12 stays in the saved message.""" else "Keep my offline private message 😀"
        var primary:Throwable?=null
        try {
            synchronized(SaitoLocalState.lock){saved.keys.forEach{check(File(context.noBackupFilesDir,it).delete())}}
            check(prefs.edit().putBoolean("offered",true).commit())
            if(cancel)check(backupPrefs.edit().putBoolean(backupKey,false).commit())
            wallets.replace(original,wallet);backends.save(SaitoBackend.OWNER)
            val alternate=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={"[]"})
            alternate.start().use{alternateServer->
            // Choose the offline port only after the live alternate is already bound.
            val port=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use{it.localPort}
            check(port!=alternateServer.port){"Offline owner port was reused by the alternate peer"}
            val intent=Intent(context,MainActivity::class.java).putExtra("feedPeerUrl","ws://127.0.0.1:$port/wsopen")
            ActivityScenario.launch<MainActivity>(Intent(intent).putExtra("feedOfficialPeerUrl","ws://127.0.0.1:${alternateServer.port}/wsopen")).use{scenario->
                withFailureCapture({captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-$label-failure.png"))}) {
                    fun open(queued:Boolean=false){
                        val chat=hasText("Chat") and hasClickAction() and isEnabled()
                        ui.waitUntil(20_000){displayed(hasText("Groups"))||displayed(chat)}
                        if(!displayed(hasText("Groups")))ui.onNode(chat).assertIsDisplayed().assertIsEnabled().performClick()
                        waitFor("Groups")
                        clickWhenReady(contact)
                        if(cancel&&queued) {
                            ui.onNodeWithText(contact).assertIsDisplayed()
                            ui.waitUntil(20_000){
                                val retained=nl.davidengelen.redsquare.chat.ChatWorkStore(context,wallet,SaitoBackend.OWNER,contact).load()
                                retained.status==nl.davidengelen.redsquare.chat.ChatWorkStore.Status.QUEUED&&
                                    retained.transaction?.let{PrivateChat.read(wallet,it.envelope(),contact).text==text}==true
                            }
                        } else ui.waitUntil(20_000){displayed(hasText("Message"))||displayed(hasText("Queued"))}
                    }
                    open()
                    ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(text)
                    if(case==PrivateQueueCase.STORAGE) {
                        val work=nl.davidengelen.redsquare.chat.ChatWorkStore(context,wallet,SaitoBackend.OWNER,contact)
                        ui.waitUntil(20_000){try{work.load().draft==text}catch(_:Exception){false}}
                        val file=File(context.noBackupFilesDir,"chatwork.owner.${wallet.publicKey}.${PrivateChat.conversationId(wallet.publicKey,contact)}.v1")
                        val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={"[]"})
                        withQueueStorageFailureOnSend(file) {
                            waitFor("Could not restore saved work. Reopen this conversation to try again.")
                            ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(text)))
                            ui.onAllNodesWithText("Queued").assertCountEquals(0)
                            captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-storage-refused.png"))
                        }
                        peer.start(port).use {
                            scenario.recreate();open()
                            ui.onNode(hasSetTextAction() and isEnabled()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(text)))
                            ui.waitUntil(20_000){peer.requests.get()>0}
                            android.os.SystemClock.sleep(6_000)
                            check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
                            check(work.load().let{it.draft==text&&it.transaction==null&&!it.automatic})
                            check(wallets.loadExisting().exportJson()==wallet.exportJson()&&backends.load()==SaitoBackend.OWNER)
                            peer.assertHealthy();alternate.assertHealthy()
                            captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-storage-restored-draft.png"))
                        }
                        return@withFailureCapture
                    }
                    androidx.test.espresso.Espresso.closeSoftKeyboard()
                    readyAction("Send").performClick()
                    if(cancel) {
                        ui.waitUntil(20_000){
                            val retained=nl.davidengelen.redsquare.chat.ChatWorkStore(context,wallet,SaitoBackend.OWNER,contact).load()
                            retained.status==nl.davidengelen.redsquare.chat.ChatWorkStore.Status.QUEUED&&
                                retained.transaction?.let{PrivateChat.read(wallet,it.envelope(),contact).text==text}==true
                        }
                        ui.waitUntil(30_000){ui.onAllNodesWithText(context.getString(R.string.chat_load_error)).fetchSemanticsNodes().isNotEmpty()}
                        ui.onNodeWithText(context.getString(R.string.wallet_backup_reminder)).assertIsDisplayed()
                        physicalChatAction(scenario,"long-message",kind="private")
                        val queuedWork=nl.davidengelen.redsquare.chat.ChatWorkStore(context,wallet,SaitoBackend.OWNER,contact)
                        val beforeScroll=requireNotNull(queuedWork.load().transaction).envelope()
                        scrollChatRegionToEnd("Queued message preview","private-cancel-preview-scroll.json")
                        physicalChatAction(scenario,"preview-end",kind="private")
                        check(queuedWork.load().transaction?.envelope()==beforeScroll)
                        check(PrivateChat.read(wallet,beforeScroll,contact).text==text)
                    }
                    waitFor("Queued")
                    captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-$label-queued.png"))
                    scenario.recreate();open(queued=cancel)
                    waitFor("Queued")
                    val cancellation=if(cancel)physicalChatAction(scenario,"reopened",kind="private") else null
                    val originalCandidate=requireNotNull(nl.davidengelen.redsquare.chat.ChatWorkStore(context,wallet,SaitoBackend.OWNER,contact).load().transaction)
                    if(cancel) {
                        ui.onNodeWithText("Cancel queued message").assertIsEnabled()
                        val target=requireNotNull(cancellation)
                        val device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        check(device.click(target.centerX(),target.centerY()))
                        captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-cancel-viewport-tapped.png"))
                        ui.waitUntil(15_000){ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
                        ui.onNode(hasSetTextAction() and isEnabled()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(text)))
                        ui.onAllNodes(hasText(text) and !hasSetTextAction()).assertCountEquals(0)
                    } else if(switchIdentity) {
                        check(nl.davidengelen.redsquare.chat.ChatWorkStore.hasWork(context,wallet,SaitoBackend.OWNER))
                        wallets.replace(wallet,replacement)
                        scenario.recreate();open()
                        ui.onAllNodesWithText("Queued").assertCountEquals(0)
                    } else if(switchBackend) {
                        ui.onNodeWithText("Settings").performClick()
                        waitFor("saito.io");ui.onNodeWithText("saito.io").performScrollTo().performClick()
                        ui.waitUntil(20_000){alternate.requests.get()>0}
                    } else scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                    val indexed=java.util.concurrent.atomic.AtomicReference<String?>()
                    val archiveChecks=java.util.concurrent.atomic.AtomicInteger()
                    val wrongResponse=java.util.concurrent.atomic.AtomicBoolean(wrongArchive)
                    val otherOriginal=if(wrongArchive)PrivateChat.create(replacement,contact,"Different archived operation",1700000999999).envelope() else null
                    val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={criteria->
                        if(archived&&criteria.has("sig")) {
                            check(criteria.get("sig").asString==originalCandidate.id)
                            check(criteria.get("field3").asString==PrivateChat.conversationId(wallet.publicKey,contact))
                            archiveChecks.incrementAndGet()
                            (if(wrongResponse.get())otherOriginal else indexed.get())?.let{original->com.google.gson.JsonArray().apply{add(com.google.gson.JsonObject().apply{addProperty("tx",original)})}.toString()} ?: "[]"
                        } else "[]"
                    },onTransaction={frame->
                        if(archived)indexed.set(postingTransaction(frame).envelope())
                    },relayResponse={signed->
                        val receiver=SaitoWallet.fromPrivateKey("0".repeat(63)+"2") // Public unsafe fixture only.
                        check(PrivateChat.read(receiver,signed,wallet.publicKey).text==text)
                        "{\"err\":\"\",\"success\":1}"
                    })
                    peer.disconnectAfterTransaction=archived
                    peer.start(port).use {
                        if(cancel) {
                            ui.waitUntil(20_000){peer.requests.get()>0}
                            android.os.SystemClock.sleep(6_000)
                            check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
                        } else if(archived) {
                            ui.waitUntil(60_000){peer.transactions.isNotEmpty()&&indexed.get()!=null}
                            check(indexed.get()==originalCandidate.envelope())
                            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                            val automaticError="Could not complete this attempt. Your message is saved for automatic retry on its original node."
                            if(wrongArchive) {
                                ui.waitUntil(20_000){archiveChecks.get()>0}
                                waitFor("Submitted. Delivery is not confirmed.")
                                check(peer.transactions.size==1&&peer.relayedMessages.isEmpty())
                                check(nl.davidengelen.redsquare.chat.ChatWorkStore(context,wallet,SaitoBackend.OWNER,contact).load().transaction?.envelope()==originalCandidate.envelope())
                                captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-wrong-archive-refused.png"))
                                ui.onNodeWithText("Retry").assertIsEnabled().performClick()
                                waitFor(automaticError)
                                check(peer.transactions.size==1&&peer.relayedMessages.isEmpty())
                                captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-wrong-archive-retry-error.png"))
                                wrongResponse.set(false)
                            }
                            ui.waitUntil(20_000) {
                                check(peer.transactions.size==1){"Archived original was broadcast again: ${peer.transactions.size} raw operations"}
                                ui.onAllNodesWithText("Available on this node. Delivery is not confirmed.").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()
                            }
                            if(wrongArchive)ui.onAllNodesWithText(automaticError).assertCountEquals(0)
                            check(archiveChecks.get()>0&&peer.relayedMessages.isEmpty())
                            check(postingTransaction(peer.transactions.single()).envelope()==originalCandidate.envelope())
                            scenario.recreate();open()
                            waitFor("Available on this node. Delivery is not confirmed.")
                            check(peer.transactions.size==1&&peer.relayedMessages.isEmpty())
                        } else {
                            if(switchIdentity) {
                                android.os.SystemClock.sleep(7_000)
                                check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
                                check(wallets.loadExisting().exportJson()==replacement.exportJson())
                                check(nl.davidengelen.redsquare.chat.ChatWorkStore(context,wallet,SaitoBackend.OWNER,contact).load().transaction?.envelope()==originalCandidate.envelope())
                                check(nl.davidengelen.redsquare.chat.ChatWorkStore.hasWork(context,wallet,SaitoBackend.OWNER))
                                captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-identity-paused.png"))
                                wallets.replace(replacement,wallet)
                                scenario.recreate();open()
                            }
                            if(switchBackend) {
                                android.os.SystemClock.sleep(7_000)
                                check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty()&&alternate.transactions.isEmpty()&&alternate.relayedMessages.isEmpty())
                                check(backends.load()==SaitoBackend.OFFICIAL)
                                check(nl.davidengelen.redsquare.chat.ChatWorkStore(context,wallet,SaitoBackend.OWNER,contact).load().transaction?.envelope()==originalCandidate.envelope())
                                captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-backend-paused.png"))
                                ui.onNodeWithText("Settings").performClick()
                                waitFor("saito.davidengelen.nl");ui.onNodeWithText("saito.davidengelen.nl").performScrollTo().performClick()
                            }
                            ui.waitUntil(60_000){peer.relayedMessages.isNotEmpty()}
                            check(postingTransaction(peer.transactions.single()).envelope()==originalCandidate.envelope())
                            check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single())==originalCandidate.envelope())
                            if(switchBackend){waitFor(contact);ui.onNodeWithText(contact).performClick()}
                            else if(!switchIdentity)scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                            waitFor("Forwarded. Delivery is not confirmed.")
                            check(peer.transactions.size==1&&peer.relayedMessages.size==1)
                        }
                        check(wallets.loadExisting().exportJson()==wallet.exportJson()&&backends.load()==SaitoBackend.OWNER)
                        check(alternate.transactions.isEmpty()&&alternate.relayedMessages.isEmpty())
                        peer.assertHealthy();alternate.assertHealthy()
                        captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-$label-result.png"))
                    }
                }
            }
            }
        }catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,
            {synchronized(SaitoLocalState.lock){context.noBackupFilesDir.listFiles()!!.filter{file->prefixes.any(file.name::startsWith)}.forEach{check(it.isFile&&it.delete())};saved.forEach{(name,bytes)->File(context.noBackupFilesDir,name).writeBytes(bytes)}}},
            {wallets.replace(wallets.loadExisting(),original);backends.save(backend)},
            {if(cancel){val edit=backupPrefs.edit();if(previousBackupHidden==null)edit.remove(backupKey) else edit.putBoolean(backupKey,previousBackupHidden);check(edit.commit())}},
            {val edit=prefs.edit();if(offered==null)edit.remove("offered") else edit.putBoolean("offered",offered);check(edit.commit())})}
    }
    /** Inject after edit readiness, in the same UI action as Send, before asynchronous persistence. */
    private fun withQueueStorageFailureOnSend(file:File,operation:()->Unit) {
        var encrypted:ByteArray?=null
        var primary:Throwable?=null
        try {
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            readyAction("Send").performSemanticsAction(SemanticsActions.OnClick){click->
                synchronized(SaitoLocalState.lock) {
                    check(!File(file.path+".bak").exists()&&!File(file.path+".new").exists())
                    encrypted=file.readBytes()
                    check(file.delete()&&file.mkdir())
                    check(click())
                }
            }
            operation()
        }catch(failure:Throwable){primary=failure;throw failure}
        finally{if(encrypted!=null)cleanupAfterTest(primary,{synchronized(SaitoLocalState.lock){
            check(file.isDirectory&&file.list()!!.isEmpty()&&file.delete())
            file.writeBytes(requireNotNull(encrypted))
        }})}
    }
    private fun waitFor(text:String)=ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
    private fun readyAction(text:String):SemanticsNodeInteraction {
        val target=hasText(text) and hasClickAction() and isEnabled()
        ui.waitUntil(20_000){displayed(target)}
        return ui.onNode(target).assertIsDisplayed().assertIsEnabled()
    }
    private fun clickWhenReady(text:String) {readyAction(text).performClick()}
    private fun displayed(target:SemanticsMatcher)=try{ui.onNode(target).assertIsDisplayed();true}catch(_:AssertionError){false}
}
