package com.jarvis.tools.impl;

import com.jarvis.model.MemoryRecord;
import com.jarvis.repository.MemoryRepository;
import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Smart Windows Clipboard & Memory Vault Tool.
 * Allows JARVIS to read/write the Windows clipboard and persist facts, notes, and reminders in MongoDB.
 */
@Component
public class ClipboardMemoryTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(ClipboardMemoryTool.class);

    @Autowired
    private MemoryRepository memoryRepository;

    @Override
    public String getName() {
        return "clipboard_memory";
    }

    @Override
    public String getDescription() {
        return "Access Windows clipboard and persistent memory vault. Read or copy text to clipboard, or store and recall facts, reminders, links, and credentials.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "enum", List.of("read_clipboard", "write_clipboard", "save_memory", "recall_memory", "list_memories"),
                                "description", "Action to perform"
                        ),
                        "text", Map.of(
                                "type", "string",
                                "description", "Text to copy to clipboard or memory content to store"
                        ),
                        "topic", Map.of(
                                "type", "string",
                                "description", "Topic, title, or search keyword for memory"
                        ),
                        "category", Map.of(
                                "type", "string",
                                "description", "Optional category: general, credential, reminder, link, note"
                        )
                ),
                "required", List.of("action")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String action = (String) params.getOrDefault("action", "read_clipboard");
        String text = (String) params.get("text");
        String topic = (String) params.get("topic");
        String category = (String) params.getOrDefault("category", "general");

        try {
            switch (action.toLowerCase()) {
                case "read_clipboard" -> {
                    String clipboardText = readClipboardContent();
                    if (clipboardText == null || clipboardText.isBlank()) {
                        return ToolResult.success("Clipboard is currently empty, sir.", Map.of("content", ""), null);
                    }
                    int charCount = clipboardText.length();
                    String preview = clipboardText.length() > 300 ? clipboardText.substring(0, 300) + "..." : clipboardText;
                    return ToolResult.success(
                            "Clipboard content (" + charCount + " chars):\n\n" + clipboardText,
                            Map.of("content", clipboardText, "charCount", charCount, "preview", preview),
                            null
                    );
                }

                case "write_clipboard" -> {
                    if (text == null || text.isBlank()) {
                        return ToolResult.failure("No text provided to copy to clipboard.");
                    }
                    writeClipboardContent(text);
                    return ToolResult.success(
                            "Text successfully copied to Windows clipboard: \"" + (text.length() > 60 ? text.substring(0, 60) + "..." : text) + "\"",
                            Map.of("copied", text),
                            null
                    );
                }

                case "save_memory" -> {
                    if (text == null || text.isBlank()) {
                        return ToolResult.failure("No content provided to save to memory vault.");
                    }
                    String finalTopic = (topic != null && !topic.isBlank()) ? topic : extractTopic(text);
                    MemoryRecord record = MemoryRecord.builder()
                            .userId("default")
                            .topic(finalTopic)
                            .content(text)
                            .category(category)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();
                    memoryRepository.save(record);
                    return ToolResult.success(
                            "Stored in Memory Vault: [" + finalTopic + "] — " + text,
                            Map.of("id", record.getId(), "topic", finalTopic, "content", text),
                            null
                    );
                }

                case "recall_memory" -> {
                    String query = (topic != null && !topic.isBlank()) ? topic : text;
                    if (query == null || query.isBlank()) {
                        return ToolResult.failure("Please specify what topic or memory you want me to recall.");
                    }
                    List<MemoryRecord> results = memoryRepository.findByUserIdAndTopicContainingIgnoreCase("default", query);
                    if (results.isEmpty()) {
                        results = memoryRepository.findByUserIdAndContentContainingIgnoreCase("default", query);
                    }
                    if (results.isEmpty()) {
                        return ToolResult.success("I searched your memory vault for '" + query + "', but found no matching entries, sir.", Map.of("matches", 0), null);
                    }
                    String summary = results.stream()
                            .map(r -> "• [" + r.getTopic() + "]: " + r.getContent())
                            .collect(Collectors.joining("\n"));
                    return ToolResult.success(
                            "Found " + results.size() + " memory record(s) matching '" + query + "':\n" + summary,
                            Map.of("matches", results.size(), "records", results),
                            null
                    );
                }

                case "list_memories" -> {
                    List<MemoryRecord> all = memoryRepository.findByUserIdOrderByCreatedAtDesc("default");
                    if (all.isEmpty()) {
                        return ToolResult.success("Your memory vault is currently empty, sir.", Map.of("count", 0), null);
                    }
                    String list = all.stream().limit(8)
                            .map(r -> "• [" + r.getTopic() + "] (" + r.getCategory() + "): " + (r.getContent().length() > 50 ? r.getContent().substring(0, 50) + "..." : r.getContent()))
                            .collect(Collectors.joining("\n"));
                    return ToolResult.success(
                            "Memory Vault (" + all.size() + " total entries):\n" + list,
                            Map.of("count", all.size()),
                            null
                    );
                }

                default -> {
                    return ToolResult.failure("Unknown clipboard/memory action: " + action);
                }
            }
        } catch (Exception e) {
            log.error("[ClipboardMemoryTool] Failed to execute action '{}': {}", action, e.getMessage(), e);
            return ToolResult.failure("Clipboard action failed: " + e.getMessage());
        }
    }

    private String readClipboardContent() {
        // 1. Try Java AWT Clipboard
        try {
            System.setProperty("java.awt.headless", "false");
            if (!GraphicsEnvironment.isHeadless()) {
                var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    return (String) clipboard.getData(DataFlavor.stringFlavor);
                }
            }
        } catch (Throwable t) {
            log.warn("[ClipboardMemoryTool] AWT Clipboard read failed ({}). Trying PowerShell...", t.getMessage());
        }

        // 2. PowerShell fallback: Get-Clipboard
        try {
            Process p = new ProcessBuilder("powershell.exe", "-Command", "Get-Clipboard").start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception ex) {
            log.warn("[ClipboardMemoryTool] PowerShell Get-Clipboard failed: {}", ex.getMessage());
        }
        return null;
    }

    private void writeClipboardContent(String text) {
        // 1. Try Java AWT Clipboard
        try {
            System.setProperty("java.awt.headless", "false");
            if (!GraphicsEnvironment.isHeadless()) {
                var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection selection = new StringSelection(text);
                clipboard.setContents(selection, selection);
                return;
            }
        } catch (Throwable t) {
            log.warn("[ClipboardMemoryTool] AWT Clipboard write failed ({}). Trying PowerShell...", t.getMessage());
        }

        // 2. PowerShell fallback: Set-Clipboard
        try {
            Process p = new ProcessBuilder("powershell.exe", "-Command", "Set-Clipboard -Value @'\n" + text + "\n'@").start();
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("[ClipboardMemoryTool] PowerShell Set-Clipboard failed: {}", ex.getMessage());
        }
    }

    private String extractTopic(String content) {
        String clean = content.replaceAll("^(remember that|remember|note that|save)\\s+", "").trim();
        String[] words = clean.split("\\s+");
        if (words.length <= 4) return clean;
        return words[0] + " " + words[1] + " " + words[2];
    }

    @Override
    public boolean isEffectful() {
        return false;
    }
}
