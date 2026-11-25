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
import nl.davidengelen.redsquare.spaces.SpaceStorageProviderClient
import nl.davidengelen.redsquare.spaces.SpaceVisibility
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

class SpaceFileLibraryTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000), ZoneOffset.UTC)

    @Test fun onlyPublishedObjectsAreDownloadableAndArchivingRejectsAnInFlightUpload() = runBlocking<Unit> {
        val authority = SaitoWallet.create()
        val owner = SaitoWallet.create()
        val reader = SaitoWallet.create()
        val provider = SaitoWallet.create()
        val endpoint = "https://files.example.test"
        val directory = files.newFolder().toPath()
        fun server(node: SpacesServiceStore) = SpaceFileProviderServer(directory, provider, endpoint, authority.publicKey,
            clock, 0, 4194304, 600000, node::checkFileAccess)
            .also(SpaceFileProviderServer::start)
        fun http(port: Int) = SpaceStorageProviderClient(endpoint) { url ->
            assertEquals("files.example.test", url.host)
            URL("http://127.0.0.1:$port${url.file}").openConnection() as HttpURLConnection
        }

        SpacesServiceStore(files.newFolder().toPath(), authority, clock).use { node ->
            fun client(wallet: SaitoWallet, store: SqliteClientFixture) = SpaceClient(wallet, "https://community.example.test",
                authority.publicKey, store, clock) { node.exchange(it.envelope()) }
            val sourceStore = files.newFile().absolutePath
            val source = ByteArray(1_012_345) { (it % 243).toByte() }
            val space: String
            val channel: String
            val accepted: String
            server(node).use { peer ->
                SqliteClientFixture(sourceStore).use { store ->
                    val author = client(owner, store)
                    space = author.create(SpaceProfile("Library", visibility = SpaceVisibility.PUBLIC),
                        listOf(SpaceChannelProfile("Files", SpaceChannelKind.FILE)))
                    author.flush(space)
                    author.refresh(space)
                    channel = author.channels(space).single().id

                    val storage = http(peer.port)
                    val offer = storage.offer(space, owner.publicKey)
                    val consent = author.approveFileStorage(space, offer)
                    author.flush(space)
                    val approval = requireNotNull(author.fileStorageApproval(space, consent))
                    val lease = storage.commit(approval)
                    author.activateFileStorage(space, approval, lease)
                    author.flush(space)
                    val scope = SpaceFileCipher.Scope(authority.publicKey, space, channel)
                    suspend fun stage(): Pair<SpaceFileDescriptor, SpaceFileStage> {
                        val encrypted = ByteArrayOutputStream()
                        val sealed = SpaceFileCipher.seal(scope, source.size.toLong(), source.inputStream(), encrypted)
                        val access = author.fileUploadPermission(space, channel, sealed)
                        val confirmation = SpaceFileStage.read(storage.put(access, encrypted.toByteArray().inputStream()),
                            authority.publicKey, provider.publicKey)
                        return SpaceFileDescriptor("Meeting notes.txt", "text/plain", sealed) to confirmation
                    }
                    val (file, proof) = stage()
                    assertEquals(emptyList<SpaceClient.Message>(), author.timeline(space, channel = channel))
                    accepted = author.publishFile(space, channel, file, proof)
                    assertEquals(accepted, author.publishFile(space, channel, file, proof))
                    assertTrue(runCatching { author.fileDownloadPermission(space, channel, accepted) }.isFailure)
                    author.flush(space)

                    val (late, held) = stage()
                    val queued = author.publishFile(space, channel, late, held)
                    // Hold the original while the receiving node accepts archive first.
                    SqliteClientFixture(files.newFile().absolutePath).use { secondOwnerStore ->
                        val secondOwner = client(owner, secondOwnerStore)
                        secondOwner.openPublic(space); secondOwner.refresh(space)
                        secondOwner.archiveChannel(space, channel, true)
                        secondOwner.flush(space)
                    }
                    author.flush(space)
                    assertTrue(author.timeline(space, channel = channel).single { it.id == queued }.rejected)
                }
            }
            // Independently reload the signed file record and decrypt through a restarted HTTP provider.
            server(node).use { peer ->
                SqliteClientFixture(files.newFile().absolutePath).use { readerStore ->
                    val receiving = client(reader, readerStore)
                    receiving.openPublic(space); receiving.joinPublic(space); receiving.flush(space); receiving.refresh(space)
                    val row = receiving.timeline(space, channel = channel).single()
                    assertEquals(accepted, row.id)
                    val file = SpaceFileDescriptor.read(requireNotNull(row.text))
                    assertEquals("Meeting notes.txt", file.name)
                    assertEquals(owner.publicKey, row.author)
                    val download = receiving.fileDownloadPermission(space, channel, row.id)
                    val recovered = ByteArrayOutputStream()
                    http(peer.port).download(download.grant) {
                        SpaceFileCipher.open(SpaceFileCipher.Scope(authority.publicKey, space, channel), file.sealed, it, recovered)
                    }
                    assertArrayEquals(source, recovered.toByteArray())
                    receiving.confirmFileDownload(space, channel, row.id, download)
                }
            }
        }
    }
}
