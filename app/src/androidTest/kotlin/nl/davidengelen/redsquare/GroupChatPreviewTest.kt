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

    @Test fun aNamedGroupCanBeCreatedWithAnInvitationAndReadAfterReopening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val store = AndroidWalletStore(context)
        val original = store.loadOrCreate()
        val fixture = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe test wallet. Never fund.
        val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
        val backend = AndroidBackendStore(context).load()
        val prefix = "groupchat.${SaitoBackend.OWNER.id}.${fixture.publicKey}."
        val retained = synchronized(SaitoLocalState.lock) {
            context.noBackupFilesDir.listFiles()!!.filter { it.name.startsWith(prefix) }.associate { it.name to it.readBytes() }
        }
        val preferences = listOf("wallet-backup-reminders" to "hidden:${fixture.publicKey}", "notification-permission" to "offered").map { (name,key) ->
            val prefs = context.getSharedPreferences(name,0)
            Triple(prefs,key,if(prefs.contains(key)) prefs.getBoolean(key,false) else null)
        }
        val peer = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" })
        var primary: Throwable? = null
        try {
            store.replace(original,fixture)
            AndroidBackendStore(context).save(SaitoBackend.OWNER)
            preferences.forEach { (prefs,key,_) -> check(prefs.edit().putBoolean(key,true).commit()) }
            synchronized(SaitoLocalState.lock) { context.noBackupFilesDir.listFiles()!!.filter { it.name.startsWith(prefix) }.forEach { check(it.delete()) } }
            peer.start().use { server ->
                ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)
                    .putExtra("feedPeerUrl","ws://127.0.0.1:${server.port}/wsopen")
                    .putExtra("feedOfficialPeerUrl","ws://127.0.0.1:${server.port}/wsopen")).use { scenario ->
                    withFailureCapture({ captureNativeScreen(File(context.cacheDir,"group-chat-evidence/create-failure.png")) }) {
                        waitFor("Chat")
                        ui.waitUntil(15_000) { ui.onAllNodes(hasText("Chat") and isEnabled()).fetchSemanticsNodes().size == 1 }
                        ui.onNodeWithText("Chat").assertIsDisplayed().performClick()
                        waitFor("Groups")
                        ui.onNodeWithText("Groups").assertIsDisplayed().performClick()
                        waitFor("Group messages are unencrypted.")
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
                        scenario.recreate()
                        waitFor("Groups")
                        ui.onNodeWithText("Groups").performClick()
                        waitFor("Beta crew")
                        captureNativeScreen(File(context.cacheDir,"group-chat-evidence/reopened.png"),"Beta crew")
                        check(peer.transactions.size == 1 && peer.relayedMessages.size == 1)
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

    private fun waitFor(text: String) = ui.waitUntil(15_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}
