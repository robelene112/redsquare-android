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

    @Test fun anAdminCanRemoveAMemberAndRetainTheDraftAndRemovalAfterReopening() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev"&&Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallets=AndroidWalletStore(context)
        val original=wallets.loadOrCreate()
        val backend=AndroidBackendStore(context).load()
        val key=SaitoWallet.fromPrivateKey("0".repeat(63)+"6") // Public unsafe fixtures only.
        val member=SaitoWallet.fromPrivateKey("0".repeat(63)+"2").publicKey
        val fixture=SaitoWallet.fromPrivateKey("0".repeat(63)+"1").withNamedChatGroup(key.publicKey,"Removal beta group",key)
        val creation=GroupChat.create(fixture,key.publicKey,"Removal beta group",listOf(member),1700000123456)
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
                    withFailureCapture({captureNativeScreen(File(context.cacheDir,"group-admin-evidence/removal-failure.png"))}) {
                        fun open(fromFeed:Boolean=false) {
                            if(fromFeed){waitForEnabled("Chat");ui.onNodeWithText("Chat").performClick()}
                            waitFor("Groups");ui.onNodeWithText("Groups").performClick()
                            waitFor("Removal beta group");ui.onNodeWithText("Removal beta group").performClick()
                        }
                        open(fromFeed=true);waitForEnabled("Message")
                        ui.onNodeWithText("Message").performTextInput("Retain this removal draft")
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        ui.onNodeWithText("Members").performClick()
                        ui.waitUntil(15_000){ui.onAllNodesWithContentDescription("Remove member $member").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
                        ui.onNodeWithContentDescription("Remove member $member").assertIsDisplayed().assertIsEnabled().performClick()
                        waitForEnabled("Confirm removal");ui.onNodeWithText("Confirm removal").performClick()
                        ui.waitUntil(15_000){peer.transactions.size==1&&peer.relayedMessages.size==1}
                        ui.waitUntil(15_000){store.load(key.publicKey)?.let{it.status==GroupChatStore.Status.FORWARDED&&it.candidate?.id!=creation.id}==true}
                        waitFor("Forwarded. Delivery is not confirmed.")
                        val saved=requireNotNull(store.load(key.publicKey));val candidate=requireNotNull(saved.candidate)
                        val signed=SaitoEnvelope.decode(candidate.envelope())
                        check(signed.author==fixture.publicKey&&signed.body.get("request").asString=="chat remove")
                        check(signed.body.get("member_id").asString==member&&signed.body.get("group_id").asString==key.publicKey)
                        check(postingTransaction(peer.transactions.single()).envelope()==candidate.envelope())
                        check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single())==candidate.envelope())
                        check(peer.relayRecipients.single()==listOf(fixture.publicKey,member))
                        check(saved.group.memberRoles[member]==ChatGroupRole.REMOVED&&saved.draft=="Retain this removal draft")
                        waitForEnabled("Members");ui.onNodeWithText("Members").performClick()
                        ui.onNodeWithText("removed").assertIsDisplayed()
                        ui.onNodeWithContentDescription("Remove member $member").assertDoesNotExist()
                        captureNativeScreen(File(context.cacheDir,"group-admin-evidence/removed.png"),member)
                        ui.onNodeWithText("Done").performClick()
                        scenario.recreate();open();waitFor("Retain this removal draft")
                        ui.onNodeWithText("Members").performClick()
                        ui.onNodeWithText("removed").assertIsDisplayed()
                        check(store.load(key.publicKey)!!.candidate!!.envelope()==candidate.envelope())
                        check(wallets.loadExisting().exportJson()==fixture.exportJson()&&AndroidBackendStore(context).load()==backend)
                        check(peer.transactions.size==1&&peer.relayedMessages.size==1)
                        captureNativeScreen(File(context.cacheDir,"group-admin-evidence/removed-reopened.png"),member)
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

    @Test fun aPromotionStopsAtRelayWhenARenameArrivesAfterRawSubmission() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev"&&Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallets=AndroidWalletStore(context)
        val original=wallets.loadOrCreate()
        val backend=AndroidBackendStore(context).load()
        val fixture=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixtures only.
        val member=SaitoWallet.fromPrivateKey("0".repeat(63)+"2").publicKey
        val key=SaitoWallet.fromPrivateKey("0".repeat(63)+"7")
        val filename="groupchat.${backend.id}.${fixture.publicKey}.${key.publicKey}.v1"
        val names=listOf(filename,"$filename.bak","$filename.new")
        synchronized(SaitoLocalState.lock){check(names.none{File(context.noBackupFilesDir,it).exists()})}
        var primary:Throwable?=null
        try {
            wallets.replace(original,fixture)
            val store=GroupChatStore(context,fixture,backend)
            val creation=GroupChat.create(fixture,key.publicKey,"Before rename",listOf(member),1700000123456)
            store.prepareCreation(creation,key);store.record(key.publicKey,creation.id,GroupChatStore.Status.FORWARDED)
            store.saveDraft(key.publicKey,"Keep this rename draft")
            val candidate=GroupChat.promote(fixture,requireNotNull(store.load(key.publicKey)).group,member,1700000123457)
            store.preparePromotion(candidate,member)
            val renameBody=SaitoEnvelope.decode(candidate.envelope()).body.deepCopy().apply{addProperty("group_name","New authenticated name")}
            val rename=SaitoTransaction.create(fixture,renameBody,1700000123458,listOf(key.publicKey))
            val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||")
            peer.start().use{server->
                fun attempt()=kotlinx.coroutines.runBlocking {
                    nl.davidengelen.redsquare.chat.submitChatTransaction(fixture,"ws://127.0.0.1:${server.port}/wsopen",fixture.publicKey,candidate,
                        nl.davidengelen.redsquare.chat.ChatSubmissionCallbacks(
                            claim={store.claim(key.publicKey,candidate.id)},assertSelected={store.assertSelected()},
                            submitted={store.record(key.publicKey,candidate.id,GroupChatStore.Status.SUBMITTED);store.receive(rename.envelope())},
                            forwarded={store.record(key.publicKey,candidate.id,GroupChatStore.Status.FORWARDED)},
                            relayRecipients={store.relayRecipients(key.publicKey,candidate.id)}))
                }
                org.junit.Assert.assertThrows(IllegalStateException::class.java){attempt()}
                ui.waitUntil(15_000){peer.transactions.size==1}
                check(postingTransaction(peer.transactions.single()).envelope()==candidate.envelope()&&peer.relayedMessages.isEmpty())
                val saved=requireNotNull(store.load(key.publicKey))
                check(saved.candidate!!.envelope()==candidate.envelope()&&saved.status==GroupChatStore.Status.SUBMITTED)
                check(saved.group.name=="New authenticated name"&&saved.draft=="Keep this rename draft")
                check(saved.creationKey!!.exportJson()==key.exportJson())
                store.prepareRetry(key.publicKey,candidate.id)
                org.junit.Assert.assertThrows(IllegalStateException::class.java){attempt()}
                check(peer.transactions.size==1&&peer.relayedMessages.isEmpty())
                check(wallets.loadExisting().exportJson()==fixture.exportJson()&&AndroidBackendStore(context).load()==backend)
                peer.assertHealthy()
            }
        }catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,
            {synchronized(SaitoLocalState.lock){names.forEach{val file=File(context.noBackupFilesDir,it);check(!file.exists()||file.delete())}}},
            {wallets.replace(wallets.loadExisting(),original)})}
    }

    @Test fun aPreparedPromotionCannotTransmitOrRetryAfterTheTargetLeaves() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev"&&Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallets=AndroidWalletStore(context)
        val original=wallets.loadOrCreate()
        val backend=AndroidBackendStore(context).load()
        val fixture=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixtures only.
        val member=SaitoWallet.fromPrivateKey("0".repeat(63)+"2")
        val key=SaitoWallet.fromPrivateKey("0".repeat(63)+"8")
        val filename="groupchat.${backend.id}.${fixture.publicKey}.${key.publicKey}.v1"
        val names=listOf(filename,"$filename.bak","$filename.new")
        synchronized(SaitoLocalState.lock){check(names.none{File(context.noBackupFilesDir,it).exists()})}
        var primary:Throwable?=null
        try {
            wallets.replace(original,fixture)
            val store=GroupChatStore(context,fixture,backend)
            val creation=GroupChat.create(fixture,key.publicKey,"Stale promotion",listOf(member.publicKey),1700000123456)
            store.prepareCreation(creation,key);store.record(key.publicKey,creation.id,GroupChatStore.Status.FORWARDED)
            store.saveDraft(key.publicKey,"Keep the stale-action draft")
            val candidate=GroupChat.promote(fixture,requireNotNull(store.load(key.publicKey)).group,member.publicKey,1700000123457)
            store.preparePromotion(candidate,member.publicKey)
            store.receive(GroupChat.leave(member,key.publicKey,1700000123458).envelope())
            val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||")
            peer.start().use{server->
                fun attempt()=kotlinx.coroutines.runBlocking {
                    nl.davidengelen.redsquare.chat.submitChatTransaction(fixture,"ws://127.0.0.1:${server.port}/wsopen",fixture.publicKey,candidate,
                        nl.davidengelen.redsquare.chat.ChatSubmissionCallbacks(
                            claim={store.claim(key.publicKey,candidate.id)},assertSelected={store.assertSelected()},
                            submitted={store.record(key.publicKey,candidate.id,GroupChatStore.Status.SUBMITTED)},
                            forwarded={store.record(key.publicKey,candidate.id,GroupChatStore.Status.FORWARDED)},
                            relayRecipients={store.relayRecipients(key.publicKey,candidate.id)}))
                }
                org.junit.Assert.assertThrows(IllegalStateException::class.java){attempt()}
                check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
                val saved=requireNotNull(store.load(key.publicKey))
                check(saved.candidate!!.envelope()==candidate.envelope()&&saved.status==GroupChatStore.Status.UNKNOWN)
                check(saved.group.memberRoles[member.publicKey]==ChatGroupRole.LEFT&&saved.draft=="Keep the stale-action draft")
                check(saved.creationKey!!.exportJson()==key.exportJson())
                store.prepareRetry(key.publicKey,candidate.id)
                org.junit.Assert.assertThrows(IllegalStateException::class.java){attempt()}
                check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
                check(store.load(key.publicKey)!!.candidate!!.envelope()==candidate.envelope())
                check(wallets.loadExisting().exportJson()==fixture.exportJson()&&AndroidBackendStore(context).load()==backend)
                peer.assertHealthy()
            }
        }catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,
            {synchronized(SaitoLocalState.lock){names.forEach{val file=File(context.noBackupFilesDir,it);check(!file.exists()||file.delete())}}},
            {wallets.replace(wallets.loadExisting(),original)})}
    }

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
                        check(peer.relayRecipients.single()==listOf(fixture.publicKey,member))
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
