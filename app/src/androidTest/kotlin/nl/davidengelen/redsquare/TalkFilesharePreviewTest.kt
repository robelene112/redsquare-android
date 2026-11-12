package nl.davidengelen.redsquare

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.fileshare.*
import nl.davidengelen.redsquare.moderation.AndroidPersonalControls
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.talk.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Actual native FileSession/Chat retention over the same upstream Stun peer as a real three-party call.
 * File/Chat wire inputs use the verified native codecs; pinned upstream Stun performs actual DC delivery.
 */
class TalkFilesharePreviewTest {
    @Test fun blockingAnotherRoomParticipantPreservesTheUnblockedPeersFileAndPrivateChat() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallets = AndroidWalletStore(context); val previous = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context); val previousBackend = backends.load()
        val rawOwner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val aliceWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").withChatContact(rawOwner.publicKey)
        val owner = rawOwner.withChatContact(aliceWallet.publicKey)
        val carolWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val files = ChatFixtureState(context, listOf("fileshare.", "chat.", "chatwork.", "personal.${owner.publicKey}."), owner.publicKey)
        val browsers = ConcurrentHashMap<String, TalkBrowserPeer>(); val sequence = AtomicInteger(9000)
        val evidence = File(context.cacheDir, "talk-fileshare-evidence").apply { mkdirs() }
        var account: TalkAccount? = null; var session: FileSession? = null; var token: String? = null; var primary: Throwable? = null
        lateinit var fixture: ControlledFeedPeer
        fun route(envelope: String) {
            val signed = SaitoEnvelope.decodePayload(envelope)
            browsers.values.filter { it.wallet.publicKey in signed.recipients && it.wallet.publicKey != signed.author }.forEach { it.receive(envelope) }
            if (owner.publicKey in signed.recipients && signed.author != owner.publicKey) {
                val value = JsonParser.parseString(envelope).asJsonObject
                val header = java.util.Base64.getDecoder().decode(value.get("t").asString)
                val body = java.util.Base64.getDecoder().decode(value.get("m").asString)
                java.nio.ByteBuffer.wrap(header).putInt(8, body.size)
                fixture.sendIncoming(header + body, sequence.incrementAndGet())
            }
        }
        fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", relayResponse = {
            route(SaitoEnvelope.fromSignedJson(it)); "{\"success\":1}"
        })
        fun await(label: String, timeout: Long = 5000, goal: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + timeout
            while (!goal() && SystemClock.elapsedRealtime() < end) {
                browsers.values.forEach { if (it.errors.isNotEmpty()) throw AssertionError("$label: ${it.errors}") }
                Thread.sleep(25)
            }
            assertTrue("$label: ${TalkRuntime.call.value?.phase}, files=${session?.transfers?.value}", goal())
        }
        try {
            files.prepare(); backends.save(SaitoBackend.OWNER); wallets.replace(previous, owner)
            fixture.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                    .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                    withFailureCapture({
                        File(evidence, "failure-state.txt").writeText("${TalkRuntime.call.value}\n${session?.transfers?.value} ready=${session?.ready?.value} attached=${session?.ownsDelivery} error=${session?.error?.value}\n${browsers.values.map { it.errors }}")
                        captureNativeScreen(File(evidence, "failure.png"))
                    }) {
                        TalkRuntime.visibility(true)
                        account = TalkRuntime.account(context, owner, SaitoBackend.OWNER, endpoint).also { it.attach() }
                        val room = TalkRoom(SaitoWallet.create().publicKey, owner.publicKey)
                        assertTrue(runBlocking { requireNotNull(account).start(TalkLink(SaitoBackend.OWNER, room), null, true, true) })
                        token = requireNotNull(TalkRuntime.call.value).token
                        TalkBrowserPeer(aliceWallet, owner, "#e02020", 660, ::route).use { alice ->
                            browsers[aliceWallet.publicKey] = alice; alice.join(room)
                            await("Alice's actual call tracks arrive", 25_000) { TalkRuntime.call.value?.participants?.singleOrNull()?.let { it.connected && it.video != null && it.audio != null } == true }
                            TalkBrowserPeer(carolWallet, owner, "#20d030", 880, ::route).use { carol ->
                                browsers[carolWallet.publicKey] = carol; carol.join(room)
                                await("Three actual call participants are connected", 30_000) { TalkRuntime.call.value?.participants?.count { it.connected && it.video != null && it.audio != null } == 2 }
                                session = FileshareRuntime.session(context, owner, SaitoBackend.OWNER, endpoint, aliceWallet.publicKey).also { it.attach() }
                                val data = ByteArray(150007) { ((it * 13 + 7) % 251).toByte() }
                                val offer = Fileshare.Offer("call104file", "during-call.bin", data.size.toLong(), "application/octet-stream")
                                await("The native private conversation owns delivery") { session?.ownsDelivery == true && session?.ready?.value == true }
                                alice.sendFrame(Fileshare.offer(aliceWallet, owner.publicKey, offer, System.currentTimeMillis()).envelope())
                                await("An actual native incoming file is offered") { session?.transfers?.value?.singleOrNull()?.phase == FilePhase.OFFERED }
                                val transferToken = requireNotNull(session?.transfers?.value?.single()).token
                                requireNotNull(session).accept(transferToken)
                                await("Explicitly accepted native file is receiving") { session?.transfers?.value?.singleOrNull()?.phase == FilePhase.RECEIVING }
                                fun chunk(offset: Int, index: Long) = alice.sendFrame(Fileshare.chunk(aliceWallet, owner.publicKey,
                                    Fileshare.Chunk(offer, offset.toLong(), index, data.copyOfRange(offset, minOf(offset + Fileshare.CHUNK_BYTES, data.size))),
                                    System.currentTimeMillis()).envelope())
                                chunk(0, 1)
                                await("Actual RTC file chunk is retained while Talk is active") { session?.transfers?.value?.singleOrNull()?.bytes == Fileshare.CHUNK_BYTES.toLong() }
                                val policy = AndroidPersonalControls(context, owner.publicKey)
                                val old = policy.load(); policy.save(old, old.copy(blocked = old.blocked + carolWallet.publicKey))
                                await("The blocked room no longer owns capture") { TalkRuntime.call.value?.let { !it.phase.live && it.localVideo == null } == true }
                                chunk(Fileshare.CHUNK_BYTES, 2)
                                chunk(Fileshare.CHUNK_BYTES * 2, 3)
                                await("The unblocked peer's native file completes after Talk ownership ends") { session?.transfers?.value?.singleOrNull()?.phase == FilePhase.COMPLETE }
                                val complete = requireNotNull(session?.transfers?.value?.single())
                                val received = FileTransferStore(context, owner.publicKey, SaitoBackend.OWNER, aliceWallet.publicKey).openPrepared(complete).use { it.readBytes() }
                                assertArrayEquals(data, received)
                                val message = "Private Chat survives the retired call owner"
                                alice.sendFrame(PrivateChat.create(aliceWallet, owner.publicKey, message, System.currentTimeMillis()).envelope())
                                await("Actual encrypted private Chat still retains over the shared peer") {
                                    ChatHistoryStore(context, owner, SaitoBackend.OWNER, aliceWallet.publicKey).load().entries.any { it.message.text == message }
                                }
                                captureNativeScreen(File(evidence, "file-and-chat-retained.png"))
                                File(evidence, "coexistence.json").writeText("{\"actualThreePartyTracks\":true,\"actualNativeFileSession\":true,\"fileBytes\":${data.size},\"exactFileBytes\":true,\"blockedDifferentPeer\":true,\"privateChatRetainedAfterCall\":true,\"wireInputs\":\"verified-native-codecs\",\"transport\":\"pinned-upstream-Stun\"}")
                            }
                        }
                        fixture.assertHealthy()
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            session?.interrupt(); session?.detach(); token?.let(TalkRuntime::end); account?.detach(); TalkRuntime.visibility(false); Thread.sleep(300)
            cleanupAfterTest(primary, { files.restoreFiles() }, { files.restorePreferences() }, { wallets.replace(wallets.loadOrCreate(), previous) },
                { backends.save(previousBackend) }, { context.stopService(Intent(context, TalkService::class.java)) },
                { context.stopService(Intent(context, FileTransferService::class.java)) })
        }
    }
}
