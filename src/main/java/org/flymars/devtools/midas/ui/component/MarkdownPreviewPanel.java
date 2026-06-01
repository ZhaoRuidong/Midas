package org.flymars.devtools.midas.ui.component;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.JBColor;
import org.intellij.markdown.ast.ASTNode;
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor;
import org.intellij.markdown.html.HtmlGenerator;
import org.intellij.markdown.html.HtmlGeneratorKt;
import org.intellij.markdown.parser.MarkdownParser;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.net.URI;

/**
 * HTML preview panel that renders markdown content using IntelliJ's markdown parser.
 */
public class MarkdownPreviewPanel extends JPanel {
    private static final Logger LOG = Logger.getInstance(MarkdownPreviewPanel.class);

    private final JEditorPane htmlPane;
    private String lastRenderedHtml = "";

    public MarkdownPreviewPanel() {
        super(new BorderLayout());
        setOpaque(false);

        htmlPane = new JEditorPane();
        htmlPane.setEditable(false);
        htmlPane.setContentType("text/html");
        htmlPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        htmlPane.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        htmlPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ex) {
                    LOG.warn("Failed to open link: " + e.getURL(), ex);
                }
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(htmlPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);

        applyThemeCss();
    }

    public void updateMarkdown(String markdownText) {
        if (markdownText == null || markdownText.isBlank()) {
            htmlPane.setText(wrapHtml("<p style='color: #999; text-align: center; margin-top: 40px;'>Start typing to see preview...</p>"));
            lastRenderedHtml = "";
            return;
        }

        String bodyHtml = renderMarkdownToHtml(markdownText);
        lastRenderedHtml = bodyHtml;
        htmlPane.setText(wrapHtml(bodyHtml));
        htmlPane.setCaretPosition(0);
    }

    public void updateTheme() {
        applyThemeCss();
        if (!lastRenderedHtml.isEmpty()) {
            htmlPane.setText(wrapHtml(lastRenderedHtml));
            htmlPane.setCaretPosition(0);
        }
    }

    private void applyThemeCss() {
        // Theme is applied dynamically in wrapHtml() via isDark check
    }

    private String wrapHtml(String bodyContent) {
        boolean isDark = !JBColor.isBright();
        String bgColor = isDark ? "#2b2b2b" : "#ffffff";
        String css = isDark ? getDarkCss() : getLightCss();

        return "<html><head><style>" + css + "</style></head>"
                + "<body bgcolor='" + bgColor + "'>"
                + bodyContent
                + "</body></html>";
    }

    public static String renderMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";

        try {
            CommonMarkFlavourDescriptor flavour = new CommonMarkFlavourDescriptor();
            MarkdownParser parser = new MarkdownParser(flavour);
            ASTNode tree = parser.buildMarkdownTreeFromString(markdown);
            HtmlGenerator generator = new HtmlGenerator(markdown, tree, flavour, false);
            return generator.generateHtml(new HtmlGenerator.DefaultTagRenderer(
                    HtmlGeneratorKt.getDUMMY_ATTRIBUTES_CUSTOMIZER(), false));
        } catch (Exception e) {
            LOG.warn("Failed to parse markdown, falling back to plain text", e);
            return escapeHtml(markdown).replace("\n", "<br>\n");
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String getLightCss() {
        return """
                body { font-family: SansSerif; font-size: 14px; color: #333; margin: 0; padding: 5px; }
                h1 { color: #2c3e50; border-bottom: solid 2px #3498db; margin-top: 16px; }
                h2 { color: #34495e; margin-top: 20px; }
                h3 { color: #555; margin-top: 16px; }
                p { margin: 8px 0; }
                ul { padding-left: 24px; }
                ol { padding-left: 24px; }
                li { margin: 4px 0; }
                code { font-family: Monospaced; font-size: 13px; }
                pre { background-color: #f5f5f5; padding: 12px; }
                blockquote { color: #666; margin: 10px 0; padding: 5px 15px; }
                hr { border: none; border-top: solid 1px #ddd; margin: 20px 0; }
                th { font-weight: bold; background-color: #f5f5f5; }
                td { padding: 8px 12px; }
                a { color: #3498db; }
                strong { font-weight: bold; }
                em { font-style: italic; }
                """;
    }

    private static String getDarkCss() {
        return """
                body { font-family: SansSerif; font-size: 14px; color: #d4d4d4; margin: 0; padding: 5px; }
                h1 { color: #569cd6; border-bottom: solid 2px #569cd6; margin-top: 16px; }
                h2 { color: #dcdcaa; margin-top: 20px; }
                h3 { color: #bbb; margin-top: 16px; }
                p { margin: 8px 0; }
                ul { padding-left: 24px; }
                ol { padding-left: 24px; }
                li { margin: 4px 0; }
                code { font-family: Monospaced; font-size: 13px; color: #ce9178; }
                pre { background-color: #1e1e1e; padding: 12px; }
                blockquote { color: #999; margin: 10px 0; padding: 5px 15px; }
                hr { border: none; border-top: solid 1px #444; margin: 20px 0; }
                th { font-weight: bold; background-color: #333; }
                td { padding: 8px 12px; }
                a { color: #4ec9b0; }
                strong { font-weight: bold; color: #e0e0e0; }
                em { font-style: italic; }
                """;
    }
}
