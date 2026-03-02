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
            prompt.append("  \"summary\": \"Concise summary of main work completed this week. Organize BY CATEGORY with subsections. Categories include: Product Development, External Support, Organization Building, etc. Each item should use format: 1) 2) 3). Be formal and concise.\",\n");
            prompt.append("  \"nextWeekPlans\": \"Brief suggestions based on THIS WEEK's work. Each item should use format: 1、2、3、. Focus on follow-up tasks and immediate next steps.\"\n");
        } else {
            prompt.append("  \"summary\": \"本周工作完成情况的简要总结。按分类组织，包括：产品研发、外部支持、组织建设等。使用格式：一、类别名\\n1）具体工作1\\n2）具体工作2。语言端庄严谨、简洁精炼。\",\n");
            prompt.append("  \"nextWeekPlans\": \"基于本周工作的简要建议。使用格式：1、具体计划1\\n2、具体计划2。聚焦于跟进任务和下一步计划。\"\n");
        }

        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("CRITICAL REQUIREMENTS:\n");

        if (isEnglish) {
            prompt.append("1. Return ONLY the JSON object, no additional text or markdown formatting outside the JSON\n");
            prompt.append("2. BE CONCISE AND FORMAL - Use professional business language\n");
            prompt.append("3. Organize work by CATEGORY (Product Development, External Support, Organization Building, etc.)\n");
            prompt.append("4. Format: 一、Category Name\\n1) Specific work item\\n2) Specific work item\n");
            prompt.append("5. Each category should have 2-5 items maximum\n");
            prompt.append("6. Omit trivial changes (formatting, comments, minor refactors)\n");
            prompt.append("7. Merge related commits into single points\n");
            prompt.append("8. Use simple Chinese numbering format: 一、二、三 for categories, 1) 2) 3) for items\n");
            prompt.append("9. Keep each field under 500 characters if possible\n");
            prompt.append("10. Write the report in English\n\n");
        } else {
            prompt.append("1. 只返回JSON对象，不要有额外的文本或markdown格式\n");
            prompt.append("2. 简洁精炼、端庄严谨 - 使用正式商务语言\n");
            prompt.append("3. 按分类组织工作（产品研发、外部支持、组织建设等）\n");
            prompt.append("4. 格式：一、分类名称\\n1）具体工作1\\n2）具体工作2\n");
            prompt.append("5. 每个分类最多5项\n");
            prompt.append("6. 省略琐碎改动（格式调整、注释修改、小型重构）\n");
            prompt.append("7. 将相关的提交合并为一个要点\n");
            prompt.append("8. 使用中文数字编号：一、二、三 表示分类，1）2）3）表示具体项目\n");
            prompt.append("9. 每个字段尽量控制在500字符以内\n");
            prompt.append("10. 使用中文撰写报告\n\n");
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
            prompt.append("\n示例格式：\n");
            prompt.append("一、产品研发\n");
            prompt.append("1）跟踪各产品补丁集\n");
            prompt.append("2）AI Coding功能验证\n");
            prompt.append("\n下周计划\n");
            prompt.append("1、数据链共建立项事宜\n");
            prompt.append("2、日照银行交流\n");
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
