package org.flymars.devtools.midas.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.flymars.devtools.midas.data.WeeklyReport;
import org.flymars.devtools.midas.report.WeeklyReportGenerator;
import org.jetbrains.annotations.NotNull;

/**
 * Action to generate a weekly report
 */
public class GenerateReportAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(GenerateReportAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }

        try {
            WeeklyReportGenerator generator = new WeeklyReportGenerator(project);
            WeeklyReport weeklyReport = generator.generateCurrentWeekReport();

            Messages.showInfoMessage(
                    "Weekly report generated successfully!",
                    "Success"
            );

            LOG.info("Report generated successfully");
        } catch (Exception e) {
            LOG.error("Error generating report", e);
            Messages.showErrorDialog(
                    "Error generating report: " + e.getMessage(),
                    "Error"
            );
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
    }
}