package nl.davidengelen.redsquare

import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import nl.davidengelen.redsquare.feed.PostDraftStore
import nl.davidengelen.redsquare.feed.UnfinishedTextRecovery
import nl.davidengelen.redsquare.policy.PublishingPermission
import nl.davidengelen.redsquare.policy.PublishingPolicies
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Actual Android encrypted-storage and writer-ownership boundaries, without a publishing Activity. */
class UnfinishedTextStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    }

    private fun awaitText(expected: String) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (unfinishedTextCheckpoint(context)?.text != expected && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
        assertEquals(expected, unfinishedTextCheckpoint(context)?.text)
    }

    private fun localFixture(name: String, block: (SaitoWallet, SaitoBackend, PostDraftStore, MutableList<UnfinishedTextRecovery.EditingSession>) -> JsonObject) {
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val backend = AndroidBackendStore(context).load()
        val retained = ChatFixtureState(context, listOf("postdraft.", "profiledraft."), wallet.publicKey)
        val policy = File(context.noBackupFilesDir, "publishing-policy-fixture.json")
        val previousPolicy = policy.takeIf { it.exists() }?.readBytes()
        val agreements = listOf("publishing-agreement.v1", "publishing-agreement.v1.bak",
            "publishing-agreement.v1.new", "publishing-agreement.pending").associate { name ->
            val file = File(context.noBackupFilesDir, name)
            file to file.takeIf { it.exists() }?.readBytes()
        }
        val sessions = mutableListOf<UnfinishedTextRecovery.EditingSession>()
        val evidence = File(context.cacheDir, "unfinished-text-storage-evidence").apply { mkdirs() }
        var primary: Throwable? = null
        try {
            retained.prepare()
            policy.writeText("""{"enforce":true,"ready":false}""")
            assertEquals(PublishingPermission.Required(null), PublishingPolicies.permission(context))
            val result = block(wallet, backend, PostDraftStore(context, wallet.publicKey, backend), sessions)
            assertEquals(PublishingPermission.Required(null), PublishingPolicies.permission(context))
            assertThrows(IllegalStateException::class.java) { PublishingPolicies.requireAllowed(context) }
            agreements.forEach { (file, previous) ->
                val after = file.takeIf { it.exists() }?.readBytes()
                if (previous == null) assertNull(after) else assertTrue(previous.contentEquals(after))
            }
            assertEquals(wallet.exportJson(), AndroidWalletStore(context).loadExisting().exportJson())
            assertEquals(backend, AndroidBackendStore(context).load())
            result.addProperty("result", "PASS")
            result.addProperty("publishingPermissionUnchanged", true)
            File(evidence, "$name.json").writeText(result.toString())
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary,
                { sessions.asReversed().forEach { it.close() } },
                { retained.restoreFiles() }, { retained.restorePreferences() },
                { agreements.forEach { (file, previous) ->
                    if (previous == null) check(!file.exists() || file.delete()) else file.writeBytes(previous)
                    val restored = file.takeIf { it.exists() }?.readBytes()
                    if (previous == null) assertNull(restored) else assertTrue(previous.contentEquals(restored))
                } },
                { if (previousPolicy == null) check(!policy.exists() || policy.delete()) else policy.writeBytes(previousPolicy) })
        }
    }

    @Test fun newerEditorsAndRetirementPreventStaleWritesAcrossIdentityAndNodeScopes() = localFixture("retirement") { wallet, backend, store, sessions ->
        store.save(PostDraftStore.Saved("Explicit saved baseline 120"))
        val older = store.unfinishedText().also(sessions::add)
        older.update("Earlier private text 120")
        awaitText("Earlier private text 120")
        val current = store.unfinishedText().also(sessions::add)
        assertEquals("Earlier private text 120", current.restored)
        current.update("Newest private text 120")
        older.update("A superseded editor must never overwrite this")
        awaitText("Newest private text 120")
        assertTrue(older.failed)
        val otherNode = SaitoBackend.entries.first { it != backend }
        assertNull(PostDraftStore(context, wallet.publicKey, otherNode).unfinishedText().also(sessions::add).restored)
        val otherWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        assertNull(PostDraftStore(context, otherWallet.publicKey, backend).unfinishedText().also(sessions::add).restored)
        current.update("Queued old work before explicit clearing")
        store.save(PostDraftStore.Saved(""))
        Thread.sleep(400)
        assertNull(unfinishedTextCheckpoint(context)?.text)
        assertNull(current.restored)
        assertEquals("", store.load().text)
        current.update("A deliberate new edit after clearing")
        awaitText("A deliberate new edit after clearing")
        val reopened = store.unfinishedText().also(sessions::add)
        assertEquals("A deliberate new edit after clearing", reopened.restored)
        assertEquals("", store.load().text)
        JsonObject().apply {
            addProperty("recoveredText", reopened.restored)
            addProperty("supersededWriterBlocked", older.failed)
            addProperty("otherIdentityAndNodeEmpty", true)
            addProperty("explicitDraftStillEmpty", store.load().text.isEmpty())
        }
    }

    @Test fun anUnreadableCheckpointCanBeRetriedWithoutReplacingItsPreviousEncryptedWork() = localFixture("read-retry") { _, _, store, sessions ->
        val writer = store.unfinishedText().also(sessions::add)
        writer.update("Earlier protected text survives read failure 120")
        awaitText("Earlier protected text survives read failure 120")
        val original = requireNotNull(unfinishedTextCheckpoint(context))
        assertFalse(original.encrypted.toString(Charsets.ISO_8859_1).contains(original.text!!))
        var primary: Throwable? = null
        try {
            synchronized(SaitoLocalState.lock) { check(original.file.delete()); check(original.file.mkdir()) }
            assertThrows(Exception::class.java) { store.unfinishedText() }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                synchronized(SaitoLocalState.lock) {
                    check(original.file.isDirectory && original.file.delete())
                    original.file.writeBytes(original.encrypted)
                    assertTrue(original.encrypted.contentEquals(original.file.readBytes()))
                }
            })
        }
        val reopened = store.unfinishedText().also(sessions::add)
        assertEquals(original.text, reopened.restored)
        assertEquals("", store.load().text)
        JsonObject().apply {
            addProperty("recoveredText", reopened.restored)
            addProperty("originalEncryptedBytesPreserved", original.encrypted.contentEquals(original.file.readBytes()))
        }
    }

    @Test fun pendingPrivateWritesCannotChangeASignedQueuedCandidateOrGrantPublication() = localFixture("queued-candidate") { wallet, _, store, sessions ->
        val writer = store.unfinishedText().also(sessions::add)
        writer.update("Obsolete unsigned text 120")
        val body = "Retain the exact reviewed queued candidate 120"
        val candidate = SaitoTransaction.create(wallet, RedSquarePosts.message(body), System.currentTimeMillis(), emptyList())
        store.save(PostDraftStore.Saved(body, candidate, queued = true))
        Thread.sleep(400)
        val saved = store.load()
        assertEquals(candidate.encoded(), saved.transaction?.encoded())
        assertEquals(body, saved.text)
        assertTrue(saved.queued)
        assertNull(unfinishedTextCheckpoint(context)?.text)
        assertThrows(IllegalStateException::class.java) { store.unfinishedText() }
        JsonObject().apply {
            addProperty("originalSignature", candidate.id)
            addProperty("recoveredSignature", saved.transaction?.id)
            addProperty("queued", saved.queued)
            addProperty("text", saved.text)
        }
    }
}
