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

    private static final Set<String> CATEGORIES = Set.of(
            "PDFs & Documents",
            "Images",
            "Installers & Programs",
            "Archives & Zips",
            "Media & Audio",
            "Code & Scripts",
            "Miscellaneous"
    );

    @Override
    public String getName() {
        return "file_organizer";
    }

    @Override
    public String getDescription() {
        return "Clean and organize loose files in Windows Downloads or Desktop folders into categorized subdirectories (action='organize'), or undo/restore them back to the root folder (action='restore' or 'undo').";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "target_folder", Map.of(
                                "type", "string",
                                "enum", List.of("downloads", "desktop"),
                                "description", "The directory to organize or restore (defaults to 'downloads')"
                        ),
                        "action", Map.of(
                                "type", "string",
                                "enum", List.of("organize", "restore", "undo"),
                                "description", "'organize' to sort files into subfolders, or 'restore'/'undo' to move all files back into the root folder."
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

        String action = (String) params.getOrDefault("action", "organize");
        if (action != null && (action.equalsIgnoreCase("restore") || action.equalsIgnoreCase("undo") || action.equalsIgnoreCase("revert"))) {
            return restoreFiles(targetDir, targetFolder);
        }

        log.info("[FileOrganizer] Organizing files in: {}", targetDir.getAbsolutePath());

        File[] files = targetDir.listFiles();
        if (files == null || files.length == 0) {
            return ToolResult.success("The " + targetFolder + " folder is already empty, sir.", Map.of("action", "organize", "moved", 0, "directory", targetDir.getAbsolutePath(), "targetFolder", targetFolder), null);
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
                    Map.of("action", "organize", "moved", 0, "breakdown", categoryCounts, "directory", targetDir.getAbsolutePath(), "targetFolder", targetFolder),
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
        data.put("action", "organize");
        data.put("moved", totalMoved);
        data.put("breakdown", categoryCounts);
        data.put("directory", targetDir.getAbsolutePath());
        data.put("targetFolder", targetFolder);

        return ToolResult.success(summary, data, null);
    }

    private ToolResult restoreFiles(File targetDir, String targetFolder) {
        log.info("[FileOrganizer] Restoring files back to root of: {}", targetDir.getAbsolutePath());
        int totalRestored = 0;
        Map<String, Integer> restoredPerCategory = new LinkedHashMap<>();

        for (String catName : CATEGORIES) {
            File catDir = new File(targetDir, catName);
            if (!catDir.exists() || !catDir.isDirectory()) {
                continue;
            }

            File[] filesInCat = catDir.listFiles();
            if (filesInCat != null) {
                for (File f : filesInCat) {
                    if (f.isDirectory()) continue;
                    File dest = getSafeDestination(targetDir, f.getName());
                    try {
                        Files.move(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        totalRestored++;
                        restoredPerCategory.put(catName, restoredPerCategory.getOrDefault(catName, 0) + 1);
                    } catch (Exception e) {
                        log.warn("[FileOrganizer] Failed to restore file '{}': {}", f.getName(), e.getMessage());
                    }
                }
            }

            // Remove category folder if now empty
            File[] remaining = catDir.listFiles();
            if (remaining == null || remaining.length == 0) {
                try {
                    catDir.delete();
                    log.info("[FileOrganizer] Removed empty category directory: {}", catName);
                } catch (Exception ignored) {}
            }
        }

        if (totalRestored == 0) {
            return ToolResult.success(
                    "No organized category folders were found in " + targetFolder + " to restore, sir. All files are already in the main folder.",
                    Map.of("action", "restore", "restored", 0, "directory", targetDir.getAbsolutePath(), "targetFolder", targetFolder),
                    null
            );
        }

        StringBuilder breakdown = new StringBuilder();
        restoredPerCategory.forEach((cat, count) -> breakdown.append("\n• ").append(cat).append(": ").append(count).append(" file(s) moved back"));

        String summary = String.format(
                "Restored %d file(s) from category folders back to your main %s folder:%s\n\nAll empty category folders have been safely removed, sir.",
                totalRestored, targetFolder, breakdown
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", "restore");
        data.put("restored", totalRestored);
        data.put("breakdown", restoredPerCategory);
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
