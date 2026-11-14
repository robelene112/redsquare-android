package nl.davidengelen.redsquare.store

import android.app.Activity
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.AndroidWalletStore
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*

/** Selected-account seller observations. Reading this screen never creates a withdrawal. */
internal class StoreProceeds(private val activity: Activity, private val wallet: SaitoWallet,
    private val backend: SaitoBackend, private val selected: SaitoBackend, private val endpoint: String,
    private val scope: CoroutineScope) {
    var report by mutableStateOf<SaitoStoreWithdrawal.Proceeds?>(null); private set
    var loading by mutableStateOf(false); private set
    var problem by mutableStateOf<Int?>(null); private set
    private var closed = false
    private var revision = 0L
    private var job: Job? = null
    private fun current(token: Long) {
        check(!closed && token == revision && !activity.isFinishing && !activity.isDestroyed &&
            AndroidWalletStore(activity).loadExisting().publicKey == wallet.publicKey && AndroidBackendStore(activity).load() == selected)
    }
    fun load() {
        if (closed || loading) return
        val token = ++revision; loading = true; problem = null; report = null
        job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    current(token)
                    SaitoPeer(wallet, endpoint).use { peer ->
                        val store = peer.servicePublicKey("Store")
                        val request = SaitoTransaction.create(wallet, SaitoStoreWithdrawal.proceedsRequest(), System.currentTimeMillis(), listOf(store))
                        SaitoStoreWithdrawal.readProceeds(peer.request(request) { current(token) }.decodeToString()).also { current(token) }
                    }
                }
                currentCoroutineContext().ensureActive()
                if (!closed && token == revision) report = result
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                if (!closed && token == revision) problem = R.string.store_proceeds_unavailable
            } finally { if (token == revision) loading = false }
        }
    }
    fun dispose() { closed = true; revision++; job?.cancel() }
}
