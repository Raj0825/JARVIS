package com.jarvis.tools;

import java.util.Map;

/** Base interface for all Jarvis tools. */
public interface JarvisTool {
    /** Unique tool name used in LLM tool calls */
    String getName();

    /** Human-readable description shown to the LLM */
    String getDescription();

    /** JSON Schema of parameters (type: object, properties: {...}) */
    Map<String, Object> getParameterSchema();

    /** Whether this tool has real-world side effects (default-deny when allowWrites=false) */
    default boolean isEffectful() { return false; }

    /** Execute the tool and return a result */
    ToolResult execute(Map<String, Object> params);
}
