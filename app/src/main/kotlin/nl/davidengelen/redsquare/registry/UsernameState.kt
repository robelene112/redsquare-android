package nl.davidengelen.redsquare.registry

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*

/** The retained request is signed once, saved before send and reconciled before retry. */
internal class UsernameState(private val wallet: SaitoWallet, private val endpoint: String,
    private val backend: SaitoBackend, private val registrar: String, private val store: UsernameRequestStore,
    private val cache: RegisteredNameStore, private val scope: CoroutineScope) {
    var name by mutableStateOf("")
        private set
    var ready by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<Int?>(null)
        private set
    var available by mutableStateOf(false)
        private set
    var candidate by mutableStateOf<SaitoTransaction?>(null)
        private set
    var loadError by mutableStateOf(false)
        private set
    var savedName by mutableStateOf<RegisteredNameStore.Saved?>(null)
        private set
    var currentName by mutableStateOf<SaitoRegisteredName?>(null)
        private set
    var cacheError by mutableStateOf(false)
        private set

    suspend fun load() {
        ready = false
        loadError = false
        try {
            candidate = withContext(Dispatchers.IO) { store.load() }
            savedName = withContext(Dispatchers.IO) { cache.load() }
            savedName?.let { name = it.name.identifier.removeSuffix("@saito") }
            candidate?.let {
                name = withContext(Dispatchers.IO) {
                    SaitoEnvelope.decode(it.envelope()).body.get("identifier").asString.removeSuffix("@saito")
                }
                status = R.string.username_pending
            }
            ready = true
            if (candidate != null || savedName != null) run(send = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadError = true }
    }

    fun edit(value: String) {
        if (!ready || busy || candidate != null || savedName != null) return
        name = value
        status = null
        available = false
    }

    fun check() {
        if (!ready || busy || (candidate != null && savedName == null)) return
        if (!name.matches(Regex("[A-Za-z0-9]{1,45}"))) { status = R.string.username_invalid; return }
        busy = true
        scope.launch { run(send = false) }
    }

    fun register() {
        if (!ready || busy || savedName != null || currentName != null || (candidate == null && !available)) return
        busy = true
        scope.launch { run(send = true) }
    }

    suspend fun persist(): Boolean {
        if (!ready || busy) return false
        return try {
            candidate?.let { withContext(Dispatchers.IO) { store.save(it) } }
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { status = R.string.username_save_error; false }
    }

    private suspend fun run(send: Boolean) {
        busy = true
        available = false
        currentName = null
        cacheError = false
        try {
            SaitoPeer(wallet, endpoint).use { peer ->
                val lookup = RegistryLookup(peer, registrar)
                val record = withContext(Dispatchers.IO) { lookup.namecheck("$name@saito") }
                if (record != null) { observed(record); return }
                if (!send) {
                    available = candidate == null && savedName == null
                    status = when {
                        savedName != null -> R.string.username_not_reconfirmed
                        candidate == null -> R.string.username_available
                        else -> R.string.username_pending
                    }
                    return
                }
                val transaction = candidate ?: withContext(Dispatchers.IO) {
                    SaitoRegistry.registration(wallet, name, System.currentTimeMillis(), registrar)
                }
                candidate = transaction
                status = R.string.username_pending
                try { withContext(Dispatchers.IO) { store.save(transaction) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { status = R.string.username_save_error; return }
                withContext(Dispatchers.IO) {
                    peer.authenticate()
                    check(store.claim(transaction.id))
                    currentCoroutineContext().ensureActive()
                    peer.submit(transaction)
                }
                val confirmed = withContext(Dispatchers.IO) { lookup.namecheck("$name@saito") }
                if (confirmed != null) observed(confirmed)
            }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            status = if (candidate == null || savedName != null) R.string.username_unavailable else R.string.username_pending_unavailable
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            status = if (candidate == null || savedName != null) R.string.username_unavailable else R.string.username_pending_unavailable
        } finally { busy = false }
    }

    private suspend fun observed(record: SaitoRegisteredName) {
        currentName = record
        if (record.publicKey == wallet.publicKey && record.active) {
            try { savedName = withContext(Dispatchers.IO) { cache.save(record, backend) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { cacheError = true }
        }
        status = when {
            record.publicKey != wallet.publicKey -> R.string.username_taken
            !record.active -> R.string.username_inactive
            else -> R.string.username_owned
        }
    }

}
