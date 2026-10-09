package nl.davidengelen.redsquare

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/** Separate test APK/process. Only Android's picker can grant document access. */
class ExportTestDocumentsProvider : DocumentsProvider() {
    private val directory get() = File(requireNotNull(context).filesDir, "wallet-test-documents").apply { mkdirs() }

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE, Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES)
        val values = mapOf<String, Any>(Root.COLUMN_ROOT_ID to ROOT_ID,
            Root.COLUMN_DOCUMENT_ID to ROOT_ID, Root.COLUMN_TITLE to TITLE,
            Root.COLUMN_FLAGS to (Root.FLAG_SUPPORTS_CREATE or Root.FLAG_LOCAL_ONLY),
            Root.COLUMN_MIME_TYPES to "application/json")
        return MatrixCursor(columns).apply { addRow(columns.map { values[it] }) }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        documents(projection, listOf(documentId))

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        require(parentDocumentId == ROOT_ID)
        return documents(projection, directory.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty())
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        require(parentDocumentId == ROOT_ID && mimeType == "application/json")
        val file = document(displayName)
        if (!file.createNewFile()) throw FileNotFoundException("Test document already exists")
        return displayName
    }

    override fun deleteDocument(documentId: String) {
        if (!document(documentId).delete()) throw FileNotFoundException("Test document unavailable")
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(document(documentId), ParcelFileDescriptor.parseMode(mode))

    override fun isChildDocument(parentDocumentId: String, documentId: String) =
        parentDocumentId == ROOT_ID && documentId != ROOT_ID && document(documentId).exists()

    private fun document(id: String): File {
        require(id.matches(Regex("[a-zA-Z0-9._-]{1,100}")) && id != "." && id != ".." && id != ROOT_ID)
        return File(directory, id)
    }

    private fun documents(projection: Array<out String>?, ids: List<String>): Cursor {
        val columns = projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE)
        return MatrixCursor(columns).apply {
            ids.forEach { id ->
                val root = id == ROOT_ID
                val values = mapOf<String, Any>(Document.COLUMN_DOCUMENT_ID to id,
                    Document.COLUMN_DISPLAY_NAME to if (root) TITLE else id,
                    Document.COLUMN_MIME_TYPE to if (root) Document.MIME_TYPE_DIR else "application/json",
                    Document.COLUMN_FLAGS to if (root) Document.FLAG_DIR_SUPPORTS_CREATE
                        else Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE,
                    Document.COLUMN_SIZE to if (root) 0L else document(id).length())
                addRow(columns.map { values[it] })
            }
        }
    }

    companion object {
        const val AUTHORITY = "nl.davidengelen.redsquare.dev.test.wallet-documents"
        const val ROOT_ID = "backups"
        const val TITLE = "Red Square test backups"
    }
}
