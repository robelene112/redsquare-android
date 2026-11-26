package nl.davidengelen.redsquare

import android.content.pm.ApplicationInfo
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.AndroidSpaceClientStore
import nl.davidengelen.redsquare.spaces.spaceServiceDirectory
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Import the signed fixture through the actual encrypted repository before timing begins. */
class SpacesScaleSeedObserver {
    @Test fun importReleaseScaleFixture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val context = instrumentation.targetContext
        check(BuildConfig.SPACES_SCALE_FIXTURE && context.packageName == "nl.davidengelen.redsquare.performance" &&
            Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0)
        val root = File(context.externalMediaDirs.single(), "spaces-scale")
        val manifest = JsonParser.parseString(File(root, "fixture.json").readText()).asJsonObject
        val source = File(root, "fixture-author.sqlite")
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        fun hex(value: ByteArray) = value.joinToString("") { "%02x".format(it) }
        val sourceHash = hex(digest.digest())
        check(sourceHash == requireNotNull(arguments.getString("spacesScaleDatabaseSha256")))
        val expectedRows = requireNotNull(arguments.getString("spacesScaleDatabaseRows")).toLong().also { require(it > 0) }
        val owner = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        val authority = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").publicKey
        check(manifest.get("owner").asString == owner.publicKey && manifest.get("authority").asString == authority)
        check(manifest.get("node").asString == SaitoBackend.OWNER.webUrl)
        check(manifest.get("fixture").asString in setOf("spaces-147-scale", "spaces-147-smoke"))
        if (manifest.get("qualifiesForTimedRuns").asBoolean) {
            check(manifest.get("spaces").asInt == 100 && manifest.get("channels").asInt == 1000 &&
                manifest.get("acceptedMessages").asInt == 100000 && manifest.get("acceptedFiles").asInt == 1000)
        }
        val port = requireNotNull(arguments.getString("spacesScalePort")).toInt().also { require(it in 1024..65535) }
        val endpoint = "ws://127.0.0.1:$port/wsopen"
        check(context.noBackupFilesDir.listFiles().orEmpty().none { it.name.startsWith("spaces-") || it.name.startsWith("wallet.") })
        val wallets = AndroidWalletStore(context)
        wallets.replace(wallets.loadOrCreate(), owner)
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        var imported = 0L
        AndroidSpaceClientStore(context, owner.publicKey, "${SaitoBackend.OWNER}:$endpoint", authority).use { destination ->
            SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READONLY).use { fixture ->
                fixture.rawQuery("SELECT key, value FROM entries ORDER BY key", null).use { rows ->
                    while (rows.moveToNext()) {
                        destination.transaction {
                            do {
                                put(rows.getString(0), rows.getBlob(1))
                                imported++
                            } while (imported % 256 != 0L && rows.moveToNext())
                        }
                        if (imported % 8192 == 0L) instrumentation.sendStatus(2, Bundle().apply {
                            putLong("spacesScaleImportedRows", imported)
                            putLong("spacesScaleExpectedRows", expectedRows)
                        })
                    }
                }
            }
        }
        check(imported == expectedRows)
        spaceServiceDirectory(context, owner.publicKey, endpoint).save(EncryptedLocalFile.Contents(1, authority))
        instrumentation.sendStatus(0, Bundle().apply {
            putString("spacesScaleSeed", JsonObject().apply {
                addProperty("databaseSha256", sourceHash)
                addProperty("authenticatedLocalRows", imported)
                addProperty("manifestSha256", hex(digest.digest(File(root, "fixture.json").readBytes())))
                addProperty("pid", android.os.Process.myPid())
                addProperty("scope", manifest.get("fixture").asString)
            }.toString())
        })
    }
}
