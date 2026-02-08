package org.flymars.devtools.midas.gitlab;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.gitlab.model.GitLabInstance;
import org.flymars.devtools.midas.gitlab.model.GitLabProject;
import org.flymars.devtools.midas.security.EncryptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for managing multiple GitLab instances
 * Handles instance CRUD operations and project fetching
 */
@Service(Service.Level.PROJECT)
public final class GitLabInstanceService {
    private static final Logger LOG = Logger.getInstance(GitLabInstanceService.class);

    private final Project project;
    private final ConfigManager configManager;
    private final GitLabApiClient apiClient;
    private final List<GitLabInstance> instancesCache;

    public GitLabInstanceService(Project project) {
        this.project = project;
        this.configManager = ConfigManager.getInstance(project);
        this.apiClient = new GitLabApiClient();
        this.instancesCache = new ArrayList<>();
        loadInstancesFromConfig();
    }

    public static GitLabInstanceService getInstance(Project project) {
        return project.getService(GitLabInstanceService.class);
    }

    // ==================== Instance Management ====================

    /**
     * Get all configured GitLab instances
     */
    public List<GitLabInstance> getInstances() {
        synchronized (instancesCache) {
            return new ArrayList<>(instancesCache);
        }
    }

    /**
     * Get instance by ID
     */
    @Nullable
    public GitLabInstance getInstance(String instanceId) {
        synchronized (instancesCache) {
            return instancesCache.stream()
                    .filter(i -> i.getId().equals(instanceId))
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Get the currently active instance
     */
    @Nullable
    public GitLabInstance getActiveInstance() {
        synchronized (instancesCache) {
            return instancesCache.stream()
                    .filter(GitLabInstance::isActive)
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Set the active instance
     */
    public void setActiveInstance(String instanceId) {
        synchronized (instancesCache) {
            instancesCache.forEach(i -> i.setActive(i.getId().equals(instanceId)));
        }
        saveInstancesToConfig();
    }

    /**
     * Add a new GitLab instance
     * @return true if added successfully, false otherwise
     */
    public boolean addInstance(@NotNull GitLabInstance instance) {
        if (!instance.isValid()) {
            return false;
        }

        // Generate ID if not provided
        if (instance.getId() == null || instance.getId().isEmpty()) {
            instance.setId(UUID.randomUUID().toString());
        }

        // Check for duplicate ID
        synchronized (instancesCache) {
            if (instancesCache.stream().anyMatch(i -> i.getId().equals(instance.getId()))) {
                return false;
            }

            // If this is the first instance, make it active
            if (instancesCache.isEmpty()) {
                instance.setActive(true);
            } else {
                instance.setActive(false);
            }

            instancesCache.add(instance);
        }

        saveInstancesToConfig();
        return true;
    }

    /**
     * Add a new GitLab instance and fetch user info
     * This method adds the instance and automatically fetches the current user information
     * @return CompletableFuture that completes when user info is fetched
     */
    public CompletableFuture<Void> addInstanceAndFetchUserInfo(@NotNull GitLabInstance instance) {
        boolean added = addInstance(instance);
        if (!added) {
            return CompletableFuture.supplyAsync(() -> {
                throw new IllegalArgumentException("Failed to add instance");
            });
        }

        // Fetch and update user info
        return fetchAndUpdateCurrentUser(instance);
    }

    /**
     * Update an existing instance
     * @return true if updated successfully, false if instance not found
     */
    public boolean updateInstance(@NotNull GitLabInstance instance) {
        synchronized (instancesCache) {
            for (int i = 0; i < instancesCache.size(); i++) {
                if (instancesCache.get(i).getId().equals(instance.getId())) {
                    instancesCache.set(i, instance);
                    saveInstancesToConfig();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Remove an instance
     * @return true if removed successfully, false if instance not found
     */
    public boolean removeInstance(String instanceId) {
        synchronized (instancesCache) {
            boolean removed = instancesCache.removeIf(i -> i.getId().equals(instanceId));

            // If we removed the active instance, set a new one
            if (removed && getActiveInstance() == null && !instancesCache.isEmpty()) {
                instancesCache.get(0).setActive(true);
            }

            if (removed) {
                saveInstancesToConfig();
            }
            return removed;
        }
    }

    /**
     * Test connection to a GitLab instance
     * Also fetches and stores the current user information (userName, userDisplayName, userEmail)
     */
    public CompletableFuture<Boolean> testConnection(GitLabInstance instance) {
        return apiClient.getCurrentUser(instance)
                .thenApply(user -> {
                    if (user != null) {
                        instance.setUserName(user.username);
                        instance.setUserDisplayName(user.name);
                        instance.setUserEmail(user.email);
                        saveInstancesToConfig();
                        LOG.info("Connection test successful for " + instance.getName() +
                                ". User: " + user.username + " (" + user.name + ")");
                        return true;
                    }
                    LOG.error("Connection test failed for " + instance.getName());
                    return false;
                });
    }

    /**
     * Fetch all projects from a GitLab instance
     */
    public CompletableFuture<List<GitLabProject>> fetchProjects(GitLabInstance instance) {
        if (!instance.isValid()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return apiClient.fetchProjects(instance);
    }

    /**
     * Fetch current user info from instance and update the instance
     * This method fetches and stores complete user information (userName, userDisplayName, userEmail)
     */
    public CompletableFuture<Void> fetchAndUpdateCurrentUser(GitLabInstance instance) {
        return apiClient.getCurrentUser(instance)
                .thenAccept(user -> {
                    if (user != null) {
                        instance.setUserName(user.username);
                        instance.setUserDisplayName(user.name);
                        instance.setUserEmail(user.email);
                        saveInstancesToConfig();
                        LOG.info("Updated user info for instance " + instance.getName() +
                                ": " + user.username + " (" + user.name + ")");
                    }
                });
    }

    // ==================== Private Methods ====================

    /**
     * Load instances from configuration
     * User info is loaded from config and automatically fetched if missing
     */
    private void loadInstancesFromConfig() {
        List<ConfigManager.GitLabInstanceConfig> configs = configManager.getGitLabInstances();

        synchronized (instancesCache) {
            instancesCache.clear();

            for (ConfigManager.GitLabInstanceConfig config : configs) {
                GitLabInstance instance = GitLabInstance.builder()
                        .id(config.id)
                        .name(config.name)
                        .serverUrl(config.serverUrl)
                        .accessToken(getAccessTokenFromPasswordSafe(config.id))
                        .isActive(config.isActive)
                        .userName(config.userName)
                        .userDisplayName(config.userDisplayName)
                        .userEmail(config.userEmail)
                        .build();

                // Add all instances, even invalid ones, so they show up in UI
                // User can see and fix configuration issues
                instancesCache.add(instance);
            }

            // Ensure there's an active instance
            if (!instancesCache.isEmpty() && getActiveInstance() == null) {
                instancesCache.get(0).setActive(true);
            }
        }

        // Auto-fetch missing user info in background
        fetchMissingUserInfo();
    }

    /**
     * Save instances to configuration
     */
    private void saveInstancesToConfig() {
        synchronized (instancesCache) {
            List<ConfigManager.GitLabInstanceConfig> configs = instancesCache.stream()
                    .map(this::toConfig)
                    .collect(Collectors.toList());

            configManager.setGitLabInstances(configs);
        }
    }

    /**
     * Convert GitLabInstance to config object
     * Includes encrypted access token and user info for offline use
     */
    private ConfigManager.GitLabInstanceConfig toConfig(GitLabInstance instance) {
        ConfigManager.GitLabInstanceConfig config = new ConfigManager.GitLabInstanceConfig();
        config.id = instance.getId();
        config.name = instance.getName();
        config.serverUrl = instance.getServerUrl();
        config.isActive = instance.isActive();
        config.userName = instance.getUserName();
        config.userDisplayName = instance.getUserDisplayName();
        config.userEmail = instance.getUserEmail();

        // Encrypt and save access token directly in config
        // Use application-level encryption since ConfigManager is app-level
        String token = instance.getAccessToken();
        if (token != null && !token.isEmpty()) {
            config.accessToken = EncryptionUtil.encryptForApp(token);
        } else {
            config.accessToken = token;
        }

        return config;
    }

    /**
     * Get access token from encrypted storage
     */
    private String getAccessTokenFromPasswordSafe(String instanceId) {
        List<ConfigManager.GitLabInstanceConfig> configs = configManager.getGitLabInstances();
        for (ConfigManager.GitLabInstanceConfig config : configs) {
            if (config.id.equals(instanceId)) {
                String encryptedToken = config.accessToken;
                if (encryptedToken != null && !encryptedToken.isEmpty()) {
                    if (EncryptionUtil.isEncrypted(encryptedToken)) {
                        // Try application-level decryption first (new format)
                        String decrypted = EncryptionUtil.decryptForApp(encryptedToken);
                        if (decrypted != null && !decrypted.isEmpty()) {
                            return decrypted;
                        }

                        // Fallback: try project-level decryption (old format for migration)
                        decrypted = EncryptionUtil.decrypt(encryptedToken, project);
                        if (decrypted != null && !decrypted.isEmpty()) {
                            // Migrate to new encryption format
                            LOG.info("Migrating token from project-level to app-level encryption for instance: " + config.name);
                            encryptAndSaveAccessToken(config.id, decrypted);
                            return decrypted;
                        }

                        // Both decryption methods failed
                        LOG.warn("Failed to decrypt access token for instance: " + config.name);
                        return null;
                    } else {
                        // Migrate unencrypted token to encrypted
                        String decryptedToken = encryptedToken;
                        encryptAndSaveAccessToken(config.id, decryptedToken);
                        return decryptedToken;
                    }
                }
                return null;
            }
        }
        return null;
    }

    /**
     * Encrypt and save access token (for migration)
     */
    private void encryptAndSaveAccessToken(String instanceId, String token) {
        List<ConfigManager.GitLabInstanceConfig> configs = configManager.getGitLabInstances();
        for (ConfigManager.GitLabInstanceConfig config : configs) {
            if (config.id.equals(instanceId)) {
                if (token != null && !token.isEmpty()) {
                    config.accessToken = EncryptionUtil.encryptForApp(token);
                }
                break;
            }
        }
    }

    /**
     * Fetch missing user info for instances that don't have complete user information
     * This runs asynchronously in the background to avoid blocking initialization
     */
    private void fetchMissingUserInfo() {
        List<GitLabInstance> instancesNeedingUserInfo = new ArrayList<>();

        synchronized (instancesCache) {
            for (GitLabInstance instance : instancesCache) {
                if (instance.isValid() && !hasCompleteUserInfo(instance)) {
                    instancesNeedingUserInfo.add(instance);
                }
            }
        }

        if (!instancesNeedingUserInfo.isEmpty()) {
            LOG.info("Found " + instancesNeedingUserInfo.size() + " instances missing user info, fetching in background...");

            // Fetch user info for each instance in parallel
            for (GitLabInstance instance : instancesNeedingUserInfo) {
                fetchAndUpdateCurrentUser(instance)
                        .thenRun(() -> {
                            LOG.info("Successfully fetched and saved user info for instance: " + instance.getName());
                        })
                        .exceptionally(e -> {
                            LOG.warn("Failed to fetch user info for instance: " + instance.getName(), e);
                            return null;
                        });
            }
        }
    }

    /**
     * Check if an instance has complete user information
     */
    private boolean hasCompleteUserInfo(GitLabInstance instance) {
        return instance.getUserName() != null && !instance.getUserName().isEmpty()
                && instance.getUserEmail() != null && !instance.getUserEmail().isEmpty();
    }

    /**
     * Clear the cache and reload from config
     */
    public void reload() {
        loadInstancesFromConfig();
    }
}
