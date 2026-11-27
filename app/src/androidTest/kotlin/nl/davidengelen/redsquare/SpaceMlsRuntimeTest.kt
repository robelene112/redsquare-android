package nl.davidengelen.redsquare

import android.os.Build
import android.os.Looper
import android.system.Os
import android.system.OsConstants
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import nl.davidengelen.redsquare.spaces.AndroidSpaceClientStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceCrypto
import nl.davidengelen.redsquare.spaces.SpaceHistoryKeys
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Library/runtime contract only. No Activity/UI or distinct-process claim. */
class SpaceMlsRuntimeTest {
    @Test(timeout = 15_000) fun concurrentAccountStoresWaitForTheSameEncryptedTransactionAndKeepCommittedRows() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(BuildConfig.DEBUG && context.packageName == "nl.davidengelen.redsquare.dev" &&
            Build.HARDWARE in setOf("ranchu", "goldfish") && Looper.myLooper() != Looper.getMainLooper())
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").publicKey
        val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
        val node = "owned-store-contract-${UUID.randomUUID()}"
        val namespace = java.security.MessageDigest.getInstance("SHA-256")
            .digest("SpacesLocal/v1:$owner:$node:$service".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
        val alias = "${context.packageName}.spaces.store.v1.$namespace"
        val files = listOf("", "-journal", "-wal", "-shm").map { File(context.noBackupFilesDir, "spaces-$namespace.sqlite$it") }
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(files.none(File::exists) && !keystore.containsAlias(alias))
        var first: AndroidSpaceClientStore? = null
        var second: AndroidSpaceClientStore? = null
        var primary: Throwable? = null
        try {
            val writerStore = AndroidSpaceClientStore(context, owner, node, service).also { first = it }
            val readerStore = AndroidSpaceClientStore(context, owner, node, service).also { second = it }
            writerStore.transaction {
                put("draft", "before".toByteArray())
                put("anchor", "before-anchor".toByteArray())
            }
            supervisorScope {
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                val readerStarted = CountDownLatch(1)
                val writer = async(Dispatchers.IO) {
                    writerStore.transaction {
                        entered.countDown()
                        check(release.await(5, TimeUnit.SECONDS))
                        put("draft", "after".toByteArray())
                        put("anchor", "after-anchor".toByteArray())
                    }
                }
                try {
                    check(entered.await(5, TimeUnit.SECONDS))
                    val reader = async(Dispatchers.IO) {
                        readerStarted.countDown()
                        readerStore.transaction { getAll(listOf("draft", "absent", "anchor", "draft")) }
                    }
                    check(readerStarted.await(5, TimeUnit.SECONDS))
                    assertNull("A second instance cannot observe an unfinished transaction", withTimeoutOrNull(200) { reader.await() })
                    release.countDown(); writer.await()
                    val rows = reader.await()
                    assertEquals(setOf("draft", "anchor"), rows.keys)
                    assertArrayEquals("after".toByteArray(), rows.getValue("draft"))
                    assertArrayEquals("after-anchor".toByteArray(), rows.getValue("anchor"))
                    rows.getValue("draft")[0] = 0
                } finally { release.countDown() }
            }
            writerStore.close()
            assertThrows(IllegalStateException::class.java) { writerStore.transaction { get("draft") } }
            assertArrayEquals("Closing the retired account does not retire its replacement", "after".toByteArray(), readerStore.transaction { get("draft") })
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally { cleanupAfterTest(primary,
            { second?.close() }, { first?.close() },
            { files.forEach { if (it.exists()) check(it.delete()) } },
            { if (keystore.containsAlias(alias)) keystore.deleteEntry(alias) }) }
        File(context.cacheDir, "spaces-store-concurrency.json").writeText(JsonObject().apply {
            addProperty("package", context.packageName); addProperty("sdk", Build.VERSION.SDK_INT)
            addProperty("pid", android.os.Process.myPid()); addProperty("appApkSha256", apkHash(File(context.applicationInfo.sourceDir)))
            addProperty("readerBlockedUntilCommit", true); addProperty("committedRow", "after")
            addProperty("retiredStoreCannotReopen", true); addProperty("replacementRemainsUsable", true); addProperty("ownedCleanupComplete", true)
        }.toString())
    }

    @Test fun actualJniAndKeystoreRecoverAdmittedHistoryWithoutWritingAnUnprotectedKeyring() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(BuildConfig.DEBUG && context.packageName == "nl.davidengelen.redsquare.dev")
        check(Build.HARDWARE in setOf("ranchu", "goldfish") && Looper.myLooper() != Looper.getMainLooper())
        val family = "spaces-contract-${UUID.randomUUID()}"
        val alias = context.packageName + "." + family
        val filename = "$family.keys"
        val files = listOf("", ".bak", ".new").map { File(context.noBackupFilesDir, filename + it) }
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(files.none(File::exists) && !keystore.containsAlias(alias))
        var originalFailure: Throwable? = null
        try {
            val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "1") // Public unsafe fixtures only.
            val member = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
            val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
            val scope = SpaceCrypto.Scope(service.publicKey, "a".repeat(64), "b".repeat(64))
            val creator = SpaceCrypto.device(owner.publicKey, "c".repeat(64)).create(scope)
            val original = creator.publish(owner, "message", "d".repeat(64), "e".repeat(128),
                "Available native private history", 1_700_000_000_000)
            val rejectedDevice = SpaceCrypto.device(member.publicKey, "f".repeat(64))
            val tentative = creator.admit(rejectedDevice.keyPackage(scope, member))
            val provisional = rejectedDevice.join(scope, tentative.welcome, tentative.bindings)
            val afterRejection = SpaceCrypto.restore(tentative.afterRejection.save())
            assertEquals(0L, afterRejection.epoch)
            assertEquals(creator.bindings, afterRejection.bindings)
            val newDevice = SpaceCrypto.device(member.publicKey, "6".repeat(64))
            val admission = afterRejection.admit(newDevice.keyPackage(scope, member))
            val joined = newDevice.join(scope, admission.welcome, admission.bindings)
            val future = admission.session.publish(owner, "message", "7".repeat(64), "8".repeat(128),
                "Accepted native membership", 1_700_000_000_002)
            assertEquals("Accepted native membership", joined.read(future.envelope()).text)
            assertThrows(java.security.GeneralSecurityException::class.java) { provisional.read(future.envelope()) }
            assertThrows(SpaceCrypto.MissingKeys::class.java) { joined.read(original.envelope()) }
            val snapshot = joined.save()
            val snapshotVersion = com.google.gson.JsonParser.parseString(snapshot.toString(Charsets.UTF_8)).asJsonObject.get("version").asInt
            assertEquals(2, snapshotVersion)
            assertTrue(snapshot.toString(Charsets.UTF_8).contains("archive_keys"))
            val storage = EncryptedLocalFile(context, filename, alias, family)
            storage.save(EncryptedLocalFile.Contents(1, snapshot.toString(Charsets.UTF_8)))
            val encrypted = files[0].readBytes()
            assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("archive_keys"))
            val loaded = requireNotNull(EncryptedLocalFile(context, filename, alias, family).load())
            assertTrue("Exact original snapshot is recovered", java.security.MessageDigest.isEqual(snapshot, loaded.text.toByteArray(Charsets.UTF_8)))
            val grant = admission.session.history(owner, joined.credential, "1".repeat(64), "2".repeat(128), 1_700_000_000_001)
            val recovered = SpaceCrypto.restore(loaded.text.toByteArray(Charsets.UTF_8)).acceptHistory(grant.transaction.envelope())
            assertEquals("Available native private history", recovered.read(original.envelope()).text)
            assertEquals(owner.publicKey, recovered.read(original.envelope()).author)
            val archiveOnly = SpaceHistoryKeys.restore(recovered.historyBackup(), member.publicKey)
            assertEquals("Available native private history", archiveOnly.read(original.envelope()).text)
            val freshScope = scope.copy(generation = "9".repeat(64))
            val fresh = SpaceCrypto.device(member.publicKey, "a".repeat(64)).create(freshScope)
            val renewed = fresh.publish(member, "message", "b".repeat(64), "c".repeat(128),
                "Fresh native key generation", 1_700_000_000_003)
            assertEquals("Fresh native key generation", fresh.read(renewed.envelope()).text)
            assertThrows(IllegalArgumentException::class.java) { archiveOnly.read(renewed.envelope()) }
            val evidence = File(context.cacheDir, "spaces-mls-evidence").apply { mkdirs() }
            File(evidence, "runtime.json").writeText(JsonObject().apply {
                addProperty("sdk", Build.VERSION.SDK_INT); addProperty("abi", Build.SUPPORTED_ABIS.first())
                addProperty("pageSize", Os.sysconf(OsConstants._SC_PAGESIZE)); addProperty("pid", android.os.Process.myPid())
                addProperty("installedApkSha256", apkHash(File(context.applicationInfo.sourceDir)))
                addProperty("installedTestApkSha256", apkHash(File(InstrumentationRegistry.getInstrumentation().context.applicationInfo.sourceDir)))
                addProperty("encryptedFileBytes", encrypted.size); addProperty("snapshotBytes", snapshot.size)
                addProperty("keystoreRoundTrip", true); addProperty("historyBeforeGrant", "unavailable")
                addProperty("historyAfterGrant", "exact original content")
                addProperty("confirmedRejectionBranchReloaded", true)
                addProperty("rejectedMembershipEpoch", afterRejection.epoch)
                addProperty("rejectedCandidateFutureDenied", true)
                addProperty("archiveOnlyHistoryRecovered", true)
                addProperty("freshGenerationSeparated", true)
                addProperty("operationalSnapshotVersion", snapshotVersion)
                addProperty("qualification", "Library/JNI/Keystore only; not UI, remote acceptance or distinct-process recovery")
            }.toString())
        } catch (failure: Throwable) { originalFailure = failure; throw failure }
        finally {
            var cleanupFailure: Throwable? = null
            val cleanup = files.map { file -> { if (file.exists()) check(file.delete()) } } +
                { if (keystore.containsAlias(alias)) keystore.deleteEntry(alias) }
            for (action in cleanup) try { action() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure.addSuppressed(failure)
            }
            cleanupFailure?.let { if (originalFailure == null) throw it else originalFailure.addSuppressed(it) }
        }
    }

    private fun apkHash(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
