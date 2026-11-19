package nl.davidengelen.redsquare.spaces

import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Test

class SpaceCryptoTest {
    @Test fun removingAMemberAdvancesPrivateContentKeysWhileRemainingMembersCanStillRead() {
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures only.
        val removedWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val remainingWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        val scope = SpaceCrypto.Scope(service.publicKey, "a".repeat(64), "b".repeat(64))
        val origin = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope)
        val departing = SpaceCrypto.device(removedWallet.publicKey, "d".repeat(64))
        val first = origin.admit(departing.keyPackage(scope, removedWallet))
        val admitted = departing.join(scope, first.welcome, first.bindings)
        val joining = SpaceCrypto.device(remainingWallet.publicKey, "e".repeat(64))
        val second = first.session.admit(joining.keyPackage(scope, remainingWallet))
        val beforeRemoval = admitted.receiveCommit(second.commit, origin.credential, second.bindings)
        val remaining = joining.join(scope, second.welcome, second.bindings)
        val previous = second.session.publish(owner, "message", "f".repeat(64), "1".repeat(128), "Before removal", 1_700_000_000_000)
        assertEquals("Before removal", beforeRemoval.read(previous.envelope()).text)
        val removal = second.session.remove(beforeRemoval.credential)
        val current = remaining.receiveCommit(removal.commit, origin.credential, removal.bindings)
        val future = removal.session.publish(owner, "message", "2".repeat(64), "3".repeat(128), "After removal", 1_700_000_000_001)
        assertEquals("After removal", current.read(future.envelope()).text)
        val oldDevice = SpaceCrypto.restore(beforeRemoval.save())
        assertThrows(SpaceCrypto.MissingKeys::class.java) { oldDevice.read(future.envelope()) }
        assertEquals("Before removal", oldDevice.read(previous.envelope()).text)
        assertThrows(IllegalArgumentException::class.java) {
            removal.session.history(owner, oldDevice.credential, "4".repeat(64), "5".repeat(128), 1_700_000_000_002)
        }
    }

    @Test fun restoringAnUnjoinedDeviceRetainsItsOriginalPublicPackageAndWalletBinding() {
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures only.
        val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val scope = SpaceCrypto.Scope(service.publicKey, "a".repeat(64), "b".repeat(64))
        val device = SpaceCrypto.device(owner.publicKey, "c".repeat(64))
        val prepared = device.keyPackage(scope, owner)
        val restored = SpaceCrypto.restore(device.save())
        assertEquals(prepared, restored.keyPackage(scope, owner))
    }

    @Test fun explicitAdmissionAndAuthenticatedHistoryGrantRecoverAnEarlierPrivateRecordAfterReload() {
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures only.
        val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val scope = SpaceCrypto.Scope(service.publicKey, "a".repeat(64), "b".repeat(64))
        val original = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope)
        val post = original.publish(owner, "message", "d".repeat(64), "e".repeat(128),
            "Available private history", 1_700_000_000_000)
        val wireBody = SaitoEnvelope.decode(post.envelope()).body
        assertFalse(wireBody.toString().contains("Available private history"))
        val joiningDevice = SpaceCrypto.device(member.publicKey, "f".repeat(64))
        val admission = original.admit(joiningDevice.keyPackage(scope, member))
        val joined = joiningDevice.join(scope, admission.welcome, admission.bindings)
        assertThrows(SpaceCrypto.MissingKeys::class.java) { joined.read(post.envelope()) }
        val grant = admission.session.history(owner, joined.credential, "1".repeat(64), "2".repeat(128), 1_700_000_000_001)
        val restored = SpaceCrypto.restore(joined.save()).acceptHistory(grant.transaction.envelope())
        val read = restored.read(post.envelope())
        assertEquals(owner.publicKey, read.author)
        assertEquals("Available private history", read.text)
    }
}
