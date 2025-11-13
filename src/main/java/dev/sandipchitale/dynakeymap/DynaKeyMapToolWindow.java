package dev.sandipchitale.dynakeymap;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.requests.SimpleDiffRequest;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

public class DynaKeyMapToolWindow extends SimpleToolWindowPanel {

    private final Project project;

    private final DefaultTableModel keyMapTableModel;
    private final JBTable keyMapTable;

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

    private static final String[] KEYMAP_COLUMNS = new String[index + MODIFIERS.length];

    static {
        KEYMAP_COLUMNS[FIRST_KEYSTROKE_KEY] = "Key in First Keystroke";
        KEYMAP_COLUMNS[SECOND_KEYSTROKE_KEY] = "Key in Second Keystroke";
        System.arraycopy(MODIFIERS, 0, KEYMAP_COLUMNS, index, MODIFIERS.length);
    }

    private final TableRowSorter<DefaultTableModel> keyMapTableRowSorter;

    private final SearchTextField keyMapSearchTextField;

    private static final int ACTION_COLUMN = 0;
    private static final int SHORTCUT_COLUMN = 1;

    private static final String[] ACTIONMAP_COLUMNS = new String[2];

    static {
        ACTIONMAP_COLUMNS[ACTION_COLUMN] = "Action";
        ACTIONMAP_COLUMNS[SHORTCUT_COLUMN] = "Shortcut";
    }

    private final JBTabbedPane tabbedPane;

    private final DefaultTableModel actionMapTableModel;
    private final JBTable actionMapTable;

    private final TableRowSorter<DefaultTableModel> actionMapTableRowSorter;


    private final DefaultComboBoxModel<String> keymapsComboBoxModel;
    private final ComboBox<String> keymapsComboBox;

    private final DefaultComboBoxModel<String> otherKeymapsComboBoxModel;
    private final ComboBox<String> otherKeymapsComboBox;

    private final SearchTextField actionMapSearchTextField;
    
    private record FirstKeyStrokeAndActionId(KeyStroke firstKeyStroke, String actionId) {
    }

    public DynaKeyMapToolWindow(@NotNull Project project) {
        super(true, true);
        this.project = project;

        tabbedPane = new JBTabbedPane();

        keyMapTableModel = new DefaultTableModel(KEYMAP_COLUMNS, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }

            @Override
            public Object getValueAt(int row, int column) {
                Vector<String> rowVector = dataVector.get(row);
                if (column == FIRST_KEYSTROKE_KEY) {
                    return rowVector.get(FIRST_KEYSTROKE_KEY);
                } else if (column == SECOND_KEYSTROKE_KEY) {
                    return rowVector.get(SECOND_KEYSTROKE_KEY);
                } else {
                    return rowVector.get(column);
                }
            }
        };

        keyMapTable = new JBTable(keyMapTableModel) {
            @Override
            public String getToolTipText(@NotNull MouseEvent event) {
                Point p = event.getPoint();
                // Locate the renderer under the event location
                int row = rowAtPoint(p);
                int column = columnAtPoint(p);
                return super.getToolTipText(event);
            }
        };

        keyMapTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
            if (mouseEvent.isAltDown() && mouseEvent.getClickCount() == 2) {
                Point p = mouseEvent.getPoint();
                int column = keyMapTable.columnAtPoint(p);
                int row = keyMapTable.rowAtPoint(p);
            }
            }
        });

        TableCellRenderer keyMapTableCellRenderer = new DefaultTableCellRenderer() {
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

        keyMapTableRowSorter = new TableRowSorter<>(keyMapTableModel);
        keyMapTable.setRowSorter(keyMapTableRowSorter);

        TableColumn column;

        column = keyMapTable.getColumnModel().getColumn(FIRST_KEYSTROKE_KEY);
        column.setMinWidth(250);
        column.setWidth(250);
        column.setMaxWidth(250);
        column.setCellRenderer(keyMapTableCellRenderer);

        column = keyMapTable.getColumnModel().getColumn(SECOND_KEYSTROKE_KEY);
        column.setMinWidth(250);
        column.setWidth(250);
        column.setMaxWidth(250);
        column.setCellRenderer(keyMapTableCellRenderer);

        JTableHeader tableHeader = keyMapTable.getTableHeader();
        Dimension tableHeaderPreferredSize = tableHeader.getPreferredSize();
        tableHeader.setPreferredSize(new Dimension(tableHeaderPreferredSize.width, 48));
        tableHeader.setToolTipText("Right click on the header to hide/show columns. Some columns may be hidden.");

        new JTableColumnSelector().install(keyMapTable);

        BorderLayoutPanel keyMapTablePanel = new BorderLayoutPanel();

        BorderLayoutPanel toolbarPanel = new BorderLayoutPanel();

        JPanel keymapsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        KeymapManagerEx keymapManager = (KeymapManagerEx) KeymapManagerEx.getInstance();
        Keymap[] allKeymaps = keymapManager.getAllKeymaps();

        keymapsComboBoxModel = new DefaultComboBoxModel<>();
        keymapsComboBoxModel.addAll(Arrays.stream(allKeymaps).map(Keymap::getName).toList());
        keymapsComboBoxModel.setSelectedItem(keymapManager.getActiveKeymap().getName());
        keymapsComboBox = new ComboBox<>(keymapsComboBoxModel);
        keymapsComboBox.setMinimumAndPreferredWidth(300);

        keymapsPanel.add(keymapsComboBox);

        JButton diffKeymapsButton = new JButton(" <- Compare Keymaps -> ");
        diffKeymapsButton.addActionListener(e -> compareSelectedKeymaps());
        keymapsPanel.add(diffKeymapsButton);

        otherKeymapsComboBoxModel = new DefaultComboBoxModel<>();
        otherKeymapsComboBoxModel.addAll(Arrays.stream(allKeymaps).map(Keymap::getName).toList());
        otherKeymapsComboBoxModel.setSelectedItem(keymapManager.getActiveKeymap().getName());
        otherKeymapsComboBox = new ComboBox<>(otherKeymapsComboBoxModel);
        otherKeymapsComboBox.setMinimumAndPreferredWidth(300);

        keymapsPanel.add(otherKeymapsComboBox);

        toolbarPanel.addToLeft(keymapsPanel);

        keyMapSearchTextField = new SearchTextField();
        keyMapSearchTextField.setToolTipText("Filter. NOTE: Text will match in hidden columns as well.");
        keyMapSearchTextField.addKeyboardListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                keyMapSearchTextField.setText("");
                search(keyMapSearchTextField, keyMapTableRowSorter);
                return;
            } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                search(keyMapSearchTextField, keyMapTableRowSorter);
            }
            }
        });
        toolbarPanel.addToCenter(keyMapSearchTextField);

        JButton searchButton = new JButton(AllIcons.General.Filter);
        searchButton.setToolTipText("Filter. NOTE: Text will match in hidden columns as well.");
        searchButton.addActionListener((ActionEvent actionEvent) -> {
            search(keyMapSearchTextField, keyMapTableRowSorter);
        });
        toolbarPanel.addToRight(searchButton);

        keyMapTablePanel.addToTop(toolbarPanel);

        JScrollPane dynaKeyMapTableScrollPane = ScrollPaneFactory.createScrollPane(keyMapTable);
        keyMapTablePanel.addToCenter(dynaKeyMapTableScrollPane);

        // Action Map
        BorderLayoutPanel actionMapTablePanel = new BorderLayoutPanel();

        actionMapTableModel = new DefaultTableModel(ACTIONMAP_COLUMNS, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }

            @Override
            public Object getValueAt(int row, int column) {
                Vector rowVector = dataVector.get(row);
                if (column == ACTION_COLUMN) {
                    return rowVector.get(ACTION_COLUMN);
                } else if (column == SHORTCUT_COLUMN) {
                    return rowVector.get(SHORTCUT_COLUMN);
                } else {
                    return rowVector.get(column);
                }
            }
        };
        actionMapTable = new JBTable(actionMapTableModel) {
            @Override
            public String getToolTipText(@NotNull MouseEvent event) {
                Point p = event.getPoint();
                // Locate the renderer under the event location
                int row = rowAtPoint(p);
                int column = columnAtPoint(p);
                return super.getToolTipText(event);
            }
        };

        actionMapTableRowSorter = new TableRowSorter<>(actionMapTableModel);
        actionMapTable.setRowSorter(actionMapTableRowSorter);

        BorderLayoutPanel actionMapToolbarPanel = new BorderLayoutPanel();

        actionMapSearchTextField = new SearchTextField();
        actionMapSearchTextField.setToolTipText("Search");
        actionMapSearchTextField.addKeyboardListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                actionMapSearchTextField.setText("");
                search(actionMapSearchTextField, actionMapTableRowSorter);
                return;
            } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                search(actionMapSearchTextField, actionMapTableRowSorter);
            }
            }
        });
        actionMapToolbarPanel.addToCenter(actionMapSearchTextField);

        JButton searchActionMapButton = new JButton(AllIcons.Actions.Find);
        searchActionMapButton.setToolTipText("Search");
        searchActionMapButton.addActionListener((ActionEvent actionEvent) -> {
            search(actionMapSearchTextField, actionMapTableRowSorter);
        });
        actionMapToolbarPanel.addToRight(searchActionMapButton);

        actionMapTablePanel.addToTop(actionMapToolbarPanel);

        actionMapTablePanel.addToCenter(ScrollPaneFactory.createScrollPane(actionMapTable));

        tabbedPane.addTab("Actions Map", actionMapTablePanel);
        tabbedPane.addTab("Keymap", keyMapTablePanel);

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

        keymapsComboBox.addActionListener((ActionEvent actionEvent) -> refresh());
    }

    void refresh() {
        keyMapTableModel.setRowCount(0);
        actionMapTableModel.setRowCount(0);

        String selectedKeymapName = String.valueOf(keymapsComboBoxModel.getSelectedItem());
        String otherSelectedKeymapName = String.valueOf(otherKeymapsComboBoxModel.getSelectedItem());

        keymapsComboBoxModel.removeAllElements();
        otherKeymapsComboBoxModel.removeAllElements();

        KeymapManagerEx keymapManager = (KeymapManagerEx) KeymapManagerEx.getInstance();

        Keymap selectedKeymap = keymapManager.getKeymap(selectedKeymapName);
        if (selectedKeymap == null) {
            selectedKeymap = keymapManager.getActiveKeymap();
        }

        Keymap otherSelectedKeymap = keymapManager.getKeymap(otherSelectedKeymapName);
        if (otherSelectedKeymap == null) {
            otherSelectedKeymap = keymapManager.getActiveKeymap();
        }

        Keymap[] allKeymaps = keymapManager.getAllKeymaps();
        keymapsComboBoxModel.addAll(Arrays.stream(allKeymaps).map(Keymap::getName).toList());
        keymapsComboBoxModel.setSelectedItem(selectedKeymap.getName());

        otherKeymapsComboBoxModel.addAll(Arrays.stream(allKeymaps).map(Keymap::getName).toList());
        otherKeymapsComboBoxModel.setSelectedItem(otherSelectedKeymap.getName());

        Map<KeyStroke, java.util.List<String>> keyStrokeToActionIdMap = new HashMap<>();
        Map<KeyStroke, java.util.List<FirstKeyStrokeAndActionId>> secondStrokeToFirstKeyStrokeAndActionIdMap = new HashMap<>();
        Collection<String> actionIdList = selectedKeymap.getActionIdList();
        for (String actionId : actionIdList) {
            Shortcut[] shortcuts = selectedKeymap.getShortcuts(actionId);
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
            keyMapTableModel.addRow(row);
            int lastRow = keyMapTableModel.getRowCount() - 1;
            keyMapTable.setRowHeight(lastRow, (maxRowsInARow * 24) + 24);

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
                keyMapTableModel.addRow(row); //
                int lastRowForSecondKeyStrokeRow = keyMapTableModel.getRowCount() - 1;
                keyMapTable.setRowHeight(lastRowForSecondKeyStrokeRow, (maxRowsInARow * 24) + 24);
            }
        }

        SortedMap<String, Shortcut[]> actionNameToShortcutsMap = new TreeMap<>();
        Set<String> unboundActionsSet = new TreeSet<>();
        for (String actionId : actionIdList) {
            AnAction action = actionManager.getAction(actionId);
            Shortcut[] shortcuts = selectedKeymap.getShortcuts(actionId);
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
        keyMapSearchTextField.setHistory(actionHistory);
        keyMapSearchTextField.setHistorySize(actionHistory.size());

        for (Map.Entry<String, Shortcut[]> entry : actionNameToShortcutsMap.entrySet()) {
            String actionName = entry.getKey();
            Shortcut[] shortcuts = entry.getValue();
            for (Shortcut shortcut : shortcuts) {
                if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                    Vector<String> row = new Vector<>();
                    row.add(actionName);
                    row.add(keyboardShortcut.toString().replaceAll("pressed ", "").replace("+", " ").replace("[", "[ ").replace("]", " ]"));
                    actionMapTableModel.addRow(row);
                }
            }
        }
        actionMapSearchTextField.setHistory(actionHistory);
        actionMapSearchTextField.setHistorySize(actionHistory.size());

        // Remaining unbound Actions
        if (!unboundActionsSet.isEmpty()) {
            for (String actionName : unboundActionsSet) {
                Vector<String> row = new Vector<>();
                row.add(actionName);
                row.add("");
                actionMapTableModel.addRow(row);
            }
        }
    }

    private void search(SearchTextField searchTextField, TableRowSorter<DefaultTableModel> tableRowSorter) {
        String text = searchTextField.getText();
        if (text.isEmpty()) {
            tableRowSorter.setRowFilter(null);
        } else {
            tableRowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
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

                String splashImageUrl;
                ApplicationInfo applicationInfo = ApplicationInfo.getInstance();
                splashImageUrl = applicationInfo.getSplashImageUrl();
                if (splashImageUrl != null) {
                    URL resourceUrl = ApplicationInfo.class.getResource(splashImageUrl);
                    if (resourceUrl != null) {
                        try {
                            Path splashImagePath = Files.createTempFile("splash", ".png");
                            Files.copy(resourceUrl.openStream(), splashImagePath, StandardCopyOption.REPLACE_EXISTING);
                            stringBuilder.append("<div class=\"p-4\"><img src=\"" + convertFileToDataUrl(splashImagePath.toFile()) + "\"></img></div>\n");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                stringBuilder.append("<div class=\"text-5xl text-bold p-4\">" + applicationInfo.getFullApplicationName() + " ( " + applicationInfo.getFullVersion() + " )</div>\n");

                Date date = new Date();
                Instant instant = date.toInstant();
                ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
                String formattedDate = zonedDateTime.format(formatter);
                stringBuilder.append("<div class=\"text-bold p-4\">As of: " + formattedDate + "</div>\n");

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

                stringBuilder.append("<div class=\"text-3xl text-bold p-4\">").append(keymapsComboBoxModel.getSelectedItem()).append(" KeyMap</div>\n");
                stringBuilder.append("\t<table class=\"table-auto border-collapse border\">\n");
                stringBuilder.append("\t\t<tr>");
                int columnCount = keyMapTableModel.getColumnCount();
                int rowCount = keyMapTableModel.getRowCount();
                for (int column = 0; column < columnCount; column++) {
                    stringBuilder.append(String.format("<th class=\"text-nowrap border p-1\">%s</th>", keyMapTableModel.getColumnName(column).replace("<html>", "")));
                }
                stringBuilder.append("</tr>\n");

                for (int row = 0; row < rowCount; row++) {
                    stringBuilder.append("\t\t<tr>");
                    for (int column = 0; column < columnCount; column++) {
                        stringBuilder.append(String.format("<td class=\"text-nowrap border p-1" + (row % 2 == 0 ? " bg-slate-100 " : "") + "\">%s</td>", String.valueOf(keyMapTableModel.getValueAt(row, column)).replace("<html>", "")));
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

    static String convertFileToDataUrl(File file) throws IOException {
        byte[] fileContent = Files.readAllBytes(file.toPath());
        String base64 = Base64.getEncoder().encodeToString(fileContent);
        String mimeType = Files.probeContentType(file.toPath());

        return "data:image/png;base64," + base64;
    }

    private static String normalizeShortcut(KeyboardShortcut keyboardShortcut) {
        return keyboardShortcut.toString()
                .replaceAll("pressed ", "")
                .replace("+", " ")
                .replace("[", "[ ")
                .replace("]", " ]");
    }

    private String buildKeymapText(Keymap keymap) {
        ActionManager actionManager = ActionManager.getInstance();
        SortedMap<String, String> lineByKey = new TreeMap<>();
        for (String actionId : keymap.getActionIdList()) {
            Shortcut[] shortcuts = keymap.getShortcuts(actionId);
            List<String> normalized = new ArrayList<>();
            for (Shortcut s : shortcuts) {
                if (s instanceof KeyboardShortcut ks) {
                    normalized.add(normalizeShortcut(ks));
                }
            }
            Collections.sort(normalized);
            AnAction action = actionManager.getAction(actionId);
            String actionName = (action == null || action.getTemplatePresentation().getText() == null)
                    ? actionId
                    : action.getTemplatePresentation().getText();
            String key = actionName + "\t(" + actionId + ")";
            String value = normalized.isEmpty() ? "" : String.join(" | ", normalized);
            lineByKey.put(key, value);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Keymap: ").append(keymap.getName()).append("\n");
        for (Map.Entry<String, String> e : lineByKey.entrySet()) {
            sb.append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }

    private void compareSelectedKeymaps() {
        String leftName = String.valueOf(keymapsComboBoxModel.getSelectedItem());
        String rightName = String.valueOf(otherKeymapsComboBoxModel.getSelectedItem());
        if (leftName == null || rightName == null) {
            Messages.showWarningDialog(project, "Please select two keymaps to compare.", "Compare Keymaps");
            return;
        }
        if (leftName.equals(rightName)) {
            Messages.showInfoMessage(project, "Selected keymaps are the same.", "Compare Keymaps");
            return;
        }
        KeymapManagerEx keymapManager = (KeymapManagerEx) KeymapManagerEx.getInstance();
        Keymap left = keymapManager.getKeymap(leftName);
        Keymap right = keymapManager.getKeymap(rightName);
        if (left == null || right == null) {
            Messages.showErrorDialog(project, "Could not resolve selected keymaps.", "Compare Keymaps");
            return;
        }
        String leftText = buildKeymapText(left);
        String rightText = buildKeymapText(right);

        String title = "Keymap Diff: " + left.getName() + " ↔ " + right.getName();
        String leftTitle = left.getName();
        String rightTitle = right.getName();

        DiffContentFactory contentFactory = DiffContentFactory.getInstance();
        var leftContent = contentFactory.create(leftText);
        var rightContent = contentFactory.create(rightText);
        SimpleDiffRequest request = new SimpleDiffRequest(
                title,
                leftContent,
                rightContent,
                leftTitle,
                rightTitle
        );
        DiffManager.getInstance().showDiff(project, request);
    }
}
