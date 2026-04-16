package com.multimode.kb.presentation.documentlist;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.multimode.kb.KbApplication;
import com.multimode.kb.R;
import com.multimode.kb.domain.entity.KbDocument;
import com.multimode.kb.presentation.chat.ChatActivity;
import com.multimode.kb.presentation.search.SearchActivity;
import com.multimode.kb.presentation.settings.SettingsActivity;
import com.multimode.kb.worker.DocumentIngestionWorker;
import com.multimode.kb.worker.EmbeddingGenerationWorker;

import java.util.List;

public class DocumentListActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_FILE = 1001;

    private DocumentListViewModel viewModel;
    private DocumentAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_list);

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar));

        // Setup RecyclerView
        RecyclerView recyclerView = findViewById(R.id.recycler_documents);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DocumentAdapter(this::onDocumentClick, this::onDocumentDelete);
        recyclerView.setAdapter(adapter);

        progressBar = findViewById(R.id.progress_loading);
        emptyView = findViewById(R.id.text_empty);

        // FAB for adding documents
        FloatingActionButton fab = findViewById(R.id.fab_add);
        fab.setOnClickListener(v -> openFilePicker());

        // ViewModel
        KbApplication app = (KbApplication) getApplication();
        viewModel = new ViewModelProvider(this,
                new DocumentListViewModelFactory(app.getAppComponent().getDocumentRepository())
        ).get(DocumentListViewModel.class);

        viewModel.getDocuments().observe(this, this::onDocumentsChanged);
        viewModel.getLoading().observe(this, loading -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        });
        viewModel.getError().observe(this, error -> {
            if (error != null) {
                // TODO: Show Snackbar with error
            }
        });

        viewModel.loadDocuments();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            startActivity(new Intent(this, SearchActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "image/*",
                "audio/*",
                "video/*"
        });
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_CODE_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                // Multiple files
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    addDocument(uri);
                }
            } else if (data.getData() != null) {
                addDocument(data.getData());
            }
        }
    }

    private void addDocument(Uri uri) {
        // Take persistent URI permission
        getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Get file info from URI
        String fileName = "Unknown";
        String mimeType = getContentResolver().getType(uri);
        long fileSize = 0;

        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIdx >= 0) fileName = cursor.getString(nameIdx);
                if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx);
            }
        }

        if (mimeType == null) mimeType = "application/octet-stream";

        // Create domain entity
        KbDocument doc = new KbDocument();
        doc.setFileName(fileName);
        doc.setFilePath(uri.toString());
        doc.setMimeType(mimeType);
        doc.setFileSize(fileSize);

        KbApplication app = (KbApplication) getApplication();

        // Insert into DB then enqueue workers
        app.getAppComponent().getDocumentRepository().addDocument(doc)
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(documentId -> {
                    // Enqueue ingestion worker
                    Data inputData = new Data.Builder()
                            .putLong(DocumentIngestionWorker.KEY_DOCUMENT_ID, documentId)
                            .build();

                    OneTimeWorkRequest ingestionWork = new OneTimeWorkRequest.Builder(DocumentIngestionWorker.class)
                            .setInputData(inputData)
                            .build();

                    // Embedding worker runs after ingestion succeeds
                    OneTimeWorkRequest embeddingWork = new OneTimeWorkRequest.Builder(EmbeddingGenerationWorker.class)
                            .build();

                    WorkManager.getInstance(this)
                            .beginWith(ingestionWork)
                            .then(embeddingWork)
                            .enqueue();

                    viewModel.loadDocuments();
                }, throwable -> {
                    // TODO: Show error
                });
    }

    private void onDocumentsChanged(List<KbDocument> docs) {
        adapter.setDocuments(docs);
        emptyView.setVisibility(docs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void onDocumentClick(KbDocument doc) {
        // Open chat for this document context
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_DOCUMENT_ID, doc.getId());
        startActivity(intent);
    }

    private void onDocumentDelete(KbDocument doc) {
        viewModel.deleteDocument(doc.getId());
    }
}
