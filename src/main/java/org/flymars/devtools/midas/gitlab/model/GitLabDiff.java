package org.flymars.devtools.midas.gitlab.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents diff information from GitLab API
 * Used for detailed commit statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitLabDiff {
    /**
     * Whether this diff indicates a new file
     */
    private boolean isNewFile;

    /**
     * Whether this diff indicates a deleted file
     */
    private boolean isDeletedFile;

    /**
     * Whether this diff indicates a renamed file
     */
    private boolean isRenamedFile;

    /**
     * Old file path
     */
    private String oldPath;

    /**
     * New file path
     */
    private String newPath;

    /**
     * Number of lines added in this diff
     */
    private int additions;

    /**
     * Number of lines deleted in this diff
     */
    private int deletions;

    /**
     * Diff content (unified diff format)
     */
    private String diff;

    /**
     * Patch content
     */
    private String patch;

    /**
     * List of changed files in this diff
     */
    private List<String> changedFiles;

    /**
     * Commit ID this diff belongs to
     */
    private String commitId;

    /**
     * Gets the total number of changed files
     */
    public int getChangedFilesCount() {
        return changedFiles != null ? changedFiles.size() : 0;
    }
}
