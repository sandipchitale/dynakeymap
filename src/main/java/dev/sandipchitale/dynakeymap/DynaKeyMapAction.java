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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;

public class DynaKeyMapAction extends AnAction {
    private static final String ICON_DATA_URL = "data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9Im5vIj8+CjxzdmcKICAgd2lkdGg9IjQwIgogICBoZWlnaHQ9IjQwIgogICB2aWV3Qm94PSIwIDAgNDAgNDAiCiAgIGZpbGw9Im5vbmUiCiAgIHZlcnNpb249IjEuMSIKICAgaWQ9InN2ZzMiCiAgIHNvZGlwb2RpOmRvY25hbWU9InBsdWdpbkljb24uc3ZnIgogICB4bWw6c3BhY2U9InByZXNlcnZlIgogICBpbmtzY2FwZTp2ZXJzaW9uPSIxLjQgKGU3YzNmZWIxMDAsIDIwMjQtMTAtMDkpIgogICB4bWxuczppbmtzY2FwZT0iaHR0cDovL3d3dy5pbmtzY2FwZS5vcmcvbmFtZXNwYWNlcy9pbmtzY2FwZSIKICAgeG1sbnM6c29kaXBvZGk9Imh0dHA6Ly9zb2RpcG9kaS5zb3VyY2Vmb3JnZS5uZXQvRFREL3NvZGlwb2RpLTAuZHRkIgogICB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayIKICAgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIgogICB4bWxuczpzdmc9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48c29kaXBvZGk6bmFtZWR2aWV3CiAgICAgaWQ9Im5hbWVkdmlldzMiCiAgICAgcGFnZWNvbG9yPSIjZmZmZmZmIgogICAgIGJvcmRlcmNvbG9yPSIjMDAwMDAwIgogICAgIGJvcmRlcm9wYWNpdHk9IjAuMjUiCiAgICAgaW5rc2NhcGU6c2hvd3BhZ2VzaGFkb3c9IjIiCiAgICAgaW5rc2NhcGU6cGFnZW9wYWNpdHk9IjAuMCIKICAgICBpbmtzY2FwZTpwYWdlY2hlY2tlcmJvYXJkPSIwIgogICAgIGlua3NjYXBlOmRlc2tjb2xvcj0iI2QxZDFkMSIKICAgICBpbmtzY2FwZTp6b29tPSI4LjM4ODA1NCIKICAgICBpbmtzY2FwZTpjeD0iLTU5LjEzMTcxMyIKICAgICBpbmtzY2FwZTpjeT0iMTYuNTcxMTg2IgogICAgIGlua3NjYXBlOndpbmRvdy13aWR0aD0iMzg0MCIKICAgICBpbmtzY2FwZTp3aW5kb3ctaGVpZ2h0PSIyMDg3IgogICAgIGlua3NjYXBlOndpbmRvdy14PSIwIgogICAgIGlua3NjYXBlOndpbmRvdy15PSIwIgogICAgIGlua3NjYXBlOndpbmRvdy1tYXhpbWl6ZWQ9IjEiCiAgICAgaW5rc2NhcGU6Y3VycmVudC1sYXllcj0ic3ZnMyIgLz48ZGVmcwogICAgIGlkPSJkZWZzMyI+PGxpbmVhckdyYWRpZW50CiAgICAgICBpZD0icGFpbnQwX2xpbmVhciIKICAgICAgIHgxPSIyLjk0MTkyIgogICAgICAgeTE9IjQuODk5NTUiCiAgICAgICB4Mj0iMzcuNzc3MiIKICAgICAgIHkyPSIzOS43MzQ1IgogICAgICAgZ3JhZGllbnRVbml0cz0idXNlclNwYWNlT25Vc2UiPjxzdG9wCiAgICAgICAgIG9mZnNldD0iMC4xNTkzNyIKICAgICAgICAgc3RvcC1jb2xvcj0iIzNCRUE2MiIKICAgICAgICAgaWQ9InN0b3AxIiAvPjxzdG9wCiAgICAgICAgIG9mZnNldD0iMC41NDA0IgogICAgICAgICBzdG9wLWNvbG9yPSIjM0M5OUNDIgogICAgICAgICBpZD0ic3RvcDIiIC8+PHN0b3AKICAgICAgICAgb2Zmc2V0PSIwLjkzNzM5IgogICAgICAgICBzdG9wLWNvbG9yPSIjNkI1N0ZGIgogICAgICAgICBpZD0ic3RvcDMiIC8+PC9saW5lYXJHcmFkaWVudD48L2RlZnM+PGltYWdlCiAgICAgd2lkdGg9IjI3Ljg1NzU4NCIKICAgICBoZWlnaHQ9IjI3Ljg1NzU4NCIKICAgICBwcmVzZXJ2ZUFzcGVjdFJhdGlvPSJub25lIgogICAgIHhsaW5rOmhyZWY9ImRhdGE6aW1hZ2UvcG5nO2Jhc2U2NCxpVkJPUncwS0dnb0FBQUFOU1VoRVVnQUFBSUFBQUFDQUNBWUFBQUREUG1ITEFBQUFCSE5DU1ZRSUNBZ0lmQWhraUFBQUNuVkpSRUZVJiMxMDtlSnp0blYrTUcwY2R4Nzh6dStmYWw3dllkMm1EZWlsS2hhaWlCdm9RcVVWTlZTSWFFUlVoSG5oQTkxRHhrQXFwdER4VUxSSVJUMGlBJiMxMDtDSlJRTldwVFZDRUNMd2p4d2lOS1JWNkFsNTZpRks0WGt0NDFOSlRFOTYrSzEvOTI3Zk91ZDRjSDI1ZTlQZHU3NngydjkrNStIeWthJiMxMDtqV2ZuZTVQWjMrN01qcjg3QmdpQ0lBaUNJQWlDSUFpQ0lBaUNJQWlDSUFpQ0lBaUNJQWlDSUFpQ0lBaUNJQWlDSUFpQ0lBaUMyRW13JiMxMDtzQldFRUxtTld1MWJRb2dUWURnQjRQQndta1lFNUg4QytEc0grMGQ2ZlB6UGpMRlNtTXFoQW1CRDE3L21NRndFTUJPNm1VUWNySENCJiMxMDs3NlFuSnQ0TldvRUhPVWdJa1RNTS9YY093eVU2K1lsbXhtRzRaQmo2UlNIRXZpQVZBdDBCMmxmK3Bjak5JMktEY2ZGTUpqUDVONy9qJiMxMDtmTzhBUW9pVUEzRmVXc3VJV0JBMjNoRkNwUHlPOHcyQVdxMzJkVEIyUkZyTGlIaGc3RWpETUU3NkhlWWJBSXlKSjZVMWlvZ1ZPOEM1JiMxMDs4NThFT2hRQU94Y1dMUUNFRUF5TWZVbHFtNGo0RURnbWhPZzcwZThiQUl3eEFTQWp2V0ZFTERDR2crMXoySk5BNndERTdvVUNZSStqJiMxMDt5aExLNTFmd3EzTnZvbDZ2Zy9OV1hERUdDQ0UyMTV1RUVFaW4wemh6NW1YTUhPcS9vSmpQcitEMWMyK2k1dElEQStDNm9YWDBmbkRtJiMxMDtaUndLb0xlWDJoY1UzNVhBbXFIM0hVTUFZSFYxSFQvOXlXdGdMTmhYQzBJSW5EMzdJK1NtY3FRM0JEMDM0L3NtQnA4RUJ1WHRDNytCJiMxMDtvbkFvQ2dmbnpEZFZWUVhuMy9pMVZMMDNKT3Z0NVBhRlFjb1FVS2xVd0JVRkVBQnozUTAzODk0VWdGYnMvYTNsTnIwdTliMzZ4VEI2JiMxMDt1N3g5WVlnY0FQVmFIYXFxUWdnQnhsa3JaV3hyM3BzeUJzRUU2clU2TXVNWmY3MXU5YnQ4SGxodkY3Y3ZMSkdIZ0VKQkErTU1pcUtBJiMxMDtjUTZ1S0lIemhZSkdlcEwxd2hJNUFEU3RDSVczRzhYYlkxWEF2S1lWU1UreVhsZ2lEd0dGdTBWd3pnQXdDQUFjN2NFcVFMNXdkL3QvJiMxMDtnUFNpNllVbGVnQVVOTmlPQTFWVndUdGpGK01RUXZUTlc2YlY4NVpJZW9QcmhTVnlBR2hhRVhyRndJRUhwdHFmc0ZhQWN0WTNYNjNxJiMxMDtQVytKcERlNFhsaWtUQUx6dDFkZ1dVMXd6c0ZaZTZMQ2VNOTgwN1p4NTVQbG5sZEUvdllLbXBMMTlrcjd3aUpsRW1pYUZtNHNMS0ZjJiMxMDtyTUJ4SE5laUJRZDNMV0xZdG9OeXVZcC9mN0NJWnRQdWVVV1lwb1hya3ZYMlN2dkNJbVVTQ0FDbWFXSHh4bjhHcWt0Njh2VENFdjBPJiMxMDtFT0UyMUswdTZVWFRDMHZrQURCTmErQzZ0bTJUbm1TOXNFUU9nQ09QUGpKdzNTODg5aWpwU2RZTFMrUUErTzZMcHdkYWo5Ni9meEl2JiMxMDt2UGc4NlVuV0M0c1VQMEQrempKZVAvYzJibHhmRFBSSEgzL2lHRjc1L2t0NDRPRDlwRGNFUFRkK2ZnQXBBVUFrbDFnTUljVE9oUUpnJiMxMDtqME1Cc01lUjVnb09TamYzcTVldzd0d2s2MW5MaXlpZi93WjRiUjBLN3oyZHNoMEdaOTlua0gzbEx4aWJpZTlkM0ZnbmdVbDMwOHJXJiMxMDthNjdmaFA2eko2Q3daaUE5QUxDaFl2TEhIMExKUFJpNFRqOFNOUW5jNm42OTU0TGxYZDJ2UExDYnRsZDlyMzQ0ZDI3MDlsWGZtY1VZJiMxMDtiNEx6MXJlNWlqdmw5L0x1OGpIV1JQWENOeVAzZFZCaUhRTGM3bGZPTzc0V0FLTDlnY3NOMnlrUDVxYnRYZCtySDlTZEs2TjlZK1ZiJiMxMDs0RXJid09OMis3YXp3cVhuTGxlMVlPc0JNb2d0QUxxNVh6Y2RMcHgzZGNQeXdPN2NQdlc5NVNIY3ZsSGE1OVIxcEJRYm9tM2xGcUx6JiMxMDtwcys5bEhmeW5uS0YyM0RxT25obVl1am5KYlloWUt2N05WenE3NlpObnA2bHJZSXhRRkV3VUdwcHEwTTdGMjVpQ3dCTks5N3JOTzdwJiMxMDtSSjk4VHpkdGd2V3M0dHJBSjE5Uld2WGpJTFlob0hDM0NNNFl3RHp1MWdENW5tN2FCT3RaMmhyYVp0L1c4YTVCUDBqZTBuWmJBQ1RjJiMxMDtUU3RiejlKVzRZalcxZXdkNi8zeWpyMUxod0M5WXJRZW9SZ0xuUHE1YVpPcTF5eXRvV3A0SHYwQ3BoVzlWVDhPWXAwRWJycGZGWS9iJiMxMDt0VWMrc0pzMmdYcVd0b3JsTmFCcHQ1L3pPY0NWMWxYZUw5KzBnZnpxTHIwRERNT2RtMVE5cTdnRzB3S3Uzd1RLVmNCeHRpNENjYytpJiMxMDtrT01BNVVycitLYTlTeWVCU0xDYlZyWmVaeEpuV2NEU3JWQnlXK29QbS9qdUFBbDMwOHJXaTNvTDMzVkRRTkxkdExMMWhOVVlXQThBJiMxMDtoQjM4QzZRb3hCWUFTWGZUeXRZYmYrVHhnZlVBWU9MbzhVajFneEpiQUNUZFRTdGI3OUR6WjhFems2SDFBRUNkUElDWjA3OFlxRzVZJiMxMDtZdlVESk4xTksxdXZzWHdUdHkrOEJHTnhMcEFld0xELzJGZngyZSs5aGJIN0h3cFlwei9rQ3Q3akpNb1FRaVFQQ29BOURnWEFIbWRvJiMxMDtLNEhkOXRLTnN2ZHRwVkxCM053Y0FBRlZWZG9PbXM2M2NraEUzbkVjQ0FjNC90UnhURTcyZndLUTNUK0RNcFJKb0d4M3JhN3JtSnQ3JiMxMDtENnFxaEczS1NHZzJiVHo5OUplUlRxZTdsZzl6YjJBdkk1a0V5dDc3OXYyclY1RktqVUhoU3N1dG0vRDB2bFFLVjY1Y2lhMS9vakNVJiMxMDtJY0IzTDkwdWFmKzlid1VVcm13NmNRUUV1R3ZmdkNUbUhkdUpzWDhHUjNvQUJOcEx0MGZhelYxcm1pWlM5NlUyYmJNdFJ3NVBmRjdoJiMxMDt2TlgyVkdxby9STVY2UUhRY2RlMnJvVE42WXpyU25Hblc4c0xCUTBQalIvYW9xZnJSc3VydDJtYTYvaW9YS1BYWmo1WjVicHVZSHA2JiMxMDthd0RJN3Arb1NKOERiSFhYZXZlNjlicHIvZmUrTlF3RGlxSzBiRmhjYVkrTm5YekhucFhNY3NNd2h0NC9VWkYvQitqbHJ2WHVkZHVsJiMxMDt2SnV4UXRkcnJwYzBoVS9NSnF0YzEydmJqcERkUDFFWnloQWcwMTJyNnpxRVl5T1ZVbnUvWXNONnZHSXp3bkt6WVVMWDlhSDNUMVNrJiMxMDtCNERzdlcrTm1vSEdSZzB6RHg2RWE0M2szc3QxcnRseWtzcTFZaG5welBaZmNCLzEzc0JlcE04QlpPOTlxK3NHUHZyb3YyZzBySzF2JiMxMDs3VzVhczF0NXhsaGl5aTNMd3RMU0xlajY5am5BcVBjRzlqS1VPMERIWGZ1NXp4L0c1UDRKcUdOcWwwVkhCc3RxUXE4YStQam1KejNkJiMxMDt0WVp1b05FdzhkN2NQL0hZRjQ5Z2FpcUxWR3BNZHJPbFlKb1dTcVVLRnE0dHdyS2FNTG9FZ096K2ljcFFKb0dRNks3dFRLUWFEUk5YJiMxMDszNzhtcVpYeDBHc1NpQkh1RGV4Ri9tT2daSGR0dDRuVVRxRmIyMGU5TjdBWDZRRWcyMTFyV1lQcmpSckgyYjRjUE9xOWdiMUlEd0RaJiMxMDs3dHBETTNMMnloa0YzWDR1ZHRSN0EzdVJIZ0N5M2JWZmVlWUV4c2FTT2VuclJ6cWR4c21USjdaOVB1cTlnYjBNeFE4ZzIxMnJGVXQ0JiMxMDs5OUpmc2JJU3ordFNVWG40NGNONDl0bVRQVTBodzl3YjJBdTVndmM0NUFvbSt1SWJBSVhDM1hoYVFrZ255TG56RFlCU1VmN2lBeEVQJiMxMDtRYzZkYndDVXF4Vlo3U0ZpcGxncSt4N2pHd0IzYnQrQmFacXkya1RFaEdtYXlPZHYreDduR3dEMWVtMzkyclVQWkxXTGlJbUZoWGswJiMxMDtHbzExditQOG53SVk1dlA1UE5iV2RzWXpPQUdzcmExaGVYa1pBbUxlNzlnZ2o0SHpqREVzTE14amFXbXg2L28ya1F4czI4YlMwaUlXJiMxMDtGbHJubllIN0JvRHYxOEdNT1JlRkVLOENTSDM2NlRwTXM0RnNOb2QwT29QSnlVbGtNbkp0eWtRNDZ2VTZxdFVxTmpZMlVDNXBLSlU3JiMxMDtFeittMnc1KzYxYy8wTHRKZi9yakgzNll6cVIvbnNsa29DZ3FTcVV5aXNYV1AwMHJ0ZDluSStLR2M0NWNiaittcDNQSTViS1luczdCJiMxMDtza3hzYkd5Z1Z0OTQ5Ym5udm4zZVZ5UFFIMUpUNThEd0w3UmZZSFFUOVAwMllqaDArcCsxZjNpZ25aOVgxZFJiUWVvSENvRFoyVms3JiMxMDtXNncreGNCZUV3STIydTlDdEtDcmY3UzArcjk5UG13Qi9ES2JxeDZmblowTlpCNElmZmxldm56NXlWSkpPMTBxVm80V2k2V2p4V0xsJiMxMDtBRTBNUndQbkhGTlQyY0xVVlBiRzFIVDJ3MngyNnZlblRwMEt1aUVSUVJBRVFSQUVRUkFFUVJBRVFSQUVRUkI3Z1A4RElDWjdLWS9VJiMxMDsxSGdBQUFBQVNVVk9SSzVDWUlJPSYjMTA7IgogICAgIGlkPSJpbWFnZTEiCiAgICAgeD0iNi4wNzEyMDgiCiAgICAgeT0iNC4wNzEyMDgiCiAgICAgc3R5bGU9InN0cm9rZS13aWR0aDo0LjU5NDgiIC8+PC9zdmc+Cg==";

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        Project project = anActionEvent.getProject();
        LightVirtualFile keymapMarkup = new LightVirtualFile("Keymap as of " + DateFormatUtil.formatDateTime(System.currentTimeMillis()),
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

    private static record FirstKeyStrokeAndActionId(KeyStroke firstKeyStroke, String actionId) {
    }

    private static String shortcutsTable() {
        Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
        Map<KeyStroke, List<String>> keyStrokeToActionIdMap = new HashMap<>();
        Map<KeyStroke, List<FirstKeyStrokeAndActionId>> firstStrokeToFirstKeyStrokeAndActionIdMap = new HashMap<>();
        Collection<String> actionIdList = activeKeymap.getActionIdList();
        for (String actionId : actionIdList) {
            Shortcut[] shortcuts = activeKeymap.getShortcuts(actionId);
            for (Shortcut shortcut : shortcuts) {
                if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                    KeyStroke firstKeyStroke = keyboardShortcut.getFirstKeyStroke();
                    keyStrokeToActionIdMap.computeIfAbsent(firstKeyStroke, k -> new ArrayList<>()).add(actionId);
                    KeyStroke secondKeyStroke = keyboardShortcut.getSecondKeyStroke();
                    if (secondKeyStroke != null) {
                        firstStrokeToFirstKeyStrokeAndActionIdMap.computeIfAbsent(secondKeyStroke, k -> new ArrayList<>()).add(new FirstKeyStrokeAndActionId(firstKeyStroke, actionId));
                    }
                }
            }
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n# Key x Modifier View\n");
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
        String[] modifiers;
        if (SystemInfo.isMac) {
            modifiers = new String[]{"shift", "ctrl", "meta", "alt", "shift ctrl", "shift meta", "shift alt", "ctrl meta", "ctrl alt", "meta alt", "shift ctrl meta", "shift ctrl alt", "shift meta alt", "ctrl meta alt", "shift ctrl meta alt", ""};
        } else {
            modifiers = new String[]{"shift", "ctrl", "alt", "shift ctrl", "shift alt", "ctrl alt", "shift ctrl alt", ""};
        }
        // Print table header
        stringBuilder.append("|![Current Key Map](").append(ICON_DATA_URL).append(")");
        for (String mod : modifiers) {
            stringBuilder.append("|<nobr>");
            if (!mod.isEmpty()) {
                stringBuilder.append("**");
            }
            stringBuilder.append(mod.isEmpty() ? "none" : kbdfy(mod));
            if (!mod.isEmpty()) {
                stringBuilder.append("**");
            }
            stringBuilder.append("</nobr>");
        }
        stringBuilder.append("|\n");

        // Print separator
        stringBuilder.append("|-");
        for (String mod : modifiers) {
            stringBuilder.append("|-");
        }
        stringBuilder.append("|\n");

        stringBuilder.append("|**Keys**|||||||||\n");

        ActionManager actionManager = ActionManager.getInstance();
        for (String key : allKeys) {
            stringBuilder.append("|").append("**").append(kbdfy(key)).append("**");
            for (String mod : modifiers) {
                // Fist stroke only
                KeyStroke keyStroke = KeyStroke.getKeyStroke(String.format("%s pressed %s", mod, key));
                if (keyStrokeToActionIdMap.containsKey(keyStroke)) {
                    stringBuilder.append("|");
                    List<String> actionIds = keyStrokeToActionIdMap.get(keyStroke);
                    Collections.sort(actionIds);
                    for (int i = 0; i < actionIds.size(); i++) {
                        if (i > 0) {
                            stringBuilder.append("<br/>");
                        }
                        String actionId = actionIds.get(i);
                        AnAction action = actionManager.getAction(actionId);
                        stringBuilder.append("<nobr>");
                        stringBuilder.append("[").append(kbdfy(keyStroke.toString().replaceAll("pressed ", ""))).append("]").append(" -> ");
                        if (action == null) {
                            stringBuilder.append(actionId);
                        } else {
                            stringBuilder.append(action.getTemplatePresentation().getText());
                        }
                        stringBuilder.append("</nobr>");
                    }
                } else {
                    stringBuilder.append("|");
                }
            }
            stringBuilder.append("|\n");

            stringBuilder.append("|").append("**").append(kbdfy(key)).append("**");
            for (String mod : modifiers) {
                // Fist stroke only
                KeyStroke keyStroke = KeyStroke.getKeyStroke(String.format("%s pressed %s", mod, key));
                if (firstStrokeToFirstKeyStrokeAndActionIdMap.containsKey(keyStroke)) {
                    stringBuilder.append("|");
                    List<FirstKeyStrokeAndActionId> firstKeyStrokeAndActionIds = firstStrokeToFirstKeyStrokeAndActionIdMap.get(keyStroke);
                    for (int i = 0; i < firstKeyStrokeAndActionIds.size(); i++) {
                        if (i > 0) {
                            stringBuilder.append("<br/>");
                        }
                        FirstKeyStrokeAndActionId firstKeyStrokeAndActionId = firstKeyStrokeAndActionIds.get(i);
                        String actionId = firstKeyStrokeAndActionId.actionId();
                        AnAction action = actionManager.getAction(actionId);
                        stringBuilder.append("<nobr>");
                        stringBuilder.append("[").append(kbdfy(firstKeyStrokeAndActionId.firstKeyStroke().toString().replaceAll("pressed ", ""))).append("]").append(" ");
                        stringBuilder.append("[").append(kbdfy(keyStroke.toString().replaceAll("pressed ", ""))).append("]").append(" -> ");
                        if (action == null) {
                            stringBuilder.append(actionId);
                        } else {
                            stringBuilder.append(action.getTemplatePresentation().getText());
                        }
                        stringBuilder.append("</nobr>");
                    }
                } else {
                    stringBuilder.append("|");
                }
            }
            stringBuilder.append("|\n");
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

        stringBuilder.append("# Action vs Keystrokes View\n");

        stringBuilder.append("|Action|![Key Strokes](").append(ICON_DATA_URL).append(")|\n");
        stringBuilder.append("|-|-|\n");

        for (Map.Entry<String, Shortcut[]> entry : actionNameToShortcutsMap.entrySet()) {
            String actionName = entry.getKey();
            Shortcut[] shortcuts = entry.getValue();
            for (Shortcut shortcut : shortcuts) {
                if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                    stringBuilder.append("|")
                            .append(actionName)
                            .append("|")
                            .append(kbdfy(shortcut.toString().replaceAll("pressed ", "").replace("+", " ")))
                            .append("|\n");
                }
            }
        }

        return stringBuilder.toString();
    }

    private static Pattern KEY_MATCHER = Pattern.compile("([_\\w]+)");

    private static String kbdfy(String keys) {
        return KEY_MATCHER.matcher(keys).replaceAll("<kbd>$1</kbd>").trim();
    }
}
