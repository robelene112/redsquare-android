package nl.davidengelen.redsquare.store

import android.content.Intent
import nl.davidengelen.redsquare.BuildConfig
import nl.davidengelen.redsquare.saito.SaitoStoreCrypto
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/** Public network names only. Payment methods and chain identities still come from the selected provider. */
internal class StoreCryptoNetworks(intent: Intent) {
    private val fixture = if (BuildConfig.DEBUG) intent.getStringExtra("storeCryptoNetworksApi")?.let { value ->
        URI(value).also { uri -> require(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
            uri.path == "/network/chains/" && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) }.toString()
    } else null
    private val names = HashMap<String, String>()
    fun name(chain: String): String {
        names[chain]?.let { return it }
        val publicUrl = SaitoStoreCrypto.chainUrl(chain)
        val request = Request.Builder().url(fixture?.let { it + chain } ?: publicUrl.toString()).build()
        val name = client.newCall(request).execute().use { response ->
            check(response.isSuccessful)
            val bytes = response.body.byteStream().use { it.readBytesAtMost(128 * 1024) }
            SaitoStoreCrypto.readChainName(bytes.toString(Charsets.UTF_8), chain)
        }
        names[chain] = name
        return name
    }
    private fun java.io.InputStream.readBytesAtMost(maximum: Int): ByteArray {
        val buffer = ByteArray(maximum + 1)
        var count = 0
        while (count < buffer.size) {
            val read = read(buffer, count, buffer.size - count)
            if (read < 0) break
            check(read > 0)
            count += read
        }
        require(count <= maximum)
        return buffer.copyOf(count)
    }
    private companion object {
        val client by lazy { OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS).retryOnConnectionFailure(false).build() }
    }
}
