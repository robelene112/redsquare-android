package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceArticleContent
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceFileAccessCheck
import nl.davidengelen.redsquare.spaces.SpaceFileCipher
import nl.davidengelen.redsquare.spaces.SpaceFileGrant
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceStorageProviderClient
import nl.davidengelen.redsquare.spaces.SpaceReportAlreadyFiled
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import nl.davidengelen.redsquare.spaces.SpaceVoiceRoom
import nl.davidengelen.redsquare.spaces.SpaceVoiceSignalKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class SpaceModerationTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun onlyCurrentStaffSeeDurablePrivateReports() = runBlocking<Unit> {
        val time = Instant.parse("2026-11-25T11:30:00Z")
        val clock = Clock.fixed(time, ZoneOffset.UTC)
        val node = SaitoWallet.create()
        val wallets = List(4) { SaitoWallet.create() }
        val stores = wallets.map { SqliteClientFixture(files.newFile().absolutePath) }
        val database = files.newFolder().toPath()
        var service = SpacesServiceStore(database, node, clock)
        try {
            val (owner, admin, member, visitor) = wallets.zip(stores).map { (wallet, store) ->
                SpaceClient(wallet, "https://spaces.example.test", node.publicKey, store, clock) { service.exchange(it.envelope()) }
            }
            val space = owner.create(SpaceProfile("Moderation", visibility = SpaceVisibility.PUBLIC))
            owner.flush(space)
            owner.refresh(space)
            val channel = owner.channels(space).first().id
            val message = owner.send(space, "Original message", channel)
            owner.flush(space)

            listOf(admin, member).forEach { client ->
                client.openPublic(space)
                client.joinPublic(space)
                client.flush(space)
                client.refresh(space)
            }
            owner.refresh(space)
            val promotion = owner.setAdministrator(space, wallets[1].publicKey, true)
            owner.flush(space)
            assertTrue(runCatching { visitor.reportMessage(space, channel, message, "Unjoined reader") }
                .exceptionOrNull() is SpaceClient.ReadDenied)
            assertTrue(runCatching { member.reportMessage(space, channel, "0".repeat(128), "Unknown message") }
                .exceptionOrNull() is SpaceClient.ReadDenied)

            val receipt = member.reportMessage(space, channel, message, "Off-topic promotion")
            assertEquals(wallets[2].publicKey, receipt.reporter)
            assertEquals(time.toEpochMilli(), receipt.receivedAt)
            assertEquals(receipt, member.reportMessage(space, channel, message, "Off-topic promotion"))
            assertTrue(runCatching { member.reportMessage(space, channel, message, "A different reason") }
                .exceptionOrNull() is SpaceReportAlreadyFiled)
            assertTrue(runCatching { member.messageReports(space) }.exceptionOrNull() is SpaceClient.ReadDenied)
            assertTrue(runCatching { visitor.messageReports(space) }.exceptionOrNull() is SpaceClient.ReadDenied)

            service.close()
            service = SpacesServiceStore(database, node, clock)
            assertEquals(listOf(receipt), admin.messageReports(space).reports)
            assertEquals(listOf(receipt), owner.messageReports(space).reports)
            assertEquals("Original message", admin.reviewReportedMessage(space, receipt)?.text)
            assertTrue(runCatching { member.reviewReportedMessage(space, receipt) }.exceptionOrNull() is SpaceClient.ReadDenied)
            assertEquals(null, owner.messageReports(space).next)
            assertTrue(runCatching { member.adminLog(space) }.exceptionOrNull() is SpaceClient.ReadDenied)
            val promoted = admin.adminLog(space).events.single { it.cursor.event == promotion }
            assertEquals("made-admin", promoted.action)
            assertEquals(wallets[0].publicKey, promoted.actor)
            assertEquals(wallets[1].publicKey, promoted.member)
            assertEquals(time.toEpochMilli(), promoted.cursor.at)
            owner.refresh(space)
            assertEquals(listOf("Original message"), owner.messages(space, channel))

            owner.setAdministrator(space, wallets[1].publicKey, false)
            owner.flush(space)
            assertTrue(runCatching { admin.messageReports(space) }.exceptionOrNull() is SpaceClient.ReadDenied)
            assertTrue(runCatching { admin.adminLog(space) }.exceptionOrNull() is SpaceClient.ReadDenied)
            assertTrue(runCatching { admin.reviewReportedMessage(space, receipt) }.exceptionOrNull() is SpaceClient.ReadDenied)
            assertEquals(listOf(receipt), owner.messageReports(space).reports)

            val reply = owner.reply(space, requireNotNull(owner.message(space, message, channel)), "Reported reply", channel)
            owner.flush(space)
            val reportedReply = member.reportMessage(space, channel, reply, "Report specifically about a reply")
            assertEquals(message, reportedReply.thread)
            assertEquals("Reported reply", owner.reviewReportedMessage(space, reportedReply)?.text)

            val actions = buildSet {
                repeat(23) { index ->
                    add(owner.setAdministrator(space, wallets[1].publicKey, index % 2 == 0))
                    owner.flush(space)
                }
            }
            val first = owner.adminLog(space)
            val second = owner.adminLog(space, requireNotNull(first.next))
            assertEquals(20, first.events.size)
            val observed = first.events + second.events
            assertEquals(observed.size, observed.map { it.cursor.event }.toSet().size)
            assertTrue(observed.all { it.cursor.at == time.toEpochMilli() })
            assertTrue(observed.map { it.cursor.event }.containsAll(actions))
            assertEquals(null, second.next)
        } finally {
            service.close()
            stores.forEach(SqliteClientFixture::close)
        }
    }

    @Test fun populatedSchemaFiveOpensWithoutChangingSignedRecords() = runBlocking<Unit> {
        val clock = Clock.fixed(Instant.parse("2026-11-25T11:30:00Z"), ZoneOffset.UTC)
        val authority = SaitoWallet.create()
        val ownerWallet = SaitoWallet.create()
        val state = files.newFolder().toPath()
        SqliteClientFixture(files.newFile().absolutePath).use { device ->
            var space: String
            var message: String
            SpacesServiceStore(state, authority, clock).use { service ->
                val owner = SpaceClient(ownerWallet, "https://spaces.example.test", authority.publicKey, device, clock) {
                    service.exchange(it.envelope())
                }
                space = owner.create(SpaceProfile("Existing public Space", visibility = SpaceVisibility.PUBLIC))
                owner.flush(space)
                message = owner.send(space, "Previously signed content")
                owner.flush(space)
            }
            val database = "jdbc:sqlite:${state.resolve("spaces.sqlite")}"
            fun signedRows(): List<Pair<String, String>> = DriverManager.getConnection(database).use { db ->
                db.createStatement().use { sql ->
                    sql.executeQuery("SELECT original,receipt FROM controls UNION ALL SELECT original,receipt FROM records ORDER BY original").use {
                        buildList { while (it.next()) add(it.getString(1) to it.getString(2)) }
                    }
                }
            }
            val original = signedRows()
            assertTrue(original.isNotEmpty())
            DriverManager.getConnection(database).use { db ->
                db.createStatement().use { sql ->
                    sql.execute("DROP TABLE space_room_admin")
                    sql.execute("DROP TABLE space_reports")
                    sql.execute("DROP INDEX controls_admin_page")
                    sql.execute("DROP INDEX joins_admin_page")
                    sql.execute("DROP INDEX records_admin_page")
                    sql.execute("PRAGMA user_version=5")
                }
            }
            repeat(2) {
                SpacesServiceStore(state, authority, clock).use { service ->
                    assertTrue(service.readiness().contains("\"schema\":6"))
                    val owner = SpaceClient(ownerWallet, "https://spaces.example.test", authority.publicKey, device, clock) {
                        service.exchange(it.envelope())
                    }
                    owner.refresh(space)
                    val channel = owner.channels(space).single().id
                    assertEquals("Previously signed content", owner.message(space, message, channel)?.text)
                    assertTrue(owner.messageReports(space).reports.isEmpty())
                }
                assertEquals(original, signedRows())
            }
        }
    }

    @Test fun timeoutAndArchiveDrainOnlyTheAuthorizedEffectsOfQueuedWork() = runBlocking<Unit> {
        val clock = Clock.fixed(Instant.parse("2026-11-25T11:30:00Z"), ZoneOffset.UTC)
        val authority = SaitoWallet.create()
        val ownerWallet = SaitoWallet.create()
        val memberWallet = SaitoWallet.create()
        val providerWallet = SaitoWallet.create()
        val endpoint = "https://moderation-files.example.test"
        val providerDirectory = files.newFolder().toPath()
        SpacesServiceStore(files.newFolder().toPath(), authority, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile().absolutePath).use { memberStore ->
                    fun client(wallet: SaitoWallet, store: SqliteClientFixture) =
                        SpaceClient(wallet, "https://spaces.example.test", authority.publicKey, store, clock) { service.exchange(it.envelope()) }
                    val owner = client(ownerWallet, ownerStore)
                    val member = client(memberWallet, memberStore)
                    val space = owner.create(SpaceProfile("Moderated community", visibility = SpaceVisibility.PUBLIC), listOf(
                        SpaceChannelProfile("General", SpaceChannelKind.CHAT), SpaceChannelProfile("Files", SpaceChannelKind.FILE),
                        SpaceChannelProfile("Articles", SpaceChannelKind.ARTICLE), SpaceChannelProfile("Room", SpaceChannelKind.VOICE)))
                    owner.flush(space)
                    owner.refresh(space)
                    val channels = owner.channels(space).associate { it.profile.kind to it.id }
                    val chat = channels.getValue(SpaceChannelKind.CHAT)
                    val filesChannel = channels.getValue(SpaceChannelKind.FILE)
                    val article = channels.getValue(SpaceChannelKind.ARTICLE)
                    val room = channels.getValue(SpaceChannelKind.VOICE)
                    member.openPublic(space)
                    member.joinPublic(space)
                    member.flush(space)
                    member.refresh(space)
                    owner.refresh(space)
                    owner.send(space, "Still readable", chat)
                    owner.flush(space)

                    val writing = member.send(space, "Queued text", chat)
                    val queuedArticle = member.publishArticle(space, SpaceArticleContent.Original("Queued article", "", "Pending body"), article)
                    val keys = SpaceVoiceSignalKey.create(SpaceVoiceSignalKey.Address(authority.publicKey, space, room), memberWallet)
                    assertTrue(member.joinVoiceRoom(space, room, keys.contact).participants.single().canSpeak)
                    val checked = AtomicInteger()
                    val bytesWhenRevoked = AtomicLong()
                    val towardPause = clock.millis() + 60_000
                    val interruptUpload = AtomicReference<suspend () -> Unit>({
                        owner.setWriteRestriction(space, memberWallet.publicKey, null, towardPause)
                        owner.flush(space)
                    })

                    SpaceFileProviderServer(providerDirectory, providerWallet, endpoint, authority.publicKey, clock, 0, 4_194_304, 600_000) { request ->
                        val permission = SpaceFileAccessCheck.read(request, authority.publicKey, clock.millis()).permission
                        if (permission.action == SpaceFileGrant.Action.PUT && checked.incrementAndGet() == 2) {
                            bytesWhenRevoked.set(Files.list(providerDirectory.resolve("objects")).use { paths ->
                                paths.filter { it.fileName.toString().endsWith(".part") }.mapToLong(Files::size).sum()
                            })
                            runBlocking { interruptUpload.get()() }
                        }
                        service.checkFileAccess(request)
                    }.also(SpaceFileProviderServer::start).use { provider ->
                        val storage = SpaceStorageProviderClient(endpoint) { url ->
                            check(url.host == "moderation-files.example.test")
                            URL("http://127.0.0.1:${provider.port}${url.file}").openConnection() as HttpURLConnection
                        }
                        val consent = owner.approveFileStorage(space, storage.offer(space, ownerWallet.publicKey))
                        owner.flush(space)
                        val approval = requireNotNull(owner.fileStorageApproval(space, consent))
                        owner.activateFileStorage(space, approval, storage.commit(approval))
                        owner.flush(space)
                        member.refresh(space, channel = filesChannel)
                        val bytes = ByteArray(2_012_345) { (it % 243).toByte() }
                        val encrypted = ByteArrayOutputStream()
                        val sealed = SpaceFileCipher.seal(SpaceFileCipher.Scope(authority.publicKey, space, filesChannel),
                            bytes.size.toLong(), bytes.inputStream(), encrypted)
                        suspend fun assertInterruptedUpload(sealedFile: SpaceFileCipher.Sealed, ciphertext: ByteArray) {
                            checked.set(0)
                            bytesWhenRevoked.set(0)
                            member.refresh(space, channel = filesChannel)
                            val permission = member.fileUploadPermission(space, filesChannel, sealedFile)
                            assertTrue(runCatching { storage.put(permission, ciphertext.inputStream()) }.isFailure)
                            assertTrue(bytesWhenRevoked.get() in 1 until sealedFile.bytes)
                            val lease = requireNotNull(owner.fileStorage(space))
                            assertEquals(0L, storage.usage(lease, owner.fileStorageUsagePermission(space, filesChannel), clock::millis).used)
                        }
                        assertInterruptedUpload(sealed, encrypted.toByteArray())

                        member.flush(space)
                        member.refresh(space, channel = chat)
                        member.refresh(space, channel = article)
                        assertTrue(member.timeline(space, channel = chat).single { it.id == writing }.rejected)
                        assertTrue(member.timeline(space, channel = article).single { it.id == queuedArticle }.rejected)
                        assertEquals(listOf("Still readable"), member.messages(space, chat))
                        assertFalse(member.community(space, chat).canSend)
                        assertTrue(member.community(space, chat).writeRestrictedSpaceWide)
                        val listening = member.renewVoiceRoom(space, room, keys.contact.session)
                        assertTrue(listening.admitted)
                        assertFalse(listening.participants.single().canSpeak)

                        owner.setWriteRestriction(space, memberWallet.publicKey, null, null)
                        owner.flush(space)
                        member.refresh(space, channel = chat)
                        val revokedText = member.send(space, "Queued before membership removal", chat)
                        interruptUpload.set {
                            owner.removeMember(space, memberWallet.publicKey)
                            owner.flush(space)
                        }
                        val nextEncrypted = ByteArrayOutputStream()
                        val nextSealed = SpaceFileCipher.seal(SpaceFileCipher.Scope(authority.publicKey, space, filesChannel),
                            bytes.size.toLong(), bytes.inputStream(), nextEncrypted)
                        assertInterruptedUpload(nextSealed, nextEncrypted.toByteArray())
                        member.flush(space)
                        member.refresh(space, channel = chat)
                        assertTrue(member.timeline(space, channel = chat).single { it.id == revokedText }.rejected)
                        assertEquals(listOf("Still readable"), member.messages(space, chat))
                        assertFalse(member.renewVoiceRoom(space, room, keys.contact.session).admitted)
                        member.joinPublic(space)
                        member.flush(space)
                        member.refresh(space, channel = chat)
                        owner.refresh(space)
                    }

                    val returning = SpaceVoiceSignalKey.create(SpaceVoiceSignalKey.Address(authority.publicKey, space, room), memberWallet)
                    assertTrue(member.joinVoiceRoom(space, room, returning.contact).admitted)
                    val archivedText = member.send(space, "Queued before channel archive", chat)
                    owner.archiveChannel(space, chat, true)
                    owner.flush(space)
                    member.flush(space)
                    member.refresh(space, channel = chat)
                    assertTrue(member.timeline(space, channel = chat).single { it.id == archivedText }.rejected)
                    assertTrue(member.renewVoiceRoom(space, room, returning.contact.session).participants.single().canSpeak)
                    owner.archiveChannel(space, room, true)
                    owner.flush(space)
                    val unavailable = member.renewVoiceRoom(space, room, returning.contact.session)
                    assertFalse(unavailable.admitted)
                    assertTrue(unavailable.participants.isEmpty())
                    assertEquals(SpaceVoiceRoom.Status.ARCHIVED, unavailable.status)
                }
            }
        }
    }
}
