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
    commitDirectorySync: (FileDescriptor) -> Unit = Os::fsync) {
    private val prefix = "personal.$owner."
    private val alias = "personal.$owner"
    private val activeFile = File(context.noBackupFilesDir, "${prefix}v1")
    private val recoveryFile = File(context.noBackupFilesDir, "${prefix}recovery.v1")
    private val active = EncryptedLocalFile(context, activeFile.name, alias, prefix)
    private val commit = EncryptedLocalFile(context, activeFile.name, alias, prefix, commitDirectorySync)
    private val recovery = EncryptedLocalFile(context, recoveryFile.name, alias, prefix)

    fun load(): PersonalPolicy = synchronized(SaitoLocalState.lock) {
        if (hasRecovery()) return@synchronized recover()
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
            recovery.save(anchor)
            commit.save(encode(updated))
            removeRecovery()
            updated
        } catch (failure: Exception) {
            try {
                // Anchor removal may itself fail after deletion. Re-establish the
                // previous policy before restoring active bytes in that case too.
                recovery.save(anchor)
                check(recover() == previous)
            } catch (restoreFailure: Exception) { throw RecoveryRequired(restoreFailure) }
            throw failure
        }
    }

    private fun hasRecovery() = listOf("", ".bak", ".new").any { File(recoveryFile.path + it).exists() }

    private fun recover(): PersonalPolicy {
        val previous = decode(requireNotNull(recovery.load()) { "Personal recovery record unavailable" })
        // Recovery bypasses only the injected commit fault, never real OS errors.
        val anchor = encode(previous)
        active.save(anchor)
        try { removeRecovery() }
        catch (failure: Exception) {
            // A failed deletion sync must not discard the recovery instruction.
            recovery.save(anchor)
            throw failure
        }
        return previous
    }

    private fun removeRecovery() {
        AtomicFile(recoveryFile).delete()
        check(!hasRecovery()) { "Personal recovery could not be committed" }
        val directory = Os.open(context.noBackupFilesDir.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(directory) } finally { Os.close(directory) }
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
