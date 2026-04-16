package com.multimode.kb.worker;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.multimode.kb.data.local.objectbox.DocumentSegmentEntity;
import com.multimode.kb.data.local.objectbox.KbDocumentEntity;
import com.multimode.kb.data.local.objectbox.ObjectBoxDataSource;
import com.multimode.kb.data.local.objectbox.TrackedDirectoryEntity;
import com.multimode.kb.data.remote.CloudProcessingClient;
import com.multimode.kb.data.remote.FileParserServiceImpl;
import com.multimode.kb.di.AppComponent;
import com.multimode.kb.domain.entity.DocumentStatus;
import com.multimode.kb.domain.service.FileParserService;

import java.util.List;

public class DocumentIngestionWorker extends Worker {

    private static final String TAG = "DocumentIngestion";

    public static final String KEY_DOCUMENT_ID = "document_id";

    private final AppComponent appComponent;

    public DocumentIngestionWorker(@NonNull Context context,
                                   @NonNull WorkerParameters params,
                                   AppComponent appComponent) {
        super(context, params);
        this.appComponent = appComponent;
    }

    @NonNull
    @Override
    public Result doWork() {
        long documentId = getInputData().getLong(KEY_DOCUMENT_ID, -1);
        if (documentId <= 0) {
            Log.e(TAG, "Invalid document ID");
            return Result.failure();
        }

        ObjectBoxDataSource ds = appComponent.getObjectBoxDataSource();
        KbDocumentEntity doc = ds.getDocument(documentId);
        if (doc == null) {
            Log.e(TAG, "Document not found: " + documentId);
            return Result.failure();
        }

        try {
            doc.status = DocumentStatus.INGESTING.name();
            ds.updateDocument(doc);

            Uri uri = Uri.parse(doc.filePath);
            String mimeType = doc.mimeType;

            List<FileParserService.SegmentData> segments;

            if (mimeType.startsWith("image/")) {
                segments = appComponent.getCloudProcessingClient()
                        .processImage(uri, appComponent.getOmniClient())
                        .blockingGet();
            } else if (mimeType.startsWith("audio/")) {
                String format = CloudProcessingClient.guessAudioFormat(mimeType);
                segments = appComponent.getCloudProcessingClient()
                        .processAudio(uri, format, appComponent.getOmniClient())
                        .blockingGet();
            } else if (mimeType.startsWith("video/")) {
                segments = appComponent.getCloudProcessingClient()
                        .processVideo(uri, appComponent.getOmniClient())
                        .blockingGet();
            } else {
                // PDF, DOCX, PPTX — local parsing
                // Set OmniClient on FileParserServiceImpl for OCR support on scanned PDFs
                FileParserService parser = appComponent.getFileParserService();
                if (parser instanceof FileParserServiceImpl && mimeType.contains("pdf")) {
                    ((FileParserServiceImpl) parser).setOmniClient(appComponent.getOmniClient());
                }
                segments = parser
                        .parse(uri, mimeType)
                        .blockingGet();
            }

            if (segments.isEmpty()) {
                doc.status = DocumentStatus.ERROR.name();
                doc.errorMessage = "No text content extracted";
                ds.updateDocument(doc);
                return Result.failure();
            }

            // Store segments in ObjectBox
            for (int i = 0; i < segments.size(); i++) {
                FileParserService.SegmentData seg = segments.get(i);
                ds.insertSegment(new DocumentSegmentEntity(
                        documentId, i, seg.text, seg.metadataJson));
            }

            doc.status = DocumentStatus.INGESTED.name();
            doc.totalSegments = segments.size();
            ds.updateDocument(doc);

            // Update directory indexedFiles counter
            if (doc.directoryId > 0) {
                TrackedDirectoryEntity dir = ds.getDirectory(doc.directoryId);
                if (dir != null) {
                    dir.indexedFiles++;
                    if (dir.indexedFiles >= dir.totalFiles) {
                        dir.status = "IDLE";
                    }
                    ds.updateDirectory(dir);
                }
            }

            Log.i(TAG, "Ingested " + segments.size() + " segments for doc " + documentId);
            return Result.success(new Data.Builder()
                    .putLong(KEY_DOCUMENT_ID, documentId)
                    .build());

        } catch (Exception e) {
            Log.e(TAG, "Ingestion failed for document " + documentId, e);
            doc.status = DocumentStatus.ERROR.name();
            doc.errorMessage = e.getMessage();
            ds.updateDocument(doc);
            return Result.retry();
        }
    }
}
