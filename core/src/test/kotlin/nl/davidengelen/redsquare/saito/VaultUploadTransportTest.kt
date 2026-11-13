package nl.davidengelen.redsquare.saito

import com.google.gson.JsonParser
import kotlinx.coroutines.*
import nl.davidengelen.redsquare.ControlledFeedPeer
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class VaultUploadTransportTest {
    @Test fun aLargeUploadWaitsForItsOwnCallbackWithoutHoldingOtherFeatureReplies() = runBlocking {
        val vector = requireNotNull(javaClass.getResourceAsStream("/native-vault-mint-reference.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val large = vector.getAsJsonObject("large")
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Public unsafe fixture only.
        val inputs = vector.getAsJsonArray("inputs").map { value -> value.asJsonObject.let {
            SaitoBalanceSnapshot.Slip(BigInteger(it.get("block").asString), BigInteger(it.get("ordinal").asString),
                it.get("index").asInt, BigInteger(it.get("amount").asString), it.get("type").asInt)
        } }
        val bytes = ByteArray(large.get("size").asInt) { ((it * 31 + 7) and 255).toByte() }
        val plan = SaitoVaultMint.create(wallet, inputs, large.get("name").asString, large.get("mime").asString,
            bytes, vector.get("timestamp").asLong)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val workers = Executors.newSingleThreadExecutor()
        val server = ControlledFeedPeer("[]", advertisedServices = "archive||;vault||", archiveResponse = { "[]" })
        server.vaultUploadResponseExecutor = workers
        server.vaultUploadResponse = { signed ->
            assertEquals(wallet.publicKey, signed.author)
            val file = SaitoEnvelope.decode(signed.body.get("data").asString)
            assertEquals(large.get("fileSignature").asString, file.id)
            entered.countDown()
            check(release.await(30, TimeUnit.SECONDS))
            "{\"status\":\"success\",\"err\":\"\"}"
        }
        val claims = AtomicInteger()
        try {
            server.start().use { endpoint ->
                SaitoPeer(wallet, "ws://127.0.0.1:${endpoint.port}/wsopen").use { peer ->
                    withTimeout(40_000) {
                        coroutineScope {
                            val upload = async(Dispatchers.IO) { peer.requestVaultUpload(plan) { claims.incrementAndGet() } }
                            assertTrue(entered.await(5, TimeUnit.SECONDS))
                            val other = withTimeout(5_000) { peer.request("{\"request\":\"archive\",\"data\":{\"request\":\"load\"}}".toByteArray()).decodeToString() }
                            assertEquals("[]", other)
                            delay(13_000) // A Vault callback may legitimately outlive the unchanged ordinary12s request budget.
                            release.countDown()
                            assertEquals("success", JsonParser.parseString(upload.await().decodeToString()).asJsonObject.get("status").asString)
                        }
                    }
                }
                server.assertHealthy()
                assertEquals(1, claims.get())
                assertEquals(1, server.vaultUploads.size)
            }
        } catch (failure: Throwable) {
            try { server.assertHealthy() } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
            throw failure
        } finally {
            release.countDown(); workers.shutdownNow()
            check(workers.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
}
