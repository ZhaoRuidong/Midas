package org.flymars.devtools.midas.gitlab;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.data.CommitInfo;
import org.flymars.devtools.midas.gitlab.model.GitLabCommit;
import org.flymars.devtools.midas.gitlab.model.GitLabInstance;
import org.flymars.devtools.midas.gitlab.model.GitLabProject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing GitLab projects and commit retrieval
 * Supports cross-project report generation from multiple instances
 */
@Service(Service.Level.PROJECT)
public final class GitLabProjectService {
    private static final Logger LOG = Logger.getInstance(GitLabProjectService.class);

    private final Project project;
    private final ConfigManager configManager;
    private final GitLabInstanceService instanceService;
    private final GitLabApiClient apiClient;
    private final GitLabModelMapper modelMapper;
    private final GitLabProjectCache projectCache;

    // Project cache: instanceId -> List<GitLabProject>
    private final Map<String, List<GitLabProject>> projectsCache;

    // Commit cache: (instanceId_projectId) -> List<CommitInfo>
    private final Map<String, CacheEntry> commitCache;

    // Cache duration in milliseconds (1 hour)
    private static final long CACHE_DURATION_MS = 60 * 60 * 1000;

    public GitLabProjectService(Project project) {
        this.project = project;
        this.configManager = ConfigManager.getInstance(project);
        this.instanceService = GitLabInstanceService.getInstance(project);
        this.apiClient = new GitLabApiClient();
        this.modelMapper = new GitLabModelMapper();
        this.projectCache = new GitLabProjectCache(project);
        this.projectsCache = new ConcurrentHashMap<>();
        this.commitCache = new ConcurrentHashMap<>();
    }

    public static GitLabProjectService getInstance(Project project) {
        return project.getService(GitLabProjectService.class);
    }

    // Flag to track if initial load has been triggered
    private volatile boolean initialLoadTriggered = false;

    // ==================== Project Management ====================

    /**
     * Ensure projects are loaded from cache or API
     * This method should be called when the plugin UI is first opened
     * It will automatically load projects in the background if cache is empty
     */
    public void ensureProjectsLoaded() {
        if (initialLoadTriggered) {
            return; // Already triggered
        }

        synchronized (this) {
            if (initialLoadTriggered) {
                return;
            }
            initialLoadTriggered = true;
        }

        LOG.info("ensureProjectsLoaded() - Checking if projects need to be loaded");

        // Check if we have any instances configured
        List<GitLabInstance> instances = instanceService.getInstances();
        if (instances.isEmpty()) {
            LOG.info("No GitLab instances configured, skipping auto-load");
            return;
        }

        // Check if cache is already populated
        if (!projectsCache.isEmpty()) {
            LOG.info("Projects cache already populated with " + projectsCache.size() + " instances");
            return;
        }

        // Try to load from file cache first
        boolean loadedFromFile = false;
        for (GitLabInstance instance : instances) {
            List<GitLabProject> cachedProjects = projectCache.getProjectsFromCache(instance.getId());
            if (!cachedProjects.isEmpty()) {
                LOG.info("Loaded " + cachedProjects.size() + " projects from file cache for instance: " + instance.getId());
                projectsCache.put(instance.getId(), cachedProjects);
                restoreSelectedState(cachedProjects);
                loadedFromFile = true;
            }
        }

        if (loadedFromFile) {
            LOG.info("Projects loaded from file cache successfully");
            return;
        }

        // No file cache found, trigger background API load
        LOG.info("No file cache found, triggering background API load for all instances");

        // Load in background without blocking
        refreshAllProjects().thenRun(() -> {
            LOG.info("Background API load completed, projects are now available");
        }).exceptionally(e -> {
            LOG.warn("Background API load failed (user may need to refresh manually)", e);
            return null;
        });
    }

    /**
     * Get all projects from all instances
     */
    public List<GitLabProject> getAllProjects() {
        List<GitLabProject> allProjects = new ArrayList<>();
        for (GitLabInstance instance : instanceService.getInstances()) {
            allProjects.addAll(getProjectsForInstance(instance.getId()));
        }
        return allProjects;
    }

    /**
     * Get projects for a specific instance
     */
    public List<GitLabProject> getProjectsForInstance(String instanceId) {
        return projectsCache.getOrDefault(instanceId, new ArrayList<>());
    }

    /**
     * Get selected projects for reporting
     * Loads projects from file cache first, then from API if needed
     * Ensures projects are loaded and selection state is restored synchronously
     */
    public List<GitLabProject> getSelectedProjects() {
        // Check if cache is populated, if not, load projects first
        if (projectsCache.isEmpty()) {
            LOG.info("Project cache is empty, loading projects from file cache or API...");
            List<GitLabInstance> instances = instanceService.getInstances();
            LOG.info("Found " + instances.size() + " instances to load projects from");

            // Log selected project IDs from config before loading
            List<String> configuredSelectedIds = configManager.getSelectedProjectIds();
            LOG.info("Configured selected project IDs from config: " + configuredSelectedIds);
            LOG.info("Config has " + configuredSelectedIds.size() + " selected projects stored");

            if (!instances.isEmpty()) {
                // Try to load from file cache first for each instance
                boolean loadedFromCache = false;
                for (GitLabInstance instance : instances) {
                    List<GitLabProject> cachedProjects = projectCache.getProjectsFromCache(instance.getId());
                    if (!cachedProjects.isEmpty()) {
                        LOG.info("Loaded " + cachedProjects.size() + " projects from file cache for instance: " + instance.getId());
                        projectsCache.put(instance.getId(), cachedProjects);
                        restoreSelectedState(cachedProjects);
                        loadedFromCache = true;
                    } else {
                        LOG.info("No file cache found for instance: " + instance.getId());
                    }
                }

                // If file cache was empty for all instances, load from API synchronously
                if (!loadedFromCache) {
                    LOG.info("No file cache found for any instance, loading projects from API...");
                    try {
                        // Load from API and wait for completion
                        // Note: refreshProjectsForInstance will call restoreSelectedState internally
                        refreshAllProjects().join();
                        LOG.info("API load completed, selected state should already be restored");
                    } catch (Exception e) {
                        LOG.error("Failed to load projects from API", e);
                    }
                }

                // Log the results after loading
                LOG.info("After loading projects, cache size: " + projectsCache.size());
                List<GitLabProject> allProjects = getAllProjects();
                LOG.info("Total projects loaded: " + allProjects.size());
                long selectedCount = allProjects.stream().filter(GitLabProject::isSelected).count();
                LOG.info("Selected projects after restore: " + selectedCount);
            }
        }

        List<GitLabProject> selected = getAllProjects().stream()
                .filter(GitLabProject::isSelected)
                .collect(Collectors.toList());

        LOG.info("getSelectedProjects() returning " + selected.size() + " projects");
        return selected;
    }

    /**
     * Set selected projects for reporting
     */
    public void setSelectedProjects(List<GitLabProject> projects) {
        LOG.info("setSelectedProjects() called with " + projects.size() + " projects");
        for (GitLabProject p : projects) {
            LOG.info("  - Selected project: " + p.getId() + " (" + p.getName() + ")");
        }

        // Clear all selections first
        projectsCache.values().stream()
                .flatMap(List::stream)
                .forEach(p -> p.setSelected(false));

        // Set new selections
        projects.forEach(p -> {
            List<GitLabProject> instanceProjects = projectsCache.get(p.getInstanceId());
            if (instanceProjects != null) {
                instanceProjects.stream()
                        .filter(ip -> ip.getId().equals(p.getId()))
                        .findFirst()
                        .ifPresent(ip -> ip.setSelected(true));
            } else {
                LOG.warn("  - No cached projects found for instance: " + p.getInstanceId());
            }
        });

        saveSelectedProjectsToConfig();
    }

    /**
     * Fetch and cache projects from all instances
     */
    public CompletableFuture<Void> refreshAllProjects() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (GitLabInstance instance : instanceService.getInstances()) {
            futures.add(refreshProjectsForInstance(instance.getId()));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Fetch and cache projects for a specific instance
     * First tries to load from file cache, if not available or expired, fetches from API
     */
    public CompletableFuture<Void> refreshProjectsForInstance(String instanceId) {
        GitLabInstance instance = instanceService.getInstance(instanceId);
        if (instance == null) {
            LOG.warn("Instance not found: " + instanceId);
            return CompletableFuture.completedFuture(null);
        }

        LOG.info("Fetching projects for instance: " + instanceId + " (" + instance.getName() + ")");

        // Try to load from file cache first
        List<GitLabProject> cachedProjects = projectCache.getProjectsFromCache(instanceId);
        if (!cachedProjects.isEmpty()) {
            LOG.info("Loaded " + cachedProjects.size() + " projects from file cache for instance: " + instanceId);
            projectsCache.put(instanceId, cachedProjects);
            restoreSelectedState(cachedProjects);
            return CompletableFuture.completedFuture(null);
        }

        // If cache is empty, fetch from API
        return instanceService.fetchProjects(instance)
                .thenAccept(projects -> {
                    LOG.info("Fetched " + projects.size() + " projects from API for instance: " + instanceId);
                    projectsCache.put(instanceId, projects);

                    // Save to file cache
                    projectCache.saveProjectsToCache(instanceId, projects);

                    // Restore selected state from config
                    restoreSelectedState(projects);
                })
                .exceptionally(e -> {
                    LOG.error("Failed to fetch projects for instance: " + instanceId, e);
                    return null;
                });
    }

    /**
     * Clear project cache (both memory and file)
     */
    public void clearProjectCache() {
        projectsCache.clear();
        projectCache.clearAllCache();
        LOG.info("Cleared all project caches");
    }

    /**
     * Clear file cache for a specific instance
     */
    public void clearFileCache(String instanceId) {
        projectCache.clearCache(instanceId);
        LOG.info("Cleared file cache for instance: " + instanceId);
    }

    /**
     * Update instance name for all projects in the cache
     * Called when an instance name is changed in settings
     */
    public void updateInstanceName(String instanceId, String newInstanceName) {
        List<GitLabProject> projects = projectsCache.get(instanceId);
        if (projects != null) {
            for (GitLabProject project : projects) {
                project.setInstanceName(newInstanceName);
            }
            // Save updated projects to file cache
            projectCache.saveProjectsToCache(instanceId, projects);
            LOG.info("Updated instance name to '" + newInstanceName + "' for " + projects.size() + " projects");
        }
    }

    // ==================== Commit Retrieval ====================

    /**
     * Get commits for a week from selected projects
     * This is the main method for report generation
     */
    public CompletableFuture<List<CommitInfo>> getCommitsForWeek(
            LocalDate weekStart,
            LocalDate weekEnd) {
        return getCommitsForWeek(weekStart, weekEnd, getSelectedProjects());
    }

    /**
     * Get commits for a week from specific projects
     * Supports cross-instance report generation
     */
    public CompletableFuture<List<CommitInfo>> getCommitsForWeek(
            LocalDate weekStart,
            LocalDate weekEnd,
            List<GitLabProject> projects) {

        if (projects.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        // Fetch commits from all projects in parallel
        List<CompletableFuture<List<CommitInfo>>> futures = new ArrayList<>();

        for (GitLabProject project : projects) {
            futures.add(getCommitsForProject(project, weekStart, weekEnd));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<CommitInfo> allCommits = new ArrayList<>();
                    for (CompletableFuture<List<CommitInfo>> future : futures) {
                        try {
                            allCommits.addAll(future.get());
                        } catch (Exception e) {
                            LOG.error("Error collecting commits", e);
                        }
                    }

                    // Sort by timestamp
                    allCommits.sort(Comparator.comparing(CommitInfo::getTimestamp).reversed());
                    return allCommits;
                });
    }

    /**
     * Get commits for a specific project within a date range
     * Uses cache if available and not expired
     */
    public CompletableFuture<List<CommitInfo>> getCommitsForProject(
            GitLabProject project,
            LocalDate since,
            LocalDate until) {

        String cacheKey = project.getInstanceId() + "_" + project.getId();
        CacheEntry cached = commitCache.get(cacheKey);

        // Check cache
        if (cached != null && !cached.isExpired()) {
            LOG.info("Using cached commits for " + project.getDisplayName());
            return CompletableFuture.completedFuture(
                    filterCommitsByDate(cached.commits, since, until)
            );
        }

        // Fetch from API
        GitLabInstance instance = instanceService.getInstance(project.getInstanceId());
        if (instance == null) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        return apiClient.fetchCommits(instance, project.getId(), since, until)
                .thenApply(gitlabCommits -> {
                    List<CommitInfo> commits = new ArrayList<>();
                    for (GitLabCommit gitlabCommit : gitlabCommits) {
                        try {
                            CommitInfo commitInfo = modelMapper.toCommitInfo(
                                    gitlabCommit,
                                    project,
                                    instance
                            );
                            if (commitInfo != null) {
                                commits.add(commitInfo);
                            }
                        } catch (Exception e) {
                            LOG.error("Error mapping commit " + gitlabCommit.getId(), e);
                        }
                    }

                    // Cache the results
                    commitCache.put(cacheKey, new CacheEntry(commits));

                    return commits;
                });
    }

    /**
     * Get commits for a specific project with full details (including diffs)
     * This is slower but provides insertions/deletions counts
     */
    public CompletableFuture<List<CommitInfo>> getCommitsWithDetails(
            GitLabProject project,
            LocalDate since,
            LocalDate until) {

        // First get basic commits
        return getCommitsForProject(project, since, until)
                .thenCompose(commits -> {
                    // Fetch detailed info for each commit
                    List<CompletableFuture<CommitInfo>> futures = new ArrayList<>();

                    for (CommitInfo commit : commits) {
                        futures.add(fetchCommitDetails(project, commit));
                    }

                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> {
                                List<CommitInfo> detailedCommits = new ArrayList<>();
                                for (CompletableFuture<CommitInfo> future : futures) {
                                    try {
                                        detailedCommits.add(future.get());
                                    } catch (Exception e) {
                                        LOG.error("Error fetching commit details", e);
                                    }
                                }
                                return detailedCommits;
                            });
                });
    }

    /**
     * Fetch detailed commit information including diff stats
     */
    private CompletableFuture<CommitInfo> fetchCommitDetails(
            GitLabProject project,
            CommitInfo commit) {

        GitLabInstance instance = instanceService.getInstance(project.getInstanceId());
        if (instance == null) {
            return CompletableFuture.completedFuture(commit);
        }

        return apiClient.fetchCommitDetail(instance, project.getId(), commit.getHash())
                .thenApply(detail -> {
                    if (detail != null) {
                        commit.setInsertions(detail.getInsertions() != null
                            ? detail.getInsertions() : 0);
                        commit.setDeletions(detail.getDeletions() != null
                            ? detail.getDeletions() : 0);
                    }
                    return commit;
                });
    }

    /**
     * Refresh cache for all selected projects
     */
    public CompletableFuture<Void> refreshCache() {
        return refreshAllProjects();
    }

    /**
     * Clear all caches
     */
    public void clearCache() {
        projectsCache.clear();
        commitCache.clear();
    }

    /**
     * Clear commit cache
     */
    public void clearCommitCache() {
        commitCache.clear();
    }

    /**
     * Clear commit cache for a specific instance
     * Call this when instance name changes to force refresh with new project names
     */
    public void clearCommitCacheForInstance(String instanceId) {
        // Remove all cache entries that start with instanceId
        commitCache.keySet().removeIf(key -> key.startsWith(instanceId + "_"));
        LOG.info("Cleared commit cache for instance: " + instanceId);
    }

    /**
     * Get commits for a week from selected projects, filtered by current user
     * Only returns commits where the author matches the authenticated user for each instance
     */
    public CompletableFuture<List<CommitInfo>> getMyCommitsForWeek(
            LocalDate weekStart,
            LocalDate weekEnd) {
        return getMyCommitsForWeek(weekStart, weekEnd, getSelectedProjects());
    }

    /**
     * Get commits for a week from specific projects, filtered by current user
     * Only returns commits where the author matches the authenticated user for each instance
     */
    public CompletableFuture<List<CommitInfo>> getMyCommitsForWeek(
            LocalDate weekStart,
            LocalDate weekEnd,
            List<GitLabProject> projects) {

        return getCommitsForWeek(weekStart, weekEnd, projects)
                .thenApply(commits -> filterCommitsByCurrentUser(commits));
    }

    /**
     * Get commits for a week from local cache (CommitStorage), filtered by current user
     * This does NOT make API calls, only reads from local JSON cache file
     */
    public List<CommitInfo> getMyCommitsForWeekFromCache(
            LocalDate weekStart,
            LocalDate weekEnd,
            List<GitLabProject> projects) {

        if (projects.isEmpty()) {
            return new ArrayList<>();
        }

        org.flymars.devtools.midas.core.CommitStorage storage =
            project.getService(org.flymars.devtools.midas.core.CommitStorage.class);

        if (storage == null) {
            LOG.warn("CommitStorage service not available");
            return new ArrayList<>();
        }

        // Get commits from local storage
        List<CommitInfo> allCommits = storage.getAllCommits();

        // Filter by date range
        LocalDateTime sinceDateTime = weekStart.atStartOfDay();
        LocalDateTime untilDateTime = weekEnd.atTime(23, 59, 59);

        // Filter by selected projects and current user
        Set<String> projectIds = projects.stream()
                .map(GitLabProject::getId)
                .collect(java.util.stream.Collectors.toSet());

        List<CommitInfo> filtered = allCommits.stream()
                .filter(c -> {
                    // Filter by date
                    LocalDateTime timestamp = c.getTimestamp();
                    if (timestamp.isBefore(sinceDateTime) || timestamp.isAfter(untilDateTime)) {
                        return false;
                    }
                    // Filter by selected projects
                    if (!projectIds.contains(c.getGitlabProjectId())) {
                        return false;
                    }
                    // Filter by current user
                    if (!isCommitByCurrentUser(c)) {
                        return false;
                    }
                    // Filter out merge commits (from merge requests)
                    if (c.isMerge()) {
                        return false;
                    }
                    return true;
                })
                .sorted(Comparator.comparing(CommitInfo::getTimestamp).reversed())
                .collect(Collectors.toList());

        LOG.info("Loaded " + filtered.size() + " commits from local cache");
        return filtered;
    }

    /**
     * Filter commits to only include those by the current authenticated user
     * Matches by username or email for each instance
     * Also excludes merge commits (e.g., from merge requests)
     */
    private List<CommitInfo> filterCommitsByCurrentUser(List<CommitInfo> commits) {
        LOG.info("filterCommitsByCurrentUser() called with " + commits.size() + " commits");

        List<CommitInfo> filtered = commits.stream()
                .filter(this::isCommitByCurrentUser)
                .filter(commit -> {
                    // Filter out merge commits (from merge requests)
                    if (commit.isMerge()) {
                        LOG.debug("Filtered out merge commit: " + commit.getHash() + " - " + commit.getMessage());
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        LOG.info("After filtering by current user and merge commits: " + filtered.size() + " commits remaining");

        // Log some details about filtered commits
        if (commits.size() > 0 && filtered.size() == 0) {
            LOG.warn("All commits were filtered out! Sample commit details:");
            CommitInfo sample = commits.get(0);
            LOG.warn("  Sample commit: author=" + sample.getAuthor() +
                     ", email=" + sample.getAuthorEmail() +
                     ", instanceId=" + sample.getGitlabInstanceId());

            GitLabInstance instance = instanceService.getInstance(sample.getGitlabInstanceId());
            if (instance != null) {
                LOG.warn("  Instance userName=" + instance.getUserName() +
                         ", userEmail=" + instance.getUserEmail());
            }
        }

        return filtered;
    }

    /**
     * Check if a commit was made by the current authenticated user
     */
    private boolean isCommitByCurrentUser(CommitInfo commit) {
        GitLabInstance instance = instanceService.getInstance(commit.getGitlabInstanceId());
        if (instance == null) {
            LOG.warn("Instance not found for commit: " + commit.getGitlabInstanceId());
            return false;
        }

        String userName = instance.getUserName();
        String userEmail = instance.getUserEmail();

        // If no username is set, cannot filter - include all commits
        if (userName == null && userEmail == null) {
            LOG.warn("No current username/email set for instance: " + instance.getId() +
                     " (" + instance.getName() + "). Please test connection first.");
            // For now, return true to show all commits
            return true;
        }

        // Match by username or email
        boolean usernameMatches = userName != null
                && userName.equals(commit.getAuthor());
        boolean emailMatches = userEmail != null
                && userEmail.equals(commit.getAuthorEmail());

        return usernameMatches || emailMatches;
    }

    // ==================== Private Methods ====================

    /**
     * Filter commits by date range
     */
    private List<CommitInfo> filterCommitsByDate(
            List<CommitInfo> commits,
            LocalDate since,
            LocalDate until) {

        LocalDateTime sinceDateTime = since.atStartOfDay();
        LocalDateTime untilDateTime = until.atTime(23, 59, 59);

        return commits.stream()
                .filter(c -> {
                    LocalDateTime timestamp = c.getTimestamp();
                    return !timestamp.isBefore(sinceDateTime)
                            && !timestamp.isAfter(untilDateTime);
                })
                .collect(Collectors.toList());
    }

    /**
     * Restore selected state from config
     */
    private void restoreSelectedState(List<GitLabProject> projects) {
        List<String> selectedIds = configManager.getSelectedProjectIds();
        LOG.info("restoreSelectedState() called with " + selectedIds.size() + " selected IDs from config: " + selectedIds);

        int restoredCount = 0;
        for (GitLabProject p : projects) {
            if (selectedIds.contains(p.getId())) {
                p.setSelected(true);
                restoredCount++;
                LOG.info("  - Restored selected: " + p.getId() + " (" + p.getName() + ")");
            }
        }
        LOG.info("Restored " + restoredCount + " projects as selected");
    }

    /**
     * Save selected projects to config
     */
    private void saveSelectedProjectsToConfig() {
        List<String> selectedIds = getAllProjects().stream()
                .filter(GitLabProject::isSelected)
                .map(GitLabProject::getId)
                .collect(Collectors.toList());

        LOG.info("saveSelectedProjectsToConfig() saving " + selectedIds.size() + " project IDs: " + selectedIds);
        configManager.setSelectedProjectIds(selectedIds);
        LOG.info("saveSelectedProjectsToConfig() completed");
    }

    // ==================== Inner Classes ====================

    /**
     * Cache entry with expiration
     */
    private static class CacheEntry {
        final List<CommitInfo> commits;
        final long createdAt;

        CacheEntry(List<CommitInfo> commits) {
            this.commits = commits;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_DURATION_MS;
        }
    }
}
