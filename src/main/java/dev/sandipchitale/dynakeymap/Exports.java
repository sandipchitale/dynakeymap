package dev.sandipchitale.dynakeymap;

import com.intellij.openapi.application.ApplicationInfo;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/** Shared helpers for the HTML and PDF exporters. */
final class Exports {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    static String nowFormatted() {
        return ZonedDateTime.now(ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER);
    }

    static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    static String convertFileToDataUrl(File file) throws IOException {
        byte[] fileContent = Files.readAllBytes(file.toPath());
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(fileContent);
    }

    /** Copies the IDE splash image to a temp file, or returns {@code null} if unavailable. */
    static Path splashImageTempFile() {
        String splashImageUrl = ApplicationInfo.getInstance().getSplashImageUrl();
        if (splashImageUrl == null) {
            return null;
        }
        URL resourceUrl = ApplicationInfo.class.getResource(splashImageUrl);
        if (resourceUrl == null) {
            return null;
        }
        try {
            Path splashImagePath = Files.createTempFile("splash", ".png");
            Files.copy(resourceUrl.openStream(), splashImagePath, StandardCopyOption.REPLACE_EXISTING);
            return splashImagePath;
        } catch (IOException e) {
            return null;
        }
    }

    private Exports() {
    }
}
