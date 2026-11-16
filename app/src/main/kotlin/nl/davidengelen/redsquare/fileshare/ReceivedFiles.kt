package nl.davidengelen.redsquare.fileshare

import android.content.Context
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.saito.*
import java.io.Serializable
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** A public account scope can survive recreation. No signer or file path belongs in saved UI state. */
internal data class ReceivedFilesScope(val owner: String, val backend: SaitoBackend) : Serializable
internal data class ReceivedFile(val transfer: FileTransfer, val retainedBytes: Long, val busy: Boolean) {
    val key get() = "${transfer.contact}:${transfer.token}"
}
internal data class ReceivedFilesSnapshot(val files: List<ReceivedFile>, val incomplete: Boolean) {
    val bytes get() = files.sumOf { it.retainedBytes }
}

/** Export holds the same per-copy lease as removal, without holding the wallet lock during streaming. */
internal object ReceivedFileLocks {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()
    fun lock(value: FileTransfer) = locks.computeIfAbsent(
        "${value.owner}:${value.backend.id}:${value.contact}:${value.token}") { ReentrantLock() }
    fun <T> withCopy(value: FileTransfer, action: () -> T): T = lock(value).withLock(action)
}

/** Device-only Fileshare storage. All callers run scans and unlink operations away from the main thread. */
internal class ReceivedFiles(private val context: Context, private val scope: ReceivedFilesScope) {
    private fun selected(): SaitoWallet = synchronized(SaitoLocalState.lock) {
        AndroidWalletStore(context).loadExisting().also {
            check(it.publicKey == scope.owner && AndroidBackendStore(context).load() == scope.backend)
        }
    }

    fun snapshot(): ReceivedFilesSnapshot {
        val wallet = selected()
        val entries = requireNotNull(context.noBackupFilesDir.listFiles())
        val pattern = Regex("fileshare\\.([0-9a-f]{64})\\.([0-9a-f-]{36})\\.info(?:\\.bak)?")
        var incomplete = false
        var legacyContacts: Map<String, String>? = null
        val contacts = linkedSetOf<String>()
        for (name in entries.map { it.name.removeSuffix(".bak") }.distinct()) {
            val match = pattern.matchEntire(name) ?: continue
            try {
                val record = requireNotNull(EncryptedLocalFile(context, name, "fileshare.metadata.v1", "fileshare.").load())
                require(record.version == 1.toByte())
                val body = JsonParser.parseString(record.text).asJsonObject
                val contact = if (body.has("owner")) {
                    if (body.get("owner").asString != scope.owner || body.get("backend").asString != scope.backend.id) continue
                    body.get("contact").asString
                } else {
                    if (legacyContacts == null) {
                        val history = ChatHistoryStore.contacts(context, wallet, scope.backend)
                        incomplete = incomplete || history.unreadable || history.unavailable
                        legacyContacts = (wallet.chatContacts() + history.accounts + FileshareRuntime.transfers.value
                            .filter { it.owner == scope.owner && it.backend == scope.backend }.map { it.contact })
                            .distinct().associateBy { digest("${scope.owner}:${scope.backend.id}:$it") }
                    }
                    val legacy = legacyContacts[match.groupValues[1]]
                    if (legacy == null) { incomplete = true; continue }
                    legacy
                }
                require(SaitoWallet.isPublicKey(contact) && contact != scope.owner)
                require(digest("${scope.owner}:${scope.backend.id}:$contact") == match.groupValues[1])
                contacts.add(contact)
            } catch (_: Exception) { incomplete = true }
        }
        val files = contacts.flatMap { contact ->
            try {
                val store = FileTransferStore(context, scope.owner, scope.backend, contact)
                store.load().filter { it.incoming && it.phase.delivered }.mapNotNull { value ->
                    val bytes = store.retainedBytes(value.token)
                    if (bytes == 0L) null else ReceivedFile(value, bytes, ReceivedFileLocks.lock(value).isLocked)
                }
            } catch (_: Exception) { incomplete = true; emptyList() }
        }.sortedByDescending { it.transfer.createdAt }
        selected()
        return ReceivedFilesSnapshot(files, incomplete)
    }

    /** The journal survives process death. A failed delete is visible and can only be retried deliberately. */
    fun remove(file: ReceivedFile) {
        val value = file.transfer
        check(value.owner == scope.owner && value.backend == scope.backend)
        val lease = ReceivedFileLocks.lock(value)
        check(lease.tryLock()) { "This file is being saved. Try again when saving has finished." }
        try {
            synchronized(SaitoLocalState.lock) {
                selected()
                val store = FileTransferStore(context, scope.owner, scope.backend, value.contact)
                val current = store.load().single { it.token == value.token }
                check(current.incoming && current.phase.delivered)
                check(FileshareRuntime.transfers.value.none { it.owner == scope.owner && it.backend == scope.backend &&
                    it.contact == value.contact && it.token == value.token && (it.phase.active || it.phase == FilePhase.OFFERED) })
                store.removeReceived(current)
            }
        } finally {
            lease.unlock()
            FileshareRuntime.refreshLocalCopies(scope.owner, scope.backend, value.contact)
        }
    }

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
