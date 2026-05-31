package dev.sandipchitale.dynakeymap;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.keymap.Keymap;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static dev.sandipchitale.dynakeymap.Exports.convertFileToDataUrl;
import static dev.sandipchitale.dynakeymap.Exports.escapeHtml;
import static dev.sandipchitale.dynakeymap.Exports.nowFormatted;
import static dev.sandipchitale.dynakeymap.Exports.splashImageTempFile;
import static dev.sandipchitale.dynakeymap.Shortcuts.normalizeShortcut;

/** Renders the action map of a keymap as a PDF (cover page + table) and opens it. */
final class PdfExporter {

    /**
     * @param selectedKeymap keymap whose bound actions are tabulated; unbound actions are intentionally omitted
     * @param keyMapLabel    label shown on the cover page
     */
    static void export(Keymap selectedKeymap, Object keyMapLabel) {
        String formattedDate = nowFormatted();
        ApplicationInfo applicationInfo = ApplicationInfo.getInstance();
        Path splashImagePath = splashImageTempFile();
        File splashFile = splashImagePath == null ? null : splashImagePath.toFile();

        // Body: OpenHTMLtoPDF expects well-formed XHTML.
        StringBuilder sb = new StringBuilder();
        sb.append("<html xmlns='http://www.w3.org/1999/xhtml'><head><meta charset='UTF-8' />");
        sb.append("<style>" +
                "@page{size: letter landscape; margin:24pt;}" +
                "body{font-family: sans-serif; font-size:10pt;}" +
                ".title{font-size:24pt; font-weight:bold; margin:12pt 0;}" +
                ".subtitle{font-size:12pt; font-weight:bold; margin:6pt 0;}" +
                ".section{font-size:16pt; font-weight:bold; margin:12pt 0 6pt 0;}" +
                "table{border-collapse:collapse; width:100%;}" +
                "th,td{border:1px solid #999; padding:4pt; white-space:nowrap;}" +
                "th{background:#eee;}" +
                ".row-alt{background:#f6f6f6;}" +
                "img.logo{max-width:100%; height:auto;}" +
                "</style></head><body>");

        if (splashFile != null) {
            try {
                sb.append("<div style='margin:6pt 0'><img class='logo' src='").append(convertFileToDataUrl(splashFile)).append("' /></div>");
            } catch (IOException e) {
                // ignore logo if unavailable
            }
        }

        sb.append("<div class='title'>").append(applicationInfo.getFullApplicationName())
                .append(" ( ").append(applicationInfo.getFullVersion()).append(" )</div>");
        sb.append("<div class='subtitle'>As of: ").append(formattedDate).append("</div>");

        ActionManager actionManager = ActionManager.getInstance();
        KeymapActions actions = KeymapActions.collect(selectedKeymap, actionManager);

        sb.append("<div class='section'>Action Map</div>");
        sb.append("<table><tr><th class='text-right'>#</th><th>Action</th><th>Shortcut</th></tr>");

        int lineNumber = 0;
        for (Map.Entry<String, KeymapActions.ActionIdAndShortCuts> entry : actions.bound().entrySet()) {
            for (Shortcut shortcut : entry.getValue().shortcuts()) {
                if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                    sb.append(String.format("<tr class='%s'><td style='text-align:right'>%5d</td><td>%s</td><td>%s</td></tr>",
                            (lineNumber % 2 == 0 ? "row-alt" : ""),
                            ++lineNumber,
                            escapeHtml(entry.getKey()),
                            escapeHtml(normalizeShortcut(keyboardShortcut))));
                }
            }
        }
        sb.append("</table>");
        sb.append("</body></html>");

        try {
            // Render the body PDF from HTML.
            Path bodyPdf = Files.createTempFile("dynakeymap-body", ".pdf");
            try (OutputStream os = Files.newOutputStream(bodyPdf)) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.withHtmlContent(sb.toString(), null);
                builder.toStream(os);
                builder.run();
            }

            // Build a landscape cover page that matches the body and carries the logo.
            Path coverPdf = Files.createTempFile("dynakeymap-cover", ".pdf");
            try (PDDocument doc = new PDDocument()) {
                PDRectangle pageSize = new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth());
                PDPage page = new PDPage(pageSize);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    float margin = 36f; // half-inch margin
                    float yTop = pageSize.getHeight() - margin;

                    writeText(cs, PDType1Font.HELVETICA_BOLD, 24, margin, yTop - 40, applicationInfo.getFullApplicationName());
                    writeText(cs, PDType1Font.HELVETICA, 12, margin, yTop - 70, "Version: " + applicationInfo.getFullVersion());
                    writeText(cs, PDType1Font.HELVETICA, 12, margin, yTop - 90, "As of: " + formattedDate);
                    writeText(cs, PDType1Font.HELVETICA_BOLD, 14, margin, yTop - 120, "Keymap: " + keyMapLabel);

                    if (splashFile != null && splashFile.exists()) {
                        try {
                            PDImageXObject img = PDImageXObject.createFromFileByContent(splashFile, doc);
                            float imgWidth = Math.min(pageSize.getWidth() - 2 * margin, img.getWidth());
                            float scale = imgWidth / img.getWidth();
                            cs.drawImage(img, margin, margin, imgWidth, img.getHeight() * scale);
                        } catch (IOException ignored) {
                        }
                    }
                }
                doc.save(coverPdf.toFile());
            }

            // Merge cover + body.
            Path finalPdf = Files.createTempFile("Action map and Key maps", ".pdf");
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationFileName(finalPdf.toString());
            merger.addSource(coverPdf.toFile());
            merger.addSource(bodyPdf.toFile());
            merger.mergeDocuments(null);

            Desktop.getDesktop().open(finalPdf.toFile());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeText(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private PdfExporter() {
    }
}
