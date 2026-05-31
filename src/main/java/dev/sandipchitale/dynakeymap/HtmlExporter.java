package dev.sandipchitale.dynakeymap;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.keymap.Keymap;

import javax.swing.table.DefaultTableModel;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static dev.sandipchitale.dynakeymap.Exports.convertFileToDataUrl;
import static dev.sandipchitale.dynakeymap.Exports.escapeHtml;
import static dev.sandipchitale.dynakeymap.Exports.nowFormatted;
import static dev.sandipchitale.dynakeymap.Exports.splashImageTempFile;
import static dev.sandipchitale.dynakeymap.Shortcuts.normalizeShortcut;

/** Renders the action map and key map as a standalone HTML page and opens it in the browser. */
final class HtmlExporter {

    /**
     * @param actionMapKeymap   keymap used for the Action Map and Unbound Actions sections
     * @param keyMapLabel       label shown as the Key Map section heading
     * @param keyMapTableModel  the populated key map table; cells already contain HTML and are emitted raw
     */
    static void export(Keymap actionMapKeymap, Object keyMapLabel, DefaultTableModel keyMapTableModel) {
        ApplicationInfo applicationInfo = ApplicationInfo.getInstance();
        ActionManager actionManager = ActionManager.getInstance();
        KeymapActions actions = KeymapActions.collect(actionMapKeymap, actionManager);

        StringBuilder sb = new StringBuilder();
        sb.append("<html>\n<head>\n<title>KeyMap and Action Map</title>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<script src=\"https://cdn.tailwindcss.com\"></script>\n");
        sb.append("</head>\n<body>");

        Path splashImagePath = splashImageTempFile();
        if (splashImagePath != null) {
            try {
                sb.append("<div class=\"p-4\"><img src=\"").append(convertFileToDataUrl(splashImagePath.toFile())).append("\"></img></div>\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        sb.append("<div class=\"text-5xl text-bold p-4\">").append(applicationInfo.getFullApplicationName())
                .append(" ( ").append(applicationInfo.getFullVersion()).append(" )</div>\n");
        sb.append("<div class=\"text-bold p-4\">As of: ").append(nowFormatted()).append("</div>\n");

        // Action Map.
        sb.append("<div class=\"text-3xl text-bold p-4\">Action Map</div>\n");
        sb.append("\t<table class=\"table-auto border-collapse border\">\n");
        sb.append("\t\t<tr>\n");
        sb.append("<th class=\"text-right text-nowrap border p-1\">#</th>");
        sb.append("<th class=\"text-nowrap border p-1\">Action</th>");
        sb.append("<th class=\"text-nowrap border p-1\">Shortcut</th>\n");
        sb.append("\t\t</tr>\n");

        int lineNumber = 0;
        for (Map.Entry<String, KeymapActions.ActionIdAndShortCuts> entry : actions.bound().entrySet()) {
            for (Shortcut shortcut : entry.getValue().shortcuts()) {
                if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                    sb.append(String.format("\t\t<tr><td class=\"text-right text-nowrap border p-1%s\">%5d</td><td class=\"text-nowrap border p-1\">%s</td><td class=\"text-nowrap border p-1\">%s</td></tr>\n",
                            (lineNumber % 2 == 0 ? " bg-slate-100 " : ""),
                            ++lineNumber,
                            escapeHtml(entry.getKey()),
                            normalizeShortcut(keyboardShortcut)));
                }
            }
        }
        sb.append("\t</table>\n");

        // Key Map (cells already contain intentional HTML markup, so they are emitted raw).
        sb.append("<div class=\"text-3xl text-bold p-4\">").append(keyMapLabel).append(" KeyMap</div>\n");
        sb.append("\t<table class=\"table-auto border-collapse border\">\n");
        sb.append("\t\t<tr>");
        int columnCount = keyMapTableModel.getColumnCount();
        int rowCount = keyMapTableModel.getRowCount();
        for (int column = 0; column < columnCount; column++) {
            sb.append(String.format("<th class=\"text-nowrap border p-1\">%s</th>", keyMapTableModel.getColumnName(column).replace("<html>", "")));
        }
        sb.append("</tr>\n");

        for (int row = 0; row < rowCount; row++) {
            sb.append("\t\t<tr>");
            for (int column = 0; column < columnCount; column++) {
                sb.append(String.format("<td class=\"text-nowrap border p-1%s\">%s</td>",
                        (row % 2 == 0 ? " bg-slate-100 " : ""),
                        String.valueOf(keyMapTableModel.getValueAt(row, column)).replace("<html>", "")));
            }
            sb.append("\t\t</tr>\n");
        }
        sb.append("\t</table>\n");

        // Unbound Actions.
        if (!actions.unbound().isEmpty()) {
            sb.append("<div class=\"text-3xl text-bold p-4\">Unbound Actions</div>\n");
            sb.append("\t<table class=\"table-auto border-collapse border\">\n");
            sb.append("\t\t<tr>\n");
            sb.append("<th class=\"text-right text-nowrap border p-1\">#</th>");
            sb.append("<th class=\"text-nowrap border p-1\">Action</th>");
            sb.append("\t\t</tr>\n");

            lineNumber = 0;
            for (String actionName : actions.unbound().keySet()) {
                sb.append(String.format("\t\t<tr><td class=\"text-right text-nowrap border p-1%s\">%5d</td><td class=\"text-nowrap border p-1\">%s</td></tr>\n",
                        (lineNumber % 2 == 0 ? " bg-slate-100 " : ""),
                        ++lineNumber,
                        escapeHtml(actionName)));
            }
            sb.append("\t</table>\n");
        }

        sb.append("</body>");
        sb.append("</html>");

        try {
            Path actionMapAndKeyMapsPath = Files.createTempFile("Action map and Key maps", ".html");
            Files.writeString(actionMapAndKeyMapsPath, sb.toString());
            Desktop.getDesktop().browse(actionMapAndKeyMapsPath.toUri());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HtmlExporter() {
    }
}
