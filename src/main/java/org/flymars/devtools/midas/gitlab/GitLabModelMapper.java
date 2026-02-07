package org.flymars.devtools.midas.gitlab;

import com.intellij.openapi.diagnostic.Logger;
import org.flymars.devtools.midas.data.CommitInfo;
import org.flymars.devtools.midas.gitlab.model.GitLabCommit;
import org.flymars.devtools.midas.gitlab.model.GitLabInstance;
import org.flymars.devtools.midas.gitlab.model.GitLabProject;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps GitLab API models to internal application models
 */
public class GitLabModelMapper {
    private static final Logger LOG = Logger.getInstance(GitLabModelMapper.class);

    // ISO 8601 timestamp formats from GitLab API
    private static final DateTimeFormatter[] TIMESTAMP_FORMATS = {
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    };

    // Ticket ID patterns (Jira, Linear, etc.)
    private static final Pattern TICKET_PATTERN = Pattern.compile(
            "([A-Z]+-\\d+)|([A-Z]{2,}-\\d+)"
    );

    /**
     * Convert GitLab commit to internal CommitInfo
     */
    @Nullable
    public CommitInfo toCommitInfo(
            GitLabCommit gitlabCommit,
            GitLabProject project,
            GitLabInstance instance) {

        if (gitlabCommit == null) {
            return null;
        }

        try {
            CommitInfo.CommitType commitType = determineCommitType(gitlabCommit.getMessage());
            LocalDateTime timestamp = parseTimestamp(gitlabCommit.getCreatedAt());
            String ticketId = extractTicketId(gitlabCommit.getMessage());

            CommitInfo commitInfo = CommitInfo.builder()
                    .hash(gitlabCommit.getShortId() != null
                            ? gitlabCommit.getShortId()
                            : gitlabCommit.getId().substring(0, 8))
                    .message(gitlabCommit.getMessage())
                    .author(gitlabCommit.getAuthorName())
                    .authorEmail(gitlabCommit.getAuthorEmail())
                    .timestamp(timestamp)
                    .branch(gitlabCommit.getBranch() != null
                            ? gitlabCommit.getBranch()
                            : project.getDefaultBranch())
                    .files(new ArrayList<>()) // Files require diff API call
                    .insertions(gitlabCommit.getInsertions() != null
                            ? gitlabCommit.getInsertions()
                            : 0)
                    .deletions(gitlabCommit.getDeletions() != null
                            ? gitlabCommit.getDeletions()
                            : 0)
                    .type(commitType)
                    .ticketId(ticketId)
                    .merge(gitlabCommit.isMergeCommit())
                    .gitlabInstanceId(instance.getId())
                    .gitlabProjectId(project.getId())
                    .gitlabProjectName(project.getDisplayName())
                    .build();

            return commitInfo;

        } catch (Exception e) {
            LOG.error("Error mapping GitLab commit to CommitInfo: " + gitlabCommit.getId(), e);
            return null;
        }
    }

    /**
     * Determine commit type from message
     */
    public CommitInfo.CommitType determineCommitType(String message) {
        if (message == null || message.isEmpty()) {
            return CommitInfo.CommitType.OTHER;
        }

        String lowerMessage = message.toLowerCase().trim();

        // Check for conventional commit format
        for (CommitInfo.CommitType type : CommitInfo.CommitType.values()) {
            String prefix = type.getPrefix();
            if (lowerMessage.startsWith(prefix + ":") ||
                    lowerMessage.startsWith(prefix + "(")) {
                return type;
            }
        }

        // Additional detection patterns
        if (lowerMessage.contains("feature") || lowerMessage.contains("add ")) {
            return CommitInfo.CommitType.FEATURE;
        } else if (lowerMessage.contains("fix") || lowerMessage.contains("bug")) {
            return CommitInfo.CommitType.BUGFIX;
        } else if (lowerMessage.contains("refactor") || lowerMessage.contains("rework")) {
            return CommitInfo.CommitType.REFACTOR;
        } else if (lowerMessage.contains("doc")) {
            return CommitInfo.CommitType.DOCUMENTATION;
        } else if (lowerMessage.contains("test")) {
            return CommitInfo.CommitType.TEST;
        } else if (lowerMessage.contains("performanc") || lowerMessage.contains("optimi")) {
            return CommitInfo.CommitType.PERF;
        } else if (lowerMessage.contains("style") || lowerMessage.contains("format")) {
            return CommitInfo.CommitType.STYLE;
        } else if (lowerMessage.contains("chore") || lowerMessage.contains("mainten")) {
            return CommitInfo.CommitType.CHORE;
        }

        return CommitInfo.CommitType.OTHER;
    }

    /**
     * Parse timestamp from ISO 8601 string
     */
    public LocalDateTime parseTimestamp(String iso8601) {
        if (iso8601 == null || iso8601.isEmpty()) {
            return LocalDateTime.now();
        }

        // Remove timezone suffix if present
        String normalized = iso8601.replace("Z", "").replace("\\+\\d{2}:\\d{2}$", "");

        for (DateTimeFormatter format : TIMESTAMP_FORMATS) {
            try {
                return LocalDateTime.parse(normalized, format);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }

        LOG.warn("Failed to parse timestamp: " + iso8601);
        return LocalDateTime.now();
    }

    /**
     * Extract ticket ID from commit message
     */
    public String extractTicketId(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        Matcher matcher = TICKET_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Convert list of GitLab commits to CommitInfo
     */
    public List<CommitInfo> toCommitInfoList(
            List<GitLabCommit> gitlabCommits,
            GitLabProject project,
            GitLabInstance instance) {

        List<CommitInfo> result = new ArrayList<>();
        for (GitLabCommit commit : gitlabCommits) {
            CommitInfo commitInfo = toCommitInfo(commit, project, instance);
            if (commitInfo != null) {
                result.add(commitInfo);
            }
        }
        return result;
    }
}
