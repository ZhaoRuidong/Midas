package org.flymars.devtools.midas.report;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.flymars.devtools.midas.analysis.AIAnalyzerService;
import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.core.CommitStorage;
import org.flymars.devtools.midas.data.CommitInfo;
import org.flymars.devtools.midas.data.Statistics;
import org.flymars.devtools.midas.data.WeeklyReport;
import org.flymars.devtools.midas.gitlab.GitLabProjectService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Generates weekly reports from collected commit data
 */
public class WeeklyReportGenerator {
    private static final Logger LOG = Logger.getInstance(WeeklyReportGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Project project;
    private final CommitStorage storage;
    private final ConfigManager configManager;
    private final AIAnalyzerService aiAnalyzer;
    private final GitLabProjectService gitlabProjectService;

    public WeeklyReportGenerator(Project project) {
        this.project = project;
        this.storage = project.getService(CommitStorage.class);
        // APP-level service must be retrieved via ServiceManager
        this.configManager = com.intellij.openapi.components.ServiceManager.getService(ConfigManager.class);
        this.aiAnalyzer = new AIAnalyzerService();
        this.gitlabProjectService = GitLabProjectService.getInstance(project);

        LOG.info("WeeklyReportGenerator created - ConfigManager: " +
                   (configManager != null ? "OK" : "NULL"));
    }

    /**
     * Generate a report for the current week
     */
    public WeeklyReport generateCurrentWeekReport() {
        System.out.println("[Midas] generateCurrentWeekReport() called");
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        System.out.println("[Midas] Week range: " + weekStart + " to " + weekEnd);
        System.out.println("[Midas] Calling generateReport()...");
        WeeklyReport report = generateReport(weekStart, weekEnd);
        System.out.println("[Midas] generateReport() returned, commits: " + report.getCommits().size());
        return report;
    }

    /**
     * Generate a report for a specific week (async version for GitLab API)
     * Only includes commits from the current authenticated user
     */
    public CompletableFuture<WeeklyReport> generateReportAsync(LocalDate weekStart, LocalDate weekEnd) {
        System.out.println("[Midas] generateReportAsync() called, creating CompletableFuture");
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("[Midas] ========== ASYNC TASK STARTED ==========");
            LOG.info("Generating report for week: " + weekStart + " to " + weekEnd);

            try {
                System.out.println("[Midas] Fetching commits from GitLab...");
                // Get commits for the week from GitLab, filtered by current user
                List<CommitInfo> commits = gitlabProjectService.getMyCommitsForWeek(weekStart, weekEnd).join();
                System.out.println("[Midas] Fetched " + commits.size() + " commits from GitLab");

                if (commits.isEmpty()) {
                    LOG.warn("No commits found for the current user during the specified week");
                    System.out.println("[Midas] No commits found, creating empty report");
                    return createEmptyReport(weekStart, weekEnd);
                }

                // Calculate statistics
                System.out.println("[Midas] Calculating statistics...");
                Statistics statistics = calculateStatistics(commits);

                // Build report
                System.out.println("[Midas] Building report object...");
                WeeklyReport report = WeeklyReport.builder()
                        .weekStart(weekStart)
                        .weekEnd(weekEnd)
                        .commits(commits)
                        .statistics(statistics)
                        .projectName(getProjectNames(commits))
                        .generatedAt(LocalDateTime.now())
                        .build();
                System.out.println("[Midas] Report object built");

                // Get daily notes for the week
                System.out.println("[Midas] Fetching daily notes...");
                List<org.flymars.devtools.midas.data.DailyNote> notes = storage.getNotesInRange(weekStart, weekEnd);
                System.out.println("[Midas] Found " + notes.size() + " daily notes");
                report.setDailyNotes(notes);

                // Generate AI analysis
                System.out.println("[Midas] About to generate AI analysis...");
                generateAIAnalysis(report);
                System.out.println("[Midas] AI analysis completed");

                // Generate markdown content
                System.out.println("[Midas] Generating markdown content...");
                String markdown = ReportTemplate.generateMarkdown(report, configManager);
                report.setMarkdownContent(markdown);
                System.out.println("[Midas] Markdown content generated, length: " + markdown.length());
                LOG.info("Markdown content generated, length: " + markdown.length());

                // Generate HTML content
                System.out.println("[Midas] Generating HTML content...");
                String html = ReportTemplate.generateHTML(report, configManager);
                report.setHtmlContent(html);
                System.out.println("[Midas] HTML content generated, length: " + html.length());
                LOG.info("HTML content generated, length: " + html.length());

                // Save report
                System.out.println("[Midas] Saving report to storage...");
                storage.saveReport(report);

                LOG.info("Report generated successfully with " + commits.size() + " commits");
                System.out.println("[Midas] ========== ASYNC TASK COMPLETED ==========");
                return report;
            } catch (Exception e) {
                System.err.println("[Midas] Exception in async task: " + e.getClass().getName());
                System.err.println("[Midas] Error: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        });
    }

    /**
     * Generate a report for a specific week (synchronous version)
     */
    public WeeklyReport generateReport(LocalDate weekStart, LocalDate weekEnd) {
        System.out.println("[Midas] generateReport() called, will join() async result");
        WeeklyReport report = generateReportAsync(weekStart, weekEnd).join();
        System.out.println("[Midas] generateReport() join() completed");
        return report;
    }

    /**
     * Get project names from commits for cross-project reports
     */
    private String getProjectNames(List<CommitInfo> commits) {
        return commits.stream()
                .map(c -> c.getGitlabProjectName())
                .filter(name -> name != null && !name.isEmpty())
                .distinct()
                .findFirst()
                .orElse(project.getName());
    }

    /**
     * Generate AI analysis for the report
     */
    private void generateAIAnalysis(WeeklyReport report) {
        try {
            System.out.println("[Midas] Starting AI analysis for " + report.getCommits().size() + " commits");

            // Debug config manager
            if (configManager == null) {
                LOG.error("ConfigManager is NULL! Cannot proceed with AI analysis.");
                report.setSummary("**AI Analysis Failed**\n\n" +
                    "Error: ConfigManager is null\n\n" +
                    "This is a critical error. Please restart IntelliJ IDEA and try again.");
                return;
            }

            LOG.info("ConfigManager is OK - Provider: " + configManager.getAIProvider());
            LOG.info("API Key configured: " + (configManager.getApiKey() != null && !configManager.getApiKey().isEmpty()));

            if (report.getDailyNotes() != null && !report.getDailyNotes().isEmpty()) {
                System.out.println("[Midas] Including " + report.getDailyNotes().size() + " daily notes in analysis");
            }
            LOG.info("Starting AI analysis for " + report.getCommits().size() + " commits");
            LOG.info("Using AI provider: " + configManager.getAIProvider());
            LOG.info("Using model: " + configManager.getModel());

            AIAnalyzerService.AnalysisResult result = aiAnalyzer.analyzeCommits(
                    report.getCommits(),
                    report.getDailyNotes(),
                    configManager
            ).join();

            if (result.hasError()) {
                System.err.println("[Midas] AI analysis failed: " + result.error);
                LOG.error("AI analysis failed: " + result.error);
                report.setSummary("**AI Analysis Failed**\n\n" +
                    "Error: " + result.error + "\n\n" +
                    "Please check:\n" +
                    "1. AI API key is correct\n" +
                    "2. Model is supported and available\n" +
                    "3. Network connection is stable\n" +
                    "4. API has sufficient quota\n\n" +
                    "Go to: Settings → Midas → AI Configuration");
                return;
            }

            System.out.println("[Midas] AI analysis completed successfully");
            LOG.info("AI analysis completed successfully");
            LOG.info("Summary length: " + (result.summary != null ? result.summary.length() : 0));
            LOG.info("Technical highlights length: " + (result.technicalHighlights != null ? result.technicalHighlights.length() : 0));
            LOG.info("Problems and solutions length: " + (result.problemsAndSolutions != null ? result.problemsAndSolutions.length() : 0));
            LOG.info("Next week plans length: " + (result.nextWeekPlans != null ? result.nextWeekPlans.length() : 0));

            report.setSummary(result.summary);
            report.setTechnicalHighlights(result.technicalHighlights);
            report.setProblemsAndSolutions(result.problemsAndSolutions);
            report.setNextWeekPlans(result.nextWeekPlans);
        } catch (Exception e) {
            System.err.println("[Midas] Failed to generate AI analysis: " + e.getMessage());
            e.printStackTrace();
            LOG.error("Failed to generate AI analysis", e);
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("timeout")) {
                report.setSummary("**AI Analysis Timeout**\n\n" +
                    "The AI model took too long to respond. This can happen when:\n" +
                    "- The prompt is too large\n" +
                    "- The AI model is overloaded\n" +
                    "- Network connection is slow\n\n" +
                    "Suggestions:\n" +
                    "1. Try using a faster model (e.g., glm-4-flash instead of glm-4-plus)\n" +
                    "2. Check your network connection\n" +
                    "3. Try again later\n\n" +
                    "Technical error: " + errorMsg);
            } else {
                report.setSummary("**AI Analysis Unavailable**\n\n" +
                    "Please check your AI configuration in:\n" +
                    "Settings → Midas → AI Configuration\n\n" +
                    "Error: " + errorMsg);
            }
        }
    }

    /**
     * Calculate statistics from commits
     */
    private Statistics calculateStatistics(List<CommitInfo> commits) {
        Statistics stats = Statistics.builder().build();
        for (CommitInfo commit : commits) {
            stats.addCommit(commit);
        }

        // Calculate average commits per day
        if (!commits.isEmpty()) {
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(
                    commits.get(0).getTimestamp().toLocalDate(),
                    commits.get(commits.size() - 1).getTimestamp().toLocalDate()
            ) + 1;
            stats.setAverageCommitsPerDay((double) stats.getTotalCommits() / Math.max(daysBetween, 1));
        }

        return stats;
    }

    /**
     * Create an empty report when no commits found
     */
    private WeeklyReport createEmptyReport(LocalDate weekStart, LocalDate weekEnd) {
        Statistics stats = Statistics.builder()
                .totalCommits(0)
                .totalInsertions(0)
                .totalDeletions(0)
                .totalFilesChanged(0)
                .build();

        return WeeklyReport.builder()
                .weekStart(weekStart)
                .weekEnd(weekEnd)
                .commits(List.of())
                .statistics(stats)
                .summary("No commits were recorded during this week.")
                .technicalHighlights("")
                .problemsAndSolutions("")
                .nextWeekPlans("")
                .projectName(project.getName())
                .generatedAt(LocalDateTime.now())
                .markdownContent(generateEmptyMarkdown(weekStart, weekEnd))
                .htmlContent(generateEmptyHTML(weekStart, weekEnd))
                .build();
    }

    private String generateEmptyMarkdown(LocalDate weekStart, LocalDate weekEnd) {
        return String.format(
                "# 开发周报 - %s 至 %s\n\n" +
                        "## 📊 本周概览\n\n" +
                        "本周没有提交记录。\n\n" +
                        "## 💡 主要工作内容\n\n" +
                        "暂无数据\n\n",
                weekStart.format(DATE_FORMATTER),
                weekEnd.format(DATE_FORMATTER)
        );
    }

    private String generateEmptyHTML(LocalDate weekStart, LocalDate weekEnd) {
        return String.format(
                "<html><head><style>%s</style></head><body>" +
                        "<h1>开发周报 - %s 至 %s</h1>" +
                        "<h2>本周概览</h2>" +
                        "<p>本周没有提交记录。</p>" +
                        "</body></html>",
                ReportTemplate.getBaseCSS(),
                weekStart.format(DATE_FORMATTER),
                weekEnd.format(DATE_FORMATTER)
        );
    }

    /**
     * Get the most recent report
     */
    public WeeklyReport getLatestReport() {
        return storage.getLatestReport().orElse(null);
    }

    /**
     * Get report for a specific week
     */
    public WeeklyReport getReportForWeek(LocalDate weekStart) {
        return storage.getReportForWeek(weekStart).orElse(null);
    }

    /**
     * Get all historical reports
     */
    public List<WeeklyReport> getAllReports() {
        return storage.getAllReports();
    }
}