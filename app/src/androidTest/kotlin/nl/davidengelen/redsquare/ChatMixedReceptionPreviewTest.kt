package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.chat.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/** Foreground mode does not choose which authenticated Chat originals may be retained. */
class ChatMixedReceptionPreviewTest {
    @get:Rule val ui=createEmptyComposeRule()
    @Test fun privateMessagesAndReactionsReachGroupsAndGroupsReachPrivateChatWithoutSending() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev"&&Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallets=AndroidWalletStore(context);val original=wallets.loadOrCreate()
        val backend=AndroidBackendStore(context).load()
        val admin=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixtures only.
        val firstKey=SaitoWallet.fromPrivateKey("0".repeat(63)+"9")
        val secondKey=SaitoWallet.fromPrivateKey("0".repeat(63)+"8")
        val fixture=SaitoWallet.fromPrivateKey("0".repeat(63)+"4")
            .withNamedChatGroup(firstKey.publicKey,"Foreground group").withChatContact(admin.publicKey)
        val first=GroupChat.create(admin,firstKey.publicKey,"Foreground group",listOf(fixture.publicKey),1700000123456)
        val second=GroupChat.create(admin,secondKey.publicKey,"Private-screen invited group",listOf(fixture.publicKey),1700000123457)
        val groupMessage=GroupChat.message(admin,GroupChat.readCreation(second.envelope()),"Group received in private Chat",1700000123458)
        val privateMessage=PrivateChat.create(admin,fixture.publicKey,"Private message received in Groups",1700000123459)
        val privateTarget=PrivateChat.recover(fixture,privateMessage.envelope()).message
        val reaction=PrivateChatLike.create(admin,fixture.publicKey,privateTarget,1700000123460)
        val conversation=PrivateChat.conversationId(fixture.publicKey,admin.publicKey)
        val bases=listOf("groupchat.${backend.id}.${fixture.publicKey}.${firstKey.publicKey}.v1",
            "groupchat.${backend.id}.${fixture.publicKey}.${secondKey.publicKey}.v1",
            "chat.${backend.id}.${fixture.publicKey}.$conversation.v1",
            "chatwork.${backend.id}.${fixture.publicKey}.$conversation.v1",
            "chatreactions.${backend.id}.${fixture.publicKey}.$conversation.v1",
            "chatpush.${fixture.publicKey}.${backend.id}.ledger.v1",
            "groupchatpush.${fixture.publicKey}.${backend.id}.ledger.v1")
        val names=bases.flatMap{listOf(it,"$it.bak","$it.new")}
        synchronized(SaitoLocalState.lock){check(processFixtureFiles(context,names.toSet()).none{File(context.noBackupFilesDir,it).exists()})}
        val preferences=listOf("wallet-backup-reminders" to "hidden:${fixture.publicKey}","notification-permission" to "offered").map{(name,key)->
            val prefs=context.getSharedPreferences(name,0);Triple(prefs,key,if(prefs.contains(key))prefs.getBoolean(key,false)else null)
        }
        val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={"[]"})
        var primary:Throwable?=null
        try {
            wallets.replace(original,fixture)
            preferences.forEach{(prefs,key,_)->check(prefs.edit().putBoolean(key,true).commit())}
            GroupChatStore(context,fixture,backend).receive(first.envelope())
            peer.start().use{server->ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)
                .putExtra("feedPeerUrl","ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("feedOfficialPeerUrl","ws://127.0.0.1:${server.port}/wsopen")).use{scenario->
                withFailureCapture({captureNativeScreen(File(context.cacheDir,"chat-mixed-evidence/failure.png"))}) {
                    waitEnabled("Chat");ui.onNodeWithText("Chat").assertIsDisplayed().assertIsEnabled().performClick()
                    wait("Groups");ui.onNodeWithText("Groups").performClick()
                    wait("Foreground group");ui.onNodeWithText("Foreground group").performClick()
                    wait("Received a signed group invitation.")
                    var requestId=900
                    fun deliver(transaction:SaitoTransaction,acknowledgement:Byte=13) {
                        val id=requestId++
                        val bytes=transaction.applicationRequest(1).let{it.copyOfRange(5,it.size)}
                        var sent=0L
                        ui.waitUntil(15_000){
                            if(System.nanoTime()-sent>500_000_000){peer.sendIncoming(bytes,id);sent=System.nanoTime()}
                            peer.incomingAcknowledgements.any{it[0]==acknowledgement&&ByteBuffer.wrap(it,1,4).int==id}
                        }
                    }
                    deliver(privateMessage);deliver(reaction)
                    val privateHistory=ChatHistoryStore(context,fixture,backend,admin.publicKey).load().entries
                    check(privateHistory.single().envelope==privateMessage.envelope())
                    check(ChatReactionStore(context,fixture,backend,admin.publicKey).load().likedBy(privateTarget)==setOf(admin.publicKey))
                    check(GroupChatStore(context,fixture,backend).load(firstKey.publicKey)!!.messages.isEmpty())
                    ui.onNodeWithText("Private message received in Groups").assertDoesNotExist()
                    captureNativeScreen(File(context.cacheDir,"chat-mixed-evidence/private-retained-in-groups.png"),"Received a signed group invitation.")
                    val oldConnections=peer.authenticatedConnectionIds
                    ui.onNodeWithText("Back").performClick();wait("Create group")
                    ui.onNodeWithText("Back").performClick();wait("Public key")
                    ui.waitUntil(15_000){val current=peer.authenticatedConnectionIds
                        current.intersect(oldConnections).isEmpty()&&current.any{it !in oldConnections}}
                    val unfinished=File(context.noBackupFilesDir,"${bases[1]}.new")
                    val faultBytes=byteArrayOf(1,2,3)
                    synchronized(SaitoLocalState.lock){check(!unfinished.exists());unfinished.writeBytes(faultBytes)}
                    val successful=peer.incomingAcknowledgements.count{it[0]==13.toByte()}
                    deliver(second,14)
                    val error="Could not save received group information on this device. Existing records are kept. Open Groups and use Refresh to check them."
                    wait(error);ui.onNodeWithText(error).assertIsDisplayed()
                    synchronized(SaitoLocalState.lock){
                        check(unfinished.readBytes().contentEquals(faultBytes))
                        check(!File(context.noBackupFilesDir,bases[1]).exists()&&!File(context.noBackupFilesDir,"${bases[1]}.bak").exists())
                    }
                    check(peer.incomingAcknowledgements.count{it[0]==13.toByte()}==successful)
                    captureNativeScreen(File(context.cacheDir,"chat-mixed-evidence/group-storage-unavailable-in-private.png"),error)
                    synchronized(SaitoLocalState.lock){check(unfinished.delete())}
                    deliver(second);deliver(groupMessage)
                    val saved=GroupChatStore(context,wallets.loadExisting(),backend).load(secondKey.publicKey)!!
                    check(saved.group.creationEnvelope==second.envelope()&&saved.candidate==null)
                    check(saved.group.memberRoles[fixture.publicKey]==ChatGroupRole.MEMBER)
                    check(saved.messages.single().id==groupMessage.id)
                    waitEnabled(admin.publicKey);ui.onNodeWithText(admin.publicKey).assertIsDisplayed().performClick()
                    wait("Private message received in Groups");ui.onNodeWithText("Private message received in Groups").assertIsDisplayed()
                    wait("1 like");ui.onNodeWithText("1 like").assertIsDisplayed()
                    captureNativeScreen(File(context.cacheDir,"chat-mixed-evidence/private-visible.png"),"Private message received in Groups")
                    ui.onNodeWithText("Back").performClick();wait("Groups");ui.onNodeWithText("Groups").performClick()
                    wait("Private-screen invited group");ui.onNodeWithText("Private-screen invited group").performClick()
                    wait("Group received in private Chat");ui.onNodeWithText("Group received in private Chat").assertIsDisplayed()
                    captureNativeScreen(File(context.cacheDir,"chat-mixed-evidence/group-visible.png"),"Group received in private Chat")
                    check(wallets.loadExisting().publicKey==fixture.publicKey&&AndroidBackendStore(context).load()==backend)
                    check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty());peer.assertHealthy()
                }
            }}
        }catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,
            {synchronized(SaitoLocalState.lock){processFixtureFiles(context,names.toSet()).forEach{val file=File(context.noBackupFilesDir,it);check(!file.exists()||file.delete())}}},
            {wallets.replace(wallets.loadExisting(),original)},
            *preferences.map{(prefs,key,value)->{check(prefs.edit().apply{if(value==null)remove(key)else putBoolean(key,value)}.commit());Unit}}.toTypedArray())}
    }
    private fun wait(text:String)=ui.waitUntil(15_000){ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
    private fun waitEnabled(text:String)=ui.waitUntil(15_000){ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
}
