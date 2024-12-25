package dev.sandipchitale.dynakeymap;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

public class DynaKeyMapToolWindow extends SimpleToolWindowPanel {
    private static final Logger LOG = Logger.getInstance(DynaKeyMapToolWindow.class);


    private final Project project;
    private final DefaultTableModel dynaKeyMapTableModel;
    private final JBTable dynaKeyMapTable;

    private final JBTextArea actionToShortcutTextArea;
    private final JBTextArea unboundActionsTextArea;

    private static int index = 0;
    private static final int FIRST_KEYSTROKE_KEY = index++;
    private static final int SECOND_KEYSTROKE_KEY = index++;

    private static final String[] MODIFIERS;

    private static final List<String> ALL_KEYS;

    static {
        java.util.List<String> VK_FIELDS = new ArrayList<>();
        // Collect VK_ fields
        for (java.lang.reflect.Field field : KeyEvent.class.getDeclaredFields()) {
            if (field.getName().startsWith("VK_")) {
                VK_FIELDS.add(field.getName().replace("VK_", ""));
            }
        }
        // Combine VK_ fields and ASCII chars
        ALL_KEYS = new ArrayList<>(VK_FIELDS);

        // Sort
        Collections.sort(ALL_KEYS);
    }

    static {
        if (SystemInfo.isMac) {
            MODIFIERS = new String[]{"shift", "ctrl", "meta", "alt", "shift ctrl", "shift meta", "shift alt", "ctrl meta", "ctrl alt", "meta alt", "shift ctrl meta", "shift ctrl alt", "shift meta alt", "ctrl meta alt", "shift ctrl meta alt", ""};
        } else {
            MODIFIERS = new String[]{"shift", "ctrl", "alt", "shift ctrl", "shift alt", "ctrl alt", "shift ctrl alt", ""};
        }
    }

    private static final String[] COLUMNS = new String[index + MODIFIERS.length];

    static {
        COLUMNS[FIRST_KEYSTROKE_KEY] = "Key in First Keystroke ";
        COLUMNS[SECOND_KEYSTROKE_KEY] = "Key in Second Keystroke";
        System.arraycopy(MODIFIERS, 0, COLUMNS, index, MODIFIERS.length);
    }

    private final TableRowSorter<DefaultTableModel> tableRowSorter;

    private final SearchTextField searchTextField;
    private final SearchTextField searchActionMapTextField;

    private record FirstKeyStrokeAndActionId(KeyStroke firstKeyStroke, String actionId) {
    }

    public DynaKeyMapToolWindow(@NotNull Project project) {
        super(true, true);
        this.project = project;

        dynaKeyMapTableModel = new DefaultTableModel(COLUMNS, 0) {

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }

            @Override
            public Object getValueAt(int row, int column) {
                Vector rowVector = dataVector.get(row);
                if (column == FIRST_KEYSTROKE_KEY) {
                    return rowVector.get(FIRST_KEYSTROKE_KEY);
                } else if (column == SECOND_KEYSTROKE_KEY) {
                    return rowVector.get(SECOND_KEYSTROKE_KEY);
                } else {
                    return rowVector.get(column);
                }
            }
        };
        dynaKeyMapTable = new JBTable(dynaKeyMapTableModel) {
            @Override
            public String getToolTipText(@NotNull MouseEvent event) {
                Point p = event.getPoint();
                // Locate the renderer under the event location
                int row = rowAtPoint(p);
                int column = columnAtPoint(p);
                return super.getToolTipText(event);
            }
        };

        dynaKeyMapTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.isAltDown() && mouseEvent.getClickCount() == 2) {
                    Point p = mouseEvent.getPoint();
                    int column = dynaKeyMapTable.columnAtPoint(p);
                    int row = dynaKeyMapTable.rowAtPoint(p);
                }

            }
        });

        TableCellRenderer dynaKeyMapTableCellRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (column == FIRST_KEYSTROKE_KEY || column == SECOND_KEYSTROKE_KEY) {
                    String keyValue = (String) value;
                    if (!keyValue.isEmpty()) {
                        label.setText(String.format("<html>%s", kbdfy(keyValue)));
                    }
                }

                return label;
            }
        };
        tableRowSorter = new TableRowSorter<>(dynaKeyMapTableModel);
        dynaKeyMapTable.setRowSorter(tableRowSorter);

        TableColumn column;

        column = dynaKeyMapTable.getColumnModel().getColumn(FIRST_KEYSTROKE_KEY);
        column.setMinWidth(250);
        column.setWidth(250);
        column.setMaxWidth(250);
        column.setCellRenderer(dynaKeyMapTableCellRenderer);

        column = dynaKeyMapTable.getColumnModel().getColumn(SECOND_KEYSTROKE_KEY);
        column.setMinWidth(250);
        column.setWidth(250);
        column.setMaxWidth(250);
        column.setCellRenderer(dynaKeyMapTableCellRenderer);

        JTableHeader tableHeader = dynaKeyMapTable.getTableHeader();
        Dimension tableHeaderPreferredSize = tableHeader.getPreferredSize();
        tableHeader.setPreferredSize(new Dimension(tableHeaderPreferredSize.width, 48));
        tableHeader.setToolTipText("Right click on the header to hide/show columns. Some columns may be hidden.");
        JBTabbedPane tabbedPane = new JBTabbedPane();

        new JTableColumnSelector().install(dynaKeyMapTable);

        BorderLayoutPanel dynaKeyMapTablePanel = new BorderLayoutPanel();

        BorderLayoutPanel toolbarPanel = new BorderLayoutPanel();

        searchTextField = new SearchTextField();
        searchTextField.setToolTipText("Filter. NOTE: Text will match in hidden columns as well.");
        searchTextField.addKeyboardListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchTextField.setText("");
                    search();
                    return;
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    search();
                }
            }
        });
        toolbarPanel.addToCenter(searchTextField);

        JButton searchButton = new JButton(AllIcons.General.Filter);
        searchButton.setToolTipText("Filter. NOTE: Text will match in hidden columns as well.");
        searchButton.addActionListener((ActionEvent actionEvent) -> {
            search();
        });
        toolbarPanel.addToRight(searchButton);

        dynaKeyMapTablePanel.addToTop(toolbarPanel);

        JScrollPane dynaKeyMapTableScrollPane = ScrollPaneFactory.createScrollPane(dynaKeyMapTable);
        dynaKeyMapTablePanel.addToCenter(dynaKeyMapTableScrollPane);

        tabbedPane.addTab("Keymap", dynaKeyMapTablePanel);

        // Action Map
        BorderLayoutPanel actionMapTablePanel = new BorderLayoutPanel();



        actionToShortcutTextArea = new JBTextArea();
        actionToShortcutTextArea.setEditable(false);
        actionToShortcutTextArea.setCaret(new DefaultCaret() {
            @Override
            public void setSelectionVisible(boolean visible) {
                // Always show selection
                super.setSelectionVisible(true);
            }
        });
        actionToShortcutTextArea.setSelectionColor(JBColor.YELLOW);


        BorderLayoutPanel actionMapToolbarPanel = new BorderLayoutPanel();

        searchActionMapTextField = new SearchTextField();
        searchActionMapTextField.setToolTipText("Find");
        searchActionMapTextField.addKeyboardListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchActionMapTextField.setText("");
                    searchActionMap(searchActionMapTextField, actionToShortcutTextArea);
                    return;
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    searchActionMap(searchActionMapTextField, actionToShortcutTextArea);
                }
            }
        });
        actionMapToolbarPanel.addToCenter(searchActionMapTextField);

        JButton searchActionMapButton = new JButton(AllIcons.Actions.Find);
        searchActionMapButton.setToolTipText("Find");
        searchActionMapButton.addActionListener((ActionEvent actionEvent) -> {
            searchActionMap(searchActionMapTextField, actionToShortcutTextArea);
        });
        actionMapToolbarPanel.addToRight(searchActionMapButton);

        actionMapTablePanel.addToTop(actionMapToolbarPanel);

        actionMapTablePanel.addToCenter(ScrollPaneFactory.createScrollPane(actionToShortcutTextArea));
        tabbedPane.addTab("Actions Map", actionMapTablePanel);

        // Unbound Actions
        unboundActionsTextArea = new JBTextArea();
        unboundActionsTextArea.setEditable(false);
        tabbedPane.addTab("Unbound Actions Map", ScrollPaneFactory.createScrollPane(unboundActionsTextArea));

        tabbedPane.setSelectedIndex(0);

        setContent(tabbedPane);

        final ActionManager actionManager = ActionManager.getInstance();
        ToolWindowEx dynaKeyMapToolWindow = (ToolWindowEx) ToolWindowManager.getInstance(project).getToolWindow("Current Keymap and Action Map");

        GenerateDynaKeyMapHtmlAction generateDynaKeyMapHtmlAction = (GenerateDynaKeyMapHtmlAction) actionManager.getAction("GenerateDynaKeyMapHtml");
        generateDynaKeyMapHtmlAction.setDynaKeyMapToolWindow(this);

        DynaKeyMapRefreshAction refreshHelmExplorerAction = (DynaKeyMapRefreshAction) actionManager.getAction("DynaKeyMapRefresh");
        refreshHelmExplorerAction.setDynaKeyMapToolWindow(this);
        Objects.requireNonNull(dynaKeyMapToolWindow).setTitleActions(java.util.List.of(generateDynaKeyMapHtmlAction, refreshHelmExplorerAction));

        refresh();
    }

    void refresh() {
        dynaKeyMapTableModel.setRowCount(0);
        actionToShortcutTextArea.setText("");
        unboundActionsTextArea.setText("");

        Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
        Map<KeyStroke, java.util.List<String>> keyStrokeToActionIdMap = new HashMap<>();
        Map<KeyStroke, java.util.List<FirstKeyStrokeAndActionId>> secondStrokeToFirstKeyStrokeAndActionIdMap = new HashMap<>();
        Collection<String> actionIdList = activeKeymap.getActionIdList();
        for (String actionId : actionIdList) {
            Shortcut[] shortcuts = activeKeymap.getShortcuts(actionId);
            for (Shortcut shortcut : shortcuts) {
                if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                    KeyStroke firstKeyStroke = keyboardShortcut.getFirstKeyStroke();
                    keyStrokeToActionIdMap.computeIfAbsent(firstKeyStroke, k -> new ArrayList<>()).add(actionId);
                    KeyStroke secondKeyStroke = keyboardShortcut.getSecondKeyStroke();
                    if (secondKeyStroke != null) {
                        secondStrokeToFirstKeyStrokeAndActionIdMap.computeIfAbsent(secondKeyStroke, k -> new ArrayList<>()).add(new FirstKeyStrokeAndActionId(firstKeyStroke, actionId));
                    }
                }
            }
        }

        ActionManager actionManager = ActionManager.getInstance();

        for (String key : ALL_KEYS) {
            // First stroke row
            int maxRowsInARow = 1;
            Vector<String> row = new Vector<>();
            row.add(key);
            row.add("");

            for (String mod : MODIFIERS) {
                KeyStroke keyStroke = KeyStroke.getKeyStroke(String.format("%s pressed %s", mod, key));
                if (keyStrokeToActionIdMap.containsKey(keyStroke)) {
                    List<String> actionIds = keyStrokeToActionIdMap.get(keyStroke);
                    Collections.sort(actionIds);
                    StringBuilder stringBuilder = new StringBuilder();
                    int rowsInCell = 0;
                    for (int i = 0; i < actionIds.size(); i++) {
                        rowsInCell++;
                        if (i == 0) {
                            stringBuilder.append("<html>");
                        }
                        if (i > 0) {
                            stringBuilder.append("<br/>");
                        }
                        stringBuilder.append("<nobr>");
                        stringBuilder.append(String.format("<code>[ %s ]</code> - ", keyStroke.toString().replaceAll("pressed ", "")));
                        String actionId = actionIds.get(i);
                        AnAction action = actionManager.getAction(actionId);
                        if (action == null) {
                            stringBuilder.append(actionId);
                        } else {
                            stringBuilder.append(action.getTemplatePresentation().getText());
                        }
                        stringBuilder.append("</nobr>");
                    }
                    row.add(stringBuilder.toString());
                    maxRowsInARow = Math.max(maxRowsInARow, rowsInCell);
                } else {
                    row.add("");
                    // No need to adjust maxRowsInARow
                }
            }
            dynaKeyMapTableModel.addRow(row);
            int lastRow = dynaKeyMapTableModel.getRowCount() - 1;
            dynaKeyMapTable.setRowHeight(lastRow, (maxRowsInARow * 24) + 24);

            maxRowsInARow = 1;
            row = new Vector<>();
            row.add("");
            row.add(key);

            boolean addRowForSecondStroke = false;
            for (String mod : MODIFIERS) {
                // Second stroke
                KeyStroke keyStroke = KeyStroke.getKeyStroke(String.format("%s pressed %s", mod, key));
                if (secondStrokeToFirstKeyStrokeAndActionIdMap.containsKey(keyStroke)) {
                    addRowForSecondStroke = true; // At least one action is mapped to this second stroke
                    List<FirstKeyStrokeAndActionId> firstKeyStrokeAndActionIds = secondStrokeToFirstKeyStrokeAndActionIdMap.get(keyStroke);
                    StringBuilder stringBuilder = new StringBuilder();
                    int rowsInCell = 0;
                    for (int i = 0; i < firstKeyStrokeAndActionIds.size(); i++) {
                        rowsInCell++;
                        if (i == 0) {
                            stringBuilder.append("<html>");
                        }
                        if (i > 0) {
                            stringBuilder.append("<br/>");
                        }
                        FirstKeyStrokeAndActionId firstKeyStrokeAndActionId = firstKeyStrokeAndActionIds.get(i);
                        stringBuilder.append("<nobr>");
                        stringBuilder.append(String.format("<code>[ %s ]</code> ", firstKeyStrokeAndActionId.firstKeyStroke().toString().replaceAll("pressed ", "")));
                        stringBuilder.append(String.format("<code>[ %s ]</code> - ", keyStroke.toString().replaceAll("pressed ", "")));
                        String actionId = firstKeyStrokeAndActionId.actionId();
                        AnAction action = actionManager.getAction(actionId);
                        if (action == null) {
                            stringBuilder.append(actionId);
                        } else {
                            stringBuilder.append(action.getTemplatePresentation().getText());
                        }
                        stringBuilder.append("</nobr>");
                    }
                    row.add(stringBuilder.toString());
                    maxRowsInARow = Math.max(maxRowsInARow, rowsInCell);
                } else {
                    row.add("");
                    // No need to adjust maxRowsInARow
                }
            }
            if (addRowForSecondStroke) {
                dynaKeyMapTableModel.addRow(row); //
                int lastRowForSecondKeyStrokeRow = dynaKeyMapTableModel.getRowCount() - 1;
                dynaKeyMapTable.setRowHeight(lastRowForSecondKeyStrokeRow, (maxRowsInARow * 24) + 24);
            }
        }

        SortedMap<String, Shortcut[]> actionNameToShortcutsMap = new TreeMap<>();
        Set<String> unboundActionsSet = new TreeSet<>();
        for (String actionId : actionIdList) {
            AnAction action = actionManager.getAction(actionId);
            Shortcut[] shortcuts = activeKeymap.getShortcuts(actionId);
            String actionKey;
            if (action == null || action.getTemplatePresentation().getText() == null) {
                actionKey = actionId;
            } else {
                actionKey = action.getTemplatePresentation().getText();
            }
            if (shortcuts.length > 0) {
                actionNameToShortcutsMap.put(actionKey, shortcuts);
            } else {
                unboundActionsSet.add(actionKey);
            }
        }

        List<String> actionHistory = actionNameToShortcutsMap.keySet().stream().filter((String s) -> s.length() > 1).toList();
        searchTextField.setHistory(actionHistory);
        searchTextField.setHistorySize(actionHistory.size());

        int lineNumber;
        StringBuilder stringBuilder;
        lineNumber = 0;
        stringBuilder = new StringBuilder();
        stringBuilder.append(String.format("---------%-60s---%s\n", "-".repeat(60), "-".repeat(60)));
        stringBuilder.append(String.format("   #     %-60s | Shortcut\n", "Action"));
        stringBuilder.append(String.format("---------%-60s---%s\n", "-".repeat(60), "-".repeat(60)));
        for (Map.Entry<String, Shortcut[]> entry : actionNameToShortcutsMap.entrySet()) {
            String actionName = entry.getKey();
            Shortcut[] shortcuts = entry.getValue();
            for (Shortcut shortcut : shortcuts) {
                if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                    stringBuilder.append(String.format("%4d     %-60s | %s\n", ++lineNumber, actionName, keyboardShortcut.toString().replaceAll("pressed ", "").replace("+", " ").replace("[", "[ ").replace("]", " ]")));
                }
            }
        }

        actionToShortcutTextArea.setText(stringBuilder.toString());
        actionToShortcutTextArea.setCaretPosition(0); // scroll to top

        // Remaining unbound Actions
        stringBuilder = new StringBuilder();
        lineNumber = 0;
        if (!unboundActionsSet.isEmpty()) {
            stringBuilder.append(String.format("---------%-60s---%s\n", "-".repeat(60), "-".repeat(60)));
            stringBuilder.append(String.format("   #     %-60s | \n", "Unbound Actions"));
            stringBuilder.append(String.format("---------%-60s---%s\n", "-".repeat(60), "-".repeat(60)));
            for (String actionName : unboundActionsSet) {
                stringBuilder.append(String.format("%4d     %-60s |\n", ++lineNumber, actionName));
            }
        }
        unboundActionsTextArea.setText(stringBuilder.toString());
        unboundActionsTextArea.setCaretPosition(0); // scroll to top
    }

    private void search() {
        String text = searchTextField.getText();
        if (text.isEmpty()) {
            tableRowSorter.setRowFilter(null);
        } else {
            tableRowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
        }
    }

    private void searchActionMap(SearchTextField searchTextField, JBTextArea textArea) {
        String text = searchTextField.getText();
        if (text.isEmpty()) {
            textArea.setCaretPosition(0);
        } else {
            int index = textArea.getText().toLowerCase().indexOf(text.toLowerCase(), textArea.getCaretPosition());
            if (index == -1) {
                // Try to wrap
                index = textArea.getText().toLowerCase().indexOf(text.toLowerCase());
                if (index == -1) {
                    // Not found
                    Toolkit.getDefaultToolkit().beep();
                } else {
                    textArea.setCaretPosition(index);
                    textArea.moveCaretPosition(index + text.length());
                }
            } else {
                textArea.setCaretPosition(index);
                textArea.moveCaretPosition(index + text.length());
            }
        }
    }

    public void generateHtml() {
        ApplicationManager.getApplication().invokeLater(() -> {
            WriteCommandAction.runWriteCommandAction(project, () -> {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("<html>\n<head>\n<title>KeyMap and Action Map</title>\n");

                stringBuilder.append("<meta charset=\"UTF-8\">\n");
                stringBuilder.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
                stringBuilder.append("<script src=\"https://cdn.tailwindcss.com\"></script>\n");

                stringBuilder.append("</head>\n<body>");

                ApplicationInfo applicationInfo = ApplicationInfo.getInstance();

                String splashImageUrl = applicationInfo.getSplashImageUrl();
                if (splashImageUrl == null) {
                    stringBuilder.append("<div class=\"text-center p-4\"><img src=\"" + splashImageUrl + "\"></img></div>\n");
                }

                stringBuilder.append("<div class=\"text-5xl text-bold p-4\">" + applicationInfo.getFullApplicationName() + " ( " + applicationInfo.getFullVersion() +  " )</div>\n");

                Date date = new Date();
                Instant instant = date.toInstant();
                ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
                String formattedDate = zonedDateTime.format(formatter);
                stringBuilder.append("<div class=\"text-3xl text-bold p-4\">As of: " + formattedDate +  "</div>\n");

                stringBuilder.append("<div class=\"text-3xl text-bold p-4\">Current Actions Map</div>\n");
                stringBuilder.append("\t<table class=\"table-auto border-collapse border\">\n");
                stringBuilder.append("\t\t<tr>\n");
                stringBuilder.append("<th class=\"text-right text-nowrap border p-1\">#</th>");
                stringBuilder.append("<th class=\"text-nowrap border p-1\">Action</th>");
                stringBuilder.append("<th class=\"text-nowrap border p-1\">Shortcut</th>\n");
                stringBuilder.append("\t\t</tr>\n");

                Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
                Collection<String> actionIdList = activeKeymap.getActionIdList();

                ActionManager actionManager = ActionManager.getInstance();
                SortedMap<String, Shortcut[]> actionNameToShortcutsMap = new TreeMap<>();
                Set<String> unboundActionsSet = new TreeSet<>();
                for (String actionId : actionIdList) {
                    AnAction action = actionManager.getAction(actionId);
                    Shortcut[] shortcuts = activeKeymap.getShortcuts(actionId);
                    String actionKey;
                    if (action == null || action.getTemplatePresentation().getText() == null) {
                        actionKey = actionId;
                    } else {
                        actionKey = action.getTemplatePresentation().getText();
                    }
                    if (shortcuts.length > 0) {
                        actionNameToShortcutsMap.put(actionKey, shortcuts);
                    } else {
                        unboundActionsSet.add(actionKey);
                    }
                }

                int lineNumber;
                lineNumber = 0;
                for (Map.Entry<String, Shortcut[]> entry : actionNameToShortcutsMap.entrySet()) {
                    String actionName = entry.getKey();
                    Shortcut[] shortcuts = entry.getValue();
                    for (Shortcut shortcut : shortcuts) {
                        if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                            stringBuilder.append(String.format("\t\t<tr><td class=\"text-right text-nowrap border p-1" + (lineNumber % 2 == 0 ? " bg-slate-100 " : "") + "\">%5d</td><td class=\"text-nowrap border p-1\">%s</td><td class=\"text-nowrap border p-1\">%s</td></tr>\n",
                                    ++lineNumber,
                                    actionName,
                                    keyboardShortcut.toString().replaceAll("pressed ", "").replace("+", " ").replace("[", "[ ").replace("]", " ]")));
                        }
                    }
                }
                stringBuilder.append("\t</table>\n");

                stringBuilder.append("<div class=\"text-3xl text-bold p-4\">Current KeyMap</div>\n");
                stringBuilder.append("\t<table class=\"table-auto border-collapse border\">\n");
                stringBuilder.append("\t\t<tr>");
                int columnCount = dynaKeyMapTableModel.getColumnCount();
                int rowCount = dynaKeyMapTableModel.getRowCount();
                for (int column = 0; column < columnCount; column++) {
                    stringBuilder.append(String.format("<th class=\"text-nowrap border p-1\">%s</th>", dynaKeyMapTableModel.getColumnName(column).replace("<html>", "")));
                }
                stringBuilder.append("</tr>\n");

                for (int row = 0; row < rowCount; row++) {
                    stringBuilder.append("\t\t<tr>");
                    for (int column = 0; column < columnCount; column++) {
                        stringBuilder.append(String.format("<td class=\"text-nowrap border p-1" + (row % 2 == 0 ? " bg-slate-100 " : "") + "\">%s</td>", String.valueOf(dynaKeyMapTableModel.getValueAt(row, column)).replace("<html>", "")));
                    }
                    stringBuilder.append("\t\t</tr>\n");
                }

                stringBuilder.append("\t</table>\n");

                if (!unboundActionsSet.isEmpty()) {
                    stringBuilder.append("<div class=\"text-3xl text-bold p-4\">Current unbound Actions</div>\n");
                    stringBuilder.append("\t<table class=\"table-auto border-collapse border\">\n");
                    stringBuilder.append("\t\t<tr>\n");
                    stringBuilder.append("<th class=\"text-right text-nowrap border p-1\">#</th>");
                    stringBuilder.append("<th class=\"text-nowrap border p-1\">Action</th>");
                    stringBuilder.append("\t\t</tr>\n");

                    lineNumber = 0;
                    for (String actionName : unboundActionsSet) {
                        stringBuilder.append(String.format("\t\t<tr><td class=\"text-right text-nowrap border p-1" + (lineNumber % 2 == 0 ? " bg-slate-100 " : "") + "\">%5d</td><td class=\"text-nowrap border p-1\">%s</td></tr>\n",
                                ++lineNumber,
                                actionName));
                    }
                    stringBuilder.append("\t</table>\n");
                }

                stringBuilder.append("</body>");
                stringBuilder.append("</html>");

                try {
                    Path currentKeyMapAndActionMapPath = Files.createTempFile("Current KeyMap and Action Map", ".html");
                    Files.writeString(currentKeyMapAndActionMapPath, stringBuilder.toString());
                    Desktop.getDesktop().browse(currentKeyMapAndActionMapPath.toUri());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    private static final Pattern KEY_MATCHER = Pattern.compile("([_\\w]+)");

    private static String kbdfy(String keys) {
        return KEY_MATCHER.matcher(keys).replaceAll("<nobr><code>[ $1 ]</code></nobr>").trim();
    }
}
