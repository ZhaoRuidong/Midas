package org.flymars.devtools.midas.ui.toolwindow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.flymars.devtools.midas.CommitReporterKeys;
import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.core.CommitStorage;
import org.flymars.devtools.midas.data.CommitInfo;
import org.flymars.devtools.midas.ui.component.DatePickerComponent;
import org.flymars.devtools.midas.data.DailyNote;
import org.flymars.devtools.midas.data.WeeklyReport;
import org.flymars.devtools.midas.gitlab.GitLabProjectService;
import org.flymars.devtools.midas.gitlab.model.GitLabProject;
import org.flymars.devtools.midas.report.WeeklyReportGenerator;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel for displaying commit history and daily notes in the tool window
 */
public class ReportPanel {
    private static final Logger LOG = Logger.getInstance(ReportPanel.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    private final Project project;
    private final ConfigManager configManager;
    private final WeeklyReportGenerator reportGenerator;
    private final GitLabProjectService gitlabProjectService;
    private final CommitStorage storage;

    private JPanel mainPanel;
    private Tree commitsTree;
    private DefaultMutableTreeNode root;
    private JButton refreshButton;
    private JButton generateReportButton;
    private JLabel statusLabel;
    private DatePickerComponent startDatePicker;
    private DatePickerComponent endDatePicker;
    private JButton resetDateButton;

    // Daily Notes UI components
    private JBList<String> weekDaysList;
    private DefaultListModel<String> weekDaysModel;
    private JTextArea noteEditor;
    private JButton saveNoteButton;
    private JBLabel noteStatusLabel;
    private LocalDate currentWeekStart;
    private List<DailyNote> currentWeekNotes = new ArrayList<>();

    // Message bus connection for listening to settings changes
    private final com.intellij.util.messages.MessageBusConnection messageBusConnection;

    public ReportPanel(Project project) {
        this.project = project;
        this.configManager = ConfigManager.getInstance(project);
        this.reportGenerator = new WeeklyReportGenerator(project);
        this.gitlabProjectService = GitLabProjectService.getInstance(project);
        this.storage = project.getService(CommitStorage.class);

        // Register listener for settings changes
        this.messageBusConnection = project.getMessageBus().connect();
        this.messageBusConnection.subscribe(CommitReporterKeys.SETTINGS_CHANGED_TOPIC, () -> refreshFromCache());

        // Trigger auto-load of projects in background if needed
        gitlabProjectService.ensureProjectsLoaded();

        createUI();
        loadInitialData();
    }

    private void createUI() {
        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Create tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();

        // Commits tab
        JPanel commitsPanel = createCommitsPanel();
        tabbedPane.addTab("Commits", commitsPanel);

        // Notes tab
        JPanel notesPanel = createNotesPanel();
        tabbedPane.addTab("Daily Notes", notesPanel);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Date range selection panel (includes action buttons)
        JPanel dateRangePanel = createDateRangePanel();
        mainPanel.add(dateRangePanel, BorderLayout.NORTH);
    }

    /**
     * Create the date range selection panel
     */
    private JPanel createDateRangePanel() {
        JPanel panel = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));

        LocalDate defaultStart = WeeklyReportGenerator.getDefaultReportWeekStart(LocalDate.now());
        LocalDate defaultEnd = WeeklyReportGenerator.getDefaultReportWeekEnd(LocalDate.now());

        // Start date
        panel.add(new JBLabel("From:"));
        startDatePicker = new DatePickerComponent();
        startDatePicker.setDate(defaultStart);
        panel.add(startDatePicker);

        // End date
        panel.add(new JBLabel("To:"));
        endDatePicker = new DatePickerComponent();
        endDatePicker.setDate(defaultEnd);
        panel.add(endDatePicker);

        // Reset button
        resetDateButton = new JButton("Reset");
        resetDateButton.setToolTipText("Reset to default report week");
        resetDateButton.addActionListener(e -> resetDateRange());
        panel.add(resetDateButton);

        // Action buttons
        refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshData());
        panel.add(refreshButton);

        generateReportButton = new JButton("Generate Report");
        generateReportButton.addActionListener(e -> generateReportPreview());
        panel.add(generateReportButton);

        return panel;
    }

    /**
     * A FlowLayout that wraps components to the next line when width is insufficient.
     */
    private static class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            return layoutSize(target, false);
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            Insets insets = target.getInsets();
            int width = target.getWidth();
            if (width == 0) {
                width = Integer.MAX_VALUE;
            }

            int x = insets.left;
            int y = insets.top;
            int rowHeight = 0;

            for (Component comp : target.getComponents()) {
                if (!comp.isVisible()) continue;
                Dimension d = preferred ? comp.getPreferredSize() : comp.getMinimumSize();
                if (x + d.width > width - insets.right && x > insets.left) {
                    x = insets.left;
                    y += rowHeight + getVgap();
                    rowHeight = 0;
                }
                x += d.width + getHgap();
                rowHeight = Math.max(rowHeight, d.height);
            }

            return new Dimension(width, y + rowHeight + insets.bottom);
        }
    }

    /**
     * Reset date range to default values
     */
    private void resetDateRange() {
        LocalDate defaultStart = WeeklyReportGenerator.getDefaultReportWeekStart(LocalDate.now());
        LocalDate defaultEnd = WeeklyReportGenerator.getDefaultReportWeekEnd(LocalDate.now());
        startDatePicker.setDate(defaultStart);
        endDatePicker.setDate(defaultEnd);
    }

    private LocalDate getSelectedStartDate() {
        LocalDate date = startDatePicker.getDate();
        return date != null ? date : LocalDate.now();
    }

    private LocalDate getSelectedEndDate() {
        LocalDate date = endDatePicker.getDate();
        return date != null ? date : LocalDate.now();
    }

    /**
     * Create the commits panel
     */
    private JPanel createCommitsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Top panel with status
        JPanel topPanel = new JPanel(new BorderLayout(10, 5));
        statusLabel = new JBLabel("Loading...");
        statusLabel.setFont(new Font(statusLabel.getFont().getName(), Font.PLAIN, 11));
        topPanel.add(statusLabel, BorderLayout.WEST);

        panel.add(topPanel, BorderLayout.NORTH);

        // Commit tree grouped by project
        root = new DefaultMutableTreeNode("Commits");
        commitsTree = new Tree(root);
        commitsTree.setRootVisible(false);
        commitsTree.setShowsRootHandles(true);
        commitsTree.setCellRenderer(new CommitTreeCellRenderer());

        // Add speed search
        new TreeSpeedSearch(commitsTree, path -> {
            Object node = path.getLastPathComponent();
            if (node instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) node).getUserObject();
                if (userObj instanceof CommitNode) {
                    CommitNode commitNode = (CommitNode) userObj;
                    return commitNode.commit.getMessage() + " " + commitNode.commit.getAuthor();
                } else if (userObj instanceof String) {
                    return (String) userObj;
                }
            }
            return "";
        });

        JScrollPane scrollPane = new JScrollPane(commitsTree);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Create the daily notes panel with IDEA native style
     */
    private JPanel createNotesPanel() {
        BorderLayoutPanel panel = JBUI.Panels.simplePanel();
        panel.setOpaque(false);

        // Initialize current week
        currentWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // Create week days model
        weekDaysModel = new DefaultListModel<>();
        updateWeekDaysList();

        // Create list with IDEA style
        weekDaysList = new JBList<>(weekDaysModel);
        weekDaysList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        weekDaysList.setCellRenderer(new WeekDayCellRenderer());

        // Create toolbar decorator for the list
        ToolbarDecorator listDecorator = ToolbarDecorator.createDecorator(weekDaysList)
                .setPreferredSize(new Dimension(150, 0))
                .disableAddAction()
                .disableRemoveAction()
                .disableUpDownActions();

        JPanel listPanel = listDecorator.createPanel();

        // Create note editor
        noteEditor = new JTextArea();
        noteEditor.setLineWrap(true);
        noteEditor.setWrapStyleWord(true);
        noteEditor.setTabSize(4);
        noteEditor.setFont(new Font("Monospaced", Font.PLAIN, 13));
        noteEditor.setOpaque(true);
        noteEditor.setBackground(UIUtil.getPanelBackground());

        JScrollPane noteEditorScrollPane = new JBScrollPane(noteEditor);
        noteEditorScrollPane.setBorder(JBUI.Borders.empty(4));

        // Create toolbar (transparent)
        JComponent toolbar = createEditorToolbar();

        // Create right panel using BorderLayout
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setOpaque(false);
        rightPanel.setBackground(null);
        rightPanel.add(toolbar, BorderLayout.NORTH);
        rightPanel.add(noteEditorScrollPane, BorderLayout.CENTER);

        // Create splitter
        JBSplitter splitter = new JBSplitter(false, 0.25f);
        splitter.setOpaque(false);
        splitter.setFirstComponent(listPanel);
        splitter.setSecondComponent(rightPanel);

        panel.addToCenter(splitter);

        // Add list selection listener
        weekDaysList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int selectedIndex = weekDaysList.getSelectedIndex();
                    if (selectedIndex >= 0) {
                        loadNoteForDay(selectedIndex);
                    }
                }
            }
        });

        return panel;
    }

    /**
     * Create editor toolbar with status label and save button
     */
    private JComponent createEditorToolbar() {
        // Use JPanel with BorderLayout instead of Box
        // This gives us better control over background
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBorder(JBUI.Borders.empty(5, 10));

        noteStatusLabel = new JBLabel("Select a day to view/edit note");
        noteStatusLabel.setOpaque(false);

        saveNoteButton = new JButton("Save");
        saveNoteButton.setEnabled(false);
        saveNoteButton.addActionListener(e -> saveCurrentNote());

        toolbar.add(noteStatusLabel, BorderLayout.WEST);
        toolbar.add(saveNoteButton, BorderLayout.EAST);

        return toolbar;
    }

    /**
     * Update the week days list with dates
     */
    private void updateWeekDaysList() {
        weekDaysModel.clear();

        String[] dayNames = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

        for (int i = 0; i < 7; i++) {
            LocalDate dayDate = currentWeekStart.plusDays(i);
            String dateStr = dayDate.format(DAY_DATE_FORMATTER);
            boolean isToday = dayDate.equals(LocalDate.now());
            String prefix = isToday ? "• " : "  ";
            weekDaysModel.addElement(prefix + dayNames[i] + " (" + dateStr + ")");
        }
    }

    /**
     * Load note for the selected day
     */
    private void loadNoteForDay(int dayIndex) {
        LocalDate selectedDate = currentWeekStart.plusDays(dayIndex);
        DailyNote note = storage.getNote(selectedDate);

        String content = (note != null && note.getContent() != null) ? note.getContent() : "";

        // Set text in editor
        noteEditor.setText(content);

        if (note != null && note.getContent() != null && !note.getContent().isEmpty()) {
            noteStatusLabel.setText(selectedDate + " - Loaded");
        } else {
            noteStatusLabel.setText(selectedDate + " - No note yet");
        }

        saveNoteButton.setEnabled(true);
    }

    /**
     * Save the current note
     */
    private void saveCurrentNote() {
        int selectedIndex = weekDaysList.getSelectedIndex();
        if (selectedIndex < 0) return;

        LocalDate selectedDate = currentWeekStart.plusDays(selectedIndex);
        String content = noteEditor.getText().trim();

        DailyNote note = new DailyNote(selectedDate, content);
        ApplicationManager.getApplication().runWriteAction(() -> storage.saveNote(note));

        noteStatusLabel.setText(selectedDate + " - Saved!");
        LOG.info("Saved note for " + selectedDate);
    }

    /**
     * Cell renderer for week days list with IDEA native style
     */
    private static class WeekDayCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (c instanceof JLabel) {
                JLabel label = (JLabel) c;
                String text = label.getText();

                // Apply IDEA-style border and padding
                label.setBorder(JBUI.Borders.empty(4, 8));

                // Highlight today with IDEA color scheme
                if (text.startsWith("• ")) {
                    label.setFont(new Font(label.getFont().getName(), Font.BOLD, 13));
                    if (!isSelected) {
                        label.setForeground(new Color(0, 102, 204));
                    }
                } else {
                    label.setFont(new Font(label.getFont().getName(), Font.PLAIN, 13));
                }
            }

            return c;
        }
    }

    private void loadInitialData() {
        refreshData();
    }

    /**
     * Public method to trigger refresh from cache (called from settings)
     */
    public void refreshFromCache() {
        refreshData();
    }

    private void refreshData() {
        refreshButton.setEnabled(false);
        gitlabProjectService.clearCommitCache();
        statusLabel.setText("Loading commits...");

        LocalDate weekStart = getSelectedStartDate();
        LocalDate weekEnd = getSelectedEndDate();

        SwingWorker<List<CommitInfo>, Void> worker = new SwingWorker<>() {
            private boolean noProjectsSelected = false;

            @Override
            protected List<CommitInfo> doInBackground() {
                try {
                    List<GitLabProject> selectedProjects = gitlabProjectService.getSelectedProjects();

                    LOG.info("refreshData() - Selected projects count: " + selectedProjects.size());

                    if (selectedProjects.isEmpty()) {
                        noProjectsSelected = true;
                        return List.of();
                    }

                    return gitlabProjectService
                            .getMyCommitsForWeek(weekStart, weekEnd, selectedProjects)
                            .get();

                } catch (Exception e) {
                    LOG.error("Error loading commits", e);
                    return List.of();
                }
            }

            @Override
            protected void done() {
                try {
                    List<CommitInfo> commits = get();
                    updateCommitsTree(commits);

                    if (noProjectsSelected) {
                        statusLabel.setText("⚠️ No projects selected. Go to Settings → Midas → GitLab to select projects.");
                        statusLabel.setForeground(Color.ORANGE);
                    } else if (commits.isEmpty()) {
                        statusLabel.setText("No commits found for report week " + weekStart + " to " + weekEnd);
                        statusLabel.setForeground(new Color(150, 150, 0));
                    } else {
                        statusLabel.setText("Showing " + commits.size() + " of your commits for report week " + weekStart + " to " + weekEnd);
                        statusLabel.setForeground(new Color(0, 150, 0));
                    }
                } catch (Exception e) {
                    LOG.error("Error updating commits tree", e);
                    statusLabel.setText("Error loading commits: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                } finally {
                    refreshButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    /**
     * Update tree with commits grouped by project
     */
    private void updateCommitsTree(List<CommitInfo> commits) {
        root.removeAllChildren();

        if (commits.isEmpty()) {
            DefaultMutableTreeNode emptyNode = new DefaultMutableTreeNode("No commits found");
            root.add(emptyNode);
        } else {
            // Group commits by project
            java.util.Map<String, List<CommitInfo>> groupedByProject = new java.util.LinkedHashMap<>();
            for (CommitInfo commit : commits) {
                String projectName = commit.getGitlabProjectName();
                if (projectName == null) projectName = "Unknown Project";

                groupedByProject
                    .computeIfAbsent(projectName, k -> new java.util.ArrayList<>())
                    .add(commit);
            }

            // Create tree structure: Project -> Commits
            for (java.util.Map.Entry<String, List<CommitInfo>> entry : groupedByProject.entrySet()) {
                DefaultMutableTreeNode projectNode = new DefaultMutableTreeNode(
                    new ProjectNode(entry.getKey(), entry.getValue().size())
                );

                for (CommitInfo commit : entry.getValue()) {
                    projectNode.add(new DefaultMutableTreeNode(new CommitNode(commit)));
                }

                root.add(projectNode);
            }
        }

        ((DefaultTreeModel) commitsTree.getModel()).nodeStructureChanged(root);

        // Expand all nodes
        for (int i = 0; i < commitsTree.getRowCount(); i++) {
            commitsTree.expandRow(i);
        }
    }

    private void generateReportPreview() {
        System.out.println("=".repeat(80));
        System.out.println("[Midas] ========== GENERATE REPORT BUTTON CLICKED ==========");
        System.out.println("[Midas] Current thread: " + Thread.currentThread().getName());
        System.out.println("[Midas] If you see this message, Console logging is working!");
        System.out.println("=".repeat(80));

        List<GitLabProject> selectedProjects = gitlabProjectService.getSelectedProjects();

        if (selectedProjects.isEmpty()) {
            Messages.showWarningDialog(
                    "Please select at least one project first.\n\n" +
                            "Go to Settings -> Midas -> GitLab tab to select projects.",
                    "No Projects Selected"
            );
            return;
        }

        LocalDate weekStart = getSelectedStartDate();
        LocalDate weekEnd = getSelectedEndDate();

        if (weekStart.isAfter(weekEnd)) {
            Messages.showWarningDialog(
                    "Start date cannot be after end date.",
                    "Invalid Date Range"
            );
            return;
        }

        generateReportButton.setEnabled(false);
        generateReportButton.setText("Generating...");

        SwingWorker<WeeklyReport, Void> worker = new SwingWorker<>() {
            @Override
            protected WeeklyReport doInBackground() throws Exception {
                System.out.println("[Midas] ========== BACKGROUND WORK STARTED ==========");
                System.out.println("[Midas] Starting report generation in background thread...");
                try {
                    WeeklyReport report = reportGenerator.generateReport(weekStart, weekEnd);
                    System.out.println("[Midas] Report generation completed in background thread");
                    System.out.println("[Midas] Report has " + report.getCommits().size() + " commits");
                    return report;
                } catch (Exception e) {
                    System.err.println("[Midas] Exception in doInBackground: " + e.getClass().getName());
                    System.err.println("[Midas] Error message: " + e.getMessage());
                    e.printStackTrace();
                    throw e;
                }
            }

            @Override
            protected void done() {
                System.out.println("[Midas] ========== DONE METHOD CALLED ==========");
                try {
                    System.out.println("[Midas] Getting result from Future...");
                    WeeklyReport report = get();
                    System.out.println("[Midas] Result retrieved successfully");

                    System.out.println("[Midas] Creating preview dialog...");
                    ReportPreviewDialog dialog = new ReportPreviewDialog(
                            project,
                            report,
                            configManager,
                            gitlabProjectService
                    );
                    System.out.println("[Midas] Preview dialog object created");

                    System.out.println("[Midas] Scheduling dialog display with invokeLater...");
                    SwingUtilities.invokeLater(() -> {
                        System.out.println("[Midas] invokeLater callback executing, showing dialog...");
                        dialog.setVisible(true);
                        System.out.println("[Midas] dialog.setVisible(true) completed");
                    });

                    System.out.println("[Midas] Dialog display scheduled");
                    System.out.println("[Midas] ========== PROCESS COMPLETE ==========");

                } catch (Exception e) {
                    System.err.println("[Midas] ========== ERROR IN DONE METHOD ==========");
                    System.err.println("[Midas] Error generating report: " + e.getMessage());
                    System.err.println("[Midas] Exception type: " + e.getClass().getName());
                    e.printStackTrace();
                    LOG.error("Error generating report", e);
                    Messages.showErrorDialog(
                            "Error generating report: " + e.getMessage() + "\n\nPlease check the IDE log (Help → Show Log in Explorer) for details.",
                            "Error"
                    );
                } finally {
                    generateReportButton.setEnabled(true);
                    generateReportButton.setText("📝 Generate Weekly Report");
                    System.out.println("[Midas] Button state restored");
                }
            }
        };
        System.out.println("[Midas] Starting SwingWorker...");
        worker.execute();
        System.out.println("[Midas] SwingWorker.execute() called, returning control to EDT");
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    /**
     * Custom cell renderer for the commit tree
     */
    private static class CommitTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObject = node.getUserObject();

                setFont(new Font("Dialog", Font.PLAIN, 12));
                setOpaque(true);

                if (userObject instanceof ProjectNode) {
                    ProjectNode projectNode = (ProjectNode) userObject;
                    setText(projectNode.name + " (" + projectNode.count + " commits)");
                    setFont(new Font("Dialog", Font.BOLD, 12));
                    setIcon(getProjectIcon());
                } else if (userObject instanceof CommitNode) {
                    CommitNode commitNode = (CommitNode) userObject;
                    CommitInfo commit = commitNode.commit;

                    String shortHash = commit.getHash() != null && commit.getHash().length() > 8
                            ? commit.getHash().substring(0, 8)
                            : commit.getHash();

                    setText(String.format("%s %s - %s",
                            shortHash,
                            commit.getTimestamp() != null ? commit.getTimestamp().format(DATE_FORMATTER) : "",
                            commit.getMessage()));

                    setIcon(getCommitIcon());
                } else if (userObject instanceof String) {
                    setText((String) userObject);
                    setIcon(null);
                }
            }

            return this;
        }

        private Icon getProjectIcon() {
            return new Icon() {
                @Override
                public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
                    java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new java.awt.Color(100, 149, 237));
                    g2.fillRect(x + 2, y + 2, 12, 10);
                    g2.dispose();
                }

                @Override
                public int getIconWidth() {
                    return 16;
                }

                @Override
                public int getIconHeight() {
                    return 16;
                }
            };
        }

        private Icon getCommitIcon() {
            return new Icon() {
                @Override
                public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
                    java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new java.awt.Color(100, 100, 100));
                    g2.drawLine(x + 2, y + 4, x + 6, y + 10);
                    g2.drawLine(x + 6, y + 4, x + 10, y + 10);
                    g2.drawLine(x + 3, y + 7, x + 9, y + 7);
                    g2.dispose();
                }

                @Override
                public int getIconWidth() {
                    return 14;
                }

                @Override
                public int getIconHeight() {
                    return 14;
                }
            };
        }
    }

    /**
     * Wrapper for project node data
     */
    private static class ProjectNode {
        final String name;
        final int count;

        ProjectNode(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    /**
     * Wrapper for commit node data
     */
    private static class CommitNode {
        final CommitInfo commit;

        CommitNode(CommitInfo commit) {
            this.commit = commit;
        }
    }
}
