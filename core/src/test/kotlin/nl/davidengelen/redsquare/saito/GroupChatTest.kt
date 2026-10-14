package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupChatTest {
    @Test fun olderMessagesDoNotReactivateAnObservedDepartureButAnExplicitJoinCan() {
        val reference=requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use{JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject}
        val creator=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixture only.
        val origin=GroupChat.readCreation(reference.get("envelope").asString)
        val older=GroupChat.message(creator,origin,"Before leaving",1700000123457)
        val leave=GroupChat.leave(creator,origin.id,1700000123458)
        val history=GroupChat.replay(origin,listOf(leave.envelope(),older.envelope()))
        assertEquals(listOf("Before leaving"),history.messages.map{it.text})
        assertEquals(ChatGroupRole.LEFT,history.group.memberRoles[creator.publicKey])
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java){
            GroupChat.message(creator,history.group,"Must remain left",1700000123459)
        }
        assertEquals(ChatGroupRole.LEFT,GroupChat.replay(origin,history.originals).group.memberRoles[creator.publicKey])
        val invitation=GroupInvitation(origin.id,origin.name,creator.publicKey,creator.publicKey)
        val join=GroupChat.join(creator,invitation,1700000123460)
        val rejoined=GroupChat.replay(origin,history.originals+join.envelope())
        assertEquals(ChatGroupRole.MEMBER,rejoined.group.memberRoles[creator.publicKey])
        assertEquals(listOf("Before leaving"),rejoined.messages.map{it.text})
    }


    @Test fun directRoutedAndUnsignedRelayMessagesRetainTheAuthenticatedGroupOriginal() {
        val creation=requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use{JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject}
        val reference=requireNotNull(javaClass.getResourceAsStream("/native-group-chat-message-reference.json"))
            .bufferedReader().use{JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject}
        val creator=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixture only.
        val peer=SaitoWallet.fromPrivateKey("0".repeat(63)+"2")
        val group=GroupChat.readCreation(creation.get("envelope").asString)
        val transaction=GroupChat.message(creator,group,reference.get("text").asString,reference.get("timestamp").asLong,
            reference.getAsJsonArray("mentioned").map{it.asString})
        val direct=transaction.applicationRequest(1).let{it.copyOfRange(5,it.size)}
        assertEquals(reference.get("envelope").asString,GroupChat.originalEnvelope(direct))
        val routed=transaction.broadcast(creator,peer.publicKey).let{it.copyOfRange(1,it.size)}
        assertEquals(reference.get("signature").asString,GroupChat.readMessage(group,GroupChat.originalEnvelope(routed)).id)
        fun relay(inner:com.google.gson.JsonObject):ByteArray {
            val body=com.google.gson.JsonObject().apply{addProperty("module","Relay");addProperty("request","chat relay");add("data",inner)}
            return SaitoTransaction.create(peer,body,1700000123459,listOf(creator.publicKey)).applicationRequest(1)
                .let{it.copyOfRange(5,it.size)}.also{it.fill(0,16,80)} // Existing Relay outer may be unsigned.
        }
        assertEquals(reference.get("envelope").asString,GroupChat.originalEnvelope(relay(transaction.signedJson())))
        val changed=transaction.signedJson().apply{
            val body=SaitoEnvelope.decode(reference.get("envelope").asString).body.deepCopy().apply{addProperty("message","Tampered group body")}
            addProperty("buffer",java.util.Base64.getEncoder().encodeToString(body.toString().toByteArray(Charsets.UTF_8)))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java){GroupChat.readMessage(group,GroupChat.originalEnvelope(relay(changed)))}
    }

    @Test fun observedHistoryRetainsEarlierMessagesThroughRemovalWithoutGrantingAdminOrReacceptingBannedAuthors() {
        val reference=requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use{JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject}
        val creator=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixture only.
        val participant=SaitoWallet.fromPrivateKey("0".repeat(63)+"4")
        val origin=GroupChat.readCreation(reference.get("envelope").asString)
        val invitation=GroupInvitation(origin.id,"Untrusted message name",creator.publicKey,creator.publicKey)
        val ownGroup=GroupChat.confirmInvitation(invitation,GroupChat.join(participant,invitation,1700000123456).envelope())
        val message=GroupChat.message(participant,ownGroup,"Earlier group message",1700000123457)
        val body=com.google.gson.JsonObject().apply{
            addProperty("module","Chat");addProperty("request","chat remove")
            addProperty("group_id",origin.id);addProperty("member_id",participant.publicKey)
        }
        val remove=SaitoTransaction.createTo(creator,body,1700000123458,origin.id)
        val originals=listOf(message.envelope(),message.envelope(),remove.envelope(),message.envelope())
        val history=GroupChat.replay(origin,originals)
        assertEquals(reference.get("name").asString,history.group.name)
        assertEquals(ChatGroupRole.ADMIN,history.group.memberRoles[creator.publicKey])
        assertEquals(ChatGroupRole.REMOVED,history.group.memberRoles[participant.publicKey])
        assertEquals(listOf("Earlier group message"),history.messages.map{it.text})
        assertEquals(listOf(message.envelope(),remove.envelope()),history.originals)
        assertEquals(history.messages,GroupChat.replay(origin,history.originals).messages)
        val later=GroupChat.message(participant,ownGroup,"Rejected after removal",1700000123459)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java){GroupChat.replay(origin,history.originals+later.envelope())}
        val observed=GroupChat.replay(origin,listOf(message.envelope()))
        assertEquals(ChatGroupRole.MEMBER,observed.group.memberRoles[participant.publicKey])
        assertEquals(ChatGroupRole.ADMIN,observed.group.memberRoles[creator.publicKey])
    }

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

    @Test fun onlyKnownAdminsCanPublishGroupSnapshotsAndRemovalCannotBeUndoneByAnOldSnapshot() {
        val creation = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val group = GroupChat.readCreation(creation.get("envelope").asString)
        val creator = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures only.
        val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val snapshot = com.google.gson.JsonObject().apply {
            addProperty("module", "Chat"); addProperty("request", "chat update")
            addProperty("group_id", group.id); addProperty("group_name", "Updated group")
            add("member_ids", com.google.gson.JsonObject().apply {
                addProperty(creator.publicKey, "admin"); addProperty(member.publicKey, "admin")
            })
        }
        val promoted = GroupChat.applyControl(group, SaitoTransaction.createTo(creator, snapshot, 1700000123457, group.id).envelope())
        assertEquals("Updated group", promoted.name)
        assertEquals(ChatGroupRole.ADMIN, promoted.memberRoles[member.publicKey])
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            GroupChat.applyControl(group, SaitoTransaction.createTo(member, snapshot, 1700000123458, group.id,
                listOf(creator.publicKey)).envelope())
        }
        val remove = com.google.gson.JsonObject().apply {
            addProperty("module", "Chat"); addProperty("request", "chat remove")
            addProperty("group_id", group.id); addProperty("member_id", member.publicKey)
        }
        val removed = GroupChat.applyControl(promoted, SaitoTransaction.createTo(creator, remove, 1700000123459, group.id).envelope())
        val delayedSnapshot = SaitoTransaction.createTo(creator, snapshot, 1700000123456, group.id).envelope()
        val retained = GroupChat.applyControl(removed, delayedSnapshot)
        assertEquals(ChatGroupRole.REMOVED, retained.memberRoles[member.publicKey])
        assertEquals(ChatGroupRole.REMOVED, GroupChat.restore(creation.get("envelope").asString, retained.controls).memberRoles[member.publicKey])
        val malformed = snapshot.deepCopy().apply { getAsJsonObject("member_ids").addProperty(member.publicKey, 2) }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            GroupChat.applyControl(group, SaitoTransaction.createTo(creator, malformed, 1700000123460, group.id).envelope())
        }
    }

    @Test fun groupMessageDisplayUsesTheAuthenticatedTransactionTimeWhenBodyTimeDiffers() {
        val creation = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val group = GroupChat.readCreation(creation.get("envelope").asString)
        val fixture = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-message-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        fixture.getAsJsonArray("delayedVectors").forEach { value ->
            val expected = value.asJsonObject
            val original = expected.get("envelope").asString
            assertEquals(expected.get("bodyTimestamp").asLong, SaitoEnvelope.decode(original).body.get("timestamp").asLong)
            org.junit.Assert.assertNotEquals(expected.get("transactionTimestamp").asLong, expected.get("bodyTimestamp").asLong)
            assertEquals(expected.get("transactionTimestamp").asLong, GroupChat.readMessage(group, original).createdAt)
        }
    }

    @Test fun aGroupWithMoreThan255MembersCanReceiveAKnownAdminSnapshot() {
        val creation = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject }
        val group = GroupChat.readCreation(creation.get("envelope").asString)
        val creator = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures, never fund.
        val members = (10..265).map { SaitoWallet.fromPrivateKey(it.toString(16).padStart(64, '0')).publicKey }
        val snapshot = com.google.gson.JsonObject().apply {
            addProperty("module", "Chat"); addProperty("request", "chat update")
            addProperty("group_id", group.id); addProperty("group_name", "Large group")
            add("member_ids", com.google.gson.JsonObject().apply { members.forEach { addProperty(it, 1) } })
        }
        val updated = GroupChat.applyControl(group, SaitoTransaction.createTo(creator, snapshot, 1700000123457, group.id).envelope())
        assertEquals(256, members.count { updated.memberRoles[it] == ChatGroupRole.MEMBER })
        assertEquals(ChatGroupRole.ADMIN, updated.memberRoles[creator.publicKey])
        assertEquals("Large group", updated.name)
    }

}
