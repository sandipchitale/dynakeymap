package dev.sandipchitale.dynakeymap;

import com.intellij.openapi.util.SystemInfo;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Column layout plus the key and modifier domain data shared by the key map tables. */
final class KeyMapLayout {

    // Keymap tab: two leading keystroke columns followed by one column per modifier combination.
    static final int FIRST_KEYSTROKE_KEY = 0;
    static final int SECOND_KEYSTROKE_KEY = 1;
    static final int KEYSTROKE_KEY_COLUMNS = 2;

    // Action Map tab.
    static final int ACTION_COLUMN = 0;
    static final int FIRST_KEYSTROKE_COLUMN = 1;
    static final int SECOND_KEYSTROKE_COLUMN = 2;
    static final int ACTION_ID_COLUMN = 3;

    static final int KEYSTROKE_COLUMN_WIDTH = 250;
    static final int HEADER_HEIGHT = 48;
    static final int ROW_LINE_HEIGHT = 24;

    static final String[] MODIFIERS;
    static final List<String> ALL_KEYS;
    static final String[] KEYMAP_COLUMNS;
    static final String[] ACTIONMAP_COLUMNS;

    static {
        // Enumerate the VK_ key names exposed by KeyEvent (e.g. VK_A -> "A").
        List<String> keys = new ArrayList<>();
        for (java.lang.reflect.Field field : KeyEvent.class.getDeclaredFields()) {
            if (field.getName().startsWith("VK_")) {
                keys.add(field.getName().replace("VK_", ""));
            }
        }
        Collections.sort(keys);
        ALL_KEYS = List.copyOf(keys);

        if (SystemInfo.isMac) {
            MODIFIERS = new String[]{"shift", "ctrl", "meta", "alt", "shift ctrl", "shift meta", "shift alt", "ctrl meta", "ctrl alt", "meta alt", "shift ctrl meta", "shift ctrl alt", "shift meta alt", "ctrl meta alt", "shift ctrl meta alt", ""};
        } else {
            MODIFIERS = new String[]{"shift", "ctrl", "alt", "shift ctrl", "shift alt", "ctrl alt", "shift ctrl alt", ""};
        }

        KEYMAP_COLUMNS = new String[KEYSTROKE_KEY_COLUMNS + MODIFIERS.length];
        KEYMAP_COLUMNS[FIRST_KEYSTROKE_KEY] = "Key in First Keystroke";
        KEYMAP_COLUMNS[SECOND_KEYSTROKE_KEY] = "Key in Second Keystroke";
        System.arraycopy(MODIFIERS, 0, KEYMAP_COLUMNS, KEYSTROKE_KEY_COLUMNS, MODIFIERS.length);

        ACTIONMAP_COLUMNS = new String[4];
        ACTIONMAP_COLUMNS[ACTION_COLUMN] = "Action";
        ACTIONMAP_COLUMNS[FIRST_KEYSTROKE_COLUMN] = "Key Stroke 1";
        ACTIONMAP_COLUMNS[SECOND_KEYSTROKE_COLUMN] = "Key Stroke 2";
        ACTIONMAP_COLUMNS[ACTION_ID_COLUMN] = "ActionId";
    }

    private KeyMapLayout() {
    }
}
