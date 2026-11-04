package nl.davidengelen.redsquare.feed

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.ui.AppSpacing

internal val LocalProfileAvatars = staticCompositionLocalOf<ProfileAvatars?> { null }

/** One active wallet/node owns discovery and pictures across all native destinations. */
@Composable
internal fun ProfileAvatarScope(wallet: SaitoWallet?, backend: SaitoBackend?, endpoint: String?, content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val avatars = remember(wallet?.publicKey, backend, endpoint) {
        if (wallet != null && backend != null && endpoint != null) ProfileAvatars(context, wallet, backend, endpoint, scope) else null
    }
    LaunchedEffect(avatars) { avatars?.run() }
    DisposableEffect(avatars) { onDispose { avatars?.close() } }
    CompositionLocalProvider(LocalProfileAvatars provides avatars, content = content)
}

/** Unverified editor previews continue using ProfileImage and never enter this shared source. */
@Composable
internal fun ProfileAvatar(author: String, modifier: Modifier, verified: SaitoProfile? = null,
    showRetry: Boolean = false, cachedOnly: Boolean = false) {
    val avatars = LocalProfileAvatars.current
    DisposableEffect(avatars, author, cachedOnly) {
        avatars?.show(author, !cachedOnly)
        onDispose { avatars?.hide(author) }
    }
    LaunchedEffect(avatars, verified) { verified?.let { avatars?.observe(it) } }
    val state = avatars?.pictures?.get(author)
    val picture = state?.bitmap
    val label = stringResource(if (picture != null) R.string.profile_picture else R.string.profile_generated_picture)
    Box(modifier.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        // A node or identity change starts fresh, including the outgoing animation's pixels.
        key(avatars, author) {
            Crossfade(picture, Modifier.fillMaxSize().clearAndSetSemantics { }, tween(AppSpacing.transitionMillis), label = "Profile picture") { bitmap ->
                if (bitmap == null) GeneratedProfilePicture(author, Modifier.fillMaxSize())
                else Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        if (showRetry && state?.failed == true) FilledTonalIconButton(onClick = { avatars.retry(author) }, Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.profile_picture_retry))
        }
    }
}

/** At most64 compact identities and16MiB displayed rasters; full source strings are not retained. */
internal class ProfileAvatars(private val context: Context, private val wallet: SaitoWallet, val backend: SaitoBackend,
    private val endpoint: String, private val scope: CoroutineScope) {
    internal data class Picture(val imageVersion: String?, val proof: String?, val timestamp: Long, val checkedAt: Long,
        val bitmap: Bitmap? = null, val failed: Boolean = false)
    val pictures = mutableStateMapOf<String, Picture>()
    private val cache = AvatarProfileCache(context, backend)
    private val active = mutableMapOf<String, Int>()
    private val requested = mutableMapOf<String, Boolean>()
    private val refreshAgain = mutableSetOf<String>()
    private val pending = Channel<String>(64)
    private val images = mutableMapOf<String, Job>()
    private val order = LinkedHashSet<String>()

    fun show(author: String, discover: Boolean) {
        if (!author.matches(Regex("[1-9A-HJ-NP-Za-km-z]{43,45}"))) return
        if (active.size >= 64 && author !in active) return
        active[author] = (active[author] ?: 0) + 1
        request(author, discover)
    }
    fun hide(author: String) { active[author]?.let { if (it <= 1) active.remove(author) else active[author] = it - 1 } }
    fun refresh() { active.keys.toList().forEach { request(it, true, true) } }
    fun retry(author: String) {
        pictures[author]?.let { loadImage(author, it) }
        request(author, true, true)
    }
    private fun request(author: String, discover: Boolean, force: Boolean = false) {
        val known = pictures[author]
        val age = System.currentTimeMillis() - (known?.checkedAt ?: 0)
        if (!force && known != null && (!discover || age in 0..300_000)) return
        if (author in requested) {
            requested[author] = requested.getValue(author) || discover
            if (force) refreshAgain.add(author)
            return
        }
        if (requested.size >= 64) return
        requested[author] = discover
        if (pending.trySend(author).isFailure) requested.remove(author)
    }
    suspend fun run() = coroutineScope {
        launch { AndroidProfileDetailsCache.changes.collect { (node, author) ->
            if (node == backend && author in pictures) observe(withContext(Dispatchers.IO) { details(author).load().profile })
        } }
        for (first in pending) {
            val keys = mutableListOf(first)
            delay(20)
            while (keys.size < 8) keys += pending.tryReceive().getOrNull() ?: break
            val discover = mutableListOf<String>()
            for (author in keys) {
                try { put(withContext(Dispatchers.IO) { details(author).load().profile }, confirmed = false) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Generated identity remains immediately available. */ }
                if (requested[author] == true) discover.add(author) else requested.remove(author)
            }
            if (discover.isEmpty()) continue
            discover.forEach { author -> pictures[author]?.let { pictures[author] = it.copy(checkedAt = System.currentTimeMillis()) } }
            try { withContext(Dispatchers.IO) {
                SaitoPeer(wallet, endpoint).use { peer ->
                    val limit = Semaphore(2)
                    coroutineScope { discover.map { author -> launch { limit.withPermit {
                        try {
                            val query = JsonObject().apply {
                                addProperty("request", "archive")
                                add("data", JsonObject().apply {
                                    addProperty("request", "load"); addProperty("field1", "Profile")
                                    addProperty("field2", author); addProperty("limit", 100)
                                })
                            }
                            val observed = SaitoProfiles.readArchive(peer.request(query.toString().toByteArray()).toString(Charsets.UTF_8), author)
                            val latest = retain(SaitoProfiles.merge(details(author).load().profile, observed))
                            currentCoroutineContext().ensureActive()
                            withContext(Dispatchers.Main) { put(latest) }
                        } catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Cached/identicon fallback stays, without blocking the feed. */ }
                    } } }.joinAll() }
                }
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* A failed peer connection preserves cached reading and permits retry. */ }
            finally { discover.forEach { author ->
                requested.remove(author)
                if (refreshAgain.remove(author) && author in active && currentCoroutineContext().isActive)
                    request(author, true, true)
            } }
        }
    }
    suspend fun observe(profile: SaitoProfile) {
        val latest = withContext(Dispatchers.IO) { retain(profile) }
        currentCoroutineContext().ensureActive()
        put(latest)
    }
    private suspend fun retain(profile: SaitoProfile): SaitoProfile = try { cache.save(profile) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { profile } // Cache availability cannot invalidate signed public evidence.

    private suspend fun put(profile: SaitoProfile, confirmed: Boolean = true) {
        val imageVersion = withContext(Dispatchers.IO) { profile.image?.let(::sourceDigest) }
        val proof = profile.updates.firstOrNull()
        val previous = pictures[profile.author]
        if (previous != null && previous.timestamp > (proof?.createdAt ?: 0)) return
        val changed = previous?.imageVersion != imageVersion || previous?.proof != proof?.id
        if (changed) images.remove(profile.author)?.cancel()
        val next = Picture(imageVersion, proof?.id, proof?.createdAt ?: 0,
            if (confirmed) System.currentTimeMillis() else previous?.checkedAt ?: 0,
            bitmap = if (!changed) previous?.bitmap else null, failed = if (!changed) previous?.failed == true else false)
        pictures[profile.author] = next
        order.remove(profile.author); order.add(profile.author)
        trim()
        if (profile.author in pictures && (changed || next.bitmap == null) && next.imageVersion != null) loadImage(profile.author, next, profile.image)
    }
    private fun loadImage(author: String, expected: Picture, observedSource: String? = null) {
        if (expected.imageVersion == null) return
        if (images[author]?.isActive == true) return
        images[author] = scope.launch {
            try {
                val source = observedSource ?: withContext(Dispatchers.IO) {
                    details(author).load().profile.takeIf { it.updates.firstOrNull()?.id == expected.proof }
                        ?.image?.takeIf { sourceDigest(it) == expected.imageVersion }
                } ?: return@launch
                val raster = AvatarImageCache.load(context, backend, author, expected.proof, source)
                currentCoroutineContext().ensureActive()
                if (pictures[author]?.let { it.imageVersion == expected.imageVersion && it.proof == expected.proof } == true)
                    pictures[author] = pictures.getValue(author).copy(bitmap = raster, failed = false)
                trim()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (pictures[author]?.let { it.imageVersion == expected.imageVersion && it.proof == expected.proof } == true)
                    pictures[author] = pictures.getValue(author).copy(failed = true)
            }
        }
    }
    private fun trim() {
        while (pictures.size > 64 ||
            pictures.values.sumOf { it.bitmap?.allocationByteCount?.toLong() ?: 0 } > 16 * 1024 * 1024) {
            val key = order.firstOrNull { it !in active } ?: order.firstOrNull() ?: break
            order.remove(key); pictures.remove(key); images.remove(key)?.cancel()
        }
    }
    private fun sourceDigest(source: String) = MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun details(author: String) = AndroidProfileDetailsCache(context, backend, author)
    fun close() { pending.close(); images.values.forEach { it.cancel() } }
}
