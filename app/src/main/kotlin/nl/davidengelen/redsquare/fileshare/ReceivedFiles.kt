package nl.davidengelen.redsquare.fileshare

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.saito.*
import java.io.Serializable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

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
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()
    fun lock(value: FileTransfer) = locks.computeIfAbsent(
        "${value.owner}:${value.backend.id}:${value.contact}:${value.token}") { ReentrantLock() }
    fun <T> withCopy(value: FileTransfer, action: () -> T): T {
        val lease = lock(value)
        lease.lock(); changes.update { it + 1 }
        try { return action() } finally { lease.unlock(); changes.update { it + 1 } }
    }
}

/** Device-only Fileshare storage. All callers run scans and unlink operations away from the main thread. */
internal class ReceivedFiles(private val context: Context, private val scope: ReceivedFilesScope,
    private val unlink: (File) -> Boolean = File::delete) {
    private fun selected(): SaitoWallet = synchronized(SaitoLocalState.lock) {
        AndroidWalletStore(context).loadExisting().also {
            check(it.publicKey == scope.owner && AndroidBackendStore(context).load() == scope.backend)
        }
    }

    fun snapshot(): ReceivedFilesSnapshot {
        val wallet = selected()
        val discovery = FileTransferStore.retainedContacts(context, wallet, scope.backend)
        var incomplete = discovery.incomplete
        val files = discovery.contacts.flatMap { contact ->
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
                store.removeReceived(current, unlink)
            }
        } finally {
            lease.unlock()
            FileshareRuntime.refreshLocalCopies(scope.owner, scope.backend, value.contact)
        }
    }

}
