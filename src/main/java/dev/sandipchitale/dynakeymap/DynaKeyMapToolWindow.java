package dev.sandipchitale.dynakeymap;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

public class DynaKeyMapToolWindow extends SimpleToolWindowPanel {
    private static final Logger LOG = Logger.getInstance(DynaKeyMapToolWindow.class);


    private final Project project;
    private final DefaultTableModel dynaKeyMapTableModel;
    private final JBTable dynaKeyMapTable;

    private final JBTextArea actionToShortcutTextArea;

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
        tableHeader.setToolTipText("Right click on the header to hide/show columns. Some columns are hidden.");
        JBTabbedPane tabbedPane = new JBTabbedPane();
        BorderLayoutPanel dynaKeyMapTablePanel = new BorderLayoutPanel();

        BorderLayoutPanel toolbarPanel = new BorderLayoutPanel();

        searchTextField = new SearchTextField();
        searchTextField.setToolTipText("Search. NOTE: Text will match in hidden columns as well.");
        searchTextField.addKeyboardListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchTextField.setText("");
                    tableRowSorter.setRowFilter(null);
                    return;
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    String text = searchTextField.getText();
                    if (text.isEmpty()) {
                        tableRowSorter.setRowFilter(null);
                    } else {
                        tableRowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
                    }
                }
            }
        });
        toolbarPanel.addToCenter(searchTextField);

        JButton searchButton = new JButton(AllIcons.Actions.Find);
        searchButton.setToolTipText("Search. NOTE: Text will match in hidden columns as well.   ");
        searchButton.addActionListener(e -> {
            String text = searchTextField.getText();
            if (text.isEmpty()) {
                tableRowSorter.setRowFilter(null);
            } else {
                tableRowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
            }
        });
        toolbarPanel.addToRight(searchButton);

        dynaKeyMapTablePanel.addToTop(toolbarPanel);

        JScrollPane dynaKeyMapTableScrollPane = ScrollPaneFactory.createScrollPane(dynaKeyMapTable);
        dynaKeyMapTablePanel.addToCenter(dynaKeyMapTableScrollPane);

        new JTableColumnSelector().install(dynaKeyMapTable);

        tabbedPane.addTab("Keymap", dynaKeyMapTablePanel);

        actionToShortcutTextArea = new JBTextArea();
        actionToShortcutTextArea.setEditable(false);
        tabbedPane.addTab("Action Map", ScrollPaneFactory.createScrollPane(actionToShortcutTextArea));

        tabbedPane.setSelectedIndex(0);

        setContent(tabbedPane);

        final ActionManager actionManager = ActionManager.getInstance();
        ToolWindowEx dynaKeyMapToolWindow = (ToolWindowEx) ToolWindowManager.getInstance(project).getToolWindow("Current Keymap and Action Map");

        DynaKeyMapRefreshAction refreshHelmExplorerAction = (DynaKeyMapRefreshAction) actionManager.getAction("DynaKeyMapRefresh");
        refreshHelmExplorerAction.setDynaKeyMapToolWindow(this);
        Objects.requireNonNull(dynaKeyMapToolWindow).setTitleActions(java.util.List.of(refreshHelmExplorerAction));

        refresh();
    }

    void refresh() {
        dynaKeyMapTableModel.setRowCount(0);
        actionToShortcutTextArea.setText("");

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
            Vector row = new Vector();
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
            row = new Vector();
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
        for (String actionId : actionIdList) {
            AnAction action = actionManager.getAction(actionId);
            Shortcut[] shortcuts = activeKeymap.getShortcuts(actionId);
            if (shortcuts.length > 0) {
                if (action == null || action.getTemplatePresentation().getText() == null) {
                    actionNameToShortcutsMap.put(actionId, shortcuts);
                } else {
                    actionNameToShortcutsMap.put(action.getTemplatePresentation().getText(), shortcuts);
                }
            }
        }

        List<String> actionHistory = actionNameToShortcutsMap.keySet().stream().filter((String s) -> s.length() > 1).toList();
        searchTextField.setHistory(actionHistory);
        searchTextField.setHistorySize(actionHistory.size());

        int lineNumber = 0;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format("---------%-60s---%s\n", "-".repeat(60), "-".repeat(60)));
        stringBuilder.append(String.format("   #     %-60s | Shortcut\n", "Action"));
        stringBuilder.append(String.format("---------%-60s---%s\n", "-".repeat(60), "-".repeat(60)));
        for (Map.Entry<String, Shortcut[]> entry : actionNameToShortcutsMap.entrySet()) {
            String actionName = entry.getKey();
            Shortcut[] shortcuts = entry.getValue();
            for (Shortcut shortcut : shortcuts) {
                if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                    stringBuilder.append(String.format("%4d     %-60s | %s\n", ++lineNumber, actionName, keyboardShortcut.toString().replaceAll("pressed ", "").replace("+", " ")));
                }
            }
        }
        actionToShortcutTextArea.setText(stringBuilder.toString());
        actionToShortcutTextArea.setCaretPosition(0); // scroll to top
    }

    private static final Pattern KEY_MATCHER = Pattern.compile("([_\\w]+)");

    private static String kbdfy(String keys) {
        return KEY_MATCHER.matcher(keys).replaceAll("<nobr><code>[ $1 ]</code></nobr>").trim();
    }
}
