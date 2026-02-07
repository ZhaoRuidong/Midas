package org.flymars.devtools.midas.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Action to refresh commit data
 */
public class RefreshAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(RefreshAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        LOG.info("Refresh action triggered");
        // Trigger a refresh of the data
        // The tool window panel will handle the actual refresh
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        // Only enable when a project is open
        event.getPresentation().setEnabled(event.getProject() != null);
    }
}
