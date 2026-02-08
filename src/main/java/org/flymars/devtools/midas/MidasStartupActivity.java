package org.flymars.devtools.midas;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.startup.StartupManager;
import kotlin.coroutines.Continuation;
import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.gitlab.GitLabInstanceService;
import org.flymars.devtools.midas.gitlab.GitLabProjectService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Startup activity for Midas plugin initialization
 * Ensures services are properly initialized when IDE starts
 */
public class MidasStartupActivity implements ProjectActivity {
    private static final Logger LOG = Logger.getInstance(MidasStartupActivity.class);

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super kotlin.Unit> continuation) {
        LOG.info("Midas plugin starting for project: " + project.getName());

        // Initialize services to ensure they are loaded
        try {
            // Get application-level service
            ConfigManager configManager = ConfigManager.getInstance(project);
            LOG.info("ConfigManager initialized");

            // Get project-level services
            GitLabInstanceService instanceService = GitLabInstanceService.getInstance(project);
            LOG.info("GitLabInstanceService initialized with " + instanceService.getInstances().size() + " instances");

            GitLabProjectService projectService = GitLabProjectService.getInstance(project);
            LOG.info("GitLabProjectService initialized");

            // Trigger auto-load of projects in background (non-blocking)
            projectService.ensureProjectsLoaded();
            LOG.info("Midas plugin startup completed for project: " + project.getName());
        } catch (Exception e) {
            LOG.error("Error during Midas plugin startup", e);
        }

        return kotlin.Unit.INSTANCE;
    }
}
