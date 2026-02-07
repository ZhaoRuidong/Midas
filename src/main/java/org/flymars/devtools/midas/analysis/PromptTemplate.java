package org.flymars.devtools.midas.analysis;

import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.config.PluginConfig;
import org.flymars.devtools.midas.data.CommitInfo;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Template for building AI prompts
 */
public class PromptTemplate {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Build a prompt for weekly report generation (JSON format)
     */
    public static String buildWeeklyReportPrompt(List<CommitInfo> commits, ConfigManager config) {
        return buildWeeklyReportPrompt(commits, null, config);
    }

    /**
     * Build a prompt for weekly report generation with daily notes (JSON format)
     */
    public static String buildWeeklyReportPrompt(List<CommitInfo> commits,
                                                   List<org.flymars.devtools.midas.data.DailyNote> notes,
                                                   ConfigManager config) {
        if (commits.isEmpty() && (notes == null || notes.isEmpty())) {
            return "No commits or notes to analyze for this week.";
        }

        PluginConfig.ReportLanguage language = config.getReportLanguage();
        boolean isEnglish = language == PluginConfig.ReportLanguage.ENGLISH;

        StringBuilder prompt = new StringBuilder();

        if (isEnglish) {
            prompt.append("You are a technical report writer. Analyze the following Git commit information and generate a weekly report in JSON format.\n\n");
        } else {
            prompt.append("你是一名技术报告撰写专家。请分析以下Git提交信息并生成周报，以JSON格式返回。\n\n");
        }

        prompt.append("## Commits Data\n\n");

        // Group commits by project
        prompt.append("### Commits by Project\n\n");
        appendCommitsByProject(prompt, commits);

        // Add daily notes if available
        if (notes != null && !notes.isEmpty()) {
            prompt.append("\n---\n\n");
            prompt.append("## Daily Notes\n\n");

            if (isEnglish) {
                prompt.append("The following are manual notes recorded by the developer during the week. ");
                prompt.append("Integrate this information with the commit data to provide a more complete report:\n\n");
            } else {
                prompt.append("以下是开发者本周手动记录的笔记。请将这些信息与提交数据结合，生成更完整的报告：\n\n");
            }

            for (org.flymars.devtools.midas.data.DailyNote note : notes) {
                prompt.append("### ").append(note.getDate().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)).append("\n\n");
                prompt.append(note.getContent()).append("\n\n");
            }

            prompt.append("---\n\n");

            if (isEnglish) {
                prompt.append("IMPORTANT: When generating the report, combine insights from BOTH commits and daily notes. ");
                prompt.append("The daily notes contain important context that may not be reflected in commit messages.\n\n");
            } else {
                prompt.append("重要提示：生成报告时，请结合提交信息和每日笔记。");
                prompt.append("每日笔记包含了一些可能在提交信息中未反映的重要上下文。\n\n");
            }
        }

        prompt.append("\n---\n\n");

        if (isEnglish) {
            prompt.append("Please analyze these commits and return a JSON object with the following structure:\n\n");
        } else {
            prompt.append("请分析这些提交并返回以下格式的JSON对象：\n\n");
        }

        prompt.append("```json\n");
        prompt.append("{\n");

        if (isEnglish) {
            prompt.append("  \"summary\": \"Concise summary of main work completed this week. Organize BY PROJECT with subsections. Each project summary should be 3-5 bullet points max.\",\n");
            prompt.append("  \"technicalHighlights\": \"Key technical achievements and decisions ONLY. Focus on: core technical solutions, important architectural changes, significant optimizations. Group by project. Limit to 2-3 items per project.\",\n");
            prompt.append("  \"problemsAndSolutions\": \"Only problems that were actually SOLVED this week. Format as table with | Problem | Solution | columns. Do not include theoretical issues.\",\n");
            prompt.append("  \"nextWeekPlans\": \"Brief suggestions based on THIS WEEK's work. 2-3 items per project max. Focus on follow-up tasks and immediate next steps.\"\n");
        } else {
            prompt.append("  \"summary\": \"本周工作完成情况的简要总结。按项目分组，每个项目3-5个要点。\",\n");
            prompt.append("  \"technicalHighlights\": \"仅包含关键技术成果和决策。聚焦于：核心技术方案、重要架构变更、重大性能优化。按项目分组，每个项目2-3项。\",\n");
            prompt.append("  \"problemsAndSolutions\": \"仅包含本周实际解决的问题。使用表格格式，列：| 问题 | 解决方案 |。不要包含理论问题。\",\n");
            prompt.append("  \"nextWeekPlans\": \"基于本周工作的简要建议。每个项目2-3项，聚焦于跟进任务和下一步计划。\"\n");
        }

        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("CRITICAL REQUIREMENTS:\n");

        if (isEnglish) {
            prompt.append("1. Return ONLY the JSON object, no additional text or markdown formatting outside the JSON\n");
            prompt.append("2. BE CONCISE - Use bullet points, avoid long paragraphs\n");
            prompt.append("3. Focus on OUTCOMES, not process. WHAT was done, not HOW\n");
            prompt.append("4. Each project section: maximum 5 bullet points for summary, 3 for technical highlights\n");
            prompt.append("5. Omit trivial changes (formatting, comments, minor refactors)\n");
            prompt.append("6. Merge related commits into single points\n");
            prompt.append("7. Use markdown formatting within JSON strings: ### for project names, - for bullet points, | for tables\n");
            prompt.append("8. Keep each field under 500 characters if possible\n");
            prompt.append("9. Write the report in English\n\n");
        } else {
            prompt.append("1. 只返回JSON对象，不要有额外的文本或markdown格式\n");
            prompt.append("2. 简洁精炼 - 使用要点列表，避免长段落\n");
            prompt.append("3. 聚焦结果，而非过程。关注\"做了什么\"，而非\"怎么做的\"\n");
            prompt.append("4. 每个项目部分：总结最多5个要点，技术亮点最多3个\n");
            prompt.append("5. 省略琐碎改动（格式调整、注释修改、小型重构）\n");
            prompt.append("6. 将相关的提交合并为一个要点\n");
            prompt.append("7. 在JSON字符串内使用markdown格式：### 表示项目名称，- 表示要点，| 表示表格\n");
            prompt.append("8. 每个字段尽量控制在500字符以内\n");
            prompt.append("9. 使用中文撰写报告\n\n");
        }

        prompt.append("Style examples:\n");

        if (isEnglish) {
            prompt.append("- ❌ 'Refactored the cache implementation by introducing a new layer of abstraction...'\n");
            prompt.append("- ✅ 'Refactored cache layer for better performance'\n");
            prompt.append("- ❌ 'Investigated the issue with the database connection...'\n");
            prompt.append("- ✅ 'Fixed database connection timeout issue'\n");
        } else {
            prompt.append("- ❌ '通过引入新的抽象层重构了缓存实现...'\n");
            prompt.append("- ✅ '重构缓存层以提升性能'\n");
            prompt.append("- ❌ '调查了数据库连接的问题...'\n");
            prompt.append("- ✅ '修复数据库连接超时问题'\n");
        }

        return prompt.toString();
    }

    /**
     * Build a prompt for summarizing a single commit
     */
    public static String buildCommitSummaryPrompt(CommitInfo commit) {
        return String.format(
                "Analyze this commit and provide a brief summary:\n" +
                        "Hash: %s\n" +
                        "Message: %s\n" +
                        "Author: %s\n" +
                        "Date: %s\n" +
                        "Files: %s\n\n" +
                        "Provide a 1-2 sentence summary of what was accomplished.",
                commit.getHash(),
                commit.getMessage(),
                commit.getAuthor(),
                commit.getTimestamp().format(DATE_FORMATTER),
                String.join(", ", commit.getFiles())
        );
    }

    /**
     * Append commits grouped by project
     */
    private static void appendCommitsByProject(StringBuilder prompt, List<CommitInfo> commits) {
        // Group by project name
        commits.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        c -> c.getGitlabProjectName() != null ? c.getGitlabProjectName() : "Unknown Project",
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ))
                .forEach((projectName, projectCommits) -> {
                    prompt.append(String.format("**%s** (%d commits)\n", projectName, projectCommits.size()));
                    for (CommitInfo commit : projectCommits) {
                        prompt.append(String.format("- %s\n", commit.getMessage()));
                    }
                    prompt.append("\n");
                });
    }
}
