package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.AccountRecoveryBackup
import nl.davidengelen.redsquare.spaces.SpaceClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceRecoveryBackupTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)

    @Test fun replacementDeviceReadsItsRecoveredArchiveWhileIndependentHistoryGrantIsHeld() = runBlocking<Unit> {
        val owner = SaitoWallet.create()
        val member = SaitoWallet.create()
        val service = SaitoWallet.create()
        val passphrase = "isolated fixture phrase recovery".toCharArray()
        val node = "fixture-node"
        val scopeIdentities = mutableListOf<String>()
        var withholdHistory = false
        var historyHeld = false
        SpacesServiceStore(files.newFolder("recovery-service").toPath(), service, clock).use { remote ->
            SqliteClientFixture(files.newFile("recovery-owner.sqlite").absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile("recovery-original.sqlite").absolutePath).use { originalStore ->
                    SqliteClientFixture(files.newFile("recovery-replacement.sqlite").absolutePath).use { replacementStore ->
                        val writer = SpaceClient(owner, node, service.publicKey, ownerStore, clock) { tx ->
                            val body = SaitoEnvelope.decode(tx.envelope()).body
                            if (withholdHistory && body.get("content_kind")?.asString == "history-keys") {
                                historyHeld = true
                                throw IOException("Hold the independent history grant")
                            }
                            remote.exchange(tx.envelope())
                        }
                        fun memberClient(wallet: SaitoWallet, store: SqliteClientFixture) =
                            SpaceClient(wallet, node, service.publicKey, store, clock) { tx ->
                                val body = SaitoEnvelope.decode(tx.envelope()).body
                                if (body.get("kind")?.asString == "join")
                                    scopeIdentities.add(body.getAsJsonObject("key_package").get("device").asString)
                                remote.exchange(tx.envelope())
                            }
                        val original = memberClient(member, originalStore)
                        val space = writer.create("Recoverable encrypted history")
                        writer.flush(space)
                        val firstInvite = writer.invite(space)
                        writer.flush(space)
                        original.join(firstInvite); original.flush(space)
                        writer.admitPending(space); writer.flush(space)
                        original.refresh(space)
                        val oldMessage = original.send(space, "Message from the original device")
                        original.flush(space)
                        val backup = AccountRecoveryBackup.seal(member, original.exportHistoryKeys(), passphrase)

                        original.leave(space); original.flush(space)
                        writer.refresh(space); writer.admitPending(space); writer.flush(space)
                        val replacementInvite = writer.invite(space)
                        writer.flush(space)
                        val payload = AccountRecoveryBackup.open(backup, passphrase)
                        val replacement = memberClient(payload.wallet, replacementStore)
                        assertEquals(member.publicKey, payload.wallet.publicKey)
                        assertTrue(replacement.exportHistoryKeys().isEmpty())
                        replacement.join(replacementInvite); replacement.flush(space)
                        writer.admitPending(space)
                        withholdHistory = true
                        try { writer.flush(space) } catch (_: IOException) { }
                        assertTrue("The peer did not deliver the historical keys", historyHeld)
                        replacement.refresh(space)
                        assertTrue(replacement.community(space).joined)
                        val awaitingKeys = requireNotNull(replacement.message(space, oldMessage))
                        assertTrue(awaitingKeys.missingKeys)
                        assertNull(awaitingKeys.text)

                        replacement.restoreHistoryKeys(payload.history)
                        replacement.restoreHistoryKeys(payload.history)
                        val restored = requireNotNull(replacement.message(space, oldMessage))
                        assertEquals("Message from the original device", restored.text)
                        assertFalse(restored.missingKeys)
                        assertEquals(2, scopeIdentities.size)
                        assertEquals(2, scopeIdentities.distinct().size)
                        val newMessage = replacement.send(space, "Message from the replacement device")
                        replacement.flush(space); writer.refresh(space)
                        assertEquals("Message from the replacement device", writer.message(space, newMessage)?.text)
                    }
                }
            }
        }
    }
}
