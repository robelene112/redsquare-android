package nl.davidengelen.redsquare

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.feed.PublicStringStore
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.stack.StackLibrary
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** The real authenticated socket and reader are cancelled by their owner, before the peer's deadline. */
class ReadCancellationTest {
    @Test fun callerCancellationDoesNotCommitTheLateArchiveReply() = exercise(false)
    @Test fun enclosingDeadlineDoesNotBecomeARecoverablePeerFailure() = exercise(true)

    private fun exercise(enclosingDeadline: Boolean) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev")
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val retained = ChatFixtureState(context, listOf("stack.owner.", "stackwindow.owner.${wallet.publicKey}."), wallet.publicKey)
        val body = JsonObject().apply {
            addProperty("module", "Stack"); addProperty("request", "create stack post request")
            add("data", JsonObject().apply {
                addProperty("type", "stack_post"); addProperty("title", "Cancelled generation must not publish this article")
                addProperty("content", "Public controlled native cancellation fixture")
            })
        }
        val row = JsonObject().apply { addProperty("created_at", 1700001000000L)
            addProperty("tx", SaitoTransaction.create(wallet, body, 1700001000000L).envelope()) }
        val reads = AtomicInteger()
        val response = java.util.concurrent.atomic.AtomicReference("[$row]")
        val peer = ControlledFeedPeer("[]", archiveResponse = { response.get() },
            onArchiveResponse = { _, _, _ -> reads.incrementAndGet() }).apply {
            withholdArchiveReply = true; retainWithheldArchiveReplies = true
        }
        var primary: Throwable? = null
        try {
            retained.prepare()
            peer.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                SaitoPeer(wallet, endpoint).use { retainedConnection ->
                retainedConnection.authenticate()
                val library = StackLibrary(context, wallet, SaitoBackend.OWNER, endpoint)
                val entered = CompletableDeferred<Unit>()
                val reader = launch(Dispatchers.Main) {
                    entered.complete(Unit)
                    if (enclosingDeadline) withTimeout(3_000) { library.start() } else library.start()
                }
                entered.await()
                withTimeout(10_000) { while (reads.get() == 0) delay(10) }
                val started = SystemClock.elapsedRealtime()
                if (enclosingDeadline) reader.join() else withTimeout(1_000) { reader.cancelAndJoin() }
                val cancellationMillis = SystemClock.elapsedRealtime() - started
                check(reader.isCancelled) { "The owner must retain cancellation semantics" }
                check(!library.failed && !library.successfulRead && !library.loading && library.articles.isEmpty())
                // Replay the exact old response including its original request id on the shared socket.
                val lateReplies = peer.releaseWithheldArchiveReplies()
                check(lateReplies == 1) { "Expected the actual withheld callback" }
                peer.withholdArchiveReply = false
                val next = StackLibrary(context, wallet, SaitoBackend.OWNER, endpoint)
                withContext(Dispatchers.Main) { next.start() }
                check(next.successfulRead && next.articles.size == 1)
                check(!library.failed && !library.successfulRead && library.articles.isEmpty())
                check(PublicStringStore(context, "stack.owner.v1", 100).load().values.size == 1)
                check(peer.transactions.isEmpty()); peer.assertHealthy()
                if (!enclosingDeadline) {
                    val originalConnections = peer.connectionAttempts.get()
                    val oversizedRow = row.deepCopy().apply {
                        addProperty("latePadding", " ".repeat(SaitoPeer.MAX_RESPONSE_BYTES))
                    }
                    response.set("[$oversizedRow]")
                    peer.sendOversizedArchiveFrames = true
                    peer.withholdArchiveReply = true
                    val initialReads = reads.get()
                    val cancelled = StackLibrary(context, wallet, SaitoBackend.OWNER, endpoint)
                    val old = launch(Dispatchers.Main) { cancelled.start() }
                    var current: Deferred<Unit>? = null
                    try {
                        withTimeout(10_000) { while (reads.get() == initialReads) delay(10) }
                        withTimeout(1_000) { old.cancelAndJoin() }
                        check(old.isCancelled && !cancelled.successfulRead)
                        response.set("[$row]")
                        val active = StackLibrary(context, wallet, SaitoBackend.OWNER, endpoint)
                        current = async(Dispatchers.Main) { active.start() }
                        withTimeout(10_000) { while (reads.get() < initialReads + 2) delay(10) }
                        peer.withholdArchiveReply = false
                        val replayed = peer.releaseWithheldArchiveReplies()
                        check(replayed == 2) { "Replay the cancelled large callback before the current small callback" }
                        withTimeout(15_000) { current.await() }
                        File(context.cacheDir, "read-cancellation-evidence/late-oversized.json").apply {
                            parentFile!!.mkdirs()
                        }.writeText(JsonObject().apply {
                            addProperty("cancelled", old.isCancelled)
                            addProperty("cancelledSuccessfulRead", cancelled.successfulRead)
                            addProperty("lateBodyBytes", "[$oversizedRow]".toByteArray().size)
                            addProperty("replayed", replayed)
                            addProperty("activeSuccessfulRead", active.successfulRead)
                            addProperty("activeArticles", active.articles.size)
                            addProperty("connectionsBefore", originalConnections)
                            addProperty("connectionsAfter", peer.connectionAttempts.get())
                            addProperty("publications", peer.transactions.size)
                        }.toString())
                        check(active.successfulRead && active.articles.size == 1) {
                            "A late oversized callback for the cancelled owner interrupted the current reader"
                        }
                        check(!cancelled.successfulRead && peer.connectionAttempts.get() == originalConnections)
                        check(peer.transactions.isEmpty()); peer.assertHealthy()
                    } finally {
                        withContext(NonCancellable) { withTimeout(5_000) {
                            old.cancelAndJoin(); current?.cancelAndJoin()
                        } }
                    }
                }
                val folder = File(context.cacheDir, "read-cancellation-evidence").apply { mkdirs() }
                File(folder, "${if(enclosingDeadline) "enclosing-deadline" else "caller-cancel"}.json").writeText(JsonObject().apply {
                    addProperty("callerCancelled", reader.isCancelled); addProperty("cancellationMillis", cancellationMillis)
                    addProperty("lateRepliesReplayed", lateReplies); addProperty("nextReaderArticles", next.articles.size)
                    addProperty("cancelledReaderArticles", library.articles.size); addProperty("publications", peer.transactions.size)
                }.toString())
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary, { retained.restoreFiles() }, { retained.restorePreferences() }) }
    }
}
