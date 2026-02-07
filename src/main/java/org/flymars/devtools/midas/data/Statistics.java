package org.flymars.devtools.midas.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Statistics about commits within a time period
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Statistics {
    /**
     * Total number of commits
     */
    private int totalCommits;

    /**
     * Total lines of code added
     */
    private int totalInsertions;

    /**
     * Total lines of code deleted
     */
    private int totalDeletions;

    /**
     * Total number of files changed
     */
    private int totalFilesChanged;

    /**
     * Number of commits by type
     */
    @Builder.Default
    private Map<CommitInfo.CommitType, Integer> commitsByType = new HashMap<>();

    /**
     * Number of commits by author (for team reports)
     */
    @Builder.Default
    private Map<String, Integer> commitsByAuthor = new HashMap<>();

    /**
     * Most frequently modified files
     */
    @Builder.Default
    private Map<String, Integer> fileChangeFrequency = new HashMap<>();

    /**
     * Average commits per day
     */
    private double averageCommitsPerDay;

    /**
     * Most productive day
     */
    private String mostProductiveDay;

    // Getters
    public int getTotalCommits() {
        return totalCommits;
    }

    public int getTotalInsertions() {
        return totalInsertions;
    }

    public int getTotalDeletions() {
        return totalDeletions;
    }

    public int getTotalFilesChanged() {
        return totalFilesChanged;
    }

    public Map<CommitInfo.CommitType, Integer> getCommitsByType() {
        return commitsByType;
    }

    public Map<String, Integer> getCommitsByAuthor() {
        return commitsByAuthor;
    }

    public Map<String, Integer> getFileChangeFrequency() {
        return fileChangeFrequency;
    }

    public double getAverageCommitsPerDay() {
        return averageCommitsPerDay;
    }

    public String getMostProductiveDay() {
        return mostProductiveDay;
    }

    // Setters
    public void setTotalCommits(int totalCommits) {
        this.totalCommits = totalCommits;
    }

    public void setTotalInsertions(int totalInsertions) {
        this.totalInsertions = totalInsertions;
    }

    public void setTotalDeletions(int totalDeletions) {
        this.totalDeletions = totalDeletions;
    }

    public void setTotalFilesChanged(int totalFilesChanged) {
        this.totalFilesChanged = totalFilesChanged;
    }

    public void setCommitsByType(Map<CommitInfo.CommitType, Integer> commitsByType) {
        this.commitsByType = commitsByType;
    }

    public void setCommitsByAuthor(Map<String, Integer> commitsByAuthor) {
        this.commitsByAuthor = commitsByAuthor;
    }

    public void setFileChangeFrequency(Map<String, Integer> fileChangeFrequency) {
        this.fileChangeFrequency = fileChangeFrequency;
    }

    public void setAverageCommitsPerDay(double averageCommitsPerDay) {
        this.averageCommitsPerDay = averageCommitsPerDay;
    }

    public void setMostProductiveDay(String mostProductiveDay) {
        this.mostProductiveDay = mostProductiveDay;
    }

    /**
     * Add a commit to statistics
     */
    public void addCommit(CommitInfo commit) {
        totalCommits++;
        totalInsertions += commit.getInsertions();
        totalDeletions += commit.getDeletions();
        totalFilesChanged += commit.getFiles().size();

        // Count by type
        commitsByType.merge(commit.getType(), 1, Integer::sum);

        // Count by author
        commitsByAuthor.merge(commit.getAuthor(), 1, Integer::sum);

        // Track file changes
        for (String file : commit.getFiles()) {
            fileChangeFrequency.merge(file, 1, Integer::sum);
        }
    }

    /**
     * Get net lines changed (insertions - deletions)
     */
    public int getNetLinesChanged() {
        return totalInsertions - totalDeletions;
    }

    /**
     * Get the primary commit type (most frequent)
     */
    public CommitInfo.CommitType getPrimaryCommitType() {
        return commitsByType.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(CommitInfo.CommitType.OTHER);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int totalCommits;
        private int totalInsertions;
        private int totalDeletions;
        private int totalFilesChanged;
        private Map<CommitInfo.CommitType, Integer> commitsByType = new HashMap<>();
        private Map<String, Integer> commitsByAuthor = new HashMap<>();
        private Map<String, Integer> fileChangeFrequency = new HashMap<>();
        private double averageCommitsPerDay;
        private String mostProductiveDay;
        
        public Builder totalCommits(int totalCommits) {
            this.totalCommits = totalCommits;
            return this;
        }
        
        public Builder totalInsertions(int totalInsertions) {
            this.totalInsertions = totalInsertions;
            return this;
        }
        
        public Builder totalDeletions(int totalDeletions) {
            this.totalDeletions = totalDeletions;
            return this;
        }
        
        public Builder totalFilesChanged(int totalFilesChanged) {
            this.totalFilesChanged = totalFilesChanged;
            return this;
        }
        
        public Builder commitsByType(Map<CommitInfo.CommitType, Integer> commitsByType) {
            this.commitsByType = commitsByType;
            return this;
        }
        
        public Builder commitsByAuthor(Map<String, Integer> commitsByAuthor) {
            this.commitsByAuthor = commitsByAuthor;
            return this;
        }
        
        public Builder fileChangeFrequency(Map<String, Integer> fileChangeFrequency) {
            this.fileChangeFrequency = fileChangeFrequency;
            return this;
        }
        
        public Builder averageCommitsPerDay(double averageCommitsPerDay) {
            this.averageCommitsPerDay = averageCommitsPerDay;
            return this;
        }
        
        public Builder mostProductiveDay(String mostProductiveDay) {
            this.mostProductiveDay = mostProductiveDay;
            return this;
        }
        
        public Statistics build() {
            Statistics statistics = new Statistics();
            statistics.totalCommits = this.totalCommits;
            statistics.totalInsertions = this.totalInsertions;
            statistics.totalDeletions = this.totalDeletions;
            statistics.totalFilesChanged = this.totalFilesChanged;
            statistics.commitsByType = this.commitsByType;
            statistics.commitsByAuthor = this.commitsByAuthor;
            statistics.fileChangeFrequency = this.fileChangeFrequency;
            statistics.averageCommitsPerDay = this.averageCommitsPerDay;
            statistics.mostProductiveDay = this.mostProductiveDay;
            return statistics;
        }
    }
}