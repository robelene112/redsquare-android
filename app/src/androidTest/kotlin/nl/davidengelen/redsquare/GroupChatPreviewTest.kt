package nl.davidengelen.redsquare

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import nl.davidengelen.redsquare.saito.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class GroupChatPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun aNamedGroupCanBeCreatedWithAnInvitationAndReadAfterReopening() = exerciseGroupUi()

    @Test fun anExistingMemberInvitationCanBeConfirmedOnTheSelectedBackendAndReadAfterReopening() = exerciseGroupUi(joining=true)

    @Test fun leavingANamedGroupRetainsTheSignedDepartureAfterReopening() = exerciseGroupUi(leaving=true)

    @Test fun anInterruptedCreationCanRetryItsSavedOriginalAndRecoverKeyMetadataWithoutAutomaticSending() = exerciseGroupUi(retrying=true)

    @Test fun aKnownGroupRejoinPreviewsAndRoutesToItsRetainedAdministrator() = exerciseGroupUi(knownRejoin=true)

    @Test fun groupMessagesCombineAvailableHistoryLiveDeliveryAndPersistentCacheWithoutDuplicateSending() = exerciseGroupUi(joining=true,messaging=true)

    @Test fun anUnsentGroupDraftSurvivesBackNavigationAndReopeningWithoutSending() = exerciseGroupUi(joining=true,messaging=true,drafting=true)

    @Test fun unavailableGroupArchiveKeepsCachedMessagesLiveDeliveryAndSendingUsable() = exerciseGroupUi(joining=true,messaging=true,archiveFault=true)

    private fun exerciseGroupUi(joining:Boolean=false, leaving:Boolean=false, retrying:Boolean=false, knownRejoin:Boolean=false, messaging:Boolean=false, drafting:Boolean=false, archiveFault:Boolean=false) {
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
        val messageReference=if(messaging)InstrumentationRegistry.getInstrumentation().context.assets.open("native-group-chat-message-reference.json")
            .bufferedReader().use{com.google.gson.JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject} else null
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { criteria ->
            if(brokenHistory.get() && criteria.get("field3")?.asString==messageReference?.get("groupId")?.asString){
                failedHistoryReads.incrementAndGet();"invalid group archive response"
            }else if(messageReference!=null && historyAvailable.get() && criteria.get("field3")?.asString==messageReference.get("groupId").asString){
                com.google.gson.JsonArray().apply{add(com.google.gson.JsonObject().apply{
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
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir,if(archiveFault) "group-chat-evidence/archive-failure.png" else if(drafting) "group-chat-evidence/draft-failure.png" else if(messaging) "group-chat-evidence/message-failure.png" else if(knownRejoin) "group-chat-evidence/known-rejoin-failure.png" else if(joining) "group-chat-evidence/join-failure.png" else if(leaving) "group-chat-evidence/leave-failure.png" else if(retrying) "group-chat-evidence/retry-failure.png" else "group-chat-evidence/create-failure.png")) }) {
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
                            waitFor("Status unknown. Your signed action is saved on this device.")
                            check(peer.transactions.isEmpty() && peer.relayedMessages.isEmpty())
                            check(store.loadExisting().exportJson()==fixture.exportJson())
                            peer.disconnectAfterTransaction=true
                            ui.onNodeWithText("Retry").assertIsDisplayed().assertIsEnabled().performClick()
                            waitFor("Group information could not be saved or sent. Your existing records are kept.")
                            waitFor("Submitted. Delivery is not confirmed.")
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
                            waitFor("Submitted. Delivery is not confirmed.")
                            check(peer.transactions.size==1 && peer.relayedMessages.isEmpty())
                            val recovered=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,updated,SaitoBackend.OWNER).load(checkpointKey.publicKey))
                            check(recovered.candidate.encoded()==checkpoint.encoded() && recovered.creationKey?.exportJson()==checkpointKey.exportJson())
                            peer.disconnectAfterTransaction=false
                            ui.onNodeWithText("Retry").assertIsEnabled().performClick()
                            waitFor("Forwarded. Delivery is not confirmed.")
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
                            waitFor("Invitation details are provided by the sender.")
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
                            if(messaging){
                                val available=requireNotNull(messageReference)
                                waitFor(available.get("text").asString)
                                ui.onNodeWithText(available.get("text").asString).assertIsDisplayed()
                                ui.onNodeWithText("Message").performTextInput("Native group hello")
                                ui.onNodeWithText("Send").assertIsEnabled().performClick()
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
                                ui.onNodeWithText("Refresh").assertIsDisplayed().performClick()
                                ui.waitUntil(15_000){failedHistoryReads.get()>0}
                                waitFor("Older group history could not be loaded. Your cached messages are kept.")
                                ui.waitUntil(15_000){ui.onAllNodes(hasText("Message") and isEnabled()).fetchSemanticsNodes().size==1}
                                ui.onNodeWithText("Message").assertIsEnabled().performTextInput("Send while archive is unavailable")
                                ui.waitUntil(15_000){ui.onAllNodes(hasText("Send") and isEnabled()).fetchSemanticsNodes().size==1}
                                ui.onNodeWithText("Send").assertIsEnabled().performClick()
                                ui.waitUntil(15_000){peer.transactions.size==3&&peer.relayedMessages.size==3}
                                waitFor("Forwarded. Delivery is not confirmed.")
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
                                ui.onNodeWithText(invitation.name).assertIsDisplayed().performClick()
                                assertRestoredGroupDraft()
                                check(peer.transactions.size==2 && peer.relayedMessages.size==2)
                            }
                            scenario.recreate()
                            waitFor("Groups")
                            ui.onNodeWithText("Groups").performClick()
                            waitFor(invitation.name)
                            captureNativeScreen(File(context.cacheDir,"group-chat-evidence/joined-reopened.png"),invitation.name)
                            if(messaging){
                                ui.onNodeWithText(invitation.name).assertIsDisplayed().performClick()
                                for(text in listOf(requireNotNull(messageReference).get("text").asString,"Native group hello","Live group message")){
                                    waitForGroupMessage(text)
                                }
                                if(archiveFault){
                                    waitFor("Older group history could not be loaded. Your cached messages are kept.")
                                    for(text in listOf("Send while archive is unavailable","Live while archive is unavailable"))waitForGroupMessage(text)
                                    ui.onAllNodesWithText("Live while archive is unavailable").assertCountEquals(1)
                                    captureNativeScreen(File(context.cacheDir,"group-chat-evidence/archive-unavailable-cached.png"),"Live while archive is unavailable")
                                }
                                ui.onAllNodesWithText("Live group message").assertCountEquals(1)
                                val cached=requireNotNull(nl.davidengelen.redsquare.chat.GroupChatStore(context,store.loadExisting(),SaitoBackend.OWNER).load(invitation.id))
                                check(cached.group.memberRoles[member]==ChatGroupRole.MEMBER)
                                check(cached.group.memberRoles[invitation.admin]==ChatGroupRole.ADMIN)
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
                            check(restored.candidate.envelope()==originalLeave)
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
            context.noBackupFilesDir.listFiles()!!.filter{it.name==prefix||it.name=="$prefix.bak"||it.name=="$prefix.new"}.forEach{check(it.delete())}
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
            check(recovered.candidate.encoded()==candidate.encoded())
            check(recovered.status==nl.davidengelen.redsquare.chat.GroupChatStore.Status.UNKNOWN)
            check(AndroidWalletStore(context).loadExisting().exportJson()==wallet.exportJson())
        } catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,{synchronized(SaitoLocalState.lock){
            context.noBackupFilesDir.listFiles()!!.filter{it.name==filename||it.name=="$filename.bak"||it.name=="$filename.new"}.forEach{check(it.delete())}
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
            check(reopened.candidate.encoded()==join.encoded() && reopened.status==nl.davidengelen.redsquare.chat.GroupChatStore.Status.UNKNOWN)
            check(runCatching{store.prepareInvitation(effective,GroupChat.join(wallet,effective,1700000123459))}.isFailure)
            check(store.load(groupKey.publicKey)?.candidate?.id==join.id)
            check(AndroidWalletStore(context).loadExisting().exportJson()==wallet.exportJson())
        } catch(failure:Throwable){primary=failure;throw failure}
        finally{cleanupAfterTest(primary,{synchronized(SaitoLocalState.lock){
            context.noBackupFilesDir.listFiles()!!.filter{it.name==filename||it.name=="$filename.bak"||it.name=="$filename.new"}.forEach{check(it.delete())}
        }})}
    }

    private fun assertRestoredGroupDraft() {
        ui.waitUntil(15_000){
            runCatching {
                ui.onNodeWithText("Message").fetchSemanticsNode().config[
                    androidx.compose.ui.semantics.SemanticsProperties.EditableText].text=="Keep this unsent group draft"
            }.getOrDefault(false)
        }
        ui.onNodeWithText("Message").assertIsDisplayed().assertIsEnabled().assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                androidx.compose.ui.text.AnnotatedString("Keep this unsent group draft")))
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
