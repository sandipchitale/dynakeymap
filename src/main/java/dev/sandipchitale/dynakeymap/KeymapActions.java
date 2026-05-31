package dev.sandipchitale.dynakeymap;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;

import java.util.SortedMap;
import java.util.TreeMap;

/** A keymap's actions split into bound (have shortcuts) and unbound, keyed by display name (sorted). */
record KeymapActions(SortedMap<String, ActionIdAndShortCuts> bound,
                     SortedMap<String, String> unbound) {

    record ActionIdAndShortCuts(String actionId, Shortcut[] shortcuts) {
    }

    static KeymapActions collect(Keymap keymap, ActionManager actionManager) {
        SortedMap<String, ActionIdAndShortCuts> bound = new TreeMap<>();
        SortedMap<String, String> unbound = new TreeMap<>();
        for (String actionId : keymap.getActionIdList()) {
            Shortcut[] shortcuts = keymap.getShortcuts(actionId);
            String name = Shortcuts.actionDisplayName(actionManager, actionId);
            if (shortcuts.length > 0) {
                bound.put(name, new ActionIdAndShortCuts(actionId, shortcuts));
            } else {
                unbound.put(name, actionId);
            }
        }
        return new KeymapActions(bound, unbound);
    }
}
