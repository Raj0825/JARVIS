package com.jarvis.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI-compatible chat completion client.
 * Works with: OpenAI, Groq, Ollama (openai-compat mode), Together AI, DeepSeek, etc.
 */
public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiCompatibleClient(String baseUrl, String apiKey, String model, double temperature) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public LlmResponse complete(List<Map<String, Object>> messages, List<Map<String, Object>> toolDefinitions) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", temperature);
            body.set("messages", mapper.valueToTree(messages));

            if (toolDefinitions != null && !toolDefinitions.isEmpty()) {
                ArrayNode tools = mapper.createArrayNode();
                for (Map<String, Object> def : toolDefinitions) {
                    ObjectNode t = mapper.createObjectNode();
                    t.put("type", "function");
                    t.set("function", mapper.valueToTree(def));
                    tools.add(t);
                }
                body.set("tools", tools);
                body.put("tool_choice", "auto");
            }

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(mapper.writeValueAsString(body), JSON_TYPE))
                    .build();

            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    String errBody = response.body() != null ? response.body().string() : "(no body)";
                    log.error("OpenAI API error {}: {}", response.code(), errBody);
                    return LlmResponse.error("LLM API returned error " + response.code() + ": " + errBody);
                }

                JsonNode root = mapper.readTree(response.body().string());
                JsonNode choice = root.path("choices").get(0).path("message");
                String finishReason = root.path("choices").get(0).path("finish_reason").asText();

                if ("tool_calls".equals(finishReason) && choice.has("tool_calls")) {
                    List<LlmResponse.ToolCallRequest> calls = new ArrayList<>();
                    for (JsonNode tc : choice.path("tool_calls")) {
                        calls.add(LlmResponse.ToolCallRequest.builder()
                                .id(tc.path("id").asText())
                                .name(tc.path("function").path("name").asText())
                                .argumentsJson(tc.path("function").path("arguments").asText())
                                .build());
                    }
                    return LlmResponse.toolCalls(calls);
                }

                return LlmResponse.text(choice.path("content").asText(""));
            }
        } catch (Exception e) {
            log.error("OpenAI client error", e);
            return LlmResponse.error("Connection error: " + e.getMessage());
        }
    }

    @Override
    public String providerName() { return "OpenAI-Compatible (" + baseUrl + ")"; }
}
