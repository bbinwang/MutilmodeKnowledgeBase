package com.multimode.kb.presentation.documentlist;

import android.content.Intent;
import android.os.Bundle;
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

import com.multimode.kb.KbApplication;
import com.multimode.kb.R;
import com.multimode.kb.domain.entity.KbDocument;
import com.multimode.kb.presentation.chat.ChatActivity;
import com.multimode.kb.presentation.search.SearchActivity;
import com.multimode.kb.presentation.settings.SettingsActivity;

import java.util.List;

public class DocumentListActivity extends AppCompatActivity {

    public static final String EXTRA_DIRECTORY_ID = "directory_id";

    private DocumentListViewModel viewModel;
    private DocumentAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;
    private long directoryId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_list);

        setSupportActionBar(findViewById(R.id.toolbar));

        directoryId = getIntent().getLongExtra(EXTRA_DIRECTORY_ID, 0);

        RecyclerView recyclerView = findViewById(R.id.recycler_documents);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DocumentAdapter(this::onDocumentClick, this::onDocumentDelete);
        recyclerView.setAdapter(adapter);

        progressBar = findViewById(R.id.progress_loading);
        emptyView = findViewById(R.id.text_empty);

        // Remove FAB - directories manage files now
        findViewById(R.id.fab_add).setVisibility(View.GONE);

        KbApplication app = (KbApplication) getApplication();
        viewModel = new ViewModelProvider(this,
                new DocumentListViewModelFactory(app.getAppComponent().getDocumentRepository())
        ).get(DocumentListViewModel.class);

        viewModel.getDocuments().observe(this, this::onDocumentsChanged);
        viewModel.getLoading().observe(this, loading ->
                progressBar.setVisibility(loading ? View.VISIBLE : View.GONE));

        if (directoryId > 0) {
            viewModel.loadDocumentsByDirectory(directoryId);
        } else {
            viewModel.loadDocuments();
        }
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

    private void onDocumentsChanged(List<KbDocument> docs) {
        adapter.setDocuments(docs);
        emptyView.setVisibility(docs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void onDocumentClick(KbDocument doc) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_DOCUMENT_ID, doc.getId());
        startActivity(intent);
    }

    private void onDocumentDelete(KbDocument doc) {
        viewModel.deleteDocument(doc.getId());
    }
}
