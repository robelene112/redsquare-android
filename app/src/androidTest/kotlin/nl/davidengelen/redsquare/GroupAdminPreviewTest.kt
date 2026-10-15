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

    @Test fun anAdminCanRemoveAMemberAndRetainTheDraftAndRemovalAfterReopening() =
        withAdminUi("6","Removal beta group","removal-failure.png") { test ->
            val context=test.context;val wallets=test.wallets;val backend=test.backend
            val fixture=test.wallet;val key=test.key;val member=test.memberWallet.publicKey
            val creation=test.creation;val store=test.store;val peer=test.peer;val scenario=test.scenario
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

    @Test fun aPromotionConfirmationUpdatesWhenItsTargetLeaves() =
        withAdminUi("5","Stale confirmation","stale-confirmation-failure.png") { test ->
            val context=test.context;val wallets=test.wallets;val backend=test.backend
            val fixture=test.wallet;val key=test.key;val member=test.memberWallet
            val creation=test.creation;val store=test.store;val peer=test.peer;val scenario=test.scenario
            waitForEnabled("Chat");ui.onNodeWithText("Chat").performClick()
            waitFor("Groups");ui.onNodeWithText("Groups").performClick()
            waitFor("Stale confirmation");ui.onNodeWithText("Stale confirmation").performClick()
            waitForEnabled("Message");ui.onNodeWithText("Message").performTextInput("Keep this confirmation draft")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            ui.waitUntil(15_000){store.load(key.publicKey)?.draft=="Keep this confirmation draft"}
            ui.onNodeWithText("Members").performClick()
            ui.onNodeWithContentDescription("Promote member ${member.publicKey}").performClick()
            waitForEnabled("Confirm promotion")
            val leave=GroupChat.leave(member,key.publicKey,1700000123457)
            ui.waitUntil(15_000){peer.authenticatedConnectionIds.isNotEmpty()}
            peer.sendIncoming(leave.applicationRequest(1).let{it.copyOfRange(5,it.size)},779)
            ui.waitUntil(15_000){store.load(key.publicKey)?.group?.memberRoles?.get(member.publicKey)==ChatGroupRole.LEFT}
            waitFor("Group membership changed. Close this dialog and review the current members.")
            ui.onNodeWithText("Confirm promotion").assertIsDisplayed().assertIsNotEnabled()
            ui.onNodeWithText("Cancel").performClick()
            waitFor("Keep this confirmation draft")
            ui.onNodeWithText("Members").performClick();waitFor("left")
            ui.onNodeWithContentDescription("Promote member ${member.publicKey}").assertDoesNotExist()
            val saved=requireNotNull(store.load(key.publicKey))
            check(saved.candidate!!.envelope()==creation.envelope()&&saved.status==GroupChatStore.Status.FORWARDED)
            check(saved.draft=="Keep this confirmation draft"&&saved.creationKey!!.exportJson()==key.exportJson())
            check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
            check(wallets.loadExisting().exportJson()==fixture.exportJson()&&AndroidBackendStore(context).load()==backend)
            captureNativeScreen(File(context.cacheDir,"group-admin-evidence/stale-confirmation.png"),member.publicKey)
            peer.assertHealthy()

        }

    @Test fun stoppingAStalePromotionRetainsItsOriginalAndAllowsNewMessages() =
        withAdminUi("f","Stopped stale promotion","stopped-stale-failure.png") { test ->
            val store=test.store
            store.saveDraft(test.key.publicKey,"Keep this stopped-action draft")
            val stale=GroupChat.promote(test.wallet,requireNotNull(store.load(test.key.publicKey)).group,test.memberWallet.publicKey,1700000123457)
            store.preparePromotion(stale,test.memberWallet.publicKey)
            store.receive(GroupChat.leave(test.memberWallet,test.key.publicKey,1700000123458).envelope())
            waitForEnabled("Chat");ui.onNodeWithText("Chat").performClick()
            waitFor("Groups");ui.onNodeWithText("Groups").performClick()
            waitFor("Stopped stale promotion");ui.onNodeWithText("Stopped stale promotion").performClick()
            waitForEnabled("Members")
            revealGroupStatus("This saved group action no longer matches the current membership or group details.")
            waitFor("This saved group action no longer matches the current membership or group details.")
            ui.onNodeWithText("This saved group action no longer matches the current membership or group details.").assertIsDisplayed()
            revealGroupStatus("Stop trying this action")
            waitForEnabled("Stop trying this action");ui.onNodeWithText("Stop trying this action").performClick()
            waitFor("Earlier delivery may still have happened. This only stops further attempts on this device and keeps the original record.")
            waitForEnabled("Stop on this device");ui.onNodeWithText("Stop on this device").performClick()
            waitFor("Stopped on this device. Earlier delivery is uncertain.")
            waitForEnabled("Message")
            check(test.peer.transactions.isEmpty()&&test.peer.relayedMessages.isEmpty())
            val stopped=requireNotNull(store.load(test.key.publicKey))
            check(stopped.candidate!!.envelope()==stale.envelope()&&stopped.draft=="Keep this stopped-action draft")
            check(stopped.creationKey!!.exportJson()==test.key.exportJson())
            check(stopped.status==GroupChatStore.Status.STOPPED)
            check(stopped.stoppedActions.single().candidate.envelope()==stale.envelope()&&
                stopped.stoppedActions.single().previousStatus==GroupChatStore.Status.UNKNOWN)
            ui.onNodeWithText("Retry").assertDoesNotExist()
            captureNativeScreen(File(test.context.cacheDir,"group-admin-evidence/stopped-stale.png"))
            test.scenario.recreate()
            waitFor("Groups");ui.onNodeWithText("Groups").performClick()
            waitFor("Stopped stale promotion");ui.onNodeWithText("Stopped stale promotion").performClick()
            waitForEnabled("Message")
            check(store.load(test.key.publicKey)!!.candidate!!.envelope()==stale.envelope())
            check(test.peer.transactions.isEmpty()&&test.peer.relayedMessages.isEmpty())
            ui.onNodeWithText("Message").performTextReplacement("New work after stopping stale action")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            waitForEnabled("Send");ui.onNodeWithText("Send").performClick()
            ui.waitUntil(15_000){store.load(test.key.publicKey)?.status==GroupChatStore.Status.FORWARDED}
            ui.waitUntil(15_000){test.peer.transactions.size==1&&test.peer.relayedMessages.size==1}
            val saved=requireNotNull(store.load(test.key.publicKey));val candidate=requireNotNull(saved.candidate)
            check(candidate.id!=stale.id&&SaitoEnvelope.decode(candidate.envelope()).body.get("request").asString=="chat message")
            check(saved.originals.contains(stale.envelope()))
            check(saved.stoppedActions.single().candidate.envelope()==stale.envelope()&&
                saved.stoppedActions.single().previousStatus==GroupChatStore.Status.UNKNOWN)
            waitFor("Stopped group actions remain saved here. Their earlier delivery is uncertain.")
            check(postingTransaction(test.peer.transactions.single()).envelope()==candidate.envelope())
            check(SaitoEnvelope.fromSignedJson(test.peer.relayedMessages.single())==candidate.envelope())
            check(test.wallets.loadExisting().exportJson()==test.wallet.exportJson()&&AndroidBackendStore(test.context).load()==test.backend)
            captureNativeScreen(File(test.context.cacheDir,"group-admin-evidence/stopped-stale-new-message.png"))
            test.peer.assertHealthy()
        }

    @Test fun aFailedSubmittedStopIsVisibleAndPreservesUncertaintyThroughNewWork() =
        withAdminUi("4","Submitted stopped action","submitted-stop-failure.png") { test ->
            val store=test.store;val key=test.key.publicKey
            store.saveDraft(key,"Keep this submitted-action draft")
            val candidate=GroupChat.promote(test.wallet,requireNotNull(store.load(key)).group,test.memberWallet.publicKey,1700000123457)
            store.preparePromotion(candidate,test.memberWallet.publicKey)
            val departure=GroupChat.leave(test.memberWallet,key,1700000123458)
            org.junit.Assert.assertThrows(IllegalStateException::class.java){
                submitPreparedAdminAction(test.wallet,test.endpoint,store,key,candidate){store.receive(departure.envelope())}
            }
            ui.waitUntil(15_000){test.peer.transactions.size==1}
            check(postingTransaction(test.peer.transactions.single()).envelope()==candidate.envelope()&&test.peer.relayedMessages.isEmpty())
            waitForEnabled("Chat");ui.onNodeWithText("Chat").performClick()
            waitFor("Groups");ui.onNodeWithText("Groups").performClick()
            waitFor("Submitted stopped action");ui.onNodeWithText("Submitted stopped action").performClick()
            waitForEnabled("Members")
            revealGroupStatus("Stop trying this action")
            waitForEnabled("Stop trying this action");ui.onNodeWithText("Stop trying this action").performClick()
            waitForEnabled("Stop on this device")
            // A real protected selection change between preview and confirmation must refuse saving.
            val altered=test.wallet.withNamedChatGroup(key,"Changed fixture metadata",test.key)
            try {
                test.wallets.replace(test.wallet,altered)
                ui.onNodeWithText("Stop on this device").performClick()
                val error=hasText(test.context.getString(R.string.group_error)) and hasAnyAncestor(isDialog())
                ui.waitUntil(15_000){ui.onAllNodes(error).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
                ui.onNode(error).assertIsDisplayed()
                val pending=requireNotNull(store.load(key))
                check(pending.candidate!!.envelope()==candidate.envelope()&&pending.status==GroupChatStore.Status.SUBMITTED)
                check(pending.stoppedActions.isEmpty()&&pending.draft=="Keep this submitted-action draft")
                check(test.peer.transactions.size==1&&test.peer.relayedMessages.isEmpty())
                captureNativeScreen(File(test.context.cacheDir,"group-admin-evidence/submitted-stop-refused.png"))
            } finally { test.wallets.replace(test.wallets.loadExisting(),test.wallet) }
            ui.onNodeWithText("Stop on this device").performClick()
            waitFor("Stopped on this device. Earlier delivery is uncertain.");waitForEnabled("Message")
            val stopped=requireNotNull(store.load(key))
            check(stopped.status==GroupChatStore.Status.STOPPED&&stopped.candidate!!.envelope()==candidate.envelope())
            check(stopped.stoppedActions.single().candidate.envelope()==candidate.envelope()&&
                stopped.stoppedActions.single().previousStatus==GroupChatStore.Status.SUBMITTED)
            check(stopped.creationKey!!.exportJson()==test.key.exportJson())
            check(test.peer.transactions.size==1&&test.peer.relayedMessages.isEmpty())
            test.scenario.recreate()
            waitFor("Groups");ui.onNodeWithText("Groups").performClick()
            waitFor("Submitted stopped action");ui.onNodeWithText("Submitted stopped action").performClick()
            waitForEnabled("Message")
            check(store.load(key)!!.stoppedActions.single().previousStatus==GroupChatStore.Status.SUBMITTED)
            check(test.peer.transactions.size==1&&test.peer.relayedMessages.isEmpty())
            ui.onNodeWithText("Message").performTextReplacement("New work after submitted stop")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            waitForEnabled("Send");ui.onNodeWithText("Send").performClick()
            ui.waitUntil(15_000){store.load(key)?.status==GroupChatStore.Status.FORWARDED}
            ui.waitUntil(15_000){test.peer.transactions.size==2&&test.peer.relayedMessages.size==1}
            val saved=requireNotNull(store.load(key));val newCandidate=requireNotNull(saved.candidate)
            check(newCandidate.id!=candidate.id&&saved.originals.contains(candidate.envelope()))
            check(saved.stoppedActions.single().candidate.envelope()==candidate.envelope()&&
                saved.stoppedActions.single().previousStatus==GroupChatStore.Status.SUBMITTED)
            check(postingTransaction(test.peer.transactions.last()).envelope()==newCandidate.envelope())
            check(SaitoEnvelope.fromSignedJson(test.peer.relayedMessages.single())==newCandidate.envelope())
            check(test.wallets.loadExisting().exportJson()==test.wallet.exportJson()&&AndroidBackendStore(test.context).load()==test.backend)
            captureNativeScreen(File(test.context.cacheDir,"group-admin-evidence/submitted-stop-new-message.png"))
            test.peer.assertHealthy()
        }

    @Test fun aRejectedPromotionRelayShowsUncertaintyAndRetriesTheSameOriginal() {
        val reject=java.util.concurrent.atomic.AtomicBoolean(true)
        withAdminUi("e","Rejected promotion","rejected-relay-failure.png",relayResponse={
            if(reject.get())"{\"err\":\"fixture refusal\",\"success\":0}" else "{\"err\":\"\",\"success\":1}"
        }) { test ->
            waitForEnabled("Chat");ui.onNodeWithText("Chat").performClick()
            waitFor("Groups");ui.onNodeWithText("Groups").performClick()
            waitFor("Rejected promotion");ui.onNodeWithText("Rejected promotion").performClick()
            waitForEnabled("Message");ui.onNodeWithText("Message").performTextInput("Keep this rejected draft")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            ui.onNodeWithText("Members").performClick()
            ui.onNodeWithContentDescription("Promote member ${test.memberWallet.publicKey}").performClick()
            waitForEnabled("Confirm promotion");ui.onNodeWithText("Confirm promotion").performClick()
            waitFor(test.context.getString(R.string.group_error))
            ui.onNodeWithText(test.context.getString(R.string.group_error)).assertIsDisplayed()
            waitFor("Submitted. Delivery is not confirmed.")
            waitForEnabled("Retry")
            ui.waitUntil(15_000){test.peer.transactions.size==1&&test.peer.relayedMessages.size==1}
            val saved=requireNotNull(test.store.load(test.key.publicKey));val candidate=requireNotNull(saved.candidate)
            check(saved.status==GroupChatStore.Status.SUBMITTED&&saved.draft=="Keep this rejected draft")
            check(saved.creationKey!!.exportJson()==test.key.exportJson())
            check(postingTransaction(test.peer.transactions.single()).envelope()==candidate.envelope())
            check(SaitoEnvelope.fromSignedJson(test.peer.relayedMessages.single())==candidate.envelope())
            captureNativeScreen(File(test.context.cacheDir,"group-admin-evidence/rejected-relay.png"))
            test.scenario.recreate()
            waitFor("Groups");ui.onNodeWithText("Groups").performClick()
            waitFor("Rejected promotion");ui.onNodeWithText("Rejected promotion").performClick()
            waitFor("Submitted. Delivery is not confirmed.");waitForEnabled("Retry")
            check(test.store.load(test.key.publicKey)!!.candidate!!.envelope()==candidate.envelope())
            check(test.peer.transactions.size==1&&test.peer.relayedMessages.size==1)
            reject.set(false)
            ui.onNodeWithText("Retry").performClick()
            ui.waitUntil(15_000){test.store.load(test.key.publicKey)?.status==GroupChatStore.Status.FORWARDED}
            ui.waitUntil(15_000){test.peer.transactions.size==2&&test.peer.relayedMessages.size==2}
            waitFor("Forwarded. Delivery is not confirmed.")
            check(test.peer.transactions.all{postingTransaction(it).envelope()==candidate.envelope()})
            check(test.peer.relayedMessages.all{SaitoEnvelope.fromSignedJson(it)==candidate.envelope()})
            val retried=requireNotNull(test.store.load(test.key.publicKey))
            check(retried.candidate!!.envelope()==candidate.envelope()&&retried.draft=="Keep this rejected draft")
            check(retried.group.controls.count{SaitoEnvelope.decode(it).id==candidate.id}==1)
            check(test.wallets.loadExisting().exportJson()==test.wallet.exportJson()&&AndroidBackendStore(test.context).load()==test.backend)
            captureNativeScreen(File(test.context.cacheDir,"group-admin-evidence/rejected-relay-retried.png"))
            test.peer.assertHealthy()
        }
    }

    @Test fun aPromotionStopsAtRelayWhenARenameArrivesAfterRawSubmission() =
        withPreparedAdminAction("7","Before rename","Keep this rename draft") { fixture ->
            val candidate=fixture.candidate
            val renameBody=SaitoEnvelope.decode(candidate.envelope()).body.deepCopy().apply{addProperty("group_name","New authenticated name")}
            val rename=SaitoTransaction.create(fixture.wallet,renameBody,1700000123458,listOf(fixture.key.publicKey))
            fun attempt()=fixture.attempt{fixture.store.receive(rename.envelope())}
            org.junit.Assert.assertThrows(IllegalStateException::class.java){attempt()}
            ui.waitUntil(15_000){fixture.peer.transactions.size==1}
            check(postingTransaction(fixture.peer.transactions.single()).envelope()==candidate.envelope()&&fixture.peer.relayedMessages.isEmpty())
            val saved=requireNotNull(fixture.store.load(fixture.key.publicKey))
            check(saved.candidate!!.envelope()==candidate.envelope()&&saved.status==GroupChatStore.Status.SUBMITTED)
            check(saved.group.name=="New authenticated name"&&saved.draft=="Keep this rename draft")
            check(saved.creationKey!!.exportJson()==fixture.key.exportJson())
            fixture.store.prepareRetry(fixture.key.publicKey,candidate.id)
            org.junit.Assert.assertThrows(IllegalStateException::class.java){attempt()}
            check(fixture.peer.transactions.size==1&&fixture.peer.relayedMessages.isEmpty())
            fixture.assertOwnerUnchanged()
            fixture.peer.assertHealthy()
        }

    @Test fun aPreparedPromotionCannotTransmitOrRetryAfterTheTargetLeaves() =
        withPreparedAdminAction("8","Stale promotion","Keep the stale-action draft") { fixture ->
            val candidate=fixture.candidate
            fixture.store.receive(GroupChat.leave(fixture.member,fixture.key.publicKey,1700000123458).envelope())
            org.junit.Assert.assertThrows(IllegalStateException::class.java){fixture.attempt()}
            check(fixture.peer.transactions.isEmpty()&&fixture.peer.relayedMessages.isEmpty())
            val saved=requireNotNull(fixture.store.load(fixture.key.publicKey))
            check(saved.candidate!!.envelope()==candidate.envelope()&&saved.status==GroupChatStore.Status.UNKNOWN)
            check(saved.group.memberRoles[fixture.member.publicKey]==ChatGroupRole.LEFT&&saved.draft=="Keep the stale-action draft")
            check(saved.creationKey!!.exportJson()==fixture.key.exportJson())
            fixture.store.prepareRetry(fixture.key.publicKey,candidate.id)
            org.junit.Assert.assertThrows(IllegalStateException::class.java){fixture.attempt()}
            check(fixture.peer.transactions.isEmpty()&&fixture.peer.relayedMessages.isEmpty())
            check(fixture.store.load(fixture.key.publicKey)!!.candidate!!.envelope()==candidate.envelope())
            fixture.assertOwnerUnchanged()
            fixture.peer.assertHealthy()
        }

    @Test fun aPromotionCannotSubmitAfterItsAdministratorLosesAuthority() = assertAdminActionAfterRevocation(false,false)

    @Test fun aPromotionCannotRelayOrRetryAfterItsAdministratorLosesAuthority() = assertAdminActionAfterRevocation(false,true)

    @Test fun aRemovalCannotSubmitAfterItsAdministratorLeaves() = assertAdminActionAfterRevocation(true,false)

    @Test fun aRemovalCannotRelayOrRetryAfterItsAdministratorLeaves() = assertAdminActionAfterRevocation(true,true)

    private fun assertAdminActionAfterRevocation(removal:Boolean,afterRaw:Boolean) =
        withPreparedAdminAction(if(removal) {if(afterRaw)"d" else "c"} else {if(afterRaw)"b" else "a"},"Authority changed","Keep this authority draft",removal) { fixture ->
            val revocation=if(removal)GroupChat.leave(fixture.wallet,fixture.key.publicKey,1700000123458) else {
                val body=SaitoEnvelope.decode(fixture.candidate.envelope()).body.deepCopy().apply {
                    getAsJsonObject("member_ids").addProperty(fixture.wallet.publicKey,1)
                }
                SaitoTransaction.create(fixture.member,body,1700000123458,listOf(fixture.key.publicKey))
            }
            if(!afterRaw)fixture.store.receive(revocation.envelope())
            org.junit.Assert.assertThrows(IllegalStateException::class.java){
                fixture.attempt{if(afterRaw)fixture.store.receive(revocation.envelope())}
            }
            if(afterRaw)ui.waitUntil(15_000){fixture.peer.transactions.size==1}
            check(fixture.peer.transactions.size==if(afterRaw)1 else 0)
            if(afterRaw)check(postingTransaction(fixture.peer.transactions.single()).envelope()==fixture.candidate.envelope())
            check(fixture.peer.relayedMessages.isEmpty())
            val saved=requireNotNull(fixture.store.load(fixture.key.publicKey))
            check(saved.group.memberRoles[fixture.wallet.publicKey]==if(removal)ChatGroupRole.LEFT else ChatGroupRole.MEMBER)
            check(saved.candidate!!.envelope()==fixture.candidate.envelope()&&saved.draft=="Keep this authority draft")
            check(saved.status==if(afterRaw)GroupChatStore.Status.SUBMITTED else GroupChatStore.Status.UNKNOWN)
            check(saved.creationKey!!.exportJson()==fixture.key.exportJson())
            fixture.store.prepareRetry(fixture.key.publicKey,fixture.candidate.id)
            org.junit.Assert.assertThrows(IllegalStateException::class.java){fixture.attempt()}
            check(fixture.peer.transactions.size==if(afterRaw)1 else 0)
            check(fixture.peer.relayedMessages.isEmpty())
            fixture.assertOwnerUnchanged();fixture.peer.assertHealthy()
        }

    private class AdminUiFixture(val context:android.content.Context,val wallets:AndroidWalletStore,val backend:SaitoBackend,
        val wallet:SaitoWallet,val memberWallet:SaitoWallet,val key:SaitoWallet,val creation:SaitoTransaction,
        val store:GroupChatStore,val peer:ControlledFeedPeer,val scenario:ActivityScenario<MainActivity>,val endpoint:String)

    private fun withAdminUi(groupScalar:String,name:String,failureCapture:String,relayResponse:((com.google.gson.JsonObject)->String)?=null,journey:(AdminUiFixture)->Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev"&&Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallets=AndroidWalletStore(context);val original=wallets.loadOrCreate()
        val backend=AndroidBackendStore(context).load()
        val key=SaitoWallet.fromPrivateKey("0".repeat(63)+groupScalar) // Public unsafe fixtures only.
        val member=SaitoWallet.fromPrivateKey("0".repeat(63)+"2")
        val fixture=SaitoWallet.fromPrivateKey("0".repeat(63)+"1").withNamedChatGroup(key.publicKey,name,key)
        val filename="groupchat.${backend.id}.${fixture.publicKey}.${key.publicKey}.v1"
        val names=listOf(filename,"$filename.bak","$filename.new")
        synchronized(SaitoLocalState.lock){check(processFixtureFiles(context,names.toSet()).none{File(context.noBackupFilesDir,it).exists()})}
        val preferences=listOf("wallet-backup-reminders" to "hidden:${fixture.publicKey}","notification-permission" to "offered").map{(prefsName,keyName)->
            val prefs=context.getSharedPreferences(prefsName,0)
            Triple(prefs,keyName,if(prefs.contains(keyName))prefs.getBoolean(keyName,false)else null)
        }
        val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={"[]"},relayResponse=relayResponse)
        var primary:Throwable?=null
        try {
            wallets.replace(original,fixture)
            preferences.forEach{(prefs,keyName,_)->check(prefs.edit().putBoolean(keyName,true).commit())}
            val store=GroupChatStore(context,fixture,backend)
            val creation=GroupChat.create(fixture,key.publicKey,name,listOf(member.publicKey),1700000123456)
            store.prepareCreation(creation,key);store.record(key.publicKey,creation.id,GroupChatStore.Status.FORWARDED)
            peer.start().use{server->ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)
                .putExtra("feedPeerUrl","ws://127.0.0.1:${server.port}/wsopen")
                .putExtra("feedOfficialPeerUrl","ws://127.0.0.1:${server.port}/wsopen")).use{scenario->
                withFailureCapture({captureNativeScreen(File(context.cacheDir,"group-admin-evidence/$failureCapture"))}) {
                    journey(AdminUiFixture(context,wallets,backend,fixture,member,key,creation,store,peer,scenario,"ws://127.0.0.1:${server.port}/wsopen"))
                }
            }}
        }catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,
            {synchronized(SaitoLocalState.lock){processFixtureFiles(context,names.toSet()).forEach{val file=File(context.noBackupFilesDir,it);check(!file.exists()||file.delete())}}},
            {wallets.replace(wallets.loadExisting(),original)},
            *preferences.map{(prefs,keyName,value)->{check(prefs.edit().apply{if(value==null)remove(keyName)else putBoolean(keyName,value)}.commit());Unit}}.toTypedArray())}
    }

    private class AdminDelivery(val wallets:AndroidWalletStore,val backendStore:AndroidBackendStore,val backend:SaitoBackend,
        val wallet:SaitoWallet,val member:SaitoWallet,val key:SaitoWallet,val store:GroupChatStore,
        val candidate:SaitoTransaction,val peer:ControlledFeedPeer,private val endpoint:String) {
        fun attempt(afterSubmitted:()->Unit={})=
            submitPreparedAdminAction(wallet,endpoint,store,key.publicKey,candidate,afterSubmitted)
        fun assertOwnerUnchanged() {
            check(wallets.loadExisting().exportJson()==wallet.exportJson()&&backendStore.load()==backend)
        }
    }

    private fun withPreparedAdminAction(groupScalar:String,name:String,draft:String,removal:Boolean=false,scenario:(AdminDelivery)->Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev"&&Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallets=AndroidWalletStore(context)
        val original=wallets.loadOrCreate()
        val backend=AndroidBackendStore(context).load()
        val wallet=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixtures only.
        val member=SaitoWallet.fromPrivateKey("0".repeat(63)+"2")
        val key=SaitoWallet.fromPrivateKey("0".repeat(63)+groupScalar)
        val filename="groupchat.${backend.id}.${wallet.publicKey}.${key.publicKey}.v1"
        val names=listOf(filename,"$filename.bak","$filename.new")
        synchronized(SaitoLocalState.lock){check(processFixtureFiles(context,names.toSet()).none{File(context.noBackupFilesDir,it).exists()})}
        var primary:Throwable?=null
        try {
            wallets.replace(original,wallet)
            val store=GroupChatStore(context,wallet,backend)
            val creation=GroupChat.create(wallet,key.publicKey,name,listOf(member.publicKey),1700000123456)
            store.prepareCreation(creation,key);store.record(key.publicKey,creation.id,GroupChatStore.Status.FORWARDED)
            store.saveDraft(key.publicKey,draft)
            val group=requireNotNull(store.load(key.publicKey)).group
            val candidate=if(removal)GroupChat.removeMember(wallet,group,member.publicKey,1700000123457)
                .also{store.prepareRemoval(it,member.publicKey)} else
                GroupChat.promote(wallet,group,member.publicKey,1700000123457).also{store.preparePromotion(it,member.publicKey)}
            val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||")
            peer.start().use{server->scenario(AdminDelivery(wallets,AndroidBackendStore(context),backend,wallet,member,key,store,candidate,peer,"ws://127.0.0.1:${server.port}/wsopen"))}
        }catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,
            {synchronized(SaitoLocalState.lock){processFixtureFiles(context,names.toSet()).forEach{val file=File(context.noBackupFilesDir,it);check(!file.exists()||file.delete())}}},
            {wallets.replace(wallets.loadExisting(),original)})}
    }

    @Test fun anAdminCanPromoteAMemberWithoutLosingTheDraftAndReopenTheNewRole() =
        withAdminUi("9","Admin beta group","failure.png") { test ->
            val context=test.context;val wallets=test.wallets;val backend=test.backend
            val fixture=test.wallet;val key=test.key;val member=test.memberWallet.publicKey
            val creation=test.creation;val store=test.store;val peer=test.peer;val scenario=test.scenario
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
    private fun revealGroupStatus(text:String) = ui.onNodeWithContentDescription("Group status and actions")
        .performScrollToNode(hasText(text))
    private fun waitFor(text:String)=ui.waitUntil(15_000){ui.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
    private fun waitForEnabled(text:String)=ui.waitUntil(15_000){ui.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
}

/** Shared actual submission wiring; each scenario owns its authenticated mutation and assertions. */
private fun submitPreparedAdminAction(wallet:SaitoWallet,endpoint:String,store:GroupChatStore,groupId:String,
    candidate:SaitoTransaction,afterSubmitted:()->Unit={})=kotlinx.coroutines.runBlocking {
    nl.davidengelen.redsquare.chat.submitChatTransaction(wallet,endpoint,wallet.publicKey,candidate,
        nl.davidengelen.redsquare.chat.ChatSubmissionCallbacks(
            claim={store.claim(groupId,candidate.id)},assertSelected={store.assertSelected()},
            submitted={store.record(groupId,candidate.id,GroupChatStore.Status.SUBMITTED);afterSubmitted()},
            forwarded={store.record(groupId,candidate.id,GroupChatStore.Status.FORWARDED)},
            relayRecipients={store.relayRecipients(groupId,candidate.id)}))
}
