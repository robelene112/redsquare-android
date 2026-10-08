package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SaitoWireTest {
    @Test fun `unsigned archive request matches the independently serialized WASM transaction`() {
        val fixture = JsonParser.parseReader(javaClass.getResourceAsStream("/native-archive-reference.json")!!.reader()).asJsonObject
        val expected = fixture.get("framedRpcHex").asString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertArrayEquals(expected, SaitoWire.applicationRequest(
            fixture.get("publicKey").asString, fixture.get("messageUtf8").asString.toByteArray(), 1, 1700000000000L,
        ))
    }
}
