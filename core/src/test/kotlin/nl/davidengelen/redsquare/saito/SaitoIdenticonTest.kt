package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class SaitoIdenticonTest {
    @Test fun `generated avatars match the pinned web library including a leading zero hash`() {
        val reference = JsonParser.parseReader(javaClass.getResourceAsStream("/native-identicon-reference.json")!!.reader()).asJsonObject
        for (item in reference.getAsJsonArray("vectors")) {
            val vector = item.asJsonObject
            val avatar = SaitoIdenticon.forAddress(vector.get("address").asString)
            val rgb = vector.get("foreground").asString.substringAfter('(').substringBefore(')').split(',').take(3).map(String::toInt)
            val expectedColor = (255 shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
            assertEquals(expectedColor, avatar.foreground)
            val occupied = vector.getAsJsonArray("rectangles").map { rect ->
                val cell = rect.asJsonObject
                (cell.get("y").asInt / 84) * 5 + cell.get("x").asInt / 84
            }.toSet()
            assertEquals((0 until 25).map { it in occupied }, avatar.cells)
        }
    }
}
