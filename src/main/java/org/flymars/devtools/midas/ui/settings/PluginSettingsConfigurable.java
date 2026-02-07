package org.flymars.devtools.midas.ui.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.flymars.devtools.midas.config.ConfigManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Configurable for plugin settings
 */
public class PluginSettingsConfigurable implements Configurable {

    private @Nullable Project project;
    private @Nullable ConfigManager configManager;
    private SettingsPanel settingsPanel;

    // Default constructor required by IntelliJ platform
    public PluginSettingsConfigurable() {
    }

    // Constructor with project parameter
    public PluginSettingsConfigurable(@Nullable Project project) {
        this.project = project;
        if (project != null) {
            this.configManager = ConfigManager.getInstance(project);
        }
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Midas";
    }

    @Override
    public JComponent createComponent() {
        if (project == null) {
            // For application-level settings, we may need to handle differently
            // For now, return a panel indicating that project is needed
            JLabel label = new JLabel("Project-specific settings. Open a project to configure.");
            return label;
        }
        if (settingsPanel == null) {
            settingsPanel = new SettingsPanel(project);
        }
        return settingsPanel.getPanel();
    }

    @Override
    public boolean isModified() {
        if (project == null || configManager == null) {
            return false;
        }
        return settingsPanel != null && settingsPanel.isModified(configManager);
    }

    @Override
    public void apply() throws ConfigurationException {
        if (project != null && configManager != null && settingsPanel != null) {
            com.intellij.openapi.diagnostic.Logger logger = com.intellij.openapi.diagnostic.Logger.getInstance(PluginSettingsConfigurable.class);

            logger.info("=== Applying Settings ===");

            // Apply settings to config manager
            settingsPanel.apply(configManager);

            // Log what was saved
            logger.info("Settings applied. AI Provider: " + configManager.getAIProvider());
            logger.info("API Key is set: " + (configManager.getApiKey() != null && !configManager.getApiKey().isEmpty()));
            logger.info("Model: " + configManager.getModel());
            logger.info("Endpoint: " + configManager.getEndpoint());

            // If tool window is open, trigger refresh via message bus
            com.intellij.openapi.wm.ToolWindowManager toolWindowManager =
                com.intellij.openapi.wm.ToolWindowManager.getInstance(project);
            com.intellij.openapi.wm.ToolWindow toolWindow = toolWindowManager.getToolWindow("Midas");
            if (toolWindow != null && toolWindow.isVisible()) {
                // Publish refresh event via project message bus
                project.getMessageBus().syncPublisher(
                    org.flymars.devtools.midas.CommitReporterKeys.SETTINGS_CHANGED_TOPIC
                ).onSettingsChanged();
            }

            logger.info("=== Settings Apply Completed ===");
        }
    }

    @Override
    public void reset() {
        if (project != null && configManager != null && settingsPanel != null) {
            settingsPanel.reset(configManager);
        }
    }

    @Override
    public void disposeUIResources() {
        settingsPanel = null;
    }
}