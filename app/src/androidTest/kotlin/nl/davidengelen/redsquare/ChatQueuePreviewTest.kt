package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.*
import java.io.File
import org.junit.Rule
import org.junit.Test

class ChatQueuePreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun anOfflinePrivateMessageRemainsCancellableAfterReopening() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val contact=SaitoWallet.fromPrivateKey("0".repeat(63)+"2").publicKey // Public unsafe fixture only.
        val wallet=SaitoWallet.fromPrivateKey("0".repeat(63)+"1").withChatContact(contact)
        val wallets=AndroidWalletStore(context)
        val original=wallets.loadOrCreate()
        val backends=AndroidBackendStore(context)
        val backend=backends.load()
        val prefixes=SaitoBackend.entries.flatMap{listOf("chat.${it.id}.${wallet.publicKey}.",
            "chatwork.${it.id}.${wallet.publicKey}.","chatqueue.${it.id}.${wallet.publicKey}.")}
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
            ActivityScenario.launch<MainActivity>(intent).use{scenario->
                withFailureCapture({captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-cancel-failure.png"))}) {
                    fun open(){
                        waitFor("Chat");ui.onNodeWithText("Chat").performClick()
                        waitFor(contact);ui.onNodeWithText(contact).performClick()
                        waitFor("Message")
                    }
                    open()
                    ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(text)
                    ui.onNodeWithText("Send").performClick()
                    waitFor("Queued")
                    captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-queued.png"))
                    scenario.recreate();open();waitFor("Queued")
                    ui.onNodeWithText("Cancel queued message").assertIsEnabled().performClick()
                    ui.waitUntil(15_000){ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
                    ui.onNode(hasSetTextAction() and isEnabled()).assertTextEquals(text)
                    val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={"[]"})
                    peer.start(port).use {
                        ui.waitUntil(20_000){peer.requests.get()>0}
                        android.os.SystemClock.sleep(6_000)
                        check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
                        check(wallets.loadExisting().exportJson()==wallet.exportJson()&&backends.load()==SaitoBackend.OWNER)
                        peer.assertHealthy()
                        captureNativeScreen(File(context.cacheDir,"chat-queue-evidence/private-cancelled-draft.png"))
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
