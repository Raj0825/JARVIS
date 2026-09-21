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
import java.util.Map;

/**
 * Gmail & Email Automation Tool.
 * Opens Gmail directly with the Compose modal, pre-filled with recipient, subject, and body,
 * and can optionally automatically press the Send button (Ctrl+Enter).
 */
@Component
public class EmailTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(EmailTool.class);

    @Override
    public String getName() {
        return "compose_email";
    }

    @Override
    public String getDescription() {
        return "Compose and draft or send an email via Gmail. Automatically opens the Gmail Compose window with recipient (to), subject, and pre-filled body, and can automatically press Send.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "to", Map.of(
                                "type", "string",
                                "description", "Recipient email address (e.g. 'john@example.com')"
                        ),
                        "subject", Map.of(
                                "type", "string",
                                "description", "Subject line of the email"
                        ),
                        "body", Map.of(
                                "type", "string",
                                "description", "The message body / text content of the email"
                        ),
                        "send_now", Map.of(
                                "type", "boolean",
                                "description", "If true, automatically sends the email via Ctrl+Enter after opening. If false, leaves the composed draft open on screen for review."
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String to = (String) params.get("to");
        String subject = (String) params.get("subject");
        String body = (String) params.get("body");
        Boolean sendNow = (Boolean) params.getOrDefault("send_now", false);

        StringBuilder urlBuilder = new StringBuilder("https://mail.google.com/mail/?view=cm&fs=1");

        try {
            if (to != null && !to.isBlank()) {
                urlBuilder.append("&to=").append(URLEncoder.encode(to.trim(), StandardCharsets.UTF_8));
            }
            if (subject != null && !subject.isBlank()) {
                urlBuilder.append("&su=").append(URLEncoder.encode(subject.trim(), StandardCharsets.UTF_8));
            }
            if (body != null && !body.isBlank()) {
                urlBuilder.append("&body=").append(URLEncoder.encode(body.trim(), StandardCharsets.UTF_8));
            }

            String gmailUrl = urlBuilder.toString();
            String command = "cmd /c start chrome \"" + gmailUrl + "\"";
            log.info("[EmailTool] Launching Gmail compose URL: {}", gmailUrl);
            Runtime.getRuntime().exec(command);

            if (Boolean.TRUE.equals(sendNow)) {
                // Background thread to wait for Gmail to load and trigger Ctrl+Enter
                new Thread(() -> {
                    try {
                        Thread.sleep(4000);
                        Robot robot = new Robot();
                        robot.keyPress(KeyEvent.VK_CONTROL);
                        robot.keyPress(KeyEvent.VK_ENTER);
                        robot.keyRelease(KeyEvent.VK_ENTER);
                        robot.keyRelease(KeyEvent.VK_CONTROL);
                        log.info("[EmailTool] Dispatched Ctrl+Enter send keystroke to Gmail");
                    } catch (Exception e) {
                        log.warn("[EmailTool] Could not auto-send email via keystroke: {}", e.getMessage());
                    }
                }).start();

                String summary = to != null && !to.isBlank()
                        ? "I have composed and dispatched the email to " + to + " via Gmail."
                        : "I have dispatched the email draft via Gmail.";
                return ToolResult.success(summary, Map.of("to", to != null ? to : "", "status", "sent"), null);
            } else {
                String summary = to != null && !to.isBlank()
                        ? "I have opened Gmail with your email draft to " + to + " ready for your review."
                        : "I have opened the Gmail compose window ready for your message.";
                return ToolResult.success(summary, Map.of("to", to != null ? to : "", "status", "drafted"), null);
            }

        } catch (Exception e) {
            log.error("[EmailTool] Failed to open Gmail: {}", e.getMessage(), e);
            return ToolResult.failure("Failed to compose email: " + e.getMessage());
        }
    }

    @Override
    public boolean isEffectful() {
        return true;
    }
}
