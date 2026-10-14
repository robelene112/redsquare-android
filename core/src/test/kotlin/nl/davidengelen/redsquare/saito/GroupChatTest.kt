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

    @Test fun joiningAddressesTheGroupAndDerivedAdminWithCompatibleSignedFields() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-join-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val joiner = SaitoWallet.fromPrivateKey("0".repeat(63) + "4") // Public unsafe fixture only.
        fixture.getAsJsonArray("vectors").forEach { value ->
            val expected = value.asJsonObject
            val payload = com.google.gson.JsonObject().apply {
                addProperty("id", expected.get("groupId").asString)
                addProperty("name", "Known invitation")
                addProperty("sender", expected.get("inviter").asString)
                addProperty("admin", expected.get("admin").asString)
            }
            val invite = GroupInvitation.parse(java.util.Base64.getEncoder().encodeToString(payload.toString().toByteArray(Charsets.UTF_8)))
            val tx = GroupChat.join(joiner, invite, expected.get("timestamp").asLong)
            assertEquals(expected.get("envelope").asString, tx.envelope())
            assertEquals(expected.get("signature").asString, tx.id)
            val signed = SaitoEnvelope.decode(tx.envelope())
            assertEquals(expected.get("author").asString, signed.author)
            assertEquals(expected.get("groupId").asString, signed.recipients.first())
        }
    }

    @Test fun leavingSignsOnlyTheCurrentMemberWithCompatibleGroupRecipients() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-leave-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "4") // Public unsafe fixture only.
        fixture.getAsJsonArray("vectors").forEach { value ->
            val expected = value.asJsonObject
            val tx = GroupChat.leave(member, expected.get("groupId").asString, expected.get("timestamp").asLong)
            assertEquals(expected.get("envelope").asString, tx.envelope())
            assertEquals(expected.get("signature").asString, tx.id)
            val signed = SaitoEnvelope.decode(tx.envelope())
            assertEquals(member.publicKey, signed.body.get("member_id").asString)
            assertEquals(expected.get("groupId").asString, signed.recipients.first())
        }
    }

    @Test fun joiningAndSelfLeavingRetainOriginalControlsWithoutReplayRejoining() {
        fun vector(file: String) = requireNotNull(javaClass.getResourceAsStream(file)).bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject
        }
        val creation = vector("/native-group-chat-creation-reference.json")
        val join = vector("/native-group-chat-join-reference.json")
        val leave = vector("/native-group-chat-leave-reference.json")
        val original = GroupChat.readCreation(creation.get("envelope").asString)
        val member = join.get("author").asString
        val joined = GroupChat.applyControl(original, join.get("envelope").asString)
        assertEquals(ChatGroupRole.MEMBER, joined.memberRoles[member])
        val left = GroupChat.applyControl(joined, leave.get("envelope").asString)
        assertEquals(ChatGroupRole.LEFT, left.memberRoles[member])
        val replayed = GroupChat.applyControl(left, join.get("envelope").asString)
        assertEquals(ChatGroupRole.LEFT, replayed.memberRoles[member])
        assertEquals(listOf(join.get("envelope").asString, leave.get("envelope").asString), replayed.controls)
        assertEquals(original.creationEnvelope, replayed.creationEnvelope)
    }

    @Test fun aRemovedMemberCannotClearTheBanWithASelfLeaveAndNewJoin() {
        val creation = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val join = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-join-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val admin = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures only.
        val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        val initial = GroupChat.readCreation(creation.get("envelope").asString)
        val joined = GroupChat.applyControl(initial, join.get("envelope").asString)
        val remove = com.google.gson.JsonObject().apply {
            addProperty("module", "Chat"); addProperty("request", "chat remove")
            addProperty("group_id", initial.id); addProperty("member_id", member.publicKey)
        }
        val banned = GroupChat.applyControl(joined, SaitoTransaction.create(admin, remove, 1700000123457, listOf(initial.id)).envelope())
        assertEquals(ChatGroupRole.REMOVED, banned.memberRoles[member.publicKey])
        val afterSelfLeave = GroupChat.applyControl(banned, GroupChat.leave(member, initial.id, 1700000123458).envelope())
        assertEquals(ChatGroupRole.REMOVED, afterSelfLeave.memberRoles[member.publicKey])
        val payload = com.google.gson.JsonObject().apply {
            addProperty("id", initial.id); addProperty("name", initial.name)
            addProperty("sender", admin.publicKey); addProperty("admin", admin.publicKey)
        }
        val invite = GroupInvitation.parse(java.util.Base64.getEncoder().encodeToString(payload.toString().toByteArray(Charsets.UTF_8)))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            GroupChat.applyControl(afterSelfLeave, GroupChat.join(member, invite, 1700000123459).envelope())
        }
    }

    @Test fun savedOriginalsReconstructMembershipAndRejectUnauthorizedControlClaims() {
        fun envelope(file: String) = requireNotNull(javaClass.getResourceAsStream(file)).bufferedReader().use {
            JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject.get("envelope").asString
        }
        val creation = envelope("/native-group-chat-creation-reference.json")
        val join = envelope("/native-group-chat-join-reference.json")
        val leave = envelope("/native-group-chat-leave-reference.json")
        val restored = GroupChat.restore(creation, listOf(join, leave, join))
        val member = SaitoEnvelope.decode(join).author
        assertEquals(ChatGroupRole.LEFT, restored.memberRoles[member])
        assertEquals(listOf(join, leave), restored.controls)
        assertEquals(creation, restored.creationEnvelope)
        val attacker = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe member fixture only.
        val creator = SaitoEnvelope.decode(creation).author
        val forged = com.google.gson.JsonObject().apply {
            addProperty("module", "Chat"); addProperty("request", "chat remove")
            addProperty("group_id", restored.id); addProperty("member_id", creator)
        }
        val badControl = SaitoTransaction.create(attacker, forged, 1700000123460, listOf(restored.id)).envelope()
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            GroupChat.restore(creation, listOf(join, leave, badControl))
        }
        assertEquals(ChatGroupRole.ADMIN, restored.memberRoles[creator])
    }
    @Test fun confirmedUnsignedInvitationRetainsItsTrustOriginWithoutInventingSignedCreation() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val invitation = GroupInvitation.parse(fixture.getAsJsonArray("invitations")[1].asJsonObject.get("link").asString)
        val joiner = SaitoWallet.fromPrivateKey("0".repeat(63) + "4") // Public unsafe fixture only.
        val join = GroupChat.join(joiner, invitation, 1700000123456).envelope()
        val group = GroupChat.confirmInvitation(invitation, join)
        org.junit.Assert.assertNull(group.creationEnvelope)
        assertEquals(invitation, group.confirmedInvitation)
        assertEquals(mapOf(invitation.admin to ChatGroupRole.ADMIN, joiner.publicKey to ChatGroupRole.MEMBER), group.memberRoles)
        org.junit.Assert.assertFalse(group.memberRoles.containsKey(invitation.sender))
        val leave = GroupChat.leave(joiner, invitation.id, 1700000123457).envelope()
        val restored = GroupChat.restore(invitation, join, listOf(leave, join))
        assertEquals(ChatGroupRole.LEFT, restored.memberRoles[joiner.publicKey])
        assertEquals(listOf(join, leave), restored.controls)
        org.junit.Assert.assertNull(restored.creationEnvelope)
        val altered = SaitoEnvelope.decode(join).body.deepCopy().apply { addProperty("invited_by", joiner.publicKey) }
        val mismatch = SaitoTransaction.createTo(joiner, altered, 1700000123458, invitation.id,
            listOf(invitation.admin, invitation.sender)).envelope()
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            GroupChat.confirmInvitation(invitation, mismatch)
        }
    }

    @Test fun namedGroupMessagesStayPlaintextWithBothPinnedWebSignaturesAndRejectRemovedSenders() {
        val creation = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val group = GroupChat.readCreation(creation.get("envelope").asString)
        val creator = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixture only.
        val fixture = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-message-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        fixture.getAsJsonArray("vectors").forEach { value ->
            val expected = value.asJsonObject
            val tx = GroupChat.message(creator, group, expected.get("text").asString, expected.get("timestamp").asLong,
                expected.getAsJsonArray("mentioned").map { it.asString })
            assertEquals(expected.get("envelope").asString, tx.envelope())
            assertEquals(expected.get("signature").asString, tx.id)
            val received = GroupChat.readMessage(group, expected.get("envelope").asString)
            assertEquals(expected.get("text").asString, received.text)
            assertEquals(creator.publicKey, received.author)
            assertEquals(group.id, received.groupId)
            assertEquals(expected.get("timestamp").asLong, received.createdAt)
            org.junit.Assert.assertTrue(SaitoEnvelope.decodePayload(tx.envelope()).body.isJsonObject)
        }
        val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val original = GroupChat.message(member, group, "Public message", 1700000123457).envelope()
        val remove = com.google.gson.JsonObject().apply {
            addProperty("module", "Chat"); addProperty("request", "chat remove")
            addProperty("group_id", group.id); addProperty("member_id", member.publicKey)
        }
        val banned = GroupChat.applyControl(group, SaitoTransaction.create(creator, remove, 1700000123458, listOf(group.id)).envelope())
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { GroupChat.readMessage(banned, original) }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { GroupChat.message(member, banned, "No", 1700000123459) }
        val wrong = SaitoEnvelope.decode(original).body.deepCopy().apply { addProperty("group_id", member.publicKey) }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            GroupChat.readMessage(group, SaitoTransaction.create(member, wrong, 1700000123460, listOf(group.id)).envelope())
        }
    }

}
