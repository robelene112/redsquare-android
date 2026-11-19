package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpaceJournalTest {
    // Public, unsafe fixture scalars. No owner/device wallet is used.
    private val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
    private val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
    private val space = "a".repeat(64)
    private val channel = "b".repeat(64)

    @Test fun anExplicitSignedJoinWithTheBearerProofAdmitsOneMemberAndAdvancesTheChannelEpoch() {
        val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val bearer = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        val scope = SpaceCrypto.Scope(service.publicKey, space, channel)
        val creatorKeys = SpaceCrypto.device(owner.publicKey, "5".repeat(64)).create(scope)
        val memberKeys = SpaceCrypto.device(member.publicKey, "6".repeat(64))
        val keyPackage = memberKeys.keyPackage(scope, member)
        val creator = event(owner, "create", "1".repeat(64)) {
            addProperty("visibility", "private"); addProperty("channel_id", channel); addProperty("epoch", 0)
            addProperty("metadata", java.util.Base64.getEncoder().encodeToString(ByteArray(40) { 11 }))
            add("devices", devices(creatorKeys.bindings))
        }
        val invitation = event(owner, "invite", "2".repeat(64)) {
            addProperty("control_id", creator.id); addProperty("invite_key", bearer.publicKey)
            addProperty("expires_at", 1_700_604_800_000)
        }
        val join = event(member, "join", "3".repeat(64)) {
            addProperty("control_id", invitation.id); addProperty("channel_id", channel)
            addProperty("invite_id", invitation.id)
            add("key_package", JsonObject().apply {
                addProperty("wallet", member.publicKey); addProperty("device", keyPackage.deviceId)
                addProperty("package", keyPackage.packageBytes); addProperty("signer", keyPackage.signer)
                addProperty("signature", keyPackage.walletSignature)
            })
            addProperty("invite_proof", bearer.sign(("SaitoSpacesJoin/v1:${scope.groupId}:${invitation.id}:" +
                "${member.publicKey}:${"3".repeat(64)}:${keyPackage.walletSignature}").toByteArray(Charsets.UTF_8)))
        }
        val admitted = creatorKeys.admit(keyPackage)
        val admission = event(owner, "admit", "4".repeat(64)) {
            addProperty("control_id", invitation.id); addProperty("channel_id", channel); addProperty("epoch", admitted.session.epoch)
            addProperty("join", join.envelope()); addProperty("welcome", admitted.welcome); addProperty("commit", admitted.commit)
            add("devices", devices(admitted.bindings))
        }
        val journal = SpaceJournal(space, service.publicKey)
        journal.receive(receipt(creator, 0))
        assertEquals(mapOf(owner.publicKey to SpaceRole.OWNER), journal.receive(receipt(invitation, 1)).members)
        val accepted = journal.receive(receipt(admission, 2))
        assertEquals(mapOf(owner.publicKey to SpaceRole.OWNER, member.publicKey to SpaceRole.MEMBER), accepted.members)
        assertEquals(admission.id, accepted.controlId)
        assertEquals(accepted, journal.receive(receipt(admission, 2)))
        val joined = memberKeys.join(scope, admitted.welcome, admitted.bindings)
        val message = admitted.session.publish(owner, "message", "7".repeat(64), admission.id, "Welcome to the real community", 1_700_000_000_002)
        assertEquals("Welcome to the real community", joined.read(message.envelope()).text)
    }

    @Test fun anInvitationAdvancesOnlyTheAuthorizedControlChainAndDoesNotEnrollItsHolder() {
        val creator = event(owner, "create", "1".repeat(64)) {
            addProperty("visibility", "private"); addProperty("channel_id", channel)
            addProperty("epoch", 0); addProperty("metadata", java.util.Base64.getEncoder().encodeToString(ByteArray(40) { 11 }))
            add("devices", devices(mapOf("SaitoSpaces/v1:${owner.publicKey}:${"5".repeat(64)}" to java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 12 }))))
        }
        val bearer = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        val invited = event(owner, "invite", "2".repeat(64)) {
            addProperty("control_id", creator.id); addProperty("invite_key", bearer.publicKey)
            addProperty("expires_at", 1_700_604_800_000)
        }
        val journal = SpaceJournal(space, service.publicKey)
        assertThrows(IllegalArgumentException::class.java) { journal.receive(receipt(invited, 1)) }
        journal.receive(receipt(creator, 0))
        val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val unauthorized = event(member, "invite", "3".repeat(64)) {
            addProperty("control_id", creator.id); addProperty("invite_key", bearer.publicKey)
            addProperty("expires_at", 1_700_604_800_000)
        }
        assertThrows(IllegalArgumentException::class.java) { journal.receive(receipt(unauthorized, 1)) }
        val accepted = journal.receive(receipt(invited, 1))
        assertEquals(invited.id, accepted.controlId)
        assertEquals(2L, accepted.nextPosition)
        assertEquals(mapOf(owner.publicKey to SpaceRole.OWNER), accepted.members)
        assertEquals(accepted, journal.receive(receipt(invited, 1)))
        assertThrows(IllegalArgumentException::class.java) { journal.receive(receipt(invited, 2)) }
    }

    private fun event(wallet: SaitoWallet, kind: String, operationId: String, fields: JsonObject.() -> Unit) =
        SaitoTransaction.create(wallet, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
            addProperty("space_id", space); addProperty("operation_id", operationId); addProperty("kind", kind)
            fields()
        }, 1_700_000_000_000, listOf(service.publicKey))

    private fun receipt(event: SaitoTransaction, position: Long) = SaitoTransaction.create(service, JsonObject().apply {
        addProperty("module", "Spaces"); addProperty("request", "receipt"); addProperty("version", 1)
        addProperty("space_id", space); addProperty("stream", "control")
        addProperty("position", position); addProperty("event", event.envelope())
    }, 1_700_000_000_001, listOf(owner.publicKey)).envelope()

    private fun devices(bindings: Map<String, String>) = JsonObject().apply { bindings.forEach { (identity, signer) -> addProperty(identity, signer) } }

    @Test fun anAuthenticatedCreationEstablishesExactlyOneOwnerAndDuplicateReceiptDoesNotCreateAnother() {
        val event = SaitoTransaction.create(owner, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", "event"); addProperty("version", 1)
            addProperty("space_id", space); addProperty("operation_id", "c".repeat(64)); addProperty("kind", "create")
            addProperty("visibility", "private"); addProperty("channel_id", channel)
            addProperty("epoch", 0); addProperty("metadata", java.util.Base64.getEncoder().encodeToString(ByteArray(40) { 11 }))
            add("devices", devices(mapOf("SaitoSpaces/v1:${owner.publicKey}:${"5".repeat(64)}" to java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 12 }))))
        }, 1_700_000_000_000, listOf(service.publicKey))
        val receipt = SaitoTransaction.create(service, JsonObject().apply {
            addProperty("module", "Spaces"); addProperty("request", "receipt"); addProperty("version", 1)
            addProperty("space_id", space); addProperty("stream", "control")
            addProperty("position", 0); addProperty("event", event.envelope())
        }, 1_700_000_000_001, listOf(owner.publicKey))
        val journal = SpaceJournal(space, service.publicKey)
        val created = journal.receive(receipt.envelope())
        assertEquals(owner.publicKey, created.owner)
        assertEquals(mapOf(owner.publicKey to SpaceRole.OWNER), created.members)
        assertEquals(event.id, created.controlId)
        assertEquals(1L, created.nextPosition)
        assertEquals(created, journal.receive(receipt.envelope()))
    }
}
