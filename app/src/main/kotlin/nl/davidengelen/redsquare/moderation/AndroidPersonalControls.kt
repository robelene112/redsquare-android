package nl.davidengelen.redsquare.moderation

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import nl.davidengelen.redsquare.saito.EncryptedLocalFile
import nl.davidengelen.redsquare.saito.SaitoLocalState
import nl.davidengelen.redsquare.saito.SaitoWallet

/** A verified previous policy remains authoritative until the new commit finishes. */
internal class AndroidPersonalControls(private val context: Context, private val owner: String,
    commitDirectorySync: (FileDescriptor) -> Unit = Os::fsync,
    private val recoveryDirectorySync: (FileDescriptor) -> Unit = Os::fsync) {
    private val prefix = "personal.$owner."
    private val alias = "personal.$owner"
    private val activeFile = File(context.noBackupFilesDir, "${prefix}v1")
    private val recoveryFile = File(context.noBackupFilesDir, "${prefix}recovery.v1")
    private val guardFile = File(context.noBackupFilesDir, "${prefix}guard.v1")
    private val active = EncryptedLocalFile(context, activeFile.name, alias, prefix)
    private val commit = EncryptedLocalFile(context, activeFile.name, alias, prefix, commitDirectorySync)
    private val recovery = EncryptedLocalFile(context, recoveryFile.name, alias, prefix)
    private val guard = EncryptedLocalFile(context, guardFile.name, alias, prefix)

    fun load(): PersonalPolicy = synchronized(SaitoLocalState.lock) {
        if (hasRecord(guardFile) || hasRecovery()) return@synchronized recover()
        active.load()?.let(::decode) ?: run {
            check(!File(activeFile.path + ".new").exists()) { "Personal controls require recovery" }
            PersonalPolicy()
        }
    }

    fun save(expected: PersonalPolicy, updated: PersonalPolicy): PersonalPolicy = synchronized(SaitoLocalState.lock) {
        validate(updated)
        val previous = try { load() } catch (failure: Exception) { throw RecoveryRequired(failure) }
        if (previous != expected) throw RecoveryRequired(IOException("Saved personal choices changed"))
        val anchor = encode(previous)
        try {
            // This verified copy survives deletion of the recovery anchor and
            // every fallible commit step. Startup treats it as authoritative.
            guard.save(anchor)
            recovery.save(anchor)
            commit.save(encode(updated))
            removeRecovery()
            removeGuard()
            updated
        } catch (failure: Exception) {
            try { restore(previous) }
            catch (restoreFailure: Exception) {
                failure.addSuppressed(restoreFailure)
                throw RecoveryRequired(failure)
            }
            throw failure
        }
    }

    private fun hasRecord(file: File) = listOf("", ".bak", ".new").any { File(file.path + it).exists() }
    private fun hasRecovery() = hasRecord(recoveryFile)

    private fun recover(): PersonalPolicy {
        val record = if (hasRecord(guardFile)) guard.load() else recovery.load()
        val previous = decode(requireNotNull(record) { "Personal recovery record unavailable" })
        restore(previous)
        return previous
    }

    private fun restore(previous: PersonalPolicy) {
        val anchor = encode(previous)
        var restorationFailure: Exception? = null
        // A failed anchor recreation must not skip normal active restoration.
        // Each attempt uses real OS sync, independently of injected commit faults.
        for (store in listOf(guard, recovery, active)) {
            try { store.save(anchor) }
            catch (failure: Exception) {
                val first = restorationFailure
                if (first == null) restorationFailure = failure else first.addSuppressed(failure)
            }
        }
        restorationFailure?.let { throw it }
        removeRecovery()
        removeGuard()
    }

    private fun removeGuard() {
        AtomicFile(guardFile).delete()
        // No fallible work follows publication. Reappearance after physical
        // power loss conservatively restores previous choices on startup.
        check(!hasRecord(guardFile)) { "Personal choices require recovery" }
    }

    private fun removeRecovery() {
        AtomicFile(recoveryFile).delete()
        check(!hasRecovery()) { "Personal recovery could not be committed" }
        val directory = Os.open(context.noBackupFilesDir.path, OsConstants.O_RDONLY, 0)
        try { recoveryDirectorySync(directory) } finally { Os.close(directory) }
    }

    private fun encode(policy: PersonalPolicy): EncryptedLocalFile.Contents {
        validate(policy)
        val record = JsonObject().apply {
            addProperty("version", 1)
            addProperty("owner", owner)
            add("muted", JsonArray().apply { policy.muted.sorted().forEach(::add) })
            add("blocked", JsonArray().apply { policy.blocked.sorted().forEach(::add) })
        }
        return EncryptedLocalFile.Contents(1, record.toString())
    }

    private fun decode(saved: EncryptedLocalFile.Contents): PersonalPolicy {
        require(saved.version == 1.toByte())
        val record = JsonParser.parseString(saved.text).asJsonObject
        require(record.get("version").asInt == 1 && record.get("owner").asString == owner)
        fun accounts(name: String): Set<String> {
            val values = record.getAsJsonArray(name).map { it.asString }
            require(values.size <= MAX_ACCOUNTS && values.distinct().size == values.size)
            return values.toSet()
        }
        return PersonalPolicy(accounts("muted"),
            if (record.has("blocked")) accounts("blocked") else emptySet()).also(::validate)
    }

    private fun validate(policy: PersonalPolicy) {
        require(SaitoWallet.isPublicKey(owner) && policy.muted.size <= MAX_ACCOUNTS && policy.blocked.size <= MAX_ACCOUNTS)
        require((policy.muted + policy.blocked).all(SaitoWallet::isPublicKey))
    }

    class RecoveryRequired(cause: Exception) : IOException("Personal controls require recovery", cause)
    private companion object { const val MAX_ACCOUNTS = 1000 }
}
