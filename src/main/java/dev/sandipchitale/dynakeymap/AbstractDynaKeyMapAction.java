package dev.sandipchitale.dynakeymap;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;

abstract class AbstractDynaKeyMapAction extends AnAction {
    protected DynaKeyMapToolWindow dynaKeyMapToolWindow;

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    public void setDynaKeyMapToolWindow(DynaKeyMapToolWindow dynaKeyMapToolWindow) {
        this.dynaKeyMapToolWindow = dynaKeyMapToolWindow;
    }
}
