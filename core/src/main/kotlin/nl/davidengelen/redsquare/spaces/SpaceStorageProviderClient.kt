package nl.davidengelen.redsquare.spaces

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** The selected provider receives ciphertext directly from the device. */
class SpaceStorageProviderClient(private val origin: String,
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    init { verifySpaceStorageOrigin(origin) }

    fun offer(space: String, owner: String): SpaceStorageOffer {
        require(space.matches(FILE_ID) && SaitoWallet.isPublicKey(owner))
        val request = JsonObject().apply {
            addProperty("space", space)
            addProperty("owner", owner)
        }
        return SpaceStorageOffer.read(post("/offer", request.toString())).also {
            require(it.space == space && it.owner == owner && it.origin == origin)
        }
    }

    fun commit(approval: String): String = post("/lease", approval)

    fun usage(lease: SpaceFileLease, grant: SpaceFileGrant, now: () -> Long): SpaceStorageUsage {
        require(grant.action == SpaceFileGrant.Action.STATUS && lease.offer.origin == origin)
        return request("/status", grant.envelope) { connection ->
            val reply = response(connection).use(::readReply)
            SpaceStorageUsage.read(reply, lease, grant, now())
        }
    }

    fun remove(grant: SpaceFileGrant) {
        require(grant.action == SpaceFileGrant.Action.DELETE)
        request("/object", grant.envelope) { connection ->
            connection.requestMethod = "DELETE"
            require(response(connection).use(::readReply) == "{\"state\":\"removed\"}")
        }
    }

    fun put(grant: SpaceFileGrant, encrypted: InputStream): String {
        require(grant.action == SpaceFileGrant.Action.PUT)
        return request("/upload", grant.envelope) { connection ->
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(grant.bytes)
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.outputStream.use { encrypted.copyTo(it, 64 * 1024) }
            response(connection).use(::readReply)
        }
    }

    fun <T> download(grant: SpaceFileGrant, read: (InputStream) -> T): T {
        require(grant.action == SpaceFileGrant.Action.GET)
        return request("/download", grant.envelope) { connection ->
            response(connection).use { stream ->
                require(connection.getHeaderField("Content-Length")?.toLongOrNull() == grant.bytes) {
                    "Storage returned an unexpected file length"
                }
                read(stream)
            }
        }
    }

    private fun post(path: String, body: String): String {
        val bytes = body.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_REPLY)
        return request(path) { connection ->
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(bytes) }
            response(connection).use(::readReply)
        }
    }

    private fun <T> request(path: String, authorization: String? = null, exchange: (HttpURLConnection) -> T): T {
        val connection = connect(URL(origin.trimEnd('/') + "/v1/spaces-storage" + path))
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            authorization?.let { connection.setRequestProperty("Authorization", "Saito $it") }
            return exchange(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun response(connection: HttpURLConnection): InputStream = when (val status = connection.responseCode) {
        200 -> connection.inputStream
        409 -> throw Rejected()
        else -> throw IOException("Storage provider returned HTTP $status")
    }

    private fun readReply(source: InputStream): String {
        val bytes = ByteArray(MAX_REPLY + 1)
        var count = 0
        while (count < bytes.size) {
            val read = source.read(bytes, count, bytes.size - count)
            if (read == -1) break
            check(read > 0)
            count += read
        }
        require(count in 1..MAX_REPLY)
        return bytes.decodeToString(0, count, throwOnInvalidSequence = true)
    }

    class Rejected : IOException("Storage could not accept the request")

    private companion object { const val MAX_REPLY = 64 * 1024 }
}
