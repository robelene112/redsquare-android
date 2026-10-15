package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
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

    @Test fun anOfflineGroupMessageCanBeCancelledAfterReopeningWithoutEnteringHistoryOrSending() = exerciseGroupQueue(cancel=true)

    @Test fun anOfflineGroupMessageResumesItsOriginalWhileBackgrounded() = exerciseGroupQueue(cancel=false)

    private fun exerciseGroupQueue(cancel:Boolean) {
        val label=if(cancel)"cancel" else "background"
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
        val prefix="groupchat.owner.${wallet.publicKey}."
        val retained=synchronized(SaitoLocalState.lock) {
            context.noBackupFilesDir.listFiles()!!.filter{it.name.startsWith(prefix)}.associate{check(it.isFile);it.name to it.readBytes()}
        }
        val prefs=context.getSharedPreferences("notification-permission",0)
        val offered=if(prefs.contains("offered"))prefs.getBoolean("offered",false) else null
        val port=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use{it.localPort}
        val text="Keep my offline group message 😀"
        var primary:Throwable?=null
        try {
            synchronized(SaitoLocalState.lock){retained.keys.forEach{check(File(context.noBackupFilesDir,it).delete())}}
            check(prefs.edit().putBoolean("offered",true).commit())
            wallets.replace(original,wallet);backends.save(SaitoBackend.OWNER)
            val store=nl.davidengelen.redsquare.chat.GroupChatStore(context,wallet,SaitoBackend.OWNER)
            val creation=GroupChat.create(wallet,key.publicKey,name,listOf(member),1700000123400)
            store.prepareCreation(creation,key)
            store.record(key.publicKey,creation.id,nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
            ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)
                .putExtra("feedPeerUrl","ws://127.0.0.1:$port/wsopen")).use{scenario->
                withFailureCapture({captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/group-$label-failure.png"))}) {
                    fun open(){
                        val chat=hasText("Chat") and hasClickAction() and isEnabled()
                        ui.waitUntil(20_000){displayed(hasText("Groups"))||displayed(chat)}
                        if(!displayed(hasText("Groups")))ui.onNode(chat).performClick()
                        clickWhenReady("Groups")
                        clickWhenReady(name)
                        waitFor("Message")
                    }
                    open()
                    ui.waitUntil(20_000){displayed(hasSetTextAction() and isEnabled())}
                    ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(text)
                    ui.onNodeWithText("Send").assertIsEnabled().performClick()
                    waitFor("Queued")
                    captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/group-$label-queued.png"))
                    scenario.recreate();open();waitFor("Queued")
                    val queued=requireNotNull(requireNotNull(store.load(key.publicKey)).queuedMessage).candidate
                    if(cancel) {
                        ui.onNodeWithText("Cancel queued message").assertIsEnabled().performClick()
                        ui.waitUntil(15_000){displayed(hasSetTextAction() and isEnabled())}
                        ui.onNode(hasSetTextAction() and isEnabled()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(text)))
                        ui.onAllNodes(hasText(text) and !hasSetTextAction()).assertCountEquals(0)
                        val saved=requireNotNull(store.load(key.publicKey))
                        check(saved.draft==text && saved.messages.isEmpty())
                        check(saved.candidate?.envelope()==creation.envelope() && saved.status==nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
                        check(saved.creationKey?.exportJson()==key.exportJson())
                    } else scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                    val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={"[]"})
                    peer.start(port).use{
                        if(cancel) {
                            ui.waitUntil(20_000){peer.requests.get()>0}
                            android.os.SystemClock.sleep(6_000)
                            check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
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
                        peer.assertHealthy()
                        captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/group-$label-result.png"))
                    }
                }
            }
        }catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,
            {synchronized(SaitoLocalState.lock){context.noBackupFilesDir.listFiles()!!.filter{it.name.startsWith(prefix)}.forEach{check(it.isFile&&it.delete())};retained.forEach{(name,bytes)->File(context.noBackupFilesDir,name).writeBytes(bytes)}}},
            {wallets.replace(wallets.loadExisting(),original);backends.save(backend)},
            {val edit=prefs.edit();if(offered==null)edit.remove("offered") else edit.putBoolean("offered",offered);check(edit.commit())})}
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
        val port=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use{it.localPort}
        val intent=Intent(context,MainActivity::class.java).putExtra("feedPeerUrl","ws://127.0.0.1:$port/wsopen")
        val text="Keep my offline private message 😀"
        var primary:Throwable?=null
        try {
            synchronized(SaitoLocalState.lock){saved.keys.forEach{check(File(context.noBackupFilesDir,it).delete())}}
            check(prefs.edit().putBoolean("offered",true).commit())
            wallets.replace(original,wallet);backends.save(SaitoBackend.OWNER)
            val alternate=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={"[]"})
            alternate.start().use{alternateServer->
            ActivityScenario.launch<MainActivity>(Intent(intent).putExtra("feedOfficialPeerUrl","ws://127.0.0.1:${alternateServer.port}/wsopen")).use{scenario->
                withFailureCapture({captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-$label-failure.png"))}) {
                    fun open(){
                        val chat=hasText("Chat") and hasClickAction() and isEnabled()
                        ui.waitUntil(20_000){displayed(hasText("Groups"))||displayed(chat)}
                        if(!displayed(hasText("Groups")))ui.onNode(chat).assertIsDisplayed().assertIsEnabled().performClick()
                        waitFor("Groups")
                        clickWhenReady(contact)
                        waitFor("Message")
                    }
                    open()
                    ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(text)
                    if(case==PrivateQueueCase.STORAGE) {
                        val work=nl.davidengelen.redsquare.chat.ChatWorkStore(context,wallet,SaitoBackend.OWNER,contact)
                        ui.waitUntil(20_000){try{work.load().draft==text}catch(_:Exception){false}}
                        val file=File(context.noBackupFilesDir,"chatwork.owner.${wallet.publicKey}.${PrivateChat.conversationId(wallet.publicKey,contact)}.v1")
                        val encrypted=synchronized(SaitoLocalState.lock) {
                            check(!File(file.path+".bak").exists()&&!File(file.path+".new").exists())
                            file.readBytes().also{check(file.delete()&&file.mkdir())}
                        }
                        val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={"[]"})
                        try {
                            ui.onNodeWithText("Send").performClick()
                            waitFor("Could not restore saved work. Reopen this conversation to try again.")
                            ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(text)))
                            ui.onAllNodesWithText("Queued").assertCountEquals(0)
                            captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-storage-refused.png"))
                        }finally {
                            synchronized(SaitoLocalState.lock) {
                                check(file.isDirectory&&file.list()!!.isEmpty()&&file.delete())
                                file.writeBytes(encrypted)
                            }
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
                    ui.onNodeWithText("Send").performClick()
                    waitFor("Queued")
                    captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-$label-queued.png"))
                    scenario.recreate();open();waitFor("Queued")
                    val originalCandidate=requireNotNull(nl.davidengelen.redsquare.chat.ChatWorkStore(context,wallet,SaitoBackend.OWNER,contact).load().transaction)
                    if(cancel) {
                        ui.onNodeWithText("Cancel queued message").assertIsEnabled().performClick()
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
                        waitFor("saito.io");ui.onNodeWithText("saito.io").performClick()
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
                            val automaticError="Could not complete this attempt. Your message is saved for automatic retry on its original server."
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
                                ui.onAllNodesWithText("Available on this server. Delivery is not confirmed.").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()
                            }
                            if(wrongArchive)ui.onAllNodesWithText(automaticError).assertCountEquals(0)
                            check(archiveChecks.get()>0&&peer.relayedMessages.isEmpty())
                            check(postingTransaction(peer.transactions.single()).envelope()==originalCandidate.envelope())
                            scenario.recreate();open()
                            waitFor("Available on this server. Delivery is not confirmed.")
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
                                waitFor("saito.davidengelen.nl");ui.onNodeWithText("saito.davidengelen.nl").performClick()
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
            {val edit=prefs.edit();if(offered==null)edit.remove("offered") else edit.putBoolean("offered",offered);check(edit.commit())})}
    }
    private fun waitFor(text:String)=ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
    private fun clickWhenReady(text:String) {
        val target=hasText(text) and hasClickAction() and isEnabled()
        ui.waitUntil(20_000){displayed(target)}
        ui.onNode(target).assertIsDisplayed().assertIsEnabled().performClick()
    }
    private fun displayed(target:SemanticsMatcher)=try{ui.onNode(target).assertIsDisplayed();true}catch(_:AssertionError){false}
}
