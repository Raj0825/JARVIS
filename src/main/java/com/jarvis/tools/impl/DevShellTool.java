package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Developer & Terminal Shell Copilot Tool.
 * Safe OS/terminal automation for developer workflows:
 * - Kill port-hogging processes (e.g. :8080, :5173)
 * - Top memory & CPU process inspection
 * - Process termination
 * - Git automation (status, log, diff, push, pull)
 * - Safe command execution
 */
@Component
public class DevShellTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(DevShellTool.class);

    private static final List<String> BLACKLISTED_TERMS = List.of(
            "format ", "diskpart", "rmdir /s /q c:\\", "del /f /s /q c:\\", "rd /s /q c:\\",
            "remove-item -recurse -force c:\\", ":(){ :|:& };:"
    );

    @Override
    public String getName() {
        return "dev_shell";
    }

    @Override
    public String getDescription() {
        return "Developer Terminal and Process Copilot: frees blocked ports (e.g. :8080), inspects top RAM/CPU processes, terminates frozen processes, runs safe git commands, and executes developer shell tasks.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of(
                                "type", "string",
                                "enum", List.of("kill_port", "top_processes", "kill_process", "run_git", "exec_cmd"),
                                "description", "Action to perform: 'kill_port', 'top_processes', 'kill_process', 'run_git', or 'exec_cmd'"
                        ),
                        "port", Map.of(
                                "type", "integer",
                                "description", "Port number for 'kill_port' (e.g. 8080, 5173, 3000)"
                        ),
                        "target", Map.of(
                                "type", "string",
                                "description", "Process name or PID for 'kill_process' (e.g. 'node', 'chrome', '12345')"
                        ),
                        "git_command", Map.of(
                                "type", "string",
                                "description", "Git subcommand for 'run_git': 'status', 'log', 'diff', 'push', 'pull', 'branch'"
                        ),
                        "command", Map.of(
                                "type", "string",
                                "description", "PowerShell command to execute for 'exec_cmd'"
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
        log.info("[DevShell] Executing action: '{}' with params: {}", action, params);

        try {
            switch (action) {
                case "kill_port" -> {
                    Number portNum = (Number) params.get("port");
                    if (portNum == null) {
                        return ToolResult.failure("Port number is required for kill_port.");
                    }
                    int port = portNum.intValue();
                    String ps = String.format(Locale.ROOT,
                            "$conns = Get-NetTCPConnection -LocalPort %d -ErrorAction SilentlyContinue; " +
                            "if ($conns) { " +
                            "  $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique; " +
                            "  foreach ($pidVal in $pids) { " +
                            "    if ($pidVal -gt 0) { " +
                            "      $proc = Get-Process -Id $pidVal -ErrorAction SilentlyContinue; " +
                            "      Stop-Process -Id $pidVal -Force -ErrorAction SilentlyContinue; " +
                            "      Write-Output \"Terminated process '$($proc.ProcessName)' (PID $pidVal) listening on port %d.\"; " +
                            "    } " +
                            "  } " +
                            "} else { " +
                            "  Write-Output \"No active process is listening on port %d.\"; " +
                            "}", port, port, port);

                    String out = runPowerShell(ps);
                    return ToolResult.success(out.isBlank() ? "Port " + port + " is now free." : out, Map.of("port", port), null);
                }

                case "top_processes" -> {
                    String ps = "Get-Process | Sort-Object -Descending WS | Select-Object -First 6 " +
                            "@{Name='Process'; Expression={$_.ProcessName}}, " +
                            "@{Name='PID'; Expression={$_.Id}}, " +
                            "@{Name='RAM_MB'; Expression={[math]::round($_.WS / 1MB, 1)}}, " +
                            "@{Name='CPU_Sec'; Expression={[math]::round($_.CPU, 1)}} | Format-Table -AutoSize | Out-String";
                    String out = runPowerShell(ps);
                    return ToolResult.success("Top memory and CPU consuming processes:\n\n" + out.trim(), Map.of("output", out), null);
                }

                case "kill_process" -> {
                    String target = (String) params.get("target");
                    if (target == null || target.isBlank()) {
                        return ToolResult.failure("Target process name or PID is required.");
                    }
                    String ps;
                    if (target.matches("^\\d+$")) {
                        ps = "Stop-Process -Id " + target + " -Force; Write-Output 'Terminated PID " + target + ".'";
                    } else {
                        String cleanName = target.replaceAll("(?i)\\.exe$", "").trim();
                        ps = "Stop-Process -Name '" + cleanName + "' -Force; Write-Output 'Terminated process " + cleanName + ".'";
                    }
                    String out = runPowerShell(ps);
                    return ToolResult.success(out.isBlank() ? "Process " + target + " terminated." : out, Map.of("target", target), null);
                }

                case "run_git" -> {
                    String gitCmd = (String) params.getOrDefault("git_command", "status");
                    gitCmd = gitCmd.trim().toLowerCase(Locale.ROOT);
                    String fullCmd = switch (gitCmd) {
                        case "log" -> "git log -n 5 --oneline";
                        case "diff" -> "git diff --stat";
                        case "push" -> "git push origin main";
                        case "pull" -> "git pull";
                        case "branch" -> "git branch -a";
                        default -> "git status --short";
                    };
                    String out = runPowerShell(fullCmd);
                    return ToolResult.success("Git " + gitCmd + " result:\n" + out.trim(), Map.of("command", fullCmd, "output", out), null);
                }

                case "exec_cmd" -> {
                    String cmd = (String) params.get("command");
                    if (cmd == null || cmd.isBlank()) {
                        return ToolResult.failure("Command is required for exec_cmd.");
                    }
                    String lower = cmd.toLowerCase(Locale.ROOT);
                    for (String black : BLACKLISTED_TERMS) {
                        if (lower.contains(black)) {
                            return ToolResult.failure("Security restriction: Command contains unsafe operation ('" + black + "').");
                        }
                    }
                    String out = runPowerShell(cmd);
                    return ToolResult.success("Command executed successfully:\n" + out.trim(), Map.of("output", out), null);
                }

                default -> {
                    return ToolResult.failure("Unknown action: " + action);
                }
            }
        } catch (Exception e) {
            log.error("[DevShell] Execution error: {}", e.getMessage(), e);
            return ToolResult.failure("Developer copilot error: " + e.getMessage());
        }
    }

    private String runPowerShell(String psScript) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psScript);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
                if (sb.length() > 4000) {
                    sb.append("... [Output truncated]");
                    break;
                }
            }
        }

        boolean finished = proc.waitFor(12, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            return sb.append("\n[Command timed out after 12 seconds]").toString();
        }
        return sb.toString().trim();
    }

    @Override
    public boolean isEffectful() {
        return true;
    }
}
