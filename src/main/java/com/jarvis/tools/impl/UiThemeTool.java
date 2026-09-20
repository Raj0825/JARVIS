package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * UI Theme tool — switches the holographic HUD colour palette in real-time.
 * Always allowed (UI-only, no real-world side effects).
 */
@Component
public class UiThemeTool implements JarvisTool {

    private static final Map<String, String> THEMES = Map.of(
            "cyan",    "Iron Man Cyan — classic arc reactor blue-cyan",
            "crimson", "Crimson Arc — deep red emergency mode",
            "gold",    "Stealth Gold — Mk50 gold signature",
            "matrix",  "Matrix Green — neural net diagnostic overlay"
    );

    @Override
    public String getName() { return "ui_theme"; }

    @Override
    public String getDescription() {
        return "Change the holographic interface colour theme. Available themes: cyan (default), crimson, gold, matrix.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "theme", Map.of("type", "string",
                                "enum", List.of("cyan", "crimson", "gold", "matrix"),
                                "description", "The colour theme to apply")
                ),
                "required", List.of("theme")
        );
    }

    @Override
    public boolean isEffectful() { return false; } // UI-only action

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String theme = ((String) params.getOrDefault("theme", "cyan")).toLowerCase();
        if (!THEMES.containsKey(theme)) {
            theme = "cyan";
        }
        String description = THEMES.get(theme);
        return ToolResult.success(
                "Interface theme switched to " + theme + ". " + description + ".",
                Map.of("theme", theme, "description", description),
                Map.of("action", "SET_THEME", "theme", theme)
        );
    }
}
