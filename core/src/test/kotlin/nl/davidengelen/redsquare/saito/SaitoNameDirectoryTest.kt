package nl.davidengelen.redsquare.saito

import org.junit.Assert.assertEquals
import org.junit.Test

class SaitoNameDirectoryTest {
    @Test fun `public directory labels keep their public keys and isolate unusable entries`() {
        // Public read-only observations from the two accounts reported by the owner.
        val digirok = "oT3f7KeqeeG6VRBVvWKJpDNtigRUMYXUgtu3ZjNrNUrZ"
        val cryptoKen = "i6LwT9rFjrsNgExzLeSghGqjHhaNwwjeDrsLr2LkTUk8"
        val response = """{
            "$digirok":"digirok@saito",
            "${SaitoRegistry.REGISTRAR}":42,
            "unrequested":"SomeoneElse@saito",
            "$cryptoKen":"CryptoKen@saito"
        }"""
        assertEquals(mapOf(digirok to "digirok@saito", cryptoKen to "CryptoKen@saito"),
            SaitoNameDirectory.readMappings(response, listOf(digirok, cryptoKen, SaitoRegistry.REGISTRAR)))
    }
}
