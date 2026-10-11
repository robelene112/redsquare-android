package nl.davidengelen.redsquare.registry

import androidx.compose.runtime.*
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** The retained request is signed once, saved before send and reconciled before retry. */
internal class UsernameState(private val wallet: SaitoWallet, private val endpoint: String,
    private val store: UsernameRequestStore, private val scope: CoroutineScope) {
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

    suspend fun load() {
        ready = false
        loadError = false
        try {
            candidate = withContext(Dispatchers.IO) { store.load() }
            candidate?.let {
                name = withContext(Dispatchers.IO) {
                    SaitoEnvelope.decode(it.envelope()).body.get("identifier").asString.removeSuffix("@saito")
                }
                status = R.string.username_pending
            }
            ready = true
            if (candidate != null) run(send = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadError = true }
    }

    fun edit(value: String) {
        if (!ready || busy || candidate != null) return
        name = value
        status = null
        available = false
    }

    fun check() {
        if (!ready || busy || candidate != null) return
        if (!name.matches(Regex("[A-Za-z0-9]{1,45}"))) { status = R.string.username_invalid; return }
        busy = true
        scope.launch { run(send = false) }
    }

    fun register() {
        if (!ready || busy || (candidate == null && !available)) return
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
        try {
            SaitoPeer(wallet, endpoint).use { peer ->
                val record = withContext(Dispatchers.IO) { namecheck(peer) }
                if (record != null) { observed(record); return }
                if (!send) {
                    available = candidate == null
                    status = if (candidate == null) R.string.username_available else R.string.username_pending
                    return
                }
                val transaction = candidate ?: withContext(Dispatchers.IO) {
                    SaitoRegistry.registration(wallet, name, System.currentTimeMillis())
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
                val confirmed = withContext(Dispatchers.IO) { namecheck(peer) }
                if (confirmed != null) observed(confirmed)
            }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            status = if (candidate == null) R.string.username_unavailable else R.string.username_pending_unavailable
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            status = if (candidate == null) R.string.username_unavailable else R.string.username_pending_unavailable
        } finally { busy = false }
    }

    private fun observed(record: SaitoRegisteredName) {
        status = when {
            record.publicKey != wallet.publicKey -> R.string.username_taken
            !record.active -> R.string.username_inactive
            else -> R.string.username_owned
        }
    }

    private suspend fun namecheck(peer: SaitoPeer): SaitoRegisteredName? {
        val identifier = "$name@saito"
        val query = JsonObject().apply {
            addProperty("request", "registry query")
            add("data", JsonObject().apply {
                addProperty("request", "registry namecheck"); addProperty("identifier", identifier)
            })
        }
        val response = peer.request(query.toString().toByteArray(Charsets.UTF_8))
        require(response.size <= 64 * 1024)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(response)).toString()
        return SaitoRegistry.readNamecheck(text, identifier)
    }
}
