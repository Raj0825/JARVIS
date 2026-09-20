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
 * Anthropic Messages API client with tool-use support.
 * Targets the /v1/messages endpoint with claude-3-5-sonnet-* models.
 */
public class AnthropicClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String BASE_URL = "https://api.anthropic.com";

    private final String apiKey;
    private final String model;
    private final double temperature;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnthropicClient(String apiKey, String model, double temperature) {
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
            body.put("max_tokens", 4096);
            body.put("temperature", temperature);

            // Separate system message from user/assistant/tool messages
            ArrayNode anthropicMessages = mapper.createArrayNode();
            String systemPrompt = "";
            for (Map<String, Object> m : messages) {
                String role = (String) m.get("role");
                if ("system".equals(role)) {
                    systemPrompt = (String) m.get("content");
                    continue;
                }
                // Convert OpenAI-style tool messages to Anthropic format
                if ("tool".equals(role)) {
                    ObjectNode toolResult = mapper.createObjectNode();
                    toolResult.put("role", "user");
                    ArrayNode content = mapper.createArrayNode();
                    ObjectNode tr = mapper.createObjectNode();
                    tr.put("type", "tool_result");
                    tr.put("tool_use_id", (String) m.getOrDefault("tool_call_id", ""));
                    tr.put("content", (String) m.getOrDefault("content", ""));
                    content.add(tr);
                    toolResult.set("content", content);
                    anthropicMessages.add(toolResult);
                } else {
                    anthropicMessages.add(mapper.valueToTree(m));
                }
            }

            if (!systemPrompt.isEmpty()) body.put("system", systemPrompt);
            body.set("messages", anthropicMessages);

            if (toolDefinitions != null && !toolDefinitions.isEmpty()) {
                ArrayNode tools = mapper.createArrayNode();
                for (Map<String, Object> def : toolDefinitions) {
                    ObjectNode t = mapper.createObjectNode();
                    t.put("name", (String) def.get("name"));
                    t.put("description", (String) def.getOrDefault("description", ""));
                    t.set("input_schema", mapper.valueToTree(def.getOrDefault("parameters", Map.of("type", "object", "properties", Map.of()))));
                    tools.add(t);
                }
                body.set("tools", tools);
            }

            Request request = new Request.Builder()
                    .url(BASE_URL + "/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(mapper.writeValueAsString(body), JSON_TYPE))
                    .build();

            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    String errBody = response.body() != null ? response.body().string() : "(no body)";
                    log.error("Anthropic API error {}: {}", response.code(), errBody);
                    return LlmResponse.error("Anthropic API error " + response.code() + ": " + errBody);
                }

                JsonNode root = mapper.readTree(response.body().string());
                String stopReason = root.path("stop_reason").asText();

                if ("tool_use".equals(stopReason)) {
                    List<LlmResponse.ToolCallRequest> calls = new ArrayList<>();
                    for (JsonNode block : root.path("content")) {
                        if ("tool_use".equals(block.path("type").asText())) {
                            calls.add(LlmResponse.ToolCallRequest.builder()
                                    .id(block.path("id").asText())
                                    .name(block.path("name").asText())
                                    .argumentsJson(mapper.writeValueAsString(block.path("input")))
                                    .build());
                        }
                    }
                    return LlmResponse.toolCalls(calls);
                }

                // Collect text content blocks
                StringBuilder sb = new StringBuilder();
                for (JsonNode block : root.path("content")) {
                    if ("text".equals(block.path("type").asText())) {
                        sb.append(block.path("text").asText());
                    }
                }
                return LlmResponse.text(sb.toString());
            }
        } catch (Exception e) {
            log.error("Anthropic client error", e);
            return LlmResponse.error("Anthropic connection error: " + e.getMessage());
        }
    }

    @Override
    public String providerName() { return "Anthropic"; }
}
