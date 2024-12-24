package dev.sandipchitale.dynakeymap;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class GenerateDynaKeyMapHtmlAction extends AbstractDynaKeyMapAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        dynaKeyMapToolWindow.generateHtml();
    }
}