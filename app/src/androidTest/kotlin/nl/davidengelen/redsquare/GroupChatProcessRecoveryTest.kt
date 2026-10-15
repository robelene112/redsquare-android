package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Device-only encrypted snapshots, two actual processes and public synthetic observations. */
class GroupChatProcessRecoveryTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext.also{
        check(it.packageName=="nl.davidengelen.redsquare.dev"&&Build.HARDWARE in listOf("ranchu","goldfish"))
    }
    private val action get()=InstrumentationRegistry.getArguments().getString("groupAction","reaction").also {
        check(it in listOf("reaction","promotion","removal"))
    }
    private val checkpoint get()=File(context.cacheDir,"group-chat-process-checkpoint.json")
    private val originals get()=File(context.noBackupFilesDir,"group-chat-process-originals")
    private val key=SaitoWallet.fromPrivateKey("0".repeat(63)+"3") // Public unsafe fixtures only.
    private val member=SaitoWallet.fromPrivateKey("0".repeat(63)+"2").publicKey
    private val fixture=SaitoWallet.fromPrivateKey("0".repeat(63)+"1")
        .withNamedChatGroup(key.publicKey,"Native group 😀 <&>",key)
    private val draft="Unsent group draft 😀 <&>\nsecond line"
    private fun reference(name:String)=InstrumentationRegistry.getInstrumentation().context.assets
        .open("native-group-chat-$name-reference.json").bufferedReader().use{
            JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject
        }

    @Test fun terminateWithGroupKeyHistoryDraftAndUncertainOriginal() {
        check(!checkpoint.exists()&&!originals.exists())
        val captures=File(context.cacheDir,"group-chat-process-evidence")
        check(!captures.exists()||captures.deleteRecursively()) // Only synthetic captures, never protected records.
        val wallets=AndroidWalletStore(context)
        val original=wallets.loadOrCreate()
        val backend=AndroidBackendStore(context).load()
        val row=JsonObject().apply{
            addProperty("action",action);addProperty("retryVerified",false)
            addProperty("phase","prepared");addProperty("owner",fixture.publicKey);addProperty("backend",backend.id)
            addProperty("originalWalletDigest",digest(original.exportJson()));addProperty("walletDigest",digest(fixture.exportJson()))
            addProperty("pid",Process.myPid())
        }
        row.add("savedFiles",snapshotProcessFixtureFiles(context,originals,fileNames(backend)))
        row.add("preferences",JsonArray().apply{preferenceKeys().forEach{(name,keyName)->
            val prefs=context.getSharedPreferences(name,0)
            add(JsonObject().apply{addProperty("name",name);addProperty("key",keyName)
                if(prefs.contains(keyName))addProperty("value",prefs.getBoolean(keyName,false))})
        }})
        writeCheckpoint(row)
        synchronized(SaitoLocalState.lock){processFixtureFiles(context,fileNames(backend)).filterNot{it.startsWith("wallet.")}.forEach{name->
            val file=File(context.noBackupFilesDir,name);check(!file.exists()||file.delete())
        }}
        wallets.replace(original,fixture)
        preferenceKeys().forEach{(name,keyName)->check(context.getSharedPreferences(name,0).edit().putBoolean(keyName,true).commit())}
        val creation=reference("creation").get("envelope").asString
        val message=reference("message").get("envelope").asString
        val group=GroupChat.readCreation(creation)
        check(group.id==key.publicKey&&group.memberRoles[fixture.publicKey]==ChatGroupRole.ADMIN)
        val store=GroupChatStore(context,fixture,backend)
        val created=GroupChat.create(fixture,key.publicKey,group.name,listOf(member),1700000123456)
        check(created.envelope()==creation)
        store.prepareCreation(created,key)
        store.record(key.publicKey,created.id,GroupChatStore.Status.FORWARDED) // Synthetic committed precondition, no delivery claim.
        store.receive(message)
        val body=JsonObject().apply{
            addProperty("module","Chat");addProperty("request","chat remove")
            addProperty("group_id",key.publicKey);addProperty("member_id",member)
        }
        val removal=SaitoTransaction.create(fixture,body,1700000123457,listOf(key.publicKey))
        if(action=="reaction")store.receive(removal.envelope())
        row.addProperty("originDigest",digest(creation));row.addProperty("messageDigest",digest(message))
        row.addProperty("controlDigest",digest(removal.envelope()));row.addProperty("keyDigest",digest(key.exportJson()))
        val peer=peer()
        peer.start().use{server->launch(server.port){
            open();waitFor(context.getString(R.string.group_history_error));waitFor("Hello group 😀 <&>!")
            ui.waitUntil(15_000){ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().size==1}
            ui.onNodeWithText("Message").performTextReplacement(draft)
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            ui.waitUntil(15_000){runCatching{ui.onNodeWithText("Back").assertIsDisplayed()}.isSuccess}
            ui.onNodeWithText("Back").performClick();waitFor("Create group")
            val current=requireNotNull(store.load(key.publicKey))
            val candidate=when(action) {
                "promotion" -> GroupChat.promote(fixture,current.group,member,1700000123458)
                    .also{store.preparePromotion(it,member)}
                "removal" -> GroupChat.removeMember(fixture,current.group,member,1700000123458)
                    .also{store.prepareRemoval(it,member)}
                else -> GroupChat.like(fixture,current.group,current.messages.single(),1700000123458)
                    .also{store.prepareReaction(it)}
            }
            row.addProperty("candidate",candidate.id);row.addProperty("candidateDigest",digest(candidate.envelope()))
            ui.onNodeWithText("Refresh").assertIsEnabled().performClick()
            waitForEnabled("Create group")
            ui.onNodeWithText("Native group 😀 <&>").performClick()
            assertUi();assertState(row,backend,peer)
            capture("pending-before-death.png",draft)
            row.addProperty("phase","pending");writeCheckpoint(row)
            Process.killProcess(Process.myPid());error("Expected actual process termination")
        }}
    }

    @Test fun coldRestartRetainsGroupStateWithoutSending() {
        val row=readCheckpoint();assertEquals("pending",row.get("phase").asString)
        assertNotEquals(row.get("pid").asInt,Process.myPid())
        val backend=SaitoBackend.entries.single{it.id==row.get("backend").asString}
        val peer=peer()
        peer.start().use{server->launch(server.port){
            open();waitFor(context.getString(R.string.group_history_error));assertUi()
            assertState(row,backend,peer);capture("pending-cold-recovered.png",draft)
            if(action!="reaction") {
                ui.onNodeWithText("Retry").performClick()
                val store=GroupChatStore(context,fixture,backend)
                ui.waitUntil(15_000){store.load(key.publicKey)?.status==GroupChatStore.Status.FORWARDED}
                waitFor(context.getString(R.string.group_forwarded))
                assertState(row,backend,peer,forwarded=true)
                capture("explicit-retry-forwarded.png",draft)
                row.addProperty("retryVerified",true)
            }
            row.addProperty("phase","recovered");row.addProperty("recoveredPid",Process.myPid());writeCheckpoint(row)
        }}
    }

    @Test fun restoreOriginalEncryptedInstallationFiles() {
        if(!checkpoint.exists())return
        val row=readCheckpoint();val backend=SaitoBackend.entries.single{it.id==row.get("backend").asString}
        restoreProcessFixtureFiles(context,originals,fileNames(backend),row.getAsJsonArray("savedFiles"))
        row.getAsJsonArray("preferences").forEach{value->val pref=value.asJsonObject
            val name=pref.get("name").asString;val keyName=pref.get("key").asString;check(name to keyName in preferenceKeys())
            check(context.getSharedPreferences(name,0).edit().apply{if(pref.has("value"))putBoolean(keyName,pref.get("value").asBoolean)else remove(keyName)}.commit())
        }
        assertEquals(row.get("originalWalletDigest").asString,digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(backend,AndroidBackendStore(context).load())
        check(originals.deleteRecursively());check(checkpoint.delete())
    }
    private fun assertState(row:JsonObject,backend:SaitoBackend,peer:ControlledFeedPeer,forwarded:Boolean=false) {
        assertEquals(row.get("walletDigest").asString,digest(AndroidWalletStore(context).loadExisting().exportJson()))
        assertEquals(backend,AndroidBackendStore(context).load())
        val entry=requireNotNull(GroupChatStore(context,fixture,backend).load(key.publicKey))
        val candidate=requireNotNull(entry.candidate)
        assertEquals(if(forwarded)GroupChatStore.Status.FORWARDED else GroupChatStore.Status.UNKNOWN,entry.status)
        assertEquals(row.get("candidate").asString,candidate.id)
        assertEquals(row.get("candidateDigest").asString,digest(candidate.envelope()))
        assertEquals(row.get("keyDigest").asString,digest(entry.creationKey!!.exportJson()))
        assertEquals(row.get("originDigest").asString,digest(entry.group.creationEnvelope!!))
        assertEquals(if(action=="promotion")ChatGroupRole.ADMIN else ChatGroupRole.REMOVED,entry.group.memberRoles[member])
        assertEquals(ChatGroupRole.ADMIN,entry.group.memberRoles[fixture.publicKey])
        val control=if(action=="reaction")row.get("controlDigest").asString else row.get("candidateDigest").asString
        assertEquals(listOf(control),entry.group.controls.map(::digest))
        val expectedOriginals=if(action=="reaction")listOf(row.get("messageDigest").asString,control,row.get("candidateDigest").asString)
            else listOf(row.get("messageDigest").asString,control)
        assertEquals(expectedOriginals,entry.originals.map(::digest))
        assertEquals(listOf("Hello group 😀 <&>!"),entry.messages.map{it.text});assertEquals(draft,entry.draft)
        assertEquals(if(action=="reaction")listOf(row.get("candidate").asString) else emptyList<String>(),entry.reactions.map{it.id})
        assertTrue(peer.clientKeys.isNotEmpty()&&peer.clientKeys.all{it==fixture.publicKey})
        if(forwarded) {
            assertEquals(1,peer.transactions.size);assertEquals(1,peer.relayedMessages.size)
            assertEquals(candidate.envelope(),postingTransaction(peer.transactions.single()).envelope())
            assertEquals(candidate.envelope(),SaitoEnvelope.fromSignedJson(peer.relayedMessages.single()))
            assertEquals(listOf(fixture.publicKey,member),peer.relayRecipients.single())
        } else {
            assertTrue(peer.transactions.isEmpty());assertTrue(peer.relayedMessages.isEmpty())
        }
        peer.assertHealthy()
    }
    private fun assertUi() {
        waitFor(context.getString(R.string.group_unknown));ui.onNodeWithText(context.getString(R.string.group_unknown)).assertIsDisplayed()
        waitForEnabled("Retry");ui.onNodeWithText("Retry").assertIsDisplayed().assertIsEnabled()
        ui.waitUntil(15_000){runCatching{ui.onNodeWithText("Message").fetchSemanticsNode().config[SemanticsProperties.EditableText].text==draft}.getOrDefault(false)}
        ui.onNodeWithText("Message").assertIsDisplayed().assertIsNotEnabled().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(draft)))
        waitFor("Hello group 😀 <&>!")
        ui.onNodeWithContentDescription("Group messages").performScrollToNode(hasText("Hello group 😀 <&>!"))
        ui.onNodeWithText("Hello group 😀 <&>!").assertIsDisplayed()
        ui.onNodeWithText("Send").assertIsNotEnabled()
        ui.onNodeWithText(if(action=="reaction")"Unlike" else "Like").assertIsNotEnabled()
        if(action=="reaction"){
            waitFor("1 like")
            ui.onNodeWithContentDescription("Group messages").performScrollToNode(hasText("1 like"))
            ui.onNodeWithText("1 like").assertIsDisplayed()
        }
    }
    private fun peer()=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={"{invalid"})
    private fun fileNames(backend:SaitoBackend)=listOf("wallet.v1","wallet.recovery.v1","groupchat.${backend.id}.${fixture.publicKey}.${key.publicKey}.v1")
        .flatMap{listOf(it,"$it.bak","$it.new")}.toSet()
    private fun preferenceKeys()=listOf("notification-permission" to "offered","wallet-backup-reminders" to "hidden:${fixture.publicKey}")
    private fun digest(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun readCheckpoint()=JsonParser.parseString(checkpoint.readText()).asJsonObject
    private fun writeCheckpoint(row:JsonObject)=checkpoint.outputStream().use{it.write(row.toString().toByteArray());it.fd.sync()}
    private fun launch(port:Int,journey:()->Unit)=ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)
        .putExtra("feedPeerUrl","ws://127.0.0.1:$port/wsopen").putExtra("feedOfficialPeerUrl","ws://127.0.0.1:$port/wsopen")).use{
        withFailureCapture({capture("failure.png")},journey)
    }
    private fun open(){waitForEnabled("Chat");ui.onNodeWithText("Chat").assertIsDisplayed().assertIsEnabled().performClick();waitFor("Groups");ui.onNodeWithText("Groups").performClick()
        waitFor("Native group 😀 <&>");ui.onNodeWithText("Native group 😀 <&>").performClick()}
    private fun capture(name:String,text:String?=null)=captureNativeScreen(File(context.cacheDir,"group-chat-process-evidence/$name"),text)
    private fun waitFor(text:String)=ui.waitUntil(15_000){ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
    private fun waitForEnabled(text:String)=ui.waitUntil(15_000){ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
}
