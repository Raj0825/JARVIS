package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Reactor core controller — adjusts the central Arc Reactor visual parameters.
 */
@Component
public class UiReactorTool implements JarvisTool {

    @Override
    public String getName() { return "ui_reactor"; }

    @Override
    public String getDescription() {
        return "Adjust the Arc Reactor core visual parameters: rotation speed, glow intensity, and audio pulse frequency.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "speed",     Map.of("type", "number", "description", "Ring rotation speed multiplier (0.1–5.0, default 1.0)"),
                        "intensity", Map.of("type", "number", "description", "Core glow intensity (0.0–1.0, default 0.85)"),
                        "frequency", Map.of("type", "number", "description", "Audio pulse frequency in Hz (100–1000, default 440)")
                ),
                "required", List.of()
        );
    }

    @Override
    public boolean isEffectful() { return false; }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        double speed = ((Number) params.getOrDefault("speed", 1.0)).doubleValue();
        double intensity = ((Number) params.getOrDefault("intensity", 0.85)).doubleValue();
        double frequency = ((Number) params.getOrDefault("frequency", 440.0)).doubleValue();

        // Clamp values
        speed = Math.max(0.1, Math.min(5.0, speed));
        intensity = Math.max(0.0, Math.min(1.0, intensity));
        frequency = Math.max(100.0, Math.min(1000.0, frequency));

        return ToolResult.success(
                String.format("Arc Reactor adjusted: speed=%.1fx, intensity=%.0f%%, frequency=%.0fHz.", speed, intensity * 100, frequency),
                Map.of("speed", speed, "intensity", intensity, "frequency", frequency),
                Map.of("action", "SET_REACTOR", "speed", speed, "intensity", intensity, "frequency", frequency)
        );
    }
}
