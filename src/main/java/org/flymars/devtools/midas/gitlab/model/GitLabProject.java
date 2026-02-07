package org.flymars.devtools.midas.gitlab.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a GitLab project fetched from the API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitLabProject {
    /**
     * Unique project ID (numeric as string)
     */
    @SerializedName("id")
    private String id;

    /**
     * Project path with namespace (e.g., "group/subgroup/project")
     */
    @SerializedName("path_with_namespace")
    private String pathWithNamespace;

    /**
     * Project name (e.g., "project")
     */
    @SerializedName("name")
    private String name;

    /**
     * Project description
     */
    @SerializedName("description")
    private String description;

    /**
     * Project URL (web interface)
     */
    @SerializedName("web_url")
    private String webUrl;

    /**
     * SSH URL to clone the repository
     */
    @SerializedName("ssh_url_to_repo")
    private String sshUrlToRepo;

    /**
     * HTTP URL to clone the repository
     */
    @SerializedName("http_url_to_repo")
    private String httpUrlToRepo;

    /**
     * Default branch name (e.g., "main", "master")
     */
    @SerializedName("default_branch")
    private String defaultBranch;

    /**
     * Whether project is archived
     */
    @SerializedName("archived")
    private boolean archived;

    /**
     * ID of the GitLab instance this project belongs to (set by client, not from API)
     */
    private String instanceId;

    /**
     * Name of the GitLab instance this project belongs to (set by client, not from API)
     */
    private String instanceName;

    /**
     * Whether this project is currently selected for reporting
     */
    @Builder.Default
    private boolean isSelected = false;

    /**
     * Last access timestamp for caching purposes
     */
    private Long lastAccessed;

    /**
     * Gets a display name for this project including instance
     */
    public String getDisplayName() {
        return instanceName != null
            ? instanceName + " / " + pathWithNamespace
            : pathWithNamespace;
    }

    /**
     * Checks if this project is archived
     */
    public boolean isArchived() {
        return archived;
    }
}
