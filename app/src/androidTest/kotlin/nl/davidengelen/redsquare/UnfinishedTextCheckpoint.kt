package nl.davidengelen.redsquare

import android.content.Context
import android.os.Build
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoLocalState
import org.junit.Assert.assertEquals
import java.io.File

internal data class NativeTextCheckpoint(val file: File, val text: String?, val encrypted: ByteArray)

/** Read the fixture's actual private checkpoint through Android Keystore, under its storage lock. */
internal fun unfinishedTextCheckpoint(context: Context): NativeTextCheckpoint? = synchronized(SaitoLocalState.lock) {
    check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
    val files = context.noBackupFilesDir.listFiles().orEmpty().filter { it.name.startsWith("editrecovery.") && it.name.endsWith(".v1") }
    if (files.isEmpty()) return@synchronized null
    assertEquals("Only this fixture's current recovery checkpoint exists", 1, files.size)
    val file = files.single()
    val contents = requireNotNull(EncryptedLocalFile(context, file.name, "redsquare.editrecovery.v1", "editrecovery.").load())
    NativeTextCheckpoint(file, JsonParser.parseString(contents.text).asJsonObject.get("text")?.asString, file.readBytes())
}
