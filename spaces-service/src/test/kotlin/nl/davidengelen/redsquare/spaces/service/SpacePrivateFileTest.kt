package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceFileCipher
import nl.davidengelen.redsquare.spaces.SpaceFileDescriptor
import nl.davidengelen.redsquare.spaces.SpaceFileStage
import nl.davidengelen.redsquare.spaces.SpaceProfile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpacePrivateFileTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000), ZoneOffset.UTC)

    @Test fun privateDescriptorUsesTheChannelKeysAndDepartureInvalidatesAnInProgressDownload() = runBlocking<Unit> {
        val node = SaitoWallet.create()
        val moderator = SaitoWallet.create()
        val member = SaitoWallet.create()
        val operator = SaitoWallet.create()
        val payload = "Independent binary fixture with no public body.".toByteArray()
        val name = "Private finance projection.txt"
        val wire = mutableListOf<String>()
        SpacesServiceStore(files.newFolder().toPath(), node, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile().absolutePath).use { memberStore ->
                    fun client(wallet: SaitoWallet, store: SqliteClientFixture) = SpaceClient(wallet,
                        "https://community.example.test", node.publicKey, store, clock) {
                        service.exchange(it.envelope().also(wire::add))
                    }
                    val owner = client(moderator, ownerStore)
                    val receiver = client(member, memberStore)
                    val space = owner.create(SpaceProfile("Private library"), listOf(SpaceChannelProfile("Files", SpaceChannelKind.FILE)))
                    owner.flush(space); owner.refresh(space)
                    val channel = owner.channels(space).single().id
                    val invite = owner.inviteFor(space, member.publicKey)
                    owner.flush(space)
                    receiver.join(invite); receiver.flush(space)
                    owner.admitPending(space); owner.flush(space); receiver.refresh(space)
                    SpaceFileProviderStore(files.newFolder().toPath(), operator, "https://files.example.test", node.publicKey, clock).use { peer ->
                        val quote = peer.offer(space, moderator.publicKey, 32768, clock.millis() + 600000)
                        val consent = owner.approveFileStorage(space, quote)
                        owner.flush(space)
                        val receipt = requireNotNull(owner.fileStorageApproval(space, consent))
                        owner.activateFileStorage(space, receipt, peer.commit(receipt).envelope)
                        owner.flush(space)
                        val ciphertext = ByteArrayOutputStream()
                        val scope = SpaceFileCipher.Scope(node.publicKey, space, channel)
                        val file = SpaceFileCipher.seal(scope, payload.size.toLong(), payload.inputStream(), ciphertext)
                        val grant = owner.fileUploadPermission(space, channel, file)
                        val staged = SpaceFileStage.read(peer.put(grant.envelope, ciphertext.toByteArray().inputStream()),
                            node.publicKey, operator.publicKey)
                        val original = owner.publishFile(space, channel, SpaceFileDescriptor(name, "text/plain", file), staged)
                        owner.flush(space); receiver.refresh(space)
                        val event = SaitoEnvelope.decode(wire.single { SaitoEnvelope.decode(it).id == original }).body
                        assertTrue(event.has("ciphertext"))
                        assertFalse(event.has("text"))
                        assertFalse(event.toString().contains(name))
                        for (providerMessage in listOf(grant.envelope, staged.envelope)) {
                            val body = SaitoEnvelope.decode(providerMessage).body.toString()
                            assertFalse(body.contains(name))
                            assertFalse(body.contains(moderator.publicKey))
                            assertFalse(body.contains(member.publicKey))
                        }
                        val received = SpaceFileDescriptor.read(requireNotNull(receiver.message(space, original)?.text))
                        assertEquals(name, received.name)
                        val permission = receiver.fileDownloadPermission(space, channel, original)
                        receiver.leave(space)
                        receiver.flush(space)
                        val checked = ByteArrayOutputStream()
                        peer.open(permission.grant.envelope).use { SpaceFileCipher.open(scope, received.sealed, it, checked) }
                        assertArrayEquals(payload, checked.toByteArray())
                        assertTrue(runCatching { receiver.confirmFileDownload(space, channel, original, permission) }.isFailure)
                        assertTrue(runCatching { receiver.fileDownloadPermission(space, channel, original) }.isFailure)
                    }
                }
            }
        }
    }
}
