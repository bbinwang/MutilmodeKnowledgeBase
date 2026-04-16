package com.multimode.kb.presentation.chat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.multimode.kb.KbApplication;
import com.multimode.kb.R;
import com.multimode.kb.domain.entity.SearchResult;
import com.multimode.kb.presentation.viewer.DocumentViewerActivity;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_DOCUMENT_ID = "document_id";

    private ChatViewModel viewModel;
    private RecyclerView recyclerChat;
    private EditText editMessage;
    private MaterialButton buttonSend;
    private ProgressBar progressBar;
    private ChatAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        recyclerChat = findViewById(R.id.recycler_chat);
        editMessage = findViewById(R.id.edit_message);
        buttonSend = findViewById(R.id.button_send);
        progressBar = findViewById(R.id.progress_chat);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerChat.setLayoutManager(layoutManager);

        adapter = new ChatAdapter();
        recyclerChat.setAdapter(adapter);

        KbApplication app = (KbApplication) getApplication();
        viewModel = new ViewModelProvider(this,
                new ChatViewModelFactory(
                        app.getAppComponent().getSearchRepository(),
                        app.getAppComponent().getRagService()
                )
        ).get(ChatViewModel.class);

        // Scope search to specific document if provided
        long documentId = getIntent().getLongExtra(EXTRA_DOCUMENT_ID, -1);
        if (documentId > 0) {
            viewModel.setScopeDocumentId(documentId);
        }

        viewModel.getMessages().observe(this, messages -> {
            adapter.setMessages(messages);
            if (!messages.isEmpty()) {
                recyclerChat.smoothScrollToPosition(messages.size() - 1);
            }
        });

        viewModel.getLoading().observe(this, loading -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            buttonSend.setEnabled(!loading);
        });

        buttonSend.setOnClickListener(v -> sendMessage());
        editMessage.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });

        // Source click listener — open document at specific location
        adapter.setSourceClickListener(source -> {
            openSourceDocument(source);
        });
    }

    private void sendMessage() {
        String text = editMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        editMessage.setText("");
        viewModel.sendMessage(text);
    }

    /**
     * Open a source document in the built-in DocumentViewerActivity,
     * which supports jumping to specific page/timestamp locations.
     */
    private void openSourceDocument(SearchResult source) {
        String filePath = source.getDocumentFilePath();
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(this, "无法打开源文件", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, DocumentViewerActivity.class);
        intent.putExtra(DocumentViewerActivity.EXTRA_URI, filePath);
        intent.putExtra(DocumentViewerActivity.EXTRA_MIME_TYPE, source.getDocumentMimeType());
        intent.putExtra(DocumentViewerActivity.EXTRA_DOCUMENT_NAME, source.getDocumentName());
        intent.putExtra(DocumentViewerActivity.EXTRA_METADATA_JSON, source.getMetadataJson());
        intent.putExtra(DocumentViewerActivity.EXTRA_SOURCE_LOCATION, source.getSourceLocation());
        startActivity(intent);
    }
}
