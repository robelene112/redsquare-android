package nl.davidengelen.redsquare

import android.os.Build
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import nl.davidengelen.redsquare.saito.*
import nl.davidengelen.redsquare.vault.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

/** Read through actual current feature APIs after install-r; never sends a transaction. */
class ModuleUpdateVerifyObserver {
    @Test fun theSignedUpdateReadsTheExactPendingOperationsDraftAndOfflineAndExportBytes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode > 127L)
        val seed = instrumentation.context.assets.open("native-module-update-seed.json").reader().use { JsonParser.parseReader(it).asJsonObject }
        val owner = seed.get("owner").asString
        check(owner == "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU" && AndroidWalletStore(context).loadExisting().publicKey == owner)
        for (item in seed.getAsJsonArray("records")) {
            val record = item.asJsonObject
            assertEquals(EncryptedLocalFile.Contents(1, record.get("text").asString),
                EncryptedLocalFile(context, record.get("name").asString, record.get("alias").asString, record.get("family").asString).load())
        }
        val expected = seed.getAsJsonObject("expected")
        val order = StoreOrderStore(context, owner).read().single()
        assertEquals(expected.get("orderId").asString, order.id)
        assertTrue(order.mayHaveSent); assertNull(order.receipt); assertNull(order.payment)
        val operation = requireNotNull(VaultUploadStore(context, owner, SaitoBackend.OWNER).read())
        assertEquals(expected.get("vaultOperationId").asString, operation.candidate.transaction.id)
        assertTrue(operation.mayHaveSent); assertTrue(operation.submitted); assertFalse(operation.complete)
        val disk = VaultDisk(context, owner, SaitoBackend.OWNER)
        assertEquals(expected.get("fileId").asString, disk.copies().items.single().fileId)
        val bytes = Base64.getDecoder().decode(expected.get("fileContentsBase64").asString)
        assertArrayEquals(bytes, disk.read(expected.get("fileId").asString).bytes)
        val draft = requireNotNull(disk.draft())
        assertEquals(expected.get("draftId").asString, draft.id)
        assertEquals(expected.get("draftName").asString, draft.name)
        assertArrayEquals(Base64.getDecoder().decode(expected.get("draftContentsBase64").asString), draft.bytes)
        val transfer = VaultUploadTransfer(context, AndroidWalletStore(context).loadExisting(), SaitoBackend.OWNER,
            "ws://127.0.0.1:1/wsopen", WalletBalances.fromIntent(android.content.Intent().putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")))
        val plan = transfer.restorePlan(operation, draft) // Pure local reconstruction; no network access.
        assertEquals(operation.candidate.transaction.encoded(), plan.mint.encoded())
        assertEquals(VaultRuleMode.STANDARD, disk.draftRules(draft.id).mode)
        assertArrayEquals(bytes, requireNotNull(disk.export()).bytes)
        val filesharePreserved = FileshareUpdateFixture.verify(context, owner)
        instrumentation.sendStatus(0, Bundle().apply { putString("moduleUpdateContinuity", JsonObject().apply {
            addProperty("owner", owner); addProperty("version", context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
            addProperty("exactPendingStoreOriginal", true); addProperty("exactPendingVaultOriginal", true); addProperty("pendingVaultPlanReconstructed", true)
            addProperty("exactVaultDraft", true); addProperty("exactOfflineAndPendingExportBytes", true)
            addProperty("exactRetainedFileshareAndInterruptedHistory", filesharePreserved)
            addProperty("encryptedRecordsUnchanged", seed.getAsJsonArray("records").size()); addProperty("noNetworkSubmission", true)
        }.toString()) })
    }
}
