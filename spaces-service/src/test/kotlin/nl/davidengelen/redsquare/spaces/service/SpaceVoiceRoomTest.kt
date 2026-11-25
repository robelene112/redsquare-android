package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import nl.davidengelen.redsquare.spaces.SpaceVoiceRoom
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
                assertFalse(visitor.joinVoiceRoom(room, channel, token(4)).admitted)
                assertTrue(runCatching { owner.voiceRoom(room, chat) }.exceptionOrNull() is SpaceClient.ReadDenied)

                joinSpace(member)
                joinSpace(secondMember)
                joinSpace(visitor)
                val opened = owner.joinVoiceRoom(room, channel, token(1))
                assertTrue(opened.admitted)
                assertEquals(wallets.first().publicKey, member.voiceRoom(room, channel).participants.single().wallet)
                assertTrue(member.joinVoiceRoom(room, channel, token(2)).admitted)
                assertTrue(secondMember.joinVoiceRoom(room, channel, token(3)).admitted)
                val full = visitor.joinVoiceRoom(room, channel, token(4))
                assertFalse(full.admitted)
                assertEquals(SpaceVoiceRoom.Status.FULL, full.status)
                assertEquals(3, full.participants.size)

                val target = full.participants.single { it.wallet == wallets[1].publicKey }
                assertTrue(runCatching { visitor.moderateVoiceRoom(room, channel, target.session, remove = true) }
                    .exceptionOrNull() is SpaceClient.ReadDenied)
                owner.moderateVoiceRoom(room, channel, target.session, remove = false)
                assertFalse(member.renewVoiceRoom(room, channel, token(2)).participants.single { it.session == token(2) }.canSpeak)
                owner.moderateVoiceRoom(room, channel, target.session, remove = true)
                assertFalse(member.renewVoiceRoom(room, channel, token(2)).admitted)
                assertTrue(visitor.joinVoiceRoom(room, channel, token(4)).admitted)

                owner.refresh(room)
                owner.setWriteRestriction(room, wallets[3].publicKey, channel, clock.millis() + 60_000)
                owner.flush(room)
                assertFalse(visitor.renewVoiceRoom(room, channel, token(4)).participants.single { it.session == token(4) }.canSpeak)
                assertTrue(owner.leaveVoiceRoom(room, channel, token(1)).participants.none { it.session == token(1) })
                clock.advance(60_001)
                assertTrue(owner.voiceRoom(room, channel).participants.isEmpty())
                assertFalse(secondMember.renewVoiceRoom(room, channel, token(3)).admitted)
                assertTrue(owner.joinVoiceRoom(room, channel, token(5)).admitted)
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

    private fun token(number: Int) = number.toString().padStart(32, '0')

    private class RoomClock(private var now: Instant = Instant.ofEpochMilli(1_800_000_000_000)) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = Clock.fixed(now, zone)
        override fun instant() = now
        fun advance(millis: Long) { now = now.plusMillis(millis) }
    }
}
