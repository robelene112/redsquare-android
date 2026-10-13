package nl.davidengelen.redsquare.chat

import android.content.Context
import androidx.compose.runtime.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.saito.*

internal class ChatReactionState(context: Context, private val wallet: SaitoWallet,
    backend: SaitoBackend, private val contact: String, private val endpoint: String,
    private val scope: CoroutineScope) {
    private val store = ChatReactionStore(context, wallet, backend, contact)
    var saved by mutableStateOf(ChatReactionStore.Saved()); private set
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    val editable: Boolean get() = ready && !busy && saved.status !in
        listOf(ChatReactionStore.Status.UNKNOWN, ChatReactionStore.Status.SUBMITTED)

    suspend fun load() {
        try { saved = withContext(Dispatchers.IO) { store.load() }; ready = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }

    fun toggle(target: PrivateChatMessage) {
        if (!editable) return
        busy = true
        scope.launch {
            try {
                val candidate = withContext(Dispatchers.IO) {
                    PrivateChatLike.create(wallet, contact, target, System.currentTimeMillis())
                }
                saved = withContext(NonCancellable + Dispatchers.IO) { store.prepare(candidate) }
                withContext(Dispatchers.IO) {
                    SaitoPeer(wallet, endpoint).use { peer ->
                        require("relay" in peer.services())
                        peer.submit(candidate) { store.claim(candidate.id) }
                        val submitted = store.record(candidate.id, ChatReactionStore.Status.SUBMITTED)
                        withContext(Dispatchers.Main.immediate) { saved = submitted }
                        val relayBody = JsonObject().apply {
                            addProperty("module", "Relay"); addProperty("request", "chat relay")
                            add("data", candidate.signedJson())
                        }
                        val relay = SaitoTransaction.create(wallet, relayBody, System.currentTimeMillis(), listOf(contact))
                            .signedJson().apply { addProperty("signature", "0".repeat(128)) }
                        val request = JsonObject().apply { addProperty("request", "relay peer message"); add("data", relay) }
                        val response = peer.request(request.toString().toByteArray(Charsets.UTF_8)) { store.assertSelected() }
                        val result = JsonParser.parseString(response.toString(Charsets.UTF_8)).asJsonObject
                        val success = result.get("success")
                        require(result.get("err")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isEmpty() } == true)
                        require(success != null && success.isJsonPrimitive && success.asJsonPrimitive.isNumber && success.asString == "1")
                        val forwarded = store.record(candidate.id, ChatReactionStore.Status.FORWARDED)
                        withContext(Dispatchers.Main.immediate) { saved = forwarded }
                        peer.finishBroadcast()
                    }
                }
                failed = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
}
