package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupChatTest {
    @Test fun creationMatchesBothPinnedExistingWebConstructorsAndTheirSignedBytes() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val creator = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe reference identity. Never fund.
        fixture.getAsJsonArray("vectors").forEach { value ->
            val reference = value.asJsonObject
            val invitees = reference.getAsJsonArray("invitees").map { it.asString }
            val transaction = GroupChat.create(creator, reference.get("groupId").asString,
                reference.get("name").asString, listOf(creator.publicKey) + invitees, reference.get("timestamp").asLong)
            assertEquals(reference.get("envelope").asString, transaction.envelope())
            assertEquals(reference.get("signature").asString, transaction.id)
            val signed = SaitoEnvelope.decode(transaction.envelope())
            assertEquals(reference.get("admin").asString, signed.author)
            assertEquals(listOf(creator.publicKey) + invitees, signed.recipients)
        }
    }

    @Test fun verifiedCreationBuildsMembershipAndRejectsAnAdminClaimFromAnotherSigner() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val member = reference.getAsJsonArray("invitees")[0].asString
        val creator = reference.get("admin").asString
        val group = GroupChat.readCreation(reference.get("envelope").asString)
        assertEquals(reference.get("groupId").asString, group.id)
        assertEquals(reference.get("name").asString, group.name)
        assertEquals(mapOf(creator to ChatGroupRole.ADMIN, member to ChatGroupRole.MEMBER), group.memberRoles)
        val claimed = SaitoEnvelope.decode(reference.get("envelope").asString).body.deepCopy()
        val outsider = SaitoWallet.fromPrivateKey("0".repeat(63) + "4") // Public unsafe fixture only.
        val forged = SaitoTransaction.create(outsider, claimed, reference.get("timestamp").asLong, listOf(member))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            GroupChat.readCreation(forged.envelope())
        }
    }

    @Test fun anInvitationMemberCanAuthenticateAvailableCreationBeforeJoining() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val laterMember = SaitoWallet.fromPrivateKey("0".repeat(63) + "4") // Public unsafe fixture only.
        val group = GroupChat.readCreation(reference.get("envelope").asString)
        assertEquals(reference.get("groupId").asString, group.id)
        assertEquals(ChatGroupRole.ADMIN, group.memberRoles[reference.get("admin").asString])
        org.junit.Assert.assertFalse(group.memberRoles.containsKey(laterMember.publicKey))
    }

    @Test fun anAuthenticatedCreationCannotPublishAnInvalidPublicKeyAsAMember() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("malformedRecipients")[0].asJsonObject }
        val envelope = reference.get("envelope").asString
        assertEquals(reference.get("signature").asString, SaitoEnvelope.decode(envelope).id)
        org.junit.Assert.assertFalse(SaitoWallet.isPublicKey(reference.get("invalidRecipient").asString))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { GroupChat.readCreation(envelope) }
    }
}
