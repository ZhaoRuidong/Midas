package org.flymars.devtools.midas.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration data class for the plugin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginConfig {
    // General Settings
    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private String storagePath = ".idea/midas";

    @Builder.Default
    private List<String> ignoredBranches = List.of();

    @Builder.Default
    private ReportLanguage reportLanguage = ReportLanguage.CHINESE;

    // AI Configuration
    @Builder.Default
    private AIProvider aiProvider = AIProvider.OPENAI;

    private String apiKey;
    private String model;

    @Builder.Default
    private String endpoint = "https://api.openai.com/v1";

    // Email Configuration
    private String smtpHost;
    private int smtpPort;
    private String smtpUsername;
    private String smtpPassword;
    private String fromEmail;
    private List<String> toEmails;          // Primary recipients
    private List<String> ccEmails;          // CC recipients

    @Builder.Default
    private String emailSubjectFormat = "${user}的工作周报(${weekStart} - ${weekEnd})";

    // Reminder Configuration
    @Builder.Default
    private ReminderSchedule reminderSchedule = ReminderSchedule.WEEKLY;

    private String reminderTime;  // Format: "HH:mm" (e.g., "09:00")

    public enum AIProvider {
        OPENAI,
        CLAUDE,
        ZHIPU,
        CUSTOM
    }

    public enum ReminderSchedule {
        DISABLED,     // No reminder
        WEEKLY,       // Every week
        CUSTOM        // Custom schedule (future)
    }

    public enum ReportLanguage {
        CHINESE("中文", "zh"),
        ENGLISH("English", "en");

        private final String displayName;
        private final String localeCode;

        ReportLanguage(String displayName, String localeCode) {
            this.displayName = displayName;
            this.localeCode = localeCode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getLocaleCode() {
            return localeCode;
        }
    }
}
