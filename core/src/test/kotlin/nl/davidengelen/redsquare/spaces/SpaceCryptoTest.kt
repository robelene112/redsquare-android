package nl.davidengelen.redsquare.spaces

import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.*
import org.junit.Test

class SpaceCryptoTest {
    @Test fun encryptedCategoryMetadataCannotBeRetargetedEvenWithANewValidEnvelopeSignature() {
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val scope = SpaceCrypto.Scope(service.publicKey, "a".repeat(64), "b".repeat(64))
        val keys = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope)
        val metadata = keys.publish(owner, "category-profile", "d".repeat(64), "e".repeat(128),
            "An actual private category name", 1_700_000_000_000, targetCategoryId = "f".repeat(64))
        assertEquals("An actual private category name", keys.read(metadata.envelope()).text)
        val body = SaitoEnvelope.decode(metadata.envelope()).body.deepCopy().apply { addProperty("target_category_id", "1".repeat(64)) }
        val redirected = SaitoTransaction.create(owner, body, 1_700_000_000_000, listOf(service.publicKey))
        assertEquals(owner.publicKey, SaitoEnvelope.decode(redirected.envelope()).author)
        assertThrows(javax.crypto.AEADBadTagException::class.java) { keys.read(redirected.envelope()) }
    }

    @Test fun encryptedChannelMetadataCannotBeRetargetedEvenWithANewValidEnvelopeSignature() {
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val scope = SpaceCrypto.Scope(service.publicKey, "a".repeat(64), "b".repeat(64))
        val keys = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope)
        val metadata = keys.publish(owner, "channel-profile-edit", "d".repeat(64), "e".repeat(128),
            "An actual encrypted private profile", 1_700_000_000_000, targetChannelId = "f".repeat(64))
        assertEquals("An actual encrypted private profile", keys.read(metadata.envelope()).text)
        val earlier = keys.publish(owner, "channel-profile-edit", "2".repeat(64), "e".repeat(128),
            "Earlier own-group profile", 1_700_000_000_000)
        assertFalse(SaitoEnvelope.decode(earlier.envelope()).body.has("target_channel_id"))
        assertEquals("Earlier own-group profile", SpaceCrypto.restore(keys.save()).read(earlier.envelope()).text)
        val body = SaitoEnvelope.decode(metadata.envelope()).body.deepCopy().apply { addProperty("target_channel_id", "1".repeat(64)) }
        val retargeted = SaitoTransaction.create(owner, body, 1_700_000_000_000, listOf(service.publicKey))
        assertEquals(owner.publicKey, SaitoEnvelope.decode(retargeted.envelope()).author)
        assertThrows(javax.crypto.AEADBadTagException::class.java) { keys.read(retargeted.envelope()) }
    }

    @Test fun portableHistoryKeysRecoverOldContentWithoutCloningAnActiveSenderIntoTheFreshGeneration() {
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures only.
        val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val scope = SpaceCrypto.Scope(service.publicKey, "a".repeat(64), "b".repeat(64))
        val old = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope)
        val earlier = old.publish(owner, "message", "d".repeat(64), "e".repeat(128), "Available archive after device loss", 1_700_000_000_000)
        val backup = old.historyBackup()
        val fields = com.google.gson.JsonParser.parseString(backup.toString(Charsets.UTF_8)).asJsonObject.keySet()
        assertEquals(setOf("version", "owner", "scope", "archive_keys"), fields)
        val recovered = SpaceHistoryKeys.restore(backup, owner.publicKey)
        assertEquals("Available archive after device loss", recovered.read(earlier.envelope()).text)
        assertThrows(IllegalArgumentException::class.java) { SpaceHistoryKeys.restore(backup, service.publicKey) }
        val freshScope = scope.copy(generation = "f".repeat(64))
        assertNotEquals(scope.groupId, freshScope.groupId)
        val fresh = SpaceCrypto.device(owner.publicKey, "1".repeat(64)).create(freshScope)
        val later = fresh.publish(owner, "message", "2".repeat(64), "3".repeat(128), "A fresh operational generation", 1_700_000_000_001)
        assertEquals("A fresh operational generation", fresh.read(later.envelope()).text)
        assertThrows(IllegalArgumentException::class.java) { recovered.read(later.envelope()) }
        assertThrows(IllegalArgumentException::class.java) { fresh.read(earlier.envelope()) }
    }

    @Test fun confirmedRejectionStatesSurviveReloadAndDoNotActivateAnUnacceptedMembership() {
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures only.
        val rejectedWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val joiningWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        val scope = SpaceCrypto.Scope(service.publicKey, "a".repeat(64), "b".repeat(64))
        val original = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope)
        val rejected = SpaceCrypto.device(rejectedWallet.publicKey, "d".repeat(64))
        val attempt = original.admit(rejected.keyPackage(scope, rejectedWallet))
        val notAdmitted = rejected.join(scope, attempt.welcome, attempt.bindings)
        val afterRejection = SpaceCrypto.restore(attempt.afterRejection.save())
        assertEquals(0L, afterRejection.epoch)
        assertEquals(original.bindings, afterRejection.bindings)
        val later = SpaceCrypto.device(joiningWallet.publicKey, "e".repeat(64))
        val accepted = afterRejection.admit(later.keyPackage(scope, joiningWallet))
        val member = later.join(scope, accepted.welcome, accepted.bindings)
        val message = accepted.session.publish(owner, "message", "f".repeat(64), "1".repeat(128), "Only the accepted membership", 1_700_000_000_000)
        assertEquals("Only the accepted membership", member.read(message.envelope()).text)
        assertThrows(javax.crypto.AEADBadTagException::class.java) { notAdmitted.read(message.envelope()) }
        val removal = accepted.session.remove(member.credential)
        val retained = SpaceCrypto.restore(removal.afterRejection.save())
        assertEquals(1L, retained.epoch)
        assertEquals(accepted.bindings, retained.bindings)
        val afterFailedRemoval = retained.publish(owner, "message", "2".repeat(64), "3".repeat(128), "Removal was not accepted", 1_700_000_000_001)
        assertEquals("Removal was not accepted", member.read(afterFailedRemoval.envelope()).text)
    }

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
