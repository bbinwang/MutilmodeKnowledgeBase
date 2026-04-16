package com.multimode.kb.data.remote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Media utilities for sampling large files before sending to cloud.
 * - Image: downsample + compress
 * - Video: keyframe extraction + audio track extraction
 * - Audio: compress to smaller format
 */
public class MediaUtils {

    private static final String TAG = "MediaUtils";

    // Image sampling
    private static final int IMAGE_MAX_DIMENSION = 2048;   // max width or height
    private static final int JPEG_QUALITY = 80;             // JPEG compression quality

    // Video sampling
    private static final int FRAME_INTERVAL_SECONDS = 10;   // extract 1 frame every N seconds
    private static final int FRAME_MAX_DIMENSION = 1024;    // frame image max dimension
    private static final int FRAMES_PER_BATCH = 5;          // send N frames per Omni API call

    private final Context context;

    public MediaUtils(Context context) {
        this.context = context;
    }

    // ==================== Image ====================

    /**
     * Compress an image URI to a base64 string, downsampling if needed.
     * A 20MB image typically becomes 200-500KB.
     */
    public String compressImageToBase64(Uri uri) throws Exception {
        // Step 1: Decode bounds only (no memory allocation for pixels)
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, bounds);
        }

        int width = bounds.outWidth;
        int height = bounds.outHeight;

        // Step 2: Calculate inSampleSize to fit within IMAGE_MAX_DIMENSION
        int sampleSize = 1;
        if (width > IMAGE_MAX_DIMENSION || height > IMAGE_MAX_DIMENSION) {
            int halfWidth = width / 2;
            int halfHeight = height / 2;
            while ((halfWidth / sampleSize) >= IMAGE_MAX_DIMENSION
                    || (halfHeight / sampleSize) >= IMAGE_MAX_DIMENSION) {
                sampleSize *= 2;
            }
        }

        // Step 3: Decode with sampling
        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
        decodeOpts.inSampleSize = sampleSize;
        decodeOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Bitmap bitmap;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(is, null, decodeOpts);
        }

        if (bitmap == null) throw new Exception("Failed to decode image");

        // Step 4: Further resize if still too large
        if (bitmap.getWidth() > IMAGE_MAX_DIMENSION || bitmap.getHeight() > IMAGE_MAX_DIMENSION) {
            float scale = Math.min(
                    (float) IMAGE_MAX_DIMENSION / bitmap.getWidth(),
                    (float) IMAGE_MAX_DIMENSION / bitmap.getHeight());
            int newW = Math.round(bitmap.getWidth() * scale);
            int newH = Math.round(bitmap.getHeight() * scale);
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true);
            bitmap.recycle();
            bitmap = scaled;
        }

        // Step 5: Compress to JPEG and base64 encode
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bos);
        bitmap.recycle();

        byte[] jpegBytes = bos.toByteArray();
        Log.i(TAG, "Image compressed: " + jpegBytes.length / 1024 + " KB (sampleSize=" + sampleSize + ")");
        return Base64.encodeToString(jpegBytes, Base64.NO_WRAP);
    }

    // ==================== Video Keyframes ====================

    /**
     * Extract keyframes from a video as compressed JPEG base64 strings.
     * Samples 1 frame every FRAME_INTERVAL_SECONDS.
     *
     * @return list of [frameIndex, timestampSeconds, base64Jpeg]
     */
    public List<FrameData> extractKeyframes(Uri uri) throws Exception {
        List<FrameData> frames = new ArrayList<>();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        try {
            retriever.setDataSource(context, uri);

            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = durationStr != null ? Long.parseLong(durationStr) : 0;
            long durationSec = durationMs / 1000;

            Log.i(TAG, "Video duration: " + durationSec + "s, extracting every " + FRAME_INTERVAL_SECONDS + "s");

            for (long timeUs = 0; timeUs < durationMs * 1000; timeUs += FRAME_INTERVAL_SECONDS * 1_000_000L) {
                Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame == null) continue;

                // Downsample
                if (frame.getWidth() > FRAME_MAX_DIMENSION || frame.getHeight() > FRAME_MAX_DIMENSION) {
                    float scale = Math.min(
                            (float) FRAME_MAX_DIMENSION / frame.getWidth(),
                            (float) FRAME_MAX_DIMENSION / frame.getHeight());
                    Bitmap scaled = Bitmap.createScaledBitmap(frame,
                            Math.round(frame.getWidth() * scale),
                            Math.round(frame.getHeight() * scale), true);
                    frame.recycle();
                    frame = scaled;
                }

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bos);
                frame.recycle();

                long timestampSec = timeUs / 1_000_000L;
                String base64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                int frameIndex = frames.size();

                frames.add(new FrameData(frameIndex, timestampSec, base64));
                Log.i(TAG, "Frame " + frameIndex + " @ " + timestampSec + "s: "
                        + bos.toByteArray().length / 1024 + " KB");
            }

            Log.i(TAG, "Total keyframes extracted: " + frames.size());
        } finally {
            retriever.release();
        }

        return frames;
    }

    /**
     * Get video metadata: duration, resolution.
     */
    public VideoMetadata getVideoMetadata(Uri uri) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);

            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

            return new VideoMetadata(
                    duration != null ? Long.parseLong(duration) / 1000 : 0,
                    width != null ? Integer.parseInt(width) : 0,
                    height != null ? Integer.parseInt(height) : 0
            );
        } finally {
            retriever.release();
        }
    }

    // ==================== Audio Track ====================

    /**
     * Extract the audio track from a video file and save as a temp file.
     * Returns the temp file path, or null if no audio track found.
     */
    public File extractAudioTrack(Uri videoUri) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        File tempFile = null;

        try {
            extractor.setDataSource(context, videoUri);

            int audioTrack = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrack = i;
                    break;
                }
            }

            if (audioTrack < 0) {
                Log.i(TAG, "No audio track found in video");
                return null;
            }

            extractor.selectTrack(audioTrack);
            MediaFormat audioFormat = extractor.getTrackFormat(audioTrack);
            String mime = audioFormat.getString(MediaFormat.KEY_MIME);
            Log.i(TAG, "Audio track: " + mime);

            // Read raw audio data to temp file
            tempFile = File.createTempFile("audio_extract_", ".raw", context.getCacheDir());
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
                while (true) {
                    int inputBufferIndex = extractor.getSampleSize();
                    if (inputBufferIndex < 0) break; // EOS

                    byte[] chunk = new byte[inputBufferIndex];
                    int read = extractor.readSampleData(java.nio.ByteBuffer.wrap(chunk), 0);
                    if (read > 0) {
                        fos.write(chunk, 0, read);
                    }
                    extractor.advance();
                }
            }

            Log.i(TAG, "Audio extracted: " + tempFile.length() / 1024 + " KB");
        } finally {
            extractor.release();
        }

        return tempFile;
    }

    /**
     * Read a (small) audio file as base64.
     * Should only be used for files < 5MB.
     */
    public String readAudioAsBase64(Uri uri) throws Exception {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new Exception("Cannot open URI: " + uri);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            byte[] bytes = bos.toByteArray();
            Log.i(TAG, "Audio file: " + bytes.length / 1024 + " KB");
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        }
    }

    // ==================== Data Classes ====================

    public static class FrameData {
        public final int frameIndex;
        public final long timestampSeconds;
        public final String base64Jpeg;

        public FrameData(int frameIndex, long timestampSeconds, String base64Jpeg) {
            this.frameIndex = frameIndex;
            this.timestampSeconds = timestampSeconds;
            this.base64Jpeg = base64Jpeg;
        }
    }

    public static class VideoMetadata {
        public final long durationSeconds;
        public final int width;
        public final int height;

        public VideoMetadata(long durationSeconds, int width, int height) {
            this.durationSeconds = durationSeconds;
            this.width = width;
            this.height = height;
        }
    }
}
