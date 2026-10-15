package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.moderation.PersonalPolicy
import org.junit.Rule
import org.junit.Test
import java.io.File

class GroupChatPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun relayMembershipIsReadAfterSubmissionBeforeBuildingTheOutgoingRelay() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val walletStore=AndroidWalletStore(context)
        val original=walletStore.loadOrCreate()
        val originalBackend=AndroidBackendStore(context).load()
        val fixture=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixtures only.
        val removed=SaitoWallet.fromPrivateKey("0".repeat(63)+"2").publicKey
        val retained=SaitoWallet.fromPrivateKey("0".repeat(63)+"3").publicKey
        val key=SaitoWallet.fromPrivateKey("0".repeat(63)+"9")
        val filename="groupchat.${SaitoBackend.OWNER.id}.${fixture.publicKey}.${key.publicKey}.v1"
        synchronized(SaitoLocalState.lock){check(processFixtureFiles(context,setOf(filename,"$filename.bak","$filename.new")).none{File(context.noBackupFilesDir,it).exists()})}
        var primary:Throwable?=null
        try {
            walletStore.replace(original,fixture)
            AndroidBackendStore(context).save(SaitoBackend.OWNER)
            val store=nl.davidengelen.redsquare.chat.GroupChatStore(context,fixture,SaitoBackend.OWNER)
            val creation=GroupChat.create(fixture,key.publicKey,"Current relay membership",listOf(removed,retained),1700000123400)
            store.prepareCreation(creation,key)
            store.record(key.publicKey,creation.id,nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
            val candidate=GroupChat.message(fixture,GroupChat.readCreation(creation.envelope()),"Prepared before member removal",1700000123401)
            store.prepareMessage(candidate)
            val removal=com.google.gson.JsonObject().apply{
                addProperty("module","Chat");addProperty("request","chat remove")
                addProperty("group_id",key.publicKey);addProperty("member_id",removed)
            }
            val change=SaitoTransaction.create(fixture,removal,1700000123402,listOf(key.publicKey))
            val laterRemoval=removal.deepCopy().apply{addProperty("member_id",retained)}
            val guardedChange=SaitoTransaction.create(fixture,laterRemoval,1700000123403,listOf(key.publicKey))
            val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||")
            peer.start().use{server->
                kotlinx.coroutines.runBlocking {
                    nl.davidengelen.redsquare.chat.submitChatTransaction(fixture,"ws://127.0.0.1:${server.port}/wsopen",removed,candidate,
                        nl.davidengelen.redsquare.chat.ChatSubmissionCallbacks(
                            claim={store.claim(key.publicKey,candidate.id)},assertSelected={
                                store.assertSelected()
                                store.receive(guardedChange.envelope()) // Last protected change before Relay enqueue.
                            },
                            submitted={
                                store.record(key.publicKey,candidate.id,nl.davidengelen.redsquare.chat.GroupChatStore.Status.SUBMITTED)
                                store.receive(change.envelope()) // Signed membership changes between raw submission and Relay.
                            },
                            forwarded={store.record(key.publicKey,candidate.id,nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)},
                            relayRecipients={
                                val roles=requireNotNull(store.load(key.publicKey)).group.memberRoles
                                check(roles[retained]==ChatGroupRole.REMOVED)
                                roles.filterValues{it.active}.keys.filter{it!=fixture.publicKey}
                            }),
                        relayRecipients=listOf(removed,retained))
                }
                check(peer.transactions.size==1 && peer.relayedMessages.size==1)
                org.junit.Assert.assertEquals(listOf(fixture.publicKey),peer.relayRecipients.single())
                check(postingTransaction(peer.transactions.single()).envelope()==candidate.envelope())
                check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single())==candidate.envelope())
                val saved=requireNotNull(store.load(key.publicKey))
                check(saved.group.memberRoles[removed]==ChatGroupRole.REMOVED)
                check(saved.group.memberRoles[retained]==ChatGroupRole.REMOVED)
                check(saved.candidate?.envelope()==candidate.envelope() && saved.status==nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
                peer.assertHealthy()
            }
        }catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,
            {synchronized(SaitoLocalState.lock){listOf(filename,"$filename.bak","$filename.new").forEach{val file=File(context.noBackupFilesDir,it);check(!file.exists()||file.delete())}}},
            {walletStore.replace(walletStore.loadExisting(),original)},
            {AndroidBackendStore(context).save(originalBackend)})}
    }

    @Test fun aNamedGroupCanBeCreatedWithAnInvitationAndReadAfterReopening() = exerciseGroupUi()

    @Test fun anExistingMemberInvitationCanBeConfirmedOnTheSelectedBackendAndReadAfterReopening() = exerciseGroupUi(joining=true)

    @Test fun leavingANamedGroupRetainsTheSignedDepartureAfterReopening() = exerciseGroupUi(leaving=true)

    @Test fun anInterruptedCreationCanRetryItsSavedOriginalAndRecoverKeyMetadataWithoutAutomaticSending() = exerciseGroupUi(retrying=true)

    @Test fun aKnownGroupRejoinPreviewsAndRoutesToItsRetainedAdministrator() = exerciseGroupUi(knownRejoin=true)

    @Test fun groupMessagesCombineAvailableHistoryLiveDeliveryAndPersistentCacheWithoutDuplicateSending() = exerciseGroupUi(joining=true,messaging=true)

    @Test fun anUnsentGroupDraftSurvivesBackNavigationAndReopeningWithoutSending() = exerciseGroupUi(joining=true,messaging=true,drafting=true)

    @Test fun unavailableGroupArchiveKeepsCachedMessagesLiveDeliveryAndSendingUsable() = exerciseGroupUi(joining=true,messaging=true,archiveFault=true)

    @Test fun signedGroupHistoryCanPageOlderAndRemainCachedAfterArchivePruning() = exerciseGroupUi(joining=true,paginating=true)

    @Test fun removedAuthorsCannotHideAvailableEarlierGroupHistory() = exerciseGroupUi(joining=true,paginating=true,rejectedPage=true)

    @Test fun aTemporarilyUnreadableGroupDraftRecoversOnRefreshWithoutSending() = exerciseGroupUi(joining=true,messaging=true,drafting=true,draftFault=true)

    private fun exerciseGroupUi(joining:Boolean=false, leaving:Boolean=false, retrying:Boolean=false, knownRejoin:Boolean=false, messaging:Boolean=false, drafting:Boolean=false, archiveFault:Boolean=false, paginating:Boolean=false, rejectedPage:Boolean=false, draftFault:Boolean=false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val store = AndroidWalletStore(context)
        val original = store.loadOrCreate()
        val fixture = SaitoWallet.fromPrivateKey("0".repeat(63) + if(joining||knownRejoin) "4" else "1") // Public unsafe test wallet. Never fund.
        val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
        val checkpointKey=SaitoWallet.fromPrivateKey("0".repeat(63)+"3") // Public unsafe fixture only. Never fund.
        val checkpoint=GroupChat.create(fixture,checkpointKey.publicKey,"Recovered group",listOf(member),1700000123456)
        val backend = AndroidBackendStore(context).load()
        val prefix = "groupchat.${SaitoBackend.OWNER.id}.${fixture.publicKey}."
        val retained = synchronized(SaitoLocalState.lock) {
            context.noBackupFilesDir.listFiles()!!.filter { it.name.startsWith(prefix) }.associate { it.name to it.readBytes() }
        }
        val preferences = listOf("wallet-backup-reminders" to "hidden:${fixture.publicKey}", "notification-permission" to "offered").map { (name,key) ->
            val prefs = context.getSharedPreferences(name,0)
            Triple(prefs,key,if(prefs.contains(key)) prefs.getBoolean(key,false) else null)
        }
        val historyAvailable=java.util.concurrent.atomic.AtomicBoolean(true)
        val brokenHistory=java.util.concurrent.atomic.AtomicBoolean(false)
        val failedHistoryReads=java.util.concurrent.atomic.AtomicInteger(0)
        val messageReference=if(messaging||paginating)InstrumentationRegistry.getInstrumentation().context.assets.open("native-group-chat-message-reference.json")
            .bufferedReader().use{com.google.gson.JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject} else null
        val oldestRecent=1700000123500L
        val olderReads=java.util.concurrent.atomic.AtomicInteger(0)
        val requestedCutoff=java.util.concurrent.atomic.AtomicLong(-1)
        var ancientMessage:SaitoTransaction?=null
        val pages=if(paginating){
            val references=InstrumentationRegistry.getInstrumentation().context.assets.open("native-group-chat-creation-reference.json")
                .bufferedReader().use{com.google.gson.JsonParser.parseReader(it).asJsonObject}
            val link=GroupInvitation.parse(references.getAsJsonArray("invitations")[3].asJsonObject.get("link").asString)
            val author=SaitoWallet.fromPrivateKey("0".repeat(63)+if(rejectedPage) "2" else "1") // Public unsafe fixture only.
            val admin=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixture only.
            val adminGroup=GroupChat.confirmInvitation(link,GroupChat.join(admin,link,1700000123498).envelope())
            val group=GroupChat.confirmInvitation(link,GroupChat.join(author,link,1700000123499).envelope())
            fun row(text:String,time:Long,signer:SaitoWallet=author,source:NamedChatGroup=group)=com.google.gson.JsonObject().apply{
                addProperty("tx",GroupChat.message(signer,source,text,time).envelope())
                addProperty("created_at",42) // Unsigned metadata must not determine the paging cursor.
            }
            val recent=com.google.gson.JsonArray().apply{repeat(50){index->add(row("Recent group message $index",oldestRecent+index))}}
            val older=com.google.gson.JsonArray().apply{add(row("Available older group message",oldestRecent-1,admin,adminGroup))}
            ancientMessage=GroupChat.message(admin,adminGroup,"Ancient live group message",oldestRecent-1000)
            recent.toString() to older.toString()
        }else null
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { criteria ->
            if(brokenHistory.get() && criteria.get("field3")?.asString==messageReference?.get("groupId")?.asString){
                failedHistoryReads.incrementAndGet();"invalid group archive response"
            }else if(messageReference!=null && historyAvailable.get() && criteria.get("field3")?.asString==messageReference.get("groupId").asString){
                if(pages!=null){
                    val cutoff=criteria.get("created_earlier_than").asLong
                    if(cutoff>oldestRecent)pages.first else {
                        olderReads.incrementAndGet();requestedCutoff.set(cutoff)
                        if(cutoff==oldestRecent)pages.second else "[]"
                    }
                }else com.google.gson.JsonArray().apply{add(com.google.gson.JsonObject().apply{
                    addProperty("tx",messageReference.get("envelope").asString);addProperty("created_at",messageReference.get("timestamp").asLong)
                })}.toString()
            }else "[]"
        })
        var primary: Throwable? = null
        try {
            store.replace(original,fixture)
            AndroidBackendStore(context).save(SaitoBackend.OWNER)
            preferences.forEach { (prefs,key,_) -> check(prefs.edit().putBoolean(key,true).commit()) }
            synchronized(SaitoLocalState.lock) { context.noBackupFilesDir.listFiles()!!.filter { it.name.startsWith(prefix) }.forEach { check(it.delete()) } }
            if(rejectedPage){
                val references=InstrumentationRegistry.getInstrumentation().context.assets.open("native-group-chat-creation-reference.json")
                    .bufferedReader().use{com.google.gson.JsonParser.parseReader(it).asJsonObject}
                val invitation=GroupInvitation.parse(references.getAsJsonArray("invitations")[3].asJsonObject.get("link").asString)
                val saved=nl.davidengelen.redsquare.chat.GroupChatStore(context,fixture,SaitoBackend.OWNER)
                val joined=GroupChat.join(fixture,invitation,1700000123456)
                saved.prepareInvitation(invitation,joined)
                saved.record(invitation.id,joined.id,nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
                val admin=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixture only.
                val removal=com.google.gson.JsonObject().apply{
                    addProperty("module","Chat");addProperty("request","chat remove")
                    addProperty("group_id",invitation.id);addProperty("member_id",member)
                }
                saved.receive(SaitoTransaction.create(admin,removal,1700000123457,listOf(invitation.id)).envelope())
                check(saved.load(invitation.id)!!.group.memberRoles[member]==ChatGroupRole.REMOVED)
            }
            if(retrying) nl.davidengelen.redsquare.chat.GroupChatStore(context,fixture,SaitoBackend.OWNER).prepareCreation(checkpoint,checkpointKey)
            var knownInvitation:GroupInvitation?=null
            if(knownRejoin){
                val references=InstrumentationRegistry.getInstrumentation().context.assets.open("native-group-chat-creation-reference.json")
                    .bufferedReader().use{com.google.gson.JsonParser.parseReader(it).asJsonObject}
                val invitation=GroupInvitation.parse(references.getAsJsonArray("invitations")[3].asJsonObject.get("link").asString)
                knownInvitation=invitation
                val initial=GroupChat.join(fixture,invitation,1700000123456)
                val saved=nl.davidengelen.redsquare.chat.GroupChatStore(context,fixture,SaitoBackend.OWNER)
                saved.prepareInvitation(invitation,initial)
                saved.record(invitation.id,initial.id,nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
                val leave=GroupChat.leave(fixture,invitation.id,1700000123457)
                saved.prepareLeave(leave)
                saved.record(invitation.id,leave.id,nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
            }
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)
                    .putExtra("feedPeerUrl","ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl","ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir,if(draftFault) "group-chat-evidence/draft-refresh-failure.png" else if(rejectedPage) "group-chat-evidence/rejected-page-failure.png" else if(paginating) "group-chat-evidence/pagination-failure.png" else if(archiveFault) "group-chat-evidence/archive-failure.png" else if(drafting) "group-chat-evidence/draft-failure.png" else if(messaging) "group-chat-evidence/message-failure.png" else if(knownRejoin) "group-chat-evidence/known-rejoin-failure.png" else if(joining) "group-chat-evidence/join-failure.png" else if(leaving) "group-chat-evidence/leave-failure.png" else if(retrying) "group-chat-evidence/retry-failure.png" else "group-chat-evidence/create-failure.png")) }) {
                        waitFor("Chat")
                        ui.waitUntil(15_000) { ui.onAllNodes(hasText("Chat") and isEnabled()).fetchSemanticsNodes().size == 1 }
                        ui.onNodeWithText("Chat").assertIsDisplayed().performClick()
                        waitFor("Groups")
                        ui.onNodeWithText("Groups").assertIsDisplayed().performClick()
                        waitFor("Group messages are unencrypted.")
                        if(knownRejoin){
                            val known=requireNotNull(knownInvitation)
                            val claims=com.google.gson.JsonObject().apply{
                                addProperty("id",known.id);addProperty("name","Untrusted renamed group")
                                addProperty("sender",known.sender);addProperty("admin",fixture.publicKey)
                            }
                            val link=java.util.Base64.getEncoder().encodeToString(claims.toString().toByteArray(Charsets.UTF_8))
                            ui.waitUntil(15_000){ui.onAllNodes(hasText("Join group") and isEnabled()).fetchSemanticsNodes().size==1}
                            ui.onNodeWithText("Join group").assertIsDisplayed().assertIsEnabled().performClick()
                            ui.waitUntil(15_000){ui.onAllNodes(hasText("Invitation link") and hasSetTextAction() and isEnabled()).fetchSemanticsNodes().size==1}
                            ui.onNodeWithText("Invitation link").performTextInput(link)
                            ui.onNodeWithText("Preview").assertIsEnabled().performClick()
                            val previewName=hasText(known.name) and hasAnyAncestor(isDialog())
                            val previewAdmin=hasText(known.admin) and hasAnyAncestor(isDialog())
                            ui.waitUntil(15_000){ui.onAllNodes(previewName).fetchSemanticsNodes().size==1}
                            ui.onNode(previewName).performScrollTo().assertIsDisplayed()
                            ui.onNode(previewAdmin).performScrollTo().assertIsDisplayed()
                            ui.onNode(hasText("Using saved group details.") and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            check(store.loadExisting().exportJson()==fixture.exportJson())
                            ui.onNodeWithText("Join").assertIsEnabled().performClick()
                            waitFor("Forwarded. Delivery is not confirmed.")
                            ui.waitUntil(15_000){peer.transactions.size==1 && peer.relayedMessages.size==1}
                            val envelope=SaitoEnvelope.fromSignedJson(peer.relayedMessages.single())
                            check(postingTransaction(peer.transactions.single()).envelope()==envelope)
                            check(SaitoEnvelope.decode(envelope).recipients==listOf(known.id,fixture.publicKey,known.admin,known.sender).distinct())
                            check(peer.relayRecipients.single()==listOf(fixture.publicKey,known.admin))
                            val updated=store.loadExisting()
                            val restored=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,updated,SaitoBackend.OWNER).load(known.id))
                            check(restored.group.confirmedInvitation?.admin==known.admin && restored.group.name==known.name)
                            check(restored.group.memberRoles[fixture.publicKey]==ChatGroupRole.MEMBER)
                            check(restored.group.memberRoles[known.admin]==ChatGroupRole.ADMIN)
                            check(updated.exportJson()==fixture.withNamedChatGroup(known.id,known.name).exportJson())
                            captureNativeScreen(File(context.cacheDir,"group-chat-evidence/known-rejoined.png"),known.name)
                            scenario.recreate();waitFor("Groups");ui.onNodeWithText("Groups").performClick();waitFor(known.name)
                            check(peer.transactions.size==1 && peer.relayedMessages.size==1)
                            peer.assertHealthy();return@withFailureCapture
                        }
                        if(retrying) {
                            waitFor("Recovered group")
                            ui.onNodeWithText("Recovered group").assertIsDisplayed().performClick()
                            ui.waitForGroupStatus("Status unknown. Your signed action is saved on this device.")
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            check(store.loadExisting().exportJson()==fixture.exportJson())
                            peer.disconnectAfterTransaction=true
                            ui.waitForGroupStatus("Retry", enabled=true)
                            ui.onNodeWithText("Retry").assertIsDisplayed().assertIsEnabled().performClick()
                            ui.waitForGroupStatus("Group information could not be read or saved, or the action could not be completed. Your existing records are kept.")
                            ui.waitForGroupStatus("Submitted. Delivery is not confirmed.")
                            check(peer.transactions.size==1 && peer.relayedMessages.isEmpty())
                            check(postingTransaction(peer.transactions.single()).envelope()==checkpoint.envelope())
                            val updated=fixture.withNamedChatGroup(checkpointKey.publicKey,"Recovered group",checkpointKey)
                            check(store.loadExisting().exportJson()==updated.exportJson())
                            val backup=com.google.gson.JsonParser.parseString(store.loadExisting().exportJson()).asJsonObject
                            val key=backup.getAsJsonArray("keys").single{it.asJsonObject.get("publicKey").asString==checkpointKey.publicKey}.asJsonObject
                            check(key.get("identifier").asString=="Recovered group" && key.get("group").asInt==1 && key.get("watched").asBoolean)
                            check(key.get("privateKey").asString=="0".repeat(63)+"3")
                            check(backup.getAsJsonObject("chat").getAsJsonArray("groups").any{it.asString==checkpointKey.publicKey})
                            captureNativeScreen(File(context.cacheDir,"group-chat-evidence/retry-uncertain.png"),"Submitted. Delivery is not confirmed.")
                            scenario.recreate()
                            waitFor("Groups");ui.onNodeWithText("Groups").performClick()
                            waitFor("Recovered group");ui.onNodeWithText("Recovered group").performClick()
                            ui.waitForGroupStatus("Submitted. Delivery is not confirmed.")
                            check(peer.transactions.size==1 && peer.relayedMessages.isEmpty())
                            val recovered=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,updated,SaitoBackend.OWNER).load(checkpointKey.publicKey))
                            check(requireNotNull(recovered.candidate).encoded()==checkpoint.encoded() && recovered.creationKey?.exportJson()==checkpointKey.exportJson())
                            peer.disconnectAfterTransaction=false
                            ui.waitForGroupStatus("Retry", enabled=true)
                            ui.onNodeWithText("Retry").assertIsEnabled().performClick()
                            ui.waitForGroupStatus("Forwarded. Delivery is not confirmed.")
                            ui.waitUntil(15_000){peer.transactions.size==2 && peer.relayedMessages.size==1}
                            check(peer.transactions.all{postingTransaction(it).envelope()==checkpoint.envelope()})
                            check(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single())==checkpoint.envelope())
                            captureNativeScreen(File(context.cacheDir,"group-chat-evidence/retry-forwarded.png"),"Forwarded. Delivery is not confirmed.")
                            scenario.recreate()
                            waitFor("Groups");ui.onNodeWithText("Groups").performClick();waitFor("Recovered group")
                            check(peer.transactions.size==2 && peer.relayedMessages.size==1)
                            check(store.loadExisting().exportJson()==updated.exportJson())
                            check(AndroidBackendStore(context).load()==SaitoBackend.OWNER)
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
                        if(joining) {
                            val references=InstrumentationRegistry.getInstrumentation().context.assets
                                .open("native-group-chat-creation-reference.json").bufferedReader().use {
                                    com.google.gson.JsonParser.parseReader(it).asJsonObject
                                }
                            val reference=references.getAsJsonArray("invitations")[3].asJsonObject
                            val invitation=GroupInvitation.parse(reference.get("link").asString)
                            check(reference.get("link").asString.contains('+'))
                            ui.waitUntil(15_000){ui.onAllNodes(hasText("Join group") and isEnabled()).fetchSemanticsNodes().size==1}
                            ui.onNodeWithText("Join group").assertIsDisplayed().assertIsEnabled().performClick()
                            ui.onNodeWithText("Invitation link").performTextInput(reference.get("link").asString)
                            ui.onNodeWithText("Preview").assertIsEnabled().performClick()
                            waitFor(invitation.name)
                            waitFor(invitation.admin)
                            waitFor(invitation.sender)
                            waitFor(invitation.id)
                            ui.onNodeWithText(invitation.admin).performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText(invitation.sender).performScrollTo().assertIsDisplayed()
                            ui.onNodeWithText(invitation.id).performScrollTo().assertIsDisplayed()
                            waitFor(if(rejectedPage) "Using saved group details." else "Invitation details are provided by the sender.")
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            check(AndroidBackendStore(context).load()==SaitoBackend.OWNER)
                            ui.onNodeWithText("Join").assertIsEnabled().performClick()
                            waitFor("Forwarded. Delivery is not confirmed.")
                            ui.waitUntil(15_000){peer.transactions.size==1 && peer.relayedMessages.size==1}
                            val envelope=SaitoEnvelope.fromSignedJson(peer.relayedMessages.single())
                            check(postingTransaction(peer.transactions.single()).envelope()==envelope)
                            val signed=SaitoEnvelope.decode(envelope)
                            check(signed.author==fixture.publicKey && signed.body.get("request").asString=="chat join")
                            check(signed.body.get("group_id").asString==invitation.id && signed.body.get("invited_by").asString==invitation.sender)
                            check(signed.recipients.first()==invitation.id && signed.recipients.containsAll(listOf(fixture.publicKey,invitation.admin,invitation.sender)))
                            val group=GroupChat.confirmInvitation(invitation,envelope)
                            check(group.creationEnvelope==null && group.memberRoles[fixture.publicKey]==ChatGroupRole.MEMBER)
                            check(!group.memberRoles.containsKey(invitation.sender))
                            waitFor("Joined using invitation details.")
                            captureNativeScreen(File(context.cacheDir,"group-chat-evidence/joined.png"),invitation.name)
                            if(paginating){
                                ui.waitForGroupStatus("Load older messages")
                                ui.waitUntil(15_000){ui.onAllNodes(hasText("Load older messages") and isEnabled()).fetchSemanticsNodes().size==1}
                                if(rejectedPage){
                                    ui.waitForGroupStatus("Older group history could not be loaded. Your cached messages are kept.")
                                    ui.onNodeWithText("Older group history could not be loaded. Your cached messages are kept.").assertIsDisplayed()
                                    ui.onNodeWithText("Recent group message 0").assertDoesNotExist()
                                }else for(text in listOf("Recent group message 0","Recent group message 49"))waitForGroupMessage(text)
                                val recent=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,store.loadExisting(),SaitoBackend.OWNER).load(invitation.id))
                                check(recent.messages.size==(if(rejectedPage) 0 else 50) && olderReads.get()==0)
                                if(rejectedPage)check(recent.group.memberRoles[member]==ChatGroupRole.REMOVED)
                                val successes=peer.incomingAcknowledgements.count{it[0]==13.toByte()}
                                peer.sendIncoming(requireNotNull(ancientMessage).applicationRequest(1).let{it.copyOfRange(5,it.size)})
                                ui.waitUntil(15_000){peer.incomingAcknowledgements.count{it[0]==13.toByte()}>successes}
                                waitForGroupMessage("Ancient live group message")
                                check(olderReads.get()==0)
                                ui.waitForGroupStatus("Load older messages", enabled=true)
                                ui.onNodeWithText("Load older messages").assertIsDisplayed().assertIsEnabled().performClick()
                                waitForGroupMessage("Available older group message")
                                check(olderReads.get()==1 && requestedCutoff.get()==oldestRecent)
                                ui.onNodeWithText("Load older messages").assertDoesNotExist()
                                val saved=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,store.loadExisting(),SaitoBackend.OWNER).load(invitation.id))
                                check(saved.messages.size==(if(rejectedPage) 2 else 52))
                                if(rejectedPage)check(saved.group.memberRoles[member]==ChatGroupRole.REMOVED)
                                historyAvailable.set(false)
                                captureNativeScreen(File(context.cacheDir,if(rejectedPage) "group-chat-evidence/rejected-page-loaded.png" else "group-chat-evidence/pagination-loaded.png"),"Available older group message")
                            }
                            if(messaging){
                                val available=requireNotNull(messageReference)
                                waitFor(available.get("text").asString)
                                ui.onNodeWithText(available.get("text").asString).assertIsDisplayed()
                                ui.onNodeWithText("Message").performTextInput("Native group hello")
                                sendMessage()
                                ui.waitUntil(15_000){peer.transactions.size==2 && peer.relayedMessages.size==2}
                                waitFor("Forwarded. Delivery is not confirmed.")
                                val sent=SaitoEnvelope.fromSignedJson(peer.relayedMessages.last())
                                check(postingTransaction(peer.transactions.last()).envelope()==sent)
                                val signed=SaitoEnvelope.decode(sent)
                                check(signed.author==fixture.publicKey && signed.body.get("request").asString=="chat message")
                                check(signed.body.get("message").asString=="Native group hello" && signed.body.get("group_id").asString==invitation.id)
                                val verified=GroupChat.readMessage(group,sent)
                                check(signed.body.get("module").asString=="Chat")
                                check(verified.author==fixture.publicKey && verified.text=="Native group hello" && verified.createdAt==signed.createdAt)
                                check(signed.recipients==listOf(fixture.publicKey,invitation.id))
                                check(peer.relayRecipients.last()==listOf(fixture.publicKey,invitation.admin))
                                ui.onNodeWithContentDescription("Group messages").performScrollToNode(hasText("Native group hello"))
                                ui.onNodeWithText("Native group hello").assertIsDisplayed()
                                val sender=SaitoWallet.fromPrivateKey("0".repeat(63)+"2") // Public unsafe fixture only.
                                val senderGroup=GroupChat.confirmInvitation(invitation,GroupChat.join(sender,invitation,1700000123459).envelope())
                                val live=GroupChat.message(sender,senderGroup,"Live group message",1700000123460)
                                val incoming=live.applicationRequest(1).let{it.copyOfRange(5,it.size)}
                                peer.sendIncoming(incoming)
                                ui.waitUntil(15_000){peer.incomingAcknowledgements.any{it[0]==13.toByte()}}
                                waitForGroupMessage("Live group message")
                                val before=peer.incomingAcknowledgements.count{it[0]==13.toByte()}
                                peer.sendIncoming(incoming)
                                ui.waitUntil(15_000){peer.incomingAcknowledgements.count{it[0]==13.toByte()}>before}
                                ui.onAllNodesWithText("Live group message").assertCountEquals(1)
                                historyAvailable.set(false)
                                captureNativeScreen(File(context.cacheDir,"group-chat-evidence/messages-sent.png"),"Live group message")
                            }
                            if(archiveFault){
                                brokenHistory.set(true)
                                androidx.test.espresso.Espresso.closeSoftKeyboard()
                                ui.waitUntil(15_000){runCatching{ui.onNodeWithText("Refresh").assertIsDisplayed()}.isSuccess}
                                ui.onNodeWithText("Refresh").assertIsDisplayed().performClick()
                                ui.waitUntil(15_000){failedHistoryReads.get()>0}
                                ui.waitForGroupStatus("Older group history could not be loaded. Your cached messages are kept.")
                                    ui.onNodeWithText("Older group history could not be loaded. Your cached messages are kept.").assertIsDisplayed()
                                ui.waitUntil(15_000){ui.onAllNodes(hasText("Message") and isEnabled()).fetchSemanticsNodes().size==1}
                                ui.onNodeWithText("Message").assertIsEnabled().performTextInput("Send while archive is unavailable")
                                ui.waitUntil(15_000){ui.onAllNodes(hasText("Send") and isEnabled()).fetchSemanticsNodes().size==1}
                                sendMessage()
                                ui.waitUntil(15_000){peer.transactions.size==3&&peer.relayedMessages.size==3}
                                ui.waitForGroupStatus("Forwarded. Delivery is not confirmed.")
                                val original=SaitoEnvelope.fromSignedJson(peer.relayedMessages.last())
                                check(postingTransaction(peer.transactions.last()).envelope()==original)
                                val retained=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,store.loadExisting(),SaitoBackend.OWNER).load(invitation.id))
                                val sent=GroupChat.readMessage(retained.group,original)
                                check(sent.author==fixture.publicKey&&sent.text=="Send while archive is unavailable")
                                check(SaitoEnvelope.decode(original).recipients==listOf(fixture.publicKey,invitation.id))
                                check(peer.relayRecipients.last()==listOf(fixture.publicKey,invitation.admin,member))
                                waitForGroupMessage(sent.text)
                                val sender=SaitoWallet.fromPrivateKey("0".repeat(63)+"2") // Public unsafe fixture only.
                                val live=GroupChat.message(sender,retained.group,"Live while archive is unavailable",1700000123461)
                                val successes=peer.incomingAcknowledgements.count{it[0]==13.toByte()}
                                peer.sendIncoming(live.applicationRequest(1).let{it.copyOfRange(5,it.size)})
                                ui.waitUntil(15_000){peer.incomingAcknowledgements.count{it[0]==13.toByte()}>successes}
                                waitForGroupMessage("Live while archive is unavailable")
                                captureNativeScreen(File(context.cacheDir,"group-chat-evidence/archive-unavailable-live.png"),"Live while archive is unavailable")
                            }
                            if(drafting){
                                ui.onNodeWithText("Message").performTextInput("Keep this unsent group draft")
                                androidx.test.espresso.Espresso.closeSoftKeyboard()
                                ui.waitUntil(15_000){runCatching{ui.onNodeWithText("Back").assertIsDisplayed()}.isSuccess}
                                ui.onNodeWithText("Back").assertIsDisplayed().performClick()
                                waitFor("Create group")
                                ui.onNodeWithText("Create group").assertIsDisplayed()
                                if(draftFault){
                                    // Preserve opaque encrypted bytes; fault only the isolated emulator record.
                                    val record=File(context.noBackupFilesDir,"${prefix}${invitation.id}.v1")
                                    val retainedEncrypted=synchronized(SaitoLocalState.lock){record.readBytes().also{
                                        check(!File(record.path+".bak").exists())
                                        record.writeBytes(byteArrayOf(1))
                                    }}
                                    var faultFailure:Throwable?=null
                                    try {
                                        ui.onNodeWithText(invitation.name).assertIsDisplayed().performClick()
                                        waitFor("Group information could not be read or saved, or the action could not be completed. Your existing records are kept.")
                                        ui.onNodeWithText("Message").assertIsDisplayed().assertIsNotEnabled()
                                        check(peer.transactions.size==2 && peer.relayedMessages.size==2)
                                        captureNativeScreen(File(context.cacheDir,"group-chat-evidence/draft-unreadable.png"),"Group information could not be read or saved, or the action could not be completed. Your existing records are kept.")
                                    }catch(failure:Throwable){faultFailure=failure;throw failure}
                                    finally{cleanupAfterTest(faultFailure,{synchronized(SaitoLocalState.lock){record.writeBytes(retainedEncrypted)}})}
                                    ui.waitUntil(15_000){ui.onAllNodes(hasText("Refresh") and isEnabled()).fetchSemanticsNodes().size==1}
                                    ui.onNodeWithText("Refresh").assertIsDisplayed().assertIsEnabled().performClick()
                                    assertRestoredGroupDraft()
                                    ui.onNodeWithText("Group information could not be read or saved, or the action could not be completed. Your existing records are kept.").assertDoesNotExist()
                                    captureNativeScreen(File(context.cacheDir,"group-chat-evidence/draft-refresh-restored.png"),"Keep this unsent group draft")
                                }else ui.onNodeWithText(invitation.name).assertIsDisplayed().performClick()
                                assertRestoredGroupDraft()
                                check(peer.transactions.size==2 && peer.relayedMessages.size==2)
                            }
                            scenario.recreate()
                            waitFor("Groups")
                            ui.onNodeWithText("Groups").performClick()
                            waitFor(invitation.name)
                            captureNativeScreen(File(context.cacheDir,"group-chat-evidence/joined-reopened.png"),invitation.name)
                            if(paginating){
                                ui.onNodeWithText(invitation.name).assertIsDisplayed().performClick()
                                for(text in (if(rejectedPage) emptyList() else listOf("Recent group message 0","Recent group message 49"))+listOf("Ancient live group message","Available older group message"))waitForGroupMessage(text)
                                val saved=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,store.loadExisting(),SaitoBackend.OWNER).load(invitation.id))
                                check(saved.messages.size==(if(rejectedPage) 2 else 52) && olderReads.get()==1)
                                if(rejectedPage)check(saved.group.memberRoles[member]==ChatGroupRole.REMOVED)
                                captureNativeScreen(File(context.cacheDir,if(rejectedPage) "group-chat-evidence/rejected-page-cached.png" else "group-chat-evidence/pagination-cached.png"),"Available older group message")
                            }
                            if(messaging){
                                ui.onNodeWithText(invitation.name).assertIsDisplayed().performClick()
                                if(drafting)assertRestoredGroupDraft()
                                for(text in listOf(requireNotNull(messageReference).get("text").asString,"Native group hello","Live group message")){
                                    waitForGroupMessage(text)
                                }
                                if(archiveFault){
                                    ui.waitForGroupStatus("Older group history could not be loaded. Your cached messages are kept.")
                                    ui.onNodeWithText("Older group history could not be loaded. Your cached messages are kept.").assertIsDisplayed()
                                    for(text in listOf("Send while archive is unavailable","Live while archive is unavailable"))waitForGroupMessage(text)
                                    ui.onAllNodesWithText("Live while archive is unavailable").assertCountEquals(1)
                                    captureNativeScreen(File(context.cacheDir,"group-chat-evidence/archive-unavailable-cached.png"),"Live while archive is unavailable")
                                }
                                ui.onAllNodesWithText("Live group message").assertCountEquals(1)
                                val cached=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,store.loadExisting(),SaitoBackend.OWNER).load(invitation.id))
                                check(cached.group.memberRoles[member]==ChatGroupRole.MEMBER)
                                check(cached.group.memberRoles[invitation.admin]==ChatGroupRole.ADMIN)
                                waitForGroupMessage("Live group message")
                                captureNativeScreen(File(context.cacheDir,"group-chat-evidence/messages-cached.png"),"Live group message")
                                if(drafting){
                                    assertRestoredGroupDraft()
                                    captureNativeScreen(File(context.cacheDir,"group-chat-evidence/draft-reopened.png"),"Keep this unsent group draft")
                                }
                            }
                            val expected=if(archiveFault)3 else if(messaging)2 else 1
                            check(peer.transactions.size==expected && peer.relayedMessages.size==expected)
                            check(store.loadExisting().publicKey==fixture.publicKey && AndroidBackendStore(context).load()==SaitoBackend.OWNER)
                            peer.assertHealthy()
                            return@withFailureCapture
                        }
                        ui.onNodeWithText("Create group").assertIsDisplayed().performClick()
                        ui.onNodeWithText("Group name").performTextInput("Beta crew")
                        ui.onNodeWithText("Invitees (optional)").performTextInput(member)
                        ui.onNodeWithText("Create").assertIsEnabled().performClick()
                        waitFor("Beta crew")
                        waitFor("Forwarded. Delivery is not confirmed.")
                        ui.waitUntil(15_000) { peer.transactions.size == 1 && peer.relayedMessages.size == 1 }
                        val creation = SaitoEnvelope.decode(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single()))
                        check(postingTransaction(peer.transactions.single()).envelope() == SaitoEnvelope.fromSignedJson(peer.relayedMessages.single()))
                        check(creation.author == fixture.publicKey && creation.body.get("request").asString == "chat group")
                        val group = GroupChat.readCreation(SaitoEnvelope.fromSignedJson(peer.relayedMessages.single()))
                        check(group.name == "Beta crew" && group.memberRoles[member] == ChatGroupRole.MEMBER)
                        check(store.loadExisting().publicKey == fixture.publicKey)
                        captureNativeScreen(File(context.cacheDir,"group-chat-evidence/created.png"),"Beta crew")
                        ui.onNodeWithText("Members").assertIsDisplayed().performClick()
                        waitFor(member)
                        ui.onNodeWithText("Done").performClick()
                        ui.onNodeWithText("Invite").assertIsDisplayed().performClick()
                        waitFor("Invitation link")
                        val link = ui.onNodeWithContentDescription("Group invitation link").fetchSemanticsNode()
                            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
                        val parsed = GroupInvitation.parse(link)
                        check(parsed.id == group.id && parsed.name == group.name && parsed.sender == fixture.publicKey && parsed.admin == fixture.publicKey)
                        ui.onNodeWithText("Done").performClick()
                        if(leaving) {
                            ui.onNodeWithText("Leave group").assertIsDisplayed().assertIsEnabled().performClick()
                            waitFor("Leaving does not erase messages or copies held by other people.")
                            check(peer.transactions.size==1 && peer.relayedMessages.size==1)
                            ui.onNodeWithText("Leave").assertIsEnabled().performClick()
                            waitFor("You have left this group.")
                            waitFor("Forwarded. Delivery is not confirmed.")
                            ui.waitUntil(15_000){peer.transactions.size==2 && peer.relayedMessages.size==2}
                            val originalLeave=SaitoEnvelope.fromSignedJson(peer.relayedMessages.last())
                            check(postingTransaction(peer.transactions.last()).envelope()==originalLeave)
                            val departure=SaitoEnvelope.decode(originalLeave)
                            check(departure.author==fixture.publicKey && departure.body.get("request").asString=="chat remove")
                            check(departure.body.get("member_id").asString==fixture.publicKey && departure.body.get("group_id").asString==group.id)
                            check(departure.recipients==listOf(group.id,fixture.publicKey))
                            check(GroupChat.applyControl(group,originalLeave).memberRoles[fixture.publicKey]==ChatGroupRole.LEFT)
                            ui.onNodeWithText("Invite").assertDoesNotExist()
                            captureNativeScreen(File(context.cacheDir,"group-chat-evidence/left.png"),"You have left this group.")
                        }
                        scenario.recreate()
                        waitFor("Groups")
                        ui.onNodeWithText("Groups").performClick()
                        waitFor("Beta crew")
                        captureNativeScreen(File(context.cacheDir,"group-chat-evidence/reopened.png"),"Beta crew")
                        if(leaving){
                            ui.onNodeWithText("Beta crew").assertIsDisplayed().performClick()
                            waitFor("You have left this group.")
                            ui.onNodeWithText("Invite").assertDoesNotExist()
                            val restored=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,store.loadExisting(),SaitoBackend.OWNER).load(group.id))
                            val originalLeave=SaitoEnvelope.fromSignedJson(peer.relayedMessages.last())
                            check(restored.group.controls.last()==originalLeave)
                            check(requireNotNull(restored.candidate).envelope()==originalLeave)
                            check(restored.group.memberRoles[fixture.publicKey]==ChatGroupRole.LEFT)
                            captureNativeScreen(File(context.cacheDir,"group-chat-evidence/left-reopened.png"),"You have left this group.")
                        }
                        val expected=if(leaving)2 else 1
                        check(peer.transactions.size == expected && peer.relayedMessages.size == expected)
                        peer.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary=failure;throw failure }
        finally {
            cleanupAfterTest(primary,
                { synchronized(SaitoLocalState.lock) {
                    context.noBackupFilesDir.listFiles()!!.filter { it.name.startsWith(prefix) }.forEach { check(it.delete()) }
                    retained.forEach { (name,bytes) -> File(context.noBackupFilesDir,name).writeBytes(bytes) }
                } },
                { store.replace(store.loadExisting(),original) },
                { AndroidBackendStore(context).save(backend) },
                *preferences.map { (prefs,key,value) -> { check(prefs.edit().apply { if(value==null) remove(key) else putBoolean(key,value) }.commit());Unit } }.toTypedArray())
        }
    }


    @Test fun aLiveAddressedGroupAppearsWithoutSendingAndItsMessagesAndDraftSurviveReopening() = exerciseIncomingGroupUi()

    @Test fun incomingGroupStorageFailureStaysVisibleThroughReconnectAndRecoversWithoutSending() = exerciseIncomingGroupUi(storageFault=true)

    @Test fun accountBlockingHidesIncomingGroupMessagesAndMutingKeepsThemOnBothBackends() {
        for(backend in SaitoBackend.entries)exerciseIncomingGroupUi(personalPolicy=true,activeBackend=backend)
    }

    @Test fun explicitNamedGroupLikesToggleDistinctOriginalsAndSurviveDuplicatesAndReopening() =
        exerciseIncomingGroupUi(reacting=true)

    @Test fun importedGroupIdsRecoverAvailableSignedOriginsWithoutTrustingNamesOrGrantingUnavailableMembership() =
        exerciseIncomingGroupUi(importing=true)

    @Test fun unreadableImportedGroupHintsKeepWalletAndAuthenticatedCachedGroupsUsable() =
        exerciseIncomingGroupUi(brokenHints=true)

    private fun exerciseIncomingGroupUi(storageFault:Boolean=false,personalPolicy:Boolean=false,reacting:Boolean=false,importing:Boolean=false,brokenHints:Boolean=false,activeBackend:SaitoBackend=SaitoBackend.OWNER) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val walletStore=AndroidWalletStore(context)
        val original=walletStore.loadOrCreate()
        val backend=AndroidBackendStore(context).load()
        val key=SaitoWallet.fromPrivateKey("0".repeat(63)+"9") // Public unsafe fixtures only.
        val unavailable=SaitoWallet.fromPrivateKey("0".repeat(63)+"8")
        val base=SaitoWallet.fromPrivateKey("0".repeat(63)+"4")
        val fixture=when {
            importing -> base.withNamedChatGroup(key.publicKey,"Untrusted saved name",key)
                .withNamedChatGroup(unavailable.publicKey,"Another untrusted name")
            brokenHints -> SaitoWallet.fromBackup(com.google.gson.JsonParser.parseString(
                base.withNamedChatGroup(key.publicKey,"Incoming beta group",key).exportJson()).asJsonObject.apply{
                    getAsJsonObject("chat").getAsJsonArray("groups").add(com.google.gson.JsonNull.INSTANCE)
                }.toString())
            else -> base
        }
        val admin=SaitoWallet.fromPrivateKey("0".repeat(63)+"1")
        val creation=GroupChat.create(if(importing)fixture else admin,key.publicKey,"Incoming beta group",
            if(importing)listOf(admin.publicKey)else listOf(fixture.publicKey),1700000123456)
        val group=GroupChat.readCreation(creation.envelope())
        val message=GroupChat.message(admin,group,"Live incoming group history",1700000123457)
        val filename="groupchat.${activeBackend.id}.${fixture.publicKey}.${key.publicKey}.v1"
        val ownedNames=listOf(filename)+if(importing)listOf("groupchat.${activeBackend.id}.${fixture.publicKey}.${unavailable.publicKey}.v1")else emptyList()
        synchronized(SaitoLocalState.lock){
            check(processFixtureFiles(context,ownedNames.flatMap{listOf(it,"$it.bak","$it.new")}.toSet()).none{File(context.noBackupFilesDir,it).exists()})
        }
        val preferences=listOf("wallet-backup-reminders" to "hidden:${fixture.publicKey}","notification-permission" to "offered").map{(name,keyName)->
            val prefs=context.getSharedPreferences(name,0)
            Triple(prefs,keyName,if(prefs.contains(keyName))prefs.getBoolean(keyName,false)else null)
        }
        val discoveryReads=java.util.concurrent.atomic.AtomicInteger()
        val forgedBody=com.google.gson.JsonObject().apply{
            addProperty("module","Chat");addProperty("request","chat group")
            addProperty("id",unavailable.publicKey);addProperty("name","Forged imported group")
            addProperty("admin",admin.publicKey) // Does not match the actual signer.
        }
        val forged=SaitoTransaction.create(fixture,forgedBody,1700000123440)
        val peer=ControlledFeedPeer("[]",advertisedServices="archive||;relay||",archiveResponse={criteria->
            if(importing&&criteria.get("field1")?.asString=="Chat"){
                check(criteria.keySet()==setOf("request","field1","field3","limit","created_earlier_than"))
                check(criteria.get("field3").asString==fixture.publicKey&&criteria.get("limit").asInt==50)
                check(criteria.get("created_earlier_than").asLong>1700000123456)
                discoveryReads.incrementAndGet()
                com.google.gson.JsonArray().apply{for(tx in listOf(creation,forged))add(com.google.gson.JsonObject().apply{addProperty("tx",tx.envelope())})}.toString()
            }else if(criteria.has("field3")){
                check(criteria.keySet()==setOf("request","field3","limit","created_earlier_than"))
                check(criteria.get("field3").asString==group.id&&criteria.get("limit").asInt==50)
                check(criteria.get("created_earlier_than").asLong>1700000123457)
                "[]"
            }else{
                check(criteria.get("field1").asString=="RedSquare")
                check(criteria.get("field4").asString==""&&criteria.get("flagged_ne").asInt==1)
                "[]"
            }
        })
        val personalPrefix="personal.${fixture.publicKey}."
        val personalFiles=if(personalPolicy)synchronized(SaitoLocalState.lock){
            context.noBackupFilesDir.listFiles()!!.filter{it.name.startsWith(personalPrefix)}.associate{it.name to it.readBytes()}
        }else emptyMap()
        val controls=nl.davidengelen.redsquare.moderation.AndroidPersonalControls(context,fixture.publicKey)
        val reactionOriginals=mutableListOf<String>()
        var primary:Throwable?=null
        try {
            if(personalPolicy)controls.save(controls.load(),PersonalPolicy(blocked=setOf(admin.publicKey)))
            walletStore.replace(original,fixture)
            AndroidBackendStore(context).save(activeBackend)
            preferences.forEach{(prefs,keyName,_)->check(prefs.edit().putBoolean(keyName,true).commit())}
            if(brokenHints)nl.davidengelen.redsquare.chat.GroupChatStore(context,fixture,activeBackend).receive(creation.envelope())
            peer.start().use{server->
                ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)
                    .putExtra("feedPeerUrl","ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl","ws://127.0.0.1:${server.port}/wsopen")).use{scenario->
                    withFailureCapture({captureNativeScreen(File(context.cacheDir,if(brokenHints) "group-chat-evidence/hints-failure.png" else if(importing) "group-chat-evidence/imported-failure.png" else if(reacting) "group-chat-evidence/reaction-failure.png" else if(personalPolicy) "group-chat-evidence/personal-${activeBackend.id}-failure.png" else if(storageFault) "group-chat-evidence/incoming-storage-failure.png" else "group-chat-evidence/incoming-ui-failure.png"))}){
                        waitFor("Chat")
                        ui.waitUntil(15_000){ui.onAllNodes(hasText("Chat") and isEnabled()).fetchSemanticsNodes().size==1}
                        ui.onNodeWithText("Chat").assertIsDisplayed().assertIsEnabled().performClick()
                        waitFor("Groups");ui.onNodeWithText("Groups").assertIsDisplayed().performClick()
                        waitFor("Create group")
                        ui.waitUntil(15_000){ui.onAllNodes(hasText("Create group") and isEnabled()).fetchSemanticsNodes().size==1}
                        ui.onNodeWithText("Create group").assertIsDisplayed().assertIsEnabled()
                        var nextRequestId=800
                        fun deliver(transaction:SaitoTransaction,acknowledgement:Byte=13){
                            val requestId=nextRequestId++
                            fun matches(frame:ByteArray)=frame[0]==acknowledgement&&java.nio.ByteBuffer.wrap(frame,1,4).int==requestId
                            val before=peer.incomingAcknowledgements.count(::matches)
                            val bytes=transaction.applicationRequest(1).let{it.copyOfRange(5,it.size)}
                            var lastDelivery=0L
                            ui.waitUntil(15_000){
                                val now=android.os.SystemClock.elapsedRealtime()
                                if(now-lastDelivery>=500){peer.sendIncoming(bytes,requestId);lastDelivery=now}
                                peer.incomingAcknowledgements.count(::matches)>before
                            }
                        }
                        if(storageFault){
                            val unfinished=File(context.noBackupFilesDir,"$filename.new")
                            val bytes=byteArrayOf(1,2,3)
                            synchronized(SaitoLocalState.lock){check(!unfinished.exists());unfinished.writeBytes(bytes)}
                            val successful=peer.incomingAcknowledgements.count{it[0]==13.toByte()}
                            deliver(creation,14)
                            val error="Group information could not be read or saved, or the action could not be completed. Your existing records are kept."
                            waitFor(error);ui.onNodeWithText(error).assertIsDisplayed()
                            check(peer.incomingAcknowledgements.count{it[0]==13.toByte()}==successful)
                            synchronized(SaitoLocalState.lock){
                                check(unfinished.readBytes().contentEquals(bytes))
                                check(!File(context.noBackupFilesDir,filename).exists()&&!File(context.noBackupFilesDir,"$filename.bak").exists())
                            }
                            check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
                            captureNativeScreen(File(context.cacheDir,"group-chat-evidence/incoming-storage-unavailable.png"),error)
                            val interrupted=peer.disconnectAuthenticatedSockets()
                            check(interrupted.isNotEmpty())
                            ui.waitUntil(15_000){
                                val connected=peer.authenticatedConnectionIds
                                connected.intersect(interrupted).isEmpty()&&connected.any{it !in interrupted}
                            }
                            ui.onNodeWithText(error).assertIsDisplayed()
                            synchronized(SaitoLocalState.lock){check(unfinished.delete())}
                            deliver(creation) // Requires the passive receiver to reconnect without Refresh.
                            waitFor(group.name)
                            ui.onNodeWithText(error).assertIsDisplayed()
                            ui.onNodeWithText("Refresh").assertIsDisplayed().assertIsEnabled().performClick()
                            ui.waitUntil(15_000){ui.onAllNodes(hasText(error)).fetchSemanticsNodes().isEmpty()}
                            ui.onNodeWithText(error).assertDoesNotExist()
                            val unknownKey=SaitoWallet.fromPrivateKey("0".repeat(63)+"8") // Public unsafe fixture only.
                            val unknown=GroupChat.create(admin,unknownKey.publicKey,"Unknown group",listOf(fixture.publicKey),1700000123456)
                            val unsupported=GroupChat.message(admin,GroupChat.readCreation(unknown.envelope()),"Unknown group traffic",1700000123457)
                            deliver(unsupported,14)
                            ui.onNodeWithText(error).assertDoesNotExist()
                            captureNativeScreen(File(context.cacheDir,"group-chat-evidence/incoming-storage-recovered.png"),group.name)
                        }else if(!importing&&!brokenHints)deliver(creation)
                        waitFor(group.name)
                        if(brokenHints){
                            val notice="Some saved group information could not be read. Your wallet and cached groups are kept."
                            waitFor(notice);ui.onNodeWithText(notice).assertIsDisplayed()
                            check(walletStore.loadExisting().exportJson()==fixture.exportJson())
                            captureNativeScreen(File(context.cacheDir,"group-chat-evidence/hints-unreadable.png"),notice)
                        }
                        if(importing){
                            // Wait for automatic watch metadata to reach protected storage before
                            // asserting the discovery view after its wallet replacement.
                            ui.waitUntil(15_000){runCatching{
                                val options=com.google.gson.JsonParser.parseString(walletStore.loadExisting().exportJson()).asJsonObject
                                val watch=options.getAsJsonArray("keys").single{
                                    it.asJsonObject.get("publicKey")?.asString==key.publicKey
                                }.asJsonObject
                                watch.get("identifier").asString=="Incoming beta group"
                            }.getOrDefault(false)}
                            scenario.recreate()
                            waitFor("Groups");ui.onNodeWithText("Groups").performClick()
                            waitFor(group.name)
                            check(discoveryReads.get()>0)
                            waitFor(unavailable.publicKey)
                            ui.onNodeWithText(unavailable.publicKey).assertIsDisplayed()
                            ui.waitUntil(15_000){runCatching{
                                ui.onNodeWithText("Group information is unavailable on this server. Use an invitation link to rejoin.").assertIsDisplayed()
                            }.isSuccess}
                            ui.onNodeWithText("Group information is unavailable on this server. Use an invitation link to rejoin.").assertIsDisplayed()
                            ui.onNodeWithText("Untrusted saved name").assertDoesNotExist()
                            ui.onNodeWithText("Another untrusted name").assertDoesNotExist()
                            ui.onNodeWithText("Forged imported group").assertDoesNotExist()
                            check(nl.davidengelen.redsquare.chat.GroupChatStore(context,walletStore.loadExisting(),activeBackend).load(unavailable.publicKey)==null)
                            captureNativeScreen(File(context.cacheDir,"group-chat-evidence/imported-hints.png"),unavailable.publicKey)
                        }
                        ui.onNodeWithText(group.name).assertIsDisplayed().performClick()
                        val received=if(importing)"Recovered signed group information." else "Received a signed group invitation."
                        waitFor(received)
                        ui.onNodeWithText(received).assertIsDisplayed()
                        ui.onNodeWithText("Retry").assertDoesNotExist()
                        check(peer.transactions.isEmpty()&&peer.relayedMessages.isEmpty())
                        deliver(message)
                        if(personalPolicy){
                            // Reload the positively acknowledged cached message before checking its visibility.
                            scenario.recreate();waitFor("Groups");ui.onNodeWithText("Groups").performClick()
                            waitFor(group.name);ui.onNodeWithText(group.name).performClick()
                            ui.waitUntil(15_000){ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().size==1}
                            ui.onNodeWithText("Received a signed group invitation.").assertIsDisplayed()
                            ui.onNodeWithText("Live incoming group history").assertDoesNotExist()
                            val saved=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,fixture,activeBackend).load(group.id))
                            check(saved.messages.single().id==message.id)
                            captureNativeScreen(File(context.cacheDir,"group-chat-evidence/personal-${activeBackend.id}-blocked.png"),"Received a signed group invitation.")
                            controls.save(controls.load(),PersonalPolicy(muted=setOf(admin.publicKey)))
                            scenario.recreate();waitFor("Groups");ui.onNodeWithText("Groups").performClick()
                            waitFor(group.name);ui.onNodeWithText(group.name).performClick()
                        }
                        waitForGroupMessage("Live incoming group history")
                        if(storageFault)ui.onNodeWithText("Group information could not be read or saved, or the action could not be completed. Your existing records are kept.").assertDoesNotExist()
                        if(reacting){
                            fun saved()=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,walletStore.loadExisting(),activeBackend).load(group.id))
                            val target=GroupChat.readMessage(group,message.envelope())
                            waitFor("Like")
                            ui.waitUntil(15_000){ui.onAllNodes(hasText("Like") and isEnabled()).fetchSemanticsNodes().size==1}
                            ui.onNodeWithText("Like").assertIsDisplayed().performClick()
                            waitFor("1 like")
                            ui.waitUntil(15_000){ui.onAllNodes(hasText("Unlike") and isEnabled()).fetchSemanticsNodes().size==1}
                            waitFor("Forwarded. Delivery is not confirmed.")
                            val first=requireNotNull(saved().candidate)
                            reactionOriginals.add(first.envelope())
                            check(GroupChat.replay(saved().group,saved().originals).likedBy(target)==setOf(fixture.publicKey))
                            ui.waitUntil(15_000){peer.transactions.size==1&&peer.relayedMessages.size==1}
                            deliver(first) // An echoed original must not toggle its own like a second time.
                            ui.onNodeWithText("1 like").assertIsDisplayed()
                            captureNativeScreen(File(context.cacheDir,"group-chat-evidence/reaction-liked.png"),"1 like")
                            ui.onNodeWithText("Unlike").assertIsEnabled().performClick()
                            ui.waitUntil(15_000){ui.onAllNodes(hasText("Like") and isEnabled()).fetchSemanticsNodes().size==1}
                            ui.onNodeWithText("1 like").assertDoesNotExist()
                            val second=requireNotNull(saved().candidate)
                            check(first.id!=second.id)
                            reactionOriginals.add(second.envelope())
                            check(GroupChat.replay(saved().group,saved().originals).likedBy(target).isEmpty())
                            ui.waitUntil(15_000){peer.transactions.size==2&&peer.relayedMessages.size==2}
                            for((index,originalLike) in reactionOriginals.withIndex()){
                                val signed=SaitoEnvelope.decode(originalLike)
                                check(signed.author==fixture.publicKey&&signed.body.get("request").asString=="chat like")
                                check(signed.body.get("sender").asString==fixture.publicKey&&signed.body.get("group_id").asString==group.id)
                                check(signed.body.get("signature").asString==message.id&&signed.body.get("mentioned").asString==admin.publicKey)
                                check(signed.recipients==listOf(fixture.publicKey,admin.publicKey,group.id))
                                check(postingTransaction(peer.transactions[index]).envelope()==originalLike)
                                check(SaitoEnvelope.fromSignedJson(peer.relayedMessages[index])==originalLike)
                                check(peer.relayRecipients[index]==listOf(fixture.publicKey,admin.publicKey))
                            }
                        }
                        ui.waitUntil(15_000){ui.onAllNodes(hasSetTextAction() and isEnabled()).fetchSemanticsNodes().size==1}
                        ui.onNodeWithText("Message").assertIsEnabled().performTextInput("Keep incoming group draft")
                        androidx.test.espresso.Espresso.closeSoftKeyboard()
                        ui.waitUntil(15_000){runCatching{ui.onNodeWithText("Back").assertIsDisplayed()}.isSuccess}
                        ui.onNodeWithText("Back").assertIsDisplayed().performClick()
                        waitFor("Create group")
                        scenario.recreate();waitFor("Groups");ui.onNodeWithText("Groups").performClick()
                        waitFor(group.name);ui.onNodeWithText(group.name).assertIsDisplayed().performClick()
                        waitForGroupMessage("Live incoming group history")
                        assertRestoredGroupDraft("Keep incoming group draft")
                        val saved=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,walletStore.loadExisting(),activeBackend).load(group.id))
                        check(saved.group.creationEnvelope?.let{SaitoEnvelope.decode(it).id}==creation.id)
                        check(saved.messages.single().id==message.id && saved.draft=="Keep incoming group draft")
                        if(reacting){
                            check(saved.status==nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
                            check(saved.candidate?.envelope()==reactionOriginals.last())
                            val replay=GroupChat.replay(saved.group,saved.originals)
                            check(replay.reactions.map{it.envelope}==reactionOriginals)
                            check(replay.likedBy(saved.messages.single()).isEmpty())
                            ui.onNodeWithText("Like").assertIsEnabled()
                            ui.onNodeWithText("1 like").assertDoesNotExist()
                        }else check(saved.candidate==null)
                        if(importing){
                            check(saved.group.memberRoles[fixture.publicKey]==ChatGroupRole.ADMIN)
                            val updated=walletStore.loadExisting()
                            check(updated.namedChatGroupIds()==listOf(key.publicKey,unavailable.publicKey))
                            val expected=fixture.withNamedChatGroup(key.publicKey,"Incoming beta group")
                            check(updated.exportJson()==expected.exportJson()) // Original group key/options stay protected and intact.
                        }
                        if(brokenHints)check(walletStore.loadExisting().exportJson()==fixture.exportJson())
                        check(walletStore.loadExisting().publicKey==fixture.publicKey&&AndroidBackendStore(context).load()==activeBackend)
                        val outgoing=if(reacting)2 else 0
                        check(peer.transactions.size==outgoing&&peer.relayedMessages.size==outgoing)
                        captureNativeScreen(File(context.cacheDir,if(brokenHints) "group-chat-evidence/hints-reopened.png" else if(importing) "group-chat-evidence/imported-reopened.png" else if(reacting) "group-chat-evidence/reaction-unliked-reopened.png" else if(personalPolicy) "group-chat-evidence/personal-${activeBackend.id}-muted-reopened.png" else if(storageFault) "group-chat-evidence/incoming-storage-reopened.png" else "group-chat-evidence/incoming-ui-reopened.png"),"Keep incoming group draft")
                        peer.assertHealthy()
                    }
                }
            }
        }catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,
            {if(personalPolicy)synchronized(SaitoLocalState.lock){
                context.noBackupFilesDir.listFiles()!!.filter{it.name.startsWith(personalPrefix)}.forEach{check(it.delete())}
                personalFiles.forEach{(name,bytes)->File(context.noBackupFilesDir,name).writeBytes(bytes)}
            }},
            {synchronized(SaitoLocalState.lock){ownedNames.flatMap{listOf(it,"$it.bak","$it.new")}.forEach{val file=File(context.noBackupFilesDir,it);check(!file.exists()||file.delete())}}},
            {walletStore.replace(walletStore.loadExisting(),original)},
            {AndroidBackendStore(context).save(backend)},
            *preferences.map{(prefs,keyName,value)->{check(prefs.edit().apply{if(value==null)remove(keyName)else putBoolean(keyName,value)}.commit());Unit}}.toTypedArray())}
    }

    @Test fun anAddressedSignedCreationIsRetainedWithoutInventingAnOutgoingAttempt() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallet=AndroidWalletStore(context).loadOrCreate()
        val backend=AndroidBackendStore(context).load()
        val admin=listOf("1","2").map{SaitoWallet.fromPrivateKey("0".repeat(63)+it)}
            .first{it.publicKey!=wallet.publicKey} // Public unsafe fixtures only.
        val key=listOf("9","8").map{SaitoWallet.fromPrivateKey("0".repeat(63)+it)}
            .first{it.publicKey!=wallet.publicKey && it.publicKey!=admin.publicKey}
        val creation=GroupChat.create(admin,key.publicKey,"Incoming native group",listOf(wallet.publicKey),1700000123456)
        val filename="groupchat.${backend.id}.${wallet.publicKey}.${key.publicKey}.v1"
        val groupStore=nl.davidengelen.redsquare.chat.GroupChatStore(context,wallet,backend)
        synchronized(SaitoLocalState.lock){
            check(processFixtureFiles(context,setOf(filename,"$filename.bak","$filename.new")).none{File(context.noBackupFilesDir,it).exists()})
        }
        check(groupStore.load(key.publicKey)==null)
        val before=wallet.exportJson()
        var primary:Throwable?=null
        try {
            val unfinished=File(context.noBackupFilesDir,"$filename.new")
            val unfinishedBytes=byteArrayOf(1,2,3)
            synchronized(SaitoLocalState.lock){unfinished.writeBytes(unfinishedBytes)}
            check(runCatching{groupStore.receive(creation.envelope())}.isFailure)
            synchronized(SaitoLocalState.lock){
                check(unfinished.readBytes().contentEquals(unfinishedBytes))
                check(!File(context.noBackupFilesDir,filename).exists()&&!File(context.noBackupFilesDir,"$filename.bak").exists())
                check(unfinished.delete())
            }
            groupStore.receive(creation.envelope())
            val received=requireNotNull(groupStore.load(key.publicKey))
            check(received.group.creationEnvelope==creation.envelope())
            check(received.group.memberRoles[wallet.publicKey]==ChatGroupRole.MEMBER)
            check(received.group.memberRoles[admin.publicKey]==ChatGroupRole.ADMIN)
            check(received.candidate==null && received.creationKey==null && received.messages.isEmpty())
            check(!nl.davidengelen.redsquare.chat.GroupChatStore.hasWork(context,wallet,backend))
            groupStore.receive(" "+creation.envelope()) // Formatting cannot replace the same authenticated original.
            check(groupStore.entries().count{it.group.id==key.publicKey}==1)
            check(AndroidWalletStore(context).loadExisting().exportJson()==before)
            val unaddressed=GroupChat.create(admin,key.publicKey,"Unaddressed conflicting creation",emptyList(),1700000123457)
            check(runCatching{groupStore.receive(unaddressed.envelope())}.isFailure)
            check(groupStore.load(key.publicKey)?.group?.creationEnvelope==creation.envelope())
        }catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,{synchronized(SaitoLocalState.lock){
            processFixtureFiles(context,setOf(filename,"$filename.bak","$filename.new")).forEach{val file=File(context.noBackupFilesDir,it);check(!file.exists()||file.delete())}
        }})}
    }

    @Test fun savedUncertainGroupWorkIsIncludedInOutgoingWalletWorkAcrossBackends() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallet=AndroidWalletStore(context).loadOrCreate()
        val selected=AndroidBackendStore(context).load()
        val groupKey=SaitoWallet.create()
        val candidate=GroupChat.create(wallet,groupKey.publicKey,"Pending group",emptyList(),1700000123456)
        val prefix="groupchat.${selected.id}.${wallet.publicKey}.${groupKey.publicKey}.v1"
        var primary:Throwable?=null
        try {
            val store=nl.davidengelen.redsquare.chat.GroupChatStore(context,wallet,selected)
            store.prepareCreation(candidate)
            check(nl.davidengelen.redsquare.chat.GroupChatStore.hasWork(context,wallet,selected))
            val other=SaitoBackend.entries.single{it!=selected}
            check(!nl.davidengelen.redsquare.chat.GroupChatStore.hasWork(context,wallet,other))
            AndroidBackendStore(context).save(other)
            val outgoing=SaitoBackend.entries.any { nl.davidengelen.redsquare.chat.GroupChatStore.hasWork(context,wallet,it) }
            check(outgoing)
            check(nl.davidengelen.redsquare.chat.GroupChatStore.hasWork(context,wallet,selected))
            store.record(groupKey.publicKey,candidate.id,nl.davidengelen.redsquare.chat.GroupChatStore.Status.SUBMITTED)
            check(nl.davidengelen.redsquare.chat.GroupChatStore.hasWork(context,wallet,selected))
            store.record(groupKey.publicKey,candidate.id,nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
            check(!nl.davidengelen.redsquare.chat.GroupChatStore.hasWork(context,wallet,selected))
            // Fault injection at the filesystem boundary; corrupt or unfinished work cannot suppress disclosure.
            val unfinished=File(context.noBackupFilesDir,"$prefix.new")
            unfinished.writeBytes(byteArrayOf(1))
            check(nl.davidengelen.redsquare.chat.GroupChatStore.hasWork(context,wallet,selected))
            check(unfinished.delete())
            File(context.noBackupFilesDir,prefix).writeBytes(byteArrayOf(1))
            check(nl.davidengelen.redsquare.chat.GroupChatStore.hasWork(context,wallet,selected))
            val unrelated=SaitoWallet.create()
            check(!nl.davidengelen.redsquare.chat.GroupChatStore.hasWork(context,unrelated,selected))
            check(AndroidWalletStore(context).loadExisting().exportJson()==wallet.exportJson())
        } catch(failure:Throwable){primary=failure;throw failure}
        finally {cleanupAfterTest(primary,{AndroidBackendStore(context).save(selected)},{synchronized(SaitoLocalState.lock){
            processFixtureFiles(context,setOf(prefix,"$prefix.bak","$prefix.new")).forEach{val file=File(context.noBackupFilesDir,it);check(!file.exists()||file.delete())}
        }})}
    }


    @Test fun creationKeySurvivesReopeningBeforeWalletMetadataCanBePublished() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallet=AndroidWalletStore(context).loadOrCreate()
        val backend=AndroidBackendStore(context).load()
        val groupKey=SaitoWallet.create()
        val candidate=GroupChat.create(wallet,groupKey.publicKey,"Interrupted metadata",emptyList(),1700000123456)
        val filename="groupchat.${backend.id}.${wallet.publicKey}.${groupKey.publicKey}.v1"
        var primary:Throwable?=null
        try {
            nl.davidengelen.redsquare.chat.GroupChatStore(context,wallet,backend).prepareCreation(candidate,groupKey)
            val recovered=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,wallet,backend).load(groupKey.publicKey))
            check(recovered.creationKey?.publicKey==groupKey.publicKey)
            check(recovered.creationKey?.exportJson()==groupKey.exportJson())
            check(requireNotNull(recovered.candidate).encoded()==candidate.encoded())
            check(recovered.status==nl.davidengelen.redsquare.chat.GroupChatStore.Status.UNKNOWN)
            check(AndroidWalletStore(context).loadExisting().exportJson()==wallet.exportJson())
        } catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,{synchronized(SaitoLocalState.lock){
            processFixtureFiles(context,setOf(filename,"$filename.bak","$filename.new")).forEach{val file=File(context.noBackupFilesDir,it);check(!file.exists()||file.delete())}
        }})}
    }

    @Test fun rejoiningAKnownGroupRetainsItsOriginalAuthorityKeyAndDeparture() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName=="nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val wallet=AndroidWalletStore(context).loadOrCreate()
        val backend=AndroidBackendStore(context).load()
        val groupKey=SaitoWallet.create()
        val member=SaitoWallet.fromPrivateKey("0".repeat(63)+"2").publicKey // Public unsafe fixture only.
        val outsider=SaitoWallet.fromPrivateKey("0".repeat(63)+"4").publicKey
        val creation=GroupChat.create(wallet,groupKey.publicKey,"Known group",listOf(member),1700000123456)
        val departure=GroupChat.leave(wallet,groupKey.publicKey,1700000123457)
        val invitation=GroupInvitation.parse(java.util.Base64.getEncoder().encodeToString(com.google.gson.JsonObject().apply{
            addProperty("id",groupKey.publicKey);addProperty("name","Untrusted renamed group")
            addProperty("sender",member);addProperty("admin",outsider)
        }.toString().toByteArray(Charsets.UTF_8)))
        var join=GroupChat.join(wallet,invitation,1700000123458)
        val filename="groupchat.${backend.id}.${wallet.publicKey}.${groupKey.publicKey}.v1"
        var primary:Throwable?=null
        try {
            val store=nl.davidengelen.redsquare.chat.GroupChatStore(context,wallet,backend)
            store.prepareCreation(creation,groupKey)
            store.record(groupKey.publicKey,creation.id,nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
            store.prepareLeave(departure)
            store.record(groupKey.publicKey,departure.id,nl.davidengelen.redsquare.chat.GroupChatStore.Status.FORWARDED)
            val effective=invitation.usingKnownGroup(requireNotNull(store.load(groupKey.publicKey)).group)
            join=GroupChat.join(wallet,effective,1700000123458)
            store.prepareInvitation(effective,join)
            val reopened=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,wallet,backend).load(groupKey.publicKey))
            check(reopened.group.creationEnvelope==creation.envelope() && reopened.group.confirmedInvitation==null)
            check(reopened.group.name=="Known group" && reopened.creationKey?.exportJson()==groupKey.exportJson())
            check(reopened.group.memberRoles[wallet.publicKey]==ChatGroupRole.MEMBER)
            check(outsider !in reopened.group.memberRoles)
            check(reopened.group.controls==listOf(departure.envelope(),join.envelope()))
            check(requireNotNull(reopened.candidate).encoded()==join.encoded() && reopened.status==nl.davidengelen.redsquare.chat.GroupChatStore.Status.UNKNOWN)
            check(runCatching{store.prepareInvitation(effective,GroupChat.join(wallet,effective,1700000123459))}.isFailure)
            check(store.load(groupKey.publicKey)?.candidate?.id==join.id)
            check(AndroidWalletStore(context).loadExisting().exportJson()==wallet.exportJson())
        } catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,{synchronized(SaitoLocalState.lock){
            processFixtureFiles(context,setOf(filename,"$filename.bak","$filename.new")).forEach{val file=File(context.noBackupFilesDir,it);check(!file.exists()||file.delete())}
        }})}
    }

    private fun assertRestoredGroupDraft(expected:String="Keep this unsent group draft") {
        ui.waitUntil(15_000){
            runCatching {
                ui.onNodeWithText("Message").fetchSemanticsNode().config[
                    androidx.compose.ui.semantics.SemanticsProperties.EditableText].text==expected
            }.getOrDefault(false)
        }
        ui.onNodeWithText("Message").assertIsDisplayed().assertIsEnabled().assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                androidx.compose.ui.text.AnnotatedString(expected)))
    }

    private fun sendMessage() {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.waitUntil(15_000){runCatching{ui.onNodeWithText("Send").assertIsDisplayed().assertIsEnabled()}.isSuccess}
        ui.onNodeWithText("Send").assertIsDisplayed().assertIsEnabled().performClick()
    }

    private fun waitForGroupMessage(text:String) {
        ui.waitUntil(15_000){
            runCatching {
                ui.onNodeWithContentDescription("Group messages").performScrollToNode(hasText(text))
                ui.onNodeWithText(text).assertIsDisplayed()
            }.isSuccess
        }
        ui.onNodeWithText(text).assertIsDisplayed()
    }

    private fun waitFor(text: String) = ui.waitUntil(15_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}
