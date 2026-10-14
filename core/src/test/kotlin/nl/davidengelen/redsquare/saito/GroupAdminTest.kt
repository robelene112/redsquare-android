package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupAdminTest {
    @Test fun removalRequiresCurrentAdminAndAnOrdinaryActiveMember() {
        val admin=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixtures only.
        val member=SaitoWallet.fromPrivateKey("0".repeat(63)+"2")
        val outsider=SaitoWallet.fromPrivateKey("0".repeat(63)+"4")
        val key=SaitoWallet.fromPrivateKey("0".repeat(63)+"3")
        val group=GroupChat.readCreation(GroupChat.create(admin,key.publicKey,"Removal guards",listOf(member.publicKey),1700000123456).envelope())
        for(author in listOf(member,outsider)) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                GroupChat.removeMember(author,group,member.publicKey,1700000123457)
            }
        }
        for(target in listOf(admin.publicKey,outsider.publicKey,key.publicKey)) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                GroupChat.removeMember(admin,group,target,1700000123457)
            }
        }
        val left=GroupChat.applyControl(group,GroupChat.leave(member,key.publicKey,1700000123457).envelope())
        val removed=GroupChat.applyControl(group,GroupChat.removeMember(admin,group,member.publicKey,1700000123457).envelope())
        for(inactive in listOf(left,removed)) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                GroupChat.removeMember(admin,inactive,member.publicKey,1700000123458)
            }
        }
    }

    @Test fun adminRemovalMatchesBothPinnedWebConstructorsAndSurvivesReplay() {
        val creation=requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use{JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject}
        val expected=requireNotNull(javaClass.getResourceAsStream("/native-group-chat-admin-reference.json"))
            .bufferedReader().use{JsonParser.parseReader(it).asJsonObject.getAsJsonArray("removalVectors")}
        val group=GroupChat.readCreation(creation.get("envelope").asString)
        val admin=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixture only.
        expected.forEach { value ->
            val vector=value.asJsonObject
            val member=vector.get("member").asString
            val removed=GroupChat.removeMember(admin,group,member,vector.get("timestamp").asLong)
            assertEquals(vector.get("envelope").asString,removed.envelope())
            assertEquals(vector.get("signature").asString,removed.id)
            val replay=GroupChat.replay(group,listOf(removed.envelope(),removed.envelope()))
            assertEquals(ChatGroupRole.REMOVED,replay.group.memberRoles[member])
            assertEquals(listOf(removed.envelope()),replay.originals)
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                GroupChat.promote(admin,replay.group,member,1700000123458)
            }
        }
    }

    @Test fun promotionRequiresCurrentAdminAndAnOrdinaryActiveMember() {
        val admin=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixtures only.
        val member=SaitoWallet.fromPrivateKey("0".repeat(63)+"2")
        val outsider=SaitoWallet.fromPrivateKey("0".repeat(63)+"4")
        val key=SaitoWallet.fromPrivateKey("0".repeat(63)+"3")
        val group=GroupChat.readCreation(GroupChat.create(admin,key.publicKey,"Role guards",listOf(member.publicKey),1700000123456).envelope())
        for(author in listOf(member,outsider)) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                GroupChat.promote(author,group,member.publicKey,1700000123457)
            }
        }
        for(target in listOf(admin.publicKey,outsider.publicKey,key.publicKey)) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                GroupChat.promote(admin,group,target,1700000123457)
            }
        }
        val left=GroupChat.applyControl(group,GroupChat.leave(member,key.publicKey,1700000123457).envelope())
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            GroupChat.promote(admin,left,member.publicKey,1700000123458)
        }
    }

    @Test fun adminPromotionMatchesBothPinnedWebConstructors() {
        val creation=requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use{JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")[0].asJsonObject}
        val expected=requireNotNull(javaClass.getResourceAsStream("/native-group-chat-admin-reference.json"))
            .bufferedReader().use{JsonParser.parseReader(it).asJsonObject.getAsJsonArray("vectors")}
        val group=GroupChat.readCreation(creation.get("envelope").asString)
        val admin=SaitoWallet.fromPrivateKey("0".repeat(63)+"1") // Public unsafe fixture, never fund.
        expected.forEach { value ->
            val vector=value.asJsonObject
            val promoted=GroupChat.promote(admin,group,vector.get("member").asString,vector.get("timestamp").asLong)
            assertEquals(vector.get("envelope").asString,promoted.envelope())
            assertEquals(vector.get("signature").asString,promoted.id)
            assertEquals(ChatGroupRole.ADMIN,GroupChat.applyControl(group,promoted.envelope())
                .memberRoles[vector.get("member").asString])
        }
    }
}
