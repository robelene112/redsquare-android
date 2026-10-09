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
import java.io.IOException;

/** Standalone test APK process: depends only on Android/Java, not the target APK. */
public final class ExportTestDocumentsProvider extends DocumentsProvider {
    public static final String AUTHORITY = "nl.davidengelen.redsquare.dev.test.wallet-documents";
    public static final String ROOT_ID = "backups";
    public static final String TITLE = "Red Square test backups";

    private File directory() {
        File directory = new File(getContext().getFilesDir(), "wallet-test-documents");
        directory.mkdirs();
        return directory;
    }

    @Override public boolean onCreate() { return true; }

    @Override public Cursor queryRoots(String[] projection) {
        String[] columns = projection != null ? projection : new String[] {
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES
        };
        MatrixCursor cursor = new MatrixCursor(columns);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            Object value = null;
            switch (column) {
                case Root.COLUMN_ROOT_ID: case Root.COLUMN_DOCUMENT_ID: value = ROOT_ID; break;
                case Root.COLUMN_TITLE: value = TITLE; break;
                case Root.COLUMN_FLAGS: value = Root.FLAG_SUPPORTS_CREATE | Root.FLAG_LOCAL_ONLY; break;
                case Root.COLUMN_MIME_TYPES: value = "application/json"; break;
            }
            row.add(value);
        }
        return cursor;
    }

    @Override public Cursor queryDocument(String id, String[] projection) {
        MatrixCursor cursor = documents(projection);
        addDocument(cursor, id);
        return cursor;
    }

    @Override public Cursor queryChildDocuments(String parent, String[] projection, String sortOrder) {
        if (!ROOT_ID.equals(parent)) throw new IllegalArgumentException("Invalid test parent");
        MatrixCursor cursor = documents(projection);
        File[] files = directory().listFiles();
        if (files != null) for (File file : files) if (file.isFile()) addDocument(cursor, file.getName());
        return cursor;
    }

    @Override public String createDocument(String parent, String mimeType, String name) throws FileNotFoundException {
        if (!ROOT_ID.equals(parent) || !"application/json".equals(mimeType)) {
            throw new FileNotFoundException("Invalid test destination");
        }
        try {
            if (!document(name).createNewFile()) throw new FileNotFoundException("Test document already exists");
        } catch (IOException failure) {
            throw new FileNotFoundException("Test document could not be created");
        }
        return name;
    }

    @Override public void deleteDocument(String id) throws FileNotFoundException {
        if (!document(id).delete()) throw new FileNotFoundException("Test document unavailable");
    }

    @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal)
        throws FileNotFoundException {
        if ((mode.contains("w") && id.startsWith("write-fails-")) ||
            (mode.equals("r") && id.startsWith("read-fails-"))) {
            throw new FileNotFoundException("Controlled test document failure");
        }
        if (mode.equals("r") && id.startsWith("truncated-read-")) {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                try (ParcelFileDescriptor.AutoCloseOutputStream stream =
                    new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    stream.write('{');
                }
                return pipe[0];
            } catch (IOException failure) { throw new FileNotFoundException("Test read pipe unavailable"); }
        }
        if (mode.equals("r") && id.startsWith("stalled-read-")) {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                new Thread(() -> {
                    try { Thread.sleep(60_000); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                    finally { try { pipe[1].close(); } catch (IOException ignored) { } }
                }, "test-stalled-document").start();
                return pipe[0];
            } catch (IOException failure) { throw new FileNotFoundException("Test read pipe unavailable"); }
        }
        return ParcelFileDescriptor.open(document(id), ParcelFileDescriptor.parseMode(mode));
    }

    @Override public boolean isChildDocument(String parent, String id) {
        return ROOT_ID.equals(parent) && !ROOT_ID.equals(id) && document(id).exists();
    }

    private File document(String id) {
        if (!id.matches("[a-zA-Z0-9._-]{1,100}") || id.equals(".") || id.equals("..") || id.equals(ROOT_ID)) {
            throw new IllegalArgumentException("Invalid test document");
        }
        return new File(directory(), id);
    }

    private MatrixCursor documents(String[] projection) {
        return new MatrixCursor(projection != null ? projection : new String[] {
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS, Document.COLUMN_SIZE
        });
    }

    private void addDocument(MatrixCursor cursor, String id) {
        boolean root = ROOT_ID.equals(id);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            Object value = null;
            switch (column) {
                case Document.COLUMN_DOCUMENT_ID: value = id; break;
                case Document.COLUMN_DISPLAY_NAME: value = root ? TITLE : id; break;
                case Document.COLUMN_MIME_TYPE: value = root ? Document.MIME_TYPE_DIR : "application/json"; break;
                case Document.COLUMN_FLAGS: value = root ? Document.FLAG_DIR_SUPPORTS_CREATE
                    : Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE; break;
                case Document.COLUMN_SIZE: value = root ? 0L : document(id).length(); break;
            }
            row.add(value);
        }
    }
}
