package org.flymars.devtools.midas.ui.notification;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Manages notifications for the plugin
 */
public class NotificationManager {

    private static final String NOTIFICATION_GROUP_ID = "Midas";

    /**
     * Show a notification when a commit is recorded
     */
    public static void showCommitRecorded(Project project, String commitHash, String message) {
        String title = "Commit Recorded";
        String content = String.format("Commit %s: %s", commitHash,
                message.length() > 50 ? message.substring(0, 47) + "..." : message);

        notify(project, title, content, NotificationType.INFORMATION);
    }

    /**
     * Show a notification when a report is generated
     */
    public static void showReportGenerated(Project project, String weekRange) {
        String title = "Report Generated";
        String content = "Weekly report for " + weekRange + " has been generated.";

        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(title, content, NotificationType.INFORMATION);

        notification.addAction(new NotificationAction("View Report") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                // TODO: Open tool window and show report
                notification.expire();
            }
        });

        notification.notify(project);
    }

    /**
     * Show a notification when an email is sent
     */
    public static void showEmailSent(Project project, String weekRange) {
        String title = "Email Sent";
        String content = "Weekly report for " + weekRange + " has been sent.";

        notify(project, title, content, NotificationType.INFORMATION);
    }

    /**
     * Show a notification when email sending fails
     */
    public static void showEmailError(Project project, String error) {
        String title = "Email Failed";
        String content = "Failed to send email: " + error;

        notify(project, title, content, NotificationType.ERROR);
    }

    /**
     * Show a general error notification
     */
    public static void showError(Project project, String title, String error) {
        notify(project, title, error, NotificationType.ERROR);
    }

    /**
     * Show a general warning notification
     */
    public static void showWarning(Project project, String title, String message) {
        notify(project, title, message, NotificationType.WARNING);
    }

    /**
     * Show a general info notification
     */
    public static void showInfo(Project project, String title, String message) {
        notify(project, title, message, NotificationType.INFORMATION);
    }

    /**
     * Internal method to show notification
     */
    private static void notify(Project project, String title, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(title, content, type)
                .notify(project);
    }
}
