package nl.davidengelen.redsquare.feed

import androidx.compose.runtime.*
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.davidengelen.redsquare.saito.*

/** Explicit author actions retain their signed candidate until observed or deliberately discarded. */
internal class AuthorEditState(private val store: PostDraftStore, private val memory: PostComposerMemory,
    private val wallet: SaitoWallet, private val endpoint: String, private val original: FeedPost,
    private val scope: CoroutineScope, private val revisions: AndroidPostRevisions,
    val kind: AuthorActionKind = AuthorActionKind.EDIT) {
    private val writes = Mutex()
    var text by mutableStateOf("")
    var ready by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var error by mutableStateOf(false)
    var networkError by mutableStateOf(false)
    var conflict by mutableStateOf(false)
    var available by mutableStateOf(false)
    var candidate by mutableStateOf<SaitoTransaction?>(null)
    var reload by mutableIntStateOf(0)
    private var conflictPost: FeedPost? = null
    private var baseline = original
    private var baselineUnknown = false

    suspend fun load() {
        try {
            check(original.author == wallet.publicKey)
            val saved = withContext(Dispatchers.IO) {
                revisions.retain(original)
                store.load()
            }
            baselineUnknown = kind == AuthorActionKind.EDIT && saved.transaction != null && saved.authorBaseline == null
            baseline = saved.authorBaseline?.let(RedSquareFeed::decode) ?: if (saved.transaction != null) original
                else withContext(Dispatchers.IO) { requireNotNull(revisions.original(original.id)) }
            candidate = saved.transaction
            available = saved.available
            text = if (kind == AuthorActionKind.DELETE) "" else
                if (saved.transaction != null) saved.text else memory.text ?: saved.text.ifEmpty { baseline.text }
            memory.text = text
            ready = true
            error = false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }

    suspend fun persist(): Boolean = writes.withLock {
        if (!ready || busy || saving) return@withLock false
        saving = true
        try {
            val snapshot = if (available) PostDraftStore.Saved("") else PostDraftStore.Saved(text, candidate,
                authorBaseline = baseline.envelope.takeIf { kind == AuthorActionKind.EDIT && !baselineUnknown })
            withContext(NonCancellable + Dispatchers.IO) {
                store.save(snapshot)
            }
            memory.text = if (available) null else text
            true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true; false }
        finally { saving = false }
    }

    fun close(onClose: () -> Unit) {
        if (busy || saving) return
        if (!ready) { onClose(); return }
        scope.launch { if (persist()) onClose() }
    }

    fun edit(value: String) {
        if (!ready || saving || busy || candidate != null) return
        text = value; memory.text = value
    }

    fun resolveConflict(useLatestText: Boolean) {
        if (!ready || busy || saving || !conflict) return
        val latest = conflictPost ?: return
        saving = true
        val retainedText = if (useLatestText) latest.text else text
        scope.launch {
            try {
                writes.withLock {
                    withContext(NonCancellable + Dispatchers.IO) {
                        revisions.observe(latest)
                        store.save(PostDraftStore.Saved(retainedText, authorBaseline = latest.envelope))
                    }
                    text = retainedText
                    memory.text = retainedText
                    baseline = latest
                    baselineUnknown = false
                    candidate = null
                    conflict = false
                    conflictPost = null
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { saving = false }
        }
    }

    fun send() {
        if (!ready || busy || saving || available || conflict || (kind == AuthorActionKind.EDIT && text.isBlank())) return
        busy = true
        error = false
        networkError = false
        scope.launch {
            try {
                val transaction = writes.withLock {
                    val prepared = candidate ?: withContext(Dispatchers.IO) {
                        check(original.author == wallet.publicKey)
                        val body = JsonObject().apply {
                            addProperty("module", "RedSquare"); addProperty("request", kind.request)
                            add("data", JsonObject().apply {
                                addProperty("tweet_id", original.id)
                                if (kind == AuthorActionKind.EDIT) addProperty("text", text)
                            })
                        }
                        val timestamp = maxOf(System.currentTimeMillis(), Math.addExact(baseline.edit?.createdAt ?: 0, 1))
                        SaitoTransaction.create(wallet, body, timestamp, original.recipients.distinct())
                    }
                    withContext(NonCancellable + Dispatchers.IO) {
                        store.save(PostDraftStore.Saved(text, prepared,
                            authorBaseline = baseline.envelope.takeIf { kind == AuthorActionKind.EDIT && !baselineUnknown }))
                    }
                    candidate = prepared
                    prepared
                }
                try {
                    withContext(Dispatchers.IO) {
                        PostDeliveryAttempts.run(transaction.id) {
                            SaitoPeer(wallet, endpoint).use { peer ->
                                suspend fun observed(): Boolean {
                                    if (kind == AuthorActionKind.DELETE) {
                                        val command = peer.authorDeletion(transaction.id) ?: return false
                                        check(RedSquareAuthorActions.deletion(original.envelope, command.envelope) != null)
                                        if (peer.authorTarget(original.id) != null) return false
                                        withContext(NonCancellable) {
                                            revisions.confirmDeletion(original, command)
                                            check(store.confirmAvailable(transaction.id))
                                        }
                                        withContext(Dispatchers.Main.immediate) { available = true }
                                        return true
                                    }
                                    val post = peer.authorTarget(original.id)
                                    val status = RedSquareAuthorActions.editStatus(baseline,
                                        RedSquareAuthorActions.decode(transaction.envelope()), post)
                                    if (status == AuthorEditStatus.AVAILABLE) {
                                        withContext(NonCancellable) {
                                            revisions.observe(requireNotNull(post))
                                            check(store.confirmAvailable(transaction.id))
                                        }
                                        withContext(Dispatchers.Main.immediate) { available = true }
                                        return true
                                    }
                                    if (status == AuthorEditStatus.CONFLICT || (baselineUnknown && post?.edit != null)) {
                                        withContext(Dispatchers.Main.immediate) { conflictPost = post; conflict = true }
                                        return true
                                    }
                                    return false
                                }
                                if (observed()) return@use
                                peer.authenticate()
                                val permitted = withContext(NonCancellable) {
                                    val saved = store.load()
                                    check(saved.transaction?.id == transaction.id)
                                    store.claim(saved)
                                }
                                if (!permitted) return@use
                                peer.submit(transaction)
                                for (attempt in 0 until 6) {
                                    if (observed()) break
                                    if (attempt < 5) delay(2_000)
                                }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { networkError = true }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
}

/** A verified command plus a successful absent-target query is needed to acknowledge removal. */
internal suspend fun SaitoPeer.authorDeletion(commandId: String): AuthorAction? {
    val query = JsonObject().apply {
        addProperty("request", "archive")
        add("data", JsonObject().apply {
            addProperty("request", "load"); addProperty("field1", "RedSquare")
            addProperty("sig", commandId); addProperty("limit", 1)
        })
    }
    val page = RedSquareFeed.readArchive(request(query.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8))
    check(page.ignored == 0 && page.posts.isEmpty() && page.deletions.size <= 1)
    return page.deletions.singleOrNull()?.also { check(it.id == commandId) }
}

/** Remote deletion commands need the same original-author check and successful removal observation. */
internal suspend fun AndroidPostRevisions.reconcileDeletions(peer: SaitoPeer, commands: List<AuthorAction>,
    known: List<FeedPost>) {
    val originals = RedSquareFeed.newest(known).associateBy { it.id }
    for (command in commands.distinctBy { it.id }) {
        val original = originals[command.target] ?: continue
        if (RedSquareAuthorActions.deletion(original.envelope, command.envelope) == null) continue
        if (peer.authorTarget(original.id) == null) confirmDeletion(original, command)
    }
}

/** Missing content is not an edit or deletion acknowledgement. */
internal suspend fun SaitoPeer.authorTarget(target: String): FeedPost? {
    for (stream in listOf("RedSquare", "RedSquareRetweet")) {
        val query = JsonObject().apply {
            addProperty("request", "archive")
            add("data", JsonObject().apply {
                addProperty("request", "load"); addProperty("field1", stream)
                addProperty("sig", target); addProperty("limit", 1)
            })
        }
        val page = RedSquareFeed.readTarget(request(query.toString().toByteArray(Charsets.UTF_8)).toString(Charsets.UTF_8), target)
        check(page.ignored == 0 && page.deletions.isEmpty() && page.rowCount == page.posts.size && page.posts.size <= 1)
        page.posts.singleOrNull()?.let { return it }
    }
    return null
}
