package nl.davidengelen.redsquare.saito

import java.nio.charset.StandardCharsets
import org.junit.Assert.*
import org.junit.Test

/** Public node metadata may be old; payment freshness remains a different contract. */
class SaitoNodeBlockTest {
    private val now = 1793908941245L
    private val hash = "641ea2da53b3f8811a0f4bf53170d83e5a9a632885aedbd222fcdfd64d905d6b"
    private val published = """{"blockchain":{"last_block_id":1180213,"last_timestamp":1793845855081,"last_block_hash":"641ea2da53b3f8811a0f4bf53170d83e5a9a632885aedbd222fcdfd64d905d6b"},"wallet":{}}"""

    @Test fun oldPublishedBlockRemainsReadableWithoutQualifyingAsFreshPaymentFunds() {
        val block = SaitoNodeBlock.parse(published.toByteArray(StandardCharsets.UTF_8), now)
        assertEquals(1180213L, block.height)
        assertEquals(1793845855081L, block.timestamp)
        assertEquals(hash, block.hash)
        assertThrows(IllegalArgumentException::class.java) {
            SaitoBalanceSnapshot.parse("1793845855081-1180213-$hash.snap\n".toByteArray(),
                "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR", now)
        }
    }

    @Test fun absentZeroFutureAndImprecisePublishedFieldsDoNotBecomeInventedHeights() {
        for (body in listOf(
            "{}", "{\"blockchain\":{}}",
            published.replace("1180213", "0"),
            published.replace("1180213", "1.5"),
            published.replace("1180213", "9007199254740992"),
            published.replace("1793845855081", "1793909001246"),
            published.replace(hash, "0".repeat(64)),
        )) assertThrows(IllegalArgumentException::class.java) {
            SaitoNodeBlock.parse(body.toByteArray(StandardCharsets.UTF_8), now)
        }
    }
}
