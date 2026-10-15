package nl.davidengelen.redsquare.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.MainActivity
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.feed.AndroidPostRevisions
import nl.davidengelen.redsquare.moderation.AndroidPersonalControls
import nl.davidengelen.redsquare.saito.*
import java.net.URI

/** Only a reference crosses the external transport. Signed content comes from the selected Archive. */
internal data class SocialPushReference(val owner: String, val backend: SaitoBackend, val signature: String) {
    companion object {
        fun parse(payload: String): SocialPushReference {
            require(payload.toByteArray(Charsets.UTF_8).size <= 1024)
            val body = requireNotNull(GsonBuilder().setStrictness(Strictness.STRICT).create()
                .fromJson(payload, JsonObject::class.java))
            require(body.keySet() == setOf("version", "wallet", "backend", "signature"))
            val version = body.get("version")
            require(version.isJsonPrimitive && version.asJsonPrimitive.isNumber && version.asBigDecimal.intValueExact() == 1)
            fun text(name: String): String {
                val value = body.get(name)
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                return value.asString
            }
            return SocialPushReference(text("wallet"), SaitoBackend.entries.single { it.id == text("backend") },
                text("signature")).also {
                require(SaitoWallet.isPublicKey(it.owner) && it.signature.matches(Regex("[0-9a-f]{128}")))
            }
        }
    }
}

internal enum class SocialPushResult { PUBLISHED, SUPPRESSED, RETRY }

/** Local reply receiver. FCM registration and transport entry points are separate integration work. */
internal object SocialPush {
    suspend fun receive(context: Context, payload: String, endpoint: String,
        now: Long = System.currentTimeMillis()): SocialPushResult = withContext(Dispatchers.IO) {
        val reference = try { SocialPushReference.parse(payload) } catch (_: Exception) {
            return@withContext SocialPushResult.SUPPRESSED
        }
        if (!allowedEndpoint(endpoint, reference.backend)) return@withContext SocialPushResult.SUPPRESSED
        try {
            val wallet = synchronized(SaitoLocalState.lock) {
                AndroidWalletStore(context).loadExisting().takeIf {
                    it.publicKey == reference.owner && AndroidBackendStore(context).load() == reference.backend &&
                        SocialPushSettings(context, reference.owner).enabled()
                }
            } ?: return@withContext SocialPushResult.SUPPRESSED
            val post = withTimeout(15_000) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    val candidate = exactPost(peer, reference.signature) ?: return@use null
                    if (candidate.author == reference.owner || candidate.parentId.isEmpty() ||
                        now < candidate.createdAt || now - candidate.createdAt > MAX_AGE) return@use null
                    val parent = exactPost(peer, candidate.parentId) ?: return@use null
                    candidate.takeIf { parent.author == reference.owner &&
                        SaitoEnvelope.decode(candidate.envelope).body.get("request").asString == "create tweet" }
                }
            } ?: return@withContext SocialPushResult.SUPPRESSED
            synchronized(SaitoLocalState.lock) {
                if (AndroidWalletStore(context).loadExisting().publicKey != reference.owner ||
                    AndroidBackendStore(context).load() != reference.backend ||
                    !SocialPushSettings(context, reference.owner).enabled() ||
                    AndroidPersonalControls(context, reference.owner).load().hidesSocial(post.author)) {
                    return@synchronized SocialPushResult.SUPPRESSED
                }
                val available = AndroidPostRevisions(context, reference.backend).apply(listOf(post))
                if (available.unreadable || available.deleted || available.posts.singleOrNull() == null) {
                    return@synchronized SocialPushResult.SUPPRESSED
                }
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) return@synchronized SocialPushResult.SUPPRESSED
                val manager = context.getSystemService(NotificationManager::class.java)
                if (!manager.areNotificationsEnabled()) return@synchronized SocialPushResult.SUPPRESSED
                manager.createNotificationChannel(NotificationChannel("replies", context.getString(R.string.notifications_replies_channel),
                    NotificationManager.IMPORTANCE_DEFAULT))
                if (manager.getNotificationChannel("replies").importance == NotificationManager.IMPORTANCE_NONE) {
                    return@synchronized SocialPushResult.SUPPRESSED
                }
                val open = Intent(context, MainActivity::class.java).setData(Uri.Builder().scheme("redsquare")
                    .authority("social").appendPath(reference.backend.id).appendPath(reference.owner)
                    .appendPath(reference.signature).build())
                val pending = PendingIntent.getActivity(context, 0, open,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val notification = Notification.Builder(context, "replies")
                    .setSmallIcon(R.drawable.ic_notifications).setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.notifications_reply_own))
                    .setCategory(Notification.CATEGORY_SOCIAL).setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setOnlyAlertOnce(true).setAutoCancel(true).setContentIntent(pending).build()
                manager.notify("social:${reference.owner}:${reference.backend.id}:${reference.signature}", 0, notification)
                // Means notify returned, not proof of visible delivery. Native shade checks provide that proof.
                SocialPushResult.PUBLISHED
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { SocialPushResult.RETRY }
    }

    private suspend fun exactPost(peer: SaitoPeer, signature: String): FeedPost? {
        val criteria = JsonObject().apply {
            addProperty("request", "load"); addProperty("field1", "RedSquare")
            addProperty("sig", signature); addProperty("flagged_ne", 1); addProperty("limit", 1)
        }
        val request = JsonObject().apply { addProperty("request", "archive"); add("data", criteria) }
        val page = RedSquareFeed.readTarget(peer.request(request.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8), signature)
        require(page.ignored == 0 && page.deletions.isEmpty() && page.rowCount == page.posts.size && page.posts.size <= 1)
        return page.posts.singleOrNull()
    }

    private fun allowedEndpoint(endpoint: String, backend: SaitoBackend): Boolean {
        if (endpoint == backend.peerUrl) return true
        if (!BuildConfig.DEBUG || Build.HARDWARE !in listOf("ranchu", "goldfish")) return false
        return try {
            val uri = URI(endpoint)
            uri.scheme == "ws" && uri.host == "127.0.0.1" && uri.port in 1..65535 && uri.path == "/wsopen" &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null
        } catch (_: Exception) { false }
    }

    private const val MAX_AGE = 7 * 24 * 60 * 60 * 1000L
}
