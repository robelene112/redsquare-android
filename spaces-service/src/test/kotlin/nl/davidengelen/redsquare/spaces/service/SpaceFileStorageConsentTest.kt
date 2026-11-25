package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceFileStorageConsentTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000), ZoneOffset.UTC)

    @Test fun anIndependentProviderCommitsOnlyTheOwnersConfirmedTermsAndReusesThatLeaseAfterRestart() = runBlocking<Unit> {
        val node = SaitoWallet.create()
        val owner = SaitoWallet.create()
        val member = SaitoWallet.create()
        val provider = SaitoWallet.create()
        val storage = files.newFolder().toPath()
        val endpoint = "https://storage.example.test"
        val expires = clock.millis() + 7 * 86_400_000L
        SpacesServiceStore(files.newFolder().toPath(), node, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile().absolutePath).use { memberStore ->
                    fun client(wallet: SaitoWallet, store: SqliteClientFixture) =
                        SpaceClient(wallet, "https://community.example.test", node.publicKey, store, clock) { service.exchange(it.envelope()) }
                    val writer = client(owner, ownerStore)
                    val visitor = client(member, memberStore)
                    val space = writer.create(SpaceProfile("Library", visibility = SpaceVisibility.PUBLIC),
                        listOf(SpaceChannelProfile("Shared files", SpaceChannelKind.FILE)))
                    writer.flush(space); writer.refresh(space)
                    visitor.openPublic(space); visitor.joinPublic(space); visitor.flush(space); visitor.refresh(space)
                    writer.refresh(space)

                    lateinit var approval: String
                    lateinit var leaseId: String
                    SpaceFileProviderStore(storage, provider, endpoint, node.publicKey, clock).use { remote ->
                        val offer = remote.offer(space, owner.publicKey, capacityBytes = 8192, expiresAt = expires)
                        assertNull(remote.leaseFor(space))
                        assertTrue(runCatching { visitor.approveFileStorage(space, offer) }.exceptionOrNull() is IllegalArgumentException)
                        val operation = writer.approveFileStorage(space, offer)
                        assertNull(writer.fileStorageApproval(space, operation))
                        writer.flush(space)
                        approval = requireNotNull(writer.fileStorageApproval(space, operation))
                        assertNull(remote.leaseFor(space))
                        val accepted = remote.commit(approval)
                        assertEquals(8192L, accepted.capacityBytes)
                        assertEquals(expires, accepted.expiresAt)
                        assertEquals(0L, accepted.usedBytes)
                        leaseId = accepted.id
                    }
                    SpaceFileProviderStore(storage, provider, endpoint, node.publicKey, clock).use { reopened ->
                        assertEquals(leaseId, reopened.commit(approval).id)
                        assertEquals(leaseId, reopened.leaseFor(space)?.id)
                    }
                }
            }
        }
    }
}
