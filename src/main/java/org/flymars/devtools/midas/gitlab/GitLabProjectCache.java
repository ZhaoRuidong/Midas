package org.flymars.devtools.midas.gitlab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.gitlab.model.GitLabProject;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cache service for GitLab projects
 * Stores project data in local file system to avoid unnecessary API calls
 */
public class GitLabProjectCache {
    private static final Logger LOG = Logger.getInstance(GitLabProjectCache.class);
    private static final String CACHE_DIR = "gitlab-projects";
    private static final long CACHE_EXPIRY_HOURS = 24; // Cache expires after 24 hours

    private final Project project;
    private final ConfigManager configManager;
    private final Gson gson;

    public GitLabProjectCache(Project project) {
        this.project = project;
        this.configManager = ConfigManager.getInstance(project);
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .create();
    }

    /**
     * Get projects from cache or return empty list if not cached
     */
    public List<GitLabProject> getProjectsFromCache(String instanceId) {
        Path cacheFile = getCacheFilePath(instanceId);
        if (!Files.exists(cacheFile)) {
            LOG.info("No cache file found for instance: " + instanceId);
            return new ArrayList<>();
        }

        try {
            String content = Files.readString(cacheFile);
            Type listType = new TypeToken<List<CachedProjectData>>(){}.getType();
            List<CachedProjectData> cachedData = gson.fromJson(content, listType);

            // Check if cache is expired
            if (isCacheExpired(cachedData)) {
                LOG.info("Cache expired for instance: " + instanceId);
                return new ArrayList<>();
            }

            // Convert to GitLabProject list
            List<GitLabProject> projects = cachedData.stream()
                    .map(CachedProjectData::toProject)
                    .collect(Collectors.toList());

            LOG.info("Loaded " + projects.size() + " projects from cache for instance: " + instanceId);
            return projects;

        } catch (Exception e) {
            LOG.error("Error reading cache file for instance: " + instanceId, e);
            return new ArrayList<>();
        }
    }

    /**
     * Save projects to cache
     */
    public void saveProjectsToCache(String instanceId, List<GitLabProject> projects) {
        try {
            // Ensure cache directory exists
            Path cacheDir = getCacheDirectory();
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }

            // Convert to cache data
            List<CachedProjectData> cachedData = projects.stream()
                    .map(CachedProjectData::from)
                    .collect(Collectors.toList());

            // Write to file
            Path cacheFile = getCacheFilePath(instanceId);
            String json = gson.toJson(cachedData);
            Files.writeString(cacheFile, json);

            LOG.info("Cached " + projects.size() + " projects for instance: " + instanceId);

        } catch (Exception e) {
            LOG.error("Error writing cache file for instance: " + instanceId, e);
        }
    }

    /**
     * Clear cache for a specific instance
     */
    public void clearCache(String instanceId) {
        try {
            Path cacheFile = getCacheFilePath(instanceId);
            if (Files.exists(cacheFile)) {
                Files.delete(cacheFile);
                LOG.info("Cleared cache for instance: " + instanceId);
            }
        } catch (Exception e) {
            LOG.error("Error clearing cache for instance: " + instanceId, e);
        }
    }

    /**
     * Clear all cached project data
     */
    public void clearAllCache() {
        try {
            Path cacheDir = getCacheDirectory();
            if (Files.exists(cacheDir)) {
                Files.walk(cacheDir)
                        .filter(Files::isRegularFile)
                        .forEach(file -> {
                            try {
                                Files.delete(file);
                            } catch (Exception e) {
                                LOG.error("Error deleting cache file: " + file, e);
                            }
                        });
                LOG.info("Cleared all project cache");
            }
        } catch (Exception e) {
            LOG.error("Error clearing all cache", e);
        }
    }

    /**
     * Check if cache is expired
     */
    private boolean isCacheExpired(List<CachedProjectData> cachedData) {
        if (cachedData.isEmpty()) {
            return true;
        }

        Instant cacheTime = cachedData.get(0).cachedAt;
        Instant now = Instant.now();
        long hoursSinceCache = ChronoUnit.HOURS.between(cacheTime, now);

        return hoursSinceCache > CACHE_EXPIRY_HOURS;
    }

    /**
     * Get cache directory path
     */
    private Path getCacheDirectory() {
        String storagePath = configManager.getStoragePath();
        Path basePath = Paths.get(project.getBasePath(), storagePath);
        return basePath.resolve(CACHE_DIR);
    }

    /**
     * Get cache file path for an instance
     */
    private Path getCacheFilePath(String instanceId) {
        // Use sanitized instance ID as filename
        String sanitizedId = instanceId.replaceAll("[^a-zA-Z0-9.-]", "_");
        return getCacheDirectory().resolve(sanitizedId + ".json");
    }

    /**
     * Cached project data structure
     */
    private static class CachedProjectData {
        String id;
        String instanceId;
        String name;
        String pathWithNamespace;
        String description;
        String defaultBranch;
        String webUrl;
        String sshUrlToRepo;
        String httpUrlToRepo;
        boolean archived;
        String instanceName;
        boolean selected;
        Long lastAccessed;
        Instant cachedAt;

        static CachedProjectData from(GitLabProject project) {
            CachedProjectData data = new CachedProjectData();
            data.id = project.getId();
            data.instanceId = project.getInstanceId();
            data.name = project.getName();
            data.pathWithNamespace = project.getPathWithNamespace();
            data.description = project.getDescription();
            data.defaultBranch = project.getDefaultBranch();
            data.webUrl = project.getWebUrl();
            data.sshUrlToRepo = project.getSshUrlToRepo();
            data.httpUrlToRepo = project.getHttpUrlToRepo();
            data.archived = project.isArchived();
            data.instanceName = project.getInstanceName();
            data.selected = project.isSelected();
            data.lastAccessed = project.getLastAccessed();
            data.cachedAt = Instant.now();
            return data;
        }

        GitLabProject toProject() {
            return GitLabProject.builder()
                    .id(id)
                    .instanceId(instanceId)
                    .name(name)
                    .pathWithNamespace(pathWithNamespace)
                    .description(description)
                    .defaultBranch(defaultBranch)
                    .webUrl(webUrl)
                    .sshUrlToRepo(sshUrlToRepo)
                    .httpUrlToRepo(httpUrlToRepo)
                    .archived(archived)
                    .instanceName(instanceName)
                    .isSelected(selected)
                    .lastAccessed(lastAccessed)
                    .build();
        }
    }

    /**
     * Gson adapter for Instant serialization
     */
    private static class InstantAdapter implements com.google.gson.JsonSerializer<Instant>,
            com.google.gson.JsonDeserializer<Instant> {
        @Override
        public com.google.gson.JsonElement serialize(Instant src, Type typeOfSrc,
                com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(src.toString());
        }

        @Override
        public Instant deserialize(com.google.gson.JsonElement json, Type typeOfT,
                com.google.gson.JsonDeserializationContext context) {
            return Instant.parse(json.getAsString());
        }
    }
}
