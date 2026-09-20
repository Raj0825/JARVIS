package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced Desktop, Deep-Settings, and Direct Web Navigation Tool for Windows.
 * - Opens specific Windows Settings (Bluetooth, Wi-Fi, Sound, Display, etc.) without false triggers.
 * - Opens direct websites and deep web features (Instagram Reels, YouTube Shorts, WhatsApp Web, ChatGPT, etc.)
 * - Opens desktop applications and folders (Notepad with notes, Camera, Calculator, Downloads, etc.)
 */
@Component
public class AppLauncherTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(AppLauncherTool.class);

    private static final Map<String, String> DEEP_APP_MAP = Map.ofEntries(
            // ─── Windows Settings (Specific Sub-pages First) ─────────
            Map.entry("bluetooth setting", "cmd /c start ms-settings:bluetooth"),
            Map.entry("bluetooth settings", "cmd /c start ms-settings:bluetooth"),
            Map.entry("bluetooth", "cmd /c start ms-settings:bluetooth"),
            Map.entry("wifi setting", "cmd /c start ms-settings:network-wifi"),
            Map.entry("wifi settings", "cmd /c start ms-settings:network-wifi"),
            Map.entry("wifi", "cmd /c start ms-settings:network-wifi"),
            Map.entry("wi-fi", "cmd /c start ms-settings:network-wifi"),
            Map.entry("sound setting", "cmd /c start ms-settings:sound"),
            Map.entry("sound settings", "cmd /c start ms-settings:sound"),
            Map.entry("volume setting", "cmd /c start ms-settings:sound"),
            Map.entry("audio setting", "cmd /c start ms-settings:sound"),
            Map.entry("display setting", "cmd /c start ms-settings:display"),
            Map.entry("display settings", "cmd /c start ms-settings:display"),
            Map.entry("battery setting", "cmd /c start ms-settings:powersleep"),
            Map.entry("battery settings", "cmd /c start ms-settings:powersleep"),
            Map.entry("power setting", "cmd /c start ms-settings:powersleep"),
            Map.entry("power settings", "cmd /c start ms-settings:powersleep"),
            Map.entry("windows update", "cmd /c start ms-settings:windowsupdate"),
            Map.entry("update setting", "cmd /c start ms-settings:windowsupdate"),
            Map.entry("update settings", "cmd /c start ms-settings:windowsupdate"),
            Map.entry("storage setting", "cmd /c start ms-settings:storagesense"),
            Map.entry("storage settings", "cmd /c start ms-settings:storagesense"),
            Map.entry("installed apps", "cmd /c start ms-settings:appsfeatures"),
            Map.entry("apps setting", "cmd /c start ms-settings:appsfeatures"),
            Map.entry("apps settings", "cmd /c start ms-settings:appsfeatures"),
            Map.entry("notifications", "cmd /c start ms-settings:notifications"),
            Map.entry("date and time", "cmd /c start ms-settings:dateandtime"),
            Map.entry("time setting", "cmd /c start ms-settings:dateandtime"),
            Map.entry("time settings", "cmd /c start ms-settings:dateandtime"),
            Map.entry("printer setting", "cmd /c start ms-settings:printers"),
            Map.entry("printer settings", "cmd /c start ms-settings:printers"),
            Map.entry("printers", "cmd /c start ms-settings:printers"),
            Map.entry("mouse setting", "cmd /c start ms-settings:mousetouchpad"),
            Map.entry("touchpad", "cmd /c start ms-settings:mousetouchpad"),
            Map.entry("wallpaper", "cmd /c start ms-settings:personalization-background"),
            Map.entry("personalization", "cmd /c start ms-settings:personalization-background"),
            Map.entry("network setting", "cmd /c start ms-settings:network"),
            Map.entry("network settings", "cmd /c start ms-settings:network"),
            Map.entry("hotspot", "cmd /c start ms-settings:network-mobilehotspot"),
            Map.entry("vpn", "cmd /c start ms-settings:network-vpn"),
            Map.entry("about pc", "cmd /c start ms-settings:about"),
            Map.entry("system specifications", "cmd /c start ms-settings:about"),
            Map.entry("laptop specifications", "cmd /c start ms-settings:about"),
            Map.entry("device specifications", "cmd /c start ms-settings:about"),
            Map.entry("specs", "cmd /c start ms-settings:about"),
            Map.entry("microphone privacy", "cmd /c start ms-settings:privacy-microphone"),
            Map.entry("camera privacy", "cmd /c start ms-settings:privacy-webcam"),
            // General Settings (only matched when user didn't ask for a specific sub-setting)
            Map.entry("windows settings", "cmd /c start ms-settings:"),
            Map.entry("settings", "cmd /c start ms-settings:"),

            // ─── Direct Web Applications & Deep Links ────────────────
            Map.entry("instagram reels", "cmd /c start chrome \"https://www.instagram.com/reels/\""),
            Map.entry("instagram reel", "cmd /c start chrome \"https://www.instagram.com/reels/\""),
            Map.entry("instagram dms", "cmd /c start chrome \"https://www.instagram.com/direct/inbox/\""),
            Map.entry("instagram messages", "cmd /c start chrome \"https://www.instagram.com/direct/inbox/\""),
            Map.entry("instagram explore", "cmd /c start chrome \"https://www.instagram.com/explore/\""),
            Map.entry("instagram", "cmd /c start chrome \"https://www.instagram.com/\""),

            Map.entry("youtube shorts", "cmd /c start chrome \"https://www.youtube.com/shorts\""),
            Map.entry("youtube subscriptions", "cmd /c start chrome \"https://www.youtube.com/feed/subscriptions\""),
            Map.entry("youtube history", "cmd /c start chrome \"https://www.youtube.com/feed/history\""),
            Map.entry("youtube", "cmd /c start chrome \"https://www.youtube.com/\""),

            Map.entry("whatsapp web", "cmd /c start chrome \"https://web.whatsapp.com/\""),
            Map.entry("whatsapp", "cmd /c start chrome \"https://web.whatsapp.com/\""),

            Map.entry("chatgpt", "cmd /c start chrome \"https://chatgpt.com/\""),
            Map.entry("netflix", "cmd /c start chrome \"https://www.netflix.com/\""),
            Map.entry("spotify web", "cmd /c start chrome \"https://open.spotify.com/\""),
            Map.entry("spotify", "cmd /c start spotify:"),
            Map.entry("gmail", "cmd /c start chrome \"https://mail.google.com/\""),
            Map.entry("google maps", "cmd /c start chrome \"https://maps.google.com/\""),
            Map.entry("maps", "cmd /c start chrome \"https://maps.google.com/\""),
            Map.entry("google drive", "cmd /c start chrome \"https://drive.google.com/\""),
            Map.entry("twitter", "cmd /c start chrome \"https://x.com/\""),
            Map.entry("x.com", "cmd /c start chrome \"https://x.com/\""),
            Map.entry("github", "cmd /c start chrome \"https://github.com/\""),
            Map.entry("reddit", "cmd /c start chrome \"https://www.reddit.com/\""),
            Map.entry("linkedin", "cmd /c start chrome \"https://www.linkedin.com/\""),
            Map.entry("amazon", "cmd /c start chrome \"https://www.amazon.in/\""),
            Map.entry("flipkart", "cmd /c start chrome \"https://www.flipkart.com/\""),
            Map.entry("facebook", "cmd /c start chrome \"https://www.facebook.com/\""),
            Map.entry("wikipedia", "cmd /c start chrome \"https://www.wikipedia.org/\""),
            Map.entry("pinterest", "cmd /c start chrome \"https://www.pinterest.com/\""),
            Map.entry("twitch", "cmd /c start chrome \"https://www.twitch.tv/\""),

            // ─── Chrome Internal Pages ───────────────────────────────
            Map.entry("chrome downloads", "cmd /c start chrome \"chrome://downloads\""),
            Map.entry("chrome history", "cmd /c start chrome \"chrome://history\""),
            Map.entry("chrome bookmarks", "cmd /c start chrome \"chrome://bookmarks\""),
            Map.entry("chrome settings", "cmd /c start chrome \"chrome://settings\""),
            Map.entry("chrome", "cmd /c start chrome"),
            Map.entry("google chrome", "cmd /c start chrome"),
            Map.entry("edge", "cmd /c start msedge"),
            Map.entry("microsoft edge", "cmd /c start msedge"),

            // ─── Windows Tools & Desktop Applications ────────────────
            Map.entry("task manager", "taskmgr.exe"),
            Map.entry("taskmgr", "taskmgr.exe"),
            Map.entry("device manager", "cmd /c start devmgmt.msc"),
            Map.entry("disk management", "cmd /c start diskmgmt.msc"),
            Map.entry("disk cleanup", "cmd /c start cleanmgr.exe"),
            Map.entry("control panel", "cmd /c start control.exe"),
            Map.entry("calculator", "calc.exe"),
            Map.entry("calc", "calc.exe"),
            Map.entry("paint", "mspaint.exe"),
            Map.entry("notepad", "notepad.exe"),
            Map.entry("camera", "cmd /c start microsoft.windows.camera:"),
            Map.entry("webcam", "cmd /c start microsoft.windows.camera:"),
            Map.entry("terminal", "cmd /c start wt"),
            Map.entry("cmd", "cmd /c start cmd"),
            Map.entry("command prompt", "cmd /c start cmd"),

            // ─── File Explorer Deep Folders ──────────────────────────
            Map.entry("downloads folder", "explorer.exe shell:Downloads"),
            Map.entry("downloads", "explorer.exe shell:Downloads"),
            Map.entry("documents folder", "explorer.exe shell:Personal"),
            Map.entry("documents", "explorer.exe shell:Personal"),
            Map.entry("desktop folder", "explorer.exe shell:Desktop"),
            Map.entry("pictures folder", "explorer.exe shell:My Pictures"),
            Map.entry("pictures", "explorer.exe shell:My Pictures"),
            Map.entry("videos folder", "explorer.exe shell:My Video"),
            Map.entry("music folder", "explorer.exe shell:My Music"),
            Map.entry("file explorer", "explorer.exe"),
            Map.entry("explorer", "explorer.exe")
    );

    @Override
    public String getName() {
        return "open_application";
    }

    @Override
    public String getDescription() {
        return "Directly launch applications, open websites (Instagram, YouTube, ChatGPT, Netflix, Amazon, etc.), deep web pages (Instagram Reels, YouTube Shorts), or specific Windows Settings (Bluetooth, Wi-Fi, Sound, Display).";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "application", Map.of(
                                "type", "string",
                                "description", "The application, website, or setting to open (e.g. 'instagram reels', 'instagram', 'youtube', 'bluetooth setting', 'settings', 'wifi setting', 'notepad', 'chrome', 'camera', 'downloads')"
                        ),
                        "content", Map.of(
                                "type", "string",
                                "description", "Optional text/note/to-do list content to write when launching Notepad."
                        ),
                        "query", Map.of(
                                "type", "string",
                                "description", "Optional search query if user asked to search something on Chrome or YouTube."
                        )
                ),
                "required", List.of("application")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String appName = (String) params.get("application");
        if (appName == null || appName.isBlank()) {
            return ToolResult.failure("Target name is required.");
        }

        String content = (String) params.get("content");
        if (content == null) content = (String) params.get("text");
        if (content == null) content = (String) params.get("note");

        String query = (String) params.get("query");
        String key = appName.toLowerCase(Locale.ROOT).trim();
        String command = null;
        String matchedLabel = appName;

        // 1. Check for song/music playback request (e.g. "play starboy", "play believer", "play a song")
        boolean isMusicPlay = key.startsWith("play ") || key.contains(" play ") || key.contains("play music")
                || key.contains("play a song") || key.contains("play song") || (key.startsWith("play") && key.length() > 5);

        if (isMusicPlay && !key.contains("playlist") && !key.contains("game")) {
            String songQuery = key.replaceAll("^(can you |please |jarvis |hey jarvis )?play( a| some)?( song| music)?", "")
                    .replace("on youtube", "")
                    .replace("in youtube", "")
                    .replace("on chrome", "")
                    .replace("in chrome", "")
                    .replace("for me", "")
                    .trim();

            if (songQuery.isBlank() || songQuery.equalsIgnoreCase("music") || songQuery.equalsIgnoreCase("song")) {
                songQuery = "top trending music hits";
            }

            log.info("[AppLauncher] Resolving YouTube video ID for autoplay: '{}'", songQuery);
            String videoId = resolveFirstYouTubeVideoId(songQuery);

            if (videoId != null && !videoId.isBlank()) {
                command = "cmd /c start chrome \"https://www.youtube.com/watch?v=" + videoId + "&autoplay=1\"";
                matchedLabel = songQuery;
                log.info("[AppLauncher] Found YouTube video ID: {} -> Autoplaying directly", videoId);
            } else {
                String encoded = URLEncoder.encode(songQuery, StandardCharsets.UTF_8);
                command = "cmd /c start chrome \"https://www.youtube.com/results?search_query=" + encoded + "\"";
                matchedLabel = songQuery;
            }

            try {
                log.info("[AppLauncher] Executing playback command: {}", command);
                Runtime.getRuntime().exec(command);
                String summary = "Playing " + matchedLabel + " on YouTube now, sir.";
                return ToolResult.success(
                        summary,
                        Map.of("target", matchedLabel, "command", command, "status", "playing"),
                        null
                );
            } catch (Exception e) {
                log.error("[AppLauncher] Failed to play '{}': {}", matchedLabel, e.getMessage());
                return ToolResult.failure("Failed to play " + matchedLabel + ": " + e.getMessage());
            }
        }

        // 2. Check for specific deep combinations in user input
        if (key.contains("reels") || (key.contains("reel") && key.contains("instagram"))) {
            command = "cmd /c start chrome \"https://www.instagram.com/reels/\"";
            matchedLabel = "Instagram Reels";
        } else if (key.contains("shorts") || (key.contains("short") && key.contains("youtube"))) {
            command = "cmd /c start chrome \"https://www.youtube.com/shorts\"";
            matchedLabel = "YouTube Shorts";
        } else {
            // 2. Exact match on longest matching key contained in user input
            // CRITICAL: only `key.contains(entry.getKey())` so general keys (like 'settings') do not falsely match specific requests
            Map.Entry<String, String> bestMatch = DEEP_APP_MAP.entrySet().stream()
                    .filter(entry -> key.contains(entry.getKey()))
                    .max(Comparator.comparingInt(e -> e.getKey().length()))
                    .orElse(null);

            if (bestMatch != null) {
                command = bestMatch.getValue();
                matchedLabel = bestMatch.getKey();
            }
        }

        // 3. Dynamic website / domain detection (e.g. "open wikipedia.org", "open coursera", etc.)
        if (command == null) {
            Pattern domainPattern = Pattern.compile("\\b([a-z0-9-]+(\\.(com|org|net|in|io|co|ai|edu|gov)))\\b");
            Matcher matcher = domainPattern.matcher(key);
            if (matcher.find()) {
                String domain = matcher.group(1);
                command = "cmd /c start chrome \"https://" + domain + "\"";
                matchedLabel = domain;
            } else if (key.startsWith("http://") || key.startsWith("https://")) {
                command = "cmd /c start chrome \"" + key + "\"";
                matchedLabel = "website";
            }
        }

        // 4. Handle web search queries if explicitly requested
        if (query != null && !query.isBlank()) {
            String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            if (key.contains("youtube")) {
                command = "cmd /c start chrome \"https://www.youtube.com/results?search_query=" + encoded + "\"";
                matchedLabel = "YouTube search for " + query;
            } else {
                command = "cmd /c start chrome \"https://www.google.com/search?q=" + encoded + "\"";
                matchedLabel = "Search for " + query;
            }
        }

        // 5. Special handling for Notepad with notes / to-do lists
        if (matchedLabel.contains("notepad") && content != null && !content.isBlank()) {
            try {
                Path userHome = Path.of(System.getProperty("user.home", "."));
                Path desktopDir = userHome.resolve("Desktop");
                Path targetDir = Files.isDirectory(desktopDir) ? desktopDir : userHome;
                Path noteFile = targetDir.resolve("Jarvis_Todo_List.txt");
                Files.writeString(noteFile, content);
                command = "notepad.exe \"" + noteFile.toAbsolutePath().toString() + "\"";
                log.info("[AppLauncher] Created note file at {} and launching Notepad", noteFile);
            } catch (Exception e) {
                log.warn("[AppLauncher] Could not write note file, launching blank notepad: {}", e.getMessage());
                command = "notepad.exe";
            }
        }

        // Fallback default command
        if (command == null) {
            command = "cmd /c start " + key;
        }

        try {
            log.info("[AppLauncher] Executing command: {}", command);
            Runtime.getRuntime().exec(command);
            String summary = (content != null && !content.isBlank())
                    ? "Successfully launched " + matchedLabel + " with your to-do list written, sir."
                    : "Successfully opened " + matchedLabel + " for you, sir.";
            return ToolResult.success(
                    summary,
                    Map.of("target", matchedLabel, "command", command, "status", "launched"),
                    null
            );
        } catch (Exception e) {
            log.error("[AppLauncher] Failed to open '{}': {}", matchedLabel, e.getMessage());
            return ToolResult.failure("Failed to open " + matchedLabel + ": " + e.getMessage());
        }
    }

    @Override
    public boolean isEffectful() {
        return false;
    }

    private static String resolveFirstYouTubeVideoId(String songQuery) {
        try {
            String encoded = URLEncoder.encode(songQuery, StandardCharsets.UTF_8);
            String url = "https://www.youtube.com/results?search_query=" + encoded;
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                    .connectTimeout(java.time.Duration.ofSeconds(4))
                    .build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(java.time.Duration.ofSeconds(4))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            Pattern p = Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"");
            Matcher m = p.matcher(resp.body());
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            log.warn("[AppLauncher] Could not resolve YouTube video ID for '{}': {}", songQuery, e.getMessage());
        }
        return null;
    }
}

