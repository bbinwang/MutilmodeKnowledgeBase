package com.multimode.kb.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.multimode.kb.data.local.objectbox.DocumentSegmentEntity;
import com.multimode.kb.data.local.objectbox.KbDocumentEntity;
import com.multimode.kb.data.local.objectbox.ObjectBoxDataSource;
import com.multimode.kb.di.AppComponent;
import com.multimode.kb.llm.EmbeddingClient;

import java.util.ArrayList;
import java.util.List;

public class EmbeddingGenerationWorker extends Worker {

    private static final String TAG = "EmbeddingGen";
    private static final int BATCH_SIZE = 20;

    private final AppComponent appComponent;

    public EmbeddingGenerationWorker(@NonNull Context context,
                                     @NonNull WorkerParameters params,
                                     AppComponent appComponent) {
        super(context, params);
        this.appComponent = appComponent;
    }

    @NonNull
    @Override
    public Result doWork() {
        ObjectBoxDataSource ds = appComponent.getObjectBoxDataSource();

        List<DocumentSegmentEntity> segments = ds.getSegmentsWithoutEmbedding();
        if (segments.isEmpty()) {
            Log.i(TAG, "No segments pending embedding");
            return Result.success();
        }

        Log.i(TAG, "Generating embeddings for " + segments.size() + " segments");

        try {
            EmbeddingClient embeddingClient = appComponent.getEmbeddingClient();

            for (int i = 0; i < segments.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, segments.size());
                List<DocumentSegmentEntity> batch = segments.subList(i, end);

                // Collect texts
                List<String> texts = new ArrayList<>();
                for (DocumentSegmentEntity seg : batch) {
                    texts.add(seg.text);
                }

                // Generate embeddings
                List<float[]> embeddings = embeddingClient.embedBatch(texts);

                // Update segments with embeddings
                for (int j = 0; j < batch.size(); j++) {
                    if (j < embeddings.size()) {
                        batch.get(j).embedding = embeddings.get(j);
                    }
                }
                ds.updateSegments(new ArrayList<>(batch));

                // Index in FTS5
                for (DocumentSegmentEntity seg : batch) {
                    KbDocumentEntity doc = ds.getDocument(seg.documentId);
                    String docName = doc != null ? doc.fileName : "";
                    String sourceLoc = extractSourceLocation(seg.metadataJson);
                    appComponent.getFtsDataSource().insert(
                            seg.id, seg.documentId, docName, sourceLoc, seg.text);
                }

                Log.i(TAG, "Embedded batch " + (i / BATCH_SIZE + 1));
            }

            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Embedding generation failed", e);
            return Result.retry();
        }
    }

    private String extractSourceLocation(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) return "";
        try {
            // Simple extraction: find "page":N
            if (metadataJson.contains("\"page\"")) {
                int idx = metadataJson.indexOf("\"page\":") + 7;
                int end = idx;
                while (end < metadataJson.length() && Character.isDigit(metadataJson.charAt(end))) {
                    end++;
                }
                return "第 " + metadataJson.substring(idx, end) + " 页";
            }
            if (metadataJson.contains("\"chunk\"")) {
                int idx = metadataJson.indexOf("\"chunk\":") + 8;
                int end = idx;
                while (end < metadataJson.length() && Character.isDigit(metadataJson.charAt(end))) {
                    end++;
                }
                return "片段 " + metadataJson.substring(idx, end);
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return "";
    }
}
