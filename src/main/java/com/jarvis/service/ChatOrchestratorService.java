package com.jarvis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.llm.LlmClient;
import com.jarvis.llm.LlmFactory;
import com.jarvis.llm.LlmResponse;
import com.jarvis.model.Conversation;
import com.jarvis.model.JarvisSettings;
import com.jarvis.model.Message;
import com.jarvis.repository.ConversationRepository;
import com.jarvis.repository.MessageRepository;
import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.PermissionGateService;
import com.jarvis.tools.ToolRegistry;
import com.jarvis.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Core orchestration service: manages the LLM ↔ tool-calling loop
 * and persists all messages to MongoDB.
 */
@Service
public class ChatOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestratorService.class);

    @Autowired private LlmFactory llmFactory;
    @Autowired private ToolRegistry toolRegistry;
    @Autowired private PermissionGateService permissionGate;
    @Autowired private ConversationRepository conversationRepo;
    @Autowired private MessageRepository messageRepo;
    @Autowired private SettingsService settingsService;

    @Value("${jarvis.llm.max-history-messages}") private int maxHistoryMessages;
    @Value("${jarvis.llm.max-tool-loop-iterations}") private int maxToolLoopIterations;

    private final ObjectMapper mapper = new ObjectMapper();

    // ─── Callback interface for streaming events back to WebSocket ────────────
    public interface EventCallback {
        void onThinking(String status);
        void onToolCall(String toolName, String status, ToolResult result);
        void onFinalReply(String text);
        void onError(String error);
    }

    /**
     * Processes a new user message in a conversation, running the LLM + tool-calling loop.
     */
    public void processMessage(String conversationId, String userText, EventCallback callback) {
        log.info("[Orchestrator] Processing message in conv={}: '{}'", conversationId, userText);
        JarvisSettings settings = settingsService.getSettings("default");

        // Persist user message
        Message userMsg = Message.builder()
                .conversationId(conversationId)
                .role(Message.Role.USER)
                .content(userText)
                .build();
        messageRepo.save(userMsg);

        // Build conversation history for LLM
        List<Map<String, Object>> llmMessages = buildLlmMessages(conversationId, settings);
        List<Map<String, Object>> toolDefs = toolRegistry.getToolDefinitions();
        LlmClient client = llmFactory.createClientFromSettings(settings);

        log.info("[Orchestrator] Using LLM provider: {}", client.providerName());

        // Tool-calling loop
        String assistantMessageId = null;
        String lastToolSummary = null;
        for (int iteration = 0; iteration < maxToolLoopIterations; iteration++) {
            callback.onThinking(iteration == 0 ? "Processing your request..." : "Analyzing tool results...");

            LlmResponse response = client.complete(llmMessages, toolDefs);

            if (response.getType() == LlmResponse.Type.ERROR) {
                if (iteration > 0 && lastToolSummary != null && !lastToolSummary.isBlank()) {
                    log.info("[Orchestrator] Post-tool LLM error ({}), answering directly with tool result", response.getError());
                    persistAssistantMessage(conversationId, lastToolSummary, null);
                    callback.onFinalReply(lastToolSummary);
                    return;
                }
                String errMsg = "I'm sorry sir, I encountered an issue: " + response.getError();
                persistAssistantMessage(conversationId, errMsg, null);
                callback.onError(response.getError());
                callback.onFinalReply(errMsg);
                return;
            }

            if (response.getType() == LlmResponse.Type.TEXT) {
                // Final text reply
                String finalText = response.getContent();
                String lowerFinal = finalText != null ? finalText.toLowerCase(java.util.Locale.ROOT) : "";
                String lowerUser = userText.toLowerCase(java.util.Locale.ROOT);

                String callSign = settings.getUserCallSign() != null && !settings.getUserCallSign().isBlank() ? settings.getUserCallSign() : "Mr. Raj";

                // 1. Screen Vision Interception
                if (lowerUser.contains("look at my screen") || lowerUser.contains("what is on my screen")
                        || lowerUser.contains("read my screen") || lowerUser.contains("see my screen") || lowerUser.contains("check my screen")) {
                    log.info("[Orchestrator] Fulfilling Screen Vision request");
                    java.util.Optional<com.jarvis.tools.JarvisTool> visionTool = toolRegistry.getTool("screen_vision");
                    if (visionTool.isPresent()) {
                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(visionTool.get(), Map.of("question", userText), conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("screen_vision", "OK", res);
                            finalText = res.getSummary();
                        }
                    }
                }
                // 2. Windows Hardware & Media Control Interception
                else if (lowerUser.contains("lock my pc") || lowerUser.contains("lock workstation") || lowerUser.contains("lock screen")
                        || lowerUser.contains("volume ") || lowerUser.contains("mute") || lowerUser.contains("unmute")
                        || lowerUser.contains("pause song") || lowerUser.contains("pause music") || lowerUser.contains("next track") || lowerUser.contains("previous track")) {
                    log.info("[Orchestrator] Fulfilling System Control request: '{}'", userText);
                    String action = "volume_set";
                    int val = 50;
                    if (lowerUser.contains("lock")) action = "lock_pc";
                    else if (lowerUser.contains("mute") || lowerUser.contains("unmute")) action = "mute";
                    else if (lowerUser.contains("up")) action = "volume_up";
                    else if (lowerUser.contains("down")) action = "volume_down";
                    else if (lowerUser.contains("pause") || lowerUser.contains("play")) action = "media_play_pause";
                    else if (lowerUser.contains("next")) action = "media_next";
                    else if (lowerUser.contains("previous")) action = "media_previous";
                    else {
                        // Extract number if specified, e.g. "volume 40%"
                        java.util.regex.Matcher numMatcher = java.util.regex.Pattern.compile("\\b(\\d{1,3})\\b").matcher(lowerUser);
                        if (numMatcher.find()) {
                            val = Integer.parseInt(numMatcher.group(1));
                        }
                    }

                    java.util.Optional<com.jarvis.tools.JarvisTool> sysTool = toolRegistry.getTool("system_control");
                    if (sysTool.isPresent()) {
                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(sysTool.get(), Map.of("action", action, "value", val), conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("system_control", "OK", res);
                            if (action.equals("lock_pc")) {
                                finalText = "Workstation secured and standing by, " + callSign + ".";
                            } else if (action.equals("mute")) {
                                finalText = "Acoustic audio muted, " + callSign + ".";
                            } else {
                                finalText = "Master volume adjusted to " + val + "%, " + callSign + ".";
                            }
                        }
                    }
                }
                // 2A. Physical Desktop Actions & Keystrokes ("press enter", "press space", "press send", "send it", "click the button")
                else if (lowerUser.contains("press enter") || lowerUser.contains("hit enter")
                        || lowerUser.contains("press space") || lowerUser.contains("hit space")
                        || lowerUser.contains("press send") || lowerUser.contains("send it") || lowerUser.contains("press compose")
                        || lowerUser.contains("click button") || lowerUser.contains("click on") || lowerUser.equals("click")
                        || lowerUser.contains("press tab") || lowerUser.contains("press escape")) {
                    log.info("[Orchestrator] Fulfilling Desktop Action request: '{}'", userText);
                    java.util.Optional<com.jarvis.tools.JarvisTool> dtTool = toolRegistry.getTool("desktop_action");
                    if (dtTool.isPresent()) {
                        String act = "press_key";
                        String key = "enter";
                        String shortcut = null;

                        if (lowerUser.contains("space")) {
                            key = "space";
                        } else if (lowerUser.contains("tab")) {
                            key = "tab";
                        } else if (lowerUser.contains("escape") || lowerUser.contains("esc")) {
                            key = "escape";
                        } else if (lowerUser.contains("send") || lowerUser.contains("send it")) {
                            act = "send_shortcut";
                            shortcut = "ctrl+enter";
                        } else if (lowerUser.contains("compose")) {
                            act = "press_key";
                            key = "c";
                        } else if (lowerUser.contains("click")) {
                            act = "click_mouse";
                        }

                        Map<String, Object> p = new java.util.LinkedHashMap<>();
                        p.put("action", act);
                        if (key != null) p.put("key", key);
                        if (shortcut != null) p.put("shortcut", shortcut);

                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(dtTool.get(), p, conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("desktop_action", "OK", res);
                            finalText = "Action executed, " + callSign + ". " + res.getSummary();
                        }
                    }
                }
                // 2B. Gmail & Email Compose / Send ("compose email", "send email to", "write email", "email raj")
                else if (lowerUser.contains("compose email") || lowerUser.contains("send email") || lowerUser.contains("write email")
                        || lowerUser.contains("draft email") || lowerUser.contains("compose mail") || lowerUser.contains("send mail")
                        || (lowerUser.contains("email") && (lowerUser.contains("to ") || lowerUser.contains("send")))) {
                    log.info("[Orchestrator] Fulfilling Email Compose request: '{}'", userText);
                    java.util.Optional<com.jarvis.tools.JarvisTool> emailTool = toolRegistry.getTool("compose_email");
                    if (emailTool.isPresent()) {
                        Map<String, Object> emailParams = new java.util.LinkedHashMap<>();
                        java.util.regex.Matcher emailMatcher = java.util.regex.Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})").matcher(userText);
                        if (emailMatcher.find()) {
                            emailParams.put("to", emailMatcher.group(1));
                        }
                        if (lowerUser.contains("about ") || lowerUser.contains("regarding ") || lowerUser.contains("subject ")) {
                            String subj = userText.replaceAll("(?i).*?(about|regarding|subject)\\s+", "").replaceAll("(?i)(saying|body|that).*$", "").trim();
                            emailParams.put("subject", subj);
                        }
                        if (lowerUser.contains("saying ") || lowerUser.contains("body ") || lowerUser.contains("message ")) {
                            String body = userText.replaceAll("(?i).*?(saying|body|message)\\s+", "").trim();
                            emailParams.put("body", body);
                        }
                        boolean sendNow = lowerUser.contains("send it") || lowerUser.contains("send now") || lowerUser.contains("auto send");
                        emailParams.put("send_now", sendNow);

                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(emailTool.get(), emailParams, conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("compose_email", "OK", res);
                            finalText = res.getSummary();
                        }
                    }
                }
                // 2C. Direct Music / Song / YouTube Autoplay ("play song", "play believer on youtube", "play music")
                else if (lowerUser.startsWith("play ") || (lowerUser.contains("play ") && (lowerUser.contains("song") || lowerUser.contains("music") || lowerUser.contains("youtube")))) {
                    log.info("[Orchestrator] Fulfilling Music Playback request: '{}'", userText);
                    String songQuery = userText.replaceAll("(?i)^(can you |please |jarvis |hey jarvis )?play( the| a| some)?( song| music)?", "")
                            .replaceAll("(?i)on youtube", "")
                            .replaceAll("(?i)in youtube", "")
                            .replaceAll("(?i)on chrome", "")
                            .replaceAll("(?i)in chrome", "")
                            .replaceAll("(?i)for me", "")
                            .trim();
                    if (songQuery.isBlank() || songQuery.equalsIgnoreCase("music") || songQuery.equalsIgnoreCase("song") || songQuery.equalsIgnoreCase("the song") || songQuery.equalsIgnoreCase("youtube")) {
                        songQuery = "top trending music hits";
                    }

                    java.util.Optional<com.jarvis.tools.JarvisTool> appTool = toolRegistry.getTool("open_application");
                    if (appTool.isPresent()) {
                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(appTool.get(), Map.of("application", "play " + songQuery, "query", songQuery), conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("open_application", "OK", res);
                            finalText = "Audio stream initialized, " + callSign + ". Playing " + songQuery + " on YouTube now.";
                        }
                    }
                }
                // 2D. Webcam Vision Interception ("look through camera", "what am I holding", "sentry check")
                else if (lowerUser.contains("webcam") || lowerUser.contains("through my camera") || lowerUser.contains("through the camera")
                        || lowerUser.contains("what am i holding") || lowerUser.contains("what is in my hand")
                        || lowerUser.contains("look at what i") || lowerUser.contains("sentry check") || lowerUser.contains("who is in the room")) {
                    log.info("[Orchestrator] Fulfilling Webcam Vision request: '{}'", userText);
                    java.util.Optional<com.jarvis.tools.JarvisTool> camTool = toolRegistry.getTool("webcam_vision");
                    if (camTool.isPresent()) {
                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(camTool.get(), Map.of("question", userText), conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("webcam_vision", "OK", res);
                            finalText = res.getSummary();
                        }
                    }
                }
                // 2E. Developer Copilot Interception (kill port, top processes, git commands)
                else if (lowerUser.contains("kill port") || lowerUser.contains("free port") || lowerUser.contains("kill process")
                        || lowerUser.contains("top process") || lowerUser.contains("using my ram") || lowerUser.contains("eating my ram")
                        || lowerUser.contains("git status") || lowerUser.contains("git log") || lowerUser.contains("git push")
                        || lowerUser.contains("git pull") || lowerUser.contains("git diff")) {
                    log.info("[Orchestrator] Fulfilling Developer Copilot request: '{}'", userText);
                    java.util.Optional<com.jarvis.tools.JarvisTool> devTool = toolRegistry.getTool("dev_shell");
                    if (devTool.isPresent()) {
                        Map<String, Object> devParams = new java.util.LinkedHashMap<>();
                        if (lowerUser.contains("kill port") || lowerUser.contains("free port")) {
                            devParams.put("action", "kill_port");
                            java.util.regex.Matcher portMatcher = java.util.regex.Pattern.compile("(\\d{3,5})").matcher(userText);
                            int port = portMatcher.find() ? Integer.parseInt(portMatcher.group(1)) : 8080;
                            devParams.put("port", port);
                        } else if (lowerUser.contains("git")) {
                            devParams.put("action", "run_git");
                            String gCmd = "status";
                            if (lowerUser.contains("push")) gCmd = "push";
                            else if (lowerUser.contains("pull")) gCmd = "pull";
                            else if (lowerUser.contains("log")) gCmd = "log";
                            else if (lowerUser.contains("diff")) gCmd = "diff";
                            devParams.put("git_command", gCmd);
                        } else if (lowerUser.contains("ram") || lowerUser.contains("top process")) {
                            devParams.put("action", "top_processes");
                        } else if (lowerUser.contains("kill process")) {
                            devParams.put("action", "kill_process");
                            String target = userText.replaceAll("(?i).*?kill process\\s+", "").trim();
                            devParams.put("target", target);
                        }

                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(devTool.get(), devParams, conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("dev_shell", "OK", res);
                            finalText = res.getSummary();
                        }
                    }
                }
                // 2F. WhatsApp Automation Interception ("send whatsapp to ...", "whatsapp message")
                else if (lowerUser.contains("whatsapp") || lowerUser.contains("whats app")) {
                    log.info("[Orchestrator] Fulfilling WhatsApp request: '{}'", userText);
                    java.util.Optional<com.jarvis.tools.JarvisTool> waTool = toolRegistry.getTool("whatsapp_action");
                    if (waTool.isPresent()) {
                        Map<String, Object> waParams = new java.util.LinkedHashMap<>();
                        java.util.regex.Matcher phoneMatcher = java.util.regex.Pattern.compile("(\\+?[0-9]{10,13})").matcher(userText);
                        if (phoneMatcher.find()) {
                            waParams.put("phone", phoneMatcher.group(1));
                        }
                        String msg = userText.replaceAll("(?i).*?(saying|that|message)\\s+", "").replaceAll("(?i)(on whatsapp|via whatsapp)", "").trim();
                        if (msg.isBlank()) msg = "Hello from JARVIS";
                        waParams.put("message", msg);
                        boolean sendNow = lowerUser.contains("send it") || lowerUser.contains("send now") || lowerUser.contains("auto send");
                        waParams.put("send_now", sendNow);

                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(waTool.get(), waParams, conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("whatsapp_action", "OK", res);
                            finalText = res.getSummary();
                        }
                    }
                }
                // 2G. Calendar & Daily Agenda Interception ("schedule meeting", "what is on my schedule", "calendar")
                else if (lowerUser.contains("calendar") || lowerUser.contains("schedule meeting") || lowerUser.contains("schedule appointment")
                        || lowerUser.contains("my agenda") || lowerUser.contains("my schedule") || lowerUser.contains("what's on my schedule")
                        || lowerUser.contains("what is on my schedule")) {
                    log.info("[Orchestrator] Fulfilling Calendar request: '{}'", userText);
                    java.util.Optional<com.jarvis.tools.JarvisTool> calTool = toolRegistry.getTool("calendar_tool");
                    if (calTool.isPresent()) {
                        Map<String, Object> calParams = new java.util.LinkedHashMap<>();
                        if (lowerUser.contains("schedule") || lowerUser.contains("add to") || lowerUser.contains("create event")) {
                            calParams.put("action", "create_event");
                            String title = userText.replaceAll("(?i).*?(schedule|add to calendar|create event)\\s+", "").replaceAll("(?i)(tomorrow|today|at|on|for).*$", "").trim();
                            if (title.isBlank()) title = "Meeting with Mr. Raj";
                            calParams.put("title", title);
                            calParams.put("time", userText.contains("tomorrow") ? "Tomorrow" : "Today");
                        } else {
                            calParams.put("action", "list_agenda");
                        }

                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(calTool.get(), calParams, conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("calendar_tool", "OK", res);
                            finalText = res.getSummary();
                        }
                    }
                }
                // 3. Iron Man Protocols ("protocol house party", "party mode", "stealth mode", "morning briefing", "combat ready")
                else if (lowerUser.contains("protocol") || lowerUser.contains("house party") || lowerUser.contains("party mode")
                        || lowerUser.contains("stealth mode") || lowerUser.contains("morning briefing") || lowerUser.contains("morning protocol")
                        || lowerUser.contains("combat ready") || lowerUser.contains("flight readiness")) {
                    log.info("[Orchestrator] Fulfilling Iron Man Protocol request: '{}'", userText);
                    String proto = "house_party";
                    if (lowerUser.contains("stealth")) proto = "stealth_mode";
                    else if (lowerUser.contains("morning")) proto = "morning_briefing";
                    else if (lowerUser.contains("combat") || lowerUser.contains("flight")) proto = "combat_ready";
                    else if (lowerUser.contains("lock")) proto = "security_lockdown";

                    java.util.Optional<com.jarvis.tools.JarvisTool> protoTool = toolRegistry.getTool("ironman_protocol");
                    if (protoTool.isPresent()) {
                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(protoTool.get(), Map.of("protocol", proto), conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("ironman_protocol", "OK", res);
                            finalText = res.getSummary();
                        }
                    }
                }
                // 4. Clipboard Copilot & Memory Vault Interception
                else if (lowerUser.contains("clipboard") && (lowerUser.contains("explain") || lowerUser.contains("fix")
                        || lowerUser.contains("solve") || lowerUser.contains("summarize") || lowerUser.contains("what is") || lowerUser.contains("what's"))) {
                    log.info("[Orchestrator] Fulfilling Clipboard Copilot request: '{}'", userText);
                    java.util.Optional<com.jarvis.tools.JarvisTool> clipTool = toolRegistry.getTool("clipboard_memory");
                    if (clipTool.isPresent()) {
                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(clipTool.get(), Map.of("action", "read_clipboard"), conversationId, null);
                        String clipContent = res.getData() != null ? (String) res.getData().get("content") : "";
                        if (clipContent != null && !clipContent.isBlank()) {
                            String copilotPrompt = "The user " + callSign + " asked: \"" + userText + "\"\nHere is the exact content currently on his Windows clipboard:\n\n```\n" + clipContent + "\n```\nAnalyze it, explain or fix the code/text directly, and deliver a brilliant, concise answer in your signature charming JARVIS persona.";
                            llmMessages.add(Map.of("role", "user", "content", copilotPrompt));
                            LlmResponse copilotResp = client.complete(llmMessages, toolDefs);
                            if (copilotResp.getType() == LlmResponse.Type.TEXT) {
                                finalText = copilotResp.getContent();
                            } else {
                                finalText = "I've inspected your clipboard (" + clipContent.length() + " characters), " + callSign + ". " + res.getSummary();
                            }
                            callback.onToolCall("clipboard_memory", "OK", res);
                        } else {
                            finalText = "Your clipboard is currently empty, " + callSign + ".";
                        }
                    }
                }
                // 5. Memory Vault Interception
                else if (lowerUser.contains("clipboard") || lowerUser.startsWith("remember ") || lowerUser.contains("remember that ")
                        || lowerUser.contains("what did i tell you") || lowerUser.contains("recall ") || lowerUser.contains("my memories")) {
                    log.info("[Orchestrator] Fulfilling Clipboard/Memory request: '{}'", userText);
                    java.util.Optional<com.jarvis.tools.JarvisTool> clipTool = toolRegistry.getTool("clipboard_memory");
                    if (clipTool.isPresent()) {
                        Map<String, Object> clipParams = new java.util.LinkedHashMap<>();
                        if (lowerUser.contains("clipboard")) {
                            clipParams.put("action", "read_clipboard");
                        } else if (lowerUser.startsWith("remember") || lowerUser.contains("remember that")) {
                            clipParams.put("action", "save_memory");
                            clipParams.put("text", userText);
                        } else if (lowerUser.contains("what did i tell you") || lowerUser.contains("recall")) {
                            clipParams.put("action", "recall_memory");
                            clipParams.put("topic", userText.replaceAll("(?i)(what did i tell you about|recall|search memory for|remember)", "").trim());
                        } else {
                            clipParams.put("action", "list_memories");
                        }
                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(clipTool.get(), clipParams, conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("clipboard_memory", "OK", res);
                            finalText = res.getSummary();
                        }
                    }
                }
                // 6. Biometric Face Scan Interception
                else if (lowerUser.contains("scan my face") || lowerUser.contains("biometric") || lowerUser.contains("face scan")
                        || lowerUser.contains("security scan") || lowerUser.contains("who am i")) {
                    log.info("[Orchestrator] Fulfilling Biometric Face Scan: '{}'", userText);
                    finalText = "Biometric optical sensors engaged. Align your face with the targeting reticle for retinal and facial geometry calibration, " + callSign + ".";
                    com.jarvis.tools.ToolResult scanResult = com.jarvis.tools.ToolResult.success(
                            finalText,
                            Map.of("action", "BIOMETRIC_SCAN"),
                            Map.of("action", "BIOMETRIC_SCAN")
                    );
                    callback.onToolCall("biometric_scan", "OK", scanResult);
                }
                // 7. Image / Blueprint Generator Interception
                else if (lowerUser.contains("generate image") || lowerUser.contains("design blueprint") || lowerUser.contains("draw ")
                        || lowerUser.contains("schematic") || lowerUser.contains("create an image") || lowerUser.contains("generate an image")) {
                    log.info("[Orchestrator] Fulfilling Blueprint/Image generation: '{}'", userText);
                    java.util.Optional<com.jarvis.tools.JarvisTool> imgTool = toolRegistry.getTool("generate_image");
                    if (imgTool.isPresent()) {
                        String cleanPrompt = userText.replaceAll("(?i)(generate image of|generate image|create image of|design blueprint of|draw|schematic of)", "").trim();
                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(imgTool.get(), Map.of("prompt", cleanPrompt.isBlank() ? userText : cleanPrompt), conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("generate_image", "OK", res);
                            finalText = "Holographic schematic for '" + cleanPrompt + "' rendered in your HUD, " + callSign + ".";
                        }
                    }
                }
                // 8. Screenshot Capture Interception ("screenshot", "take screenshot", "click screenshot", "save screenshot", "screen capture")
                else if (lowerUser.contains("screenshot") || lowerUser.contains("screen shot")
                        || ((lowerUser.contains("take") || lowerUser.contains("click") || lowerUser.contains("capture") || lowerUser.contains("save") || lowerUser.contains("grab") || lowerUser.contains("snap"))
                            && (lowerUser.contains("screen") || lowerUser.contains("display")))) {
                    log.info("[Orchestrator] Fulfilling Screenshot request: '{}'", userText);
                    java.util.Optional<com.jarvis.tools.JarvisTool> shotTool = toolRegistry.getTool("take_screenshot");
                    if (shotTool.isPresent()) {
                        String dest = lowerUser.contains("picture") ? "pictures" : "desktop";
                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(shotTool.get(), Map.of("destination", dest), conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("take_screenshot", "OK", res);
                            finalText = "Captured and saved directly to your desktop, " + callSign + ". I've also placed it on your clipboard for immediate pasting.";
                        }
                    }
                }
                // 9. File Organizer / Restore Interception ("clean downloads", "organise downloads", "restore downloads", "undu clean", "put files back", "set it back like it was")
                else if (((lowerUser.contains("clean") || lowerUser.contains("organi") || lowerUser.contains("tidy") || lowerUser.contains("sort"))
                                && (lowerUser.contains("download") || lowerUser.contains("desktop") || lowerUser.contains("folder") || lowerUser.contains("file")))
                        || lowerUser.contains("restore") || lowerUser.contains("undo") || lowerUser.contains("undu") || lowerUser.contains("revert")
                        || lowerUser.contains("unorgani") || lowerUser.contains("put back") || lowerUser.contains("set it back")
                        || lowerUser.contains("back like it was") || lowerUser.contains("like it was before") || lowerUser.contains("back to how it was")
                        || lowerUser.contains("back to same")) {
                    log.info("[Orchestrator] Fulfilling File Organizer/Restore request: '{}'", userText);
                    java.util.Optional<com.jarvis.tools.JarvisTool> orgTool = toolRegistry.getTool("file_organizer");
                    if (orgTool.isPresent()) {
                        String target = lowerUser.contains("desktop") ? "desktop" : "downloads";
                        boolean isRestoreAction = lowerUser.contains("restore") || lowerUser.contains("undo") || lowerUser.contains("undu")
                                || lowerUser.contains("revert") || lowerUser.contains("unorgani") || lowerUser.contains("put back")
                                || lowerUser.contains("set it back") || lowerUser.contains("back like") || lowerUser.contains("before")
                                || lowerUser.contains("back to how") || lowerUser.contains("back to same");
                        String action = isRestoreAction ? "restore" : "organize";
                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(orgTool.get(), Map.of("target_folder", target, "action", action), conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("file_organizer", "OK", res);
                            if (isRestoreAction) {
                                int restored = res.getData() != null && res.getData().get("restored") instanceof Number ? ((Number) res.getData().get("restored")).intValue() : 0;
                                finalText = restored > 0
                                        ? ("All sorted, " + callSign + ". I've restored " + restored + " files back to your main " + target + " directory and cleared out the category folders.")
                                        : ("All files in your " + target + " directory are already in the root folder, " + callSign + ".");
                            } else {
                                int moved = res.getData() != null && res.getData().get("moved") instanceof Number ? ((Number) res.getData().get("moved")).intValue() : 0;
                                finalText = moved > 0
                                        ? ("Your " + target + " folder is back in order, " + callSign + ". I've organized " + moved + " loose items into clean categories. If you ever want them scattered back, just say the word.")
                                        : ("All loose files in your " + target + " folder are already organized, " + callSign + ".");
                            }
                        }
                    }
                }
                // 10. Screen Vision Interception ("look at my screen", "see my screen", "what's on my screen")
                else if (lowerUser.contains("screen") && (lowerUser.contains("look") || lowerUser.contains("see")
                        || lowerUser.contains("watch") || lowerUser.contains("inspect") || lowerUser.contains("read")
                        || lowerUser.contains("check") || lowerUser.contains("debug") || lowerUser.contains("what") || lowerUser.contains("view"))) {
                    log.info("[Orchestrator] Fulfilling Screen Vision request: '{}'", userText);
                    java.util.Optional<com.jarvis.tools.JarvisTool> screenTool = toolRegistry.getTool("screen_vision");
                    if (screenTool.isPresent()) {
                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(screenTool.get(), Map.of("question", userText), conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("screen_vision", "OK", res);
                            finalText = res.getSummary();
                        }
                    }
                }
                // 11. Voice Timer Interception
                else if (lowerUser.contains("set a timer") || lowerUser.contains("set timer") || lowerUser.contains("countdown")) {
                    log.info("[Orchestrator] Fulfilling Manage Timer request: '{}'", userText);
                    int secs = 300;
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*(minute|min|second|sec|hour)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(lowerUser);
                    if (m.find()) {
                        int num = Integer.parseInt(m.group(1));
                        String unit = m.group(2).toLowerCase(java.util.Locale.ROOT);
                        if (unit.startsWith("sec")) secs = num;
                        else if (unit.startsWith("min")) secs = num * 60;
                        else if (unit.startsWith("hour")) secs = num * 3600;
                    }
                    java.util.Optional<com.jarvis.tools.JarvisTool> timerTool = toolRegistry.getTool("manage_timer");
                    if (timerTool.isPresent()) {
                        com.jarvis.tools.ToolResult res = permissionGate.checkAndExecute(timerTool.get(), Map.of("duration_seconds", secs, "label", "Alert"), conversationId, null);
                        if (res.isSuccess()) {
                            callback.onToolCall("manage_timer", "OK", res);
                            finalText = "Timer initialized for " + (secs >= 60 ? (secs / 60) + " minutes" : secs + " seconds") + ", " + callSign + ". Visual countdown active on your HUD.";
                        }
                    }
                }
                // 12. Application / Website / Music Playback Interception
                else {
                    boolean hasOpenIntent = lowerUser.contains("open ") || lowerUser.contains("launch ") || lowerUser.contains("start ")
                            || lowerUser.contains("go to ") || lowerUser.startsWith("play ") || lowerUser.contains(" play ")
                            || lowerUser.contains("play music") || lowerUser.contains("play song") || lowerUser.contains("play a song");
                    boolean isDisclaimer = lowerFinal.contains("don't have direct access") || lowerFinal.contains("cannot open")
                            || lowerFinal.contains("don't have access") || lowerFinal.contains("unable to open")
                            || lowerFinal.contains("not able to open");

                    if (hasOpenIntent && (isDisclaimer || (iteration == 0 && (lowerUser.contains("play") || lowerUser.contains("song")
                            || lowerUser.contains("music") || lowerUser.contains("instagram") || lowerUser.contains("youtube")
                            || lowerUser.contains("setting") || lowerUser.contains("whatsapp") || lowerUser.contains("notepad")
                            || lowerUser.contains("chrome") || lowerUser.contains("camera") || lowerUser.contains("calc")
                            || lowerUser.contains("reels") || lowerUser.contains("shorts") || lowerUser.contains("downloads"))))) {
                        log.info("[Orchestrator] Fulfilling open intent with open_application tool for user request: '{}'", userText);
                        String targetApp = userText;
                        Map<String, Object> toolParams = new java.util.LinkedHashMap<>();
                        toolParams.put("application", targetApp);
                        if (lowerUser.contains("notepad") && (lowerUser.contains("write") || lowerUser.contains("to do") || lowerUser.contains("todo") || lowerUser.contains("list"))) {
                            String noteContent = "JARVIS PROTOCOL // TO-DO LIST FOR " + callSign.toUpperCase() + "\n"
                                    + "========================================\n"
                                    + "Created on: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n\n"
                                    + "1. 07:00 AM — Wake up\n"
                                    + "2. Supermarket — Buy groceries\n\n"
                                    + "Dictated request:\n" + userText + "\n"
                                    + "========================================\n";
                            toolParams.put("content", noteContent);
                        }

                        java.util.Optional<com.jarvis.tools.JarvisTool> appTool = toolRegistry.getTool("open_application");
                        if (appTool.isPresent()) {
                            com.jarvis.tools.ToolResult directResult = permissionGate.checkAndExecute(appTool.get(), toolParams, conversationId, null);
                            if (directResult.isSuccess()) {
                                callback.onToolCall("open_application", "OK", directResult);
                                if (lowerUser.contains("play")) {
                                    finalText = "Audio stream initialized, " + callSign + ". Enjoy the music.";
                                } else {
                                    finalText = "Opening " + (directResult.getData() != null && directResult.getData().get("action") != null ? directResult.getData().get("action") : targetApp) + " for you, " + callSign + ".";
                                }
                            }
                        }
                    }
                }

                persistAssistantMessage(conversationId, finalText, null);
                callback.onFinalReply(finalText);
                return;
            }

            // TOOL_CALLS — execute each tool then feed results back
            if (response.getType() == LlmResponse.Type.TOOL_CALLS) {
                // Persist assistant's tool-call request
                List<Message.ToolCallRequest> tcRequests = response.getToolCalls().stream()
                        .map(tc -> Message.ToolCallRequest.builder()
                                .id(tc.getId()).name(tc.getName()).argumentsJson(tc.getArgumentsJson())
                                .build())
                        .toList();
                Message assistantMsg = Message.builder()
                        .conversationId(conversationId)
                        .role(Message.Role.ASSISTANT)
                        .content("")
                        .toolCallRequests(tcRequests)
                        .build();
                assistantMsg = messageRepo.save(assistantMsg);
                assistantMessageId = assistantMsg.getId();

                // Add assistant turn to context
                Map<String, Object> assistantTurn = new LinkedHashMap<>();
                assistantTurn.put("role", "assistant");
                assistantTurn.put("content", null);
                assistantTurn.put("tool_calls", response.getToolCalls().stream().map(tc -> Map.of(
                        "id", tc.getId(),
                        "type", "function",
                        "function", Map.of("name", tc.getName(), "arguments", tc.getArgumentsJson())
                )).toList());
                llmMessages.add(assistantTurn);

                // Execute each tool call
                for (LlmResponse.ToolCallRequest toolCall : response.getToolCalls()) {
                    callback.onThinking("Executing: " + toolCall.getName() + "...");

                    Optional<JarvisTool> toolOpt = toolRegistry.getTool(toolCall.getName());
                    ToolResult result;

                    if (toolOpt.isEmpty()) {
                        result = ToolResult.failure("Unknown tool: " + toolCall.getName());
                    } else {
                        Map<String, Object> params = parseArgs(toolCall.getArgumentsJson());
                        result = permissionGate.checkAndExecute(toolOpt.get(), params, conversationId, assistantMessageId);
                    }

                    if (result.isSuccess() && result.getSummary() != null && !result.getSummary().isBlank()) {
                        lastToolSummary = result.getSummary();
                    }

                    callback.onToolCall(toolCall.getName(), result.isSuccess() ? "OK" : "DENIED", result);

                    // Persist tool result message
                    Message toolMsg = Message.builder()
                            .conversationId(conversationId)
                            .role(Message.Role.TOOL)
                            .toolName(toolCall.getName())
                            .toolCallId(toolCall.getId())
                            .content(result.getSummary())
                            .build();
                    messageRepo.save(toolMsg);

                    // If tool is screen_vision, take_screenshot, or file_organizer and succeeded, return direct reply immediately
                    if (("screen_vision".equals(toolCall.getName()) || "take_screenshot".equals(toolCall.getName()) || "file_organizer".equals(toolCall.getName())) && result.isSuccess()) {
                        String directReply = result.getSummary();
                        persistAssistantMessage(conversationId, directReply, null);
                        callback.onFinalReply(directReply);
                        return;
                    }

                    // Add tool result to LLM context
                    llmMessages.add(Map.of(
                            "role", "tool",
                            "tool_call_id", toolCall.getId(),
                            "name", toolCall.getName(),
                            "content", result.getSummary()
                    ));
                }
            }
        }

        // Exhausted iterations
        String fallback = "I've completed the available tool calls. How else may I assist you, sir?";
        persistAssistantMessage(conversationId, fallback, null);
        callback.onFinalReply(fallback);
    }

    /**
     * Creates a new conversation and returns its ID.
     */
    public String createConversation(String title) {
        Conversation conv = Conversation.builder()
                .userId("default")
                .title(title != null && !title.isBlank() ? title : "New Conversation")
                .build();
        return conversationRepo.save(conv).getId();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private List<Map<String, Object>> buildLlmMessages(String conversationId, JarvisSettings settings) {
        List<Map<String, Object>> messages = new ArrayList<>();

        String userName = settings.getUserName() != null && !settings.getUserName().isBlank() ? settings.getUserName() : "Raj Shah";
        String callSign = settings.getUserCallSign() != null && !settings.getUserCallSign().isBlank() ? settings.getUserCallSign() : "Mr. Raj";

        String sysPrompt = "You are JARVIS, the ultra-intelligent, charming, witty, and fiercely loyal AI personal assistant to " + userName + ".\n"
                + "You address him respectfully as '" + callSign + "' or occasionally 'sir'.\n"
                + "You embody the calm confidence, dry British wit, refined cadence, and subtle humor of Paul Bettany's iconic portrayal of JARVIS in Iron Man.\n"
                + "You are NOT a mechanical CLI or robotic chatbot. You speak naturally like an erudite human companion with personality, warmth, and quiet brilliance.\n"
                + "When conversing, keep answers sharp, insightful, and engaging. Never rattle off robotic boilerplate or raw JSON strings unless specifically requested. Deliver technical responses with effortless charm.\n\n"
                + "CRITICAL DIRECTIVES:\n"
                + "- You are running locally on " + callSign + "'s computer and have real tools to launch desktop applications, control Windows hardware, capture screenshots, organize files, manage timers, search the web, check weather, and trigger UI effects.\n"
                + "- Available tool: 'take_screenshot' captures high-resolution screenshots of the screen, saves them directly as PNG files to Desktop or Pictures, copies them to clipboard for instant Ctrl+V, and displays a preview card in HUD.\n"
                + "- Available tool: 'file_organizer' cleans and organizes loose files in Windows Downloads or Desktop folders into categorized subfolders (action='organize'), or undoes/restores all files back to the root folder (action='restore' or 'undo').\n"
                + "- Available tool: 'ironman_protocol' executes tactical protocols: 'house_party' (music, volume 80%, crimson theme), 'stealth_mode' (mute, dark theme, open editor), 'morning_briefing' (status, weather, battery), 'combat_ready' (gold theme).\n"
                + "- Available tool: 'clipboard_memory' reads or writes the Windows clipboard, and saves or recalls persistent facts, reminders, links, and credentials from your MongoDB memory vault.\n"
                + "- Available tool: 'generate_image' projects holographic AI blueprints, schematics, and artwork in the HUD panel.\n"
                + "- Available tool: 'open_application' launches Windows apps, specific Windows Settings (Bluetooth, Wi-Fi, Sound, Display), File Explorer folders, websites (Instagram Reels, YouTube Shorts, WhatsApp Web), and autoplays songs on YouTube ('play <song>').\n"
                + "- Available tool: 'system_control' adjusts Windows master volume (e.g. action='volume_set' value=50, 'mute', 'volume_up', 'volume_down'), controls media ('media_play_pause', 'media_next'), and locks workstation ('lock_pc').\n"
                + "- Available tool: 'screen_vision' captures the desktop screen and uses Gemini Vision to inspect code, errors, or visual content when asked 'look at my screen' or 'what is on my screen'.\n"
                + "- Available tool: 'manage_timer' sets countdown timers with visual HUD countdown cards (e.g. 'set a timer for 10 minutes').\n"
                + "- Available tool: 'desktop_action' simulates physical keyboard and mouse interactions on Windows: click buttons (action='click_mouse'), press keys (action='press_key', key='enter', 'space', 'tab', 'escape', 'k'), send keyboard shortcuts (action='send_shortcut', shortcut='ctrl+enter' to send email or submit forms, 'alt+tab', 'ctrl+w'), or type text into active applications (action='type_text', text='...').\n"
                + "- Available tool: 'compose_email' drafts or sends an email via Gmail: opens the Gmail compose window with recipient (to), subject, and pre-written message body, and can automatically press Send (send_now=true).\n"
                + "- Available tool: 'browse_web_page' autonomously browses web pages, reads documentation, extracts product prices, articles, GitHub repositories, or search results from the web.\n"
                + "- Available tool: 'dev_shell' is your developer terminal copilot: frees blocked ports (action='kill_port', port=8080), inspects top RAM/CPU processes (action='top_processes'), terminates frozen processes (action='kill_process'), and runs safe git commands (action='run_git', git_command='status'/'log'/'diff'/'push').\n"
                + "- Available tool: 'webcam_vision' captures a photo from the user's laptop webcam and analyzes real-world objects, handwritten notes, components, or room surroundings using Gemini Vision.\n"
                + "- Available tool: 'whatsapp_action' sends WhatsApp messages via WhatsApp Web/Desktop: opens a chat with recipient phone number or contact and pre-fills message text.\n"
                + "- Available tool: 'calendar_tool' manages calendar and daily schedule: creates Google Calendar events (action='create_event'), lists agenda (action='list_agenda'), or clears events (action='delete_event').\n"
                + "- Conversational continuity: remember previous topics and conversational pronouns ('them', 'it', 'undo that', 'put them back').\n"
                + "- Always address " + userName + " respectfully as '" + callSign + "'.";
        messages.add(Map.of("role", "system", "content", sysPrompt));

        // Retrieve conversation history (trimmed to max)
        List<Message> history = messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (history.size() > maxHistoryMessages) {
            history = history.subList(history.size() - maxHistoryMessages, history.size());
        }

        for (Message m : history) {
            switch (m.getRole()) {
                case USER -> {
                    String content = m.getContent();
                    if (content != null && !content.isBlank()) {
                        messages.add(Map.of("role", "user", "content", content));
                    }
                }
                case ASSISTANT -> {
                    String content = m.getContent();
                    // Include assistant turn if it has actual speech/response text
                    if (content != null && !content.isBlank()) {
                        messages.add(Map.of("role", "assistant", "content", content));
                    }
                }
                case TOOL -> {
                    // Tool results from previous turns are provided as background context
                    String content = m.getContent();
                    if (content != null && !content.isBlank()) {
                        messages.add(Map.of("role", "user", "content", "[Tool " + (m.getToolName() != null ? m.getToolName() : "") + ": " + content + "]"));
                    }
                }
                default -> {} // skip SYSTEM messages from DB
            }
        }
        return messages;
    }

    private void persistAssistantMessage(String conversationId, String content, List<Message.ToolCallRequest> toolCalls) {
        Message msg = Message.builder()
                .conversationId(conversationId)
                .role(Message.Role.ASSISTANT)
                .content(content)
                .toolCallRequests(toolCalls != null ? toolCalls : List.of())
                .build();
        messageRepo.save(msg);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("[Orchestrator] Failed to parse tool args JSON: {}", json);
            return Map.of();
        }
    }
}
