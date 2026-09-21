package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * File Organizer Tool.
 * Automatically cleans and categorizes loose files in Downloads or Desktop folders
 * into organized subfolders (PDFs, Images, Installers, Archives, Media, Code).
 */
@Component
public class FileOrganizerTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(FileOrganizerTool.class);

    private static final Map<String, String> EXTENSION_CATEGORY = new HashMap<>();

    static {
        // Documents
        for (String ext : List.of("pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", "txt", "csv", "epub")) {
            EXTENSION_CATEGORY.put(ext, "PDFs & Documents");
        }
        // Images
        for (String ext : List.of("png", "jpg", "jpeg", "webp", "svg", "gif", "bmp", "ico")) {
            EXTENSION_CATEGORY.put(ext, "Images");
        }
        // Installers
        for (String ext : List.of("exe", "msi", "bat", "cmd", "apk", "jar")) {
            EXTENSION_CATEGORY.put(ext, "Installers & Programs");
        }
        // Archives
        for (String ext : List.of("zip", "rar", "7z", "tar", "gz", "iso")) {
            EXTENSION_CATEGORY.put(ext, "Archives & Zips");
        }
        // Media
        for (String ext : List.of("mp4", "mkv", "avi", "mov", "webm", "mp3", "wav", "flac", "m4a")) {
            EXTENSION_CATEGORY.put(ext, "Media & Audio");
        }
        // Code
        for (String ext : List.of("java", "js", "ts", "jsx", "tsx", "py", "html", "css", "json", "sql", "cpp", "c")) {
            EXTENSION_CATEGORY.put(ext, "Code & Scripts");
        }
    }

    @Override
    public String getName() {
        return "file_organizer";
    }

    @Override
    public String getDescription() {
        return "Clean and organize loose files in Windows Downloads or Desktop folders into categorized subdirectories (PDFs & Documents, Images, Installers, Archives, Media, Code).";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "target_folder", Map.of(
                                "type", "string",
                                "enum", List.of("downloads", "desktop"),
                                "description", "The directory to organize (defaults to 'downloads')"
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String targetFolder = (String) params.getOrDefault("target_folder", params.getOrDefault("folder", "downloads"));
        if (targetFolder == null || targetFolder.isBlank()) {
            targetFolder = "downloads";
        }
        String lowerTarget = targetFolder.toLowerCase(Locale.ROOT);
        if (lowerTarget.contains("desktop")) {
            targetFolder = "desktop";
        } else {
            targetFolder = "downloads";
        }

        File targetDir = resolveDirectory(targetFolder);
        if (targetDir == null || !targetDir.exists() || !targetDir.isDirectory()) {
            return ToolResult.failure("Directory not found: " + targetFolder);
        }

        log.info("[FileOrganizer] Organizing files in: {}", targetDir.getAbsolutePath());

        File[] files = targetDir.listFiles();
        if (files == null || files.length == 0) {
            return ToolResult.success("The " + targetFolder + " folder is already empty, sir.", Map.of("moved", 0, "directory", targetDir.getAbsolutePath(), "targetFolder", targetFolder), null);
        }

        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        int totalMoved = 0;

        for (File file : files) {
            // Skip subdirectories, hidden files, temporary files, in-progress downloads
            if (file.isDirectory() || file.isHidden() || file.getName().startsWith(".")) {
                continue;
            }
            String name = file.getName();
            if (name.endsWith(".tmp") || name.endsWith(".crdownload") || name.equalsIgnoreCase("desktop.ini")) {
                continue;
            }

            int lastDot = name.lastIndexOf('.');
            String ext = (lastDot > 0 && lastDot < name.length() - 1) ? name.substring(lastDot + 1).toLowerCase(Locale.ROOT) : "";
            String category = EXTENSION_CATEGORY.getOrDefault(ext, "Miscellaneous");

            File categoryDir = new File(targetDir, category);
            if (!categoryDir.exists()) {
                categoryDir.mkdirs();
            }

            File destination = getSafeDestination(categoryDir, name);
            try {
                Files.move(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
                totalMoved++;
            } catch (Exception e) {
                log.warn("[FileOrganizer] Failed to move file '{}': {}", name, e.getMessage());
            }
        }

        if (totalMoved == 0) {
            return ToolResult.success(
                    "All loose files in " + targetFolder + " are already organized into subdirectories, sir.",
                    Map.of("moved", 0, "breakdown", categoryCounts, "directory", targetDir.getAbsolutePath(), "targetFolder", targetFolder),
                    null
            );
        }

        StringBuilder breakdown = new StringBuilder();
        categoryCounts.forEach((cat, count) -> breakdown.append("\n• ").append(cat).append(": ").append(count).append(" file(s)"));

        String summary = String.format(
                "Organized %d loose file(s) in your %s folder into clean categories:%s\n\nDestination: %s",
                totalMoved, targetFolder, breakdown, targetDir.getAbsolutePath()
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("moved", totalMoved);
        data.put("breakdown", categoryCounts);
        data.put("directory", targetDir.getAbsolutePath());
        data.put("targetFolder", targetFolder);

        return ToolResult.success(summary, data, null);
    }

    private File resolveDirectory(String target) {
        String userHome = System.getProperty("user.home");
        if ("desktop".equalsIgnoreCase(target)) {
            File oneDriveDesktop = new File(userHome, "OneDrive" + File.separator + "Desktop");
            if (oneDriveDesktop.exists() && oneDriveDesktop.isDirectory()) {
                return oneDriveDesktop;
            }
            File desktop = new File(userHome, "Desktop");
            if (desktop.exists() && desktop.isDirectory()) {
                return desktop;
            }
            String userProfile = System.getenv("USERPROFILE");
            if (userProfile != null) {
                File pDesktop = new File(userProfile, "Desktop");
                if (pDesktop.exists()) return pDesktop;
            }
            return desktop;
        }

        // Downloads: check OneDrive Downloads, userHome Downloads, and USERPROFILE Downloads
        File oneDriveDownloads = new File(userHome, "OneDrive" + File.separator + "Downloads");
        if (oneDriveDownloads.exists() && oneDriveDownloads.isDirectory()) {
            return oneDriveDownloads;
        }
        File downloads = new File(userHome, "Downloads");
        if (downloads.exists() && downloads.isDirectory()) {
            return downloads;
        }
        String userProfile = System.getenv("USERPROFILE");
        if (userProfile != null) {
            File pDown = new File(userProfile, "Downloads");
            if (pDown.exists()) return pDown;
        }
        return downloads;
    }

    private File getSafeDestination(File dir, String filename) {
        File dest = new File(dir, filename);
        if (!dest.exists()) return dest;

        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String ext = dot > 0 ? filename.substring(dot) : "";

        int counter = 1;
        while (dest.exists()) {
            dest = new File(dir, base + "_" + counter + ext);
            counter++;
        }
        return dest;
    }

    @Override
    public boolean isEffectful() {
        return false;
    }
}
