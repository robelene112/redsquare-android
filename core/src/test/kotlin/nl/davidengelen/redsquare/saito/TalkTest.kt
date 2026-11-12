package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class TalkTest {
    @Test fun anActualUpstreamInvitationOpensTheSameRoomAndSharesACompatibleLink() {
        val reference = javaClass.getResourceAsStream("/native-talk-reference.json")!!.reader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        // Literal output of pinned Videocall.generateCallLink, executed independently in Node.
        val link = reference.get("link").asString
        val parsed = requireNotNull(TalkLink.parse(link))
        assertEquals(SaitoBackend.OFFICIAL, parsed.backend)
        assertEquals("jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR", parsed.room.id)
        assertEquals(parsed.room.id, parsed.room.host)
        assertEquals(TalkMode.VIDEO, parsed.room.mode)
        assertEquals(emptyList<String>(), parsed.room.peers)
        assertEquals(reference.getAsJsonObject("room"), parsed.room.json())
        assertEquals(link, parsed.url())
    }
    @Test fun aLegacyUnsignedInvitationRemainsAClaimUntilTheActualPeerIsAuthenticated() {
        val alice = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val bob = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val room = TalkRoom(alice.publicKey, alice.publicKey)
        val outgoing = TalkProtocol.invite(alice, bob.publicKey, room, TalkDial.REQUEST, 100)
        val signed = requireNotNull(TalkProtocol.read(outgoing.envelope(), bob.publicKey))
        assertTrue(signed.identityVerified)
        assertEquals("Relay", signed.body.get("module").asString)
        assertEquals("stun-connection-request", signed.request)
        assertEquals(room.json(), signed.body.getAsJsonObject("data"))
        val legacy = JsonParser.parseString(outgoing.envelope()).asJsonObject
        val bytes = java.util.Base64.getDecoder().decode(legacy.get("t").asString)
        bytes.fill(0, 16, 80) // Legacy Relay.createRelayTransaction does not sign.
        legacy.addProperty("t", java.util.Base64.getEncoder().encodeToString(bytes))
        val claim = requireNotNull(TalkProtocol.read(legacy.toString(), bob.publicKey))
        assertEquals(alice.publicKey, claim.sender)
        assertFalse(claim.identityVerified)
        assertTrue(requireNotNull(TalkProtocol.read(legacy.toString(), bob.publicKey, alice.publicKey)).identityVerified)
        assertThrows(IllegalArgumentException::class.java) { TalkProtocol.read(legacy.toString(), bob.publicKey, bob.publicKey) }
    }

    @Test fun aSignedRoomRosterAddressesTheJoinerAndPreservesTheOtherParticipants() {
        val host = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val joiner = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val existing = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val room = TalkRoom(existing.publicKey, host.publicKey)
        val tx = TalkProtocol.participants(host, room, joiner.publicKey,
            listOf(host.publicKey, existing.publicKey), 200)
        val message = requireNotNull(TalkProtocol.read(tx.envelope(), joiner.publicKey))
        assertTrue(message.identityVerified)
        assertEquals("Videocall", message.module)
        assertEquals("call-list-response", message.request)
        assertEquals(room.id, message.roomId)
        assertEquals(joiner.publicKey, message.body.get("public_key").asString)
        assertEquals(listOf(host.publicKey, existing.publicKey),
            message.body.getAsJsonArray("call_list").map { it.asString })
        assertThrows(IllegalArgumentException::class.java) { TalkProtocol.read(tx.envelope(), existing.publicKey) }
        val entry = SaitoEnvelope.decode(TalkProtocol.enter(joiner, room, 201).envelope())
        assertEquals("call-list-request", entry.body.get("request").asString)
        assertTrue(entry.recipients.containsAll(listOf(room.id, host.publicKey)))
    }

}
