package nl.davidengelen.redsquare.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.davidengelen.redsquare.R
import nl.davidengelen.redsquare.saito.*

@Composable
internal fun ChatComposer(wallet: SaitoWallet, backend: SaitoBackend, endpoint: String, contact: String,
    permitted: Boolean, onHistory: (List<ChatHistoryStore.Entry>) -> Unit) {
    val context = LocalContext.current.applicationContext
    val work = remember(wallet, backend, contact) { ChatWorkStore(context, wallet, backend, contact) }
    val history = remember(wallet, backend, contact) { ChatHistoryStore(context, wallet, backend, contact) }
    val scope = rememberCoroutineScope()
    val writes = remember(work) { Mutex() }
    var saved by remember(work) { mutableStateOf(ChatWorkStore.Saved()) }
    var text by remember(work) { mutableStateOf("") }
    var ready by remember(work) { mutableStateOf(false) }
    var sending by remember(work) { mutableStateOf(false) }
    var failed by remember(work) { mutableStateOf(false) }
    LaunchedEffect(work) {
        try {
            saved = withContext(Dispatchers.IO) { work.load() }
            text = saved.draft
            saved.transaction?.let { candidate ->
                onHistory(withContext(Dispatchers.IO) { history.save(listOf(candidate.envelope())).entries })
            }
            ready = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    LaunchedEffect(ready, text) {
        if (!ready || sending || saved.status == ChatWorkStore.Status.UNKNOWN) return@LaunchedEffect
        delay(300)
        writes.withLock {
            if (sending) return@withLock
            try { saved = withContext(NonCancellable + Dispatchers.IO) { work.saveDraft(text) }; failed = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
    }
    val retrying = saved.transaction != null && (saved.status == ChatWorkStore.Status.UNKNOWN ||
        (saved.status == ChatWorkStore.Status.SUBMITTED && text.isBlank()))
    Column {
        when (saved.status) {
            ChatWorkStore.Status.UNKNOWN -> Text(stringResource(R.string.chat_send_unknown))
            ChatWorkStore.Status.SUBMITTED -> Text(stringResource(R.string.chat_send_submitted))
            ChatWorkStore.Status.FORWARDED -> Text(stringResource(R.string.chat_send_forwarded))
            ChatWorkStore.Status.NONE -> Unit
        }
        if (failed) Text(stringResource(if (ready) R.string.chat_send_error else R.string.chat_work_read_error))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.chat_message)) },
                modifier = Modifier.weight(1f), enabled = ready && !sending && saved.status != ChatWorkStore.Status.UNKNOWN)
            Button(enabled = permitted && ready && !sending && (retrying || text.isNotBlank()),
                onClick = {
                    sending = true
                    val outgoing = text
                    val original = if (retrying) saved.transaction else null
                    scope.launch {
                        try {
                            val candidate = writes.withLock {
                                val prepared = original ?: withContext(Dispatchers.IO) {
                                    PrivateChat.create(wallet, contact, outgoing, System.currentTimeMillis())
                                }
                                saved = withContext(NonCancellable + Dispatchers.IO) {
                                    if (original == null) work.prepare(prepared) else work.prepareRetry(prepared.id)
                                }
                                if (original == null) text = ""
                                onHistory(withContext(Dispatchers.IO) { history.save(listOf(prepared.envelope())).entries })
                                prepared
                            }
                            withContext(Dispatchers.IO) {
                                SaitoPeer(wallet, endpoint).use { peer ->
                                    require("relay" in peer.services())
                                    peer.submit(candidate) { work.claim(candidate.id) }
                                    val submitted = work.record(candidate.id, ChatWorkStore.Status.SUBMITTED)
                                    withContext(Dispatchers.Main.immediate) { saved = submitted }
                                    val relayBody = JsonObject().apply {
                                        addProperty("module", "Relay"); addProperty("request", "chat relay")
                                        add("data", candidate.signedJson())
                                    }
                                    val relay = SaitoTransaction.create(wallet, relayBody, System.currentTimeMillis(), listOf(contact))
                                        .signedJson().apply { addProperty("signature", "0".repeat(128)) }
                                    val request = JsonObject().apply { addProperty("request", "relay peer message"); add("data", relay) }
                                    val response = peer.request(request.toString().toByteArray(Charsets.UTF_8)) { work.assertSelected() }
                                    val result = JsonParser.parseString(response.toString(Charsets.UTF_8)).asJsonObject
                                    val success = result.get("success")
                                    require(result.get("err")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isEmpty() } == true)
                                    require(success != null && success.isJsonPrimitive && success.asJsonPrimitive.isNumber && success.asString == "1")
                                    val forwarded = work.record(candidate.id, ChatWorkStore.Status.FORWARDED)
                                    withContext(Dispatchers.Main.immediate) { saved = forwarded }
                                    peer.finishBroadcast()
                                }
                            }
                            failed = false
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true }
                        finally { sending = false }
                    }
                }) { Text(stringResource(if (retrying) R.string.retry else R.string.chat_send)) }
        }
    }
}
