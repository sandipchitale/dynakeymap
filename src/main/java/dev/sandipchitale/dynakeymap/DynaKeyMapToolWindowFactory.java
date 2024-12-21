package dev.sandipchitale.dynakeymap;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class DynaKeyMapToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        DynaKeyMapToolWindow dynaKeyMapToolWindow = new DynaKeyMapToolWindow(project);
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(dynaKeyMapToolWindow.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
