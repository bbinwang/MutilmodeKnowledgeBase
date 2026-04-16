package com.multimode.kb.llm;

import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Client for LongCat Omni multimodal API.
 * Supports image understanding, audio transcription, and video understanding
 * via content array format with input_image, input_audio, input_video types.
 *
 * Model: LongCat-Flash-Omni-2603
 * Endpoint: POST /openai/v1/chat/completions (same as text chat, but content is an array)
 */
public class OmniClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Moshi moshi;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public OmniClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.moshi = new Moshi.Builder().build();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS) // multimodal can be slow, especially video frames
                .writeTimeout(180, TimeUnit.SECONDS) // large base64 payloads
                .build();
    }

    /**
     * Describe an image using Omni model.
     *
     * @param base64Image base64-encoded image data
     * @param prompt      instruction text (e.g., "请详细描述这张图片的内容")
     * @return description text
     */
    public String describeImage(String base64Image, String prompt) throws IOException {
        List<Map<String, Object>> content = new ArrayList<>();

        // Image input
        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "input_image");
        Map<String, Object> imageData = new HashMap<>();
        imageData.put("type", "base64");
        imageData.put("data", Arrays.asList(base64Image));
        imagePart.put("input_image", imageData);
        content.add(imagePart);

        // Text prompt
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        content.add(textPart);

        return sendOmniRequest(content);
    }

    /**
     * Transcribe audio using Omni model.
     *
     * @param base64Audio base64-encoded audio data
     * @param format      audio format (wav, mp3, pcm16)
     * @param prompt      instruction text (e.g., "请将这段音频转写为文字，并标注时间")
     * @return transcription text
     */
    public String transcribeAudio(String base64Audio, String format, String prompt) throws IOException {
        List<Map<String, Object>> content = new ArrayList<>();

        // Audio input
        Map<String, Object> audioPart = new HashMap<>();
        audioPart.put("type", "input_audio");
        Map<String, Object> audioData = new HashMap<>();
        audioData.put("type", "base64");
        audioData.put("data", base64Audio);
        audioData.put("format", format);
        audioPart.put("input_audio", audioData);
        content.add(audioPart);

        // Text prompt
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        content.add(textPart);

        return sendOmniRequest(content);
    }

    /**
     * Understand/describe a video using Omni model.
     *
     * @param base64Video base64-encoded video data
     * @param prompt      instruction text
     * @return description text
     */
    public String describeVideo(String base64Video, String prompt) throws IOException {
        List<Map<String, Object>> content = new ArrayList<>();

        // Video input
        Map<String, Object> videoPart = new HashMap<>();
        videoPart.put("type", "input_video");
        Map<String, Object> videoData = new HashMap<>();
        videoData.put("type", "base64");
        videoData.put("data", base64Video);
        videoPart.put("input_video", videoData);
        content.add(videoPart);

        // Text prompt
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        content.add(textPart);

        return sendOmniRequest(content);
    }

    private String sendOmniRequest(List<Map<String, Object>> userContent) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("session_id", UUID.randomUUID().toString());
        body.put("output_modalities", Arrays.asList("text"));

        // System message
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        List<Map<String, Object>> systemContent = new ArrayList<>();
        Map<String, Object> sysText = new HashMap<>();
        sysText.put("type", "text");
        sysText.put("text", "你是一个专业的内容分析助手，请准确、详细地描述和分析你看到或听到的内容。");
        systemContent.add(sysText);
        systemMsg.put("content", systemContent);

        // User message with multimodal content
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);

        body.put("messages", Arrays.asList(systemMsg, userMsg));

        String json = moshi.<Object>adapter(Types.newParameterizedType(Map.class, String.class, Object.class))
                .toJson(body);

        Request request = new Request.Builder()
                .url(baseUrl + "/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                throw new IOException("Omni API error " + response.code() + ": " + errorBody);
            }

            String responseBody = response.body().string();
            Map<String, Object> parsed = moshi.<Object>adapter(
                    Types.newParameterizedType(Map.class, String.class, Object.class))
                    .fromJson(responseBody);

            List<Object> choices = (List<Object>) parsed.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = (Map<String, Object>) choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                if (message != null) {
                    return (String) message.get("content");
                }
            }
            throw new IOException("Unexpected Omni response: " + responseBody);
        }
    }

    public String getBaseUrl() { return baseUrl; }
    public String getModel() { return model; }
}
