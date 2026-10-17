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
import com.google.gson.JsonArray
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
import nl.davidengelen.redsquare.registry.RegistryAuthority
import nl.davidengelen.redsquare.registry.RegisteredNameStore
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

/** Local social receiver. FCM registration and transport entry points are separate integration work. */
internal object SocialPush {
    private enum class Kind(val channel: String, val channelName: Int, val message: Int) {
        REPLY("replies", R.string.notifications_replies_channel, R.string.notifications_reply_own),
        MENTION("mentions", R.string.notifications_mentions_channel, R.string.notifications_mention)
    }
    private data class Event(val post: FeedPost, val kind: Kind)

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        Kind.entries.forEach { kind ->
            manager.createNotificationChannel(NotificationChannel(kind.channel, context.getString(kind.channelName),
                NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    /** An uncreated channel keeps its default enabled choice. Android remains the category authority. */
    fun requestedCategories(context: Context): String? {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return null
        return Kind.entries.map { it.channel }.sorted().filter {
            manager.getNotificationChannel(it)?.importance != NotificationManager.IMPORTANCE_NONE
        }.joinToString(",").takeIf { it.isNotEmpty() }
    }

    suspend fun receive(context: Context, payload: String, endpoint: String,
        now: Long = System.currentTimeMillis(), fixtureRegistrar: Boolean = false): SocialPushResult = withContext(Dispatchers.IO) {
        val reference = try { SocialPushReference.parse(payload) } catch (_: Exception) {
            return@withContext SocialPushResult.SUPPRESSED
        }
        if (!allowedEndpoint(endpoint, reference.backend)) return@withContext SocialPushResult.SUPPRESSED
        val registrar = RegistryAuthority.forPeer(endpoint, fixtureRegistrar)
        try {
            val ledger = SocialPushLedger(context, reference)
            val wallet = synchronized(SaitoLocalState.lock) {
                AndroidWalletStore(context).loadExisting().takeIf {
                    it.publicKey == reference.owner && AndroidBackendStore(context).load() == reference.backend &&
                        SocialPushSettings(context, reference.owner).enabled() && !ledger.wasPublished(now)
                }
            } ?: return@withContext SocialPushResult.SUPPRESSED
            val event = withTimeout(15_000) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    verifiedEvent(context, peer, reference, now, registrar)
                }
            } ?: return@withContext SocialPushResult.SUPPRESSED
            val post = event.post
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
                ensureChannels(context)
                if (manager.getNotificationChannel(event.kind.channel).importance == NotificationManager.IMPORTANCE_NONE) {
                    return@synchronized SocialPushResult.SUPPRESSED
                }
                val open = Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .setData(Uri.Builder().scheme("redsquare")
                    .authority("social").appendPath(reference.backend.id).appendPath(reference.owner)
                    .appendPath(reference.signature).build())
                // Preserve only the already validated emulator transport configuration across
                // a real notification launch. Destination routing still verifies the selected Archive.
                if (BuildConfig.DEBUG && Build.HARDWARE in listOf("ranchu", "goldfish") && endpoint != reference.backend.peerUrl) {
                    open.putExtra(if (reference.backend == SaitoBackend.OWNER) "feedPeerUrl" else "feedOfficialPeerUrl", endpoint)
                    if (registrar != SaitoRegistry.REGISTRAR) open.putExtra("usernameFixtureRegistrar", true)
                }
                val pending = PendingIntent.getActivity(context, 0, open,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val notification = Notification.Builder(context, event.kind.channel)
                    .setSmallIcon(R.drawable.ic_notifications).setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(event.kind.message))
                    .setCategory(Notification.CATEGORY_SOCIAL).setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setOnlyAlertOnce(true).setAutoCancel(true).setContentIntent(pending).build()
                // Recheck after the network suspension. Concurrent receipts share this lock.
                if (!ledger.prepare(now)) return@synchronized SocialPushResult.SUPPRESSED
                manager.notify("social:${reference.owner}:${reference.backend.id}:${reference.signature}", 0, notification)
                ledger.recordPublication(now)
                // Means notify and its disk acknowledgement returned, not proof of visible delivery.
                // A death between them leaves pending state; stable tag/onlyAlertOnce deduplicate an
                // active OS alert on retry. Android dismissal and disk commit cannot be atomic.
                SocialPushResult.PUBLISHED
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { SocialPushResult.RETRY }
    }

    /** A user tap can open an older displayed alert, but still requires fresh selected-Archive proof. */
    suspend fun resolveTap(context: Context, reference: SocialPushReference, endpoint: String,
        fixtureRegistrar: Boolean = false): FeedPost? =
        withContext(Dispatchers.IO) {
            if (!allowedEndpoint(endpoint, reference.backend)) return@withContext null
            val wallet = synchronized(SaitoLocalState.lock) {
                AndroidWalletStore(context).loadExisting().takeIf {
                    it.publicKey == reference.owner && AndroidBackendStore(context).load() == reference.backend &&
                        SocialPushSettings(context, reference.owner).enabled()
                }
            } ?: return@withContext null
            withTimeout(15_000) {
                SaitoPeer(wallet, endpoint).use {
                    verifiedEvent(context, it, reference, null, RegistryAuthority.forPeer(endpoint, fixtureRegistrar))?.post
                }
            }
        }

    fun canOpen(context: Context, reference: SocialPushReference, post: FeedPost): Boolean =
        synchronized(SaitoLocalState.lock) {
            if (AndroidWalletStore(context).loadExisting().publicKey != reference.owner ||
                AndroidBackendStore(context).load() != reference.backend ||
                !SocialPushSettings(context, reference.owner).enabled() ||
                AndroidPersonalControls(context, reference.owner).load().hidesSocial(post.author)) false
            else AndroidPostRevisions(context, reference.backend).apply(listOf(post)).let {
                !it.unreadable && !it.deleted && it.posts.singleOrNull() != null
            }
        }

    private suspend fun verifiedEvent(context: Context, peer: SaitoPeer, reference: SocialPushReference, now: Long?, registrar: String): Event? {
        val candidate = exactPost(peer, reference.signature) ?: return null
        if (candidate.author == reference.owner ||
            SaitoEnvelope.decode(candidate.envelope).body.get("request").asString != "create tweet") return null
        if (now != null && (now < candidate.createdAt || now - candidate.createdAt > MAX_AGE)) return null
        if (candidate.parentId.isNotEmpty() && exactPost(peer, candidate.parentId)?.author == reference.owner) {
            return Event(candidate, Kind.REPLY)
        }
        val signedPost = JsonArray().apply { add(JsonObject().apply { addProperty("tx", candidate.envelope) }) }
        val name = try { RegisteredNameStore(context, reference.owner, registrar).load()?.name }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        return if (RedSquareFeed.readMentions(signedPost.toString(), reference.owner, name, registrar).posts.singleOrNull()?.id == candidate.id)
            Event(candidate, Kind.MENTION) else null
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
