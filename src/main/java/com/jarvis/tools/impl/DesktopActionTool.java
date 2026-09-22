package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Desktop Action & UI Automation Tool.
 * Simulates physical user interactions on Windows:
 * - Keyboard key presses (Enter, Space, Tab, Escape, Backspace, etc.)
 * - Keyboard shortcuts (Ctrl+Enter to send emails, Ctrl+T, Alt+Tab, etc.)
 * - Mouse clicks (Left click, Right click, Double click at cursor or coordinates)
 * - Text typing into active window
 */
@Component
public class DesktopActionTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(DesktopActionTool.class);

    @Override
    public String getName() {
        return "desktop_action";
    }

    @Override
    public String getDescription() {
        return "Simulate physical keyboard and mouse interactions on Windows: click buttons, press keys (Enter, Space, Tab, Escape, etc.), send keyboard shortcuts (e.g. 'ctrl+enter' to send email, 'alt+tab', 'ctrl+w'), or type text into active applications.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "enum", List.of("press_key", "send_shortcut", "click_mouse", "type_text", "scroll"),
                                "description", "The action to perform: 'press_key', 'send_shortcut', 'click_mouse', 'type_text', or 'scroll'"
                        ),
                        "key", Map.of(
                                "type", "string",
                                "description", "Key to press for 'press_key': 'enter', 'space', 'tab', 'escape', 'backspace', 'up', 'down', 'left', 'right', 'k' (youtube play/pause), 'f' (fullscreen)"
                        ),
                        "shortcut", Map.of(
                                "type", "string",
                                "description", "Keyboard shortcut for 'send_shortcut': 'ctrl+enter' (send email), 'ctrl+t', 'ctrl+w', 'ctrl+c', 'ctrl+v', 'alt+tab', 'win+d', 'alt+f4'"
                        ),
                        "text", Map.of(
                                "type", "string",
                                "description", "Text to type into the active window for 'type_text'"
                        ),
                        "button", Map.of(
                                "type", "string",
                                "enum", List.of("left", "right", "double"),
                                "description", "Mouse button for 'click_mouse' (defaults to 'left')"
                        ),
                        "x", Map.of(
                                "type", "integer",
                                "description", "Optional screen X coordinate for mouse click"
                        ),
                        "y", Map.of(
                                "type", "integer",
                                "description", "Optional screen Y coordinate for mouse click"
                        ),
                        "scroll_amount", Map.of(
                                "type", "integer",
                                "description", "Mouse wheel scroll amount (positive = down, negative = up)"
                        )
                ),
                "required", List.of("action")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String action = (String) params.get("action");
        if (action == null || action.isBlank()) {
            return ToolResult.failure("Action parameter is required.");
        }

        action = action.toLowerCase(Locale.ROOT).trim();
        log.info("[DesktopAction] Executing action: '{}' with params: {}", action, params);

        try {
            Robot robot = new Robot();
            robot.setAutoDelay(50);

            switch (action) {
                case "press_key" -> {
                    String key = (String) params.get("key");
                    if (key == null || key.isBlank()) key = "enter";
                    key = key.toLowerCase(Locale.ROOT).trim();
                    bringTargetToFront((String) params.get("target_window"));
                    pressNamedKey(robot, key);
                    return ToolResult.success("Pressed " + key.toUpperCase() + " key.", Map.of("key", key), null);
                }

                case "send_shortcut" -> {
                    String shortcut = (String) params.get("shortcut");
                    if (shortcut == null || shortcut.isBlank()) shortcut = "ctrl+enter";
                    shortcut = shortcut.toLowerCase(Locale.ROOT).trim();
                    bringTargetToFront((String) params.get("target_window"));
                    sendKeyCombination(robot, shortcut);
                    return ToolResult.success("Sent shortcut combination " + shortcut.toUpperCase() + ".", Map.of("shortcut", shortcut), null);
                }

                case "click_mouse" -> {
                    String button = (String) params.getOrDefault("button", "left");
                    Number xNum = (Number) params.get("x");
                    Number yNum = (Number) params.get("y");

                    if (xNum != null && yNum != null) {
                        robot.mouseMove(xNum.intValue(), yNum.intValue());
                        robot.delay(100);
                    } else {
                        // Click at current physical mouse cursor position
                        try {
                            Point cur = MouseInfo.getPointerInfo().getLocation();
                            robot.mouseMove(cur.x, cur.y);
                            robot.delay(50);
                        } catch (Exception ignored) {}
                    }

                    int mask = button.equalsIgnoreCase("right") ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
                    if (button.equalsIgnoreCase("double")) {
                        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                        robot.delay(80);
                        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    } else {
                        robot.mousePress(mask);
                        robot.mouseRelease(mask);
                    }
                    return ToolResult.success("Performed " + button + " mouse click.", Map.of("button", button), null);
                }

                case "type_text" -> {
                    String text = (String) params.get("text");
                    if (text == null || text.isEmpty()) {
                        return ToolResult.failure("Text is required for type_text.");
                    }
                    // For maximum reliability, place text on clipboard and send Ctrl+V
                    try {
                        StringSelection selection = new StringSelection(text);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                        robot.delay(80);
                        robot.keyPress(KeyEvent.VK_CONTROL);
                        robot.keyPress(KeyEvent.VK_V);
                        robot.keyRelease(KeyEvent.VK_V);
                        robot.keyRelease(KeyEvent.VK_CONTROL);
                        return ToolResult.success("Typed text into active window.", Map.of("length", text.length()), null);
                    } catch (Exception clipEx) {
                        // Fallback to WScript.Shell SendKeys
                        String escaped = text.replace("\"", "\\\"");
                        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command",
                                "$ws = New-Object -ComObject WScript.Shell; $ws.SendKeys(\"" + escaped + "\")");
                        pb.start().waitFor();
                        return ToolResult.success("Typed text via shell.", Map.of("text", text), null);
                    }
                }

                case "scroll" -> {
                    Number amt = (Number) params.getOrDefault("scroll_amount", 3);
                    robot.mouseWheel(amt.intValue());
                    return ToolResult.success("Scrolled window by " + amt + " notches.", Map.of("amount", amt), null);
                }

                default -> {
                    return ToolResult.failure("Unknown action: " + action);
                }
            }
        } catch (Exception e) {
            log.error("[DesktopAction] Failed executing action '{}': {}", action, e.getMessage(), e);
            return ToolResult.failure("Desktop action failed: " + e.getMessage());
        }
    }

    private void bringTargetToFront(String targetWindow) {
        try {
            String script;
            if (targetWindow != null && !targetWindow.isBlank()) {
                script = "$ws = New-Object -ComObject WScript.Shell; $ws.AppActivate('" + targetWindow.replace("'", "") + "')";
            } else {
                // Focus the last active browser/messenger/email window
                script = "$ws = New-Object -ComObject WScript.Shell; " +
                        "if (-not $ws.AppActivate('Gmail')) { " +
                        "  if (-not $ws.AppActivate('Google Chrome')) { " +
                        "    if (-not $ws.AppActivate('WhatsApp')) { " +
                        "      $ws.AppActivate('Edge'); " +
                        "    } " +
                        "  } " +
                        "}";
            }
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", script);
            pb.start().waitFor(600, java.util.concurrent.TimeUnit.MILLISECONDS);
            Thread.sleep(120);
        } catch (Exception ignored) {}
    }

    private void pressNamedKey(Robot robot, String key) {
        int code = switch (key) {
            case "enter", "return", "send" -> KeyEvent.VK_ENTER;
            case "space", "spacebar", "play", "pause" -> KeyEvent.VK_SPACE;
            case "tab" -> KeyEvent.VK_TAB;
            case "esc", "escape" -> KeyEvent.VK_ESCAPE;
            case "backspace" -> KeyEvent.VK_BACK_SPACE;
            case "delete", "del" -> KeyEvent.VK_DELETE;
            case "up" -> KeyEvent.VK_UP;
            case "down" -> KeyEvent.VK_DOWN;
            case "left" -> KeyEvent.VK_LEFT;
            case "right" -> KeyEvent.VK_RIGHT;
            case "c" -> KeyEvent.VK_C; // Gmail compose
            case "k" -> KeyEvent.VK_K; // YouTube play/pause
            case "f" -> KeyEvent.VK_F; // YouTube full screen
            case "m" -> KeyEvent.VK_M; // YouTube mute
            case "j" -> KeyEvent.VK_J; // YouTube rewind 10s
            case "l" -> KeyEvent.VK_L; // YouTube forward 10s
            default -> KeyEvent.VK_ENTER;
        };

        robot.keyPress(code);
        robot.keyRelease(code);
    }

    private void sendKeyCombination(Robot robot, String combo) {
        combo = combo.replace(" ", "").toLowerCase(Locale.ROOT);

        if (combo.contains("ctrl") && combo.contains("enter")) {
            // Gmail send, message send, or form submit
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            // Also execute via WScript.Shell SendKeys for maximum compatibility
            try {
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command",
                        "$ws = New-Object -ComObject WScript.Shell; $ws.SendKeys('^{ENTER}')");
                pb.start().waitFor(400, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {}
        } else if (combo.contains("ctrl") && combo.contains("t")) {
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_T);
            robot.keyRelease(KeyEvent.VK_T);
            robot.keyRelease(KeyEvent.VK_CONTROL);
        } else if (combo.contains("ctrl") && combo.contains("w")) {
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_W);
            robot.keyRelease(KeyEvent.VK_W);
            robot.keyRelease(KeyEvent.VK_CONTROL);
        } else if (combo.contains("alt") && combo.contains("tab")) {
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_ALT);
        } else if (combo.contains("ctrl") && combo.contains("c")) {
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_C);
            robot.keyRelease(KeyEvent.VK_C);
            robot.keyRelease(KeyEvent.VK_CONTROL);
        } else if (combo.contains("ctrl") && combo.contains("v")) {
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_CONTROL);
        } else if (combo.contains("ctrl") && combo.contains("a")) {
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_CONTROL);
        } else if (combo.contains("win") && combo.contains("d")) {
            robot.keyPress(KeyEvent.VK_WINDOWS);
            robot.keyPress(KeyEvent.VK_D);
            robot.keyRelease(KeyEvent.VK_D);
            robot.keyRelease(KeyEvent.VK_WINDOWS);
        } else {
            // Use PowerShell SendKeys as general fallback
            try {
                String psSend = combo.replace("ctrl+", "^").replace("alt+", "%").replace("shift+", "+");
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command",
                        "$ws = New-Object -ComObject WScript.Shell; $ws.SendKeys('" + psSend + "')");
                pb.start().waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean isEffectful() {
        return true;
    }
}
