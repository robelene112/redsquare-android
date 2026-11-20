package nl.davidengelen.redsquare.spaces

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Transactional, device-only encrypted rows. Scope selection is also checked
 * inside SpaceClient, so a callback cannot repurpose another account's store. */
internal class AndroidSpaceClientStore(context: Context, owner: String, node: String, service: String) :
    SpaceClientStore, AutoCloseable {
    private val namespace = MessageDigest.getInstance("SHA-256")
        .digest("SpacesLocal/v1:$owner:$node:$service".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private val databaseGate = databaseGate(File(context.noBackupFilesDir, "spaces-$namespace.sqlite").canonicalPath)
    private var closed = false
    private val alias = "${context.packageName}.spaces.store.v1.$namespace"
    private val aadPrefix = "${context.packageName}:SpacesLocal/v1:$namespace:"
    private val helper = object : SQLiteOpenHelper(context.applicationContext,
        File(context.noBackupFilesDir, "spaces-$namespace.sqlite").absolutePath, null, 1) {
        init { setWriteAheadLoggingEnabled(true) }
        override fun onConfigure(db: SQLiteDatabase) {
            db.execSQL("PRAGMA synchronous=FULL")
        }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE entries (key TEXT PRIMARY KEY NOT NULL,value BLOB NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            throw IllegalStateException("Community storage needs a compatible migration")
        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            throw IllegalStateException("Community storage cannot be downgraded")
    }

    override fun <T> transaction(action: SpaceClientStore.Transaction.() -> T): T {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Community storage belongs off main" }
        return databaseGate.withLock {
            check(!closed) { "Community storage is closed" }
            val db = helper.writableDatabase
            db.beginTransactionNonExclusive()
            try {
                val value = action(object : SpaceClientStore.Transaction {
                    override fun get(key: String): ByteArray? {
                        validateKey(key)
                        return db.rawQuery("SELECT value FROM entries WHERE key=?", arrayOf(key)).use {
                            if (it.moveToFirst()) decrypt(key, it.getBlob(0)) else null
                        }
                    }
                    override fun put(key: String, value: ByteArray) {
                        validateKey(key); require(value.size <= MAX_ROW_BYTES)
                        db.insertOrThrow("entries", null, ContentValues().apply {
                            put("key", key); put("value", encrypt(db, key, value))
                        }.also {
                            // Replace one authenticated row inside the enclosing transaction.
                            db.delete("entries", "key=?", arrayOf(key))
                        })
                    }
                    override fun remove(key: String) { validateKey(key); db.delete("entries", "key=?", arrayOf(key)) }
                    override fun page(prefix: String, cursor: String, limit: Int): List<SpaceClientStore.Entry> {
                        validateKey(prefix); require(cursor.length <= 512 && limit in 1..100)
                        return db.rawQuery("SELECT key,value FROM entries WHERE key>=? AND key<? AND key>=? ORDER BY key LIMIT ?",
                            arrayOf(prefix, prefix + '\uffff', cursor, limit.toString())).use { rows ->
                            buildList { while (rows.moveToNext()) {
                                val key = rows.getString(0)
                                add(SpaceClientStore.Entry(key, decrypt(key, rows.getBlob(1))))
                            } }
                        }
                    }
                })
                db.setTransactionSuccessful()
                value
            } finally { db.endTransaction() }
        }
    }

    private fun existingKey(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        .getKey(alias, null) as? SecretKey

    private fun encrypt(db: SQLiteDatabase, key: String, plaintext: ByteArray): ByteArray {
        val secret = existingKey() ?: run {
            db.rawQuery("SELECT 1 FROM entries LIMIT 1", null).use {
                check(!it.moveToFirst()) { "Community storage keys require recovery" }
            }
            KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secret); updateAAD((aadPrefix + key).toByteArray(Charsets.UTF_8))
        }
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
    }
    private fun decrypt(key: String, bytes: ByteArray): ByteArray {
        require(bytes.size in 29..(MAX_ROW_BYTES + 29) && bytes[0] == 1.toByte())
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, requireNotNull(existingKey()) { "Community storage keys require recovery" },
                GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            updateAAD((aadPrefix + key).toByteArray(Charsets.UTF_8))
            doFinal(bytes.copyOfRange(13, bytes.size))
        }
    }
    private fun validateKey(key: String) { require(key.isNotEmpty() && key.length <= 512 && key.all { it.code in 33..126 }) }
    override fun close() = databaseGate.withLock {
        if (!closed) { closed = true; helper.close() }
    }
    companion object {
        private const val MAX_ROW_BYTES = 8 * 1024 * 1024
        /** Schema maintenance shares the canonical-file gate with every
         * application transaction. Release it before driving any UI action. */
        fun <T> withDatabaseMaintenance(file: File, action: () -> T): T {
            check(Looper.myLooper() != Looper.getMainLooper()) { "Community storage belongs off main" }
            return databaseGate(file.canonicalPath).withLock(action)
        }
        private val databaseGates = mutableMapOf<String, WeakReference<ReentrantLock>>()
        private fun databaseGate(path: String): ReentrantLock = synchronized(databaseGates) {
            databaseGates.entries.removeAll { it.value.get() == null }
            databaseGates[path]?.get() ?: ReentrantLock(true).also { databaseGates[path] = WeakReference(it) }
        }
    }
}
