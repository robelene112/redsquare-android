package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceAddressedInvitationTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)

    @Test fun onlyTheNamedWalletCanUseTheOfferAndLegacyNodesDoNotReceiveABearerFallback() = runBlocking<Unit> {
        val owner = SaitoWallet.create()
        val intended = SaitoWallet.create()
        val other = SaitoWallet.create()
        val service = SaitoWallet.create()
        var advertise = false
        SpacesServiceStore(files.newFolder("addressed-invitation-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("addressed-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("addressed-intended.sqlite").absolutePath).use { intendedStore ->
                    SqliteClientFixture(files.newFile("addressed-other.sqlite").absolutePath).use { otherStore ->
                        val writer = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { tx ->
                            val response = remote.exchange(tx.envelope())
                            val signed = SaitoEnvelope.decode(response)
                            if (advertise || !signed.body.has("addressed_invitations")) response
                            else SaitoTransaction.createSpaceReply(service, signed.body.deepCopy().apply {
                                remove("addressed_invitations")
                            }, clock.millis(), owner.publicKey).envelope()
                        }
                        val recipient = SpaceClient(intended, "fixture-node", service.publicKey, intendedStore, clock) {
                            remote.exchange(it.envelope())
                        }
                        val stranger = SpaceClient(other, "fixture-node", service.publicKey, otherStore, clock) {
                            remote.exchange(it.envelope())
                        }
                        val space = writer.create("Addressed protected conversation")
                        writer.flush(space); writer.refresh(space)
                        assertThrows(IllegalArgumentException::class.java) { writer.inviteFor(space, intended.publicKey) }
                        assertNull(writer.invitation(space))
                        advertise = true
                        writer.refresh(space)
                        val invitation = writer.inviteFor(space, intended.publicKey)
                        writer.flush(space)
                        try { stranger.join(invitation); fail("Another wallet must not gain access from the link") }
                        catch (denied: SpaceClient.ReadDenied) { assertEquals(SpaceClient.ReadProblem.UNAVAILABLE, denied.problem) }
                        assertTrue(stranger.communities().isEmpty())
                        assertEquals(space, recipient.preview(invitation).spaceId)
                        assertTrue(recipient.communities().isEmpty())
                        recipient.join(invitation); recipient.flush(space)
                        writer.admitPending(space); writer.flush(space)
                        recipient.refresh(space)
                        assertTrue(recipient.community(space).joined)
                        assertEquals(setOf(owner.publicKey, intended.publicKey), writer.members(space).map { it.publicKey }.toSet())
                        val protected = writer.send(space, "Receivable only by an admitted device")
                        writer.flush(space); recipient.refresh(space)
                        assertEquals("Receivable only by an admitted device", recipient.message(space, protected)?.text)
                    }
                }
            }
        }
    }
}
