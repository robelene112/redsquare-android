package nl.davidengelen.redsquare.spaces

import android.content.Context
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import java.security.MessageDigest
import java.time.Clock

internal fun spaceServiceDirectory(context: Context, owner: String, endpoint: String): EncryptedLocalFile {
    val namespace = MessageDigest.getInstance("SHA-256").digest("$owner:$endpoint".encodeToByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    val filename = "spaces-directory-$namespace.v1"
    return EncryptedLocalFile(context, filename, "${context.packageName}.spaces.directory.$namespace", filename)
}

internal class AndroidSpacesRecovery(private val context: Context, private val endpoint: (SaitoBackend) -> String) {
    fun collect(wallet: SaitoWallet): List<SpaceHistoryKeys> {
        val archives = linkedMapOf<SpaceCrypto.Scope, SpaceHistoryKeys>()
        fun retain(keys: SpaceHistoryKeys) {
            archives[keys.scope] = archives[keys.scope]?.merge(keys) ?: keys
        }
        retainedSpaceArchives(context, wallet.publicKey).forEach(::retain)
        for (node in SaitoBackend.entries) {
            val address = endpoint(node)
            val known = spaceServiceDirectory(context, wallet.publicKey, address).load() ?: continue
            require(known.version == 1.toByte() && SaitoWallet.isPublicKey(known.text))
            AndroidSpaceClientStore(context, wallet.publicKey, "$node:$address", known.text).use { store ->
                val client = SpaceClient(wallet, node.webUrl, known.text, store, Clock.systemUTC()) {
                    error("Collecting a local backup cannot access the network")
                }
                client.exportHistoryKeys().forEach(::retain)
            }
        }
        return archives.values.toList()
    }

    fun retain(wallet: SaitoWallet, archives: List<SpaceHistoryKeys>) {
        if (archives.isEmpty()) return
        val owner = wallet.publicKey
        AndroidSpaceClientStore(context, owner, "local-recovery", owner).use { store ->
            SpaceArchiveInbox(owner, store).retain(archives)
        }
    }
}

internal fun retainedSpaceArchives(context: Context, owner: String, service: String? = null): List<SpaceHistoryKeys> =
    AndroidSpaceClientStore(context, owner, "local-recovery", owner).use { store ->
        if (!store.hasStorage()) emptyList() else SpaceArchiveInbox(owner, store).records(service)
    }
