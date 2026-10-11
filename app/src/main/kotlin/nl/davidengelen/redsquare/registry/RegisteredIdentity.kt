package nl.davidengelen.redsquare.registry

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.davidengelen.redsquare.saito.SaitoRegistry

internal val LocalRegistryAuthority = staticCompositionLocalOf { SaitoRegistry.REGISTRAR }

/** Verified local names never delay identity display or require a network lookup. */
@Composable
internal fun registeredIdentityName(publicKey: String): String {
    val context = LocalContext.current.applicationContext
    val registrar = LocalRegistryAuthority.current
    var identifier by remember(publicKey, registrar) { mutableStateOf<String?>(null) }
    LaunchedEffect(publicKey, registrar) {
        identifier = try {
            withContext(Dispatchers.IO) { RegisteredNameStore(context, publicKey, registrar).load()?.name?.identifier }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
    return identifier ?: "Anon-${publicKey.take(6)}"
}
