package com.multimode.kb.presentation.directory;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.multimode.kb.KbApplication;
import com.multimode.kb.R;
import com.multimode.kb.domain.entity.TrackedDirectory;
import com.multimode.kb.presentation.documentlist.DocumentListActivity;
import com.multimode.kb.presentation.search.SearchActivity;
import com.multimode.kb.presentation.settings.SettingsActivity;
import com.multimode.kb.worker.DirectoryScanWorker;

import java.util.List;

public class DirectoryListActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_DIR = 2001;

    private DirectoryListViewModel viewModel;
    private DirectoryAdapter adapter;
    private ProgressBar progressBar;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_directory_list);

        setSupportActionBar(findViewById(R.id.toolbar));

        RecyclerView recyclerView = findViewById(R.id.recycler_directories);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DirectoryAdapter(this::onDirectoryClick, new DirectoryAdapter.OnDirectoryActionListener() {
            @Override
            public void onRescan(TrackedDirectory dir) {
                DirectoryListActivity.this.onRescanDirectory(dir);
            }

            @Override
            public void onDelete(TrackedDirectory dir) {
                DirectoryListActivity.this.onDeleteDirectory(dir);
            }
        });
        recyclerView.setAdapter(adapter);

        progressBar = findViewById(R.id.progress_loading);
        emptyView = findViewById(R.id.text_empty);

        FloatingActionButton fab = findViewById(R.id.fab_add_directory);
        fab.setOnClickListener(v -> openDirectoryPicker());

        KbApplication app = (KbApplication) getApplication();
        viewModel = new ViewModelProvider(this,
                new DirectoryListViewModelFactory(app.getAppComponent().getDirectoryRepository())
        ).get(DirectoryListViewModel.class);

        viewModel.getDirectories().observe(this, this::onDirectoriesChanged);
        viewModel.getLoading().observe(this, loading ->
                progressBar.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.loadDirectories();
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

    private void openDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_CODE_PICK_DIR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_DIR && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri == null) return;

            // Take persistent URI permission
            getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);

            addDirectory(treeUri);
        }
    }

    private void addDirectory(Uri treeUri) {
        KbApplication app = (KbApplication) getApplication();

        TrackedDirectory dir = new TrackedDirectory();
        dir.setTreeUri(treeUri.toString());
        // Extract display name from URI
        String lastSegment = treeUri.getLastPathSegment();
        String displayName = lastSegment != null ? lastSegment : treeUri.getPath();
        dir.setDisplayName(displayName);

        app.getAppComponent().getDirectoryRepository().addDirectory(dir)
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(directoryId -> {
                    // Enqueue full scan for this directory
                    Data inputData = new Data.Builder()
                            .putLong(DirectoryScanWorker.KEY_DIRECTORY_ID, directoryId)
                            .putString(DirectoryScanWorker.KEY_SCAN_TYPE, "full")
                            .build();

                    OneTimeWorkRequest scanWork = new OneTimeWorkRequest.Builder(DirectoryScanWorker.class)
                            .setInputData(inputData)
                            .build();

                    androidx.work.WorkManager.getInstance(this).enqueue(scanWork);
                    viewModel.loadDirectories();
                }, throwable -> {
                    // TODO: show error
                });
    }

    private void onDirectoryClick(TrackedDirectory dir) {
        Intent intent = new Intent(this, DocumentListActivity.class);
        intent.putExtra(DocumentListActivity.EXTRA_DIRECTORY_ID, dir.getId());
        startActivity(intent);
    }

    private void onRescanDirectory(TrackedDirectory dir) {
        Data inputData = new Data.Builder()
                .putLong(DirectoryScanWorker.KEY_DIRECTORY_ID, dir.getId())
                .putString(DirectoryScanWorker.KEY_SCAN_TYPE, "delta")
                .build();

        OneTimeWorkRequest scanWork = new OneTimeWorkRequest.Builder(DirectoryScanWorker.class)
                .setInputData(inputData)
                .build();

        androidx.work.WorkManager.getInstance(this).enqueue(scanWork);
        viewModel.loadDirectories();
    }

    private void onDeleteDirectory(TrackedDirectory dir) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_directory)
                .setMessage(R.string.confirm_delete_directory_message)
                .setPositiveButton(R.string.action_delete, (d, w) -> viewModel.deleteDirectory(dir.getId()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onDirectoriesChanged(List<TrackedDirectory> dirs) {
        adapter.setDirectories(dirs);
        emptyView.setVisibility(dirs.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
