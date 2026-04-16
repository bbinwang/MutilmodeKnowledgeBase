package com.multimode.kb.presentation.settings;

import android.os.Bundle;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.multimode.kb.KbApplication;
import com.multimode.kb.R;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        KbApplication app = (KbApplication) getApplication();

        // LongCat API settings
        EditText editLongcatKey = findViewById(R.id.edit_longcat_api_key);
        EditText editLongcatUrl = findViewById(R.id.edit_longcat_base_url);
        EditText editChatModel = findViewById(R.id.edit_chat_model);
        EditText editOmniModel = findViewById(R.id.edit_omni_model);

        // Embedding settings
        EditText editEmbeddingKey = findViewById(R.id.edit_embedding_api_key);
        EditText editEmbeddingUrl = findViewById(R.id.edit_embedding_base_url);
        EditText editEmbeddingModel = findViewById(R.id.edit_embedding_model);

        // Load current values
        editLongcatKey.setText(app.getAppComponent().getLongcatApiKey());
        editLongcatUrl.setText(app.getAppComponent().getLongcatBaseUrl());
        editChatModel.setText(app.getAppComponent().getChatModel());
        editOmniModel.setText(app.getAppComponent().getOmniModel());
        editEmbeddingKey.setText(app.getAppComponent().getEmbeddingApiKey());
        editEmbeddingUrl.setText(app.getAppComponent().getEmbeddingBaseUrl());
        editEmbeddingModel.setText(app.getAppComponent().getEmbeddingModel());
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSettings();
    }

    private void saveSettings() {
        KbApplication app = (KbApplication) getApplication();
        app.getAppComponent().getPrefs().edit()
                .putString("longcat_api_key", ((EditText) findViewById(R.id.edit_longcat_api_key)).getText().toString())
                .putString("longcat_base_url", ((EditText) findViewById(R.id.edit_longcat_base_url)).getText().toString())
                .putString("chat_model", ((EditText) findViewById(R.id.edit_chat_model)).getText().toString())
                .putString("omni_model", ((EditText) findViewById(R.id.edit_omni_model)).getText().toString())
                .putString("embedding_api_key", ((EditText) findViewById(R.id.edit_embedding_api_key)).getText().toString())
                .putString("embedding_base_url", ((EditText) findViewById(R.id.edit_embedding_base_url)).getText().toString())
                .putString("embedding_model", ((EditText) findViewById(R.id.edit_embedding_model)).getText().toString())
                .apply();
        app.getAppComponent().invalidateClients();
    }
}
