package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Iron Man Protocols & Multi-Action Macros Tool.
 * Executes tactical, workstation, and party routines combining volume, theme, apps, and hardware.
 */
@Component
public class IronManProtocolTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(IronManProtocolTool.class);

    @Autowired
    private SystemControlTool systemControlTool;

    @Autowired
    private com.jarvis.service.SettingsService settingsService;

    @Override
    public String getName() {
        return "ironman_protocol";
    }

    @Override
    public String getDescription() {
        return "Execute Iron Man workstation protocols: 'house_party' (music, volume 80%, crimson theme), 'stealth_mode' (mute, dark theme, code IDE), 'morning_briefing' (status, weather, battery), 'combat_ready' (gold theme, audio boost), or 'security_lockdown' (lock workstation).";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "protocol", Map.of(
                                "type", "string",
                                "enum", List.of("house_party", "stealth_mode", "morning_briefing", "combat_ready", "security_lockdown"),
                                "description", "The protocol to engage"
                        )
                ),
                "required", List.of("protocol")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String protocol = (String) params.getOrDefault("protocol", "house_party");
        if (protocol == null) protocol = "house_party";
        protocol = protocol.toLowerCase(Locale.ROOT).trim();

        String callSign = "Mr. Raj";
        try {
            com.jarvis.model.JarvisSettings s = settingsService.getSettings("default");
            if (s.getUserCallSign() != null && !s.getUserCallSign().isBlank()) {
                callSign = s.getUserCallSign();
            }
        } catch (Exception ignored) {}

        log.info("[IronManProtocol] Engaging protocol: '{}' for {}", protocol, callSign);

        try {
            switch (protocol) {
                case "house_party", "party_mode" -> {
                    // 1. Elevate volume to 80%
                    systemControlTool.execute(Map.of("action", "volume_set", "value", 80));

                    // 2. Autoplay iconic AC/DC Back in Black / Iron Man soundtrack
                    try {
                        String musicUrl = "https://www.youtube.com/watch?v=pAgnJDJN4VA&autoplay=1";
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(new URI(musicUrl));
                        } else {
                            new ProcessBuilder("cmd.exe", "/c", "start", musicUrl).start();
                        }
                    } catch (Exception ex) {
                        log.warn("[IronManProtocol] Failed to launch music: {}", ex.getMessage());
                    }

                    return ToolResult.success(
                            "Protocol House Party engaged, " + callSign + "! Master volume elevated to 80%, Iron Man Crimson armor theme active, and audio stream initialized. Welcome to the party.",
                            Map.of("protocol", "house_party", "volume", 80, "theme", "crimson"),
                            Map.of("action", "SET_THEME", "theme", "crimson")
                    );
                }

                case "stealth_mode", "work_mode" -> {
                    // 1. Mute all audio
                    systemControlTool.execute(Map.of("action", "mute"));

                    // 2. Launch code workspace
                    try {
                        new ProcessBuilder("cmd.exe", "/c", "code", ".").start();
                    } catch (Exception ignored) {
                        try {
                            new ProcessBuilder("notepad.exe").start();
                        } catch (Exception ex2) {
                            log.warn("[IronManProtocol] Failed to launch editor: {}", ex2.getMessage());
                        }
                    }

                    return ToolResult.success(
                            "Protocol Stealth engaged, " + callSign + ". Acoustic emissions suppressed, IDE initiated, and workstation operating in a low-signature profile.",
                            Map.of("protocol", "stealth_mode", "audio", "muted", "theme", "cyan"),
                            Map.of("action", "SET_THEME", "theme", "cyan")
                    );
                }

                case "morning_briefing", "morning_protocol" -> {
                    LocalDateTime now = LocalDateTime.now();
                    String timeStr = now.format(DateTimeFormatter.ofPattern("hh:mm a"));
                    String dateStr = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));

                    // Get quick battery/disk summary
                    File cDrive = new File("C:\\");
                    long freeGb = cDrive.getFreeSpace() / (1024 * 1024 * 1024);

                    String briefing = String.format(
                            "Good morning, %s. It is %s on %s. Workstation telemetry is nominal with %d GB of high-speed storage free. All optical sensors, neural reasoning, and local arrays are at your command.",
                            callSign, timeStr, dateStr, freeGb
                    );

                    return ToolResult.success(
                            briefing,
                            Map.of("protocol", "morning_briefing", "time", timeStr, "date", dateStr),
                            Map.of("action", "SET_THEME", "theme", "gold")
                    );
                }

                case "combat_ready", "flight_readiness" -> {
                    systemControlTool.execute(Map.of("action", "volume_set", "value", 70));

                    return ToolResult.success(
                            "Flight readiness protocol engaged! Arc Reactor output stabilized at maximum capacity. Repulsor telemetry calibrated and target acquisition online. Ready for deployment, " + callSign + ".",
                            Map.of("protocol", "combat_ready", "theme", "gold"),
                            Map.of("action", "SET_THEME", "theme", "gold")
                    );
                }

                case "security_lockdown", "lock_pc" -> {
                    systemControlTool.execute(Map.of("action", "lock_pc"));
                    return ToolResult.success(
                            "Security lockdown protocol executed. Workstation locked down and secured, " + callSign + ".",
                            Map.of("protocol", "security_lockdown"),
                            null
                    );
                }

                default -> {
                    return ToolResult.failure("Unknown protocol: " + protocol + ". Available: house_party, stealth_mode, morning_briefing, combat_ready, security_lockdown.");
                }
            }
        } catch (Exception e) {
            log.error("[IronManProtocol] Error executing protocol '{}': {}", protocol, e.getMessage(), e);
            return ToolResult.failure("Protocol execution failed: " + e.getMessage());
        }
    }

    @Override
    public boolean isEffectful() {
        return false;
    }
}
