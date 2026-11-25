package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceFileCipher
import nl.davidengelen.redsquare.spaces.SpaceFileGrant
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceFileBytesTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000), ZoneOffset.UTC)

    @Test fun interruptedTransferCanRetryAndIndependentReaderRecoversExactBytesAfterProviderRestart() = runBlocking<Unit> {
        val node = SaitoWallet.create()
        val owner = SaitoWallet.create()
        val operator = SaitoWallet.create()
        val endpoint = "https://storage.example.test"
        val storage = files.newFolder().toPath()
        val plain = ByteArray(2 * 1024 * 1024 + 19) { (it % 251).toByte() }
        val encrypted = ByteArrayOutputStream()
        lateinit var readGrant: SpaceFileGrant
        lateinit var sealed: SpaceFileCipher.Sealed
        lateinit var scope: SpaceFileCipher.Scope
        SpacesServiceStore(files.newFolder().toPath(), node, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { store ->
                val sender = SpaceClient(owner, "https://community.example.test", node.publicKey, store, clock) { service.exchange(it.envelope()) }
                val space = sender.create(SpaceProfile("Library", visibility = SpaceVisibility.PUBLIC),
                    listOf(SpaceChannelProfile("Files", SpaceChannelKind.FILE)))
                sender.flush(space); sender.refresh(space)
                val channel = sender.channels(space).single().id
                scope = SpaceFileCipher.Scope(node.publicKey, space, channel)
                sealed = SpaceFileCipher.seal(scope, plain.size.toLong(), plain.inputStream(), encrypted)
                assertFalse(plain.contentEquals(encrypted.toByteArray()))
                SpaceFileProviderStore(storage, operator, endpoint, node.publicKey, clock).use { remote ->
                    val terms = remote.offer(space, owner.publicKey, sealed.bytes, clock.millis() + 600_000L)
                    val approval = sender.approveFileStorage(space, terms)
                    sender.flush(space)
                    val lease = remote.commit(requireNotNull(sender.fileStorageApproval(space, approval)))
                    fun grant(action: SpaceFileGrant.Action, content: SpaceFileCipher.Sealed) = SpaceFileGrant.create(
                        node, operator.publicKey, space, lease.id, action, content.id, content.sha256, content.bytes,
                        clock.millis(), clock.millis() + 60_000L)
                    val upload = grant(SpaceFileGrant.Action.PUT, sealed)
                    readGrant = grant(SpaceFileGrant.Action.GET, sealed)
                    val ciphertext = encrypted.toByteArray()
                    assertThrows(Exception::class.java) { remote.put(upload.envelope, ciphertext.copyOf(ciphertext.size - 1).inputStream()) }
                    assertEquals(0L, remote.leaseFor(space)?.usedBytes)
                    assertThrows(IllegalArgumentException::class.java) { remote.open(readGrant.envelope) }
                    val stage = remote.put(upload.envelope, ciphertext.inputStream())
                    assertEquals(stage, remote.put(upload.envelope, ciphertext.inputStream()))
                    assertEquals(sealed.bytes, remote.leaseFor(space)?.usedBytes)

                    val excess = SpaceFileCipher.seal(scope, 0, ByteArray(0).inputStream(), ByteArrayOutputStream())
                    assertThrows(IllegalArgumentException::class.java) {
                        remote.put(grant(SpaceFileGrant.Action.PUT, excess).envelope, ByteArray(0).inputStream())
                    }
                    assertEquals(sealed.bytes, remote.leaseFor(space)?.usedBytes)
                }
            }
        }
        // The uploader's client and the Spaces node are both stopped at this point.
        SpaceFileProviderStore(storage, operator, endpoint, node.publicKey, clock).use { restarted ->
            val recovered = ByteArrayOutputStream()
            restarted.open(readGrant.envelope).use { SpaceFileCipher.open(scope, sealed, it, recovered) }
            assertArrayEquals(plain, recovered.toByteArray())
            assertThrows(IllegalArgumentException::class.java) {
                restarted.open(readGrant.envelope).use {
                    SpaceFileCipher.open(scope.copy(channel = "f".repeat(64)), sealed, it, ByteArrayOutputStream())
                }
            }
            val raw = encrypted.toByteArray().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            assertThrows(IllegalArgumentException::class.java) {
                SpaceFileCipher.open(scope, sealed, raw.inputStream(), ByteArrayOutputStream())
            }
            assertEquals(1L, Files.list(storage.resolve("objects")).use { it.count() })
            val path = storage.resolve("objects").resolve(sealed.id)
            SpaceFileCipher.Reader(scope, sealed, RandomAccessFile(path.toFile(), "r")).use { reader ->
                val offset = 256 * 1024L - 5
                val crossing = ByteArray(33)
                assertEquals(crossing.size, reader.read(offset, crossing.size, crossing))
                assertArrayEquals(plain.copyOfRange(offset.toInt(), offset.toInt() + crossing.size), crossing)
                assertEquals(4, reader.read(plain.size - 4L, crossing.size, crossing))
                assertArrayEquals(plain.takeLast(4).toByteArray(), crossing.copyOf(4))
                assertEquals(0, reader.read(plain.size.toLong(), crossing.size, crossing))
                assertEquals(crossing.size, reader.read(0L, crossing.size, crossing))
                assertArrayEquals(plain.copyOf(crossing.size), crossing)
            }
        }
    }
}
