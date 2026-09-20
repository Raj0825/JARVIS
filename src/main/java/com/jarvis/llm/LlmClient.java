package com.jarvis.llm;

import java.util.List;
import java.util.Map;

/**
 * Pluggable LLM client interface.
 * Implementations communicate with OpenAI, Anthropic, Gemini, Ollama, or the built-in Mock.
 */
public interface LlmClient {

    /**
     * Produces a completion response, potentially calling tools.
     *
     * @param messages       Ordered conversation messages (role + content)
     * @param toolDefinitions JSON-schema tool definitions exposed to the LLM
     * @return LlmResponse containing either a text reply or a set of tool call requests
     */
    LlmResponse complete(List<Map<String, Object>> messages, List<Map<String, Object>> toolDefinitions);

    /** Identifies this provider (used in logging) */
    String providerName();
}
