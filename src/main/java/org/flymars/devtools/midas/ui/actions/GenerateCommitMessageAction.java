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
                    Map<File, List<Change>> grouped = groupByGitRoot(finalChanges, project);
                    LOG.info("Local agent: " + grouped.size() + " git repo(s) detected");

                    if (grouped.size() == 1) {
                        Map.Entry<File, List<Change>> entry = grouped.entrySet().iterator().next();
                        message = runAgentProcess(provider, config, entry.getKey());
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
        return project != null && project.getBasePath() != null ? new File(project.getBasePath()) : new File(".");
    }

    private String runMultiRepoAgent(PluginConfig.CommitMsgProvider provider, Map<File, List<Change>> grouped, ConfigManager config) throws Exception {
        StringBuilder combinedMessage = new StringBuilder();
        for (Map.Entry<File, List<Change>> entry : grouped.entrySet()) {
            String repoName = entry.getKey().getName();
            LOG.info("Running agent for repo: " + repoName);
            String partial = runAgentProcess(provider, config, entry.getKey());
            combinedMessage.append("[ ").append(repoName).append(" ] ").append(partial.trim()).append("\n");
        }

        String result = combinedMessage.toString().trim();
        if (result.isEmpty()) {
            throw new RuntimeException("All agents returned empty output");
        }
        return result;
    }

    private String runAgentProcess(PluginConfig.CommitMsgProvider provider, ConfigManager config, File workDir) throws Exception {
        String prompt = config.getCommitMsgLocalPrompt();
        String command = buildAgentCommand(provider, prompt);

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

    private String buildAgentCommand(PluginConfig.CommitMsgProvider provider, String prompt) {
        String escapedPrompt = prompt.replace("'", "'\\''");

        return switch (provider) {
            case CLAUDE -> "claude -p '" + escapedPrompt + "' < /dev/null";
            case CODEX -> "codex --prompt '" + escapedPrompt + "' < /dev/null";
            case OPENCODE -> "opencode -p '" + escapedPrompt + "' < /dev/null";
            default -> throw new IllegalArgumentException("Unknown local agent: " + provider);
        };
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
        String gitDiff = buildGitDiffContent(changes);
        if (gitDiff != null && !gitDiff.isEmpty()) {
            return gitDiff;
        }

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
        if (result.length() > 30000) {
            result = result.substring(0, 30000) + "\n... (truncated)";
        }
        return result;
    }

    private String buildGitDiffContent(Change[] changes) {
        try {
            Map<File, List<Change>> byRoot = new LinkedHashMap<>();
            for (Change change : changes) {
                File root = findGitRoot(change, null);
                if (root != null) {
                    byRoot.computeIfAbsent(root, k -> new ArrayList<>()).add(change);
                }
            }
            if (byRoot.isEmpty()) return null;

            StringBuilder diff = new StringBuilder();

            for (Map.Entry<File, List<Change>> entry : byRoot.entrySet()) {
                File gitRoot = entry.getKey();
                List<Change> repoChanges = entry.getValue();

                List<String> trackedFiles = new ArrayList<>();
                List<Change> newFileChanges = new ArrayList<>();

                for (Change c : repoChanges) {
                    String relPath = getRelativePathFromRoot(c, gitRoot);
                    if (relPath == null) continue;
                    if (c.getBeforeRevision() == null) {
                        newFileChanges.add(c);
                    } else {
                        trackedFiles.add(relPath);
                    }
                }

                if (!trackedFiles.isEmpty()) {
                    List<String> cmd = new ArrayList<>();
                    cmd.add("git");
                    cmd.add("diff");
                    cmd.add("HEAD");
                    cmd.add("--");
                    cmd.addAll(trackedFiles);

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.directory(gitRoot);
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    String output = new String(process.getInputStream().readAllBytes(), "UTF-8");
                    int exitCode = process.waitFor();

                    if (exitCode != 0) {
                        cmd.remove(2);
                        pb = new ProcessBuilder(cmd);
                        pb.directory(gitRoot);
                        pb.redirectErrorStream(true);
                        process = pb.start();
                        output = new String(process.getInputStream().readAllBytes(), "UTF-8");
                        process.waitFor();
                    }

                    diff.append(output);
                }

                for (Change c : newFileChanges) {
                    String relPath = getRelativePathFromRoot(c, gitRoot);
                    if (relPath == null) continue;
                    String content = getContent(c.getAfterRevision());
                    if (content != null) {
                        diff.append("diff --git a/").append(relPath).append(" b/").append(relPath).append("\n");
                        diff.append("new file mode 100644\n");
                        diff.append("--- /dev/null\n");
                        diff.append("+++ b/").append(relPath).append("\n");
                        for (String line : content.split("\n")) {
                            diff.append("+").append(line).append("\n");
                        }
                        diff.append("\n");
                    }
                }
            }

            String result = diff.toString();
            if (result.length() > 30000) {
                result = result.substring(0, 30000) + "\n... (truncated)";
            }
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            LOG.warn("Failed to build git diff, falling back to content-based diff", e);
            return null;
        }
    }

    private String getRelativePathFromRoot(Change change, File root) {
        ContentRevision rev = change.getAfterRevision() != null ? change.getAfterRevision() : change.getBeforeRevision();
        if (rev == null) return null;
        String absPath = rev.getFile().getPath();
        String rootPath = root.getAbsolutePath();
        if (absPath.startsWith(rootPath + File.separator)) {
            return absPath.substring(rootPath.length() + 1);
        }
        return rev.getFile().getName();
    }

    private String getFilePath(Change change) {
        ContentRevision rev = change.getAfterRevision() != null ? change.getAfterRevision() : change.getBeforeRevision();
        if (rev == null) return "unknown";
        String path = rev.getFile().getPath();
        File gitRoot = findGitRoot(change, null);
        if (gitRoot != null) {
            String rootPath = gitRoot.getAbsolutePath();
            if (path.startsWith(rootPath + File.separator)) {
                return path.substring(rootPath.length() + 1);
            }
        }
        return rev.getFile().getName();
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
