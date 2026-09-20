package com.jarvis.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Offline diagnostic LLM that simulates tool-calling behaviour.
 * Works without any API key and demonstrates all Jarvis tools with rich responses.
 * This is the default provider until the user configures a real LLM key.
 */
public class MockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private static final List<String> GREETINGS = List.of(
            "All systems nominal, sir. Jarvis is fully operational and ready for your orders.",
            "Good to see you. The holographic interface is calibrated. How may I assist you today?",
            "Systems online. Neural pathways synchronized. Ready to serve, sir."
    );

    private final Random rng = new Random();

    @Override
    public LlmResponse complete(List<Map<String, Object>> messages, List<Map<String, Object>> toolDefinitions) {
        log.debug("[Mock LLM] Processing {} messages with {} tools", messages.size(), toolDefinitions.size());

        // Find the last user message
        String userText = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> m = messages.get(i);
            if ("user".equals(m.get("role"))) {
                userText = ((String) m.getOrDefault("content", "")).toLowerCase();
                break;
            }
        }

        // Simulate intelligent tool routing
        if (userText.contains("search") || userText.contains("look up") || userText.contains("find") || userText.contains("what is") || userText.contains("who is") || userText.contains("weather")) {
            return triggerTool("web_search", Map.of("query", extractQuery(userText)));
        }
        if (userText.contains("image") || userText.contains("generate") || userText.contains("picture") || userText.contains("draw") || userText.contains("visualize")) {
            return triggerTool("generate_image", Map.of("prompt", extractQuery(userText)));
        }
        if (userText.contains("theme") || userText.contains("color") || userText.contains("crimson") || userText.contains("cyan") || userText.contains("gold") || userText.contains("green")) {
            String theme = userText.contains("crimson") ? "crimson" : userText.contains("gold") ? "gold" : userText.contains("green") ? "matrix" : "cyan";
            return triggerTool("ui_theme", Map.of("theme", theme));
        }
        if (userText.contains("effect") || userText.contains("scan") || userText.contains("radar") || userText.contains("pulse") || userText.contains("burst")) {
            String effect = userText.contains("radar") ? "radar_sweep" : userText.contains("burst") ? "particle_burst" : "scanner_sweep";
            return triggerTool("ui_effect", Map.of("effect", effect));
        }
        if (userText.contains("reactor") || userText.contains("core") || userText.contains("power") || userText.contains("intensity")) {
            return triggerTool("ui_reactor", Map.of("speed", 1.5, "intensity", 0.9));
        }
        if (userText.contains("status") || userText.contains("system") || userText.contains("stats") || userText.contains("health") || userText.contains("memory")) {
            return triggerTool("system_stats", Map.of());
        }
        if (userText.contains("hello") || userText.contains("hey") || userText.contains("hi") || userText.contains("good morning") || userText.contains("good evening")) {
            return LlmResponse.text(GREETINGS.get(rng.nextInt(GREETINGS.size())));
        }
        if (userText.contains("help") || userText.contains("what can you")) {
            return LlmResponse.text("I can help you with web searches, UI customization, system diagnostics, and more. Try asking me to 'search for Mars news', 'change the theme to crimson', 'run a radar scan', or 'check system status'. I am also wired into a tool-calling loop — configure a real LLM API key in Settings for the full experience.");
        }
        if (userText.contains("time") || userText.contains("date")) {
            return LlmResponse.text("The current time is " + new Date() + ". All temporal systems are synchronized.");
        }
        if (userText.contains("settings") || userText.contains("api key") || userText.contains("configure")) {
            return LlmResponse.text("To configure a real LLM provider, open the Settings panel using the gear icon. You can enter your OpenAI, Anthropic, or Gemini API key there. I will switch from diagnostic mode to live intelligence immediately.");
        }

        // Generic fallback with personality
        return LlmResponse.text("Understood, sir. I am currently operating in diagnostic mode without a live LLM connection. Configure an API key in Settings to unlock full conversational intelligence. I received: \"" + userText + "\".");
    }

    private LlmResponse triggerTool(String toolName, Map<String, Object> args) {
        try {
            LlmResponse.ToolCallRequest call = LlmResponse.ToolCallRequest.builder()
                    .id("mock-" + System.currentTimeMillis())
                    .name(toolName)
                    .argumentsJson(mapper.writeValueAsString(args))
                    .build();
            return LlmResponse.toolCalls(List.of(call));
        } catch (Exception e) {
            return LlmResponse.text("Error preparing tool call: " + e.getMessage());
        }
    }

    private String extractQuery(String text) {
        // Strip common preamble words
        return text.replaceAll("(?i)(search for|look up|find|what is|who is|generate|draw|create|visualize|picture of|image of)", "").strip();
    }

    @Override
    public String providerName() { return "Jarvis Diagnostic Mock"; }
}
