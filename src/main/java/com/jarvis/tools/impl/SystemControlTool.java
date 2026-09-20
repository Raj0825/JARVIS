package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Windows Hardware, System & Media Controller.
 * Controls:
 * - Master Volume (set exact %, volume up/down, mute/unmute)
 * - Media Playback (play/pause, next track, previous track)
 * - Workstation Security (lock PC, sleep PC)
 */
@Component
public class SystemControlTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(SystemControlTool.class);

    @Override
    public String getName() {
        return "system_control";
    }

    @Override
    public String getDescription() {
        return "Control Windows system hardware and media: adjust volume (set exact percentage e.g. 50%, mute, unmute, volume up/down), media playback (play/pause, next, previous), or lock/sleep the workstation.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "description", "The action to perform: 'volume_set', 'volume_up', 'volume_down', 'mute', 'unmute', 'media_play_pause', 'media_next', 'media_previous', 'lock_pc', 'sleep_pc'"
                        ),
                        "value", Map.of(
                                "type", "number",
                                "description", "Optional numeric value, e.g. volume percentage from 0 to 100 for 'volume_set'"
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
        Number valueNum = (Number) params.get("value");
        int volumePercent = valueNum != null ? Math.max(0, Math.min(100, valueNum.intValue())) : 50;

        try {
            switch (action) {
                case "volume_set" -> {
                    float scalar = volumePercent / 100.0f;
                    // PowerShell inline CoreAudio API script to set exact master volume scalar
                    String psCmd = String.format(Locale.US,
                            "$wsh = New-Object -ComObject WScript.Shell; " +
                            "Add-Type -TypeDefinition @'" +
                            "using System.Runtime.InteropServices; " +
                            "[Guid(\"5CDF2C82-841E-4546-9722-0CF74078229A\"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] " +
                            "public interface IAudioEndpointVolume { " +
                            "    int f(); int g(); int h(); int m(); " +
                            "    int SetMasterVolumeLevelScalar(float fLevel, System.Guid pguidEventContext); " +
                            "} " +
                            "[Guid(\"D666063F-1587-4E43-81F1-B948E807363F\"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] " +
                            "public interface IMMDevice { " +
                            "    int Activate(ref System.Guid id, int clsCtx, int opt, [MarshalAs(UnmanagedType.IUnknown)] out object val); " +
                            "} " +
                            "[Guid(\"A95664D2-9614-4F35-A746-DE8DB63617E6\"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] " +
                            "public interface IMMDeviceEnumerator { " +
                            "    int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice endpoint); " +
                            "} " +
                            "[ComImport, Guid(\"BCDE0395-E52F-467C-8E3D-C4579291692E\")] " +
                            "public class MMDeviceEnumeratorComObject { } " +
                            "public class MasterAudio { " +
                            "    public static void SetVol(float v) { " +
                            "        var enumerator = (IMMDeviceEnumerator)(new MMDeviceEnumeratorComObject()); " +
                            "        IMMDevice dev = null; " +
                            "        enumerator.GetDefaultAudioEndpoint(0, 1, out dev); " +
                            "        System.Guid IID = typeof(IAudioEndpointVolume).GUID; " +
                            "        object o = null; " +
                            "        dev.Activate(ref IID, 23, 0, out o); " +
                            "        ((IAudioEndpointVolume)o).SetMasterVolumeLevelScalar(v, System.Guid.Empty); " +
                            "    } " +
                            "} " +
                            "'@ -ErrorAction SilentlyContinue; [MasterAudio]::SetVol(%.2ff)", scalar);

                    runProcess("powershell", "-NoProfile", "-NonInteractive", "-Command", psCmd);
                    return ToolResult.success(
                            "Master volume set to " + volumePercent + "%, sir.",
                            Map.of("volume", volumePercent, "status", "set"),
                            null
                    );
                }

                case "volume_up" -> {
                    String psCmd = "$wsh = New-Object -ComObject WScript.Shell; 1..5 | ForEach-Object { $wsh.SendKeys([char]175) }";
                    runProcess("powershell", "-NoProfile", "-NonInteractive", "-Command", psCmd);
                    return ToolResult.success("Volume increased, sir.", Map.of("action", "volume_up"), null);
                }

                case "volume_down" -> {
                    String psCmd = "$wsh = New-Object -ComObject WScript.Shell; 1..5 | ForEach-Object { $wsh.SendKeys([char]174) }";
                    runProcess("powershell", "-NoProfile", "-NonInteractive", "-Command", psCmd);
                    return ToolResult.success("Volume decreased, sir.", Map.of("action", "volume_down"), null);
                }

                case "mute", "unmute" -> {
                    String psCmd = "$wsh = New-Object -ComObject WScript.Shell; $wsh.SendKeys([char]173)";
                    runProcess("powershell", "-NoProfile", "-NonInteractive", "-Command", psCmd);
                    return ToolResult.success("Audio mute status toggled, sir.", Map.of("action", "mute_toggle"), null);
                }

                case "media_play_pause" -> {
                    String psCmd = "$wsh = New-Object -ComObject WScript.Shell; $wsh.SendKeys([char]179)";
                    runProcess("powershell", "-NoProfile", "-NonInteractive", "-Command", psCmd);
                    return ToolResult.success("Media playback toggled, sir.", Map.of("action", "play_pause"), null);
                }

                case "media_next" -> {
                    String psCmd = "$wsh = New-Object -ComObject WScript.Shell; $wsh.SendKeys([char]176)";
                    runProcess("powershell", "-NoProfile", "-NonInteractive", "-Command", psCmd);
                    return ToolResult.success("Skipped to next track, sir.", Map.of("action", "media_next"), null);
                }

                case "media_previous" -> {
                    String psCmd = "$wsh = New-Object -ComObject WScript.Shell; $wsh.SendKeys([char]177)";
                    runProcess("powershell", "-NoProfile", "-NonInteractive", "-Command", psCmd);
                    return ToolResult.success("Returned to previous track, sir.", Map.of("action", "media_previous"), null);
                }

                case "lock_pc" -> {
                    runProcess("rundll32.exe", "user32.dll,LockWorkStation");
                    return ToolResult.success("Workstation locked successfully, sir.", Map.of("status", "locked"), null);
                }

                case "sleep_pc" -> {
                    runProcess("rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0");
                    return ToolResult.success("Initiating workstation sleep sequence, sir.", Map.of("status", "sleep"), null);
                }

                default -> {
                    return ToolResult.failure("Unknown system control action: " + action);
                }
            }
        } catch (Exception e) {
            log.error("[SystemControlTool] Error executing action '{}': {}", action, e.getMessage(), e);
            return ToolResult.failure("Failed to execute " + action + ": " + e.getMessage());
        }
    }

    private void runProcess(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.waitFor();
    }

    @Override
    public boolean isEffectful() {
        return false;
    }
}
