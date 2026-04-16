package com.multimode.kb.presentation.viewer;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.multimode.kb.R;

import org.json.JSONObject;

import java.io.InputStream;

/**
 * Document viewer that supports jumping to specific locations:
 * - PDF: Renders at target page using PdfRenderer
 * - Video: Plays at target timestamp using VideoView
 * - Image: Displays the image
 * - Audio: Plays with seek position using MediaPlayer
 * - PPTX/DOCX/other: Falls back to external viewer via ACTION_VIEW
 */
public class DocumentViewerActivity extends AppCompatActivity {

    private static final String TAG = "DocViewer";

    // Intent extras
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_MIME_TYPE = "mime_type";
    public static final String EXTRA_DOCUMENT_NAME = "document_name";
    public static final String EXTRA_METADATA_JSON = "metadata_json";
    public static final String EXTRA_SOURCE_LOCATION = "source_location";

    // Views
    private ImageView imagePdfPage;
    private ImageView imageViewer;
    private VideoView videoView;
    private LinearLayout audioControls;
    private TextView textFallbackMessage;
    private ProgressBar progressLoading;
    private LinearLayout pdfNavigation;
    private TextView textPageIndicator;

    // Audio controls
    private SeekBar seekBarAudio;
    private com.google.android.material.button.MaterialButton buttonPlayPauseAudio;
    private TextView textAudioPosition;
    private MediaPlayer audioPlayer;
    private Handler audioUpdateHandler;
    private boolean audioSeeking = false;

    // PDF state
    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPage;
    private ParcelFileDescriptor pdfPfd;
    private int currentPdfPage = 0;
    private int totalPdfPages = 0;

    // Intent data
    private Uri sourceUri;
    private String mimeType;
    private String metadataJson;
    private String sourceLocation;
    private int targetPage = -1;
    private long targetTimestampMs = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_viewer);

        // Bind views
        imagePdfPage = findViewById(R.id.image_pdf_page);
        imageViewer = findViewById(R.id.image_viewer);
        videoView = findViewById(R.id.video_view);
        audioControls = findViewById(R.id.audio_controls);
        textFallbackMessage = findViewById(R.id.text_fallback_message);
        progressLoading = findViewById(R.id.progress_loading);
        pdfNavigation = findViewById(R.id.pdf_navigation);
        textPageIndicator = findViewById(R.id.text_page_indicator);

        seekBarAudio = findViewById(R.id.seek_bar_audio);
        buttonPlayPauseAudio = findViewById(R.id.button_play_pause_audio);
        textAudioPosition = findViewById(R.id.text_audio_position);

        // Read intent extras
        readIntentExtras();

        if (sourceUri == null) {
            Toast.makeText(this, R.string.viewer_error_no_file, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Setup toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        String docName = getIntent().getStringExtra(EXTRA_DOCUMENT_NAME);
        toolbar.setTitle(docName != null ? docName : "文档查看");
        toolbar.setNavigationOnClickListener(v -> finish());

        // Parse location from metadata
        parseLocationFromMetadata();

        // Dispatch by MIME type
        setupForMimeType();
    }

    private void readIntentExtras() {
        String uriStr = getIntent().getStringExtra(EXTRA_URI);
        sourceUri = uriStr != null ? Uri.parse(uriStr) : null;
        mimeType = getIntent().getStringExtra(EXTRA_MIME_TYPE);
        metadataJson = getIntent().getStringExtra(EXTRA_METADATA_JSON);
        sourceLocation = getIntent().getStringExtra(EXTRA_SOURCE_LOCATION);
    }

    private void parseLocationFromMetadata() {
        // Try metadataJson first (structured)
        targetPage = extractPageFromJson(metadataJson);
        targetTimestampMs = extractTimestampMsFromJson(metadataJson);

        // Fall back to sourceLocation string parsing
        if (targetPage < 0 && sourceLocation != null) {
            targetPage = parsePageFromSourceLocation(sourceLocation);
        }
        if (targetTimestampMs < 0 && sourceLocation != null) {
            targetTimestampMs = parseTimestampToMs(sourceLocation);
        }
    }

    private void setupForMimeType() {
        if (mimeType == null) {
            fallbackToExternalViewer();
            return;
        }

        if (mimeType.contains("pdf")) {
            setupPdfViewer();
        } else if (mimeType.startsWith("image/")) {
            setupImageViewer();
        } else if (mimeType.startsWith("video/")) {
            setupVideoPlayer();
        } else if (mimeType.startsWith("audio/")) {
            setupAudioPlayer();
        } else {
            fallbackToExternalViewer();
        }
    }

    // ---- PDF Viewer ----

    private void setupPdfViewer() {
        showLoading(true);

        try {
            pdfPfd = getContentResolver().openFileDescriptor(sourceUri, "r");
            if (pdfPfd == null) {
                showError("无法打开文件");
                return;
            }

            pdfRenderer = new PdfRenderer(pdfPfd);
            totalPdfPages = pdfRenderer.getPageCount();

            // Target page: convert 1-based to 0-based
            int pageIdx = targetPage > 0 ? targetPage - 1 : 0;
            if (pageIdx >= totalPdfPages) pageIdx = 0;

            renderPdfPage(pageIdx);
            showView(imagePdfPage);
            pdfNavigation.setVisibility(View.VISIBLE);
            showLoading(false);

            // Page navigation buttons
            findViewById(R.id.button_prev_page).setOnClickListener(v -> navigatePdfPage(-1));
            findViewById(R.id.button_next_page).setOnClickListener(v -> navigatePdfPage(1));

        } catch (SecurityException e) {
            showError(getString(R.string.viewer_error_permission));
        } catch (Exception e) {
            showError("PDF加载失败: " + e.getMessage());
        }
    }

    private void renderPdfPage(int pageIndex) {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= totalPdfPages) return;

        // Close previous page
        if (currentPage != null) {
            currentPage.close();
        }

        currentPage = pdfRenderer.openPage(pageIndex);
        currentPdfPage = pageIndex;

        // Calculate bitmap size: fit to screen, max 2048px
        int width = currentPage.getWidth();
        int height = currentPage.getHeight();
        float scale = Math.min(2048f / width, 2048f / height);
        if (scale > 2f) scale = 2f; // Don't upscale too much
        int bmpWidth = (int) (width * scale);
        int bmpHeight = (int) (height * scale);

        Bitmap bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

        // Recycle old bitmap
        if (imagePdfPage.getDrawable() != null
                && imagePdfPage.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
            Bitmap old = ((android.graphics.drawable.BitmapDrawable) imagePdfPage.getDrawable()).getBitmap();
            if (old != null && old != bitmap) old.recycle();
        }

        imagePdfPage.setImageBitmap(bitmap);
        updatePageIndicator();
    }

    private void navigatePdfPage(int delta) {
        int newPage = currentPdfPage + delta;
        if (newPage >= 0 && newPage < totalPdfPages) {
            renderPdfPage(newPage);
        }
    }

    private void updatePageIndicator() {
        textPageIndicator.setText(getString(R.string.viewer_page_indicator,
                currentPdfPage + 1, totalPdfPages));
    }

    // ---- Image Viewer ----

    private void setupImageViewer() {
        showLoading(true);
        new Thread(() -> {
            try {
                // Decode with sampling for large images
                android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                InputStream is = getContentResolver().openInputStream(sourceUri);
                android.graphics.BitmapFactory.decodeStream(is, null, opts);
                if (is != null) is.close();

                int inSampleSize = 1;
                if (opts.outWidth > 2048 || opts.outHeight > 2048) {
                    inSampleSize = Math.max(opts.outWidth, opts.outHeight) / 2048;
                }
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = inSampleSize;

                is = getContentResolver().openInputStream(sourceUri);
                Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is, null, opts);
                if (is != null) is.close();

                runOnUiThread(() -> {
                    if (bitmap != null) {
                        imageViewer.setImageBitmap(bitmap);
                        showView(imageViewer);
                    } else {
                        showError("无法加载图片");
                    }
                    showLoading(false);
                });
            } catch (SecurityException e) {
                runOnUiThread(() -> {
                    showError(getString(R.string.viewer_error_permission));
                    showLoading(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showError("图片加载失败: " + e.getMessage());
                    showLoading(false);
                });
            }
        }).start();
    }

    // ---- Video Player ----

    private void setupVideoPlayer() {
        showView(videoView);

        MediaController controller = new MediaController(this);
        controller.setAnchorView(videoView);
        videoView.setMediaController(controller);

        videoView.setVideoURI(sourceUri);

        videoView.setOnPreparedListener(mp -> {
            if (targetTimestampMs > 0) {
                mp.seekTo((int) targetTimestampMs);
            }
            videoView.start();
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            showError("视频播放失败，尝试用其他应用打开");
            fallbackToExternalViewer();
            return true;
        });
    }

    // ---- Audio Player ----

    private void setupAudioPlayer() {
        showView(audioControls);
        audioUpdateHandler = new Handler(Looper.getMainLooper());

        try {
            audioPlayer = new MediaPlayer();
            audioPlayer.setDataSource(this, sourceUri);

            audioPlayer.setOnPreparedListener(mp -> {
                seekBarAudio.setMax(mp.getDuration());
                if (targetTimestampMs > 0) {
                    mp.seekTo((int) targetTimestampMs);
                }
                mp.start();
                buttonPlayPauseAudio.setText("暂停");
                startAudioProgressUpdate();
            });

            audioPlayer.setOnCompletionListener(mp -> {
                buttonPlayPauseAudio.setText("播放");
                stopAudioProgressUpdate();
            });

            audioPlayer.setOnErrorListener((mp, what, extra) -> {
                showError("音频播放失败: " + what);
                return true;
            });

            buttonPlayPauseAudio.setOnClickListener(v -> {
                if (audioPlayer.isPlaying()) {
                    audioPlayer.pause();
                    buttonPlayPauseAudio.setText("播放");
                } else {
                    audioPlayer.start();
                    buttonPlayPauseAudio.setText("暂停");
                    startAudioProgressUpdate();
                }
            });

            seekBarAudio.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && audioPlayer != null) {
                        audioPlayer.seekTo(progress);
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) { audioSeeking = true; }
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) { audioSeeking = false; }
            });

            audioPlayer.prepareAsync();

        } catch (SecurityException e) {
            showError(getString(R.string.viewer_error_permission));
        } catch (Exception e) {
            showError("音频加载失败: " + e.getMessage());
        }
    }

    private void startAudioProgressUpdate() {
        audioUpdateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (audioPlayer != null && audioPlayer.isPlaying()) {
                    if (!audioSeeking) {
                        seekBarAudio.setProgress(audioPlayer.getCurrentPosition());
                    }
                    textAudioPosition.setText(formatDuration(audioPlayer.getCurrentPosition())
                            + " / " + formatDuration(audioPlayer.getDuration()));
                    audioUpdateHandler.postDelayed(this, 200);
                }
            }
        }, 200);
    }

    private void stopAudioProgressUpdate() {
        if (audioUpdateHandler != null) {
            audioUpdateHandler.removeCallbacksAndMessages(null);
        }
    }

    // ---- Fallback ----

    private void fallbackToExternalViewer() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(sourceUri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
            finish(); // Close our activity since we handed off to external app
        } catch (Exception e) {
            showError(getString(R.string.viewer_error_no_app));
        }
    }

    // ---- Helpers ----

    private void showLoading(boolean show) {
        progressLoading.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showView(@NonNull View view) {
        imagePdfPage.setVisibility(View.GONE);
        imageViewer.setVisibility(View.GONE);
        videoView.setVisibility(View.GONE);
        audioControls.setVisibility(View.GONE);
        textFallbackMessage.setVisibility(View.GONE);
        pdfNavigation.setVisibility(View.GONE);

        view.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        showLoading(false);
        textFallbackMessage.setText(message);
        showView(textFallbackMessage);
    }

    private String formatDuration(int ms) {
        int seconds = ms / 1000;
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    // ---- JSON/Location Parsers ----

    /**
     * Extract 1-based page number from metadataJson.
     */
    public static int extractPageFromJson(String json) {
        if (json == null) return -1;
        try {
            JSONObject obj = new JSONObject(json);
            if (obj.has("page")) return obj.getInt("page");
            if (obj.has("slide")) return obj.getInt("slide");
        } catch (Exception ignored) {}
        return -1;
    }

    /**
     * Extract timestamp in milliseconds from metadataJson.
     */
    public static long extractTimestampMsFromJson(String json) {
        if (json == null) return -1;
        try {
            JSONObject obj = new JSONObject(json);
            if (obj.has("start_sec")) return obj.getLong("start_sec") * 1000;
            if (obj.has("timestamp")) return parseTimestampToMs(obj.getString("timestamp"));
        } catch (Exception ignored) {}
        return -1;
    }

    /**
     * Parse "MM:SS" or "HH:MM:SS" to milliseconds.
     */
    public static long parseTimestampToMs(String timestamp) {
        if (timestamp == null) return -1;
        try {
            String[] parts = timestamp.trim().split(":");
            if (parts.length == 2) {
                long m = Long.parseLong(parts[0]);
                long s = Long.parseLong(parts[1]);
                return (m * 60 + s) * 1000;
            } else if (parts.length == 3) {
                long h = Long.parseLong(parts[0]);
                long m = Long.parseLong(parts[1]);
                long s = Long.parseLong(parts[2]);
                return (h * 3600 + m * 60 + s) * 1000;
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    /**
     * Parse page number from sourceLocation strings like "Page 5" or "第3页".
     */
    public static int parsePageFromSourceLocation(String location) {
        if (location == null) return -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)page\\s*(\\d+)")
                .matcher(location);
        if (m.find()) return Integer.parseInt(m.group(1));
        m = java.util.regex.Pattern.compile("第\\s*(\\d+)\\s*页").matcher(location);
        if (m.find()) return Integer.parseInt(m.group(1));
        return -1;
    }

    // ---- Lifecycle ----

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
        if (audioPlayer != null && audioPlayer.isPlaying()) {
            audioPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // PDF cleanup
        if (currentPage != null) currentPage.close();
        if (pdfRenderer != null) pdfRenderer.close();
        if (pdfPfd != null) {
            try { pdfPfd.close(); } catch (Exception ignored) {}
        }
        // Recycle PDF bitmap
        if (imagePdfPage.getDrawable() != null
                && imagePdfPage.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
            Bitmap bmp = ((android.graphics.drawable.BitmapDrawable) imagePdfPage.getDrawable()).getBitmap();
            if (bmp != null) bmp.recycle();
        }
        // Audio cleanup
        stopAudioProgressUpdate();
        if (audioPlayer != null) {
            audioPlayer.release();
            audioPlayer = null;
        }
    }
}
