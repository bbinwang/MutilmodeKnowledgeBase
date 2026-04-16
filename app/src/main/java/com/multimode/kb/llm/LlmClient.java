package com.multimode.kb.llm;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Client for OpenAI-compatible chat completion API (LongCat Flash Chat).
 * Supports multi-turn conversations.
 */
public class LlmClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Moshi moshi;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    private final JsonAdapter<Map<String, Object>> mapAdapter;

    public LlmClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.moshi = new Moshi.Builder().build();
        this.mapAdapter = moshi.adapter(Types.newParameterizedType(Map.class, String.class, Object.class));

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Single-turn chat: system prompt + user message.
     */
    public String chat(String systemPrompt, String userMessage) throws IOException {
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        return chatWithMessages(messages);
    }

    /**
     * Multi-turn chat with full message history.
     *
     * @param systemPrompt system message
     * @param history      previous conversation turns as [role, content] pairs
     * @param userMessage  current user message
     * @return assistant response text
     */
    public String chatMultiTurn(String systemPrompt, List<String[]> history, String userMessage) throws IOException {
        List<Map<String, String>> messages = new ArrayList<>();

        // System message
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        // History
        if (history != null) {
            for (String[] turn : history) {
                Map<String, String> msg = new HashMap<>();
                msg.put("role", turn[0]);
                msg.put("content", turn[1]);
                messages.add(msg);
            }
        }

        // Current user message
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        return chatWithMessages(messages);
    }

    private String chatWithMessages(List<Map<String, String>> messages) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", messages);

        String json = mapAdapter.toJson(body);
        Request request = new Request.Builder()
                .url(baseUrl + "/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                throw new IOException("LLM API error " + response.code() + ": " + errorBody);
            }

            String responseBody = response.body().string();
            Map<String, Object> parsed = mapAdapter.fromJson(responseBody);

            List<Object> choices = (List<Object>) parsed.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = (Map<String, Object>) choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                if (message != null) {
                    return (String) message.get("content");
                }
            }
            throw new IOException("Unexpected response format: " + responseBody);
        }
    }

    public String getBaseUrl() { return baseUrl; }
    public String getModel() { return model; }
}
