package com.jarvis.tools.impl;

import com.jarvis.tools.JarvisTool;
import com.jarvis.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Screenshot Tool.
 * Captures the Windows desktop display, saves a high-res PNG to the Desktop or Pictures folder,
 * and copies the image to the Windows system clipboard for instant pasting.
 */
@Component
public class ScreenshotTool implements JarvisTool {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotTool.class);

    @Override
    public String getName() {
        return "take_screenshot";
    }

    @Override
    public String getDescription() {
        return "Capture a screenshot of the computer screen, save it directly to the Windows Desktop or Pictures folder as a PNG file, and copy it to the clipboard.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "destination", Map.of(
                                "type", "string",
                                "enum", List.of("desktop", "pictures"),
                                "description", "Where to save the screenshot (defaults to 'desktop')"
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String destination = (String) params.getOrDefault("destination", "desktop");
        if (destination == null || destination.isBlank()) destination = "desktop";

        try {
            log.info("[ScreenshotTool] Capturing screenshot...");
            System.setProperty("java.awt.headless", "false");

            BufferedImage capture = null;

            // 1. Try Java AWT Robot with union bounds across all monitors
            try {
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                Rectangle screenRect = new Rectangle(0, 0, 0, 0);
                for (GraphicsDevice gd : ge.getScreenDevices()) {
                    screenRect = screenRect.union(gd.getDefaultConfiguration().getBounds());
                }
                if (screenRect.width <= 0 || screenRect.height <= 0) {
                    Toolkit toolkit = Toolkit.getDefaultToolkit();
                    screenRect = new Rectangle(toolkit.getScreenSize());
                }
                Robot robot = new Robot();
                capture = robot.createScreenCapture(screenRect);
                log.info("[ScreenshotTool] Captured screen via Robot ({}x{})", capture.getWidth(), capture.getHeight());
            } catch (Throwable t) {
                log.warn("[ScreenshotTool] Robot capture failed ({}). Attempting PowerShell fallback...", t.getMessage());
            }

            // 2. PowerShell fallback
            if (capture == null) {
                try {
                    File tempPng = File.createTempFile("jarvis_shot_", ".png");
                    String outPath = tempPng.getAbsolutePath().replace("\\", "/");
                    String script = "Add-Type -AssemblyName System.Windows.Forms\n" +
                            "Add-Type -AssemblyName System.Drawing\n" +
                            "$s = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds\n" +
                            "$b = New-Object System.Drawing.Bitmap $s.Width, $s.Height\n" +
                            "$g = [System.Drawing.Graphics]::FromImage($b)\n" +
                            "$g.CopyFromScreen($s.Location, [System.Drawing.Point]::Empty, $s.Size)\n" +
                            "$b.Save('" + outPath + "', [System.Drawing.Imaging.ImageFormat]::Png)\n" +
                            "$b.Dispose()\n" +
                            "$g.Dispose()\n";
                    File psFile = File.createTempFile("jarvis_ps_", ".ps1");
                    java.nio.file.Files.writeString(psFile.toPath(), script);
                    Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", psFile.getAbsolutePath()).start();
                    p.waitFor(6, java.util.concurrent.TimeUnit.SECONDS);
                    psFile.delete();
                    if (tempPng.exists() && tempPng.length() > 500) {
                        capture = ImageIO.read(tempPng);
                        tempPng.delete();
                    }
                } catch (Exception ex) {
                    log.error("[ScreenshotTool] PowerShell fallback failed: {}", ex.getMessage());
                }
            }

            if (capture == null) {
                return ToolResult.failure("Unable to capture screen display. Please verify display is unlocked.");
            }

            // 3. Resolve save directory (Desktop or Pictures)
            File saveDir = resolveSaveDirectory(destination);
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName = "JARVIS_Screenshot_" + timestamp + ".png";
            File targetFile = new File(saveDir, fileName);

            ImageIO.write(capture, "png", targetFile);
            log.info("[ScreenshotTool] Screenshot saved to: {}", targetFile.getAbsolutePath());

            // 4. Copy image to Windows clipboard for instant pasting (Ctrl+V)
            copyImageToClipboard(capture, targetFile);

            // 5. Build base64 preview for HUD panel
            String base64Uri = null;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(capture, "png", baos);
                base64Uri = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
            } catch (Exception ignored) {}

            String destLabel = destination.equalsIgnoreCase("desktop") ? "Desktop" : "Pictures";
            String reply = String.format(
                    "Screenshot captured and saved to %s:\n`%s`\n\nIt has also been copied to your clipboard for instant pasting (`Ctrl+V`), sir.",
                    destLabel,
                    targetFile.getAbsolutePath()
            );

            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("filePath", targetFile.getAbsolutePath());
            data.put("fileName", fileName);
            data.put("destination", destLabel);
            data.put("sizeBytes", targetFile.length());
            if (base64Uri != null) {
                data.put("url", base64Uri);
            }

            return ToolResult.success(
                    reply,
                    data,
                    base64Uri != null ? Map.of("action", "SHOW_IMAGE", "url", base64Uri, "prompt", fileName) : null
            );

        } catch (Exception e) {
            String err = (e.getMessage() != null && !e.getMessage().isBlank()) ? e.getMessage() : e.getClass().getSimpleName();
            log.error("[ScreenshotTool] Failed to capture and save screenshot: {}", err, e);
            return ToolResult.failure("Failed to save screenshot: " + err);
        }
    }

    private File resolveSaveDirectory(String destination) {
        String userHome = System.getProperty("user.home");
        if ("pictures".equalsIgnoreCase(destination)) {
            File oneDrivePictures = new File(userHome, "OneDrive" + File.separator + "Pictures" + File.separator + "Screenshots");
            if (oneDrivePictures.exists() && oneDrivePictures.isDirectory()) {
                return oneDrivePictures;
            }
            File pictures = new File(userHome, "Pictures" + File.separator + "Screenshots");
            if (pictures.exists() && pictures.isDirectory()) {
                return pictures;
            }
            return new File(userHome, "Pictures");
        }

        // Default: Desktop
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
            File profileDesktop = new File(userProfile, "Desktop");
            if (profileDesktop.exists()) return profileDesktop;
        }
        return desktop;
    }

    private void copyImageToClipboard(BufferedImage img, File savedFile) {
        // 1. Try Java AWT Clipboard
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableImage(img), null);
                log.info("[ScreenshotTool] Image copied to AWT clipboard.");
                return;
            }
        } catch (Throwable t) {
            log.warn("[ScreenshotTool] AWT Clipboard copy failed ({}). Trying PowerShell...", t.getMessage());
        }

        // 2. PowerShell clipboard fallback
        try {
            String safePath = savedFile.getAbsolutePath().replace("'", "''");
            String psCmd = "$img = [System.Drawing.Image]::FromFile('" + safePath + "'); " +
                    "Add-Type -AssemblyName System.Windows.Forms; " +
                    "[System.Windows.Forms.Clipboard]::SetImage($img); " +
                    "$img.Dispose()";
            new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", psCmd).start();
        } catch (Exception ex) {
            log.warn("[ScreenshotTool] PowerShell clipboard copy failed: {}", ex.getMessage());
        }
    }

    private static class TransferableImage implements Transferable {
        private final Image image;

        public TransferableImage(Image image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (isDataFlavorSupported(flavor)) {
                return image;
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }

    @Override
    public boolean isEffectful() {
        return false;
    }
}
