package nl.davidengelen.redsquare;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;

/** Separate read-only synthetic image root. Never reads wallet fixture documents. */
public final class ImageTestDocumentsProvider extends DocumentsProvider {
    public static final String TITLE = "Red Square test images";
    private static final String ROOT = "images";

    @Override public boolean onCreate() { return true; }

    private File directory() { return new File(getContext().getFilesDir(), "image-test-documents"); }

    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : new String[] {
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES
        });
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            Object value = null;
            switch (column) {
                case Root.COLUMN_ROOT_ID: case Root.COLUMN_DOCUMENT_ID: value = ROOT; break;
                case Root.COLUMN_TITLE: value = TITLE; break;
                case Root.COLUMN_FLAGS: value = Root.FLAG_LOCAL_ONLY; break;
                case Root.COLUMN_MIME_TYPES: value = "image/png"; break;
            }
            row.add(value);
        }
        return cursor;
    }

    @Override public Cursor queryDocument(String id, String[] projection) {
        MatrixCursor cursor = documents(projection);
        add(cursor, id);
        return cursor;
    }

    @Override public Cursor queryChildDocuments(String parent, String[] projection, String sortOrder) {
        if (!ROOT.equals(parent)) throw new IllegalArgumentException("Invalid test image parent");
        MatrixCursor cursor = documents(projection);
        File[] files = directory().listFiles();
        if (files != null) for (File file : files) if (file.isFile()) add(cursor, file.getName());
        return cursor;
    }

    @Override public boolean isChildDocument(String parent, String id) {
        return ROOT.equals(parent) && !ROOT.equals(id) && document(id).isFile();
    }

    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only test image");
        return ParcelFileDescriptor.open(document(id), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private File document(String id) {
        if (!id.matches("[a-zA-Z0-9._-]{1,100}\\.png")) throw new IllegalArgumentException("Invalid test image");
        return new File(directory(), id);
    }

    private MatrixCursor documents(String[] projection) {
        return new MatrixCursor(projection != null ? projection : new String[] {
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE
        });
    }

    private void add(MatrixCursor cursor, String id) {
        boolean root = ROOT.equals(id);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            Object value = null;
            switch (column) {
                case Document.COLUMN_DOCUMENT_ID: value = id; break;
                case Document.COLUMN_DISPLAY_NAME: value = root ? TITLE : id; break;
                case Document.COLUMN_MIME_TYPE: value = root ? Document.MIME_TYPE_DIR : "image/png"; break;
                case Document.COLUMN_FLAGS: value = 0; break;
                case Document.COLUMN_SIZE: value = root ? 0L : document(id).length(); break;
            }
            row.add(value);
        }
    }
}
