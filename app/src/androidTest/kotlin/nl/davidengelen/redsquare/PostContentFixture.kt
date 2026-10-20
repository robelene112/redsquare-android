package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*

internal data class PostContentFixture(val row: JsonObject, val authorName: String, val originalName: String)

/** Fresh signed identities keep this readable reference independent of earlier personal controls. */
internal fun postContentFixture(): PostContentFixture {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    check(instrumentation.targetContext.packageName == "nl.davidengelen.redsquare.dev" &&
        android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    val images = instrumentation.context.assets.open("native-images-reference.json").reader().use {
        JsonParser.parseReader(it).asJsonObject.getAsJsonObject("images")
    }
    val originalAuthor = SaitoWallet.create()
    val author = SaitoWallet.create()
    val original = RedSquareFeed.decode(SaitoTransaction.create(originalAuthor,
        RedSquarePosts.message("Quoted viewport original"), 1_700_000_000_000).envelope())
    val row = postingArchiveRow(SaitoTransaction.create(author,
        RedSquarePosts.message("Media and quote viewport preview",
            images = listOf(images.get("red").asString, images.get("blue").asString), quote = original),
        1_700_000_060_000))
    return PostContentFixture(row, "Anon-${author.publicKey.take(6)}", "Anon-${originalAuthor.publicKey.take(6)}")
}
