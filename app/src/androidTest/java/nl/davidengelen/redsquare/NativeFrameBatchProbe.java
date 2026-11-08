package nl.davidengelen.redsquare;

import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Diagnostic-only standalone comparison of every original frame; no JUnit case or app state. */
public final class NativeFrameBatchProbe {
    public static void main(String[] paths) throws Exception {
        JSONArray movies = new JSONArray();
        for (String path : paths) {
            MediaExtractor extractor = new MediaExtractor();
            List<Long> times = new ArrayList<>();
            try {
                extractor.setDataSource(path);
                int track = -1;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("video/")) {
                        if (track != -1) throw new AssertionError("Expected one video track");
                        track = i;
                    }
                }
                if (track < 0) throw new AssertionError("Missing video");
                extractor.selectTrack(track);
                while (extractor.getSampleTime() >= 0) {
                    long value = extractor.getSampleTime();
                    if (!times.isEmpty() && value <= times.get(times.size() - 1)) throw new AssertionError("Reordered PTS");
                    times.add(value); extractor.advance();
                }
            } finally { extractor.release(); }
            JSONObject baseline = decode(path, times.size(), 1);
            JSONArray modes = new JSONArray().put(baseline);
            for (int size : new int[]{2, 4}) {
                JSONObject candidate = decode(path, times.size(), size);
                if (!baseline.getJSONArray("pngSha256").toString().equals(candidate.getJSONArray("pngSha256").toString()))
                    throw new AssertionError("Original PNG hashes differ for batch " + size);
                modes.put(candidate);
            }
            movies.put(new JSONObject().put("movie", new File(path).getName())
                .put("movieSha256", digest(Files.readAllBytes(new File(path).toPath())))
                .put("presentationTimeUs", new JSONArray(times)).put("modes", modes));
        }
        System.out.println("NATIVE_FRAME_BATCH_JSON=" + new JSONObject()
            .put("maxMemoryBytes", Runtime.getRuntime().maxMemory()).put("movies", movies));
    }

    private static JSONObject decode(String path, int count, int batchSize) throws Exception {
        MediaMetadataRetriever decoder = new MediaMetadataRetriever();
        JSONArray hashes = new JSONArray();
        long decodeNanos = 0, encodeNanos = 0, started = System.nanoTime();
        int width = -1, height = -1;
        try {
            decoder.setDataSource(path);
            if (count < 2 || count != Integer.parseInt(decoder.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)))
                throw new AssertionError("Frame count mismatch");
            for (int first = 0; first < count; first += batchSize) {
                long tick = System.nanoTime();
                int size = Math.min(batchSize, count - first);
                List<Bitmap> frames = batchSize == 1
                    ? java.util.Collections.singletonList(decoder.getFrameAtIndex(first))
                    : decoder.getFramesAtIndex(first, size);
                decodeNanos += System.nanoTime() - tick;
                if (frames == null || frames.size() != size) throw new AssertionError("Missing decoded frames");
                try {
                    for (Bitmap frame : frames) {
                        if (frame == null) throw new AssertionError("Null original frame");
                        if (width < 0) { width = frame.getWidth(); height = frame.getHeight(); }
                        if (frame.getWidth() != width || frame.getHeight() != height) throw new AssertionError("Changing dimensions");
                        tick = System.nanoTime();
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        if (!frame.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new AssertionError("PNG failed");
                        hashes.put(digest(output.toByteArray()));
                        encodeNanos += System.nanoTime() - tick;
                    }
                } finally { for (Bitmap frame : frames) if (frame != null) frame.recycle(); }
            }
        } finally { decoder.release(); }
        return new JSONObject().put("batchSize", batchSize).put("frameCount", count)
            .put("width", width).put("height", height).put("decodeMillis", decodeNanos / 1_000_000.0)
            .put("encodeMillis", encodeNanos / 1_000_000.0).put("totalMillis", (System.nanoTime() - started) / 1_000_000.0)
            .put("pngSha256", hashes);
    }

    private static String digest(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) text.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return text.toString();
    }
}
