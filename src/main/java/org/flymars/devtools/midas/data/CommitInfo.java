package org.flymars.devtools.midas.data;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a single Git commit with all relevant information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitInfo {
    // Getters
    /**
     * Commit hash (short form, e.g., "abc1234")
     */
    private String hash;

    /**
     * Full commit message
     */
    private String message;

    /**
     * Commit author name
     */
    private String author;

    /**
     * Author email
     */
    private String authorEmail;

    /**
     * Commit timestamp
     */
    private LocalDateTime timestamp;

    /**
     * Branch name where commit was made
     */
    private String branch;

    /**
     * List of changed files in this commit
     */
    private List<String> files;

    /**
     * Number of lines added
     */
    private int insertions;

    /**
     * Number of lines deleted
     */
    private int deletions;

    /**
     * Commit type (feat, fix, refactor, docs, etc.)
     */
    private CommitType type;

    /**
     * Jira/Linear ticket ID if present in commit message
     */
    private String ticketId;

    /**
     * Whether this commit is a merge commit
     */
    private boolean isMerge;

    // GitLab metadata fields
    /**
     * GitLab instance ID this commit belongs to
     */
    private String gitlabInstanceId;

    /**
     * GitLab project ID this commit belongs to
     */
    private String gitlabProjectId;

    /**
     * GitLab project name this commit belongs to
     */
    private String gitlabProjectName;

    @Getter
    public enum CommitType {
        FEATURE("feat"),
        BUGFIX("fix"),
        REFACTOR("refactor"),
        DOCUMENTATION("docs"),
        TEST("test"),
        CHORE("chore"),
        STYLE("style"),
        PERF("perf"),
        OTHER("other");

        private final String prefix;

        CommitType(String prefix) {
            this.prefix = prefix;
        }

        public static CommitType fromMessage(String message) {
            if (message == null || message.isEmpty()) {
                return OTHER;
            }

            String lowerMessage = message.toLowerCase().trim();
            for (CommitType type : values()) {
                if (lowerMessage.startsWith(type.prefix + ":") ||
                    lowerMessage.startsWith(type.prefix + "(")) {
                    return type;
                }
            }

            // Additional detection patterns
            if (lowerMessage.contains("feature") || lowerMessage.contains("add")) {
                return FEATURE;
            } else if (lowerMessage.contains("fix") || lowerMessage.contains("bug")) {
                return BUGFIX;
            } else if (lowerMessage.contains("refactor") || lowerMessage.contains("rework")) {
                return REFACTOR;
            } else if (lowerMessage.contains("doc")) {
                return DOCUMENTATION;
            } else if (lowerMessage.contains("test")) {
                return TEST;
            }

            return OTHER;
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String hash;
        private String message;
        private String author;
        private String authorEmail;
        private LocalDateTime timestamp;
        private String branch;
        private List<String> files;
        private int insertions;
        private int deletions;
        private CommitType type;
        private String ticketId;
        private boolean isMerge;
        private String gitlabInstanceId;
        private String gitlabProjectId;
        private String gitlabProjectName;
        
        public Builder hash(String hash) {
            this.hash = hash;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder author(String author) {
            this.author = author;
            return this;
        }
        
        public Builder authorEmail(String authorEmail) {
            this.authorEmail = authorEmail;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder branch(String branch) {
            this.branch = branch;
            return this;
        }
        
        public Builder files(List<String> files) {
            this.files = files;
            return this;
        }
        
        public Builder insertions(int insertions) {
            this.insertions = insertions;
            return this;
        }
        
        public Builder deletions(int deletions) {
            this.deletions = deletions;
            return this;
        }
        
        public Builder type(CommitType type) {
            this.type = type;
            return this;
        }
        
        public Builder ticketId(String ticketId) {
            this.ticketId = ticketId;
            return this;
        }
        
        public Builder merge(boolean merge) {
            this.isMerge = merge;
            return this;
        }

        public Builder gitlabInstanceId(String gitlabInstanceId) {
            this.gitlabInstanceId = gitlabInstanceId;
            return this;
        }

        public Builder gitlabProjectId(String gitlabProjectId) {
            this.gitlabProjectId = gitlabProjectId;
            return this;
        }

        public Builder gitlabProjectName(String gitlabProjectName) {
            this.gitlabProjectName = gitlabProjectName;
            return this;
        }

        public CommitInfo build() {
            CommitInfo commitInfo = new CommitInfo();
            commitInfo.hash = this.hash;
            commitInfo.message = this.message;
            commitInfo.author = this.author;
            commitInfo.authorEmail = this.authorEmail;
            commitInfo.timestamp = this.timestamp;
            commitInfo.branch = this.branch;
            commitInfo.files = this.files;
            commitInfo.insertions = this.insertions;
            commitInfo.deletions = this.deletions;
            commitInfo.type = this.type;
            commitInfo.ticketId = this.ticketId;
            commitInfo.isMerge = this.isMerge;
            commitInfo.gitlabInstanceId = this.gitlabInstanceId;
            commitInfo.gitlabProjectId = this.gitlabProjectId;
            commitInfo.gitlabProjectName = this.gitlabProjectName;
            return commitInfo;
        }
    }
}