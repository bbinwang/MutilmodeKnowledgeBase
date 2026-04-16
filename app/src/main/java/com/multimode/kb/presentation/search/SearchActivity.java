package com.multimode.kb.presentation.search;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.multimode.kb.KbApplication;
import com.multimode.kb.R;
import com.multimode.kb.domain.entity.SearchResult;

import java.util.List;

public class SearchActivity extends AppCompatActivity {

    public static final String EXTRA_QUERY = "query";

    private SearchViewModel viewModel;
    private EditText editSearch;
    private RecyclerView recyclerResults;
    private SearchAdapter adapter;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        editSearch = findViewById(R.id.edit_search);
        recyclerResults = findViewById(R.id.recycler_results);
        progressBar = findViewById(R.id.progress_search);
        recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchAdapter();
        recyclerResults.setAdapter(adapter);

        KbApplication app = (KbApplication) getApplication();
        viewModel = new ViewModelProvider(this,
                new SearchViewModelFactory(app.getAppComponent().getSearchRepository())
        ).get(SearchViewModel.class);

        viewModel.getResults().observe(this, this::onResultsChanged);
        viewModel.getLoading().observe(this, loading ->
                progressBar.setVisibility(loading ? View.VISIBLE : View.GONE));
        viewModel.getError().observe(this, error -> {
            // TODO: Snackbar
        });

        findViewById(R.id.button_search).setOnClickListener(v ->
                performSearch());

        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            performSearch();
            return true;
        });

        String query = getIntent().getStringExtra(EXTRA_QUERY);
        if (query != null) {
            editSearch.setText(query);
            performSearch();
        }
    }

    private void performSearch() {
        String query = editSearch.getText().toString().trim();
        if (!query.isEmpty()) {
            viewModel.search(query);
        }
    }

    private void onResultsChanged(List<SearchResult> results) {
        adapter.setResults(results);
    }
}
