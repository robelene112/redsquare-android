package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceFileCipher
import nl.davidengelen.redsquare.spaces.SpaceFileDescriptor
import nl.davidengelen.redsquare.spaces.SpaceFileStage
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceStorageUsage
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class SpaceFileStorageLifecycleTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun renewalRetainsBytesAndConfirmedSharedDeletionReturnsCapacityWithoutRevivingTheObject() = runBlocking<Unit> {
        val node = SaitoWallet.create()
        val wallet = SaitoWallet.create()
        val provider = SaitoWallet.create()
        val origin = "https://files.example.test"
        val clock = TestClock()
        val initialExpiry = clock.millis() + 600_000
        SpacesServiceStore(files.newFolder().toPath(), node, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { local ->
                val owner = SpaceClient(wallet, "https://community.example.test", node.publicKey, local, clock) { service.exchange(it.envelope()) }
                val space = owner.create(SpaceProfile("Library", visibility = SpaceVisibility.PUBLIC),
                    listOf(SpaceChannelProfile("Files", SpaceChannelKind.FILE)))
                owner.flush(space); owner.refresh(space)
                val channel = owner.channels(space).single().id
                val source = byteArrayOf(1, 2, 3, 4, 5)
                val ciphertext = ByteArrayOutputStream()
                val scope = SpaceFileCipher.Scope(node.publicKey, space, channel)
                val file = SpaceFileCipher.seal(scope, source.size.toLong(), source.inputStream(), ciphertext)
                val storage = files.newFolder().toPath()
                SpaceFileProviderStore(storage, provider, origin, node.publicKey, clock).use { peer ->
                    suspend fun activate(expiry: Long): String {
                        val offer = peer.offer(space, wallet.publicKey, file.bytes, expiry)
                        val approval = owner.approveFileStorage(space, offer)
                        owner.flush(space)
                        val receipt = requireNotNull(owner.fileStorageApproval(space, approval))
                        val lease = peer.commit(receipt)
                        assertEquals(lease.id, peer.commit(receipt).id)
                        owner.activateFileStorage(space, receipt, lease.envelope)
                        owner.flush(space)
                        return lease.id
                    }
                    suspend fun used(): Long {
                        val grant = owner.fileStorageUsagePermission(space, channel)
                        val current = requireNotNull(owner.fileStorage(space))
                        return SpaceStorageUsage.read(peer.usage(grant.envelope), current, grant, clock.millis()).used
                    }
                    val originalLease = activate(initialExpiry)
                    assertEquals(0L, used())
                    val upload = owner.fileUploadPermission(space, channel, file)
                    val stage = SpaceFileStage.read(peer.put(upload.envelope, ciphertext.toByteArray().inputStream()),
                        node.publicKey, provider.publicKey)
                    val recordId = owner.publishFile(space, channel, SpaceFileDescriptor("notes.txt", "text/plain", file), stage)
                    owner.flush(space)
                    assertEquals(file.bytes, used())

                    clock.now = initialExpiry
                    assertTrue(runCatching { owner.fileDownloadPermission(space, channel, recordId) }.isFailure)
                    assertNotEquals(originalLease, activate(initialExpiry + 600_000))
                    assertEquals(file.bytes, used())
                    val download = owner.fileDownloadPermission(space, channel, recordId)
                    peer.open(download.grant.envelope).use { assertArrayEquals(ciphertext.toByteArray(), it.readBytes()) }

                    val record = requireNotNull(owner.message(space, recordId, channel))
                    owner.deleteMessage(space, record, channel)
                    owner.flush(space)
                    val removal = owner.fileRemovalPermission(space, channel, recordId)
                    peer.remove(removal.envelope)
                    peer.remove(removal.envelope) // dropped response retries exact original
                    assertEquals(0L, used())
                    assertTrue(runCatching { owner.fileDownloadPermission(space, channel, recordId) }.isFailure)
                    assertThrows(IllegalArgumentException::class.java) { peer.open(download.grant.envelope) }
                }
                SpaceFileProviderStore(storage, provider, origin, node.publicKey, clock).use { reopened ->
                    assertEquals(0L, reopened.leaseFor(space)?.usedBytes)
                }
            }
        }
    }

    private class TestClock : Clock() {
        var now = 1_800_000_000_000L
        override fun instant(): Instant = Instant.ofEpochMilli(now)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = fixed(instant(), zone)
    }
}
