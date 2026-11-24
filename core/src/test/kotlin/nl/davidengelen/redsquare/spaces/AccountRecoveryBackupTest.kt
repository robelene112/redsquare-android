package nl.davidengelen.redsquare.spaces

import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class AccountRecoveryBackupTest {
    private val owner = SaitoWallet.create()
    private val node = SaitoWallet.create()
    private val passphrase = "a synthetic recovery passphrase".toCharArray()
    private val scope = SpaceCrypto.Scope(node.publicKey, "a".repeat(64), "b".repeat(64))

    @Test fun sealedBackupRestoresIdentityAndOldArchiveWithoutRestoringSendingState() {
        val previous = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope)
        val received = previous.publish(owner, "message", "d".repeat(64), "e".repeat(128), "Archived recovery content", 1)
        val history = SpaceHistoryKeys.restore(previous.historyBackup(), owner.publicKey)
        val bytes = AccountRecoveryBackup.seal(owner, listOf(history), passphrase)

        assertTrue(AccountRecoveryBackup.recognizes(bytes))
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(owner.publicKey))
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("archive_keys"))
        val restored = AccountRecoveryBackup.open(bytes, passphrase)
        assertEquals(owner.publicKey, restored.wallet.publicKey)
        assertEquals(scope, restored.history.single().scope)
        assertEquals("Archived recovery content", restored.history.single().read(received.envelope()).text)

        val replacement = SpaceCrypto.device(owner.publicKey, "1".repeat(64)).create(scope.copy(generation = "f".repeat(64)))
        val replacementRecord = replacement.publish(owner, "message", "2".repeat(64), "3".repeat(128), "New device content", 2)
        assertThrows(IllegalArgumentException::class.java) { restored.history.single().read(replacementRecord.envelope()) }
        assertThrows(IllegalArgumentException::class.java) { replacement.read(received.envelope()) }
    }

    @Test fun walletOnlyBackupHasAnExplicitEmptyHistoryAndRejectsBadAuthentication() {
        val bytes = AccountRecoveryBackup.seal(owner, emptyList(), passphrase)
        val restored = AccountRecoveryBackup.open(bytes, passphrase)
        assertEquals(owner.publicKey, restored.wallet.publicKey)
        assertTrue(restored.history.isEmpty())
        assertThrows(AEADBadTagException::class.java) {
            AccountRecoveryBackup.open(bytes, "a different passphrase".toCharArray())
        }
        val corrupted = bytes.copyOf()
        corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { AccountRecoveryBackup.open(corrupted, passphrase) }
        assertThrows(IllegalArgumentException::class.java) { AccountRecoveryBackup.open(bytes.copyOf(12), passphrase) }
        assertFalse(AccountRecoveryBackup.recognizes(owner.exportJson().toByteArray()))
    }

    @Test fun refusesArchiveKeysFromAnotherIdentityAndDuplicateScopes() {
        val history = SpaceHistoryKeys.restore(
            SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope).historyBackup(), owner.publicKey
        )
        assertThrows(IllegalArgumentException::class.java) {
            AccountRecoveryBackup.seal(node, listOf(history), passphrase)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountRecoveryBackup.seal(owner, listOf(history, history), passphrase)
        }
    }
}
