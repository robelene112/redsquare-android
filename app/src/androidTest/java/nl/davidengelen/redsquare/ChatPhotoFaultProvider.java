package nl.davidengelen.redsquare;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
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
    private int assetCalls;
    private int typedCalls;
    private int openCalls;

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }

    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
        synchronized (this) { assetCalls++; }
        return new AssetFileDescriptor(openFile(uri, mode, signal), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    @Override public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeType, Bundle options, CancellationSignal signal) throws FileNotFoundException {
        synchronized (this) { typedCalls++; }
        if (!"*/*".equals(mimeType) && !"image/png".equals(mimeType)) throw new FileNotFoundException("Synthetic PNG only");
        return openAssetFile(uri, "r", signal);
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
        if (!"/stalled.png".equals(uri.getPath()) || !"r".equals(mode) || signal == null)
            throw new FileNotFoundException("Synthetic read-only photo only");
        final ParcelFileDescriptor[] pipe;
        try { pipe = ParcelFileDescriptor.createPipe(); }
        catch (IOException failure) { throw new FileNotFoundException(failure.getMessage()); }
        synchronized (this) {
            closeWriter(); writer = pipe[1]; opened = true; openCalls++;
        }
        signal.setOnCancelListener(() -> { synchronized (this) { if (writer == pipe[1]) closeWriter(); } });
        return pipe[0];
    }

    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        if ("reset".equals(method)) { closeWriter(); opened = false; assetCalls = 0; typedCalls = 0; openCalls = 0; }
        else if ("complete".equals(method)) {
            byte[] bytes = extras == null ? null : extras.getByteArray("png");
            if (bytes == null || bytes.length > 8192) throw new IllegalArgumentException("Small synthetic PNG required");
            boolean delivered = writer != null;
            if (delivered) {
                try (java.io.OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(writer)) {
                    writer = null;
                    output.write(bytes);
                } catch (IOException failure) { throw new IllegalStateException(failure); }
            }
            Bundle result = status(); result.putBoolean("delivered", delivered); return result;
        }
        else if ("release".equals(method)) closeWriter(); // EOF is not a valid raster.
        else if (!"status".equals(method)) throw new IllegalArgumentException("Unknown synthetic photo action");
        return status();
    }

    private Bundle status() {
        Bundle result = new Bundle(); result.putBoolean("opened", opened); result.putInt("openCalls", openCalls);
        result.putInt("assetCalls", assetCalls); result.putInt("typedCalls", typedCalls); return result;
    }

    private void closeWriter() {
        if (writer != null) {
            try { writer.close(); } catch (IOException failure) { throw new IllegalStateException(failure); }
            finally { writer = null; }
        }
    }
}
