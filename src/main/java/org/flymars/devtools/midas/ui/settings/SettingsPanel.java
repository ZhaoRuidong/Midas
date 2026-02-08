package org.flymars.devtools.midas.ui.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.config.PluginConfig;
import org.flymars.devtools.midas.gitlab.GitLabInstanceService;
import org.flymars.devtools.midas.gitlab.GitLabProjectService;
import org.flymars.devtools.midas.gitlab.model.GitLabInstance;
import org.flymars.devtools.midas.gitlab.model.GitLabProject;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Settings panel for plugin configuration
 */
public class SettingsPanel {
    private static final Logger LOG = Logger.getInstance(SettingsPanel.class);
    private final Project project;
    private final GitLabInstanceService instanceService;
    private final GitLabProjectService projectService;
    private JPanel mainPanel;
    private JTabbedPane tabbedPane;

    // General settings
    private JBCheckBox enabledCheckBox;
    private JBTextField ignoredBranchesField;
    private JComboBox<ReportLanguageItem> reportLanguageCombo;

    // GitLab settings - Instance management
    private JPanel instancesPanel;
    private JBLabel titleLabel;  // Title label for instances panel
    private List<CollapsibleInstancePanel> instanceEditorPanels = new ArrayList<>();

    // GitLab settings - Project management
    private CheckboxTree projectsTree;
    private CheckedTreeNode projectsRoot;
    private SearchTextField searchField;
    private JBLabel selectedProjectsLabel;

    // Dynamic height tracking for GitLab panel scroll areas
    private JScrollPane instancesScroll;
    private JScrollPane projectsScroll;
    private static final int BUTTON_HEIGHT = 50;      // 按钮区域高度
    private static final int TITLE_HEIGHT = 35;       // 标题行高度
    private static final int COUNT_HEIGHT = 30;        // 选择计数行高度
    private static final int MIN_SCROLL_HEIGHT = 150;  // 最小滚动区域高度
    private static final int BORDER_HEIGHT = 30;       // 边框等预留高度

    // AI settings
    private JComboBox<AIProviderItem> aiProviderCombo;
    private JBPasswordField apiKeyField;
    private JBTextField modelField;
    private JBTextField endpointField;
    private JButton testAiButton;

    // Email settings
    private JBTextField smtpHostField;
    private JBTextField smtpPortField;
    private JBTextField smtpUsernameField;
    private JBPasswordField smtpPasswordField;
    private JBTextField fromEmailField;
    private JBTextField toEmailsField;
    private JBTextField ccEmailsField;
    private JBTextField emailSubjectFormatField;
    private JComboBox<ReminderScheduleItem> reminderScheduleCombo;
    private JBTextField reminderTimeField;
    private JButton testEmailButton;

    public SettingsPanel(Project project) {
        this.project = project;
        this.instanceService = GitLabInstanceService.getInstance(project);
        this.projectService = GitLabProjectService.getInstance(project);

        // Trigger auto-load of projects in background if needed
        this.projectService.ensureProjectsLoaded();

        createUI();
    }

    private void createUI() {
        mainPanel = new JPanel(new BorderLayout());
        tabbedPane = new JTabbedPane();

        tabbedPane.addTab("General", createGeneralPanel());
        tabbedPane.addTab("GitLab", createGitLabPanel());
        tabbedPane.addTab("AI Configuration", createAIPanel());
        tabbedPane.addTab("Email Configuration", createEmailPanel());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createGeneralPanel() {
        enabledCheckBox = new JBCheckBox("Enable commit tracking");
        ignoredBranchesField = new JBTextField(30);
        ignoredBranchesField.setToolTipText("Comma-separated list of branches to ignore (e.g., develop, staging)");

        // Report language selection
        reportLanguageCombo = new JComboBox<>();
        reportLanguageCombo.addItem(new ReportLanguageItem(PluginConfig.ReportLanguage.CHINESE));
        reportLanguageCombo.addItem(new ReportLanguageItem(PluginConfig.ReportLanguage.ENGLISH));
        reportLanguageCombo.setToolTipText("Select the language for generated weekly reports");

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        JPanel settingsPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Enabled:"), enabledCheckBox)
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("Ignored Branches:"), ignoredBranchesField)
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("Report Language:"), reportLanguageCombo)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        mainPanel.add(settingsPanel);

        return mainPanel;
    }

    private JPanel createGitLabPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // Split pane: instances on left, projects on right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        // Set narrow divider for modern look (1px instead of default)
        splitPane.setDividerSize(1);
        splitPane.setResizeWeight(0.5);

        // Remove border from split pane
        splitPane.setBorder(null);

        // Left panel - Instances
        JPanel instancesContainer = new JPanel(new BorderLayout());
        instancesContainer.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Title panel with "GitLab Instances" label and "Add Instance" button
        JPanel titlePanel = new JPanel(new BorderLayout());
        titleLabel = new JBLabel("GitLab Instances");
        titleLabel.setFont(new Font(titleLabel.getFont().getName(), Font.BOLD, 13));

        JButton addInstanceButton = new JButton("➕ Add");
        addInstanceButton.addActionListener(e -> addNewInstance());

        titlePanel.add(titleLabel, BorderLayout.WEST);
        titlePanel.add(addInstanceButton, BorderLayout.EAST);

        instancesPanel = new JPanel();
        instancesPanel.setLayout(new BoxLayout(instancesPanel, BoxLayout.Y_AXIS));

        // Create scroll panel (will be sized dynamically)
        instancesScroll = new JScrollPane(instancesPanel);
        instancesScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        instancesScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        instancesContainer.add(titlePanel, BorderLayout.NORTH);
        instancesContainer.add(instancesScroll, BorderLayout.CENTER);

        // Right panel - Projects
        JPanel projectsContainer = new JPanel(new BorderLayout());
        projectsContainer.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Title and search panel (top row)
        JPanel topRowPanel = new JPanel(new BorderLayout(10, 5));
        JBLabel projectsTitleLabel = new JBLabel("Projects for Weekly Report");
        projectsTitleLabel.setFont(new Font(projectsTitleLabel.getFont().getName(), Font.BOLD, 13));
        topRowPanel.add(projectsTitleLabel, BorderLayout.WEST);

        // Search field (right aligned)
        searchField = new SearchTextField(true);
        searchField.setToolTipText("Search projects by name or description");
        searchField.setPreferredSize(new Dimension(200, 28));
        topRowPanel.add(searchField, BorderLayout.EAST);

        // Selection count panel (second row)
        JPanel countPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        selectedProjectsLabel = new JBLabel("Selected: 0 projects");
        selectedProjectsLabel.setFont(new Font(selectedProjectsLabel.getFont().getName(), Font.BOLD, 12));
        countPanel.add(selectedProjectsLabel);

        // Create project tree with IntelliJ native CheckboxTree
        projectsRoot = new CheckedTreeNode("Projects");

        // Create custom renderer for displaying instance and project information
        CheckboxTree.CheckboxTreeCellRenderer renderer = new CheckboxTree.CheckboxTreeCellRenderer() {
            @Override
            public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus);

                if (value instanceof CheckedTreeNode node) {
                    Object userObject = node.getUserObject();

                    getTextRenderer().clear();

                    if (userObject instanceof GitLabInstance instance) {
                        // Instance node: show name in bold
                        getTextRenderer().append(instance.getName(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, null));
                    } else if (userObject instanceof GitLabProject proj) {
                        // Project node: show name with optional description
                        getTextRenderer().append(proj.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                        if (proj.getDescription() != null && !proj.getDescription().isEmpty()) {
                            String desc = proj.getDescription();
                            if (desc.length() > 50) desc = desc.substring(0, 50) + "...";
                            getTextRenderer().append(" - " + desc, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                        }
                    } else if (userObject instanceof String) {
                        // Hint node
                        getTextRenderer().append((String) userObject, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                    }
                }
            }
        };

        projectsTree = new CheckboxTree(renderer, projectsRoot) {
            @Override
            protected void onNodeStateChanged(CheckedTreeNode node) {
                super.onNodeStateChanged(node);
                Object userObject = node.getUserObject();

                // Update project selection state
                if (userObject instanceof GitLabProject proj) {
                    proj.setSelected(node.isChecked());
                    updateSelectionCount();
                    // Auto-save on selection change
                    saveSelectedProjects();
                }

                // Handle parent node state change (select/deselect all children)
                if (userObject instanceof GitLabInstance) {
                    for (int i = 0; i < node.getChildCount(); i++) {
                        if (node.getChildAt(i) instanceof CheckedTreeNode child) {
                            Object childObj = child.getUserObject();
                            if (childObj instanceof GitLabProject proj) {
                                child.setChecked(node.isChecked());
                                proj.setSelected(node.isChecked());
                            }
                        }
                    }
                    updateSelectionCount();
                    // Auto-save on selection change
                    saveSelectedProjects();
                }
            }
        };

        projectsTree.setRootVisible(false);
        projectsTree.setShowsRootHandles(true);

        // Create TreeSpeedSearch for project tree
        TreeSpeedSearch speedSearch = new TreeSpeedSearch(projectsTree, path -> {
            Object lastComponent = path.getLastPathComponent();
            if (lastComponent instanceof CheckedTreeNode node) {
                Object userObject = node.getUserObject();
                if (userObject instanceof GitLabInstance instance) {
                    return instance.getName();
                } else if (userObject instanceof GitLabProject proj) {
                    return proj.getDisplayName() + " " + (proj.getDescription() != null ? proj.getDescription() : "");
                } else if (userObject instanceof String) {
                    return (String) userObject;
                }
            }
            return "";
        });

        // Link SearchTextField with TreeSpeedSearch
        searchField.addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateSearch();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateSearch();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateSearch();
            }

            private void updateSearch() {
                String text = searchField.getText();
                if (!text.isEmpty()) {
                    speedSearch.findAndSelectElement(text);
                }
            }
        });

        // Create scroll pane (will be sized dynamically)
        projectsScroll = new JScrollPane(projectsTree);
        projectsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        projectsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Combine top panels
        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.add(topRowPanel, BorderLayout.NORTH);
        topContainer.add(countPanel, BorderLayout.CENTER);

        projectsContainer.add(topContainer, BorderLayout.NORTH);
        projectsContainer.add(projectsScroll, BorderLayout.CENTER);

        splitPane.setLeftComponent(instancesContainer);
        splitPane.setRightComponent(projectsContainer);
        splitPane.setDividerLocation(400);
        splitPane.setPreferredSize(new Dimension(800, 310));

        mainPanel.add(splitPane);

        // Add component listener for dynamic height calculation
        mainPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                updateScrollHeights();
            }
        });

        // Initial height calculation
        SwingUtilities.invokeLater(() -> updateScrollHeights());

        // Load initial data
        loadInstances();
        loadProjectsTree();

        return mainPanel;
    }

    private void loadInstances() {
        instancesPanel.removeAll();
        instanceEditorPanels.clear();

        List<GitLabInstance> instances = instanceService.getInstances();
        for (GitLabInstance instance : instances) {
            CollapsibleInstancePanel panel = new CollapsibleInstancePanel(instance);
            instanceEditorPanels.add(panel);
            instancesPanel.add(panel);
        }

        if (instances.isEmpty()) {
            JLabel emptyLabel = new JLabel("No GitLab instances configured.");
            emptyLabel.setFont(new Font(emptyLabel.getFont().getName(), Font.ITALIC, 12));
            instancesPanel.add(emptyLabel);
        }

        instancesPanel.revalidate();
        instancesPanel.repaint();
    }

    private void addNewInstance() {
        // Collapse all existing panels
        for (Component comp : instancesPanel.getComponents()) {
            if (comp instanceof CollapsibleInstancePanel) {
                ((CollapsibleInstancePanel) comp).collapse();
            }
        }

        GitLabInstance newInstance = GitLabInstance.builder()
                .id(UUID.randomUUID().toString())
                .name("New GitLab Instance")
                .serverUrl("https://gitlab.com")
                .accessToken("")
                .isActive(false)
                .build();

        CollapsibleInstancePanel panel = new CollapsibleInstancePanel(newInstance);
        panel.expand(); // Auto-expand new instance

        instanceEditorPanels.add(panel);
        instancesPanel.add(panel);

        // Remove empty label if present
        for (Component comp : instancesPanel.getComponents()) {
            if (comp instanceof JLabel label && label.getText().startsWith("No GitLab")) {
                instancesPanel.remove(label);
                break;
            }
        }

        instancesPanel.revalidate();
        instancesPanel.repaint();

        // Scroll to bottom to show new instance
        SwingUtilities.invokeLater(() -> {
            panel.scrollIntoView();
        });
    }

    private void removeInstance(CollapsibleInstancePanel panel) {
        instanceEditorPanels.remove(panel);
        instancesPanel.remove(panel);
        instancesPanel.revalidate();
        instancesPanel.repaint();
    }

    private void loadProjectsTree() {
        projectsRoot.removeAllChildren();

        List<GitLabInstance> instances = instanceService.getInstances();
        LOG.info("Loading projects tree with " + instances.size() + " instances");

        // Only show active instance's projects
        GitLabInstance activeInstance = instances.stream()
                .filter(GitLabInstance::isActive)
                .findFirst()
                .orElse(null);

        if (activeInstance != null) {
            CheckedTreeNode instanceNode = new CheckedTreeNode(activeInstance);
            List<GitLabProject> projects = projectService.getProjectsForInstance(activeInstance.getId());

            LOG.info("Active instance: " + activeInstance.getName() + " has " + projects.size() + " projects in cache");

            for (GitLabProject project : projects) {
                CheckedTreeNode projectNode = new CheckedTreeNode(project);
                projectNode.setChecked(project.isSelected());
                instanceNode.add(projectNode);
                LOG.info("  - Project: " + project.getDisplayName() + " (selected=" + project.isSelected() + ")");
            }

            // Check if all children are selected to set parent state
            boolean allSelected = projects.stream().allMatch(GitLabProject::isSelected);
            instanceNode.setChecked(allSelected && !projects.isEmpty());

            projectsRoot.add(instanceNode);

            // Show hint if no projects
            if (projects.isEmpty()) {
                CheckedTreeNode hintNode = new CheckedTreeNode(
                        "No projects found. Click '🔄 Refresh Projects' to fetch projects.");
                hintNode.setEnabled(false);
                projectsRoot.add(hintNode);
            }
        } else {
            LOG.info("No active instance found");
            // Show hint when no active instance
            CheckedTreeNode hintNode = new CheckedTreeNode(
                    "No active GitLab instance. Please add an instance and set it as active.");
            hintNode.setEnabled(false);
            projectsRoot.add(hintNode);
        }

        LOG.info("Total root nodes: " + projectsRoot.getChildCount());
        ((javax.swing.tree.DefaultTreeModel)projectsTree.getModel()).reload();
        // Expand first level
        for (int i = 0; i < projectsTree.getRowCount(); i++) {
            projectsTree.expandRow(i);
        }
        updateSelectionCount();
    }

    private void saveInstancesTemporarily() {
        LOG.info("Saving instances temporarily to instanceService");
        for (CollapsibleInstancePanel panel : instanceEditorPanels) {
            GitLabInstance instance = panel.getInstance();
            LOG.info("Panel instance: " + instance.getName() + " (id=" + instance.getId() + ", valid=" + instance.isValid() + ")");
            GitLabInstance existing = instanceService.getInstance(instance.getId());
            if (existing != null) {
                instanceService.updateInstance(instance);
                LOG.info("  Updated existing instance");
            } else {
                instanceService.addInstance(instance);
                LOG.info("  Added new instance");
            }
        }
        LOG.info("Total instances in service after save: " + instanceService.getInstances().size());
    }

    private JPanel createAIPanel() {
        aiProviderCombo = new JComboBox<>(new AIProviderItem[]{
                new AIProviderItem(PluginConfig.AIProvider.OPENAI, "OpenAI"),
                new AIProviderItem(PluginConfig.AIProvider.CLAUDE, "Claude"),
                new AIProviderItem(PluginConfig.AIProvider.ZHIPU, "Zhipu AI (智谱)"),
                new AIProviderItem(PluginConfig.AIProvider.CUSTOM, "Custom")
        });

        // Add listener to update default values when provider changes
        aiProviderCombo.addActionListener(e -> {
            AIProviderItem selected = (AIProviderItem) aiProviderCombo.getSelectedItem();
            if (selected != null) {
                updateAIProviderDefaults(selected.getProvider());
            }
        });

        apiKeyField = new JBPasswordField();
        apiKeyField.setColumns(40);
        apiKeyField.setToolTipText("API key for the selected provider");

        modelField = new JBTextField(20);
        modelField.setToolTipText("Model name (e.g., gpt-4, claude-3-opus, glm-4-plus)");

        endpointField = new JBTextField(40);
        endpointField.setToolTipText("API endpoint URL");

        testAiButton = new JButton("Test Connection");
        testAiButton.addActionListener(e -> testAIConnection());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(testAiButton);

        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Provider:"), aiProviderCombo)
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("API Key:"), apiKeyField)
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("Model:"), modelField)
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("Endpoint:"), endpointField)
                .addVerticalGap(10)
                .addComponent(buttonPanel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    private JPanel createEmailPanel() {
        smtpHostField = new JBTextField(40);
        smtpPortField = new JBTextField(10);
        smtpPortField.setText("465");
        smtpPortField.setToolTipText("SMTP port (465 for SSL, 587 for STARTTLS)");
        smtpUsernameField = new JBTextField(40);
        smtpPasswordField = new JBPasswordField();
        smtpPasswordField.setColumns(40);

        fromEmailField = new JBTextField(40);
        fromEmailField.setToolTipText("Sender email address");

        toEmailsField = new JBTextField(40);
        toEmailsField.setToolTipText("Comma-separated list of recipient emails (e.g., user1@example.com, user2@example.com)");

        ccEmailsField = new JBTextField(40);
        ccEmailsField.setToolTipText("Comma-separated list of CC recipients (optional)");

        emailSubjectFormatField = new JBTextField(40);
        emailSubjectFormatField.setText("${user}的工作周报(${weekStart} - ${weekEnd})");
        emailSubjectFormatField.setToolTipText("Variables: ${user}, ${weekStart}, ${weekEnd}, ${year}, ${month}, ${day}");

        reminderScheduleCombo = new JComboBox<>(new ReminderScheduleItem[]{
                new ReminderScheduleItem(PluginConfig.ReminderSchedule.DISABLED, "Disabled"),
                new ReminderScheduleItem(PluginConfig.ReminderSchedule.WEEKLY, "Weekly Reminder"),
                new ReminderScheduleItem(PluginConfig.ReminderSchedule.CUSTOM, "Custom")
        });

        reminderTimeField = new JBTextField(10);
        reminderTimeField.setText("09:00");
        reminderTimeField.setToolTipText("Format: HH:mm (e.g., 09:00 for 9:00 AM)");

        testEmailButton = new JButton("Test Email");
        testEmailButton.addActionListener(e -> testEmailConnection());

        // Create vertical layout with groups
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Group 1: SMTP Server Configuration
        JPanel smtpGroup = createGroupPanel("SMTP Server Configuration");
        JPanel smtpFields = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Host:"), smtpHostField)
                .addVerticalGap(5)
                .addLabeledComponent(new JBLabel("Port:"), smtpPortField)
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("Username:"), smtpUsernameField)
                .addVerticalGap(5)
                .addLabeledComponent(new JBLabel("Password:"), smtpPasswordField)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        smtpGroup.add(smtpFields);
        mainPanel.add(smtpGroup);
        mainPanel.add(Box.createVerticalStrut(10));

        // Group 2: Email Addresses
        JPanel addressGroup = createGroupPanel("Email Addresses");
        JPanel addressFields = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("From:"), fromEmailField)
                .addVerticalGap(10)
                .addLabeledComponent(new JBLabel("To:"), toEmailsField)
                .addVerticalGap(5)
                .addLabeledComponent(new JBLabel("CC:"), ccEmailsField)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        addressGroup.add(addressFields);
        mainPanel.add(addressGroup);
        mainPanel.add(Box.createVerticalStrut(10));

        // Group 3: Email Content
        JPanel contentGroup = createGroupPanel("Email Content");
        JPanel contentFields = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Subject Format:"), emailSubjectFormatField)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        contentGroup.add(contentFields);
        mainPanel.add(contentGroup);
        mainPanel.add(Box.createVerticalStrut(10));

        // Group 4: Reminder Settings
        JPanel reminderGroup = createGroupPanel("Reminder Settings");
        JPanel reminderFields = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Schedule:"), reminderScheduleCombo)
                .addVerticalGap(5)
                .addLabeledComponent(new JBLabel("Time:"), reminderTimeField)
                .addVerticalGap(10)
                .addComponent(testEmailButton)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reminderGroup.add(reminderFields);
        mainPanel.add(reminderGroup);

        return mainPanel;
    }

    /**
     * Create a group panel with titled border
     */
    private JPanel createGroupPanel(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 5, 5, 5),
                BorderFactory.createTitledBorder(title)
        ));
        return panel;
    }

    /**
     * Check if settings are modified
     */
    public boolean isModified(ConfigManager config) {
        if (!enabledCheckBox.isSelected() == config.isEnabled()) return true;
        if (!getIgnoredBranches().equals(config.getIgnoredBranches())) return true;

        AIProviderItem selectedAI = (AIProviderItem) aiProviderCombo.getSelectedItem();
        if (selectedAI != null && selectedAI.getProvider() != config.getAIProvider()) return true;

        String currentApiKey = apiKeyField.getPassword() != null ? new String(apiKeyField.getPassword()) : "";
        String storedApiKey = config.getApiKey() != null ? config.getApiKey() : "";

        LOG.info("isModified API Key check - Current length: " + currentApiKey.length() +
                   ", Stored length: " + storedApiKey.length() +
                   ", Current first 4: " + (currentApiKey.length() > 4 ? currentApiKey.substring(0, 4) : "empty") +
                   ", Stored first 4: " + (storedApiKey.length() > 4 ? storedApiKey.substring(0, 4) : "empty"));

        if (!currentApiKey.equals(storedApiKey)) return true;
        if (!modelField.getText().equals(config.getModel())) return true;
        if (!endpointField.getText().equals(config.getEndpoint())) return true;

        // Check report language
        ReportLanguageItem selectedLanguage = (ReportLanguageItem) reportLanguageCombo.getSelectedItem();
        if (selectedLanguage != null && selectedLanguage.getLanguage() != config.getReportLanguage()) return true;

        if (!smtpHostField.getText().equals(config.getSmtpHost())) return true;
        if (!smtpPortField.getText().isEmpty() && Integer.parseInt(smtpPortField.getText()) != config.getSmtpPort()) return true;
        if (!smtpUsernameField.getText().equals(config.getSmtpUsername())) return true;

        // Check SMTP password
        char[] smtpPasswordChars = smtpPasswordField.getPassword();
        String currentSmtpPassword = smtpPasswordChars != null ? new String(smtpPasswordChars) : "";
        if (!currentSmtpPassword.equals(config.getSmtpPassword() != null ? config.getSmtpPassword() : "")) return true;

        if (!fromEmailField.getText().equals(config.getFromEmail())) return true;
        if (!getToEmails().equals(config.getToEmails())) return true;
        if (!getCcEmails().equals(config.getCcEmails())) return true;
        if (!emailSubjectFormatField.getText().equals(config.getEmailSubjectFormat())) return true;

        ReminderScheduleItem reminderItem = (ReminderScheduleItem) reminderScheduleCombo.getSelectedItem();
        if (reminderItem != null && reminderItem.getSchedule() != config.getReminderSchedule()) return true;

        if (!reminderTimeField.getText().equals(config.getReminderTime())) return true;

        return false;
    }

    /**
     * Apply settings to config
     */
    public void apply(ConfigManager config) {
        config.setEnabled(enabledCheckBox.isSelected());
        config.setIgnoredBranches(getIgnoredBranches());

        // Save report language
        ReportLanguageItem selectedLanguage = (ReportLanguageItem) reportLanguageCombo.getSelectedItem();
        if (selectedLanguage != null) {
            config.setReportLanguage(selectedLanguage.getLanguage());
            LOG.info("Saving report language: " + selectedLanguage.getLanguage());
        }

        AIProviderItem selectedAI = (AIProviderItem) aiProviderCombo.getSelectedItem();
        if (selectedAI != null) {
            config.setAIProvider(selectedAI.getProvider());
        }

        // Log API key saving for debugging
        char[] apiKeyChars = apiKeyField.getPassword();
        String apiKeyValue = (apiKeyChars != null) ? new String(apiKeyChars) : "";
        LOG.info("Saving API Key - Length: " + apiKeyValue.length() + ", Is empty: " + apiKeyValue.isEmpty());
        config.setApiKey(apiKeyValue);

        config.setModel(modelField.getText());
        config.setEndpoint(endpointField.getText());

        config.setSmtpHost(smtpHostField.getText());
        config.setSmtpPort(smtpPortField.getText().isEmpty() ? 587 : Integer.parseInt(smtpPortField.getText()));
        config.setSmtpUsername(smtpUsernameField.getText());
        config.setSmtpPassword(new String(smtpPasswordField.getPassword()));
        config.setFromEmail(fromEmailField.getText());
        config.setToEmails(getToEmails());
        config.setCcEmails(getCcEmails());
        config.setEmailSubjectFormat(emailSubjectFormatField.getText());

        ReminderScheduleItem reminderItem = (ReminderScheduleItem) reminderScheduleCombo.getSelectedItem();
        if (reminderItem != null) {
            config.setReminderSchedule(reminderItem.getSchedule());
        }
        config.setReminderTime(reminderTimeField.getText());

        // Save GitLab instances
        saveInstances();
    }

    private void saveInstances() {
        // Clear all existing instances
        List<GitLabInstance> existingInstances = new ArrayList<>(instanceService.getInstances());
        for (GitLabInstance instance : existingInstances) {
            instanceService.removeInstance(instance.getId());
        }

        // Add all instances from panels
        for (CollapsibleInstancePanel panel : instanceEditorPanels) {
            GitLabInstance instance = panel.getInstance();
            if (instance.isValid()) {
                instanceService.addInstance(instance);
            }
        }

        // Set active instance
        for (CollapsibleInstancePanel panel : instanceEditorPanels) {
            if (panel.isActive()) {
                instanceService.setActiveInstance(panel.getInstance().getId());
                break;
            }
        }
    }

    /**
     * Reset settings from config
     */
    public void reset(ConfigManager config) {
        // Trigger auto-load of projects (non-blocking)
        projectService.ensureProjectsLoaded();

        enabledCheckBox.setSelected(config.isEnabled());
        ignoredBranchesField.setText(String.join(", ", config.getIgnoredBranches()));

        // Set report language
        PluginConfig.ReportLanguage language = config.getReportLanguage();
        LOG.info("Loading report language from config: " + language);
        for (int i = 0; i < reportLanguageCombo.getItemCount(); i++) {
            ReportLanguageItem item = reportLanguageCombo.getItemAt(i);
            if (item.getLanguage() == language) {
                reportLanguageCombo.setSelectedIndex(i);
                LOG.info("Report language combo index set to: " + i);
                break;
            }
        }

        // Set AI Provider
        PluginConfig.AIProvider provider = config.getAIProvider();
        LOG.info("Loading AI Provider from config: " + provider);
        for (int i = 0; i < aiProviderCombo.getItemCount(); i++) {
            AIProviderItem item = aiProviderCombo.getItemAt(i);
            if (item.getProvider() == provider) {
                aiProviderCombo.setSelectedIndex(i);
                LOG.info("AI Provider combo index set to: " + i);
                break;
            }
        }

        // Set AI API key (restore like GitLab token)
        String apiKey = config.getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            apiKeyField.setText(apiKey);
            LOG.info("API key restored (length: " + apiKey.length() + ", first 4 chars: " +
                     (apiKey.length() > 4 ? apiKey.substring(0, 4) + "..." : apiKey) + ")");
        } else {
            apiKeyField.setText("");
            LOG.info("API key is empty, setting empty field");
        }

        // Set other AI settings
        modelField.setText(config.getModel());
        endpointField.setText(config.getEndpoint());

        smtpHostField.setText(config.getSmtpHost());
        smtpPortField.setText(String.valueOf(config.getSmtpPort()));
        smtpUsernameField.setText(config.getSmtpUsername());

        // Set SMTP password (restore like GitLab token)
        String smtpPassword = config.getSmtpPassword();
        if (smtpPassword != null && !smtpPassword.isEmpty()) {
            smtpPasswordField.setText(smtpPassword);
            LOG.info("SMTP password restored (length: " + smtpPassword.length() + ")");
        } else {
            smtpPasswordField.setText("");
        }

        fromEmailField.setText(config.getFromEmail());
        toEmailsField.setText(String.join(", ", config.getToEmails()));
        ccEmailsField.setText(String.join(", ", config.getCcEmails()));
        emailSubjectFormatField.setText(config.getEmailSubjectFormat());

        // Set reminder schedule
        for (int i = 0; i < reminderScheduleCombo.getItemCount(); i++) {
            ReminderScheduleItem item = reminderScheduleCombo.getItemAt(i);
            if (item.getSchedule() == config.getReminderSchedule()) {
                reminderScheduleCombo.setSelectedIndex(i);
                break;
            }
        }
        reminderTimeField.setText(config.getReminderTime());

        // Reload GitLab instances and projects
        loadInstances();
        loadProjectsTree();
    }

    private List<String> getIgnoredBranches() {
        String text = ignoredBranchesField.getText().trim();
        if (text.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> getToEmails() {
        String text = toEmailsField.getText().trim();
        if (text.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private List<String> getCcEmails() {
        String text = ccEmailsField.getText().trim();
        if (text.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private void testAIConnection() {
        testAiButton.setEnabled(false);
        testAiButton.setText("Testing...");

        // Get current field values
        AIProviderItem selectedAI = (AIProviderItem) aiProviderCombo.getSelectedItem();
        PluginConfig.AIProvider provider = selectedAI != null ? selectedAI.getProvider() : PluginConfig.AIProvider.OPENAI;
        String apiKey = apiKeyField.getPassword() != null ? new String(apiKeyField.getPassword()).trim() : "";
        String model = modelField.getText().trim();
        String endpoint = endpointField.getText().trim();

        // Validate API key is not empty
        if (apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel,
                    "Please enter your API key.",
                    "Configuration Error",
                    JOptionPane.ERROR_MESSAGE);
            testAiButton.setEnabled(true);
            testAiButton.setText("Test Connection");
            return;
        }

        // Get ConfigManager instance
        ConfigManager configManager = ConfigManager.getInstance(project);

        // Temporarily save the current settings
        PluginConfig.AIProvider originalProvider = configManager.getAIProvider();
        String originalApiKey = configManager.getApiKey();
        String originalModel = configManager.getModel();
        String originalEndpoint = configManager.getEndpoint();

        // Set test values
        configManager.setAIProvider(provider);
        configManager.setApiKey(apiKey);
        configManager.setModel(model);
        configManager.setEndpoint(endpoint);

        org.flymars.devtools.midas.analysis.AIAnalyzerService aiService =
                new org.flymars.devtools.midas.analysis.AIAnalyzerService();

        aiService.testConnection(configManager).thenAccept(success -> {
            SwingUtilities.invokeLater(() -> {
                if (success) {
                    JOptionPane.showMessageDialog(mainPanel,
                            "Connection test successful!",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(mainPanel,
                            "Connection test failed. Please check your API key and settings.",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
                // Restore original settings
                configManager.setAIProvider(originalProvider);
                configManager.setApiKey(originalApiKey);
                configManager.setModel(originalModel);
                configManager.setEndpoint(originalEndpoint);
                testAiButton.setEnabled(true);
                testAiButton.setText("Test Connection");
            });
        }).exceptionally(e -> {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(mainPanel,
                        "Connection test failed: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                // Restore original settings
                configManager.setAIProvider(originalProvider);
                configManager.setApiKey(originalApiKey);
                configManager.setModel(originalModel);
                configManager.setEndpoint(originalEndpoint);
                testAiButton.setEnabled(true);
                testAiButton.setText("Test Connection");
            });
            return null;
        });
    }

    private void testEmailConnection() {
        testEmailButton.setEnabled(false);
        testEmailButton.setText("Sending...");

        // Validate required fields
        String smtpHost = smtpHostField.getText().trim();
        String smtpPortStr = smtpPortField.getText().trim();
        String username = smtpUsernameField.getText().trim();
        String password = new String(smtpPasswordField.getPassword());
        String fromEmail = fromEmailField.getText().trim();
        List<String> toEmails = getToEmails();

        if (smtpHost.isEmpty() || smtpPortStr.isEmpty() || username.isEmpty() ||
            password.isEmpty() || fromEmail.isEmpty() || toEmails.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(mainPanel,
                        "Please fill in all required fields:\n" +
                        "• SMTP Host\n" +
                        "• SMTP Port\n" +
                        "• Username\n" +
                        "• Password\n" +
                        "• From Email\n" +
                        "• To Emails",
                        "Configuration Error",
                        JOptionPane.ERROR_MESSAGE);
                testEmailButton.setEnabled(true);
                testEmailButton.setText("Test Email");
            });
            return;
        }

        int smtpPort;
        try {
            smtpPort = Integer.parseInt(smtpPortStr);
        } catch (NumberFormatException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(mainPanel,
                        "Invalid SMTP port number",
                        "Configuration Error",
                        JOptionPane.ERROR_MESSAGE);
                testEmailButton.setEnabled(true);
                testEmailButton.setText("Test Email");
            });
            return;
        }

        // Save current config state
        ConfigManager configManager = ConfigManager.getInstance(project);
        String savedHost = configManager.getSmtpHost();
        int savedPort = configManager.getSmtpPort();
        String savedUsername = configManager.getSmtpUsername();
        String savedPassword = configManager.getSmtpPassword();
        String savedFromEmail = configManager.getFromEmail();
        List<String> savedToEmails = configManager.getToEmails();
        List<String> savedCcEmails = configManager.getCcEmails();

        // Temporarily set test config
        configManager.setSmtpHost(smtpHost);
        configManager.setSmtpPort(smtpPort);
        configManager.setSmtpUsername(username);
        configManager.setSmtpPassword(password);
        configManager.setFromEmail(fromEmail);
        configManager.setToEmails(toEmails);
        configManager.setCcEmails(getCcEmails());

        // Send test email in background
        org.flymars.devtools.midas.email.EmailService emailService =
                new org.flymars.devtools.midas.email.EmailService(configManager, project);

        emailService.sendTestEmail(toEmails.get(0)).thenAccept(success -> {
            SwingUtilities.invokeLater(() -> {
                if (success) {
                    JOptionPane.showMessageDialog(mainPanel,
                            "Test email sent successfully!\n\n" +
                            "To: " + toEmails.get(0) + "\n" +
                            "Check your inbox to verify the email was received.",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(mainPanel,
                            "Failed to send test email.\n\n" +
                            "Please check:\n" +
                            "• SMTP server and port are correct (465 for SSL, 587 for STARTTLS)\n" +
                            "• Username and password are correct\n" +
                            "• From email address is valid\n" +
                            "• Network connection is stable",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }

                // Restore original config
                configManager.setSmtpHost(savedHost);
                configManager.setSmtpPort(savedPort);
                configManager.setSmtpUsername(savedUsername);
                configManager.setSmtpPassword(savedPassword);
                configManager.setFromEmail(savedFromEmail);
                configManager.setToEmails(savedToEmails);
                configManager.setCcEmails(savedCcEmails);

                testEmailButton.setEnabled(true);
                testEmailButton.setText("Test Email");
            });
        }).exceptionally(e -> {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(mainPanel,
                        "Failed to send test email:\n" + e.getMessage() + "\n\n" +
                        "Please check:\n" +
                        "• SMTP server and port are correct (465 for SSL, 587 for STARTTLS)\n" +
                        "• Username and password are correct\n" +
                        "• From email address is valid\n" +
                        "• Network connection is stable",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);

                // Restore original config
                configManager.setSmtpHost(savedHost);
                configManager.setSmtpPort(savedPort);
                configManager.setSmtpUsername(savedUsername);
                configManager.setSmtpPassword(savedPassword);
                configManager.setFromEmail(savedFromEmail);
                configManager.setToEmails(savedToEmails);
                configManager.setCcEmails(savedCcEmails);

                testEmailButton.setEnabled(true);
                testEmailButton.setText("Test Email");
            });
            return null;
        });
    }

    /**
     * Update default values when AI provider changes
     */
    private void updateAIProviderDefaults(PluginConfig.AIProvider provider) {
        String currentEndpoint = endpointField.getText().trim();
        String currentModel = modelField.getText().trim();

        // Only set defaults if field is empty or contains the previous provider's default
        switch (provider) {
            case OPENAI:
                if (currentEndpoint.isEmpty() || currentEndpoint.startsWith("https://open.bigmodel.cn")) {
                    endpointField.setText("https://api.openai.com/v1");
                }
                if (currentModel.isEmpty() || currentModel.contains("glm")) {
                    modelField.setText("gpt-4o-mini");
                }
                apiKeyField.setToolTipText("OpenAI API key");
                break;

            case CLAUDE:
                if (currentEndpoint.isEmpty() || currentEndpoint.startsWith("https://api.openai.com")) {
                    endpointField.setText("https://api.anthropic.com");
                }
                if (currentModel.isEmpty() || currentModel.contains("gpt") || currentModel.contains("glm")) {
                    modelField.setText("claude-3-5-sonnet-20241022");
                }
                apiKeyField.setToolTipText("Anthropic API key");
                break;

            case ZHIPU:
                if (currentEndpoint.isEmpty() || currentEndpoint.startsWith("https://api.openai.com") ||
                    currentEndpoint.startsWith("https://api.anthropic.com")) {
                    endpointField.setText("https://open.bigmodel.cn/api/paas/v4");
                }
                if (currentModel.isEmpty() || currentModel.contains("gpt") || currentModel.contains("claude")) {
                    modelField.setText("glm-4-plus");
                }
                apiKeyField.setToolTipText("Zhipu AI API key (get from https://open.bigmodel.cn/usercenter/apikeys)");
                break;

            case CUSTOM:
                // Don't override custom settings
                apiKeyField.setToolTipText("Custom API key");
                break;
        }
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    /**
     * Collapsible panel for editing a single GitLab instance
     */
    private class CollapsibleInstancePanel extends JPanel {
        private final GitLabInstance instance;
        private final JPanel contentPanel;
        private final JButton toggleButton;
        private final JLabel titleLabel;
        private final JTextField nameField;
        private final JTextField urlField;
        private final JPasswordField tokenField;
        private final JButton testButton;
        private final JButton refreshProjectsButton;
        private final JButton setActiveButton;
        private final JButton removeButton;
        private final JLabel connectionStatus;
        private final JLabel activeStatusLabel;
        private boolean isExpanded = true;

        public CollapsibleInstancePanel(GitLabInstance instance) {
            this.instance = instance;
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

            // Header panel with toggle button and status
            JPanel headerPanel = new JPanel(new BorderLayout(10, 5));
            headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

            toggleButton = new JButton("▼");
            toggleButton.setPreferredSize(new Dimension(25, 20));
            toggleButton.setFont(new Font("Monospaced", Font.PLAIN, 12));
            toggleButton.setFocusPainted(false);
            toggleButton.addActionListener(e -> toggleExpanded());

            JPanel titlePanel = new JPanel(new BorderLayout(5, 0));
            titleLabel = new JLabel(instance.getName());
            titleLabel.setFont(new Font(titleLabel.getFont().getName(), Font.BOLD, 13));

            activeStatusLabel = new JLabel();
            activeStatusLabel.setFont(new Font(activeStatusLabel.getFont().getName(), Font.BOLD, 11));
            // Don't call updateActiveStatusLabel() yet, as setActiveButton is not initialized

            titlePanel.add(titleLabel, BorderLayout.CENTER);
            titlePanel.add(activeStatusLabel, BorderLayout.EAST);

            headerPanel.add(toggleButton, BorderLayout.WEST);
            headerPanel.add(titlePanel, BorderLayout.CENTER);

            // Content panel
            contentPanel = new JPanel();
            contentPanel.setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            // Name field
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
            contentPanel.add(new JLabel("Name:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1;
            nameField = new JTextField(20);

            // Add focus listener to update tree when name changes
            nameField.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    String oldName = instance.getName();
                    String newName = nameField.getText().trim();
                    if (!newName.equals(oldName) && !newName.isEmpty()) {
                        saveCurrentInstance();
                    }
                }
            });

            // Add action listener to update tree when Enter is pressed
            nameField.addActionListener(e -> {
                String oldName = instance.getName();
                String newName = nameField.getText().trim();
                if (!newName.equals(oldName) && !newName.isEmpty()) {
                    saveCurrentInstance();
                }
            });

            contentPanel.add(nameField, gbc);

            // URL field
            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
            contentPanel.add(new JLabel("URL:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1;
            urlField = new JTextField(20);
            contentPanel.add(urlField, gbc);

            // Token field
            gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
            contentPanel.add(new JLabel("Token:"), gbc);
            gbc.gridx = 1; gbc.weightx = 1;
            tokenField = new JPasswordField(20);
            contentPanel.add(tokenField, gbc);

            // Buttons row
            gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));

            testButton = new JButton("Test");
            testButton.setToolTipText("Test connection to GitLab instance");
            testButton.addActionListener(e -> testConnection());
            buttonPanel.add(testButton);

            connectionStatus = new JLabel();
            connectionStatus.setFont(new Font(connectionStatus.getFont().getName(), Font.PLAIN, 11));
            buttonPanel.add(connectionStatus);

            refreshProjectsButton = new JButton("Refresh");
            refreshProjectsButton.setToolTipText("Refresh projects");
            refreshProjectsButton.addActionListener(e -> refreshProjectsForThisInstance());
            buttonPanel.add(refreshProjectsButton);

            setActiveButton = new JButton("Activate");
            setActiveButton.setToolTipText("Set as active instance");
            setActiveButton.addActionListener(e -> setActiveInstance());
            buttonPanel.add(setActiveButton);

            removeButton = new JButton("Remove");
            removeButton.setToolTipText("Remove instance");
            removeButton.setFont(new Font(removeButton.getFont().getName(), Font.PLAIN, 11));
            removeButton.setFocusPainted(false);
            removeButton.addActionListener(e -> SettingsPanel.this.removeInstance(this));
            buttonPanel.add(removeButton);

            contentPanel.add(buttonPanel, gbc);

            // Add components
            add(headerPanel, BorderLayout.NORTH);
            add(contentPanel, BorderLayout.CENTER);

            // Load data
            loadInstanceData();
        }

        private void toggleExpanded() {
            if (isExpanded) {
                collapse();
            } else {
                expand();
            }
        }

        public void expand() {
            isExpanded = true;
            toggleButton.setText("▼");
            contentPanel.setVisible(true);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
            revalidate();
            repaint();
        }

        public void collapse() {
            isExpanded = false;
            toggleButton.setText("▶");
            contentPanel.setVisible(false);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            revalidate();
            repaint();
        }

        public void scrollIntoView() {
            SwingUtilities.invokeLater(() -> {
                scrollRectToVisible(getBounds());
            });
        }

        private void loadInstanceData() {
            nameField.setText(instance.getName());
            urlField.setText(instance.getServerUrl());
            tokenField.setText(instance.getAccessToken());
            // Update active status label after all components are initialized
            SwingUtilities.invokeLater(() -> updateActiveStatusLabel());
        }

        private void testConnection() {
            testButton.setEnabled(false);
            connectionStatus.setText("Testing...");

            SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
                @Override
                protected Boolean doInBackground() {
                    GitLabInstance testInstance = getInstance();
                    Boolean result = instanceService.testConnection(testInstance).join();

                    // If successful, update the service's instance with user info
                    if (result) {
                        // Update or add the instance to the service with user info
                        GitLabInstance existing = instanceService.getInstance(testInstance.getId());
                        if (existing != null) {
                            existing.setUserName(testInstance.getUserName());
                            existing.setUserDisplayName(testInstance.getUserDisplayName());
                            existing.setUserEmail(testInstance.getUserEmail());
                            instanceService.updateInstance(existing);
                        } else {
                            instanceService.addInstance(testInstance);
                        }

                        LOG.info("Connection successful. Current user: " + testInstance.getUserName());
                    }

                    return result;
                }

                @Override
                protected void done() {
                    try {
                        Boolean success = get();

                        if (success) {
                            connectionStatus.setText("✓ Connected");
                            connectionStatus.setForeground(new Color(0, 150, 0));
                        } else {
                            connectionStatus.setText("✗ Failed");
                            connectionStatus.setForeground(Color.RED);
                        }
                    } catch (Exception e) {
                        connectionStatus.setText("✗ Error");
                        connectionStatus.setForeground(Color.RED);
                    } finally {
                        testButton.setEnabled(true);
                    }
                }
            };
            worker.execute();
        }

        public GitLabInstance getInstance() {
            instance.setName(nameField.getText().trim());
            instance.setServerUrl(urlField.getText().trim());
            instance.setAccessToken(new String(tokenField.getPassword()));
            return instance;
        }

        public boolean isActive() {
            return instance.isActive();
        }

        private void updateActiveStatusLabel() {
            if (instance.isActive()) {
                activeStatusLabel.setText("⭐ Active");
                activeStatusLabel.setForeground(new Color(0, 150, 0));
                setActiveButton.setEnabled(false);
            } else {
                activeStatusLabel.setText("");
                setActiveButton.setEnabled(true);
            }
        }

        private void refreshProjectsForThisInstance() {
            // Save this instance first
            saveCurrentInstance();

            // Refresh projects for this instance only
            refreshProjectsButton.setEnabled(false);

            SwingWorker<List<GitLabProject>, Void> worker = new SwingWorker<>() {
                @Override
                protected List<GitLabProject> doInBackground() {
                    try {
                        projectService.refreshProjectsForInstance(instance.getId()).get();
                        return projectService.getProjectsForInstance(instance.getId());
                    } catch (Exception e) {
                        LOG.error("Error refreshing projects for instance: " + instance.getName(), e);
                        return new ArrayList<>();
                    }
                }

                @Override
                protected void done() {
                    List<GitLabProject> projects = projectService.getProjectsForInstance(instance.getId());
                    refreshProjectsButton.setEnabled(true);

                    // Show notification
                    com.intellij.notification.Notification notification =
                            new com.intellij.notification.Notification(
                                    "Midas",
                                    "Projects Refreshed",
                                    "Fetched " + projects.size() + " projects from " + instance.getName(),
                                    com.intellij.notification.NotificationType.INFORMATION
                            );
                    com.intellij.notification.Notifications.Bus.notify(notification, project);

                    // Reload projects tree to show updated projects
                    loadProjectsTree();
                }
            };
            worker.execute();
        }

        private void setActiveInstance() {
            // Deactivate all instances
            for (GitLabInstance inst : instanceService.getInstances()) {
                inst.setActive(false);
            }

            // Activate this instance
            instance.setActive(true);
            saveCurrentInstance();
            updateActiveStatusLabel();

            // Update all panels' active status labels
            for (CollapsibleInstancePanel panel : instanceEditorPanels) {
                panel.updateActiveStatusLabel();
            }

            // Reload projects tree to show only active instance's projects
            loadProjectsTree();

            LOG.info("Set active instance: " + instance.getName());
        }

        private void saveCurrentInstance() {
            String oldName = instance.getName();
            instance.setName(nameField.getText().trim());
            instance.setServerUrl(urlField.getText().trim());
            instance.setAccessToken(new String(tokenField.getPassword()));

            GitLabInstance existing = instanceService.getInstance(instance.getId());
            if (existing != null) {
                instanceService.updateInstance(instance);
            } else {
                instanceService.addInstance(instance);
            }

            // Update title label, project cache, clear commit cache, and reload projects tree if instance name changed
            if (!instance.getName().equals(oldName)) {
                titleLabel.setText(instance.getName());

                // Update instance name in all cached projects for this instance
                projectService.updateInstanceName(instance.getId(), instance.getName());

                // Clear commit cache for this instance to force refresh with new project names
                projectService.clearCommitCacheForInstance(instance.getId());

                // Publish settings changed event to notify ReportPanel and other listeners
                project.getMessageBus().syncPublisher(
                        org.flymars.devtools.midas.CommitReporterKeys.SETTINGS_CHANGED_TOPIC
                ).onSettingsChanged();

                // Reload projects tree to show updated instance name
                SwingUtilities.invokeLater(() -> loadProjectsTree());
            }
        }
    }

    /**
     * Update selected projects count label
     */
    private void updateSelectionCount() {
        int selectedCount = 0;
        List<GitLabInstance> instances = instanceService.getInstances();

        for (GitLabInstance instance : instances) {
            List<GitLabProject> projects = projectService.getProjectsForInstance(instance.getId());
            selectedCount += (int) projects.stream().filter(GitLabProject::isSelected).count();
        }

        selectedProjectsLabel.setText("Selected: " + selectedCount + " projects");
    }

    /**
     * Save selected projects to configuration (auto-saved on checkbox change)
     */
    private void saveSelectedProjects() {
        List<GitLabProject> selectedProjects = new ArrayList<>();

        // Get all selected projects from service
        for (GitLabInstance instance : instanceService.getInstances()) {
            List<GitLabProject> projects = projectService.getProjectsForInstance(instance.getId());
            selectedProjects.addAll(projects.stream()
                    .filter(GitLabProject::isSelected)
                    .collect(Collectors.toList()));
        }

        projectService.setSelectedProjects(selectedProjects);
        LOG.info("Auto-saved " + selectedProjects.size() + " projects to configuration");
    }

    /**
     * Update scroll pane heights based on available window size
     * This ensures buttons remain visible while maximizing scroll area
     */
    private void updateScrollHeights() {
        if (instancesScroll == null || projectsScroll == null) {
            return;
        }

        // Get the available height from the main panel (settings panel)
        int availableHeight = mainPanel.getHeight();

        // Limit available height to settings window maximum
        int maxAvailableHeight = 600; // Reasonable maximum for settings window
        availableHeight = Math.min(availableHeight, maxAvailableHeight);

        // Calculate scroll height, accounting for fixed elements
        // For instances: available height - title panel (with button) height - borders
        int instancesScrollHeight = Math.max(MIN_SCROLL_HEIGHT,
                availableHeight - TITLE_HEIGHT - BORDER_HEIGHT);

        // For projects: available height - (title + search + count) height - borders
        int projectsScrollHeight = Math.max(MIN_SCROLL_HEIGHT,
                availableHeight - TITLE_HEIGHT - COUNT_HEIGHT - BORDER_HEIGHT);

        // Update the preferred sizes for both scroll panes
        instancesScroll.setPreferredSize(new Dimension(Integer.MAX_VALUE, instancesScrollHeight));
        projectsScroll.setPreferredSize(new Dimension(Integer.MAX_VALUE, projectsScrollHeight));

        // Revalidate to apply the changes
        instancesScroll.revalidate();
        projectsScroll.revalidate();
    }

    /**
     * Custom cell renderer for project tree
     * Similar to IDEA's Git commit dialog style
     */
    private static class ProjectTreeCellRenderer extends javax.swing.tree.DefaultTreeCellRenderer {
        private static final java.awt.Font PLAIN_FONT = new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12);
        private static final java.awt.Font BOLD_FONT = new java.awt.Font("Dialog", java.awt.Font.BOLD, 12);

        @Override
        public java.awt.Component getTreeCellRendererComponent(
                javax.swing.JTree tree,
                Object value,
                boolean selected,
                boolean expanded,
                boolean leaf,
                int row,
                boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            if (value instanceof CheckedTreeNode) {
                CheckedTreeNode node = (CheckedTreeNode) value;
                Object userObject = node.getUserObject();

                // Set consistent font
                setFont(PLAIN_FONT);
                setOpaque(true);

                if (userObject instanceof GitLabInstance) {
                    GitLabInstance instance = (GitLabInstance) userObject;
                    setText(instance.getName());
                    setFont(BOLD_FONT);

                    // Set icon for expanded/collapsed state
                    if (expanded) {
                        setIcon(getExpandedFolderIcon());
                    } else {
                        setIcon(getCollapsedFolderIcon());
                    }

                } else if (userObject instanceof GitLabProject) {
                    GitLabProject project = (GitLabProject) userObject;

                    // Build text with checkbox
                    String checkbox = node.isChecked() ? "☑ " : "☐ ";
                    String text = checkbox + project.getName();
                    setText(text);

                    // File icon for projects
                    setIcon(getFileIcon());

                    // Add tooltip with description
                    if (project.getDescription() != null && !project.getDescription().isEmpty()) {
                        setToolTipText(project.getName() + "\n" + project.getDescription());
                    } else {
                        setToolTipText(project.getName());
                    }
                }
            } else {
                // Root node or hint text
                if (value instanceof javax.swing.tree.DefaultMutableTreeNode) {
                    Object userObject = ((javax.swing.tree.DefaultMutableTreeNode) value).getUserObject();
                    if (userObject instanceof String) {
                        setText((String) userObject);
                        setFont(PLAIN_FONT);
                        setIcon(null);
                        setEnabled(false);
                    }
                }
            }

            // Override selection colors
            if (selected) {
                setBackground(new java.awt.Color(76, 120, 214)); // IDEA selection blue
                setForeground(java.awt.Color.WHITE);
            } else {
                setBackground(tree.getBackground());
                setForeground(java.awt.Color.BLACK);
            }

            return this;
        }

        private javax.swing.Icon getExpandedFolderIcon() {
            return new FolderIcon(true);
        }

        private javax.swing.Icon getCollapsedFolderIcon() {
            return new FolderIcon(false);
        }

        private javax.swing.Icon getFileIcon() {
            return new FileIcon();
        }
    }

    /**
     * Simple folder icon
     */
    private static class FolderIcon implements javax.swing.Icon {
        private final boolean expanded;

        FolderIcon(boolean expanded) {
            this.expanded = expanded;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw folder icon
            g2.setColor(new java.awt.Color(127, 127, 127));
            if (expanded) {
                // Open folder
                g2.drawRect(x, y + 2, 14, 10);
                g2.drawRect(x + 2, y + 4, 10, 8);
                g2.drawLine(x + 6, y, x + 6, y + 2);
            } else {
                // Closed folder
                g2.drawRect(x, y + 2, 14, 11);
                g2.drawLine(x + 6, y, x + 6, y + 2);
            }
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
    }

    /**
     * Simple file icon
     */
    private static class FileIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw simple file icon
            g2.setColor(new java.awt.Color(100, 100, 100));
            g2.drawRect(x, y, 14, 18);
            g2.drawLine(x + 3, y + 5, x + 11, y + 5);
            g2.drawLine(x + 3, y + 8, x + 11, y + 8);
            g2.drawLine(x + 3, y + 11, x + 11, y + 11);

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
    }

    /**
     * Wrapper for AI provider combo box items
     */
    private static class AIProviderItem {
        private final PluginConfig.AIProvider provider;
        private final String displayName;

        public AIProviderItem(PluginConfig.AIProvider provider, String displayName) {
            this.provider = provider;
            this.displayName = displayName;
        }

        public PluginConfig.AIProvider getProvider() {
            return provider;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Wrapper for report language combo box items
     */
    private static class ReportLanguageItem {
        private final PluginConfig.ReportLanguage language;

        public ReportLanguageItem(PluginConfig.ReportLanguage language) {
            this.language = language;
        }

        public PluginConfig.ReportLanguage getLanguage() {
            return language;
        }

        @Override
        public String toString() {
            return language.getDisplayName();
        }
    }

    /**
     * Wrapper for reminder schedule combo box items
     */
    private static class ReminderScheduleItem {
        private final PluginConfig.ReminderSchedule schedule;
        private final String displayName;

        public ReminderScheduleItem(PluginConfig.ReminderSchedule schedule, String displayName) {
            this.schedule = schedule;
            this.displayName = displayName;
        }

        public PluginConfig.ReminderSchedule getSchedule() {
            return schedule;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
