package org.flymars.devtools.midas.gitlab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.flymars.devtools.midas.gitlab.model.GitLabCommit;
import org.flymars.devtools.midas.gitlab.model.GitLabInstance;
import org.flymars.devtools.midas.gitlab.model.GitLabProject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for GitLab REST API
 * Supports both gitlab.com and self-managed instances
 */
public class GitLabApiClient {
    private static final Logger LOG = Logger.getInstance(GitLabApiClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    public GitLabApiClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .create();
    }

    // ==================== Authentication ====================

    /**
     * Validate token and get current user info
     * GET /user
     */
    public CompletableFuture<GitLabUser> validateToken(String serverUrl, String token) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = normalizeUrl(serverUrl) + "/api/v4/user";
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("PRIVATE-TOKEN", token)
                        .get()
                        .build();

                try (Response response = executeWithRetry(request)) {
                    if (!response.isSuccessful()) {
                        LOG.error("Token validation failed: " + response.code());
                        return null;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    return gson.fromJson(responseBody, GitLabUser.class);
                }
            } catch (Exception e) {
                LOG.error("Error validating token", e);
                return null;
            }
        });
    }

    /**
     * Test connection to a GitLab instance
     * @deprecated Use GitLabInstanceService.testConnection() instead
     */
    @Deprecated
    public CompletableFuture<Boolean> testConnection(GitLabInstance instance) {
        return validateToken(instance.getServerUrl(), instance.getAccessToken())
                .thenApply(user -> {
                    if (user != null) {
                        instance.setUserName(user.username);
                        instance.setUserDisplayName(user.name);
                        instance.setUserEmail(user.email);
                        return true;
                    }
                    return false;
                });
    }

    // ==================== Project Operations ====================

    /**
     * Fetch all accessible projects from the instance
     * GET /projects?membership=true&per_page=100
     * Supports pagination
     */
    public CompletableFuture<List<GitLabProject>> fetchProjects(GitLabInstance instance) {
        return CompletableFuture.supplyAsync(() -> {
            List<GitLabProject> allProjects = new ArrayList<>();
            int page = 1;
            boolean hasMore = true;

            while (hasMore) {
                try {
                    String url = instance.getApiBaseUrl() + "/projects" +
                            "?membership=true" +
                            "&per_page=100" +
                            "&page=" + page +
                            "&order_by=name" +
                            "&sort=asc";

                    Request request = new Request.Builder()
                            .url(url)
                            .addHeader("PRIVATE-TOKEN", instance.getAccessToken())
                            .get()
                            .build();

                    try (Response response = executeWithRetry(request)) {
                        if (!response.isSuccessful()) {
                            LOG.error("Failed to fetch projects: " + response.code());
                            hasMore = false;
                            continue;
                        }

                        String responseBody = response.body() != null ? response.body().string() : "";
                        List<GitLabProject> projects = parseProjectsList(responseBody);

                        // Set instance metadata
                        projects.forEach(p -> {
                            p.setInstanceId(instance.getId());
                            p.setInstanceName(instance.getName());
                        });

                        allProjects.addAll(projects);

                        // Check pagination
                        String linkHeader = response.header("Link");
                        hasMore = linkHeader != null && linkHeader.contains("rel=\"next\"");
                        page++;
                    }
                } catch (Exception e) {
                    LOG.error("Error fetching projects page " + page, e);
                    hasMore = false;
                }
            }

            LOG.info("Fetched " + allProjects.size() + " projects from " + instance.getName());
            return allProjects;
        });
    }

    /**
     * Fetch a single project by ID
     * GET /projects/:id
     */
    public CompletableFuture<GitLabProject> fetchProject(GitLabInstance instance, String projectId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = instance.getApiBaseUrl() + "/projects/" + encodePath(projectId);
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("PRIVATE-TOKEN", instance.getAccessToken())
                        .get()
                        .build();

                try (Response response = executeWithRetry(request)) {
                    if (!response.isSuccessful()) {
                        LOG.error("Failed to fetch project: " + response.code());
                        return null;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    GitLabProject project = gson.fromJson(responseBody, GitLabProject.class);
                    if (project != null) {
                        project.setInstanceId(instance.getId());
                        project.setInstanceName(instance.getName());
                    }
                    return project;
                }
            } catch (Exception e) {
                LOG.error("Error fetching project " + projectId, e);
                return null;
            }
        });
    }

    // ==================== Commit Operations ====================

    /**
     * Fetch commits for a project within a date range
     * GET /projects/:id/repository/commits?since=:since&until=:until&per_page=100
     */
    public CompletableFuture<List<GitLabCommit>> fetchCommits(
            GitLabInstance instance,
            String projectId,
            LocalDate since,
            LocalDate until) {
        return CompletableFuture.supplyAsync(() -> {
            List<GitLabCommit> allCommits = new ArrayList<>();
            int page = 1;
            boolean hasMore = true;

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
            String sinceStr = since.atStartOfDay().format(formatter);
            String untilStr = until.atTime(23, 59, 59).format(formatter);

            while (hasMore) {
                try {
                    String url = instance.getApiBaseUrl() + "/projects/" + encodePath(projectId) +
                            "/repository/commits" +
                            "?since=" + sinceStr +
                            "&until=" + untilStr +
                            "&per_page=100" +
                            "&page=" + page +
                            "&order_by=created_at" +
                            "&sort=asc";

                    Request request = new Request.Builder()
                            .url(url)
                            .addHeader("PRIVATE-TOKEN", instance.getAccessToken())
                            .get()
                            .build();

                    try (Response response = executeWithRetry(request)) {
                        if (!response.isSuccessful()) {
                            LOG.error("Failed to fetch commits: " + response.code());
                            hasMore = false;
                            continue;
                        }

                        String responseBody = response.body() != null ? response.body().string() : "";
                        List<GitLabCommit> commits = parseCommitsList(responseBody);

                        // Set metadata
                        commits.forEach(c -> {
                            c.setProjectId(projectId);
                            c.setInstanceId(instance.getId());
                        });

                        allCommits.addAll(commits);

                        // Check pagination
                        String linkHeader = response.header("Link");
                        hasMore = linkHeader != null && linkHeader.contains("rel=\"next\"");
                        page++;
                    }
                } catch (Exception e) {
                    LOG.error("Error fetching commits page " + page, e);
                    hasMore = false;
                }
            }

            LOG.info("Fetched " + allCommits.size() + " commits for project " + projectId);
            return allCommits;
        });
    }

    /**
     * Fetch a single commit with detailed diff information
     * GET /projects/:id/repository/commits/:sha
     */
    public CompletableFuture<GitLabCommit> fetchCommitDetail(
            GitLabInstance instance,
            String projectId,
            String sha) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = instance.getApiBaseUrl() + "/projects/" + encodePath(projectId) +
                        "/repository/commits/" + sha;

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("PRIVATE-TOKEN", instance.getAccessToken())
                        .get()
                        .build();

                try (Response response = executeWithRetry(request)) {
                    if (!response.isSuccessful()) {
                        LOG.error("Failed to fetch commit detail: " + response.code());
                        return null;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    GitLabCommit commit = gson.fromJson(responseBody, GitLabCommit.class);
                    if (commit != null) {
                        commit.setProjectId(projectId);
                        commit.setInstanceId(instance.getId());
                    }
                    return commit;
                }
            } catch (Exception e) {
                LOG.error("Error fetching commit detail " + sha, e);
                return null;
            }
        });
    }

    /**
     * Fetch diff for a commit
     * GET /projects/:id/repository/commits/:sha/diff
     */
    public CompletableFuture<String> fetchCommitDiff(
            GitLabInstance instance,
            String projectId,
            String sha) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = instance.getApiBaseUrl() + "/projects/" + encodePath(projectId) +
                        "/repository/commits/" + sha + "/diff";

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("PRIVATE-TOKEN", instance.getAccessToken())
                        .get()
                        .build();

                try (Response response = executeWithRetry(request)) {
                    if (!response.isSuccessful()) {
                        LOG.error("Failed to fetch commit diff: " + response.code());
                        return null;
                    }

                    return response.body() != null ? response.body().string() : null;
                }
            } catch (Exception e) {
                LOG.error("Error fetching commit diff " + sha, e);
                return null;
            }
        });
    }

    // ==================== User Operations ====================

    /**
     * Get current authenticated user
     * Returns complete user information including username, name, and email
     */
    public CompletableFuture<GitLabUser> getCurrentUser(GitLabInstance instance) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = instance.getApiBaseUrl() + "/user";
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("PRIVATE-TOKEN", instance.getAccessToken())
                        .get()
                        .build();

                try (Response response = executeWithRetry(request)) {
                    if (!response.isSuccessful()) {
                        LOG.error("Failed to fetch current user: " + response.code());
                        return null;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    GitLabUser user = gson.fromJson(responseBody, GitLabUser.class);
                    if (user != null) {
                        LOG.info("Successfully fetched user: " + user.username + " (" + user.name + ")");
                    }
                    return user;
                }
            } catch (Exception e) {
                LOG.error("Error fetching current user", e);
                return null;
            }
        });
    }

    // ==================== Helper Methods ====================

    /**
     * Execute request with exponential backoff retry
     */
    private Response executeWithRetry(Request request) throws IOException {
        IOException lastException = null;
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                Response response = httpClient.newCall(request).execute();

                // Check for rate limiting (429)
                if (response.code() == 429) {
                    response.close();
                    String retryAfter = response.header("Retry-After");
                    long waitTime = retryAfter != null
                        ? Long.parseLong(retryAfter) * 1000
                        : backoffMs;

                    LOG.warn("Rate limited. Waiting " + waitTime + "ms before retry.");
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted during rate limit wait", ie);
                    }
                    backoffMs *= 2;
                    continue;
                }

                return response;
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES - 1) {
                    try {
                        LOG.warn("Request failed, retrying in " + backoffMs + "ms");
                        Thread.sleep(backoffMs);
                        backoffMs *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted during retry backoff", ie);
                    }
                }
            }
        }

        throw new IOException("Request failed after " + MAX_RETRIES + " attempts", lastException);
    }

    /**
     * Normalize URL to ensure consistent format
     */
    private String normalizeUrl(String url) {
        String normalized = url.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        return normalized;
    }

    /**
     * URL-encode a path component (e.g., project ID with namespace)
     */
    private String encodePath(String path) {
        return path.replace("/", "%2F");
    }

    /**
     * Parse projects list from JSON response
     */
    private List<GitLabProject> parseProjectsList(String json) {
        Type listType = new TypeToken<List<GitLabProject>>() {}.getType();
        List<GitLabProject> projects = gson.fromJson(json, listType);
        return projects != null ? projects : new ArrayList<>();
    }

    /**
     * Parse commits list from JSON response
     */
    private List<GitLabCommit> parseCommitsList(String json) {
        Type listType = new TypeToken<List<GitLabCommit>>() {}.getType();
        List<GitLabCommit> commits = gson.fromJson(json, listType);
        return commits != null ? commits : new ArrayList<>();
    }

    // ==================== Inner Classes ====================

    /**
     * GitLab user information
     */
    public static class GitLabUser {
        public long id;
        public String username;
        public String name;
        public String email;
        public String state;
        public String avatar_url;
    }
}
