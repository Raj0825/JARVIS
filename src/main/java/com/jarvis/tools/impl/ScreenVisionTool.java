package com.jarvis.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jarvis.model.JarvisSettings;
import com.jarvis.service.SettingsService;
import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Screen Vision AI Tool.
 * Captures the current Windows desktop display via Java AWT Robot and passes it to
 * Google Gemini's multimodal vision engine to analyze errors, debug code, or explain visual content.
 */
@Component
public class ScreenVisionTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(ScreenVisionTool.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private SettingsService settingsService;

    @Override
    public String getName() {
        return "screen_vision";
    }

    @Override
    public String getDescription() {
        return "Capture and analyze what is currently on the user's computer screen using Gemini multimodal vision. Use whenever the user asks 'look at my screen', 'what is on my screen', 'read my screen', 'debug this error on my screen', or asks about anything currently visible on their display.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of(
                                "type", "string",
                                "description", "The user's specific question or request regarding what is shown on their screen (e.g., 'explain the error in my terminal', 'summarize this page', 'what code is shown')."
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        if (params == null) {
            params = Map.of();
        }
        String question = (String) params.get("question");
        if (question == null || question.isBlank()) {
            question = (String) params.get("prompt");
        }
        if (question == null || question.isBlank()) {
            question = "Please analyze what is displayed on the user's screen in detail and describe the main window, code, error, or content visible.";
        }

        try {
            log.info("[ScreenVision] Capturing desktop screen...");
            BufferedImage capture = null;

            // 1. Try Java AWT Robot (ensure headless mode is disabled)
            try {
                System.setProperty("java.awt.headless", "false");
                Toolkit toolkit = Toolkit.getDefaultToolkit();
                Dimension screenSize = toolkit.getScreenSize();
                Rectangle screenRect = new Rectangle(screenSize);
                Robot robot = new Robot();
                capture = robot.createScreenCapture(screenRect);
                log.info("[ScreenVision] Captured screen via Java AWT Robot ({}x{})", capture.getWidth(), capture.getHeight());
            } catch (Throwable t) {
                log.warn("[ScreenVision] AWT Robot capture failed ({}). Attempting PowerShell capture...", t.getMessage());
            }

            // 2. PowerShell fallback if Robot failed
            if (capture == null) {
                try {
                    java.nio.file.Path tempJpg = java.nio.file.Files.createTempFile("jarvis_screen_", ".jpg");
                    java.nio.file.Path scriptFile = java.nio.file.Files.createTempFile("jarvis_cap_", ".ps1");
                    String outPath = tempJpg.toAbsolutePath().toString().replace("\\", "/");
                    String script = "Add-Type -AssemblyName System.Windows.Forms\n" +
                            "Add-Type -AssemblyName System.Drawing\n" +
                            "$s = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds\n" +
                            "$b = New-Object System.Drawing.Bitmap $s.Width, $s.Height\n" +
                            "$g = [System.Drawing.Graphics]::FromImage($b)\n" +
                            "$g.CopyFromScreen($s.Location, [System.Drawing.Point]::Empty, $s.Size)\n" +
                            "$b.Save('" + outPath + "', [System.Drawing.Imaging.ImageFormat]::Jpeg)\n" +
                            "$b.Dispose()\n" +
                            "$g.Dispose()\n";
                    java.nio.file.Files.writeString(scriptFile, script);
                    Process p = new ProcessBuilder("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", scriptFile.toAbsolutePath().toString()).start();
                    p.waitFor(6, java.util.concurrent.TimeUnit.SECONDS);
                    java.nio.file.Files.deleteIfExists(scriptFile);
                    if (java.nio.file.Files.exists(tempJpg) && java.nio.file.Files.size(tempJpg) > 500) {
                        capture = ImageIO.read(tempJpg.toFile());
                        java.nio.file.Files.deleteIfExists(tempJpg);
                        log.info("[ScreenVision] Captured screen via PowerShell fallback");
                    }
                } catch (Exception ex) {
                    log.warn("[ScreenVision] PowerShell capture failed: {}", ex.getMessage());
                }
            }

            if (capture == null) {
                return ToolResult.failure("Unable to capture screen. Please verify display is active and unlocked.");
            }

            // 3. Scale down if huge (e.g. 4K) to reduce latency and token size
            int targetWidth = Math.min(1920, capture.getWidth());
            int targetHeight = (int) ((double) capture.getHeight() * ((double) targetWidth / capture.getWidth()));
            BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(capture, 0, 0, targetWidth, targetHeight, null);
            g.dispose();

            // 4. Compress to JPEG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(scaled, "jpeg", baos);
            byte[] imageBytes = baos.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            log.info("[ScreenVision] Screen captured: {}x{}, {} KB. Sending to Gemini Vision...", targetWidth, targetHeight, imageBytes.length / 1024);

            // 5. Retrieve Gemini API key and model from settings
            JarvisSettings settings = settingsService.getSettings("default");
            if (settings == null) {
                return ToolResult.failure("Could not load user settings.");
            }
            String apiKey = settings.getApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                return ToolResult.failure("Gemini API key is not configured in Settings.");
            }

            String model = settings.getModel();
            if (model == null || model.isBlank() || model.contains("mock")) {
                model = "gemini-2.0-flash";
            } else if (model.startsWith("models/")) {
                model = model.substring(7);
            }

            // 6. Build Gemini Multimodal Payload
            ObjectNode body = mapper.createObjectNode();
            ArrayNode contents = body.putArray("contents");
            ObjectNode turn = contents.addObject();
            turn.put("role", "user");
            ArrayNode parts = turn.putArray("parts");

            // Text prompt
            parts.addObject().put("text", "You are JARVIS inspecting Tony Stark's workstation screen. The user asks: \""
                    + question + "\"\nAnalyze the screenshot precisely, identify the active applications, read any errors or code, and answer their question clearly and respectfully.");

            // Image part
            ObjectNode inlineData = parts.addObject().putObject("inlineData");
            inlineData.put("mimeType", "image/jpeg");
            inlineData.put("data", base64Image);

            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(25))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.error("[ScreenVision] Gemini Vision error {}: {}", response.statusCode(), response.body());
                // If model failed with 400 on custom model, try fast fallback with gemini-2.0-flash
                if (!"gemini-2.0-flash".equals(model)) {
                    log.info("[ScreenVision] Retrying with gemini-2.0-flash fallback...");
                    String fallbackUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey;
                    HttpRequest fallbackReq = HttpRequest.newBuilder()
                            .uri(URI.create(fallbackUrl))
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(25))
                            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                            .build();
                    response = client.send(fallbackReq, HttpResponse.BodyHandlers.ofString());
                }
            }

            if (response.statusCode() >= 400) {
                return ToolResult.failure("Gemini Vision returned error " + response.statusCode());
            }

            JsonNode respJson = mapper.readTree(response.body());
            JsonNode textNode = respJson.at("/candidates/0/content/parts/0/text");
            String analysis = textNode.isMissingNode() ? "Screen captured, but no description was generated." : textNode.asText();

            return ToolResult.success(
                    analysis,
                    Map.of("status", "analyzed", "resolution", targetWidth + "x" + targetHeight),
                    null
            );

        } catch (Exception e) {
            String err = (e.getMessage() != null && !e.getMessage().isBlank()) ? e.getMessage() : e.getClass().getSimpleName();
            log.error("[ScreenVision] Failed to analyze screen: {}", err, e);
            return ToolResult.failure("Screen analysis failed: " + err);
        }
    }

    @Override
    public boolean isEffectful() {
        return false;
    }
}
