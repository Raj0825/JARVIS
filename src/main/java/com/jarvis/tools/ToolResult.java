package com.jarvis.tools;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** Result returned by a tool execution. */
@Data
@Builder
public class ToolResult {

    /** Whether the tool succeeded */
    private final boolean success;

    /** Summary text the LLM should see */
    private final String summary;

    /**
     * Optional rich data payload (can contain anything: search results, image URL, stats).
     * This is sent to the frontend over WebSocket for HUD card rendering.
     */
    private final Map<String, Object> data;

    /**
     * Optional UI action command for the React frontend.
     * E.g. {"action": "SET_THEME", "theme": "crimson"}
     */
    private final Map<String, Object> uiAction;

    /** Error message on failure */
    private final String error;

    public static ToolResult success(String summary) {
        return ToolResult.builder().success(true).summary(summary).build();
    }

    public static ToolResult success(String summary, Map<String, Object> data) {
        return ToolResult.builder().success(true).summary(summary).data(data).build();
    }

    public static ToolResult success(String summary, Map<String, Object> data, Map<String, Object> uiAction) {
        return ToolResult.builder().success(true).summary(summary).data(data).uiAction(uiAction).build();
    }

    public static ToolResult failure(String error) {
        return ToolResult.builder().success(false).summary("Tool failed: " + error).error(error).build();
    }
}
