package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.Stun
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import nl.davidengelen.redsquare.spaces.SpaceVoiceRoom
import nl.davidengelen.redsquare.spaces.SpaceVoiceSignalKey
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceVoiceRoomTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun onlyExplicitAdmittedSessionsOccupyTheReusableRoom() = runBlocking<Unit> {
        val clock = RoomClock()
        val node = SaitoWallet.create()
        val wallets = List(4) { SaitoWallet.create() }
        val state = files.newFolder().toPath()
        SpacesServiceStore(state, node, clock).use { service ->
            val stores = wallets.map { SqliteClientFixture(files.newFile().absolutePath) }
            try {
                val clients = wallets.zip(stores).map { (wallet, store) ->
                    SpaceClient(wallet, "https://rooms.example.test", node.publicKey, store, clock) { service.exchange(it.envelope()) }
                }
                val (owner, member, secondMember, visitor) = clients
                val room = owner.create(SpaceProfile("Reusable room", visibility = SpaceVisibility.PUBLIC), listOf(
                    SpaceChannelProfile("Lounge", SpaceChannelKind.VOICE), SpaceChannelProfile("Chat", SpaceChannelKind.CHAT)))
                owner.flush(room)
                owner.refresh(room)
                val channel = owner.channels(room).single { it.profile.kind == SpaceChannelKind.VOICE }.id
                val chat = owner.channels(room).single { it.profile.kind == SpaceChannelKind.CHAT }.id
                suspend fun joinSpace(client: SpaceClient) {
                    client.openPublic(room)
                    client.joinPublic(room)
                    client.flush(room)
                    client.refresh(room)
                }
                visitor.openPublic(room)
                assertTrue(owner.voiceRoom(room, channel).participants.isEmpty())
                assertTrue(visitor.voiceRoom(room, channel).participants.isEmpty())
                val address = SpaceVoiceSignalKey.Address(node.publicKey, room, channel)
                val signalKeys = wallets.mapIndexed { index, wallet -> SpaceVoiceSignalKey.create(address, wallet, token(index + 1)) }
                val (ownerMedia, memberMedia, secondMedia, visitorMedia) = signalKeys
                assertFalse(visitor.joinVoiceRoom(room, channel, visitorMedia.contact).admitted)
                assertTrue(runCatching { owner.voiceRoom(room, chat) }.exceptionOrNull() is SpaceClient.ReadDenied)

                joinSpace(member)
                joinSpace(secondMember)
                joinSpace(visitor)
                val opened = owner.joinVoiceRoom(room, channel, ownerMedia.contact)
                assertTrue(opened.admitted)
                assertEquals(wallets.first().publicKey, member.voiceRoom(room, channel).participants.single().wallet)
                assertTrue(member.joinVoiceRoom(room, channel, memberMedia.contact).admitted)
                assertTrue(secondMember.joinVoiceRoom(room, channel, secondMedia.contact).admitted)
                val full = visitor.joinVoiceRoom(room, channel, visitorMedia.contact)
                assertFalse(full.admitted)
                assertEquals(SpaceVoiceRoom.Status.FULL, full.status)
                assertEquals(3, full.participants.size)

                val privateDescription = "v=0\r\na=fingerprint:sha-256 owned-isolated-description"
                val description = Stun.signal(ownerMedia.rtcWallet, memberMedia.contact.key, "peer-description", JsonObject().apply {
                    addProperty("type", "offer"); addProperty("sdp", privateDescription)
                }, clock.millis())
                val encrypted = ownerMedia.seal(memberMedia.contact, description.envelope())
                assertFalse(encrypted.json().toString().contains(privateDescription))
                assertTrue(owner.relayVoiceSignal(room, channel, token(1), encrypted).admitted)
                assertTrue(owner.relayVoiceSignal(room, channel, token(1), encrypted).admitted)
                assertTrue(secondMember.renewVoiceRoom(room, channel, token(3)).signals.isEmpty())
                assertTrue(visitor.voiceRoom(room, channel).signals.isEmpty())
                val received = member.renewVoiceRoom(room, channel, token(2)).signals.single()
                assertEquals(encrypted, received.packet)
                assertEquals(description.id, memberMedia.open(ownerMedia.contact, received.packet).id)
                assertEquals(privateDescription, memberMedia.open(ownerMedia.contact, received.packet).body.get("sdp").asString)
                assertTrue(runCatching { secondMedia.open(ownerMedia.contact, received.packet.copy(to = token(3))) }.isFailure)
                assertTrue(member.renewVoiceRoom(room, channel, token(2), received.sequence).signals.isEmpty())
                assertTrue(owner.relayVoiceSignal(room, channel, token(1), encrypted).admitted)
                assertTrue(member.renewVoiceRoom(room, channel, token(2), received.sequence).signals.isEmpty())
                val stoppedCamera = SpaceVoiceSignalKey.CaptureState(2, microphone = true, camera = false)
                val cameraNotice = ownerMedia.sealCapture(memberMedia.contact, stoppedCamera, clock.millis())
                assertFalse(cameraNotice.json().has("camera"))
                assertTrue(owner.relayVoiceSignal(room, channel, token(1), cameraNotice).admitted)
                val cameraDelivery = member.renewVoiceRoom(room, channel, token(2), received.sequence).signals.single()
                assertEquals(stoppedCamera, SpaceVoiceSignalKey.captureState(memberMedia.open(ownerMedia.contact, cameraDelivery.packet)))
                assertTrue(member.renewVoiceRoom(room, channel, token(2), cameraDelivery.sequence).signals.isEmpty())

                val target = full.participants.single { it.wallet == wallets[1].publicKey }
                assertTrue(runCatching { visitor.moderateVoiceRoom(room, channel, target.session, remove = true) }
                    .exceptionOrNull() is SpaceClient.ReadDenied)
                owner.moderateVoiceRoom(room, channel, target.session, remove = false)
                assertFalse(member.renewVoiceRoom(room, channel, token(2)).participants.single { it.session == token(2) }.canSpeak)
                assertFalse(member.joinVoiceRoom(room, channel, SpaceVoiceSignalKey.create(address, wallets[1], token(6)).contact).admitted)
                assertFalse(member.renewVoiceRoom(room, channel, token(2)).participants.single { it.session == token(2) }.canSpeak)
                owner.moderateVoiceRoom(room, channel, target.session, remove = false)
                owner.moderateVoiceRoom(room, channel, target.session, remove = true)
                val audit = owner.adminLog(room).events.filter { it.action in setOf("muted-in-room", "removed-from-room") }
                assertEquals(setOf("muted-in-room", "removed-from-room"), audit.map { it.action }.toSet())
                assertEquals(2, audit.size)
                assertTrue(audit.all { it.actor == wallets[0].publicKey && it.member == target.wallet && it.channel == channel &&
                    it.cursor.at == clock.millis() })
                assertFalse(member.renewVoiceRoom(room, channel, token(2)).admitted)
                assertTrue(visitor.joinVoiceRoom(room, channel, visitorMedia.contact).admitted)
                assertTrue(runCatching { owner.relayVoiceSignal(room, channel, token(1), encrypted) }.exceptionOrNull() is SpaceClient.ReadDenied)

                owner.refresh(room)
                owner.setWriteRestriction(room, wallets[3].publicKey, channel, clock.millis() + 60_000)
                owner.flush(room)
                assertEquals(clock.millis() + 60_000, owner.scopedWriteRestriction(room, wallets[3].publicKey, channel))
                assertFalse(visitor.renewVoiceRoom(room, channel, token(4)).participants.single { it.session == token(4) }.canSpeak)
                assertTrue(owner.leaveVoiceRoom(room, channel, token(1)).participants.none { it.session == token(1) })
                clock.advance(60_001)
                assertTrue(owner.voiceRoom(room, channel).participants.isEmpty())
                assertFalse(secondMember.renewVoiceRoom(room, channel, token(3)).admitted)
                assertTrue(owner.joinVoiceRoom(room, channel, SpaceVoiceSignalKey.create(address, wallets[0], token(5)).contact).admitted)
                owner.refresh(room)
                owner.archiveChannel(room, channel, true)
                owner.flush(room)
                val archived = owner.renewVoiceRoom(room, channel, token(5))
                assertFalse(archived.admitted)
                assertEquals(SpaceVoiceRoom.Status.ARCHIVED, archived.status)
                assertTrue(archived.participants.isEmpty())
            } finally { stores.forEach(SqliteClientFixture::close) }
        }
    }

    @Test fun privateParticipationRequiresTheCurrentEnrolledDeviceKey() = runBlocking<Unit> {
        val clock = RoomClock()
        val node = SaitoWallet.create()
        val memberWallet = SaitoWallet.create()
        SpacesServiceStore(files.newFolder().toPath(), node, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile().absolutePath).use { memberStore ->
                    fun client(wallet: SaitoWallet, store: SqliteClientFixture) =
                        SpaceClient(wallet, "https://rooms.example.test", node.publicKey, store, clock) { service.exchange(it.envelope()) }
                    val owner = client(SaitoWallet.create(), ownerStore)
                    val member = client(memberWallet, memberStore)
                    val room = owner.create(SpaceProfile("Private lounge"), listOf(SpaceChannelProfile("Lounge", SpaceChannelKind.VOICE)))
                    owner.flush(room); owner.refresh(room)
                    val channel = owner.channels(room).single().id
                    val invite = owner.inviteFor(room, memberWallet.publicKey)
                    owner.flush(room)
                    member.join(invite); member.flush(room)
                    owner.admitPending(room); owner.flush(room); member.refresh(room)
                    val media = SpaceVoiceSignalKey.create(SpaceVoiceSignalKey.Address(node.publicKey, room, channel), memberWallet, token(2))
                    val scope = SpaceVoiceRoom.scope(room, channel, SpaceVoiceRoom.Command.JOIN, token(2)).apply {
                        add("contact", media.contact.json())
                    }
                    fun raw(kind: String, nonce: String? = null): JsonObject {
                        val request = SaitoTransaction.create(memberWallet, JsonObject().apply {
                            addProperty("module", "Spaces"); addProperty("version", 1); addProperty("request", kind)
                            addProperty("request_id", "1".repeat(64)); add("scope", scope)
                            nonce?.let { addProperty("nonce", it) }
                        }, clock.millis(), listOf(node.publicKey))
                        return SaitoEnvelope.decode(service.exchange(request.envelope())).body
                    }
                    val nonce = raw("challenge").get("nonce").asString
                    assertEquals("denied", raw("read", nonce).get("request").asString)
                    assertTrue(owner.voiceRoom(room, channel).participants.isEmpty())
                    assertTrue(member.joinVoiceRoom(room, channel, media.contact).admitted)
                    assertEquals(memberWallet.publicKey, owner.voiceRoom(room, channel).participants.single().wallet)
                    member.leave(room); member.flush(room)
                    assertTrue(owner.voiceRoom(room, channel).participants.isEmpty())
                    assertTrue(runCatching { member.renewVoiceRoom(room, channel, token(2)) }.isFailure)
                }
            }
        }
    }

    private fun token(number: Int) = number.toString().padStart(32, '0')

    private class RoomClock(private var now: Instant = Instant.ofEpochMilli(1_800_000_000_000)) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = Clock.fixed(now, zone)
        override fun instant() = now
        fun advance(millis: Long) { now = now.plusMillis(millis) }
    }
}
