package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Tool to create and manage countdown timers displayed on the JARVIS holographic HUD.
 */
@Component
public class TimerTool implements JarvisTool {

    @Override
    public String getName() {
        return "manage_timer";
    }

    @Override
    public String getDescription() {
        return "Set a countdown timer with a visual HUD card and alarm alert. Use when the user asks 'set a timer for 10 minutes', 'remind me in 5 minutes', or 'set an alarm'.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "duration_seconds", Map.of(
                                "type", "number",
                                "description", "Total duration of the timer in seconds (e.g. 300 for 5 minutes, 600 for 10 minutes)"
                        ),
                        "label", Map.of(
                                "type", "string",
                                "description", "The name or purpose of the timer (e.g. 'Pizza', 'Coffee', 'Meeting', 'Break')"
                        )
                ),
                "required", List.of("duration_seconds")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        Number durationNum = (Number) params.get("duration_seconds");
        if (durationNum == null) {
            durationNum = (Number) params.get("duration");
        }
        if (durationNum == null) {
            durationNum = 300; // default 5 minutes
        }

        int seconds = durationNum.intValue();
        String label = (String) params.get("label");
        if (label == null || label.isBlank()) {
            label = "Timer";
        }

        String formatted = formatTime(seconds);
        String summary = "Timer for " + label + " set for " + formatted + ", sir. The holographic countdown is active.";

        return ToolResult.success(
                summary,
                Map.of("duration", seconds, "label", label, "status", "active"),
                Map.of(
                        "action", "START_TIMER",
                        "duration", seconds,
                        "label", label,
                        "timestamp", System.currentTimeMillis()
                )
        );
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (minutes > 0 && seconds > 0) {
            return minutes + " minute" + (minutes > 1 ? "s " : " ") + seconds + " second" + (seconds > 1 ? "s" : "");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        } else {
            return seconds + " second" + (seconds > 1 ? "s" : "");
        }
    }

    @Override
    public boolean isEffectful() {
        return false;
    }
}
