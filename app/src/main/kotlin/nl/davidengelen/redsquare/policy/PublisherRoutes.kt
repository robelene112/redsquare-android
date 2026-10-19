package nl.davidengelen.redsquare.policy

import android.content.Context
import android.os.Build
import android.util.Patterns
import androidx.annotation.StringRes
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.SaitoBackend
import java.io.File

/** Public owner-approved configuration only. No actual contact is configured yet. */
internal data class PublisherRoutes(val ready: Boolean = false, val supportEmail: String? = null,
    val safetyEmail: String? = null, val privacyEmail: String? = null) {
    fun email(purpose: PublisherContactPurpose): String? = if (!ready) null else when (purpose) {
        PublisherContactPurpose.SUPPORT -> supportEmail
        PublisherContactPurpose.SAFETY -> safetyEmail
        PublisherContactPurpose.PRIVACY -> privacyEmail
    }
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
                    fun email(field: String): String? {
                        val element = value.get(field) ?: return null
                        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null
                        return element.asString.takeIf { address -> address.length <= 254 &&
                            address.none { it.isWhitespace() || it.code < 32 } &&
                            Patterns.EMAIL_ADDRESS.matcher(address).matches() }
                    }
                    PublisherRoutes(ready = true, supportEmail = email("supportEmail"),
                        safetyEmail = email("safetyEmail"), privacyEmail = email("privacyEmail"))
                } catch (_: Exception) { PublisherRoutes() }
            }
            return PublisherRoutes()
        }
    }
}

internal enum class PublisherContactPurpose(@StringRes val entry: Int, @StringRes val summary: Int,
    @StringRes val title: Int, @StringRes val subject: Int, @StringRes val unavailable: Int) {
    SUPPORT(R.string.publisher_contact, R.string.publisher_contact_summary, R.string.publisher_support,
        R.string.publisher_support_subject, R.string.publisher_support_unavailable),
    SAFETY(R.string.publisher_safety_entry, R.string.publisher_safety_summary, R.string.publisher_safety,
        R.string.publisher_safety_subject, R.string.publisher_safety_unavailable),
    PRIVACY(R.string.publisher_privacy_entry, R.string.publisher_privacy_summary, R.string.publisher_privacy,
        R.string.publisher_privacy_subject, R.string.publisher_privacy_unavailable),
}

/** Immutable public context captured by the originating profile, independently of later UI loads. */
internal data class PublisherUserReport(val publicKey: String, val backend: SaitoBackend)
