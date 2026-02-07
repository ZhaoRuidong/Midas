package org.flymars.devtools.midas.gitlab.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a commit from GitLab API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitLabCommit {
    /**
     * Commit hash (full SHA)
     */
    @SerializedName("id")
    private String id;

    /**
     * Short commit hash (first 8 characters)
     */
    @SerializedName("short_id")
    private String shortId;

    /**
     * Commit message
     */
    @SerializedName("message")
    private String message;

    /**
     * Commit title (first line of message)
     */
    @SerializedName("title")
    private String title;

    /**
     * Author name
     */
    @SerializedName("author_name")
    private String authorName;

    /**
     * Author email
     */
    @SerializedName("author_email")
    private String authorEmail;

    /**
     * Committer name
     */
    @SerializedName("committer_name")
    private String committerName;

    /**
     * Committer email
     */
    @SerializedName("committer_email")
    private String committerEmail;

    /**
     * Commit timestamp (ISO 8601 format from API)
     */
    @SerializedName("created_at")
    private String createdAt;

    /**
     * Authored date
     */
    @SerializedName("authored_date")
    private String authoredDate;

    /**
     * Committed date
     */
    @SerializedName("committed_date")
    private String committedDate;

    /**
     * Commit URL (web interface)
     */
    @SerializedName("web_url")
    private String webUrl;

    /**
     * List of parent commit IDs
     */
    @SerializedName("parent_ids")
    private List<String> parentIds;

    /**
     * Project ID this commit belongs to (set by client, not from API)
     */
    private String projectId;

    /**
     * Instance ID this commit belongs to (set by client, not from API)
     */
    private String instanceId;

    /**
     * Branch name (if available, requires additional API call)
     */
    private String branch;

    /**
     * Number of lines added (requires diff API call)
     */
    private Integer insertions;

    /**
     * Number of lines deleted (requires diff API call)
     */
    private Integer deletions;

    /**
     * Whether this is a merge commit
     */
    private Boolean isMerge;

    /**
     * Checks if this commit is a merge commit based on parents
     */
    public boolean isMergeCommit() {
        if (isMerge != null) {
            return isMerge;
        }
        return parentIds != null && parentIds.size() > 1;
    }

    /**
     * Gets the parsed timestamp as LocalDateTime
     */
    public LocalDateTime getParsedTimestamp() {
        if (createdAt == null || createdAt.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(createdAt.replace("Z", ""));
        } catch (Exception e) {
            // Try alternative formats
            return null;
        }
    }
}
