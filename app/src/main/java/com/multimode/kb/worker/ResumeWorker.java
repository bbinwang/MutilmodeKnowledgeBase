package com.multimode.kb.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.multimode.kb.data.local.objectbox.KbDocumentEntity;
import com.multimode.kb.data.local.objectbox.ObjectBoxDataSource;
import com.multimode.kb.data.local.objectbox.TrackedDirectoryEntity;
import com.multimode.kb.di.AppComponent;

import java.util.ArrayList;
import java.util.List;

import androidx.work.Data;

/**
 * Worker that runs on app startup to resume any incomplete indexing work.
 */
public class ResumeWorker extends Worker {

    private static final String TAG = "ResumeWorker";

    private final AppComponent appComponent;

    public ResumeWorker(@NonNull Context context,
                        @NonNull WorkerParameters params,
                        AppComponent appComponent) {
        super(context, params);
        this.appComponent = appComponent;
    }

    @NonNull
    @Override
    public Result doWork() {
        ObjectBoxDataSource ds = appComponent.getObjectBoxDataSource();

        // Find all documents that were PENDING or INGESTING (crash during processing)
        List<KbDocumentEntity> pendingDocs = ds.getDocumentsByStatus("PENDING", "INGESTING");

        if (pendingDocs.isEmpty()) {
            Log.i(TAG, "No pending documents to resume");
            return Result.success();
        }

        Log.i(TAG, "Resuming " + pendingDocs.size() + " pending documents");

        // Reset INGESTING docs back to PENDING (they crashed mid-processing)
        List<OneTimeWorkRequest> ingestionRequests = new ArrayList<>();
        for (KbDocumentEntity doc : pendingDocs) {
            if ("INGESTING".equals(doc.status)) {
                doc.status = "PENDING";
                ds.updateDocument(doc);
            }

            Data inputData = new Data.Builder()
                    .putLong(DocumentIngestionWorker.KEY_DOCUMENT_ID, doc.id)
                    .build();
            ingestionRequests.add(new OneTimeWorkRequest.Builder(DocumentIngestionWorker.class)
                    .setInputData(inputData)
                    .build());
        }

        // Reset any directories stuck in SCANNING/INGESTING back to IDLE
        List<TrackedDirectoryEntity> stuckDirs = new ArrayList<>();
        stuckDirs.addAll(ds.getDirectoriesByStatus("SCANNING"));
        stuckDirs.addAll(ds.getDirectoriesByStatus("INGESTING"));
        for (TrackedDirectoryEntity dir : stuckDirs) {
            Log.i(TAG, "Resetting stuck directory: " + dir.displayName);
            dir.status = "IDLE";
            ds.updateDirectory(dir);
        }

        // Enqueue ingestion chain
        if (!ingestionRequests.isEmpty()) {
            OneTimeWorkRequest embeddingWork = new OneTimeWorkRequest.Builder(EmbeddingGenerationWorker.class)
                    .build();
            androidx.work.WorkManager.getInstance(getApplicationContext())
                    .beginWith(ingestionRequests)
                    .then(embeddingWork)
                    .enqueue();
        }

        return Result.success();
    }
}
