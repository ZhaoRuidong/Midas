package org.flymars.devtools.midas.email;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.config.PluginConfig;

import javax.swing.*;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler for showing reminder notifications to send weekly reports
 */
public class ReminderScheduler implements ProjectComponent {
    private static final Logger LOG = Logger.getInstance(ReminderScheduler.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final Project project;
    private final ConfigManager configManager;
    private ScheduledExecutorService scheduler;

    public ReminderScheduler(Project project) {
        this.project = project;
        this.configManager = project.getService(ConfigManager.class);
    }

    @Override
    public void projectOpened() {
        if (configManager.getReminderSchedule() == PluginConfig.ReminderSchedule.DISABLED) {
            LOG.info("Reminder scheduling is disabled");
            return;
        }

        startScheduler();
    }

    @Override
    public void projectClosed() {
        stopScheduler();
    }

    /**
     * Start the reminder scheduler
     */
    private void startScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            LOG.warn("Scheduler is already running");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "midas-Reminder-Scheduler");
            thread.setDaemon(true);
            return thread;
        });

        switch (configManager.getReminderSchedule()) {
            case WEEKLY:
                scheduleWeeklyReminder();
                break;
            case DISABLED:
                // Do nothing
                break;
            case CUSTOM:
                // Future implementation
                break;
        }

        LOG.info("Reminder scheduler started");
    }

    /**
     * Stop the reminder scheduler
     */
    private void stopScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            LOG.info("Reminder scheduler stopped");
        }
    }

    /**
     * Schedule weekly reminder (every week at configured time)
     */
    private void scheduleWeeklyReminder() {
        // Parse reminder time
        LocalTime reminderTime = parseReminderTime();
        if (reminderTime == null) {
            LOG.warn("Invalid reminder time format, using default 09:00");
            reminderTime = LocalTime.of(9, 0);
        }

        // Calculate initial delay to next configured time
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextReminder = now.with(DayOfWeek.MONDAY).with(reminderTime);

        if (now.isAfter(nextReminder)) {
            nextReminder = nextReminder.plusWeeks(1);
        }

        long initialDelay = java.time.Duration.between(now, nextReminder).toMillis();
        long period = TimeUnit.DAYS.toMillis(7); // 7 days

        scheduler.scheduleAtFixedRate(
                this::showReminder,
                initialDelay,
                period,
                TimeUnit.MILLISECONDS
        );

        LOG.info("Scheduled weekly reminder for: " + nextReminder.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
    }

    /**
     * Parse reminder time from configuration
     */
    private LocalTime parseReminderTime() {
        try {
            String timeStr = configManager.getReminderTime();
            if (timeStr != null && !timeStr.isEmpty()) {
                return LocalTime.parse(timeStr);
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse reminder time: " + configManager.getReminderTime(), e);
        }
        return LocalTime.of(9, 0); // Default 9:00 AM
    }

    /**
     * Show reminder notification
     */
    private void showReminder() {
        try {
            LOG.info("Showing weekly report reminder");

            SwingUtilities.invokeLater(() -> {
                String time = configManager.getReminderTime();
                if (time == null || time.isEmpty()) {
                    time = "09:00";
                }

                // Create notification
                Notification notification = new Notification(
                        "Midas",
                        "Weekly Report Reminder",
                        "It's time to send your weekly report!",
                        NotificationType.INFORMATION
                );

                // Add action to generate report
                notification.addAction(new NotificationAction("Generate Report") {
                    @Override
                    public void actionPerformed(AnActionEvent event, Notification notification) {
                        // Notify user to use the tool window
                        showReminderDialog();
                        notification.expire();
                    }
                });

                notification.addAction(new NotificationAction("Dismiss") {
                    @Override
                    public void actionPerformed(AnActionEvent event, Notification notification) {
                        notification.expire();
                    }
                });

                Notifications.Bus.notify(notification, project);
            });
        } catch (Exception e) {
            LOG.error("Error showing reminder", e);
        }
    }

    /**
     * Show reminder dialog with instructions
     */
    private void showReminderDialog() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel messageLabel = new JLabel("<html><h2>Weekly Report Reminder</h2>" +
                "<p>It's time to send your weekly report!</p>" +
                "<p>To generate and send your report:</p>" +
                "<ol>" +
                "<li>Open the <b>Midas</b> tool window</li>" +
                "<li>Click the <b>📝 Generate Weekly Report</b> button</li>" +
                "<li>Review and edit the report if needed</li>" +
                "<li>Click <b>📧 Send Email</b> to send</li>" +
                "</ol>" +
                "<p><i>You can configure reminder settings in:<br>" +
                "Settings → Midas → Email Configuration</i></p></html>");

        panel.add(messageLabel, BorderLayout.CENTER);

        JButton closeButton = new JButton("Got it!");
        closeButton.addActionListener(e -> {
            JDialog dialog = (JDialog) SwingUtilities.getWindowAncestor(panel);
            if (dialog != null) {
                dialog.dispose();
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(closeButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        JDialog dialog = new JDialog();
        dialog.setTitle("Weekly Report Reminder");
        dialog.setContentPane(panel);
        dialog.setModal(true);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    /**
     * Restart scheduler (e.g., when configuration changes)
     */
    public void restart() {
        stopScheduler();
        startScheduler();
    }

    /**
     * Trigger immediate reminder (for testing)
     */
    public void showNow() {
        showReminder();
    }
}
