package com.multimode.kb.llm;

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
 * Client for OpenAI-compatible Embedding API.
 * LongCat does NOT provide an embedding endpoint, so this connects to
 * a separately configured embedding service (e.g., OpenAI, BGE, etc.).
 */
public class EmbeddingClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Moshi moshi;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public EmbeddingClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.moshi = new Moshi.Builder().build();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Generate embedding for a single text.
     */
    public float[] embed(String text) throws IOException {
        List<float[]> results = embedBatch(Arrays.asList(text));
        return results.isEmpty() ? new float[0] : results.get(0);
    }

    /**
     * Generate embeddings for a batch of texts.
     * Uses POST /v1/embeddings (OpenAI format).
     */
    public List<float[]> embedBatch(List<String> texts) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", texts);

        String json = moshi.<Object>adapter(Types.newParameterizedType(Map.class, String.class, Object.class))
                .toJson(body);

        Request request = new Request.Builder()
                .url(baseUrl + "/v1/embeddings")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                throw new IOException("Embedding API error " + response.code() + ": " + errorBody);
            }

            String responseBody = response.body().string();
            return parseEmbeddingResponse(responseBody);
        }
    }

    private List<float[]> parseEmbeddingResponse(String json) throws IOException {
        Map<String, Object> parsed = (Map<String, Object>) moshi.<Object>adapter(
                Types.newParameterizedType(Map.class, String.class, Object.class))
                .fromJson(json);

        List<float[]> embeddings = new ArrayList<>();
        List<Object> data = (List<Object>) parsed.get("data");

        if (data != null) {
            for (Object item : data) {
                Map<String, Object> entry = (Map<String, Object>) item;
                List<Number> embeddingList = (List<Number>) entry.get("embedding");
                if (embeddingList != null) {
                    float[] vec = new float[embeddingList.size()];
                    for (int i = 0; i < embeddingList.size(); i++) {
                        vec[i] = embeddingList.get(i).floatValue();
                    }
                    embeddings.add(vec);
                }
            }
        }
        return embeddings;
    }

    public String getBaseUrl() { return baseUrl; }
    public String getModel() { return model; }
}
