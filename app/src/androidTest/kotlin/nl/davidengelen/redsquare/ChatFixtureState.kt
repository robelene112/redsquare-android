package nl.davidengelen.redsquare

import java.io.File
import nl.davidengelen.redsquare.saito.SaitoLocalState
import nl.davidengelen.redsquare.feed.PublicStringStore

/** Preserve declared files and this wallet's Chat alert state; restoration remains independent of identity. */
internal class ChatFixtureState(private val context: android.content.Context,
    declaredPrefixes: List<String>, owner: String) {
    private val prefixes = (declaredPrefixes + listOf("chatpush.$owner.", "groupchatpush.$owner.", "avatarprofiles.")).distinct()
    private val files = withStoreLocks {
        context.noBackupFilesDir.listFiles()!!.filter { file -> prefixes.any(file.name::startsWith) }
            .associate { check(it.isFile); it.name to it.readBytes() }
    }
    private val preferences = listOf("notification-permission" to "offered",
        "notification-permission" to "social-offered:$owner", "wallet-backup-reminders" to "hidden:$owner")
        .map { (name, key) -> val prefs = context.getSharedPreferences(name, 0)
            Triple(prefs, key, if (prefs.contains(key)) prefs.getBoolean(key, false) else null) }
    fun prepare() {
        withStoreLocks {
            context.noBackupFilesDir.listFiles()!!.filter { file -> prefixes.any(file.name::startsWith) }.forEach { file ->
                // Two captured wallet scopes may share the same public avatar cache.
                // An absent file is already prepared; a present file must still clear successfully.
                check(!file.exists() || (file.isFile && file.delete()))
            }
        }
        preferences.forEach { (prefs, key, _) -> check(prefs.edit().apply {
            if (key.startsWith("social-offered:")) remove(key) else putBoolean(key, true)
        }.commit()) }
    }
    fun restoreFiles() {
        withStoreLocks {
            context.noBackupFilesDir.listFiles()!!.filter { file -> prefixes.any(file.name::startsWith) }
                .forEach { check(it.isFile && it.delete()) }
            files.forEach { (name, bytes) -> File(context.noBackupFilesDir, name).writeBytes(bytes) }
        }
    }
    // Wallet/Chat and public caches use distinct monitors. Keep fixture capture,
    // preparation and restoration atomic with their actual storage operations.
    private fun <T> withStoreLocks(block: () -> T): T = synchronized(SaitoLocalState.lock) {
        synchronized(PublicStringStore.lock) { block() }
    }
    fun restorePreferences() {
        preferences.forEach { (prefs, key, value) -> check(prefs.edit().apply {
            if (value == null) remove(key) else putBoolean(key, value)
        }.commit()) }
    }
}

