package org.flymars.devtools.midas.ui.toolwindow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.data.CommitInfo;
import org.flymars.devtools.midas.data.WeeklyReport;
import org.flymars.devtools.midas.email.EmailService;
import org.flymars.devtools.midas.gitlab.GitLabProjectService;
import org.flymars.devtools.midas.report.ReportTemplate;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.util.List;

/**
 * Dialog for previewing, editing and sending weekly reports
 */
public class ReportPreviewDialog extends JDialog {
    private static final Logger LOG = Logger.getInstance(ReportPreviewDialog.class);

    private final Project project;
    private final WeeklyReport report;
    private final ConfigManager configManager;
    private final GitLabProjectService gitlabProjectService;
    private final EmailService emailService;

    private JTextArea reportTextArea;
    private JButton sendButton;
    private JButton saveButton;
    private JButton closeButton;
    private JLabel infoLabel;

    public ReportPreviewDialog(Project project, WeeklyReport report,
                            ConfigManager configManager,
                            GitLabProjectService gitlabProjectService) {
        super(getParentFrame(project));
        this.project = project;
        this.report = report;
        this.configManager = configManager;
        this.gitlabProjectService = gitlabProjectService;
        this.emailService = new EmailService(configManager, project);

        System.out.println("[Midas] Creating ReportPreviewDialog...");
        setTitle("Weekly Report Preview");
        setModal(true);
        setSize(800, 600);
        setLocationRelativeTo(getParentFrame(project));
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        System.out.println("[Midas] Initializing UI components...");
        initComponents();
        System.out.println("[Midas] Loading report content...");
        loadReport();
        System.out.println("[Midas] ReportPreviewDialog creation completed");
    }

    /**
     * Get the parent frame for the dialog
     */
    private static java.awt.Frame getParentFrame(Project project) {
        if (project == null) {
            return null;
        }
        // Try to get the active frame from the IDE
        return com.intellij.openapi.wm.WindowManager.getInstance().getFrame(project);
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout(10, 5));
        JLabel titleLabel = new JLabel("<html><h2 style='margin:0;'>Weekly Report Preview</h2></html>");
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        infoLabel = new JLabel();
        infoLabel.setFont(new Font(infoLabel.getFont().getName(), Font.PLAIN, 11));
        infoLabel.setForeground(new Color(100, 100, 100));
        headerPanel.add(infoLabel, BorderLayout.CENTER);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Report content (editable)
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createTitledBorder("Report Content"));

        reportTextArea = new JTextArea();
        reportTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(reportTextArea);

        contentPanel.add(scrollPane, BorderLayout.CENTER);

        // Edit instructions
        JLabel editLabel = new JLabel("<html><i style='color: #666;'>You can edit the report content above before sending.</i></html>");
        contentPanel.add(editLabel, BorderLayout.SOUTH);

        mainPanel.add(contentPanel, BorderLayout.CENTER);

        // Footer buttons
        JPanel footerPanel = new JPanel(new BorderLayout(10, 5));

        saveButton = new JButton("💾 Save to File");
        saveButton.setToolTipText("Save report to a text file");
        saveButton.addActionListener(e -> saveToFile());

        sendButton = new JButton("📧 Send Email");
        sendButton.setToolTipText("Send report via email");
        sendButton.addActionListener(e -> sendEmail());

        closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        JPanel buttonsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        buttonsRight.add(saveButton);
        buttonsRight.add(sendButton);
        buttonsRight.add(closeButton);

        footerPanel.add(buttonsRight, BorderLayout.EAST);

        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void loadReport() {
        System.out.println("[Midas] Loading report into preview dialog...");
        LOG.info("Loading report into preview dialog");

        // Use the AI-generated markdown content if available
        String content = report.getMarkdownContent();

        System.out.println("[Midas] Markdown content available: " + (content != null && !content.isEmpty()));
        LOG.info("Markdown content available: " + (content != null && !content.isEmpty()));
        if (content != null) {
            System.out.println("[Midas] Markdown content length: " + content.length());
            LOG.info("Markdown content length: " + content.length());
            System.out.println("[Midas] Summary available: " + (report.getSummary() != null && !report.getSummary().isEmpty()));
            LOG.info("Summary available: " + (report.getSummary() != null && !report.getSummary().isEmpty()));
            System.out.println("[Midas] Technical highlights available: " + (report.getTechnicalHighlights() != null && !report.getTechnicalHighlights().isEmpty()));
            LOG.info("Technical highlights available: " + (report.getTechnicalHighlights() != null && !report.getTechnicalHighlights().isEmpty()));
        }

        if (content == null || content.isEmpty()) {
            System.out.println("[Midas] Markdown content is empty, using fallback report generation");
            LOG.warn("Markdown content is empty, using fallback report generation");
            // Fallback: generate simple report if markdown content is not available
            content = generateSimpleReport();
            System.out.println("[Midas] Generated fallback report, length: " + content.length());
        }

        System.out.println("[Midas] Setting report text to text area, length: " + content.length());
        reportTextArea.setText(content);
        reportTextArea.setCaretPosition(0);
        System.out.println("[Midas] Report content loaded successfully");

        // Update info label
        List<CommitInfo> commits = report.getCommits();
        if (commits.isEmpty()) {
            infoLabel.setText("No commits in this week");
        } else {
            infoLabel.setText(String.format("Week: %s to %s | %d commits from %d projects",
                    report.getWeekStart(),
                    report.getWeekEnd(),
                    commits.size(),
                    commits.stream().map(c -> c.getGitlabProjectName()).distinct().count()));
        }
    }

    /**
     * Generate a simple report if markdown content is not available
     */
    private String generateSimpleReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\n");
        sb.append("                              WEEKLY REPORT\n");
        sb.append("================================================================================\n\n");

        sb.append("Period: ").append(report.getWeekStart()).append(" to ").append(report.getWeekEnd()).append("\n");
        sb.append("Generated: ").append(report.getGeneratedAt()).append("\n");
        sb.append("Total Commits: ").append(report.getCommits().size()).append("\n");
        // Statistics may be null
        if (report.getStatistics() != null) {
            sb.append("Total Files Changed: ").append(report.getStatistics().getTotalFilesChanged()).append("\n");
            sb.append("Total Additions: ").append(report.getStatistics().getTotalInsertions()).append("\n");
            sb.append("Total Deletions: ").append(report.getStatistics().getTotalDeletions()).append("\n");
        }
        sb.append("================================================================================\n\n");

        // If AI summary is available, include it
        if (report.getSummary() != null && !report.getSummary().isEmpty()) {
            sb.append("## WORK SUMMARY\n\n");
            sb.append(report.getSummary());
            sb.append("\n\n");
        }

        if (report.getTechnicalHighlights() != null && !report.getTechnicalHighlights().isEmpty()) {
            sb.append("## TECHNICAL HIGHLIGHTS\n\n");
            sb.append(report.getTechnicalHighlights());
            sb.append("\n\n");
        }

        if (report.getProblemsAndSolutions() != null && !report.getProblemsAndSolutions().isEmpty()) {
            sb.append("## PROBLEMS AND SOLUTIONS\n\n");
            sb.append(report.getProblemsAndSolutions());
            sb.append("\n\n");
        }

        if (report.getNextWeekPlans() != null && !report.getNextWeekPlans().isEmpty()) {
            sb.append("## NEXT WEEK PLANS\n\n");
            sb.append(report.getNextWeekPlans());
            sb.append("\n\n");
        }

        // Group commits by project
        sb.append("================================================================================\n");
        sb.append("COMMITS BY PROJECT\n");
        sb.append("================================================================================\n\n");

        List<CommitInfo> commits = report.getCommits();
        String currentProject = "";

        for (CommitInfo commit : commits) {
            String projectName = commit.getGitlabProjectName() != null
                ? commit.getGitlabProjectName()
                : "Unknown Project";

            if (!projectName.equals(currentProject)) {
                if (!currentProject.isEmpty()) {
                    sb.append("\n");
                }
                currentProject = projectName;
                sb.append("────────────────────────────────────────────────────────────────────────────────\n");
                sb.append("PROJECT: ").append(projectName).append("\n");
                sb.append("────────────────────────────────────────────────────────────────────────────────\n\n");
            }

            // Commit details
            sb.append("• ").append(commit.getHash())
              .append(" (").append(commit.getTimestamp()).append(") ")
              .append(commit.getAuthor()).append("\n");
            sb.append("  ").append(commit.getMessage()).append("\n");

            if (commit.getInsertions() > 0 || commit.getDeletions() > 0) {
                sb.append("  Stats: +").append(commit.getInsertions())
                  .append(" / -").append(commit.getDeletions()).append("\n");
            }
            if (commit.getFiles() != null && !commit.getFiles().isEmpty()) {
                sb.append("  Files: ").append(commit.getFiles().size()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("================================================================================\n");
        sb.append("                           END OF REPORT\n");
        sb.append("================================================================================\n");

        return sb.toString();
    }

    private void saveToFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Weekly Report");
        String defaultFileName = String.format("weekly-report-%s.txt", report.getWeekStart());
        chooser.setSelectedFile(new java.io.File(defaultFileName));
        chooser.setFileFilter(new FileNameExtensionFilter("Text Files", "txt"));

        int result = chooser.showSaveDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                java.io.File file = chooser.getSelectedFile();
                java.nio.file.Files.writeString(file.toPath(), reportTextArea.getText(), java.nio.file.StandardOpenOption.CREATE);

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Report saved successfully to:\n" + file.getAbsolutePath(),
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception e) {
                LOG.error("Error saving report to file", e);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Failed to save report:\n" + e.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }
    }

    private void sendEmail() {
        StringBuilder message = new StringBuilder();
        message.append("Are you sure you want to send this report via email?\n\n");
        message.append("To: ").append(String.join(", ", configManager.getToEmails())).append("\n");

        if (configManager.getCcEmails() != null && !configManager.getCcEmails().isEmpty()) {
            message.append("CC: ").append(String.join(", ", configManager.getCcEmails())).append("\n");
        }

        message.append("Subject: ").append(configManager.getEmailSubjectFormat()).append("\n");
        message.append("Commits: ").append(report.getCommits().size());

        int result = JOptionPane.showConfirmDialog(this,
                message.toString(),
                "Confirm Send Email",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        sendButton.setEnabled(false);
        sendButton.setText("Sending...");

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    // Update report content with user's edits
                    String editedMarkdown = reportTextArea.getText();
                    report.setMarkdownContent(editedMarkdown);
                    // Update HTML content from edited markdown
                    report.setHtmlContent(convertMarkdownToHtml(editedMarkdown));

                    emailService.sendReport(report).get();
                    return true;
                } catch (Exception e) {
                    LOG.error("Error sending email", e);
                    return false;
                }
            }

            @Override
            protected void done() {
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (Boolean.TRUE.equals(get())) {
                            StringBuilder successMsg = new StringBuilder();
                            successMsg.append("Report sent successfully!\n\n");
                            successMsg.append("To: ").append(String.join(", ", configManager.getToEmails()));

                            if (configManager.getCcEmails() != null && !configManager.getCcEmails().isEmpty()) {
                                successMsg.append("\nCC: ").append(String.join(", ", configManager.getCcEmails()));
                            }

                            successMsg.append("\n\nTotal commits: ").append(report.getCommits().size());

                            JOptionPane.showMessageDialog(ReportPreviewDialog.this,
                                    successMsg.toString(),
                                    "Success",
                                    JOptionPane.INFORMATION_MESSAGE);
                            dispose();
                        } else {
                            JOptionPane.showMessageDialog(ReportPreviewDialog.this,
                                    "Failed to send report.\n" +
                                    "Please check your email settings in:\n" +
                                    "Settings → Midas → Email Configuration",
                                    "Error",
                                    JOptionPane.ERROR_MESSAGE);
                            sendButton.setEnabled(true);
                            sendButton.setText("📧 Send Email");
                        }
                    } catch (Exception e) {
                        LOG.error("Error in done method", e);
                        JOptionPane.showMessageDialog(ReportPreviewDialog.this,
                                "Error: " + e.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                        sendButton.setEnabled(true);
                        sendButton.setText("📧 Send Email");
                    }
                });
            }
        };
        worker.execute();
    }

    /**
     * Convert markdown to HTML for email content
     */
    private String convertMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"zh-CN\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>开发周报</title>\n");
        html.append("    <style>\n");
        html.append(ReportTemplate.getBaseCSS());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append(markdownToHTML(markdown));
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    /**
     * Simple markdown to HTML converter
     */
    private String markdownToHTML(String markdown) {
        if (markdown == null) {
            return "";
        }

        String html = markdown
                .replaceAll("# (.*)", "<h1>$1</h1>")
                .replaceAll("### (.*)", "<h3>$1</h3>")
                .replaceAll("## (.*)", "<h2>$1</h2>")
                .replaceAll("- (.*)", "<li>$1</li>")
                .replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>")
                .replaceAll("\\*(.*?)\\*", "<em>$1</em>");

        // Wrap lists
        if (html.contains("<li>")) {
            html = "<ul>" + html + "</ul>";
        }

        // Convert line breaks
        html = html.replaceAll("\n", "<br>\n");

        return html;
    }

    /**
     * Display the dialog with debug information
     */
    public void display() {
        System.out.println("[Midas] ReportPreviewDialog.display() called, thread: " + Thread.currentThread().getName());
        System.out.println("[Midas] Dialog is modal: " + isModal());
        System.out.println("[Midas] Dialog size: " + getSize());
        System.out.println("[Midas] Dialog location: " + getLocation());
        System.out.println("[Midas] Parent frame: " + getParent());
        setVisible(true);
        System.out.println("[Midas] ReportPreviewDialog.setVisible(true) completed");
        System.out.println("[Midas] Dialog is visible: " + isVisible());
    }
}
