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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Real-time Webcam Vision & Physical Space Awareness AI Tool.
 * Uses the connected webcam to see the real world, inspect objects held in front of the camera,
 * read physical documents, or perform room security sentry checks using Gemini Vision.
 */
@Component
public class WebcamVisionTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(WebcamVisionTool.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private SettingsService settingsService;

    @Override
    public String getName() {
        return "webcam_vision";
    }

    @Override
    public String getDescription() {
        return "Capture a photo from the user's laptop/PC webcam and analyze the real world with Gemini Multimodal Vision. Use when the user asks: 'look at what I am holding', 'look through my camera', 'what is in front of me', 'read this paper', 'who is in the room', or 'sentry check'.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of(
                                "type", "string",
                                "description", "The user's question or intent regarding what to inspect via webcam (e.g., 'what am I holding?', 'read this text', 'describe what you see in the room')"
                        ),
                        "image_base64", Map.of(
                                "type", "string",
                                "description", "Optional pre-captured base64 image data from the HUD camera sensor"
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        if (params == null) params = Map.of();

        String question = (String) params.get("question");
        if (question == null || question.isBlank()) {
            question = "Please inspect what is shown in front of the webcam. Identify any objects, read any handwritten notes or text, and explain what you see clearly.";
        }

        String base64Image = (String) params.get("image_base64");
        if (base64Image != null && !base64Image.isBlank()) {
            if (base64Image.contains(",")) {
                base64Image = base64Image.substring(base64Image.indexOf(",") + 1);
            }
            base64Image = base64Image.replaceAll("\\s+", "");
            log.info("[WebcamVision] Received valid base64 frame from client (length: {})", base64Image.length());
        }

        try {
            // 1. If no image was passed from HUD, capture via Windows MediaCapture PowerShell script
            if (base64Image == null || base64Image.isBlank()) {
                log.info("[WebcamVision] Snapping webcam frame via Windows MediaCapture...");
                Path tempJpg = Files.createTempFile("jarvis_cam_", ".jpg");

                String psScript = String.format(
                        "Add-Type -AssemblyName System.Drawing; " +
                        "$code = @'\n" +
                        "using System;\n" +
                        "using System.Runtime.InteropServices;\n" +
                        "public class CamSnap {\n" +
                        "    [DllImport(\"avicap32.dll\")]\n" +
                        "    public static extern IntPtr capCreateCaptureWindowA(string lpszWindowName, int dwStyle, int x, int y, int nWidth, int nHeight, IntPtr hWnd, int nID);\n" +
                        "    [DllImport(\"user32.dll\")]\n" +
                        "    public static extern bool SendMessage(IntPtr hWnd, uint Msg, int wParam, int lParam);\n" +
                        "    [DllImport(\"user32.dll\")]\n" +
                        "    public static extern bool DestroyWindow(IntPtr hWnd);\n" +
                        "}\n" +
                        "'@\n" +
                        "Add-Type -TypeDefinition $code -ErrorAction SilentlyContinue;\n" +
                        "$hWnd = [CamSnap]::capCreateCaptureWindowA('Webcam', 0, 0, 0, 640, 480, [IntPtr]::Zero, 0);\n" +
                        "if ($hWnd -ne [IntPtr]::Zero) {\n" +
                        "    [CamSnap]::SendMessage($hWnd, 0x40A, 0, 0) | Out-Null; # WM_CAP_DRIVER_CONNECT\n" +
                        "    Start-Sleep -Milliseconds 400;\n" +
                        "    [CamSnap]::SendMessage($hWnd, 0x41E, 0, 0) | Out-Null; # WM_CAP_GRAB_FRAME\n" +
                        "    [CamSnap]::SendMessage($hWnd, 0x419, 0, 0) | Out-Null; # WM_CAP_EDIT_COPY\n" +
                        "    [CamSnap]::SendMessage($hWnd, 0x40B, 0, 0) | Out-Null; # WM_CAP_DRIVER_DISCONNECT\n" +
                        "    [CamSnap]::DestroyWindow($hWnd) | Out-Null;\n" +
                        "    $img = [System.Windows.Forms.Clipboard]::GetImage();\n" +
                        "    if ($img) { $img.Save('%s', [System.Drawing.Imaging.ImageFormat]::Jpeg); }\n" +
                        "}", tempJpg.toAbsolutePath().toString().replace("\\", "\\\\")
                );

                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psScript);
                Process proc = pb.start();
                proc.waitFor(4, TimeUnit.SECONDS);

                File file = tempJpg.toFile();
                if (file.exists() && file.length() > 1000) {
                    byte[] bytes = Files.readAllBytes(tempJpg);
                    base64Image = Base64.getEncoder().encodeToString(bytes);
                    file.delete();
                    log.info("[WebcamVision] Successfully captured webcam frame ({} KB)", bytes.length / 1024);
                } else {
                    file.delete();
                }
            }

            // If still no image, prompt the user to use the optical viewfinder in HUD
            if (base64Image == null || base64Image.isBlank()) {
                return ToolResult.success(
                        "Optical sensor offline or busy, Mr. Raj. Please click the [ 👁 CAM ] viewfinder button in the top HUD bar to activate your browser webcam feed for instant inspection.",
                        Map.of("status", "camera_permission_required"),
                        null
                );
            }

            // 2. Call Gemini Vision
            JarvisSettings settings = settingsService.getSettings("default");
            if (settings == null || settings.getApiKey() == null || settings.getApiKey().isBlank()) {
                return ToolResult.failure("Gemini API key is not configured in Settings.");
            }

            String callSign = settings.getUserCallSign() != null ? settings.getUserCallSign() : "Mr. Raj";
            String apiKey = settings.getApiKey();
            String model = settings.getModel();
            if (model == null || model.isBlank() || model.contains("mock")) {
                model = "gemini-2.0-flash";
            } else if (model.startsWith("models/")) {
                model = model.substring(7);
            }

            ObjectNode body = mapper.createObjectNode();
            ArrayNode contents = body.putArray("contents");
            ObjectNode turn = contents.addObject();
            turn.put("role", "user");
            ArrayNode parts = turn.putArray("parts");

            // Text prompt
            parts.addObject().put("text", "You are JARVIS inspecting what is held or visible in front of " + callSign + "'s workstation webcam.\n"
                    + "The user asks: \"" + question + "\"\n"
                    + "Identify any real-world objects, electronic components, books, documents, or surroundings visible in this camera frame. "
                    + "Provide a sharp, intelligent, and insightful answer in Paul Bettany's refined JARVIS persona.");

            // Image part
            ObjectNode inlineData = parts.addObject().putObject("inlineData");
            inlineData.put("mimeType", "image/jpeg");
            inlineData.put("data", base64Image);

            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(12))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(25))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.error("[WebcamVision] Gemini Vision error {}: {}", response.statusCode(), response.body());
                return ToolResult.failure("Gemini Vision failed to process webcam capture (HTTP " + response.statusCode() + ")");
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
            if (textNode.isMissingNode()) {
                return ToolResult.failure("Gemini returned empty visual analysis.");
            }

            String analysis = textNode.asText();
            log.info("[WebcamVision] Successfully analyzed webcam frame. Length: {} chars", analysis.length());
            return ToolResult.success(analysis, Map.of("analysis", analysis), null);

        } catch (Exception e) {
            log.error("[WebcamVision] Error in webcam analysis: {}", e.getMessage(), e);
            return ToolResult.failure("Failed to analyze webcam visual: " + e.getMessage());
        }
    }

    @Override
    public boolean isEffectful() {
        return false;
    }
}
