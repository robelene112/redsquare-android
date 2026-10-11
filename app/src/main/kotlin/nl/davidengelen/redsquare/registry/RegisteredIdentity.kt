package nl.davidengelen.redsquare.registry

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.SaitoRegistry
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoPeer
import nl.davidengelen.redsquare.saito.SaitoWallet
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal val LocalRegistryAuthority = staticCompositionLocalOf { SaitoRegistry.REGISTRAR }

/** Show local knowledge first. Missing names are discovered without delaying the profile. */
@Composable
internal fun registeredIdentityName(publicKey: String, wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend): String {
    val context = LocalContext.current.applicationContext
    val registrar = LocalRegistryAuthority.current
    var identifier by remember(publicKey, registrar, wallet, endpoint, backend) { mutableStateOf<String?>(null) }
    LaunchedEffect(publicKey, registrar, wallet, endpoint, backend) {
        identifier = try {
            withContext(Dispatchers.IO) { RegisteredNameStore(context, publicKey, registrar).load()?.name?.identifier }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        if (identifier == null && wallet != null) {
            try {
                identifier = withContext(Dispatchers.IO) {
                    SaitoPeer(wallet, endpoint).use { peer ->
                        val query = JsonObject().apply {
                            addProperty("request", "registry query")
                            add("data", JsonObject().apply {
                                addProperty("request", "registry query")
                                add("keys", JsonArray().apply { add(publicKey) })
                            })
                        }
                        val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
                        val hints = requireNotNull(gson.fromJson(registryResponse(peer, query), JsonObject::class.java))
                        require(hints.keySet().all { it == publicKey })
                        val hint = hints.get(publicKey) ?: return@use null
                        require(hint.isJsonPrimitive && hint.asJsonPrimitive.isString)
                        val name = hint.asString.also { require(it.matches(Regex("[A-Za-z0-9]{1,45}@saito"))) }
                        query.add("data", JsonObject().apply {
                            addProperty("request", "registry namecheck"); addProperty("identifier", name)
                        })
                        val record = SaitoRegistry.readNamecheck(registryResponse(peer, query), name, registrar) ?: return@use null
                        require(record.publicKey == publicKey && record.active)
                        RegisteredNameStore(context, publicKey, registrar).save(record, backend).name.identifier
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Unavailable or unverified hints keep the generated identity. */ }
        }
    }
    return identifier ?: "Anon-${publicKey.take(6)}"
}

private suspend fun registryResponse(peer: SaitoPeer, query: JsonObject): String {
    val response = peer.request(query.toString().toByteArray(Charsets.UTF_8))
    require(response.size <= 64 * 1024)
    return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response)).toString()
}
