package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * UI Effect tool — triggers visual effects on the holographic interface.
 * UI-only, always permitted regardless of allowWrites.
 */
@Component
public class UiEffectTool implements JarvisTool {

    @Override
    public String getName() { return "ui_effect"; }

    @Override
    public String getDescription() {
        return "Trigger a visual effect on the holographic interface. Effects: particle_burst, scanner_sweep, radar_sweep, energy_pulse, glitch_flash.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "effect", Map.of("type", "string",
                                "enum", List.of("particle_burst", "scanner_sweep", "radar_sweep", "energy_pulse", "glitch_flash"),
                                "description", "The visual effect to trigger"),
                        "duration", Map.of("type", "number", "description", "Effect duration in milliseconds (default 2000)")
                ),
                "required", List.of("effect")
        );
    }

    @Override
    public boolean isEffectful() { return false; }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String effect = (String) params.getOrDefault("effect", "energy_pulse");
        int duration = ((Number) params.getOrDefault("duration", 2000)).intValue();

        return ToolResult.success(
                "Visual effect '" + effect + "' triggered on the interface.",
                Map.of("effect", effect, "duration", duration),
                Map.of("action", "TRIGGER_EFFECT", "effect", effect, "duration", duration)
        );
    }
}
