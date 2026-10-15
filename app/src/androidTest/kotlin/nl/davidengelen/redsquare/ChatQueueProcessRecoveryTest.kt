package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.chat.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Actual queued process termination and automatic recovery, with device-only encrypted snapshots. */
class ChatQueueProcessRecoveryTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext.also{
        check(it.packageName=="nl.davidengelen.redsquare.dev"&&Build.HARDWARE in listOf("ranchu","goldfish"))
    }
    private val kind get()=InstrumentationRegistry.getArguments().getString("queueKind","private").also{check(it in listOf("private","group"))}
    private val checkpoint get()=File(context.cacheDir,"chat-queue-process-checkpoint.json")
    private val originals get()=File(context.noBackupFilesDir,"chat-queue-process-originals")
    private val other=SaitoWallet.fromPrivateKey("0".repeat(63)+"2") // Public unsafe fixtures only. Never fund.
    private val key=SaitoWallet.fromPrivateKey("0".repeat(63)+"9")
    private val groupName="Queue process recovery"
    private val fixture=SaitoWallet.fromPrivateKey("0".repeat(63)+"1").withChatContact(other.publicKey)
        .withNamedChatGroup(key.publicKey,groupName,key)
    private val cachedText="Cached before queued process death"
    private val text="Automatically resume this exact original 😀"

    @Test fun terminateAfterExplicitOfflineSendWithItsOriginalStillQueued() {
        check(!checkpoint.exists()&&!originals.exists())
        val captures=File(context.cacheDir,"chat-queue-process-evidence")
        check(!captures.exists()||captures.deleteRecursively())
        val wallets=AndroidWalletStore(context)
        val original=wallets.loadOrCreate()
        val backend=AndroidBackendStore(context).load()
        val row=JsonObject().apply {
            addProperty("kind",kind);addProperty("phase","prepared");addProperty("owner",fixture.publicKey)
            addProperty("backend",backend.id);addProperty("pid",Process.myPid())
            addProperty("walletDigest",digest(fixture.exportJson()));addProperty("originalWalletDigest",digest(original.exportJson()))
        }
        row.add("savedFiles",snapshotProcessFixtureFiles(context,originals,fileNames(backend)))
        row.add("preferences",JsonArray().apply{preferenceKeys().forEach{(name,keyName)->
            val prefs=context.getSharedPreferences(name,0)
            add(JsonObject().apply{addProperty("name",name);addProperty("key",keyName)
                if(prefs.contains(keyName))addProperty("value",prefs.getBoolean(keyName,false))})
        }})
        writeCheckpoint(row)
        synchronized(SaitoLocalState.lock){fileNames(backend).filterNot{it.startsWith("wallet.")}.forEach{name->
            val file=File(context.noBackupFilesDir,name);check(!file.exists()||file.delete())
        }}
        wallets.replace(original,fixture)
        preferenceKeys().forEach{(name,keyName)->check(context.getSharedPreferences(name,0).edit().putBoolean(keyName,true).commit())}
        val incoming=if(kind=="private")PrivateChat.create(other,fixture.publicKey,cachedText,1700000123401) else {
            val store=GroupChatStore(context,fixture,backend)
            val creation=GroupChat.create(fixture,key.publicKey,groupName,listOf(other.publicKey),1700000123400)
            store.prepareCreation(creation,key);store.record(key.publicKey,creation.id,GroupChatStore.Status.FORWARDED)
            row.addProperty("originDigest",digest(creation.envelope()));row.addProperty("keyDigest",digest(key.exportJson()))
            GroupChat.message(other,GroupChat.readCreation(creation.envelope()),cachedText,1700000123401)
        }
        if(kind=="private")ChatHistoryStore(context,fixture,backend,other.publicKey).save(listOf(incoming.envelope()))
        else GroupChatStore(context,fixture,backend).receive(incoming.envelope())
        row.addProperty("historyDigest",digest(incoming.envelope()))
        val port=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use{it.localPort}
        launch(port){
            open();ui.waitUntil(20_000){displayed(hasSetTextAction() and isEnabled())}
            ui.onNode(hasSetTextAction() and isEnabled()).performTextReplacement(text)
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            click("Send");waitFor("Queued")
            val candidate=queued(backend)
            row.addProperty("candidate",candidate.id);row.addProperty("candidateDigest",digest(candidate.envelope()))
            assertPending(row,backend)
            capture("queued-before-death.png")
            row.addProperty("phase","pending");writeCheckpoint(row)
            Process.killProcess(Process.myPid());error("Expected actual process termination")
        }
    }

    @Test fun coldRestartAutomaticallyForwardsTheSameOriginalAndKeepsHistory() {
        val row=readCheckpoint();assertEquals("pending",row.get("phase").asString)
        assertEquals(kind,row.get("kind").asString);assertNotEquals(row.get("pid").asInt,Process.myPid())
        val backend=SaitoBackend.entries.single{it.id==row.get("backend").asString}
        assertPending(row,backend)
        val candidate=queued(backend)
        val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={"[]"})
        peer.start().use{server->launch(server.port){
            open()
            ui.waitUntil(60_000){peer.relayedMessages.isNotEmpty()&&completed(backend,candidate.id)}
            waitFor("Forwarded. Delivery is not confirmed.")
            assertEquals(candidate.envelope(),postingTransaction(peer.transactions.single()).envelope())
            assertEquals(candidate.envelope(),SaitoEnvelope.fromSignedJson(peer.relayedMessages.single()))
            assertEquals(listOf(fixture.publicKey,other.publicKey),peer.relayRecipients.single())
            val received=if(kind=="private")PrivateChat.read(other,candidate.envelope(),fixture.publicKey).text
                else GroupChat.readMessage(requireNotNull(GroupChatStore(context,fixture,backend).load(key.publicKey)).group,candidate.envelope()).text
            assertEquals(text,received)
            assertHistory(row,backend,completed=true,candidate=candidate)
            assertSelection(row,backend)
            assertEquals(1,peer.transactions.size);assertEquals(1,peer.relayedMessages.size)
            peer.assertHealthy();capture("queued-original-automatically-forwarded.png")
            row.addProperty("phase","recovered");row.addProperty("recoveredPid",Process.myPid());writeCheckpoint(row)
        }}
    }

    @Test fun restoreOriginalEncryptedInstallationFiles() {
        if(!checkpoint.exists())return
        val row=readCheckpoint();assertEquals(kind,row.get("kind").asString)
        val backend=SaitoBackend.entries.single{it.id==row.get("backend").asString}
        restoreProcessFixtureFiles(context,originals,fileNames(backend),row.getAsJsonArray("savedFiles"))
        row.getAsJsonArray("preferences").forEach{value->val pref=value.asJsonObject
            val pair=pref.get("name").asString to pref.get("key").asString;check(pair in preferenceKeys())
            check(context.getSharedPreferences(pair.first,0).edit().apply{
                if(pref.has("value"))putBoolean(pair.second,pref.get("value").asBoolean)else remove(pair.second)
            }.commit())
        }
        assertEquals(row.get("originalWalletDigest").asString,digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(backend,AndroidBackendStore(context).load())
        check(originals.deleteRecursively());check(checkpoint.delete())
    }

    private fun queued(backend:SaitoBackend):SaitoTransaction = if(kind=="private") {
        val saved=ChatWorkStore(context,fixture,backend,other.publicKey).load()
        assertEquals(ChatWorkStore.Status.QUEUED,saved.status);assertEquals("",saved.draft)
        requireNotNull(saved.transaction)
    } else {
        val saved=requireNotNull(GroupChatStore(context,fixture,backend).load(key.publicKey))
        assertEquals("",saved.draft);requireNotNull(saved.queuedMessage).candidate
    }
    private fun completed(backend:SaitoBackend,id:String):Boolean = if(kind=="private")
        ChatWorkStore(context,fixture,backend,other.publicKey).load().let{it.transaction?.id==id&&it.status==ChatWorkStore.Status.FORWARDED}
        else GroupChatStore(context,fixture,backend).load(key.publicKey)?.let{it.candidate?.id==id&&it.queuedMessage==null&&it.status==GroupChatStore.Status.FORWARDED}==true
    private fun assertPending(row:JsonObject,backend:SaitoBackend) {
        assertSelection(row,backend)
        val candidate=queued(backend)
        assertEquals(row.get("candidate").asString,candidate.id)
        assertEquals(row.get("candidateDigest").asString,digest(candidate.envelope()))
        assertHistory(row,backend)
    }
    private fun assertSelection(row:JsonObject,backend:SaitoBackend) {
        assertEquals(row.get("walletDigest").asString,digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(backend,AndroidBackendStore(context).load())
    }
    private fun assertHistory(row:JsonObject,backend:SaitoBackend,completed:Boolean=false,candidate:SaitoTransaction?=null) {
        val envelopes=if(kind=="private")ChatHistoryStore(context,fixture,backend,other.publicKey).load().entries.map{it.envelope}
            else requireNotNull(GroupChatStore(context,fixture,backend).load(key.publicKey)).also{
                assertEquals(row.get("keyDigest").asString,digest(requireNotNull(it.creationKey).exportJson()))
                assertEquals(row.get("originDigest").asString,digest(requireNotNull(it.group.creationEnvelope)))
            }.originals
        val expected=listOf(row.get("historyDigest").asString)+(if(completed)listOf(digest(requireNotNull(candidate).envelope()))else emptyList())
        assertEquals(expected.sorted(),envelopes.map(::digest).sorted())
    }
    private fun fileNames(backend:SaitoBackend):Set<String> {
        val conversation=PrivateChat.conversationId(fixture.publicKey,other.publicKey)
        return listOf("wallet.v1","wallet.recovery.v1","chat.${backend.id}.${fixture.publicKey}.$conversation.v1",
            "chatwork.${backend.id}.${fixture.publicKey}.$conversation.v1","groupchat.${backend.id}.${fixture.publicKey}.${key.publicKey}.v1")
            .flatMap{listOf(it,"$it.bak","$it.new")}.toSet()
    }
    private fun preferenceKeys()=listOf("notification-permission" to "offered","wallet-backup-reminders" to "hidden:${fixture.publicKey}")
    private fun readCheckpoint()=JsonParser.parseString(checkpoint.readText()).asJsonObject
    private fun writeCheckpoint(row:JsonObject)=checkpoint.outputStream().use{it.write(row.toString().toByteArray());it.fd.sync()}
    private fun digest(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun launch(port:Int,journey:()->Unit)=ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)
        .putExtra("feedPeerUrl","ws://127.0.0.1:$port/wsopen").putExtra("feedOfficialPeerUrl","ws://127.0.0.1:$port/wsopen")).use{
        withFailureCapture({capture("failure.png")},journey)
    }
    private fun open() {
        val chat=hasText("Chat") and hasClickAction() and isEnabled()
        ui.waitUntil(20_000){displayed(hasText("Groups"))||displayed(chat)}
        if(!displayed(hasText("Groups")))ui.onNode(chat).performClick()
        if(kind=="group"){click("Groups");click(groupName)}else click(other.publicKey)
        waitFor("Message")
    }
    private fun click(text:String) {val target=hasText(text) and hasClickAction() and isEnabled();ui.waitUntil(20_000){displayed(target)};ui.onNode(target).performClick()}
    private fun waitFor(text:String)=ui.waitUntil(20_000){ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
    private fun displayed(target:SemanticsMatcher)=try{ui.onNode(target).assertIsDisplayed();true}catch(_:AssertionError){false}
    private fun capture(name:String)=captureNativeScreen(File(context.cacheDir,"chat-queue-process-evidence/$name"))
}
