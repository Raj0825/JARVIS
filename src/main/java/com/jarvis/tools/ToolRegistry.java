package com.jarvis.tools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry of all available Jarvis tools.
 * Auto-discovers all JarvisTool beans in the Spring context.
 */
@Component
public class ToolRegistry {

    @Autowired
    private List<JarvisTool> tools;

    private final Map<String, JarvisTool> toolMap = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        for (JarvisTool tool : tools) {
            toolMap.put(tool.getName(), tool);
        }
    }

    public Optional<JarvisTool> getTool(String name) {
        return Optional.ofNullable(toolMap.get(name));
    }

    public List<JarvisTool> getAllTools() {
        return new ArrayList<>(toolMap.values());
    }

    /**
     * Returns tool definitions in the OpenAI function-calling schema format.
     */
    public List<Map<String, Object>> getToolDefinitions() {
        return toolMap.values().stream()
                .map(tool -> {
                    Map<String, Object> def = new LinkedHashMap<>();
                    def.put("name", tool.getName());
                    def.put("description", tool.getDescription());
                    def.put("parameters", tool.getParameterSchema());
                    return def;
                })
                .collect(Collectors.toList());
    }
}
