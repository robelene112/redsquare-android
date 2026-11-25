package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpacePresentationTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")

    @Test(timeout = 45_000) fun aFreshPresentationKeepsPermissionsPrivateFilteringAndReadingWorkTogether() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("presentation-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("presentation-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("presentation-member.sqlite").absolutePath).use { memberStore ->
                    val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                    val reader = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                    val space = creator.create(SpaceProfile("A coherent community", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                    val general = creator.community(space).channel
                    reader.openPublic(space); reader.joinPublic(space); reader.flush(space); creator.refresh(space)
                    val hidden = creator.createPrivateChannel(space, SpaceChannelProfile("Staff notes"), emptySet()); creator.flush(space)
                    creator.send(space, "An independently received message"); creator.flush(space); reader.refresh(space)
                    reader.saveDraft(space, "Keep writing through management changes")
                    val received = reader.timeline(space).single()
                    val anchor = SpaceClient.ReadingAnchor(received.id, requireNotNull(received.position), 19, 0)
                    reader.saveReadingAnchor(space, general, anchor)
                    val first = reader.presentation(space)
                    assertEquals(listOf(space), first.communities.map { it.id })
                    assertEquals("A coherent community", first.profiles.getValue(space).name)
                    assertEquals(general, first.current?.channel)
                    assertFalse(requireNotNull(first.current).canManage)
                    assertTrue(requireNotNull(first.current).canSend)
                    assertEquals(SpaceRole.MEMBER, first.members.single { it.publicKey == member.publicKey }.role)
                    assertFalse(first.channels.any { it.id == hidden })
                    assertEquals(listOf("An independently received message"), first.messages.mapNotNull { it.text })
                    assertTrue(requireNotNull(first.window).loaded)
                    assertEquals("Keep writing through management changes", first.draft)
                    assertEquals(anchor, first.anchor)
                    val unrelated = reader.create(SpaceProfile("Another community", visibility = SpaceVisibility.PUBLIC))
                    assertEquals(setOf(space, unrelated), reader.communityIds().toSet())
                    assertEquals(setOf(space, unrelated), reader.presentation(space).profiles.keys)
                    assertEquals(first.copy(communities = emptyList()), reader.presentation(space, includeCommunities = false))
                    try { reader.presentation("f".repeat(64)); fail("An unknown Space cannot produce a presentation") }
                    catch (_: IllegalArgumentException) { }
                    creator.setAdministrator(space, member.publicKey, true); creator.flush(space); reader.refresh(space)
                    val promoted = reader.presentation(space)
                    assertEquals(SpaceRole.ADMIN, promoted.members.single { it.publicKey == member.publicKey }.role)
                    assertTrue(requireNotNull(promoted.current).canManage)
                    assertEquals(anchor, promoted.anchor)
                    creator.setAdministrator(space, member.publicKey, false); creator.flush(space); reader.refresh(space)
                    val demoted = reader.presentation(space, includeCommunities = false)
                    assertFalse(requireNotNull(demoted.current).canManage)
                    assertFalse(demoted.channels.any { it.id == hidden })
                    assertEquals(SpaceRole.MEMBER, demoted.members.single { it.publicKey == member.publicKey }.role)
                    assertEquals("Keep writing through management changes", demoted.draft)
                    assertEquals(anchor, demoted.anchor)
                    assertTrue("An earlier returned snapshot stays an immutable observation", requireNotNull(promoted.current).canManage)
                }
            }
        }
    }
}
