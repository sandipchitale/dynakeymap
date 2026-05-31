package dev.sandipchitale.dynakeymap;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/** Renders two keymaps as text and shows them side by side in the IDE diff viewer. */
final class KeymapComparator {

    static void compare(Project project, Keymap left, Keymap right) {
        if (left == null || right == null) {
            Messages.showWarningDialog(project, "Please select two keymaps to compare.", "Compare Keymaps");
            return;
        }
        if (left.getName().equals(right.getName())) {
            Messages.showInfoMessage(project, "Selected keymaps are the same.", "Compare Keymaps");
            return;
        }

        DiffContentFactory contentFactory = DiffContentFactory.getInstance();
        SimpleDiffRequest request = new SimpleDiffRequest(
                "Keymap Diff: " + left.getName() + " ↔ " + right.getName(),
                contentFactory.create(buildKeymapText(left)),
                contentFactory.create(buildKeymapText(right)),
                left.getName(),
                right.getName()
        );
        DiffManager.getInstance().showDiff(project, request);
    }

    private static String buildKeymapText(Keymap keymap) {
        ActionManager actionManager = ActionManager.getInstance();
        SortedMap<String, String> lineByKey = new TreeMap<>();
        for (String actionId : keymap.getActionIdList()) {
            List<String> normalized = new ArrayList<>();
            for (Shortcut s : keymap.getShortcuts(actionId)) {
                if (s instanceof KeyboardShortcut ks) {
                    normalized.add(Shortcuts.normalizeShortcut(ks));
                }
            }
            Collections.sort(normalized);
            String actionName = Shortcuts.actionDisplayName(actionManager, actionId);
            String key = actionName + "\t(" + actionId + ")";
            lineByKey.put(key, normalized.isEmpty() ? "" : String.join(" | ", normalized));
        }

        StringBuilder sb = new StringBuilder();
        String keymapName = keymap.getName();
        Keymap activeKeymap = KeymapManagerEx.getInstanceEx().getActiveKeymap();
        if (activeKeymap.getName().equals(keymapName)) {
            keymapName = keymapName + " (active)";
        }
        Keymap keymapParent = keymap.getParent();
        if (keymapParent != null) {
            keymapName += " ( Based on " + keymapParent.getName() + " )";
        }
        sb.append("Keymap: ").append(keymapName).append("\n");
        for (Map.Entry<String, String> e : lineByKey.entrySet()) {
            sb.append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }

    private KeymapComparator() {
    }
}
