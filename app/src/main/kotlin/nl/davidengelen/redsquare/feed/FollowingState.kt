package nl.davidengelen.redsquare.feed

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Local public identities followed by one wallet. Shared by both backend sessions. */
internal class FollowingState(context: Context, owner: String, private val scope: CoroutineScope) {
    private val storage = PublicStringStore(context, "following.$owner.v1", 1000)
    var accounts by mutableStateOf<Set<String>>(emptySet())
        private set
    var ready by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf(false)
        private set
    var loading by mutableStateOf(true)
        private set

    suspend fun load() {
        loading = true
        try {
            val saved = withContext(Dispatchers.IO) { storage.load() }
            require(!saved.unreadable && saved.envelopes.all { it.matches(publicKeyPattern) })
            accounts = saved.envelopes.toSet()
            ready = true
            error = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }

    fun retry() {
        if (loading || saving) return
        loading = true
        scope.launch { load() }
    }

    fun toggle(author: String) {
        if (!ready || saving || !author.matches(publicKeyPattern)) return
        val updated = if (author in accounts) accounts - author else accounts + author
        if (updated.size > 1000) { error = true; return }
        saving = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { storage.save(updated.sorted()) }
                accounts = updated
                error = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }

    private companion object { val publicKeyPattern = Regex("[1-9A-HJ-NP-Za-km-z]{43,45}") }
}
