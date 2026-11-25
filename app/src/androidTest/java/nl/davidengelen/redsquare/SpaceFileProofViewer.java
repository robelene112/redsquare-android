package nl.davidengelen.redsquare;

import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Objects;

/** Standalone test APK process. Only Android and Java classes are available here. */
public final class SpaceFileProofViewer extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!"ranchu".equals(Build.HARDWARE) && !"goldfish".equals(Build.HARDWARE)) {
            finish();
            return;
        }
        LinearLayout views = new LinearLayout(this);
        views.setOrientation(LinearLayout.VERTICAL);
        views.setPadding(24, 32, 24, 24);
        TextView title = new TextView(this);
        title.setText("Reading shared file");
        TextView outcome = new TextView(this);
        outcome.setTextIsSelectable(true);
        views.addView(title);
        views.addView(outcome);
        setContentView(views);
        new Thread(() -> {
            try {
                String digest = readAndVerify(Objects.requireNonNull(getIntent().getData()));
                runOnUiThread(() -> {
                    title.setText("File bytes verified");
                    outcome.setText(digest);
                });
            } catch (Exception failure) {
                android.util.Log.e("SpaceFileProofViewer", "Could not read the actual URI grant", failure);
                runOnUiThread(() -> {
                    title.setText("Could not read shared file");
                    outcome.setText(failure.getClass().getSimpleName());
                });
            }
        }, "Owned file viewer").start();
    }

    private String readAndVerify(Uri uri) throws Exception {
        ParcelFileDescriptor handle = Objects.requireNonNull(getContentResolver().openFileDescriptor(uri, "r"));
        try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(handle)) {
            input.getChannel().position(12);
            int jumped = input.read();
            input.getChannel().position(0);
            MessageDigest hasher = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count = input.read(buffer);
            if (jumped < 0 || count <= 12 || (buffer[12] & 255) != jumped) {
                throw new IOException("Seeking did not return the expected byte");
            }
            do {
                hasher.update(buffer, 0, count);
                count = input.read(buffer);
            } while (count != -1);
            StringBuilder result = new StringBuilder(64);
            for (byte value : hasher.digest()) {
                result.append(String.format(Locale.ROOT, "%02x", value & 255));
            }
            return result.toString();
        }
    }
}
