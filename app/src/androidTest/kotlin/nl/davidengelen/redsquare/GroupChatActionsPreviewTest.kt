package nl.davidengelen.redsquare

import android.content.ClipboardManager
import android.content.Context
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

/** Native message actions through actual signed transport; public unsafe fixtures only. */
class GroupChatActionsPreviewTest {
    @get:Rule val ui=createEmptyComposeRule()

    @Test fun groupReplyUsesCompatibleQuoteAndCopyRetainsOnlyLiteralBodyAcrossReopening() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev"&&Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallets=AndroidWalletStore(context)
        val original=wallets.loadOrCreate()
        val backend=AndroidBackendStore(context).load()
        val admin=SaitoWallet.fromPrivateKey("0".repeat(63)+"1")
        val key=SaitoWallet.fromPrivateKey("0".repeat(63)+"9")
        val fixture=SaitoWallet.fromPrivateKey("0".repeat(63)+"4").withNamedChatGroup(key.publicKey,"Reply beta group")
        val creation=GroupChat.create(admin,key.publicKey,"Reply beta group",listOf(fixture.publicKey),1700000123456)
        val source=GroupChat.message(admin,GroupChat.readCreation(creation.envelope()),"Original group message",1700000123457)
        val prefix="<blockquote href=\"${admin.publicKey}1700000123457\">Original group message</blockquote>"
        val body="Native reply 😀 <literal>&"
        val filename="groupchat.${backend.id}.${fixture.publicKey}.${key.publicKey}.v1"
        val names=listOf(filename,"$filename.bak","$filename.new")
        synchronized(SaitoLocalState.lock){check(names.none{File(context.noBackupFilesDir,it).exists()})}
        val preferences=listOf("wallet-backup-reminders" to "hidden:${fixture.publicKey}","notification-permission" to "offered").map{(name,keyName)->
            val prefs=context.getSharedPreferences(name,0)
            Triple(prefs,keyName,if(prefs.contains(keyName))prefs.getBoolean(keyName,false)else null)
        }
        val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var previousClip:android.content.ClipData?=null
        var clipboardChanged=false
        val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={criteria->
            if(criteria.has("field3"))check(criteria.get("field3").asString==key.publicKey)
            "[]"
        })
        var primary:Throwable?=null
        try {
            wallets.replace(original,fixture)
            preferences.forEach{(prefs,keyName,_)->check(prefs.edit().putBoolean(keyName,true).commit())}
            val store=GroupChatStore(context,fixture,backend)
            store.receive(creation.envelope());store.receive(source.envelope())
            peer.start().use{server->
                ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)
                    .putExtra("feedPeerUrl","ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl","ws://127.0.0.1:${server.port}/wsopen")).use{scenario->
                    withFailureCapture({captureNativeScreen(File(context.cacheDir,"group-chat-actions-evidence/failure.png"))}) {
                        fun open(fromFeed:Boolean=false) {
                            if(fromFeed){waitForEnabled("Chat");ui.onNodeWithText("Chat").assertIsDisplayed().assertIsEnabled().performClick()}
                            waitFor("Groups");ui.onNodeWithText("Groups").performClick()
                            waitFor("Reply beta group");ui.onNodeWithText("Reply beta group").performClick()
                        }
                        open(fromFeed=true);waitFor("Original group message")
                        previousClip=clipboard.primaryClip
                        waitForEnabled("Copy")
                        clipboardChanged=true
                        ui.onNodeWithText("Copy").assertIsDisplayed().performClick()
                        check(clipboard.primaryClip!!.getItemAt(0).text.toString()=="Original group message")
                        check(clipboard.primaryClipDescription!!.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
                        waitForEnabled("Reply")
                        ui.onNodeWithText("Reply").assertIsDisplayed().performClick()
                        waitFor("Remove reply")
                        ui.onNodeWithText("Message").performTextInput(body)
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        waitForEnabled("Send");ui.onNodeWithText("Send").assertIsEnabled().performClick()
                        waitFor("Forwarded. Delivery is not confirmed.")
                        ui.waitUntil(15_000){peer.transactions.size==1&&peer.relayedMessages.size==1}
                        val saved=requireNotNull(store.load(key.publicKey))
                        val candidate=requireNotNull(saved.candidate)
                        val signed=SaitoEnvelope.decode(candidate.envelope())
                        check(signed.author==fixture.publicKey&&signed.body.get("message").asString==prefix+body)
                        check(signed.body.get("mentioned").asJsonArray.map{it.asString}==listOf(admin.publicKey))
                        check(signed.recipients==listOf(fixture.publicKey,key.publicKey))
                        check(postingTransaction(peer.transactions.single()).envelope()==candidate.envelope())
                        check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single())==candidate.envelope())
                        check(peer.relayRecipients.single()==listOf(fixture.publicKey,admin.publicKey))
                        ui.onNodeWithText("Remove reply").assertDoesNotExist()
                        scenario.recreate();open();waitFor(body)
                        ui.onNodeWithContentDescription("Group messages").performScrollToNode(hasText(body))
                        ui.onNodeWithText(body).assertIsDisplayed()
                        ui.onAllNodesWithText("Copy").onLast().assertIsDisplayed().performClick()
                        check(clipboard.primaryClip!!.getItemAt(0).text.toString()==body)
                        check(clipboard.primaryClipDescription!!.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
                        val reopened=requireNotNull(store.load(key.publicKey))
                        check(reopened.messages.map{it.text}==listOf("Original group message",prefix+body))
                        check(reopened.candidate!!.envelope()==candidate.envelope()&&reopened.draft.isEmpty())
                        check(wallets.loadExisting().exportJson()==fixture.exportJson()&&AndroidBackendStore(context).load()==backend)
                        check(peer.transactions.size==1&&peer.relayedMessages.size==1)
                        captureNativeScreen(File(context.cacheDir,"group-chat-actions-evidence/reply-cached-copy.png"),body)
                        peer.assertHealthy()
                    }
                }
            }
        }catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,
            {synchronized(SaitoLocalState.lock){names.forEach{val file=File(context.noBackupFilesDir,it);check(!file.exists()||file.delete())}}},
            {wallets.replace(wallets.loadExisting(),original)},
            {if(clipboardChanged){previousClip?.let{clipboard.setPrimaryClip(it)} ?: clipboard.clearPrimaryClip()}},
            *preferences.map{(prefs,keyName,value)->{check(prefs.edit().apply{if(value==null)remove(keyName)else putBoolean(keyName,value)}.commit());Unit}}.toTypedArray())}
    }
    private fun waitFor(text:String)=ui.waitUntil(15_000){ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
    private fun waitForEnabled(text:String)=ui.waitUntil(15_000){ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
}
