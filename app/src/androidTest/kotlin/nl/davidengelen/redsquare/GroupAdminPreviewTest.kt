package nl.davidengelen.redsquare

import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.chat.GroupChatStore
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Public unsafe fixture identities, actual native UI and signed controlled transport. */
class GroupAdminPreviewTest {
    @get:Rule val ui=createEmptyComposeRule()

    @Test fun anAdminCanPromoteAMemberWithoutLosingTheDraftAndReopenTheNewRole() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev"&&Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallets=AndroidWalletStore(context)
        val original=wallets.loadOrCreate()
        val backend=AndroidBackendStore(context).load()
        val key=SaitoWallet.fromPrivateKey("0".repeat(63)+"9") // Public unsafe fixtures only.
        val member=SaitoWallet.fromPrivateKey("0".repeat(63)+"2").publicKey
        val fixture=SaitoWallet.fromPrivateKey("0".repeat(63)+"1").withNamedChatGroup(key.publicKey,"Admin beta group",key)
        val creation=GroupChat.create(fixture,key.publicKey,"Admin beta group",listOf(member),1700000123456)
        val filename="groupchat.${backend.id}.${fixture.publicKey}.${key.publicKey}.v1"
        val names=listOf(filename,"$filename.bak","$filename.new")
        synchronized(SaitoLocalState.lock){check(names.none{File(context.noBackupFilesDir,it).exists()})}
        val preferences=listOf("wallet-backup-reminders" to "hidden:${fixture.publicKey}","notification-permission" to "offered").map{(name,keyName)->
            val prefs=context.getSharedPreferences(name,0)
            Triple(prefs,keyName,if(prefs.contains(keyName))prefs.getBoolean(keyName,false)else null)
        }
        val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={"[]"})
        var primary:Throwable?=null
        try {
            wallets.replace(original,fixture)
            preferences.forEach{(prefs,keyName,_)->check(prefs.edit().putBoolean(keyName,true).commit())}
            val store=GroupChatStore(context,fixture,backend)
            store.prepareCreation(creation,key);store.record(key.publicKey,creation.id,GroupChatStore.Status.FORWARDED)
            peer.start().use{server->
                ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)
                    .putExtra("feedPeerUrl","ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl","ws://127.0.0.1:${server.port}/wsopen")).use{scenario->
                    withFailureCapture({captureNativeScreen(File(context.cacheDir,"group-admin-evidence/failure.png"))}) {
                        fun open(fromFeed:Boolean=false) {
                            if(fromFeed){waitForEnabled("Chat");ui.onNodeWithText("Chat").assertIsDisplayed().performClick()}
                            waitFor("Groups");ui.onNodeWithText("Groups").performClick()
                            waitFor("Admin beta group");ui.onNodeWithText("Admin beta group").performClick()
                        }
                        open(fromFeed=true);waitForEnabled("Message")
                        ui.onNodeWithText("Message").performTextInput("Retain this admin draft")
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        ui.onNodeWithText("Members").assertIsDisplayed().performClick()
                        ui.waitUntil(15_000){ui.onAllNodesWithContentDescription("Promote member $member").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
                        ui.onNodeWithContentDescription("Promote member $member").assertIsDisplayed().assertIsEnabled().performClick()
                        waitForEnabled("Confirm promotion");ui.onNodeWithText("Confirm promotion").performClick()
                        ui.waitUntil(15_000){peer.transactions.size==1&&peer.relayedMessages.size==1}
                        ui.waitUntil(15_000){store.load(key.publicKey)?.let{it.status==GroupChatStore.Status.FORWARDED&&it.candidate?.id!=creation.id}==true}
                        waitFor("Forwarded. Delivery is not confirmed.")
                        val saved=requireNotNull(store.load(key.publicKey));val candidate=requireNotNull(saved.candidate)
                        val signed=SaitoEnvelope.decode(candidate.envelope())
                        check(signed.author==fixture.publicKey&&signed.body.get("request").asString=="chat update")
                        check(signed.body.get("member_ids").asJsonObject.get(member).asString=="admin")
                        check(postingTransaction(peer.transactions.single()).envelope()==candidate.envelope())
                        check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single())==candidate.envelope())
                        check(peer.relayRecipients.single()==listOf(member))
                        check(saved.group.memberRoles[member]==ChatGroupRole.ADMIN&&saved.draft=="Retain this admin draft")
                        ui.onNodeWithText("Members").performClick()
                        ui.onNodeWithContentDescription("Promote member $member").assertDoesNotExist()
                        ui.onAllNodesWithText("admin").assertCountEquals(2)
                        captureNativeScreen(File(context.cacheDir,"group-admin-evidence/promoted.png"),member)
                        ui.onNodeWithText("Done").performClick()
                        scenario.recreate();open();waitFor("Retain this admin draft")
                        ui.onNodeWithText("Members").performClick()
                        ui.onAllNodesWithText("admin").assertCountEquals(2)
                        check(store.load(key.publicKey)!!.candidate!!.envelope()==candidate.envelope())
                        check(wallets.loadExisting().exportJson()==fixture.exportJson()&&AndroidBackendStore(context).load()==backend)
                        check(peer.transactions.size==1&&peer.relayedMessages.size==1)
                        captureNativeScreen(File(context.cacheDir,"group-admin-evidence/promoted-reopened.png"),member)
                        peer.assertHealthy()
                    }
                }
            }
        }catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,
            {synchronized(SaitoLocalState.lock){names.forEach{val file=File(context.noBackupFilesDir,it);check(!file.exists()||file.delete())}}},
            {wallets.replace(wallets.loadExisting(),original)},
            *preferences.map{(prefs,keyName,value)->{check(prefs.edit().apply{if(value==null)remove(keyName)else putBoolean(keyName,value)}.commit());Unit}}.toTypedArray())}
    }
    private fun waitFor(text:String)=ui.waitUntil(15_000){ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
    private fun waitForEnabled(text:String)=ui.waitUntil(15_000){ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
}
