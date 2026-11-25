package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceFileCipher
import nl.davidengelen.redsquare.spaces.SpaceFileAccessCheck
import nl.davidengelen.redsquare.spaces.SpaceFileGrant
import nl.davidengelen.redsquare.spaces.SpaceFileDescriptor
import nl.davidengelen.redsquare.spaces.SpaceFileStage
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceStorageProviderClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

class SpacePrivateFileTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000), ZoneOffset.UTC)

    @Test fun privateDescriptorUsesTheChannelKeysAndDepartureInvalidatesAnInProgressDownload() = runBlocking<Unit> {
        val node = SaitoWallet.create()
        val moderator = SaitoWallet.create()
        val member = SaitoWallet.create()
        val operator = SaitoWallet.create()
        val payload = ByteArray(1_560_789) { (it % 251).toByte() }
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
                    val origin = "https://files.example.test"
                    val revokeAfter = AtomicInteger(-1)
                    SpaceFileProviderServer(files.newFolder().toPath(), operator, origin, node.publicKey, clock,
                        0, 4194304, 600000) { request ->
                        if (revokeAfter.get() > 0 && SpaceFileAccessCheck.read(request, node.publicKey, clock.millis())
                                .permission.action == SpaceFileGrant.Action.GET && revokeAfter.decrementAndGet() == 0) {
                            runBlocking { receiver.leave(space); receiver.flush(space) }
                        }
                        service.checkFileAccess(request)
                    }.use { peer ->
                        peer.start()
                        val provider = SpaceStorageProviderClient(origin) { base ->
                            URL("http://127.0.0.1:${peer.port}${base.file}").openConnection() as HttpURLConnection
                        }
                        val quote = provider.offer(space, moderator.publicKey)
                        val consent = owner.approveFileStorage(space, quote)
                        owner.flush(space)
                        val receipt = requireNotNull(owner.fileStorageApproval(space, consent))
                        owner.activateFileStorage(space, receipt, provider.commit(receipt))
                        owner.flush(space)
                        val ciphertext = ByteArrayOutputStream()
                        val scope = SpaceFileCipher.Scope(node.publicKey, space, channel)
                        val file = SpaceFileCipher.seal(scope, payload.size.toLong(), payload.inputStream(), ciphertext)
                        val grant = owner.fileUploadPermission(space, channel, file)
                        val staged = SpaceFileStage.read(provider.put(grant, ciphertext.toByteArray().inputStream()),
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
                        val authorized = receiver.fileDownloadPermission(space, channel, original)
                        val checked = ByteArrayOutputStream()
                        provider.download(authorized.grant) { SpaceFileCipher.open(scope, received.sealed, it, checked) }
                        assertArrayEquals(payload, checked.toByteArray())
                        val permission = receiver.fileDownloadPermission(space, channel, original)
                        val interrupted = receiver.fileDownloadPermission(space, channel, original)
                        val partial = ByteArrayOutputStream()
                        revokeAfter.set(2)
                        assertTrue(runCatching {
                            provider.download(interrupted.grant) { SpaceFileCipher.open(scope, received.sealed, it, partial) }
                        }.isFailure)
                        assertTrue("Only an already delivered prefix may be present", partial.size() in 1 until payload.size)
                        assertThrows(SpaceStorageProviderClient.Rejected::class.java) {
                            provider.download(permission.grant) { fail("Provider should reject before exposing any bytes") }
                        }
                        assertTrue(runCatching { receiver.confirmFileDownload(space, channel, original, permission) }.isFailure)
                        assertTrue(runCatching { receiver.fileDownloadPermission(space, channel, original) }.isFailure)
                    }
                }
            }
        }
    }
}
