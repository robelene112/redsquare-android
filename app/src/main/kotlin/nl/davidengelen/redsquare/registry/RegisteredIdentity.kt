package nl.davidengelen.redsquare.registry

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.*

internal val LocalRegistryAuthority = staticCompositionLocalOf { SaitoRegistry.REGISTRAR }
internal data class RegisteredIdentityLabel(val name: String, val saveError: Boolean = false)

/** Show local knowledge first. Missing names are discovered without delaying the profile. */
@Composable
internal fun registeredIdentity(publicKey: String, wallet: SaitoWallet?, endpoint: String, backend: SaitoBackend): RegisteredIdentityLabel {
    val context = LocalContext.current.applicationContext
    val registrar = LocalRegistryAuthority.current
    var identity by remember(publicKey, registrar, wallet, endpoint, backend) {
        mutableStateOf(RegisteredIdentityLabel("Anon-${publicKey.take(6)}"))
    }
    LaunchedEffect(publicKey, registrar, wallet, endpoint, backend) {
        val cache = RegisteredNameStore(context, publicKey, registrar)
        val cached = try { withContext(Dispatchers.IO) { cache.load()?.name?.identifier } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        if (cached != null) {
            identity = RegisteredIdentityLabel(cached)
        } else if (wallet != null) {
            try {
                val verified = withContext(Dispatchers.IO) {
                    SaitoPeer(wallet, endpoint).use { peer ->
                        val lookup = RegistryLookup(peer, registrar)
                        val name = lookup.hint(publicKey) ?: return@use null
                        val record = lookup.namecheck(name) ?: return@use null
                        require(record.publicKey == publicKey && record.active)
                        val saveError = try { cache.save(record, backend); false }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { true }
                        RegisteredIdentityLabel(record.identifier, saveError)
                    }
                }
                if (verified != null) identity = verified
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Unavailable or unverified hints keep the generated identity. */ }
        }
    }
    return identity
}
