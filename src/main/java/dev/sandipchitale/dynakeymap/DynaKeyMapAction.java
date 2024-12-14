package dev.sandipchitale.dynakeymap;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class DynaKeyMapAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        Project project = anActionEvent.getProject();
        LightVirtualFile keymapMarkup = new LightVirtualFile("Current Keymap",
                FileTypeManager.getInstance().getFileTypeByExtension("markdown"),
                shortcutsTable());
        keymapMarkup.setWritable(false);
        FileEditorManagerEx fileEditorManager = (FileEditorManagerEx) FileEditorManager.getInstance(Objects.requireNonNull(project));
        FileEditor[] fileEditors = fileEditorManager.openFile(keymapMarkup, true, true);
        for (FileEditor fileEditor : fileEditors) {
            if (fileEditor instanceof TextEditorWithPreview textEditorWithPreview) {
                try {
                    // Reflection to the rescue
                    Method setLayout = textEditorWithPreview.getClass().getDeclaredMethod("setLayout", TextEditorWithPreview.Layout.class);
                    setLayout.setAccessible(true);
                    setLayout.invoke(textEditorWithPreview, TextEditorWithPreview.Layout.SHOW_PREVIEW);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignore) {
                }
            }
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    private static String shortcutsTable() {
        Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
        Map<KeyStroke, List<String>> keyStrokeToActionIdMap = new HashMap<>();
        Collection<String> actionIdList = activeKeymap.getActionIdList();
        for (String actionId : actionIdList) {
            Shortcut[] shortcuts = activeKeymap.getShortcuts(actionId);
            for (Shortcut shortcut : shortcuts) {
                if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                    KeyStroke firstKeyStroke = keyboardShortcut.getFirstKeyStroke();
                    KeyStroke secondKeyStroke = keyboardShortcut.getSecondKeyStroke();
                    keyStrokeToActionIdMap.computeIfAbsent(firstKeyStroke, k -> new ArrayList<>()).add(actionId);
                }
            }
        }
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("# Key x Modifier View\n");
        // Get all VK_ fields
        java.util.List<String> vkFields = new ArrayList<>();

        // Collect VK_ fields
        for (java.lang.reflect.Field field : KeyEvent.class.getDeclaredFields()) {
            if (field.getName().startsWith("VK_")) {
                vkFields.add(field.getName().replace("VK_", ""));
            }
        }

        // Combine VK_ fields and ASCII chars
        List<String> allKeys = new ArrayList<>(vkFields);

        // Sort the combined list
        Collections.sort(allKeys);

        // Generate header combinations (excluding zero-length item)
        String[] modifiers = {"shift", "ctrl", "alt", "shift ctrl", "shift alt", "ctrl alt", "shift ctrl alt", ""};

        // Print table header
        stringBuilder.append("|");
        for (String mod : modifiers) {
            stringBuilder.append("|");
            if (!mod.isEmpty()) {
                stringBuilder.append("**");
            }
            stringBuilder.append(mod.isEmpty() ? "none" : mod);
            if (!mod.isEmpty()) {
                stringBuilder.append("**");
            }
        }
        stringBuilder.append("|\n");

        // Print separator
        stringBuilder.append("|-");
        for (String mod : modifiers) {
            stringBuilder.append("|-");
        }
        stringBuilder.append("|\n");

        ActionManager actionManager = ActionManager.getInstance();
        // Print rows
        for (String key : allKeys) {
            // Print key
            stringBuilder.append("|").append("**").append(key).append("**");
            for (String mod : modifiers) {
                KeyStroke keyStroke = KeyStroke.getKeyStroke(String.format("%s pressed %s", mod, key));
                if (keyStrokeToActionIdMap.containsKey(keyStroke)) {
                    stringBuilder.append("|");
                    List<String> actionIds = keyStrokeToActionIdMap.get(keyStroke);
                    Collections.sort(actionIds);
                    for (int i = 0; i < actionIds.size(); i++) {
                        String actionId = actionIds.get(i);
                        AnAction action = actionManager.getAction(actionId);
                        if (action != null) {
                            if (i > 0) {
                                stringBuilder.append("<br/>");
                            }
                            stringBuilder.append(action.getTemplatePresentation().getText());
                        }
                    }
                } else {
                    stringBuilder.append("|");
                }
            }
            stringBuilder.append("|\n");
        }

        stringBuilder.append("# Action vs Keystrokes View\n");

        stringBuilder.append("|Action|Keystroke(s)|\n");
        stringBuilder.append("|-|-|\n");
        for (String actionId : actionIdList) {
            AnAction action = actionManager.getAction(actionId);
            Shortcut[] shortcuts = activeKeymap.getShortcuts(actionId);
            for (Shortcut shortcut : shortcuts) {
                if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                     stringBuilder.append("|")
                            .append(action == null ? actionId : action.getTemplatePresentation().getText())
                            .append("|")
                            .append(shortcut)
                            .append("|\n");
                }
            }
        }

        return stringBuilder.toString();
    }
}
