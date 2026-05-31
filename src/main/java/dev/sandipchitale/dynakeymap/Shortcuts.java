package dev.sandipchitale.dynakeymap;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;

import javax.swing.KeyStroke;

/** Formatting helpers for actions, keystrokes and shortcuts. */
final class Shortcuts {

    static String actionDisplayName(ActionManager actionManager, String actionId) {
        AnAction action = actionManager.getAction(actionId);
        String text = (action == null) ? null : action.getTemplatePresentation().getText();
        return text == null ? actionId : text;
    }

    static KeyStroke toKeyStroke(String modifier, String key) {
        return KeyStroke.getKeyStroke(String.format("%s pressed %s", modifier, key));
    }

    static String keyStrokeDisplay(KeyStroke keyStroke) {
        return keyStroke.toString().replaceAll("pressed ", "");
    }

    static String normalize(String shortcutText) {
        return shortcutText.replaceAll("pressed ", "").replace("+", " ").replace("[", "[ ").replace("]", " ]");
    }

    static String bracketed(KeyStroke keyStroke) {
        return "[ " + normalize(keyStroke.toString()) + " ]";
    }

    static String normalizeShortcut(KeyboardShortcut keyboardShortcut) {
        return normalize(keyboardShortcut.toString());
    }

    private Shortcuts() {
    }
}
