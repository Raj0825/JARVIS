package com.jarvis.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.service.ChatOrchestratorService;
import com.jarvis.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WebSocket handler for the Jarvis real-time voice/chat channel (/ws/jarvis).
 *
 * Message protocol (JSON):
 *   Client → Server:
 *     { "type": "CHAT", "text": "...", "conversationId": "..." }
 *     { "type": "NEW_CONVERSATION", "title": "..." }
 *     { "type": "PING" }
 *
 *   Server → Client:
 *     { "type": "THINKING", "status": "..." }
 *     { "type": "TOOL_CALL", "tool": "...", "status": "OK|DENIED", "data": {...}, "uiAction": {...} }
 *     { "type": "REPLY", "text": "...", "conversationId": "..." }
 *     { "type": "ERROR", "error": "..." }
 *     { "type": "CONVERSATION_CREATED", "conversationId": "..." }
 *     { "type": "PONG" }
 */
@Component
public class JarvisWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(JarvisWebSocketHandler.class);

    @Autowired
    private ChatOrchestratorService orchestrator;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("[WS] Client connected: {}", session.getId());
        sendToSession(session, Map.of("type", "CONNECTED", "message", "Jarvis online. All systems operational."));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("[WS] Client disconnected: {} ({})", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = mapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.getOrDefault("type", "");

            switch (type) {
                case "PING" -> sendToSession(session, Map.of("type", "PONG"));
                case "NEW_CONVERSATION" -> handleNewConversation(session, payload);
                case "CHAT" -> handleChat(session, payload);
                default -> sendToSession(session, Map.of("type", "ERROR", "error", "Unknown message type: " + type));
            }
        } catch (Exception e) {
            log.error("[WS] Error handling message from {}: {}", session.getId(), e.getMessage(), e);
            sendToSession(session, Map.of("type", "ERROR", "error", "Failed to process message: " + e.getMessage()));
        }
    }

    private void handleNewConversation(WebSocketSession session, Map<String, Object> payload) {
        String title = (String) payload.getOrDefault("title", "New Conversation");
        String convId = orchestrator.createConversation(title);
        sendToSession(session, Map.of("type", "CONVERSATION_CREATED", "conversationId", convId, "title", title));
        log.info("[WS] Created conversation '{}' for session {}", convId, session.getId());
    }

    private void handleChat(WebSocketSession session, Map<String, Object> payload) {
        String text = (String) payload.getOrDefault("text", "");
        String conversationId = (String) payload.get("conversationId");

        if (text.isBlank()) {
            sendToSession(session, Map.of("type", "ERROR", "error", "Empty message text."));
            return;
        }

        // Create a conversation if none provided
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = orchestrator.createConversation(text.length() > 40 ? text.substring(0, 40) + "..." : text);
            sendToSession(session, Map.of("type", "CONVERSATION_CREATED", "conversationId", conversationId));
        }

        final String finalConvId = conversationId;
        final WebSocketSession boundSession = session;

        asyncExecutor.submit(() -> {
            orchestrator.processMessage(finalConvId, text, new ChatOrchestratorService.EventCallback() {
                @Override
                public void onThinking(String status) {
                    sendToSession(boundSession, Map.of("type", "THINKING", "status", status));
                }

                @Override
                public void onToolCall(String toolName, String status, ToolResult result) {
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("type", "TOOL_CALL");
                    msg.put("tool", toolName);
                    msg.put("status", status);
                    msg.put("summary", result.getSummary());
                    if (result.getData() != null) msg.put("data", result.getData());
                    if (result.getUiAction() != null) msg.put("uiAction", result.getUiAction());
                    sendToSession(boundSession, msg);
                }

                @Override
                public void onFinalReply(String text) {
                    sendToSession(boundSession, Map.of(
                            "type", "REPLY",
                            "text", text,
                            "conversationId", finalConvId
                    ));
                }

                @Override
                public void onError(String error) {
                    sendToSession(boundSession, Map.of("type", "ERROR", "error", error));
                }
            });
        });
    }

    private void sendToSession(WebSocketSession session, Map<String, Object> payload) {
        try {
            if (session.isOpen()) {
                String json = mapper.writeValueAsString(payload);
                synchronized (session) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            log.warn("[WS] Failed to send to session {}: {}", session.getId(), e.getMessage());
        }
    }
}
