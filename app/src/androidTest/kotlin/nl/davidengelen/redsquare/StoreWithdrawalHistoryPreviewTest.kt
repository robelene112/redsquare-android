package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class StoreWithdrawalHistoryPreviewTest {
    @Test fun aFullLegacyHistoryCanAcceptTheNextRequestWithoutDeletingSubmittedOrUncertainOriginals() {
        withStoreSellerFixture("withdrawal-full-history") { _, _, _ ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val ref = InstrumentationRegistry.getInstrumentation().context.assets.open("native-store-seller-payments-reference.json")
                .reader().use { JsonParser.parseReader(it).asJsonObject }
            val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
            val source = ref.getAsJsonObject("history")
            val candidates = source.getAsJsonArray("quotes").mapIndexed { index, value ->
                val now = source.get("timestamp").asLong + index
                val quote = SaitoStoreWithdrawal.readQuote(value.toString(), ref.get("store").asString, "USDT",
                    "0x1111111111111111111111111111111111111111", now)
                SaitoStoreWithdrawal.create(wallet, quote, emptyList(), BigInteger.ZERO, now)
            }
            val chain = OwnedStoreCryptoFixture().methods.getAsJsonArray("ac").map { it.asJsonObject }.single { it.get("ticker").asString == "USDT" }.get("chain_id").asString
            // The pre-paging v1 persisted format is an independent upgrade input at the encrypted filesystem seam.
            val fundedOriginal = SaitoStoreWithdrawal.restore(ref.getAsJsonObject("candidate").get("networkBase64").asString, ref.get("store").asString)
            val retained = candidates.take(99) + fundedOriginal
            val legacy = JsonArray().apply { retained.forEachIndexed { index, candidate -> add(JsonObject().apply {
                addProperty("backend", "owner"); addProperty("candidate", candidate.transaction.encoded()); addProperty("store", candidate.quote.store)
                addProperty("chain", chain); addProperty("network", "Ethereum"); addProperty("mayHaveSent", true)
                add("result", if (index == 99) JsonNull.INSTANCE else JsonObject().apply { addProperty("status", "submitted"); addProperty("id", (index + 1).toString()) })
            }) } }
            val prefix = "store-withdrawals.${wallet.publicKey}."
            EncryptedLocalFile(context, prefix + "rows", "redsquare-store-withdrawals-v1", "store-withdrawals.").save(EncryptedLocalFile.Contents(1, legacy.toString()))
            EncryptedLocalFile(context, prefix + "guard", "redsquare-store-withdrawals-v1", "store-withdrawals.").save(EncryptedLocalFile.Contents(1, wallet.publicKey))
            val journal = StoreWithdrawalStore(context, wallet.publicKey)
            // Replay an interrupted, not-yet-committed new page; older committed work stays usable.
            val partial = java.io.File(context.noBackupFilesDir, prefix + "rows.1.new")
            java.io.FileOutputStream(partial).use { it.write(byteArrayOf(1, 2, 3)); it.fd.sync() }
            val original = journal.read(); assertEquals(100, original.size)
            val next = journal.reserve(SaitoBackend.OWNER, candidates.last(), chain, "Ethereum")
            assertEquals(candidates.last().transaction.id, journal.read().last().candidate.transaction.id)
            // Replay process loss after the original page commit but before the page-count publication.
            android.util.AtomicFile(java.io.File(context.noBackupFilesDir, prefix + "pages")).delete()
            val cold = StoreWithdrawalStore(context, wallet.publicKey)
            assertEquals(candidates.last().transaction.encoded(), cold.read().single().candidate.transaction.encoded())
            assertTrue(TransferStore(context, wallet.publicKey).available(fundedOriginal.inputs).isEmpty())
            journal.observe(original.first(), SaitoStoreWithdrawal.Result(SaitoStoreWithdrawal.State.SUBMITTED, "1"))
            assertThrows(IllegalArgumentException::class.java) { journal.discard(original.first()) }
            assertThrows(IllegalArgumentException::class.java) { journal.discard(original.last()) }
            assertThrows(IllegalArgumentException::class.java) { journal.reserve(SaitoBackend.OWNER, original.last().candidate, chain, "Ethereum") }
            assertEquals(retained.map { it.transaction.encoded() }, journal.read(1).map { it.candidate.transaction.encoded() })
            val firstPage = journal.history(SaitoBackend.OWNER)
            assertEquals(24, firstPage.rows.size); assertTrue(firstPage.more)
            val lastPage = journal.history(SaitoBackend.OWNER, 4)
            assertEquals(5, lastPage.rows.size); assertFalse(lastPage.more)
            assertTrue(journal.history(SaitoBackend.OFFICIAL).rows.isEmpty())
            journal.discard(next)
            assertTrue(TransferStore(context, wallet.publicKey).hasWork())
            assertTrue(TransferStore(context, wallet.publicKey).available(fundedOriginal.inputs).isEmpty())
        }
    }
}
