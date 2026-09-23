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
import java.util.*;
import java.util.List;

/**
 * The Meeting Whisperer - Real-Time Interview & Meeting Co-Pilot.
 * Delivers covert teleprompter intelligence for technical interviews, client
 * pitches,
 * visual screen problem solving, and automated meeting minutes.
 */
@Component
public class MeetingCopilotTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(MeetingCopilotTool.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private SettingsService settingsService;

    @Override
    public String getName() {
        return "meeting_copilot";
    }

    @Override
    public String getDescription() {
        return "Real-time interview and meeting co-pilot. Secretly whispers answers, solves coding/architecture problems shown on screen, handles sales objections, and drafts meeting minutes.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "enum", List.of("whisper_advice", "solve_screen", "start_session", "generate_minutes"),
                                "description",
                                "Action to perform: 'whisper_advice' (answer interviewer question), 'solve_screen' (analyze and solve problem on screen), 'start_session' (init co-pilot HUD), or 'generate_minutes'"),
                        "query", Map.of(
                                "type", "string",
                                "description",
                                "The question or statement asked by the interviewer or meeting participant"),
                        "mode", Map.of(
                                "type", "string",
                                "enum", List.of("interview", "sales", "meeting"),
                                "description",
                                "Session mode: 'interview' (STAR method & algorithms), 'sales' (objections & value), or 'meeting' (minutes & alignment)"),
                        "topic", Map.of(
                                "type", "string",
                                "description",
                                "Context, company, or target role (e.g. 'Google Senior Backend Engineer', 'Enterprise Client Demo')"),
                        "notes", Map.of(
                                "type", "string",
                                "description", "Additional meeting notes or custom context to incorporate")),
                "required", List.of("action"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String action = (String) params.getOrDefault("action", "whisper_advice");
        String mode = (String) params.getOrDefault("mode", "interview");
        String query = (String) params.get("query");
        String topic = (String) params.getOrDefault("topic", "General Tech & Leadership");
        String notes = (String) params.get("notes");

        log.info("[MeetingCopilot] Executing action='{}', mode='{}', query='{}'", action, mode, query);

        return switch (action.toLowerCase(Locale.ROOT)) {
            case "start_session" -> handleStartSession(mode, topic);
            case "solve_screen" -> handleSolveScreen(query, mode);
            case "generate_minutes" -> handleGenerateMinutes(query, notes, topic);
            case "whisper_advice" -> handleWhisperAdvice(query, mode, topic);
            default -> handleWhisperAdvice(query != null ? query : action, mode, topic);
        };
    }

    private ToolResult handleStartSession(String mode, String topic) {
        String title = switch (mode.toLowerCase(Locale.ROOT)) {
            case "sales" -> "Client Pitch & Negotiation Co-Pilot";
            case "meeting" -> "Executive Meeting & Minutes Co-Pilot";
            default -> "Technical & Behavioral Interview Co-Pilot";
        };

        Map<String, Object> data = Map.of(
                "mode", mode,
                "topic", topic,
                "title", title,
                "status", "ACTIVE");

        Map<String, Object> uiAction = Map.of(
                "action", "OPEN_WHISPERER",
                "mode", mode,
                "topic", topic);

        return ToolResult.success(
                "Meeting Whisperer initialized in " + mode.toUpperCase() + " mode for " + topic
                        + ". Floating teleprompter is ready.",
                data,
                uiAction);
    }

    private ToolResult handleWhisperAdvice(String question, String mode, String topic) {
        if (question == null || question.isBlank()) {
            return ToolResult.failure("Please provide the question or statement to analyze.");
        }

        String prompt = buildAdvicePrompt(question, mode, topic);
        String advice = callGeminiText(prompt);

        if (advice == null || advice.isBlank()) {
            advice = "• Be confident, concise, and structured.\n• Provide a high-level summary before diving into details.\n• Highlight practical tradeoffs and metric-driven impact.";
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "whisper");
        data.put("mode", mode);
        data.put("question", question);
        data.put("advice", advice);
        data.put("timestamp", System.currentTimeMillis());

        Map<String, Object> uiAction = Map.of(
                "action", "UPDATE_WHISPER_FEED",
                "item", data);

        return ToolResult.success(
                "Whisper advice ready for question: \""
                        + (question.length() > 40 ? question.substring(0, 40) + "..." : question) + "\"",
                data,
                uiAction);
    }

    private ToolResult handleSolveScreen(String query, String mode) {
        BufferedImage capture = captureDesktopScreen();
        if (capture == null) {
            return ToolResult.failure("Could not capture desktop screen. Ensure display permissions are active.");
        }

        String base64Jpg = encodeAndScaleImage(capture, 1280, 720);
        if (base64Jpg == null) {
            return ToolResult.failure("Failed to encode screen capture.");
        }

        String visionPrompt = "You are JARVIS operating as a live stealth interview co-pilot. Inspect this screen capture:\n"
                + "1. IDENTIFY: If this is a coding challenge (LeetCode, HackerRank, CodeSignal), math problem, or system architecture prompt, identify the core problem title and category (e.g. Dynamic Programming, Two Pointers, Trie, Kafka vs RabbitMQ).\n"
                + "2. TALKING POINTS: 3 rapid, high-confidence points the candidate should say verbally to impress the interviewer before writing code.\n"
                + "3. COMPLEXITY: Explicit Time and Space Complexity (Big-O).\n"
                + "4. OPTIMAL CODE: Clean, production-ready code solution with edge cases handled.\n"
                + "5. PITFALLS: 2 common mistakes to explicitly warn against.\n"
                + "Keep your response sharp, highly scannable on a teleprompter, formatted with markdown headers and code blocks.";

        if (query != null && !query.isBlank()) {
            visionPrompt += "\nCandidate note: " + query;
        }

        String solution = callGeminiVision(visionPrompt, base64Jpg);
        if (solution == null || solution.isBlank()) {
            solution = "Unable to extract problem clearly from screen. Please ensure the problem description is visible in the foreground.";
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "screen_solve");
        data.put("mode", mode);
        data.put("solution", solution);
        data.put("timestamp", System.currentTimeMillis());

        Map<String, Object> uiAction = Map.of(
                "action", "UPDATE_WHISPER_FEED",
                "item", data);

        return ToolResult.success(
                "Screen problem analyzed. Stealth solution published to your teleprompter HUD.",
                data,
                uiAction);
    }

    private ToolResult handleGenerateMinutes(String query, String notes, String topic) {
        String prompt = "You are JARVIS creating an executive meeting briefing and follow-up email.\n"
                + "Context: " + (topic != null ? topic : "Meeting Discussion") + "\n"
                + "Meeting Notes & Highlights:\n"
                + (notes != null ? notes
                        : (query != null ? query
                                : "Discussion regarding project milestones, deliverables, and timeline."))
                + "\n\n"
                + "Produce:\n"
                + "1. EXECUTIVE SUMMARY (3 sentences max)\n"
                + "2. KEY DECISIONS & AGREEMENTS\n"
                + "3. ACTION ITEMS (Owner, Task, Target Deadline)\n"
                + "4. PROFESSIONAL FOLLOW-UP EMAIL DRAFT (Ready to send to participants with subject line)";

        String minutes = callGeminiText(prompt);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "minutes");
        data.put("minutes", minutes);
        data.put("timestamp", System.currentTimeMillis());

        Map<String, Object> uiAction = Map.of(
                "action", "UPDATE_WHISPER_FEED",
                "item", data);

        return ToolResult.success(
                "Executive meeting minutes and follow-up email draft generated.",
                data,
                uiAction);
    }

    private String buildAdvicePrompt(String question, String mode, String topic) {
        if ("sales".equalsIgnoreCase(mode)) {
            return "You are JARVIS acting as a live sales & negotiation co-pilot.\n"
                    + "Client Question / Objection: \"" + question + "\"\n"
                    + "Context: " + topic + "\n\n"
                    + "Provide:\n"
                    + "1. IMMEDIATE VALIDATION (One sentence acknowledging their concern warmly without conceding price)\n"
                    + "2. VALUE RE-FRAME (2 punchy bullet points proving ROI, competitive edge, or risk mitigation)\n"
                    + "3. CLOSING POWER QUESTION (A smart question to regain control of the conversation)";
        } else if ("meeting".equalsIgnoreCase(mode)) {
            return "You are JARVIS acting as an executive meeting co-pilot.\n"
                    + "Discussion Topic / Question: \"" + question + "\"\n"
                    + "Context: " + topic + "\n\n"
                    + "Provide:\n"
                    + "1. DIRECT ANSWER & STRATEGIC RECOMMENDATION (2 concise bullet points)\n"
                    + "2. RISK OR TRADE-OFF TO FLAG\n"
                    + "3. PROPOSED NEXT ACTION ITEM";
        } else {
            // Default: Interview Mode
            return "You are JARVIS acting as a live stealth interview co-pilot for " + topic + ".\n"
                    + "Interviewer Question: \"" + question + "\"\n\n"
                    + "Format for rapid teleprompter reading:\n"
                    + "1. ELEVATOR PITCH: Direct 1-sentence answer showing senior mastery.\n"
                    + "2. 3 CORE TALKING POINTS: Using the STAR method (Situation, Task, Action, Result) or Technical Deep-Dive.\n"
                    + "3. PRO-TIP / ARCHITECTURAL TRADEOFF: A subtle metric, scalability nuance, or edge case that makes the candidate sound like a top 1% engineer.";
        }
    }

    private BufferedImage captureDesktopScreen() {
        try {
            System.setProperty("java.awt.headless", "false");
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Rectangle screenRect = new Rectangle(screenSize);
            Robot robot = new Robot();
            return robot.createScreenCapture(screenRect);
        } catch (Exception e) {
            log.warn("[MeetingCopilot] Robot screen capture failed: {}", e.getMessage());
            return null;
        }
    }

    private String encodeAndScaleImage(BufferedImage original, int maxWidth, int maxHeight) {
        try {
            int w = original.getWidth();
            int h = original.getHeight();
            double scale = Math.min((double) maxWidth / w, (double) maxHeight / h);
            if (scale > 1.0)
                scale = 1.0;

            int targetW = (int) (w * scale);
            int targetH = (int) (h * scale);

            BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, targetW, targetH, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(scaled, "jpg", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("[MeetingCopilot] Image scale error: {}", e.getMessage());
            return null;
        }
    }

    private String callGeminiText(String prompt) {
        try {
            JarvisSettings settings = settingsService.getSettings("default");
            String apiKey = settings != null ? settings.getApiKey() : null;
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = System.getenv("GEMINI_API_KEY");
            }
            if (apiKey == null || apiKey.isBlank()) {
                return "Gemini API key is required to generate real-time whisper advice.";
            }

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key="
                    + apiKey;

            ObjectNode body = mapper.createObjectNode();
            ArrayNode contents = mapper.createArrayNode();
            ObjectNode turn = mapper.createObjectNode();
            ArrayNode parts = mapper.createArrayNode();
            ObjectNode textPart = mapper.createObjectNode();
            textPart.put("text", prompt);
            parts.add(textPart);
            turn.set("parts", parts);
            contents.add(turn);
            body.set("contents", contents);

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(20))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode resJson = mapper.readTree(response.body());
                JsonNode candidates = resJson.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    return candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                }
            } else {
                log.warn("[MeetingCopilot] Gemini API error ({}): {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[MeetingCopilot] Text generation error: {}", e.getMessage());
        }
        return null;
    }

    private String callGeminiVision(String prompt, String base64Jpg) {
        try {
            JarvisSettings settings = settingsService.getSettings("default");
            String apiKey = settings != null ? settings.getApiKey() : null;
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = System.getenv("GEMINI_API_KEY");
            }
            if (apiKey == null || apiKey.isBlank()) {
                return "Gemini API key required for visual screen solving.";
            }

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key="
                    + apiKey;

            ObjectNode body = mapper.createObjectNode();
            ArrayNode contents = mapper.createArrayNode();
            ObjectNode turn = mapper.createObjectNode();
            ArrayNode parts = mapper.createArrayNode();

            ObjectNode textPart = mapper.createObjectNode();
            textPart.put("text", prompt);
            parts.add(textPart);

            ObjectNode imgPart = mapper.createObjectNode();
            ObjectNode inlineData = mapper.createObjectNode();
            inlineData.put("mimeType", "image/jpeg");
            inlineData.put("data", base64Jpg);
            imgPart.set("inlineData", inlineData);
            parts.add(imgPart);

            turn.set("parts", parts);
            contents.add(turn);
            body.set("contents", contents);

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(25))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode resJson = mapper.readTree(response.body());
                JsonNode candidates = resJson.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    return candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                }
            } else {
                log.warn("[MeetingCopilot] Gemini Vision error ({}): {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[MeetingCopilot] Vision error: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public boolean isEffectful() {
        return false;
    }
}
