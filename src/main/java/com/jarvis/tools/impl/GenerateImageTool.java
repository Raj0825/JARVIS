package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Holographic Blueprint & AI Image Projector Tool.
 * Uses Pollinations Flux AI engine to generate real, high-resolution AI schematics, blueprints, and artwork.
 */
@Component
public class GenerateImageTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(GenerateImageTool.class);
    private static final String POLLINATIONS_BASE = "https://image.pollinations.ai/prompt/";
    private final Random rng = new Random();

    @Override
    public String getName() {
        return "generate_image";
    }

    @Override
    public String getDescription() {
        return "Generate high-resolution AI artwork, holographic schematics, or blueprints from a text prompt. Emits a SHOW_IMAGE action to project in the HUD panel.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "prompt", Map.of(
                                "type", "string",
                                "description", "Detailed description of the image or blueprint to generate (e.g. 'holographic blueprint of Iron Man arc reactor, cyan wireframe technical drawing on dark background')"
                        ),
                        "width", Map.of(
                                "type", "integer",
                                "description", "Image width in pixels (default 800)"
                        ),
                        "height", Map.of(
                                "type", "integer",
                                "description", "Image height in pixels (default 600)"
                        )
                ),
                "required", List.of("prompt")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String rawPrompt = (String) params.getOrDefault("prompt", "futuristic holographic arc reactor schematic");
        if (rawPrompt == null || rawPrompt.isBlank()) {
            rawPrompt = "futuristic holographic arc reactor schematic";
        }

        int width = ((Number) params.getOrDefault("width", 800)).intValue();
        int height = ((Number) params.getOrDefault("height", 600)).intValue();
        int seed = 100000 + rng.nextInt(900000);

        // If user asks for blueprint/schematic or Iron Man tech, enrich the prompt for maximum cinematic quality
        String enrichedPrompt = rawPrompt;
        String lower = rawPrompt.toLowerCase();
        if (lower.contains("blueprint") || lower.contains("schematic") || lower.contains("reactor") || lower.contains("iron man") || lower.contains("jarvis")) {
            if (!lower.contains("wireframe") && !lower.contains("holographic")) {
                enrichedPrompt += ", holographic wireframe, 8k technical schematic, glowing cyan HUD accents, Stark Industries aesthetic";
            }
        }

        String encoded = URLEncoder.encode(enrichedPrompt, StandardCharsets.UTF_8);
        String imageUrl = POLLINATIONS_BASE + encoded + "?width=" + width + "&height=" + height + "&seed=" + seed + "&nologo=true&model=flux";

        log.info("[GenerateImageTool] Generated holographic AI image URL: {}", imageUrl);

        return ToolResult.success(
                "Holographic image projection generated for: \"" + rawPrompt + "\". Displaying on HUD projector, sir.",
                Map.of("type", "image", "prompt", rawPrompt, "url", imageUrl, "width", width, "height", height, "seed", seed),
                Map.of("action", "SHOW_IMAGE", "url", imageUrl, "prompt", rawPrompt)
        );
    }

    @Override
    public boolean isEffectful() {
        return false;
    }
}
