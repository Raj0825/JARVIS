package com.jarvis.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * REST API for real-time Windows hardware telemetry.
 * Delivers CPU, RAM, Battery, and Disk metrics to the holographic HUD gauges.
 */
@RestController
@RequestMapping("/api/telemetry")
public class SystemTelemetryController {

    private static final Logger log = LoggerFactory.getLogger(SystemTelemetryController.class);

    private final AtomicInteger cachedBatteryPercent = new AtomicInteger(100);
    private final AtomicLong lastBatteryCheck = new AtomicLong(0);

    @GetMapping("/system")
    public Map<String, Object> getSystemTelemetry() {
        Map<String, Object> stats = new LinkedHashMap<>();

        try {
            // 1. CPU & Memory via com.sun.management.OperatingSystemMXBean
            java.lang.management.OperatingSystemMXBean baseBean = ManagementFactory.getOperatingSystemMXBean();
            double cpuPercent = 0.0;
            long ramTotalMb = 0;
            long ramFreeMb = 0;
            long ramUsedMb = 0;
            double ramPercent = 0.0;

            if (baseBean instanceof com.sun.management.OperatingSystemMXBean os) {
                double cpuLoad = os.getCpuLoad();
                if (cpuLoad < 0) {
                    cpuLoad = os.getProcessCpuLoad();
                }
                cpuPercent = Math.max(0.0, Math.min(100.0, Math.round(cpuLoad * 1000.0) / 10.0));

                long totalBytes = os.getTotalMemorySize();
                long freeBytes = os.getFreeMemorySize();
                ramTotalMb = totalBytes / (1024 * 1024);
                ramFreeMb = freeBytes / (1024 * 1024);
                ramUsedMb = ramTotalMb - ramFreeMb;
                ramPercent = ramTotalMb > 0 ? Math.round(((double) ramUsedMb / ramTotalMb) * 1000.0) / 10.0 : 0.0;
            }

            // 2. Battery status (cached every 15 seconds)
            long now = System.currentTimeMillis();
            if (now - lastBatteryCheck.get() > 15000) {
                lastBatteryCheck.set(now);
                checkBatteryAsync();
            }

            // 3. Storage Disk (C: drive)
            File cDrive = new File("C:\\");
            long diskTotalGb = cDrive.getTotalSpace() / (1024 * 1024 * 1024);
            long diskFreeGb = cDrive.getFreeSpace() / (1024 * 1024 * 1024);
            long diskUsedGb = diskTotalGb - diskFreeGb;
            double diskPercent = diskTotalGb > 0 ? Math.round(((double) diskUsedGb / diskTotalGb) * 1000.0) / 10.0 : 0.0;

            // 4. Runtime uptime
            long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

            stats.put("cpuPercent", cpuPercent);
            stats.put("ramTotalMb", ramTotalMb);
            stats.put("ramUsedMb", ramUsedMb);
            stats.put("ramFreeMb", ramFreeMb);
            stats.put("ramPercent", ramPercent);
            stats.put("batteryPercent", cachedBatteryPercent.get());
            stats.put("diskTotalGb", diskTotalGb);
            stats.put("diskUsedGb", diskUsedGb);
            stats.put("diskFreeGb", diskFreeGb);
            stats.put("diskPercent", diskPercent);
            stats.put("uptimeSeconds", uptimeSeconds);
            stats.put("osName", System.getProperty("os.name"));
            stats.put("processors", Runtime.getRuntime().availableProcessors());
            stats.put("status", "NOMINAL");

        } catch (Exception e) {
            log.error("[Telemetry] Failed to collect system metrics: {}", e.getMessage());
            stats.put("status", "DEGRADED");
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    private void checkBatteryAsync() {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Process p = new ProcessBuilder("powershell.exe", "-Command", "(Get-CimInstance Win32_Battery).EstimatedChargeRemaining").start();
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && !line.isBlank()) {
                        int pct = Integer.parseInt(line.trim());
                        cachedBatteryPercent.set(Math.max(0, Math.min(100, pct)));
                    }
                }
            } catch (Exception ignored) {
                // Desktop PCs or VMs without battery stay at 100%
            }
        });
    }
}
