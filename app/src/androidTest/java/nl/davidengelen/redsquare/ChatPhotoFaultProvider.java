package nl.davidengelen.redsquare;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Test APK only. A public synthetic pipe lets the native reader stall, fail and cancel without private files. */
public final class ChatPhotoFaultProvider extends ContentProvider {
    private ParcelFileDescriptor writer;
    private boolean opened;

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
        if (!"/stalled.png".equals(uri.getPath()) || !"r".equals(mode) || signal == null)
            throw new FileNotFoundException("Synthetic read-only photo only");
        final ParcelFileDescriptor[] pipe;
        try { pipe = ParcelFileDescriptor.createPipe(); }
        catch (IOException failure) { throw new FileNotFoundException(failure.getMessage()); }
        synchronized (this) {
            closeWriter(); writer = pipe[1]; opened = true;
        }
        signal.setOnCancelListener(() -> { synchronized (this) { closeWriter(); } });
        return pipe[0];
    }

    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        if ("reset".equals(method)) { closeWriter(); opened = false; }
        else if ("release".equals(method)) closeWriter(); // EOF is not a valid raster.
        else if (!"status".equals(method)) throw new IllegalArgumentException("Unknown synthetic photo action");
        Bundle result = new Bundle(); result.putBoolean("opened", opened); return result;
    }

    private void closeWriter() {
        if (writer != null) {
            try { writer.close(); } catch (IOException failure) { throw new IllegalStateException(failure); }
            finally { writer = null; }
        }
    }
}
