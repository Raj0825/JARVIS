package com.jarvis.llm;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.ArrayList;

/**
 * Unified response model returned from any LlmClient implementation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse {

    public enum Type { TEXT, TOOL_CALLS, ERROR }

    private Type type;

    /** Final text answer (populated when type == TEXT) */
    private String content;

    /** Tool calls requested by LLM (populated when type == TOOL_CALLS) */
    @Builder.Default
    private List<ToolCallRequest> toolCalls = new ArrayList<>();

    /** Error message (populated when type == ERROR) */
    private String error;

    /** Usage stats */
    private int promptTokens;
    private int completionTokens;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallRequest {
        private String id;
        private String name;
        private String argumentsJson;
    }

    public static LlmResponse text(String content) {
        return LlmResponse.builder().type(Type.TEXT).content(content).build();
    }

    public static LlmResponse error(String message) {
        return LlmResponse.builder().type(Type.ERROR).error(message).build();
    }

    public static LlmResponse toolCalls(List<ToolCallRequest> calls) {
        return LlmResponse.builder().type(Type.TOOL_CALLS).toolCalls(calls).build();
    }
}
