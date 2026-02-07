package org.flymars.devtools.midas.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.flymars.devtools.midas.security.EncryptionUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages plugin configuration with persistence
 * Application-level service - configuration is shared across all projects
 * Stored in IntelliJ's global configuration directory
 */
@Service(Service.Level.APP)
@State(name = "CommitReporterSettings", storages = @Storage(value = "midas.xml"), reportStatistic = false)
public final class ConfigManager implements PersistentStateComponent<ConfigManager.State> {

    private static final Logger LOG = Logger.getInstance(ConfigManager.class);
    private State state = new State();

    public ConfigManager() {
        LOG.info("ConfigManager initialized (application-level service)");
    }

    /**
     * Get the ConfigManager instance
     * For application-level services, returns the single global instance
     */
    public static ConfigManager getInstance(Project project) {
        return com.intellij.openapi.components.ServiceManager.getService(ConfigManager.class);
    }

    /**
     * Get the application-level instance (alias for getInstance)
     */
    public static ConfigManager getGlobalInstance() {
        return com.intellij.openapi.components.ServiceManager.getService(ConfigManager.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, this.state);
        LOG.info("ConfigManager state loaded");
    }

    // General Settings
    public boolean isEnabled() {
        return state.enabled;
    }

    public void setEnabled(boolean enabled) {
        state.enabled = enabled;
    }

    public String getStoragePath() {
        return state.storagePath;
    }

    public void setStoragePath(String path) {
        state.storagePath = path;
    }

    public List<String> getIgnoredBranches() {
        return state.ignoredBranches;
    }

    public void setIgnoredBranches(List<String> branches) {
        state.ignoredBranches = branches;
    }

    public boolean isBranchIgnored(String branch) {
        if (branch == null) {
            return false;
        }
        return state.ignoredBranches.contains(branch);
    }

    // AI Settings
    public PluginConfig.AIProvider getAIProvider() {
        return PluginConfig.AIProvider.valueOf(state.aiProvider);
    }

    public void setAIProvider(PluginConfig.AIProvider provider) {
        state.aiProvider = provider.name();
    }

    public String getApiKey() {
        String encrypted = state.apiKey;
        if (encrypted != null && !encrypted.isEmpty() && EncryptionUtil.isEncrypted(encrypted)) {
            return EncryptionUtil.decryptForApp(encrypted);
        }
        return encrypted;
    }

    public void setApiKey(String key) {
        LOG.info("ConfigManager.setApiKey called - Key length: " + (key != null ? key.length() : 0) + ", Is empty: " + (key == null || key.isEmpty()));
        if (key != null && !key.isEmpty()) {
            String encrypted = EncryptionUtil.encryptForApp(key);
            LOG.info("API Key encrypted - Encrypted length: " + encrypted.length());
            state.apiKey = encrypted;
        } else {
            state.apiKey = key;
        }
    }

    public String getModel() {
        return state.model;
    }

    public void setModel(String model) {
        state.model = model;
    }

    public String getEndpoint() {
        return state.endpoint;
    }

    public void setEndpoint(String endpoint) {
        state.endpoint = endpoint;
    }

    // Email Settings
    public String getSmtpHost() {
        return state.smtpHost;
    }

    public void setSmtpHost(String host) {
        state.smtpHost = host;
    }

    public int getSmtpPort() {
        return state.smtpPort;
    }

    public void setSmtpPort(int port) {
        state.smtpPort = port;
    }

    public String getSmtpUsername() {
        return state.smtpUsername;
    }

    public void setSmtpUsername(String username) {
        state.smtpUsername = username;
    }

    public String getSmtpPassword() {
        String encrypted = state.smtpPassword;
        if (encrypted != null && !encrypted.isEmpty() && EncryptionUtil.isEncrypted(encrypted)) {
            return EncryptionUtil.decryptForApp(encrypted);
        }
        return encrypted;
    }

    public void setSmtpPassword(String password) {
        if (password != null && !password.isEmpty()) {
            state.smtpPassword = EncryptionUtil.encryptForApp(password);
        } else {
            state.smtpPassword = password;
        }
    }

    public String getFromEmail() {
        return state.fromEmail;
    }

    public void setFromEmail(String email) {
        state.fromEmail = email;
    }

    public List<String> getToEmails() {
        return state.toEmails;
    }

    public void setToEmails(List<String> emails) {
        state.toEmails = emails;
    }

    public List<String> getCcEmails() {
        return state.ccEmails;
    }

    public void setCcEmails(List<String> emails) {
        state.ccEmails = emails;
    }

    public String getEmailSubjectFormat() {
        return state.emailSubjectFormat;
    }

    public void setEmailSubjectFormat(String format) {
        state.emailSubjectFormat = format;
    }

    public PluginConfig.ReminderSchedule getReminderSchedule() {
        try {
            return PluginConfig.ReminderSchedule.valueOf(state.reminderSchedule);
        } catch (Exception e) {
            return PluginConfig.ReminderSchedule.WEEKLY;
        }
    }

    public void setReminderSchedule(PluginConfig.ReminderSchedule schedule) {
        state.reminderSchedule = schedule.name();
    }

    public String getReminderTime() {
        return state.reminderTime;
    }

    public void setReminderTime(String time) {
        state.reminderTime = time;
    }

    // GitLab Instances Settings
    public List<GitLabInstanceConfig> getGitLabInstances() {
        return state.gitlabInstances;
    }

    public void setGitLabInstances(List<GitLabInstanceConfig> instances) {
        state.gitlabInstances = instances;
    }

    public String getActiveInstanceId() {
        return state.activeInstanceId;
    }

    public void setActiveInstanceId(String instanceId) {
        state.activeInstanceId = instanceId;
    }

    public List<String> getSelectedProjectIds() {
        return state.selectedProjectIds;
    }

    public void setSelectedProjectIds(List<String> projectIds) {
        state.selectedProjectIds = projectIds;
    }

    // Report Language Settings
    public PluginConfig.ReportLanguage getReportLanguage() {
        try {
            return PluginConfig.ReportLanguage.valueOf(state.reportLanguage);
        } catch (Exception e) {
            return PluginConfig.ReportLanguage.CHINESE;
        }
    }

    public void setReportLanguage(PluginConfig.ReportLanguage language) {
        state.reportLanguage = language.name();
    }

    /**
     * State class for XML serialization
     */
    public static class State {
        public boolean enabled = true;
        // Storage path is now fixed, not configurable by users
        // Stored in user home directory: ~/.midas/
        private String storagePath = getDefaultStoragePath();
        public List<String> ignoredBranches = List.of();

        /**
         * Get the default storage path in user home directory
         */
        public static String getDefaultStoragePath() {
            String userHome = System.getProperty("user.home");
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("mac")) {
                // macOS: ~/Library/Application Support/midas/
                return userHome + "/Library/Application Support/midas";
            } else if (os.contains("win")) {
                // Windows: %APPDATA%/midas/
                String appData = System.getenv("APPDATA");
                if (appData != null) {
                    return appData + File.separator + "midas";
                }
                return userHome + File.separator + ".midas";
            } else {
                // Linux: ~/.midas/
                return userHome + "/.midas";
            }
        }

        public String getStoragePath() {
            return storagePath;
        }

        public void setStoragePath(String path) {
            // Storage path is now fixed, ignore user changes
            this.storagePath = getDefaultStoragePath();
        }

        public String aiProvider = "OPENAI";
        public String apiKey = "";
        public String model = "gpt-3.5-turbo";
        public String endpoint = "https://api.openai.com/v1";

        public String smtpHost = "";
        public int smtpPort = 465;
        public String smtpUsername = "";
        public String smtpPassword = "";
        public String fromEmail = "";
        public List<String> toEmails = List.of();
        public List<String> ccEmails = List.of();
        public String emailSubjectFormat = "${user}的工作周报(${weekStart} - ${weekEnd})";
        public String reminderSchedule = "WEEKLY";
        public String reminderTime = "09:00";

        // Report language
        public String reportLanguage = "CHINESE";

        // GitLab instances configuration
        public List<GitLabInstanceConfig> gitlabInstances = new ArrayList<>();
        public String activeInstanceId = null;

        // Selected projects for current report
        public List<String> selectedProjectIds = new ArrayList<>();
    }

    /**
     * GitLab instance configuration
     * Access tokens are encrypted using AES-256-GCM encryption
     */
    public static class GitLabInstanceConfig {
        public String id;
        public String name;
        public String serverUrl;
        public String accessToken;  // Encrypted using EncryptionUtil
        public boolean isActive;

        // User information fetched from GitLab API
        public String userName;          // GitLab username (@username)
        public String userDisplayName;   // Full display name
        public String userEmail;         // Email address
    }
}
