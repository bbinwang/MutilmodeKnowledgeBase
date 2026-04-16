package com.multimode.kb.worker;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.multimode.kb.data.local.objectbox.KbDocumentEntity;
import com.multimode.kb.data.local.objectbox.ObjectBoxDataSource;
import com.multimode.kb.data.local.objectbox.TrackedDirectoryEntity;
import com.multimode.kb.data.scanner.DirectoryScanner;
import com.multimode.kb.di.AppComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Worker that scans a tracked directory for files and enqueues ingestion workers.
 */
public class DirectoryScanWorker extends Worker {

    private static final String TAG = "DirScanWorker";

    public static final String KEY_DIRECTORY_ID = "directory_id";
    public static final String KEY_SCAN_TYPE = "scan_type"; // "full" or "delta"

    private final AppComponent appComponent;

    public DirectoryScanWorker(@NonNull Context context,
                               @NonNull WorkerParameters params,
                               AppComponent appComponent) {
        super(context, params);
        this.appComponent = appComponent;
    }

    @NonNull
    @Override
    public Result doWork() {
        long directoryId = getInputData().getLong(KEY_DIRECTORY_ID, -1);
        String scanType = getInputData().getString(KEY_SCAN_TYPE);
        if (directoryId <= 0) {
            Log.e(TAG, "Invalid directory ID");
            return Result.failure();
        }

        ObjectBoxDataSource ds = appComponent.getObjectBoxDataSource();
        TrackedDirectoryEntity dir = ds.getDirectory(directoryId);
        if (dir == null) {
            Log.e(TAG, "Directory not found: " + directoryId);
            return Result.failure();
        }

        try {
            // Set directory status to SCANNING
            dir.status = "SCANNING";
            ds.updateDirectory(dir);

            Uri treeUri = Uri.parse(dir.treeUri);
            DirectoryScanner scanner = new DirectoryScanner(getApplicationContext(), ds);

            DirectoryScanner.ScanResult result;
            if ("delta".equals(scanType)) {
                result = scanner.scanDelta(treeUri, directoryId);
            } else {
                result = scanner.scanFull(treeUri, directoryId);
            }

            // Handle deleted files
            for (Long docId : result.deletedDocumentIds) {
                ds.deleteDocument(docId);
                Log.i(TAG, "Deleted document: " + docId);
            }

            // Handle modified files: delete old segments, reset status
            for (DirectoryScanner.ModifiedFile mod : result.modifiedFiles) {
                ds.deleteDocument(mod.existingDocumentId);
                Log.i(TAG, "Removed modified document for re-indexing: " + mod.existingDocumentId);
            }

            // Collect all files that need ingestion (new + modified)
            List<KbDocumentEntity> newDocs = new ArrayList<>();

            for (DirectoryScanner.FileInfo file : result.newFiles) {
                KbDocumentEntity doc = new KbDocumentEntity(
                        file.fileName, file.uriString, file.mimeType, file.fileSize);
                doc.directoryId = directoryId;
                doc.fileLastModified = file.lastModified;
                doc.relativePath = file.relativePath;
                newDocs.add(doc);
            }

            for (DirectoryScanner.ModifiedFile mod : result.modifiedFiles) {
                KbDocumentEntity doc = new KbDocumentEntity(
                        mod.fileInfo.fileName, mod.fileInfo.uriString,
                        mod.fileInfo.mimeType, mod.fileInfo.fileSize);
                doc.directoryId = directoryId;
                doc.fileLastModified = mod.fileInfo.lastModified;
                doc.relativePath = mod.fileInfo.relativePath;
                newDocs.add(doc);
            }

            // Insert all new documents and collect their IDs
            List<Long> docIds = new ArrayList<>();
            for (KbDocumentEntity doc : newDocs) {
                long id = ds.insertDocument(doc);
                docIds.add(id);
            }

            // Update directory counters
            dir.totalFiles = result.unchangedCount + result.newFiles.size() + result.modifiedFiles.size();
            dir.lastScannedAt = System.currentTimeMillis();

            if (docIds.isEmpty()) {
                dir.status = "IDLE";
                dir.indexedFiles = dir.totalFiles;
                ds.updateDirectory(dir);
                Log.i(TAG, "No new files to ingest");
                return Result.success();
            }

            // Set directory status to INGESTING
            dir.status = "INGESTING";
            ds.updateDirectory(dir);

            // Enqueue ingestion workers for each document
            List<OneTimeWorkRequest> ingestionWorks = new ArrayList<>();
            for (Long docId : docIds) {
                Data inputData = new Data.Builder()
                        .putLong(DocumentIngestionWorker.KEY_DOCUMENT_ID, docId)
                        .build();

                OneTimeWorkRequest ingestionWork = new OneTimeWorkRequest.Builder(DocumentIngestionWorker.class)
                        .setInputData(inputData)
                        .addTag("dir_" + directoryId)
                        .build();
                ingestionWorks.add(ingestionWork);
            }

            // Chain: all ingestions -> embedding generation
            OneTimeWorkRequest embeddingWork = new OneTimeWorkRequest.Builder(EmbeddingGenerationWorker.class)
                    .addTag("dir_" + directoryId)
                    .build();

            WorkContinuation chain = getWorkManager()
                    .beginWith(ingestionWorks)
                    .then(embeddingWork);
            chain.enqueue();

            Log.i(TAG, "Enqueued " + docIds.size() + " ingestion workers for directory " + directoryId);
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Directory scan failed for " + directoryId, e);
            dir.status = "ERROR";
            ds.updateDirectory(dir);
            return Result.retry();
        }
    }

    private androidx.work.WorkManager getWorkManager() {
        return androidx.work.WorkManager.getInstance(getApplicationContext());
    }
}
