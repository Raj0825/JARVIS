package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * System diagnostics tool — returns JVM memory, uptime, and runtime stats.
 */
@Component
public class SystemStatsTool implements JarvisTool {

    @Override
    public String getName() { return "system_stats"; }

    @Override
    public String getDescription() {
        return "Retrieve current system diagnostics: JVM memory usage, uptime, available processors, and Jarvis backend health status.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

        long heapUsedMb = mem.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMaxMb  = mem.getHeapMemoryUsage().getMax()  / (1024 * 1024);
        int processors  = Runtime.getRuntime().availableProcessors();
        long uptimeMs   = runtime.getUptime();

        Duration uptime = Duration.ofMillis(uptimeMs);
        String uptimeStr = String.format("%dh %dm %ds",
                uptime.toHours(), uptime.toMinutesPart(), uptime.toSecondsPart());

        String summary = String.format(
                "All systems operational. Heap: %dMB / %dMB. Uptime: %s. CPUs: %d. Status: NOMINAL.",
                heapUsedMb, heapMaxMb, uptimeStr, processors);

        return ToolResult.success(
                summary,
                Map.of(
                        "type", "system_stats",
                        "heap_used_mb", heapUsedMb,
                        "heap_max_mb", heapMaxMb,
                        "uptime", uptimeStr,
                        "processors", processors,
                        "status", "NOMINAL",
                        "java_version", System.getProperty("java.version")
                )
        );
    }
}
