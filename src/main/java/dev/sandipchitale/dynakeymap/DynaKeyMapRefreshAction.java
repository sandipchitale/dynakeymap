package dev.sandipchitale.dynakeymap;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class DynaKeyMapRefreshAction extends AnAction {
    private DynaKeyMapToolWindow dynaKeyMapToolWindow;

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        dynaKeyMapToolWindow.refresh();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    public void setDynaKeyMapToolWindow(DynaKeyMapToolWindow dynaKeyMapToolWindow) {
        this.dynaKeyMapToolWindow = dynaKeyMapToolWindow;
    }
}
