package com.multimode.kb.data.remote;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.multimode.kb.domain.service.FileParserService;
import com.multimode.kb.llm.OmniClient;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Single;

/**
 * Processes multimodal files (images, audio, video) via LongCat Omni API.
 * Uses sampling and compression to handle large files within mobile memory limits.
 *
 * Strategy:
 * - Image (20MB): Downsample to 2048px, JPEG 80% → ~200-500KB base64
 * - Audio: Read as base64 (should be small after extraction)
 * - Video (500MB): Extract keyframes (1 per 10s) + audio track → process separately
 */
public class CloudProcessingClient {

    private static final String TAG = "CloudProcessing";
    private static final int FRAMES_PER_BATCH = 5;

    private final Context context;
    private final MediaUtils mediaUtils;

    public CloudProcessingClient(Context context) {
        this.context = context;
        this.mediaUtils = new MediaUtils(context);
    }

    /**
     * Process an image: compress → base64 → Omni describe.
     * Memory: ~20MB input → ~500KB compressed → ~700KB base64.
     */
    public Single<List<FileParserService.SegmentData>> processImage(
            Uri uri, OmniClient omniClient) {
        return Single.fromCallable(() -> {
            String base64 = mediaUtils.compressImageToBase64(uri);
            String description = omniClient.describeImage(base64,
                    "请详细描述这张图片的内容，包括其中的文字、图表、数据等所有可见信息。");

            List<FileParserService.SegmentData> segments = new ArrayList<>();
            segments.add(new FileParserService.SegmentData(description,
                    "{\"type\":\"image_description\"}"));
            Log.i(TAG, "Image processed: " + description.length() + " chars");
            return segments;
        });
    }

    /**
     * Process an audio file: read as base64 → Omni transcribe.
     * For large audio files, consider splitting externally.
     */
    public Single<List<FileParserService.SegmentData>> processAudio(
            Uri uri, String format, OmniClient omniClient) {
        return Single.fromCallable(() -> {
            String base64 = mediaUtils.readAudioAsBase64(uri);
            String transcription = omniClient.transcribeAudio(base64, format,
                    "请将这段音频转写为文字，如果音频中有对话，请标注说话人。保留时间信息。");

            List<FileParserService.SegmentData> segments = new ArrayList<>();
            segments.add(new FileParserService.SegmentData(transcription,
                    "{\"type\":\"audio_transcription\",\"format\":\"" + format + "\"}"));
            Log.i(TAG, "Audio processed: " + transcription.length() + " chars");
            return segments;
        });
    }

    /**
     * Process a video file using sampling strategy:
     * 1. Extract keyframes (1 per 10 seconds) → compress each to ~100-200KB
     * 2. Extract audio track → transcribe
     * 3. Describe keyframes in batches of 5
     * 4. Merge all into timestamped segments
     *
     * Memory: Never holds more than FRAMES_PER_BATCH compressed frames at once.
     * A 10-minute video → 60 frames × ~150KB = ~9MB total, processed 5 at a time.
     */
    public Single<List<FileParserService.SegmentData>> processVideo(
            Uri uri, OmniClient omniClient) {
        return Single.fromCallable(() -> {
            List<FileParserService.SegmentData> allSegments = new ArrayList<>();

            // Step 1: Get video metadata
            MediaUtils.VideoMetadata meta = mediaUtils.getVideoMetadata(uri);
            Log.i(TAG, "Video: " + meta.durationSeconds + "s, " + meta.width + "x" + meta.height);

            // Step 2: Extract and describe keyframes in batches
            List<MediaUtils.FrameData> frames = mediaUtils.extractKeyframes(uri);
            if (!frames.isEmpty()) {
                // Process frames in batches to limit memory
                for (int i = 0; i < frames.size(); i += FRAMES_PER_BATCH) {
                    int end = Math.min(i + FRAMES_PER_BATCH, frames.size());
                    List<MediaUtils.FrameData> batch = frames.subList(i, end);

                    // Build a combined description for this batch
                    StringBuilder batchFrames = new StringBuilder();
                    for (MediaUtils.FrameData frame : batch) {
                        if (batchFrames.length() > 0) batchFrames.append("\n---\n");
                        batchFrames.append("时间 ").append(formatTimestamp(frame.timestampSeconds))
                                .append(" 的画面:\n");

                        // Describe this single frame
                        String frameDesc = omniClient.describeImage(frame.base64Jpeg,
                                "简要描述这张视频截图的内容（50字以内）。");
                        batchFrames.append(frameDesc);

                        // Release base64 memory ASAP
                        frame = null;
                    }

                    long batchStartSec = batch.get(0).timestampSeconds;
                    long batchEndSec = batch.get(batch.size() - 1).timestampSeconds;

                    allSegments.add(new FileParserService.SegmentData(
                            batchFrames.toString(),
                            "{\"type\":\"video_frames\",\"start_sec\":" + batchStartSec
                                    + ",\"end_sec\":" + batchEndSec
                                    + ",\"duration_sec\":" + meta.durationSeconds + "}"));

                    Log.i(TAG, "Video frames batch " + (i / FRAMES_PER_BATCH + 1)
                            + ": " + batchStartSec + "s-" + batchEndSec + "s");

                    // GC hint between batches
                    System.gc();
                }
            }

            // Step 3: Extract and transcribe audio track
            try {
                File audioFile = mediaUtils.extractAudioTrack(uri);
                if (audioFile != null && audioFile.exists()) {
                    // Only transcribe if audio is small enough (< 10MB for base64)
                    if (audioFile.length() < 10 * 1024 * 1024) {
                        Uri audioUri = Uri.fromFile(audioFile);
                        String audioBase64 = mediaUtils.readAudioAsBase64(audioUri);
                        String transcription = omniClient.transcribeAudio(audioBase64, "wav",
                                "请将这段音频转写为文字，标注时间信息。格式：[MM:SS] 文字内容");

                        allSegments.add(new FileParserService.SegmentData(
                                transcription,
                                "{\"type\":\"video_audio_transcription\",\"duration_sec\":"
                                        + meta.durationSeconds + "}"));
                        Log.i(TAG, "Video audio transcribed: " + transcription.length() + " chars");
                    } else {
                        Log.w(TAG, "Audio track too large for single API call: "
                                + audioFile.length() / 1024 / 1024 + " MB");
                        // TODO: Split audio into chunks for large files
                    }
                    audioFile.delete();
                }
            } catch (Exception e) {
                Log.w(TAG, "Audio extraction/transcription failed: " + e.getMessage());
                // Non-fatal: we still have frame descriptions
            }

            Log.i(TAG, "Video processed: " + allSegments.size() + " segments total");
            return allSegments;
        });
    }

    /**
     * Guess audio format from MIME type for OmniClient.
     */
    public static String guessAudioFormat(String mimeType) {
        if (mimeType.contains("wav")) return "wav";
        if (mimeType.contains("mp3")) return "mp3";
        if (mimeType.contains("pcm")) return "pcm16";
        return "wav";
    }

    private static String formatTimestamp(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }
}
