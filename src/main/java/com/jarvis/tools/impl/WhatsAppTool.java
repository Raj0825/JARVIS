package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WhatsApp Desktop & Web Automation Tool.
 * Opens WhatsApp Web or WhatsApp Desktop pre-addressed to a phone number or contact
 * with the pre-filled message, and can automatically dispatch the message via Enter keystroke.
 */
@Component
public class WhatsAppTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppTool.class);

    @Override
    public String getName() {
        return "whatsapp_action";
    }

    @Override
    public String getDescription() {
        return "Send WhatsApp messages via WhatsApp Web or Desktop: opens a direct chat with the recipient phone number or contact, pre-fills the message text, and can automatically press send.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "phone", Map.of(
                                "type", "string",
                                "description", "Recipient phone number with country code (e.g. '+919876543210' or '9876543210')"
                        ),
                        "contact", Map.of(
                                "type", "string",
                                "description", "Recipient contact name if no phone number is provided (e.g. 'Dad', 'Rohit', 'Alex')"
                        ),
                        "message", Map.of(
                                "type", "string",
                                "description", "The message text to send via WhatsApp"
                        ),
                        "send_now", Map.of(
                                "type", "boolean",
                                "description", "If true, automatically sends the message after WhatsApp loads. Defaults to false."
                        )
                ),
                "required", List.of("message")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String phone = (String) params.get("phone");
        String contact = (String) params.get("contact");
        String message = (String) params.get("message");
        Boolean sendNow = (Boolean) params.getOrDefault("send_now", false);

        if (message == null || message.isBlank()) {
            return ToolResult.failure("Message text is required for whatsapp_action.");
        }

        try {
            String encodedMessage = URLEncoder.encode(message.trim(), StandardCharsets.UTF_8);
            String targetUrl;
            String recipientLabel;

            if (phone != null && !phone.isBlank()) {
                String cleanPhone = phone.replaceAll("[^0-9]", "");
                if (cleanPhone.length() == 10) {
                    // Default to India country code +91 if 10 digits provided
                    cleanPhone = "91" + cleanPhone;
                }
                targetUrl = "https://web.whatsapp.com/send?phone=" + cleanPhone + "&text=" + encodedMessage;
                recipientLabel = "+" + cleanPhone;
            } else {
                targetUrl = "https://web.whatsapp.com/send?text=" + encodedMessage;
                recipientLabel = (contact != null && !contact.isBlank()) ? contact : "your contact";
            }

            String command = "cmd /c start chrome \"" + targetUrl + "\"";
            log.info("[WhatsApp] Launching WhatsApp URL: {}", targetUrl);
            Runtime.getRuntime().exec(command);

            if (Boolean.TRUE.equals(sendNow)) {
                new Thread(() -> {
                    try {
                        // Wait for WhatsApp Web to load the chat pane
                        Thread.sleep(6000);
                        Robot robot = new Robot();
                        robot.keyPress(KeyEvent.VK_ENTER);
                        robot.keyRelease(KeyEvent.VK_ENTER);
                        log.info("[WhatsApp] Sent Enter keystroke to dispatch WhatsApp message");
                    } catch (Exception e) {
                        log.warn("[WhatsApp] Could not auto-send WhatsApp message: {}", e.getMessage());
                    }
                }).start();

                String summary = "Dispatched your WhatsApp message to " + recipientLabel + ": \"" + message + "\"";
                return ToolResult.success(summary, Map.of("recipient", recipientLabel, "message", message, "status", "sent"), null);
            } else {
                String summary = "Opened WhatsApp chat with " + recipientLabel + " ready to send: \"" + message + "\"";
                return ToolResult.success(summary, Map.of("recipient", recipientLabel, "message", message, "status", "drafted"), null);
            }

        } catch (Exception e) {
            log.error("[WhatsApp] Failed to launch WhatsApp: {}", e.getMessage(), e);
            return ToolResult.failure("WhatsApp automation failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isEffectful() {
        return true;
    }
}
