package nl.davidengelen.redsquare.feed

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.saito.SaitoLocalState
import java.net.URI

/** Wallet-wide input receipts survive navigation, node changes and process recreation. */
internal class PostLikeInputRate(private val context: Context, owner: String,
    private val clock: () -> Long) {
    private val storage = PublicStringStore(context, "socialrate.$owner.v1", 2)

    fun reserve(): Boolean = synchronized(SaitoLocalState.lock) {
        val loaded = storage.load()
        check(!loaded.unreadable)
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        check(boot >= 0)
        val receipts = loaded.values.map { value ->
            val parts = value.split(':')
            require(parts.size == 2)
            val recordedBoot = parts[0].toInt().also { require(it >= 0) }
            val elapsed = parts[1].toLong().also { require(it >= 0) }
            recordedBoot to elapsed
        }
        require(receipts.map { it.first }.distinct().size <= 1)
        val previous = if (receipts.firstOrNull()?.first == boot) receipts.map { it.second } else emptyList()
        val next = PostLikeInputPolicy.reserve(previous, clock()) ?: return@synchronized false
        val values = next.map { "$boot:$it" }
        storage.save(values)
        check(storage.load().let { !it.unreadable && it.values == values })
        true
    }
}

/** Deterministic elapsed time is available only to disposable loopback-native fixtures. */
internal object PostLikeInputClock {
    fun fromIntent(intent: Intent, endpoint: String): () -> Long {
        val controlled = if (BuildConfig.DEBUG && Build.HARDWARE in listOf("ranchu", "goldfish") &&
            runCatching { URI(endpoint).host == "127.0.0.1" }.getOrDefault(false)) {
            @Suppress("DEPRECATION")
            val value = runCatching { intent.extras?.get("socialLikeElapsedMillis") as? Long }.getOrNull()
            value?.takeIf { it >= 0 }
        } else null
        return { controlled ?: SystemClock.elapsedRealtime() }
    }
}
