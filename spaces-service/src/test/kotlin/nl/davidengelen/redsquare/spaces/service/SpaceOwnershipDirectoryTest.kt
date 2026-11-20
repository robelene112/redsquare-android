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

class SpaceOwnershipDirectoryTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
    private val other = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")

    @Test(timeout = 45_000) fun theSuccessorCanChangeDiscoverabilityWithoutBreakingIndependentDirectoryReading() = runBlocking<Unit> {
        SpacesServiceStore(files.newFolder("ownership-directory-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("ownership-directory-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("ownership-directory-member.sqlite").absolutePath).use { memberStore ->
                    SqliteClientFixture(files.newFile("ownership-directory-reader.sqlite").absolutePath).use { readerStore ->
                        val creator = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { remote.exchange(it.envelope()) }
                        val successor = SpaceClient(member, "fixture-node", service.publicKey, memberStore, clock) { remote.exchange(it.envelope()) }
                        val reader = SpaceClient(other, "fixture-node", service.publicKey, readerStore, clock) { remote.exchange(it.envelope()) }
                        val space = creator.create(SpaceProfile("Directory after handover", visibility = SpaceVisibility.PUBLIC)); creator.flush(space)
                        successor.openPublic(space); successor.joinPublic(space); successor.flush(space); creator.refresh(space)
                        assertEquals(listOf(space), reader.discover("handover").items.map { it.id })
                        val offer = creator.offerOwnership(space, member.publicKey); creator.flush(space); successor.refresh(space)
                        successor.acceptOwnership(space, offer); successor.flush(space); creator.refresh(space)
                        successor.setListed(space, false); successor.flush(space)
                        assertTrue(reader.discover("handover").items.isEmpty())
                        successor.setListed(space, true); successor.flush(space)
                        val listed = reader.discover("handover").items.single()
                        assertEquals(space, listed.id)
                        assertEquals("Directory after handover", listed.profile.name)
                        assertTrue(listed.profile.listed)
                        assertTrue("Verifying directory ownership does not admit or join the reader", reader.communities().isEmpty())
                    }
                }
            }
        }
    }
}
