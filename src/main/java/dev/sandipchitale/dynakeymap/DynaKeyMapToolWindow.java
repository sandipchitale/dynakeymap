package dev.sandipchitale.dynakeymap;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.ui.EditKeymapsDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

import static dev.sandipchitale.dynakeymap.KeyMapLayout.*;

public class DynaKeyMapToolWindow extends SimpleToolWindowPanel {

    private static final Pattern KEY_MATCHER = Pattern.compile("([_\\w]+)");

    private final Project project;

    private final DefaultTableModel keyMapTableModel;
    private final JBTable keyMapTable;
    private final TableRowSorter<DefaultTableModel> keyMapTableRowSorter;
    private final SearchTextField keyMapSearchTextField;

    private final JBTabbedPane tabbedPane;

    private final DefaultTableModel actionMapTableModel;
    private final JBTable actionMapTable;
    private final TableRowSorter<DefaultTableModel> actionMapTableRowSorter;
    private final SearchTextField actionMapSearchTextField;

    private final DefaultComboBoxModel<Keymap> keymapsComboBoxModel;
    private final ComboBox<Keymap> keymapsComboBox;

    private final DefaultComboBoxModel<Keymap> otherKeymapsComboBoxModel;
    private final ComboBox<Keymap> otherKeymapsComboBox;

    // Guards against re-entrant refresh() calls triggered by mutating the keymap combo box model.
    private boolean refreshing;

    private record FirstKeyStrokeAndActionId(KeyStroke firstKeyStroke, String actionId) {
    }

    static class KeymapListCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value instanceof Keymap keymap) {
                if (KeymapManagerEx.getInstanceEx().getActiveKeymap().getName().equals(keymap.getName())) {
                    value = value + " (active)";
                }
                Keymap keymapParent = keymap.getParent();
                if (keymapParent != null) {
                    value += " ( Based on " + keymapParent.getName() + " )";
                }
            }
            return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
    }

    public DynaKeyMapToolWindow(@NotNull Project project) {
        super(true, true);
        this.project = project;

        tabbedPane = new JBTabbedPane();

        // Keymap tab.
        keyMapTableModel = new DefaultTableModel(KEYMAP_COLUMNS, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }
        };

        keyMapTable = new JBTable(keyMapTableModel);

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

        for (int keyStrokeColumn : new int[]{FIRST_KEYSTROKE_KEY, SECOND_KEYSTROKE_KEY}) {
            TableColumn column = keyMapTable.getColumnModel().getColumn(keyStrokeColumn);
            column.setMinWidth(KEYSTROKE_COLUMN_WIDTH);
            column.setWidth(KEYSTROKE_COLUMN_WIDTH);
            column.setMaxWidth(KEYSTROKE_COLUMN_WIDTH);
            column.setCellRenderer(keyMapTableCellRenderer);
        }

        JTableHeader tableHeader = keyMapTable.getTableHeader();
        Dimension tableHeaderPreferredSize = tableHeader.getPreferredSize();
        tableHeader.setPreferredSize(new Dimension(tableHeaderPreferredSize.width, HEADER_HEIGHT));
        tableHeader.setToolTipText("Right click on the header to hide/show columns. Some columns may be hidden.");

        new JTableColumnSelector().install(keyMapTable);

        BorderLayoutPanel keyMapTablePanel = new BorderLayoutPanel();
        BorderLayoutPanel toolbarPanel = new BorderLayoutPanel();
        JPanel keymapsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

        KeymapManagerEx keymapManager = (KeymapManagerEx) KeymapManagerEx.getInstance();
        KeymapListCellRenderer keymapListCellRenderer = new KeymapListCellRenderer();

        keymapsComboBoxModel = new DefaultComboBoxModel<>();
        keymapsComboBox = new ComboBox<>(keymapsComboBoxModel);
        keymapsComboBox.setMinimumAndPreferredWidth(400);
        keymapsComboBox.setRenderer(keymapListCellRenderer);
        keymapsPanel.add(keymapsComboBox);

        JButton diffKeymapsButton = new JButton(AllIcons.Actions.SplitVertically);
        diffKeymapsButton.setToolTipText("Compare Keymaps");
        diffKeymapsButton.addActionListener(e -> compareSelectedKeymaps());
        keymapsPanel.add(diffKeymapsButton);

        otherKeymapsComboBoxModel = new DefaultComboBoxModel<>();
        otherKeymapsComboBox = new ComboBox<>(otherKeymapsComboBoxModel);
        otherKeymapsComboBox.setMinimumAndPreferredWidth(400);
        otherKeymapsComboBox.setRenderer(keymapListCellRenderer);
        keymapsPanel.add(otherKeymapsComboBox);

        toolbarPanel.addToLeft(keymapsPanel);

        keyMapSearchTextField = new SearchTextField();
        keyMapSearchTextField.setToolTipText("Filter. NOTE: Text will match in hidden columns as well.");
        keyMapSearchTextField.addKeyboardListener(searchFieldListener(keyMapSearchTextField, keyMapTableRowSorter));
        toolbarPanel.addToCenter(keyMapSearchTextField);

        JButton searchButton = new JButton(AllIcons.General.Filter);
        searchButton.setToolTipText("Filter. NOTE: Text will match in hidden columns as well.");
        searchButton.addActionListener(e -> search(keyMapSearchTextField, keyMapTableRowSorter));
        toolbarPanel.addToRight(searchButton);

        keyMapTablePanel.addToTop(toolbarPanel);
        keyMapTablePanel.addToCenter(ScrollPaneFactory.createScrollPane(keyMapTable));

        // Action Map tab.
        BorderLayoutPanel actionMapTablePanel = new BorderLayoutPanel();

        actionMapTableModel = new DefaultTableModel(ACTIONMAP_COLUMNS, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        actionMapTable = new JBTable(actionMapTableModel) {
            @Override
            public String getToolTipText(@NotNull MouseEvent event) {
                Point p = event.getPoint();
                int row = rowAtPoint(p);
                int column = columnAtPoint(p);
                String actionId = String.valueOf(getValueAt(row, ACTION_ID_COLUMN));
                return getValueAt(row, column) + " ( Double click to edit shortcut for action id: " + actionId + ")";
            }
        };

        actionMapTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2) {
                    return;
                }
                int row = actionMapTable.rowAtPoint(event.getPoint());
                int column = actionMapTable.columnAtPoint(event.getPoint());
                if (column == ACTION_COLUMN || column == ACTION_ID_COLUMN) {
                    String actionId = actionMapTable.getValueAt(row, ACTION_ID_COLUMN).toString();
                    EditKeymapsDialog editKeymapsDialog = new EditKeymapsDialog(project, actionId, false);
                    editKeymapsDialog.setSize(600, 900);
                    editKeymapsDialog.show();
                }
            }
        });

        actionMapTableRowSorter = new TableRowSorter<>(actionMapTableModel);
        actionMapTable.setRowSorter(actionMapTableRowSorter);

        BorderLayoutPanel actionMapToolbarPanel = new BorderLayoutPanel();

        actionMapSearchTextField = new SearchTextField();
        actionMapSearchTextField.setToolTipText("Search");
        actionMapSearchTextField.addKeyboardListener(searchFieldListener(actionMapSearchTextField, actionMapTableRowSorter));
        actionMapToolbarPanel.addToCenter(actionMapSearchTextField);

        JButton searchActionMapButton = new JButton(AllIcons.Actions.Find);
        searchActionMapButton.setToolTipText("Search");
        searchActionMapButton.addActionListener(e -> search(actionMapSearchTextField, actionMapTableRowSorter));
        actionMapToolbarPanel.addToRight(searchActionMapButton);

        actionMapTablePanel.addToTop(actionMapToolbarPanel);
        actionMapTablePanel.addToCenter(ScrollPaneFactory.createScrollPane(actionMapTable));

        tabbedPane.addTab("Actions Map", actionMapTablePanel);
        tabbedPane.addTab("Keymap", keyMapTablePanel);
        tabbedPane.setSelectedIndex(0);
        tabbedPane.setToolTipTextAt(1, "Active Keymap: " + keymapManager.getActiveKeymap().getName());

        setContent(tabbedPane);

        wireTitleActions();

        refresh();

        keymapsComboBox.addActionListener(e -> refresh());
    }

    private void wireTitleActions() {
        ActionManager actionManager = ActionManager.getInstance();
        ToolWindowEx dynaKeyMapToolWindow = (ToolWindowEx) ToolWindowManager.getInstance(project).getToolWindow("Action map and Key maps");

        GenerateDynaKeyMapHtmlAction generateHtmlAction = (GenerateDynaKeyMapHtmlAction) actionManager.getAction("GenerateDynaKeyMapHtml");
        generateHtmlAction.setDynaKeyMapToolWindow(this);

        GenerateDynaKeyMapPdfAction generatePdfAction = (GenerateDynaKeyMapPdfAction) actionManager.getAction("GenerateDynaKeyMapPdf");
        if (generatePdfAction != null) {
            generatePdfAction.setDynaKeyMapToolWindow(this);
        }

        DynaKeyMapRefreshAction refreshAction = (DynaKeyMapRefreshAction) actionManager.getAction("DynaKeyMapRefresh");
        refreshAction.setDynaKeyMapToolWindow(this);

        Objects.requireNonNull(dynaKeyMapToolWindow);
        if (generatePdfAction != null) {
            dynaKeyMapToolWindow.setTitleActions(List.of(generateHtmlAction, generatePdfAction, refreshAction));
        } else {
            dynaKeyMapToolWindow.setTitleActions(List.of(generateHtmlAction, refreshAction));
        }
    }

    private KeyAdapter searchFieldListener(SearchTextField searchTextField, TableRowSorter<DefaultTableModel> sorter) {
        return new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    searchTextField.setText("");
                    search(searchTextField, sorter);
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    search(searchTextField, sorter);
                }
            }
        };
    }

    // ---- Refresh ---------------------------------------------------------

    void refresh() {
        if (refreshing) {
            return;
        }
        refreshing = true;
        try {
            doRefresh();
        } finally {
            refreshing = false;
        }
    }

    private void doRefresh() {
        Keymap selectedKeymap = (Keymap) keymapsComboBoxModel.getSelectedItem();
        Keymap otherSelectedKeymap = (Keymap) otherKeymapsComboBoxModel.getSelectedItem();

        keymapsComboBoxModel.removeAllElements();
        otherKeymapsComboBoxModel.removeAllElements();

        KeymapManagerEx keymapManager = (KeymapManagerEx) KeymapManagerEx.getInstance();
        List<Keymap> allKeymaps = Arrays.asList(keymapManager.getAllKeymaps());

        keymapsComboBoxModel.addAll(allKeymaps);
        if (selectedKeymap == null) {
            selectedKeymap = keymapManager.getActiveKeymap();
        }
        keymapsComboBoxModel.setSelectedItem(selectedKeymap);

        otherKeymapsComboBoxModel.addAll(allKeymaps);
        if (otherSelectedKeymap == null) {
            otherSelectedKeymap = keymapManager.getActiveKeymap();
        }
        otherKeymapsComboBoxModel.setSelectedItem(otherSelectedKeymap);

        keyMapTableModel.setRowCount(0);
        actionMapTableModel.setRowCount(0);

        ActionManager actionManager = ActionManager.getInstance();

        populateKeyMapTable(selectedKeymap, actionManager);
        populateActionMapTable(KeymapActions.collect(selectedKeymap, actionManager));
    }

    private void populateKeyMapTable(Keymap selectedKeymap, ActionManager actionManager) {
        Map<KeyStroke, List<String>> keyStrokeToActionIdMap = new HashMap<>();
        Map<KeyStroke, List<FirstKeyStrokeAndActionId>> secondStrokeToFirstKeyStrokeAndActionIdMap = new HashMap<>();
        for (String actionId : selectedKeymap.getActionIdList()) {
            for (Shortcut shortcut : selectedKeymap.getShortcuts(actionId)) {
                if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                    KeyStroke firstKeyStroke = keyboardShortcut.getFirstKeyStroke();
                    keyStrokeToActionIdMap.computeIfAbsent(firstKeyStroke, k -> new ArrayList<>()).add(actionId);
                    KeyStroke secondKeyStroke = keyboardShortcut.getSecondKeyStroke();
                    if (secondKeyStroke != null) {
                        secondStrokeToFirstKeyStrokeAndActionIdMap.computeIfAbsent(secondKeyStroke, k -> new ArrayList<>())
                                .add(new FirstKeyStrokeAndActionId(firstKeyStroke, actionId));
                    }
                }
            }
        }

        for (String key : ALL_KEYS) {
            // First-keystroke row.
            int maxRowsInARow = 1;
            Vector<String> row = new Vector<>();
            row.add(key);
            row.add("");
            for (String mod : MODIFIERS) {
                KeyStroke keyStroke = Shortcuts.toKeyStroke(mod, key);
                List<String> actionIds = keyStrokeToActionIdMap.get(keyStroke);
                if (actionIds != null && !actionIds.isEmpty()) {
                    Collections.sort(actionIds);
                    row.add(buildActionsHtmlForFirst(actionIds, Shortcuts.keyStrokeDisplay(keyStroke), actionManager));
                    maxRowsInARow = Math.max(maxRowsInARow, actionIds.size());
                } else {
                    row.add("");
                }
            }
            addRowAndSetHeight(keyMapTableModel, keyMapTable, row, maxRowsInARow);

            // Second-keystroke (chord) row, only added when there is at least one chord ending on this key.
            maxRowsInARow = 1;
            row = new Vector<>();
            row.add("");
            row.add(key);
            boolean addRowForSecondStroke = false;
            for (String mod : MODIFIERS) {
                KeyStroke secondKeyStroke = Shortcuts.toKeyStroke(mod, key);
                List<FirstKeyStrokeAndActionId> pairs = secondStrokeToFirstKeyStrokeAndActionIdMap.get(secondKeyStroke);
                if (pairs != null && !pairs.isEmpty()) {
                    addRowForSecondStroke = true;
                    row.add(buildActionsHtmlForChord(pairs, Shortcuts.keyStrokeDisplay(secondKeyStroke), actionManager));
                    maxRowsInARow = Math.max(maxRowsInARow, pairs.size());
                } else {
                    row.add("");
                }
            }
            if (addRowForSecondStroke) {
                addRowAndSetHeight(keyMapTableModel, keyMapTable, row, maxRowsInARow);
            }
        }
    }

    private void populateActionMapTable(KeymapActions actions) {
        List<String> actionHistory = actions.bound().keySet().stream().filter(s -> s.length() > 1).toList();
        keyMapSearchTextField.setHistory(actionHistory);
        keyMapSearchTextField.setHistorySize(actionHistory.size());

        for (Map.Entry<String, KeymapActions.ActionIdAndShortCuts> entry : actions.bound().entrySet()) {
            String actionName = entry.getKey();
            KeymapActions.ActionIdAndShortCuts actionIdAndShortCuts = entry.getValue();
            for (Shortcut shortcut : actionIdAndShortCuts.shortcuts()) {
                if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                    Vector<String> row = new Vector<>();
                    row.add(actionName);
                    row.add(Shortcuts.bracketed(keyboardShortcut.getFirstKeyStroke()));
                    KeyStroke secondKeyStroke = keyboardShortcut.getSecondKeyStroke();
                    row.add(secondKeyStroke == null ? "" : Shortcuts.bracketed(secondKeyStroke));
                    row.add(actionIdAndShortCuts.actionId());
                    actionMapTableModel.addRow(row);
                }
            }
        }

        actionMapSearchTextField.setHistory(actionHistory);
        actionMapSearchTextField.setHistorySize(actionHistory.size());

        for (Map.Entry<String, String> entry : actions.unbound().entrySet()) {
            Vector<String> row = new Vector<>();
            row.add(entry.getKey());
            row.add("");
            row.add("");
            row.add(entry.getValue());
            actionMapTableModel.addRow(row);
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

    // ---- Key map table cell rendering helpers ----------------------------

    private static String buildActionsHtmlForFirst(List<String> actionIds, String keyStrokeLabel, ActionManager actionManager) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < actionIds.size(); i++) {
            sb.append(i == 0 ? "<html>" : "<br/>");
            sb.append("<nobr>");
            sb.append(String.format("<code>[ %s ]</code> - ", keyStrokeLabel));
            sb.append(Shortcuts.actionDisplayName(actionManager, actionIds.get(i)));
            sb.append("</nobr>");
        }
        return sb.toString();
    }

    private static String buildActionsHtmlForChord(List<FirstKeyStrokeAndActionId> pairs, String secondStrokeLabel, ActionManager actionManager) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairs.size(); i++) {
            sb.append(i == 0 ? "<html>" : "<br/>");
            FirstKeyStrokeAndActionId pair = pairs.get(i);
            sb.append("<nobr>");
            sb.append(String.format("<code>[ %s ]</code> ", Shortcuts.keyStrokeDisplay(pair.firstKeyStroke())));
            sb.append(String.format("<code>[ %s ]</code> - ", secondStrokeLabel));
            sb.append(Shortcuts.actionDisplayName(actionManager, pair.actionId()));
            sb.append("</nobr>");
        }
        return sb.toString();
    }

    private static void addRowAndSetHeight(DefaultTableModel model, JBTable table, Vector<String> row, int linesPerCellMax) {
        model.addRow(row);
        int lastRow = model.getRowCount() - 1;
        table.setRowHeight(lastRow, (linesPerCellMax + 1) * ROW_LINE_HEIGHT);
    }

    private static String kbdfy(String keys) {
        return KEY_MATCHER.matcher(keys).replaceAll("<nobr><code>[ $1 ]</code></nobr>").trim();
    }

    // ---- Title bar actions -----------------------------------------------

    public void generateHtml() {
        ApplicationManager.getApplication().invokeLater(() -> {
            Object label = keymapsComboBoxModel.getSelectedItem();
            Keymap selectedKeymap = selectedKeymap(label);
            HtmlExporter.export(selectedKeymap, label, keyMapTableModel);
        });
    }

    public void generatePdf() {
        ApplicationManager.getApplication().invokeLater(() -> {
            Object label = keymapsComboBoxModel.getSelectedItem();
            PdfExporter.export(selectedKeymap(label), label);
        });
    }

    private static Keymap selectedKeymap(Object comboSelection) {
        return (comboSelection instanceof Keymap keymap) ? keymap : KeymapManager.getInstance().getActiveKeymap();
    }

    private void compareSelectedKeymaps() {
        KeymapComparator.compare(project,
                (Keymap) keymapsComboBoxModel.getSelectedItem(),
                (Keymap) otherKeymapsComboBoxModel.getSelectedItem());
    }
}
