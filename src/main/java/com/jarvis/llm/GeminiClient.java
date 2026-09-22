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
 * Google Gemini API client using function-calling (generateContent).
 */
public class GeminiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final String model;
    private final double temperature;
    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiClient(String apiKey, String model, double temperature) {
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public LlmResponse complete(List<Map<String, Object>> messages, List<Map<String, Object>> toolDefinitions) {
        try {
            ObjectNode body = mapper.createObjectNode();

            // Convert OpenAI-style messages to Gemini contents
            ArrayNode contents = mapper.createArrayNode();
            String systemInstruction = null;
            for (Map<String, Object> m : messages) {
                String role = (String) m.get("role");
                if ("system".equals(role)) {
                    systemInstruction = (String) m.getOrDefault("content", "");
                    continue;
                }

                ObjectNode entry = mapper.createObjectNode();
                ArrayNode parts = mapper.createArrayNode();

                if ("tool".equals(role)) {
                    // Function response from tool execution (Gemini REST API expects role: "user")
                    entry.put("role", "user");
                    String toolName = (String) m.get("name");
                    String toolContent = (String) m.getOrDefault("content", "");
                    ObjectNode part = mapper.createObjectNode();
                    ObjectNode fr = mapper.createObjectNode();
                    fr.put("name", toolName);
                    ObjectNode resp = mapper.createObjectNode();
                    resp.put("output", toolContent != null ? toolContent : "");
                    fr.set("response", resp);
                    part.set("functionResponse", fr);
                    parts.add(part);
                } else if ("assistant".equals(role) && m.containsKey("tool_calls")) {
                    // Model previously returned function call(s)
                    entry.put("role", "model");
                    Object toolCallsObj = m.get("tool_calls");
                    if (toolCallsObj instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> tc) {
                                Map<?, ?> fn = (Map<?, ?>) tc.get("function");
                                String name = fn != null ? (String) fn.get("name") : "";
                                String argsJson = fn != null ? (String) fn.get("arguments") : "{}";
                                String id = (String) tc.get("id");
                                ObjectNode part = mapper.createObjectNode();
                                ObjectNode fc = mapper.createObjectNode();
                                fc.put("name", name);
                                try {
                                    fc.set("args", mapper.readTree(argsJson != null && !argsJson.isBlank() ? argsJson : "{}"));
                                } catch (Exception ignored) {
                                    fc.set("args", mapper.createObjectNode());
                                }
                                part.set("functionCall", fc);
                                parts.add(part);
                            }
                        }
                    }
                    String content = (String) m.get("content");
                    if (content != null && !content.isBlank()) {
                        ObjectNode textPart = mapper.createObjectNode();
                        textPart.put("text", content);
                        parts.add(textPart);
                    }
                } else {
                    entry.put("role", "user".equals(role) ? "user" : "model");
                    String content = (String) m.getOrDefault("content", "");
                    if (content != null && !content.isBlank()) {
                        ObjectNode part = mapper.createObjectNode();
                        part.put("text", content);
                        parts.add(part);
                    }
                }

                if (!parts.isEmpty()) {
                    entry.set("parts", parts);
                    // Merge with previous entry if it has the same role to maintain strict role alternation
                    if (contents.size() > 0) {
                        JsonNode prev = contents.get(contents.size() - 1);
                        if (prev.path("role").asText().equals(entry.path("role").asText())) {
                            ArrayNode prevParts = (ArrayNode) prev.path("parts");
                            prevParts.addAll(parts);
                            continue;
                        }
                    }
                    contents.add(entry);
                }
            }
            body.set("contents", contents);

            if (systemInstruction != null) {
                ObjectNode si = mapper.createObjectNode();
                ArrayNode parts = mapper.createArrayNode();
                ObjectNode part = mapper.createObjectNode();
                part.put("text", systemInstruction);
                parts.add(part);
                si.set("parts", parts);
                body.set("systemInstruction", si);
            }

            // Add tools
            if (toolDefinitions != null && !toolDefinitions.isEmpty()) {
                ArrayNode functionDeclarations = mapper.createArrayNode();
                for (Map<String, Object> def : toolDefinitions) {
                    ObjectNode fd = mapper.createObjectNode();
                    fd.put("name", (String) def.get("name"));
                    fd.put("description", (String) def.getOrDefault("description", ""));
                    fd.set("parameters", mapper.valueToTree(def.getOrDefault("parameters", Map.of("type", "object", "properties", Map.of()))));
                    functionDeclarations.add(fd);
                }
                ArrayNode toolsNode = mapper.createArrayNode();
                ObjectNode toolEntry = mapper.createObjectNode();
                toolEntry.set("functionDeclarations", functionDeclarations);
                toolsNode.add(toolEntry);
                body.set("tools", toolsNode);
            }

            // Generation config
            ObjectNode genConfig = mapper.createObjectNode();
            genConfig.put("temperature", temperature);
            body.set("generationConfig", genConfig);

            String cleanModel = (model != null && !model.isBlank()) ? model.trim() : "gemini-2.0-flash";
            if (cleanModel.startsWith("models/")) {
                cleanModel = cleanModel.substring(7);
            }
            String url = BASE_URL + cleanModel + ":generateContent?key=" + apiKey;
            Request request = new Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(mapper.writeValueAsString(body), JSON_TYPE))
                    .build();
            log.info("[Gemini] Sending generateContent request to model '{}' (payload size: {} bytes)...", cleanModel, mapper.writeValueAsString(body).length());
            try (Response response = http.newCall(request).execute()) {
                log.info("[Gemini] Response received: HTTP {}", response.code());
                if (!response.isSuccessful() || response.body() == null) {
                    String errBody = response.body() != null ? response.body().string() : "(no body)";
                    // Transparent self-healing fallback for models with strict thought_signature validation
                    if (response.code() == 400 && errBody.contains("thought_signature") && !"gemini-2.0-flash".equals(cleanModel)) {
                        log.warn("[Gemini] Model '{}' rejected history with thought_signature error. Retrying with 'gemini-2.0-flash'...", cleanModel);
                        String fallbackUrl = BASE_URL + "gemini-2.0-flash:generateContent?key=" + apiKey;
                        Request retryReq = new Request.Builder()
                                .url(fallbackUrl)
                                .header("Content-Type", "application/json")
                                .post(RequestBody.create(mapper.writeValueAsString(body), JSON_TYPE))
                                .build();
                        try (Response retryResp = http.newCall(retryReq).execute()) {
                            if (retryResp.isSuccessful() && retryResp.body() != null) {
                                JsonNode root = mapper.readTree(retryResp.body().string());
                                JsonNode candidate = root.path("candidates").get(0);
                                return parseCandidate(candidate);
                            }
                        } catch (Exception retryEx) {
                            log.warn("[Gemini] Fallback retry failed: {}", retryEx.getMessage());
                        }
                    }
                    log.error("Gemini API error {}: {}", response.code(), errBody);
                    return LlmResponse.error("Gemini API error " + response.code() + ": " + errBody);
                }

                JsonNode root = mapper.readTree(response.body().string());
                JsonNode candidate = root.path("candidates").get(0);
                return parseCandidate(candidate);
            }
        } catch (Exception e) {
            log.error("Gemini client error", e);
            return LlmResponse.error("Gemini connection error: " + e.getMessage());
        }
    }

    private LlmResponse parseCandidate(JsonNode candidate) throws Exception {
        if (candidate == null || candidate.isMissingNode()) {
            return LlmResponse.text("No response generated.");
        }
        JsonNode contentNode = candidate.path("content");

        // Inspect whole candidate for thought signature
        String candidateSig = null;
        if (candidate.has("thought_signature")) candidateSig = candidate.path("thought_signature").asText();
        else if (candidate.has("thoughtSignature")) candidateSig = candidate.path("thoughtSignature").asText();

        for (JsonNode p : contentNode.path("parts")) {
            if (p.has("thought_signature")) candidateSig = p.path("thought_signature").asText();
            else if (p.has("thoughtSignature")) candidateSig = p.path("thoughtSignature").asText();
            if (p.has("functionCall")) {
                JsonNode f = p.path("functionCall");
                if (f.has("thought_signature")) candidateSig = f.path("thought_signature").asText();
                else if (f.has("thoughtSignature")) candidateSig = f.path("thoughtSignature").asText();
            }
        }

        // Check for function call parts
        List<LlmResponse.ToolCallRequest> calls = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();

        for (JsonNode part : contentNode.path("parts")) {
            if (part.has("functionCall")) {
                JsonNode fc = part.path("functionCall");
                String rawName = fc.path("name").asText();
                String cleanName = rawName.replace("default_api:", "");
                String sig = candidateSig;
                if (part.has("thought_signature")) sig = part.path("thought_signature").asText();
                else if (part.has("thoughtSignature")) sig = part.path("thoughtSignature").asText();
                else if (fc.has("thought_signature")) sig = fc.path("thought_signature").asText();
                else if (fc.has("thoughtSignature")) sig = fc.path("thoughtSignature").asText();

                String callId = (sig != null && !sig.isBlank()) ? "ts:" + sig : ("gemini-" + System.currentTimeMillis());
                calls.add(LlmResponse.ToolCallRequest.builder()
                        .id(callId)
                        .name(cleanName)
                        .argumentsJson(mapper.writeValueAsString(fc.path("args")))
                        .build());
            } else if (part.has("text")) {
                textBuilder.append(part.path("text").asText());
            }
        }

        if (!calls.isEmpty()) return LlmResponse.toolCalls(calls);
        return LlmResponse.text(textBuilder.toString());
    }

    @Override
    public String providerName() { return "Google Gemini"; }
}
