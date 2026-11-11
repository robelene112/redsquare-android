package nl.davidengelen.redsquare;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

/** Instrumentation APK only. A controlled external document boundary, never packaged in the app. */
public final class FileshareTestProvider extends ContentProvider {
    private File file(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]{1,60}\\.bin")) throw new IllegalArgumentException("Invalid synthetic document");
        File directory = new File(getContext().getFilesDir(), "fileshare-test-documents");
        directory.mkdirs();
        return new File(directory, name);
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        File file = file(uri.getLastPathSegment());
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) row.add(column.equals(OpenableColumns.DISPLAY_NAME) ? file.getName() : file.length());
        return cursor;
    }
    @Override public Bundle call(String method, String name, Bundle extras) {
        if (!"prepare".equals(method)) throw new IllegalArgumentException("Unknown synthetic document action");
        File target = file(name);
        try (FileOutputStream out = new FileOutputStream(target)) { out.write(extras.getByteArray("bytes")); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
        Uri uri = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/" + name);
        getContext().grantUriPermission("nl.davidengelen.redsquare.dev", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        return new Bundle();
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(uri.getLastPathSegment()), ParcelFileDescriptor.parseMode(mode));
    }
    @Override public int delete(Uri uri, String selection, String[] args) { return file(uri.getLastPathSegment()).delete() ? 1 : 0; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
