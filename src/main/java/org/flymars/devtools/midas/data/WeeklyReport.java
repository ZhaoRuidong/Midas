package org.flymars.devtools.midas.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Represents a generated weekly report
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyReport {
    /**
     * Start date of the week
     */
    private LocalDate weekStart;

    /**
     * End date of the week
     */
    private LocalDate weekEnd;

    /**
     * All commits within this week
     */
    private List<CommitInfo> commits;

    /**
     * AI-generated summary of work done
     */
    private String summary;

    /**
     * Technical highlights extracted by AI
     */
    private String technicalHighlights;

    /**
     * Problems and solutions identified by AI
     */
    private String problemsAndSolutions;

    /**
     * Suggested plans for next week
     */
    private String nextWeekPlans;

    /**
     * Statistical information
     */
    private Statistics statistics;

    /**
     * Generated report content in Markdown format
     */
    private String markdownContent;

    /**
     * Generated report content in HTML format
     */
    private String htmlContent;

    /**
     * When the report was generated
     */
    private java.time.LocalDateTime generatedAt;

    /**
     * Whether the report has been sent via email
     */
    @Builder.Default
    private boolean emailSent = false;

    /**
     * When the report was sent via email
     */
    private java.time.LocalDateTime emailSentAt;

    /**
     * Project name/path
     */
    private String projectName;

    /**
     * Daily notes for supplementary content
     */
    private List<DailyNote> dailyNotes;

    // Getters
    public LocalDate getWeekStart() {
        return weekStart;
    }

    public List<DailyNote> getDailyNotes() {
        return dailyNotes;
    }

    public LocalDate getWeekEnd() {
        return weekEnd;
    }

    public List<CommitInfo> getCommits() {
        return commits;
    }

    public String getSummary() {
        return summary;
    }

    public String getTechnicalHighlights() {
        return technicalHighlights;
    }

    public String getProblemsAndSolutions() {
        return problemsAndSolutions;
    }

    public String getNextWeekPlans() {
        return nextWeekPlans;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    public String getMarkdownContent() {
        return markdownContent;
    }

    public String getHtmlContent() {
        return htmlContent;
    }

    public java.time.LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public boolean isEmailSent() {
        return emailSent;
    }

    public java.time.LocalDateTime getEmailSentAt() {
        return emailSentAt;
    }

    public String getProjectName() {
        return projectName;
    }

    // Setters
    public void setWeekStart(LocalDate weekStart) {
        this.weekStart = weekStart;
    }

    public void setWeekEnd(LocalDate weekEnd) {
        this.weekEnd = weekEnd;
    }

    public void setCommits(List<CommitInfo> commits) {
        this.commits = commits;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setTechnicalHighlights(String technicalHighlights) {
        this.technicalHighlights = technicalHighlights;
    }

    public void setProblemsAndSolutions(String problemsAndSolutions) {
        this.problemsAndSolutions = problemsAndSolutions;
    }

    public void setNextWeekPlans(String nextWeekPlans) {
        this.nextWeekPlans = nextWeekPlans;
    }

    public void setStatistics(Statistics statistics) {
        this.statistics = statistics;
    }

    public void setMarkdownContent(String markdownContent) {
        this.markdownContent = markdownContent;
    }

    public void setHtmlContent(String htmlContent) {
        this.htmlContent = htmlContent;
    }

    public void setGeneratedAt(java.time.LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public void setEmailSent(boolean emailSent) {
        this.emailSent = emailSent;
    }

    public void setEmailSentAt(java.time.LocalDateTime emailSentAt) {
        this.emailSentAt = emailSentAt;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setDailyNotes(List<DailyNote> dailyNotes) {
        this.dailyNotes = dailyNotes;
    }

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private LocalDate weekStart;
        private LocalDate weekEnd;
        private List<CommitInfo> commits;
        private String summary;
        private String technicalHighlights;
        private String problemsAndSolutions;
        private String nextWeekPlans;
        private Statistics statistics;
        private String markdownContent;
        private String htmlContent;
        private java.time.LocalDateTime generatedAt;
        private boolean emailSent = false;
        private java.time.LocalDateTime emailSentAt;
        private String projectName;
        private List<DailyNote> dailyNotes;

        public Builder weekStart(LocalDate weekStart) {
            this.weekStart = weekStart;
            return this;
        }
        
        public Builder weekEnd(LocalDate weekEnd) {
            this.weekEnd = weekEnd;
            return this;
        }
        
        public Builder commits(List<CommitInfo> commits) {
            this.commits = commits;
            return this;
        }
        
        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }
        
        public Builder technicalHighlights(String technicalHighlights) {
            this.technicalHighlights = technicalHighlights;
            return this;
        }
        
        public Builder problemsAndSolutions(String problemsAndSolutions) {
            this.problemsAndSolutions = problemsAndSolutions;
            return this;
        }
        
        public Builder nextWeekPlans(String nextWeekPlans) {
            this.nextWeekPlans = nextWeekPlans;
            return this;
        }
        
        public Builder statistics(Statistics statistics) {
            this.statistics = statistics;
            return this;
        }
        
        public Builder markdownContent(String markdownContent) {
            this.markdownContent = markdownContent;
            return this;
        }
        
        public Builder htmlContent(String htmlContent) {
            this.htmlContent = htmlContent;
            return this;
        }
        
        public Builder generatedAt(java.time.LocalDateTime generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }
        
        public Builder emailSent(boolean emailSent) {
            this.emailSent = emailSent;
            return this;
        }
        
        public Builder emailSentAt(java.time.LocalDateTime emailSentAt) {
            this.emailSentAt = emailSentAt;
            return this;
        }
        
        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder dailyNotes(List<DailyNote> dailyNotes) {
            this.dailyNotes = dailyNotes;
            return this;
        }

        public WeeklyReport build() {
            WeeklyReport report = new WeeklyReport();
            report.weekStart = this.weekStart;
            report.weekEnd = this.weekEnd;
            report.commits = this.commits;
            report.summary = this.summary;
            report.technicalHighlights = this.technicalHighlights;
            report.problemsAndSolutions = this.problemsAndSolutions;
            report.nextWeekPlans = this.nextWeekPlans;
            report.statistics = this.statistics;
            report.markdownContent = this.markdownContent;
            report.htmlContent = this.htmlContent;
            report.generatedAt = this.generatedAt;
            report.emailSent = this.emailSent;
            report.emailSentAt = this.emailSentAt;
            report.projectName = this.projectName;
            report.dailyNotes = this.dailyNotes;
            return report;
        }
    }
}