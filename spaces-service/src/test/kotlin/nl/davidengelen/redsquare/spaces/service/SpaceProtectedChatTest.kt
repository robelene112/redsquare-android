package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceProtectedChatTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)

    @Test fun protectedDirectChatRequiresItsExactParticipantAndCannotBeMadePublic() = runBlocking<Unit> {
        val owner = SaitoWallet.create()
        val peer = SaitoWallet.create()
        val outsider = SaitoWallet.create()
        val service = SaitoWallet.create()
        var supportsProtectedChat = false
        SpacesServiceStore(files.newFolder("protected-chat-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("protected-chat-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("protected-chat-peer.sqlite").absolutePath).use { peerStore ->
                    var original: String? = null
                    val writer = SpaceClient(owner, "fixture-node", service.publicKey, ownerStore, clock) { tx ->
                        val originalBody = SaitoEnvelope.decode(tx.envelope()).body
                        if (originalBody.get("content_kind")?.asString == "message") original = tx.envelope()
                        val response = remote.exchange(tx.envelope())
                        val reply = SaitoEnvelope.decode(response).body
                        if (supportsProtectedChat || !reply.has("protected_chat_v1")) response
                        else SaitoTransaction.createSpaceReply(service, reply.deepCopy().apply {
                            remove("protected_chat_v1")
                        }, clock.millis(), owner.publicKey).envelope()
                    }
                    val reader = SpaceClient(peer, "fixture-node", service.publicKey, peerStore, clock) { remote.exchange(it.envelope()) }
                    try {
                        writer.createProtectedChat("Direct chat with an unsupported node", peer.publicKey)
                        fail("The service did not advertise protected conversations")
                    } catch (_: IllegalArgumentException) { }
                    assertTrue(writer.communities().isEmpty())
                    supportsProtectedChat = true
                    val space = writer.createProtectedChat("Confidential direct chat", peer.publicKey)
                    writer.flush(space)
                    assertEquals(peer.publicKey, writer.community(space).protectedChat?.directPeer)
                    assertThrows(IllegalArgumentException::class.java) { writer.invite(space) }
                    assertThrows(IllegalArgumentException::class.java) { writer.inviteFor(space, outsider.publicKey) }
                    assertThrows(IllegalArgumentException::class.java) { writer.changeVisibility(space, SpaceVisibility.PUBLIC) }
                    assertTrue(writer.discover().items.none { it.id == space })
                    val invite = writer.inviteFor(space, peer.publicKey)
                    writer.flush(space)
                    reader.join(invite); reader.flush(space)
                    writer.admitPending(space); writer.flush(space)
                    reader.refresh(space)
                    assertEquals(peer.publicKey, reader.community(space).protectedChat?.directPeer)
                    val text = "A real protected direct message"
                    val messageId = writer.send(space, text)
                    writer.flush(space); reader.refresh(space)
                    val wire = SaitoEnvelope.decode(requireNotNull(original)).body
                    assertEquals("content", wire.get("kind").asString)
                    assertTrue(wire.has("ciphertext"))
                    assertFalse(wire.has("text"))
                    assertFalse(wire.toString().contains(text))
                    assertEquals(text, reader.message(space, messageId)?.text)
                }
            }
        }
    }

    @Test fun aProtectedGroupAdmitsOnlyAddressedWalletsAndEveryMemberReadsTheSameMessage() = runBlocking<Unit> {
        val service = SaitoWallet.create()
        val owner = SaitoWallet.create()
        val bob = SaitoWallet.create()
        val carol = SaitoWallet.create()
        val outsider = SaitoWallet.create()
        SpacesServiceStore(files.newFolder("protected-group-service").toPath(), service, clock).use { remote ->
            val stores = listOf(owner, bob, carol, outsider).associateWith {
                SqliteClientFixture(files.newFile().absolutePath)
            }
            try {
                val clients = stores.mapValues { (wallet, store) ->
                    SpaceClient(wallet, "fixture-node", service.publicKey, store, clock) { remote.exchange(it.envelope()) }
                }
                val founder = clients.getValue(owner)
                val space = founder.createProtectedChat("Independent protected group")
                founder.flush(space)
                for (member in listOf(bob, carol)) {
                    val invite = founder.inviteFor(space, member.publicKey)
                    founder.flush(space)
                    try {
                        clients.getValue(outsider).join(invite, expectProtectedChat = true)
                        fail("An unaddressed wallet must not use a forwarded invitation")
                    } catch (_: SpaceClient.ReadDenied) { }
                    val participant = clients.getValue(member)
                    participant.join(invite, expectProtectedChat = true)
                    participant.flush(space); founder.admitPending(space); founder.flush(space)
                    participant.refresh(space)
                }
                val writer = clients.getValue(carol)
                val message = writer.send(space, "Shared protected group message")
                writer.flush(space)
                listOf(owner, bob).forEach { wallet ->
                    val reader = clients.getValue(wallet)
                    reader.refresh(space)
                    assertEquals("Shared protected group message", reader.message(space, message)?.text)
                }
                assertTrue(clients.getValue(outsider).communities().isEmpty())
            } finally {
                stores.values.forEach { it.close() }
            }
        }
    }

    @Test fun replacementRetiresTheOldDeviceAndRetriesTheOriginalAcrossStoreReopen() = runBlocking<Unit> {
        val owner = SaitoWallet.create()
        val peer = SaitoWallet.create()
        val service = SaitoWallet.create()
        val replacementFile = files.newFile("replacement-device.sqlite").absolutePath
        val originals = mutableListOf<String>()
        var loseAcknowledgement = true
        SpacesServiceStore(files.newFolder("replacement-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("retired-device.sqlite").absolutePath).use { oldStore ->
                SqliteClientFixture(files.newFile("remaining-device.sqlite").absolutePath).use { remainingStore ->
                    val old = SpaceClient(owner, "fixture-node", service.publicKey, oldStore, clock) { remote.exchange(it.envelope()) }
                    val remaining = SpaceClient(peer, "fixture-node", service.publicKey, remainingStore, clock) { remote.exchange(it.envelope()) }
                    fun replacement(store: SqliteClientFixture) = SpaceClient(owner, "fixture-node", service.publicKey, store, clock) { txn ->
                        val envelope = txn.envelope()
                        val result = remote.exchange(envelope)
                        if (SaitoEnvelope.decode(envelope).body.get("kind")?.asString == "replace-device") {
                            originals += envelope
                            if (loseAcknowledgement) {
                                loseAcknowledgement = false
                                throw IOException("Fixture drops the accepted signed reply")
                            }
                        }
                        result
                    }
                    val space = old.createProtectedChat("Continuing chat", peer.publicKey)
                    old.flush(space)
                    val link = old.inviteFor(space, peer.publicKey)
                    old.flush(space)
                    assertEquals(owner.publicKey, remaining.preview(link).owner)
                    remaining.join(link, expectProtectedChat = true)
                    remaining.flush(space); old.admitPending(space); old.flush(space); remaining.refresh(space)
                    val historicalId = old.send(space, "Delivered before replacement")
                    old.flush(space); remaining.refresh(space)
                    val retiredDraft = old.send(space, "Signed before replacement while offline")
                    SqliteClientFixture(replacementFile).use { firstStore ->
                        val first = replacement(firstStore)
                        assertEquals(space, first.requestDeviceReplacement(link, expectProtectedChat = true))
                        assertTrue(first.community(space).replacingDevice)
                        try {
                            first.flush(space)
                            fail("The first acknowledgement should be lost")
                        } catch (_: IOException) { }
                    }
                    remaining.admitPending(space); remaining.flush(space)
                    SqliteClientFixture(replacementFile).use { reopenedStore ->
                        val fresh = replacement(reopenedStore)
                        assertTrue(fresh.community(space).replacingDevice)
                        fresh.flush(space)
                        assertEquals(2, originals.size)
                        assertEquals(originals.first(), originals.last())
                        fresh.refresh(space)
                        assertFalse(fresh.community(space).replacingDevice)
                        assertTrue(fresh.community(space).canSend)
                        assertEquals("Delivered before replacement", fresh.message(space, historicalId)?.text)
                        old.refresh(space); old.flush(space)
                        assertTrue(old.community(space).deviceReplaced)
                        assertFalse(old.community(space).canSend)
                        assertTrue(old.message(space, retiredDraft)?.rejected == true)
                        val newId = remaining.send(space, "Only the continuing devices can read this")
                        remaining.flush(space); fresh.refresh(space); old.refresh(space)
                        assertEquals("Only the continuing devices can read this", fresh.message(space, newId)?.text)
                        assertNull(old.message(space, newId))
                        val reply = fresh.send(space, "Fresh device can send securely")
                        fresh.flush(space); remaining.refresh(space)
                        assertEquals("Fresh device can send securely", remaining.message(space, reply)?.text)
                    }
                }
            }
        }
    }
}
