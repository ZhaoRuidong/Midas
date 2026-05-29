package org.flymars.devtools.midas.ui.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.CommitMessageI;
import org.flymars.devtools.midas.analysis.AIAnalyzerService;
import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.config.PluginConfig;
import org.flymars.devtools.midas.ui.notification.NotificationManager;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GenerateCommitMessageAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(GenerateCommitMessageAction.class);

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        ConfigManager config = getConfig(e);
        PluginConfig.CommitMsgProvider provider = config.getCommitMsgProvider();
        boolean enabled;
        if (provider == PluginConfig.CommitMsgProvider.API) {
            String apiKey = config.getApiKey();
            enabled = apiKey != null && !apiKey.isEmpty();
        } else {
            enabled = true;
        }
        e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ConfigManager config = getConfig(e);
        boolean includeConfigFiles = config.isCommitMsgConfigFileEnabled();

        Change[] changes = e.getData(VcsDataKeys.CHANGES);
        if (changes == null || changes.length == 0) {
            changes = ChangeListManager.getInstance(project).getAllChanges().toArray(new Change[0]);
        }
        if (changes.length == 0) {
            NotificationManager.showWarning(project, "Midas", "No changes found in the current workspace.");
            return;
        }

        if (!includeConfigFiles) {
            changes = filterOutConfigFiles(changes);
        }
        if (changes.length == 0) {
            NotificationManager.showWarning(project, "Midas", "All changes are config files. Enable '配置文件提交' to include them.");
            return;
        }

        CommitMessageI commitMessagePanel = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL);
        Change[] finalChanges = changes;

        e.getPresentation().setEnabled(false);
        e.getPresentation().setText("Generating...");

        PluginConfig.CommitMsgProvider provider = config.getCommitMsgProvider();
        LOG.info("GenerateCommitMessage triggered. Provider=" + provider + ", changes=" + finalChanges.length);

        CompletableFuture.runAsync(() -> {
            try {
                LOG.info("Async task started. Provider=" + provider);
                String message;
                if (provider == PluginConfig.CommitMsgProvider.API) {
                    String diffContent = buildDiffContent(finalChanges);
                    if (diffContent.isEmpty()) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            NotificationManager.showWarning(project, "Midas", "Could not read diff content from changes.");
                            restoreButton(e);
                        });
                        return;
                    }
                    AIAnalyzerService aiService = ApplicationManager.getApplication().getService(AIAnalyzerService.class);
                    message = aiService.generateCommitMessage(diffContent, config).join();
                } else {
                    LOG.info("Building diff content for local agent...");
                    String diffContent = buildDiffContent(finalChanges);
                    LOG.info("Diff content built. Length=" + diffContent.length() + ", empty=" + diffContent.isEmpty());

                    if (diffContent.isEmpty()) {
                        throw new RuntimeException("Could not read diff content from changes.");
                    }

                    Map<File, List<Change>> grouped = groupByGitRoot(finalChanges, project);
                    LOG.info("Local agent: " + grouped.size() + " git repo(s) detected");

                    if (grouped.size() == 1) {
                        Map.Entry<File, List<Change>> entry = grouped.entrySet().iterator().next();
                        message = runAgentProcess(provider, diffContent, config, entry.getKey());
                    } else {
                        message = runMultiRepoAgent(provider, grouped, config);
                    }
                }

                LOG.info("Commit message generated. Raw length=" + message.length());
                String finalMessage = cleanCommitMessage(message);
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (commitMessagePanel != null) {
                        commitMessagePanel.setCommitMessage(finalMessage);
                        NotificationManager.showInfo(project, "Midas", "Commit message generated successfully.");
                    } else {
                        Messages.showInputDialog(project, "Generated Commit Message:", "Midas", null, finalMessage, null);
                    }
                    restoreButton(e);
                });
            } catch (Exception ex) {
                LOG.error("Failed to generate commit message", ex);
                ApplicationManager.getApplication().invokeLater(() -> {
                    NotificationManager.showError(project, "Midas", "Failed to generate commit message: " + ex.getMessage());
                    restoreButton(e);
                });
            }
        });
    }

    private void restoreButton(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(true);
        e.getPresentation().setText("Generate Commit Messages With Midas");
    }

    private String cleanCommitMessage(String raw) {
        String cleaned = raw.trim();
        // Remove markdown code block markers
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline >= 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            } else {
                cleaned = cleaned.substring(3);
            }
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        // Remove surrounding quotes if the entire message is quoted
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) ||
            (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.trim();
    }

    private ConfigManager getConfig(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            return ConfigManager.getInstance(project);
        }
        return ConfigManager.getGlobalInstance();
    }

    /**
     * Group changes by their git repository root, detected by walking up to find .git directory.
     * This avoids git4idea API threading issues.
     */
    private Map<File, List<Change>> groupByGitRoot(Change[] changes, Project project) {
        Map<File, List<Change>> grouped = new LinkedHashMap<>();
        for (Change change : changes) {
            File repoRoot = findGitRoot(change, project);
            grouped.computeIfAbsent(repoRoot, k -> new ArrayList<>()).add(change);
        }
        return grouped;
    }

    private File findGitRoot(Change change, Project project) {
        ContentRevision revision = change.getAfterRevision() != null ? change.getAfterRevision() : change.getBeforeRevision();
        if (revision != null) {
            String path = revision.getFile().getPath();
            File dir = new File(path).getParentFile();
            while (dir != null) {
                if (new File(dir, ".git").exists()) {
                    return dir;
                }
                dir = dir.getParentFile();
            }
        }
        return project.getBasePath() != null ? new File(project.getBasePath()) : new File(".");
    }

    private String runMultiRepoAgent(PluginConfig.CommitMsgProvider provider, Map<File, List<Change>> grouped, ConfigManager config) throws Exception {
        StringBuilder combinedMessage = new StringBuilder();
        for (Map.Entry<File, List<Change>> entry : grouped.entrySet()) {
            String diffContent = buildDiffContent(entry.getValue().toArray(new Change[0]));
            if (diffContent.isEmpty()) continue;

            String repoName = entry.getKey().getName();
            LOG.info("Running agent for repo: " + repoName);
            String partial = runAgentProcess(provider, diffContent, config, entry.getKey());
            combinedMessage.append("[ ").append(repoName).append(" ] ").append(partial.trim()).append("\n");
        }

        String result = combinedMessage.toString().trim();
        if (result.isEmpty()) {
            throw new RuntimeException("All agents returned empty output");
        }
        return result;
    }

    private String runAgentProcess(PluginConfig.CommitMsgProvider provider, String diffContent, ConfigManager config, File workDir) throws Exception {
        String prompt = config.getCommitMsgLocalPrompt();
        String command = buildAgentCommand(provider, prompt, diffContent);

        LOG.info("Running local agent. workDir=" + workDir.getAbsolutePath() + ", command=" + command);

        ProcessBuilder pb;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            pb = new ProcessBuilder("cmd", "/c", command);
        } else {
            pb = new ProcessBuilder("sh", "-c", command);
        }
        pb.redirectErrorStream(true);
        pb.directory(workDir);

        LOG.info("Starting process...");
        Process process = pb.start();
        LOG.info("Process started, waiting for output...");
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Agent in " + workDir.getName() + " exited with code " + exitCode + ": " + output);
        }

        String result = output.toString().trim();
        if (result.isEmpty()) {
            throw new RuntimeException("Agent in " + workDir.getName() + " returned empty output");
        }
        return result;
    }

    private String buildAgentCommand(PluginConfig.CommitMsgProvider provider, String prompt, String diffContent) {
        try {
            File tempFile = File.createTempFile("midas-diff-", ".txt");
            tempFile.deleteOnExit();
            Files.writeString(tempFile.toPath(), diffContent);

            String escapedPrompt = prompt.replace("'", "'\\''");
            String diffFilePath = tempFile.getAbsolutePath();

            return switch (provider) {
                case CLAUDE -> "claude -p '" + escapedPrompt + "' < '" + diffFilePath + "'";
                case CODEX -> "codex --prompt '" + escapedPrompt + "' < '" + diffFilePath + "'";
                case OPENCODE -> "opencode -p '" + escapedPrompt + "' < '" + diffFilePath + "'";
                default -> throw new IllegalArgumentException("Unknown local agent: " + provider);
            };
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create temp diff file", e);
        }
    }

    private Change[] filterOutConfigFiles(Change[] changes) {
        return java.util.Arrays.stream(changes)
                .filter(c -> {
                    String path = getFilePath(c).toLowerCase();
                    return !path.endsWith(".properties") &&
                           !path.endsWith(".xml") &&
                           !path.endsWith(".yml") &&
                           !path.endsWith(".yaml") &&
                           !path.endsWith(".json") &&
                           !path.endsWith(".toml") &&
                           !path.endsWith(".ini") &&
                           !path.endsWith(".conf") &&
                           !path.endsWith(".cfg");
                })
                .toArray(Change[]::new);
    }

    private String buildDiffContent(Change[] changes) {
        StringBuilder sb = new StringBuilder();
        for (Change change : changes) {
            sb.append("--- ").append(getFilePath(change)).append("\n");

            String before = getContent(change.getBeforeRevision());
            String after = getContent(change.getAfterRevision());

            if (before != null && after != null) {
                sb.append("+++ ").append(getFilePath(change)).append("\n");
                for (String line : before.split("\n")) {
                    sb.append("- ").append(line).append("\n");
                }
                for (String line : after.split("\n")) {
                    sb.append("+ ").append(line).append("\n");
                }
            } else if (after != null) {
                sb.append("+++ ").append(getFilePath(change)).append(" (new file)\n");
                for (String line : after.split("\n")) {
                    sb.append("+ ").append(line).append("\n");
                }
            } else if (before != null) {
                sb.append("+++ (deleted)\n");
                for (String line : before.split("\n")) {
                    sb.append("- ").append(line).append("\n");
                }
            }

            sb.append("\n");
        }

        String result = sb.toString();
        if (result.length() > 15000) {
            result = result.substring(0, 15000) + "\n... (truncated)";
        }
        return result;
    }

    private String getFilePath(Change change) {
        if (change.getAfterRevision() != null) {
            return change.getAfterRevision().getFile().getName();
        }
        if (change.getBeforeRevision() != null) {
            return change.getBeforeRevision().getFile().getName();
        }
        return "unknown";
    }

    private String getContent(ContentRevision revision) {
        if (revision == null) return null;
        try {
            return revision.getContent();
        } catch (Exception e) {
            LOG.warn("Failed to read content for revision", e);
            return null;
        }
    }
}
