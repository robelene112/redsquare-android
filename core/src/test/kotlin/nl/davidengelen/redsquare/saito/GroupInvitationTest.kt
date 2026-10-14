package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupInvitationTest {
    @Test fun existingCreatorAndMemberLinksPreserveUnicodeClaimsAndLiteralBase64Plus() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/native-group-chat-creation-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val invitations = reference.getAsJsonArray("invitations")
        org.junit.Assert.assertTrue(invitations.any { it.asJsonObject.get("link").asString.contains('+') })
        invitations.forEach { value ->
            val expected = value.asJsonObject
            val invite = GroupInvitation.parse(expected.get("link").asString)
            assertEquals(expected.get("id").asString, invite.id)
            assertEquals(expected.get("name").asString, invite.name)
            assertEquals(expected.get("sender").asString, invite.sender)
            assertEquals(expected.get("admin").asString, invite.admin)
        }
    }

    @Test fun malformedLinksReturnARecoverableErrorWithoutExposingTheirContents() {
        val invalid = listOf(
            "https://saito.io/chat?chat_id=bnVsbA==", // JSON null, not a group.
            "https://saito.io/chat?chat_id=%GG", // Invalid URI encoding.
            "/w==", // Invalid UTF8 body.
            "https://saito.io/chat?chat_id=e30=&chat_id=e30=" // Ambiguous duplicate.
        )
        invalid.forEach { link ->
            val failure = org.junit.Assert.assertThrows(GroupInvitation.Invalid::class.java) { GroupInvitation.parse(link) }
            assertEquals("Group invitation could not be read", failure.message)
            org.junit.Assert.assertNull(failure.cause)
        }
    }
}
