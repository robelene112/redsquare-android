package nl.davidengelen.redsquare.policy

import android.content.Context
import android.os.Build
import android.util.Patterns
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.BuildConfig
import java.io.File

/** Public owner-approved configuration only. No actual contact is configured yet. */
internal data class PublisherRoutes(val ready: Boolean = false, val supportEmail: String? = null) {
    companion object {
        fun load(context: Context): PublisherRoutes {
            if (BuildConfig.DEBUG && context.packageName == "nl.davidengelen.redsquare.dev" &&
                Build.HARDWARE in listOf("ranchu", "goldfish")) {
                val fixture = File(context.noBackupFilesDir, "publisher-routes-fixture.json")
                if (fixture.exists()) return try {
                    require(fixture.isFile && fixture.length() in 1L..32768L)
                    val value = JsonParser.parseString(fixture.readText()).asJsonObject
                    val ready = value.getAsJsonPrimitive("ready")
                    require(ready.isBoolean && ready.asBoolean)
                    val email = value.getAsJsonPrimitive("supportEmail")
                    require(email.isString)
                    val address = email.asString
                    require(address.length <= 254 && address.none { it.isWhitespace() || it.code < 32 } &&
                        Patterns.EMAIL_ADDRESS.matcher(address).matches())
                    PublisherRoutes(ready = true, supportEmail = address)
                } catch (_: Exception) { PublisherRoutes() }
            }
            return PublisherRoutes()
        }
    }
}
