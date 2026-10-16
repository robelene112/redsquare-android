package nl.davidengelen.redsquare

import java.io.File
import nl.davidengelen.redsquare.saito.SaitoLocalState

/** Preserve only the explicit fixture scopes; cleanup actions remain independent of identity restoration. */
internal class ChatFixtureState(private val context: android.content.Context,
    private val prefixes: List<String>, owner: String) {
    private val files = synchronized(SaitoLocalState.lock) {
        context.noBackupFilesDir.listFiles()!!.filter { file -> prefixes.any(file.name::startsWith) }
            .associate { check(it.isFile); it.name to it.readBytes() }
    }
    private val preferences = listOf("notification-permission" to "offered",
        "notification-permission" to "social-offered:$owner", "wallet-backup-reminders" to "hidden:$owner")
        .map { (name, key) -> val prefs = context.getSharedPreferences(name, 0)
            Triple(prefs, key, if (prefs.contains(key)) prefs.getBoolean(key, false) else null) }
    fun prepare() {
        synchronized(SaitoLocalState.lock) { files.keys.forEach { check(File(context.noBackupFilesDir, it).delete()) } }
        preferences.forEach { (prefs, key, _) -> check(prefs.edit().apply {
            if (key.startsWith("social-offered:")) remove(key) else putBoolean(key, true)
        }.commit()) }
    }
    fun restoreFiles() {
        synchronized(SaitoLocalState.lock) {
            context.noBackupFilesDir.listFiles()!!.filter { file -> prefixes.any(file.name::startsWith) }
                .forEach { check(it.isFile && it.delete()) }
            files.forEach { (name, bytes) -> File(context.noBackupFilesDir, name).writeBytes(bytes) }
        }
    }
    fun restorePreferences() {
        preferences.forEach { (prefs, key, value) -> check(prefs.edit().apply {
            if (value == null) remove(key) else putBoolean(key, value)
        }.commit()) }
    }
}

