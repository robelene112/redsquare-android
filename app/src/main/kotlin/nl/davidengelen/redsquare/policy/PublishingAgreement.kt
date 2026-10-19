package nl.davidengelen.redsquare.policy

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.R
import java.io.File

internal data class PublishingTerms(val revision: String, val terms: String, val community: String)

internal sealed interface PublishingPermission {
    data object Preview : PublishingPermission
    data class Required(val terms: PublishingTerms?) : PublishingPermission
}

/** No owner-approved final policy is configured. Controlled input is debug/emulator-only. */
internal object PublishingPolicies {
    fun permission(context: Context): PublishingPermission {
        if (BuildConfig.DEBUG && context.packageName == "nl.davidengelen.redsquare.dev" &&
            Build.HARDWARE in listOf("ranchu", "goldfish")) {
            val fixture = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
            if (fixture.exists()) {
                require(fixture.isFile && fixture.length() in 1L..32768L)
                val value = JsonParser.parseString(fixture.readText()).asJsonObject
                require(value.getAsJsonPrimitive("enforce").isBoolean)
                if (value.get("enforce").asBoolean) {
                    require(value.getAsJsonPrimitive("ready").isBoolean)
                    if (!value.get("ready").asBoolean) return PublishingPermission.Required(null)
                    val revision = value.get("revision").asString
                    val terms = value.get("terms").asString
                    val community = value.get("community").asString
                    require(revision.matches(Regex("[a-zA-Z0-9._-]{1,120}")))
                    require(terms.isNotBlank() && terms.length <= 16000 &&
                        community.isNotBlank() && community.length <= 16000)
                    return PublishingPermission.Required(PublishingTerms(revision, terms, community))
                }
            }
        }
        return if (BuildConfig.DEBUG || BuildConfig.REVIEW_BUILD) PublishingPermission.Preview
        else PublishingPermission.Required(null)
    }
}

internal class PublishingAgreementController(private val context: Context) {
    var prompt by mutableStateOf<PublishingPermission.Required?>(null); private set
    val open: Boolean get() = prompt != null

    /** The caller owns the coroutine/session. Agreement never stores or automatically runs its action. */
    suspend fun beforePublishing(): Boolean {
        val permission = try { withContext(Dispatchers.IO) { PublishingPolicies.permission(context) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { PublishingPermission.Required(null) }
        return when (permission) {
            PublishingPermission.Preview -> true
            is PublishingPermission.Required -> { prompt = permission; false }
        }
    }

    fun cancel() { prompt = null }
}

internal val LocalPublishingAgreement = staticCompositionLocalOf<PublishingAgreementController> {
    error("Publishing agreement host is required")
}

@Composable
internal fun PublishingAgreementDialog(controller: PublishingAgreementController) {
    val prompt = controller.prompt ?: return
    val terms = prompt.terms
    AlertDialog(onDismissRequest = controller::cancel,
        title = { Text(stringResource(if (terms == null) R.string.publishing_unavailable_title
            else R.string.publishing_review_title)) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (terms == null) Text(stringResource(R.string.publishing_unavailable))
                else {
                    Text(stringResource(R.string.policy_terms), style = MaterialTheme.typography.titleSmall)
                    Text(terms.terms)
                    Text(stringResource(R.string.policy_community), style = MaterialTheme.typography.titleSmall)
                    Text(terms.community)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = controller::cancel) {
            Text(stringResource(if (terms == null) R.string.done else R.string.publishing_cancel))
        } })
}
