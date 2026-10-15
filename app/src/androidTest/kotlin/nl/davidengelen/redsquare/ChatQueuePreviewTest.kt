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

    private enum class PrivateQueueCase { CANCEL, BACKGROUND, BACKEND, IDENTITY, ARCHIVED }
    private fun exercisePrivateQueue(case:PrivateQueueCase) {
        val cancel=case==PrivateQueueCase.CANCEL
        val switchBackend=case==PrivateQueueCase.BACKEND
        val switchIdentity=case==PrivateQueueCase.IDENTITY
        val archived=case==PrivateQueueCase.ARCHIVED
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
                        waitFor("Chat");ui.onNodeWithText("Chat").performClick()
                        waitFor(contact);ui.onNodeWithText(contact).performClick()
                        waitFor("Message")
                    }
                    open()
                    ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(text)
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
                    val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={criteria->
                        if(archived&&criteria.has("sig")) {
                            check(criteria.get("sig").asString==originalCandidate.id)
                            check(criteria.get("field3").asString==PrivateChat.conversationId(wallet.publicKey,contact))
                            archiveChecks.incrementAndGet()
                            indexed.get()?.let{original->com.google.gson.JsonArray().apply{add(com.google.gson.JsonObject().apply{addProperty("tx",original)})}.toString()} ?: "[]"
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
                            ui.waitUntil(60_000){peer.transactions.isNotEmpty()}
                            check(indexed.get()==originalCandidate.envelope())
                            peer.disconnectAfterTransaction=false
                            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                            ui.waitUntil(20_000) {
                                check(peer.transactions.size==1){"Archived original was broadcast again: ${peer.transactions.size} raw operations"}
                                ui.onAllNodesWithText("Available on this server. Delivery is not confirmed.").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()
                            }
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
}
