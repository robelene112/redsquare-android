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
}
