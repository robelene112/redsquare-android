package nl.davidengelen.redsquare.spaces

import nl.davidengelen.redsquare.saito.SaitoWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceDestinationTest {
    private val community = SpacePublicReference("https://spaces.example.test",
        SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey, "a".repeat(64))
    private val channel = "b".repeat(64)
    private val record = "c".repeat(128)
    private val parent = "d".repeat(128)

    @Test fun exactDestinationsKeepTheNodeChannelAndRecordScopeThroughASharedLink() {
        val destinations = listOf(
            SpaceDestination(community, channel, SpaceDestination.Kind.CHANNEL),
            SpaceDestination(community, channel, SpaceDestination.Kind.ROOM),
            SpaceDestination(community, channel, SpaceDestination.Kind.MESSAGE, record),
            SpaceDestination(community, channel, SpaceDestination.Kind.MESSAGE, record, parent),
            SpaceDestination(community, channel, SpaceDestination.Kind.THREAD, parent),
            SpaceDestination(community, channel, SpaceDestination.Kind.ARTICLE, record),
            SpaceDestination(community, channel, SpaceDestination.Kind.FILE, record),
        )
        assertEquals(destinations.size, destinations.map { it.link() }.toSet().size)
        destinations.forEach { destination ->
            assertEquals(destination, SpaceDestination.parse(destination.link()))
        }
    }

    @Test fun roomLinksCarryOnlyARoomAddressAndRejectARecordOrAnAlternateEntryRoute() {
        assertTrue(runCatching { SpaceDestination(community, channel, SpaceDestination.Kind.ROOM, record) }.isFailure)
        assertTrue(runCatching { SpaceDestination(community, channel, SpaceDestination.Kind.FILE, record, parent) }.isFailure)
        assertTrue(runCatching { SpaceDestination(community, channel, SpaceDestination.Kind.MESSAGE, record, record) }.isFailure)
        val room = SpaceDestination(community, channel, SpaceDestination.Kind.ROOM).link()
        assertTrue(runCatching { SpaceDestination.parse(room.replace("#", "?join=true#")) }.isFailure)
        assertTrue(runCatching { SpaceDestination.parse(room.replace("/target#", "/join#")) }.isFailure)
        assertTrue(runCatching { SpaceDestination.parse(community.link()) }.isFailure)
    }
}
