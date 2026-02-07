package org.flymars.devtools.midas.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.email.EmailService;
import org.jetbrains.annotations.NotNull;

/**
 * Action to send email reports
 */
public class SendEmailAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(SendEmailAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        try {
            ConfigManager configManager = ConfigManager.getInstance(project);
            EmailService emailService = new EmailService(configManager);

            // Test email configuration
            emailService.testConnection().thenAccept(success -> {
                if (success) {
                    Messages.showInfoMessage(
                            "Email configuration is valid!",
                            "Success"
                    );
                    LOG.info("Email configuration tested successfully");
                } else {
                    Messages.showErrorDialog(
                            "Email configuration test failed",
                            "Error"
                    );
                }
            });

        } catch (Exception e) {
            LOG.error("Error testing email configuration", e);
            Messages.showErrorDialog(
                    "Error testing email configuration: " + e.getMessage(),
                    "Error"
            );
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
    }
}
