package nl.davidengelen.redsquare

import android.os.Build
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Uses only APIs already shipped in127, inside its real nondebuggable ordinary package. */
class ModuleUpdateSeedObserver {
    @Test fun seedPublicModuleFixturesOnlyBeforeTheFirstLaunchOfSigned127OnAnEmulator() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode == 127L)
        check(context.noBackupFilesDir.listFiles().orEmpty().none { it.name.startsWith("wallet.") })
        val seed = instrumentation.context.assets.open("native-module-update-seed.json").reader().use { JsonParser.parseReader(it).asJsonObject }
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2") // Published unsafe fixture. Never fund.
        check(wallet.publicKey == "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU" && seed.get("owner").asString == wallet.publicKey)
        val wallets = AndroidWalletStore(context)
        wallets.replace(wallets.loadOrCreate(), wallet)
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        for (item in seed.getAsJsonArray("records")) {
            val record = item.asJsonObject
            val name = record.get("name").asString
            check(name.startsWith("vault.${wallet.publicKey}.") || name.startsWith("store-orders.${wallet.publicKey}."))
            check(!File(context.noBackupFilesDir, name).exists())
            val file = EncryptedLocalFile(context, name, record.get("alias").asString, record.get("family").asString)
            val contents = EncryptedLocalFile.Contents(1, record.get("text").asString)
            file.save(contents); assertEquals(contents, file.load())
        }
        instrumentation.sendStatus(0, Bundle().apply { putString("moduleUpdateSeed", JsonObject().apply {
            addProperty("owner", wallet.publicKey); addProperty("sourceVersion", 127)
            addProperty("encryptedRecords", seed.getAsJsonArray("records").size()); addProperty("noNetworkSubmission", true)
            add("expected", seed.getAsJsonObject("expected"))
        }.toString()) })
    }
}
