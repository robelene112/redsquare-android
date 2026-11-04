package nl.davidengelen.redsquare

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.*
import java.io.ByteArrayOutputStream
import java.util.Base64

internal val referenceArticleLink = "https://saito.io/stack/jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR/${"a".repeat(128)}"

internal fun linkedFeedRow(text: String, link: String = referenceArticleLink, metadata: String): JsonObject {
    val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Published unsafe fixture only.
    val transaction = SaitoTransaction.create(author, JsonObject().apply {
        addProperty("module", "RedSquare"); addProperty("request", "create tweet")
        add("data", JsonObject().apply { addProperty("text", text); addProperty("link", link) })
    }, 1_700_000_000_000)
    val row = postingArchiveRow(transaction)
    val envelope = JsonParser.parseString(row.get("tx").asString).asJsonObject
    envelope.addProperty("opt", Base64.getEncoder().encodeToString(metadata.toByteArray(Charsets.UTF_8)))
    row.addProperty("tx", envelope.toString())
    return row
}

internal fun linkPreviewPng(): ByteArray {
    val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
    return try { ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray() }
    finally { bitmap.recycle() }
}

/** Intercept only the external Android boundary; no public article/provider is opened. */
internal fun withInterceptedPostLinks(block: () -> Unit) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
    var primary: Throwable? = null
    Intents.init()
    try {
        Intents.intending(hasAction(Intent.ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        block()
    } catch (error: Throwable) { primary = error; throw error }
    finally { cleanupAfterTest(primary, { Intents.release() }) }
}
