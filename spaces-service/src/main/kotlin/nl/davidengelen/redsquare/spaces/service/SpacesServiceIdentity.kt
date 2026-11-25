package nl.davidengelen.redsquare.spaces.service

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

/** Read an existing owner-only signing identity for an explicit local process. */
internal fun readSpacesServiceIdentity(path: Path): SaitoWallet {
    val identity = path.toRealPath()
    val permissions = Files.getPosixFilePermissions(identity)
    require(permissions.none { it in setOf(PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
        PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE,
        PosixFilePermission.OTHERS_EXECUTE) }) { "Spaces identity file must be owner-only" }
    require(Files.size(identity) in 1..4096)
    val bytes = Files.newInputStream(identity).use { it.readNBytes(4097) }
    return try {
        require(bytes.size in 1..4096)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val data = GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(text, JsonObject::class.java)
        require(data.keySet() == setOf("version", "publicKey", "privateKey") && data.get("version").toString() == "1")
        val key = data.get("privateKey").also { require(it.isJsonPrimitive && it.asJsonPrimitive.isString) }.asString
        val restored = SaitoWallet.fromPrivateKey(key)
        require(data.get("publicKey").isJsonPrimitive && data.get("publicKey").asJsonPrimitive.isString &&
            data.get("publicKey").asString == restored.publicKey)
        restored
    } finally { bytes.fill(0) }
}
