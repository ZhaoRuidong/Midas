package org.flymars.devtools.midas.ui.component;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Tabbed markdown editor with "Edit" and "Preview" tabs.
 * Edit tab: plain text JTextArea.
 * Preview tab: rendered HTML from the markdown content.
 */
public class MarkdownSplitEditor {

    private final JTabbedPane tabbedPane;
    private final JTextArea textArea;
    private final MarkdownPreviewPanel previewPanel;
    private final Timer debounceTimer;
    private boolean disposed = false;
    private String lastPreviewedText = "";

    public MarkdownSplitEditor(@NotNull Disposable parentDisposable) {
        tabbedPane = new JTabbedPane();

        // Edit tab
        textArea = new JTextArea();
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setTabSize(4);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane editScrollPane = new JScrollPane(textArea);
        tabbedPane.addTab("Edit", editScrollPane);

        // Preview tab
        previewPanel = new MarkdownPreviewPanel();
        tabbedPane.addTab("Preview", previewPanel);

        // Update preview when switching to Preview tab
        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedIndex() == 1) {
                String currentText = textArea.getText();
                if (!currentText.equals(lastPreviewedText)) {
                    previewPanel.updateMarkdown(currentText);
                    lastPreviewedText = currentText;
                }
            }
        });

        // Debounce timer (unused but kept for API compatibility)
        debounceTimer = new Timer(300, e -> {});
        debounceTimer.setRepeats(false);
    }

    public JComponent getComponent() {
        return tabbedPane;
    }

    public String getText() {
        return textArea.getText();
    }

    public void setText(String text) {
        textArea.setText(text != null ? text : "");
        lastPreviewedText = ""; // force preview refresh on next tab switch
    }

    public void updateTheme() {
        if (!disposed) {
            previewPanel.updateTheme();
        }
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        debounceTimer.stop();
    }
}
