package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Image generation tool — returns a Picsum placeholder image for the HUD.
 * In production, swap the URL builder for a real image API (DALL-E / Stability AI).
 */
@Component
public class GenerateImageTool implements JarvisTool {

    private static final String PLACEHOLDER_BASE = "https://picsum.photos/seed/";
    private final Random rng = new Random();

    @Override
    public String getName() { return "generate_image"; }

    @Override
    public String getDescription() {
        return "Generate or retrieve an image based on a text prompt. Returns an image URL for display in the HUD panel.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "prompt", Map.of("type", "string", "description", "Description of the image to generate"),
                        "width",  Map.of("type", "integer", "description", "Image width in pixels (default 512)"),
                        "height", Map.of("type", "integer", "description", "Image height in pixels (default 512)")
                ),
                "required", List.of("prompt")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String prompt = (String) params.getOrDefault("prompt", "abstract");
        int width  = ((Number) params.getOrDefault("width",  512)).intValue();
        int height = ((Number) params.getOrDefault("height", 512)).intValue();

        // Deterministic seed from prompt for consistent results
        int seed = Math.abs(prompt.hashCode()) % 1000;
        String imageUrl = PLACEHOLDER_BASE + seed + "/" + width + "/" + height;

        return ToolResult.success(
                "Image generated for: \"" + prompt + "\". Displaying in HUD panel.",
                Map.of("type", "image", "prompt", prompt, "url", imageUrl, "width", width, "height", height),
                Map.of("action", "SHOW_IMAGE", "url", imageUrl, "prompt", prompt)
        );
    }
}
