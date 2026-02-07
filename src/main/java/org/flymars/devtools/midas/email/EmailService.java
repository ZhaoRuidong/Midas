package org.flymars.devtools.midas.email;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.flymars.devtools.midas.config.ConfigManager;
import org.flymars.devtools.midas.data.WeeklyReport;
import org.flymars.devtools.midas.gitlab.GitLabInstanceService;
import org.flymars.devtools.midas.gitlab.model.GitLabInstance;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Service for sending emails via SMTP
 * Concrete implementation using Jakarta Mail
 */
public class EmailService {
    private static final Logger LOG = Logger.getInstance(EmailService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter SUBJECT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    private final ConfigManager config;
    private final Project project;

    public EmailService(ConfigManager config) {
        this.config = config;
        this.project = null;
    }

    public EmailService(ConfigManager config, Project project) {
        this.config = config;
        this.project = project;
    }

    /**
     * Send a weekly report via email
     */
    public CompletableFuture<Boolean> sendReport(WeeklyReport report) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Session session = createSMTPSession();
                MimeMessage message = buildEmail(report, session);
                Transport.send(message);

                LOG.info("Report email sent successfully to " + String.join(", ", config.getToEmails()));
                return true;
            } catch (Exception e) {
                LOG.error("Failed to send report email", e);
                return false;
            }
        });
    }

    /**
     * Send a test email to verify configuration
     */
    public CompletableFuture<Boolean> sendTestEmail(String recipientEmail) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Session session = createSMTPSession();
                MimeMessage message = new MimeMessage(session);

                // Set from address - use SMTP username as sender for better compatibility
                String fromEmail = config.getFromEmail();
                if (fromEmail == null || fromEmail.isEmpty()) {
                    // Fallback to SMTP username if from email is not set
                    fromEmail = config.getSmtpUsername();
                    LOG.info("Using SMTP username as from address: " + fromEmail);
                }

                // Validate email format
                if (!fromEmail.contains("@")) {
                    LOG.error("Invalid from email format: " + fromEmail);
                    return false;
                }

                LOG.info("Sending test email with From: " + fromEmail + ", To: " + recipientEmail);
                message.setFrom(new InternetAddress(fromEmail));
                message.setRecipients(Message.RecipientType.TO, recipientEmail);
                message.setSubject("Midas Plugin - Test Email");

                Multipart multipart = new MimeMultipart();

                // Add text part
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText("This is a test email from the Midas IntelliJ plugin.\n\n" +
                        "Your email configuration is working correctly!", "utf-8");
                multipart.addBodyPart(textPart);

                // Add HTML part
                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent("<html><body>" +
                        "<h2>Midas Plugin - Test Email</h2>" +
                        "<p>Your email configuration is working correctly!</p>" +
                        "</body></html>", "text/html; charset=utf-8");
                multipart.addBodyPart(htmlPart);

                message.setContent(multipart);

                Transport.send(message);
                LOG.info("Test email sent successfully to " + recipientEmail);
                return true;
            } catch (MessagingException e) {
                LOG.error("Failed to send test email - SMTP Error", e);
                // Log detailed error information
                if (e.getNextException() != null) {
                    LOG.error("Root cause: " + e.getNextException().getMessage());
                }
                return false;
            } catch (Exception e) {
                LOG.error("Failed to send test email", e);
                return false;
            }
        });
    }

    /**
     * Test email configuration by connecting to SMTP server
     */
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Session session = createSMTPSession();
                Transport transport = session.getTransport();
                transport.connect();
                transport.close();

                LOG.info("SMTP connection test successful");
                return true;
            } catch (Exception e) {
                LOG.error("SMTP connection test failed", e);
                return false;
            }
        });
    }

    /**
     * Create SMTP session with authentication
     * Automatically detects SSL (port 465) vs STARTTLS (port 587)
     */
    private Session createSMTPSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", config.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(config.getSmtpPort()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.trust", config.getSmtpHost());

        // Detect SSL vs STARTTLS based on port
        // Port 465 is typically SSL, Port 587 is typically STARTTLS
        if (config.getSmtpPort() == 465) {
            // Use SSL for port 465
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
            props.put("mail.smtp.socketFactory.port", String.valueOf(config.getSmtpPort()));
            LOG.info("Using SSL connection for port 465");
        } else {
            // Use STARTTLS for other ports (e.g., 587)
            props.put("mail.smtp.starttls.enable", "true");
            LOG.info("Using STARTTLS connection for port " + config.getSmtpPort());
        }

        // Log authentication info for debugging
        String username = config.getSmtpUsername();
        String password = config.getSmtpPassword();
        LOG.info("SMTP Authentication - Username: " + username + ", Password length: " +
                (password != null ? password.length() : 0));

        // Check if password is empty
        if (password == null || password.isEmpty()) {
            LOG.error("SMTP password is empty! Please check your email configuration.");
        }

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                LOG.debug("Providing authentication for user: " + username);
                return new PasswordAuthentication(username, password);
            }
        });
    }

    /**
     * Build email message with report content
     */
    private MimeMessage buildEmail(WeeklyReport report, Session session) throws MessagingException {
        MimeMessage message = new MimeMessage(session);

        // Set from address - use SMTP username as sender for better compatibility
        String fromEmail = config.getFromEmail();
        if (fromEmail == null || fromEmail.isEmpty()) {
            // Fallback to SMTP username if from email is not set
            fromEmail = config.getSmtpUsername();
            LOG.info("Using SMTP username as from address: " + fromEmail);
        }

        LOG.info("Sending report email with From: " + fromEmail);
        message.setFrom(new InternetAddress(fromEmail));

        List<String> toEmails = config.getToEmails();
        if (!toEmails.isEmpty()) {
            message.setRecipients(Message.RecipientType.TO,
                    String.join(", ", toEmails));
            LOG.info("To: " + String.join(", ", toEmails));
        }

        // Add CC recipients
        List<String> ccEmails = config.getCcEmails();
        if (ccEmails != null && !ccEmails.isEmpty()) {
            message.setRecipients(Message.RecipientType.CC,
                    String.join(", ", ccEmails));
            LOG.info("CC: " + String.join(", ", ccEmails));
        }

        // Set subject with dynamic format
        String subject = formatEmailSubject(report);
        message.setSubject(subject, "UTF-8");

        // Build multipart content
        Multipart multipart = new MimeMultipart();

        // Add text part
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(buildTextContent(report), "utf-8");
        multipart.addBodyPart(textPart);

        // Add HTML part
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(report.getHtmlContent(), "text/html; charset=utf-8");
        multipart.addBodyPart(htmlPart);

        message.setContent(multipart);

        return message;
    }

    /**
     * Format email subject using configured format with variable substitution
     */
    private String formatEmailSubject(WeeklyReport report) {
        String format = config.getEmailSubjectFormat();
        if (format == null || format.isEmpty()) {
            format = "${user}的工作周报(${weekStart} - ${weekEnd})";
        }

        // Get current user name from GitLab instance
        String userName = "User";
        if (project != null) {
            GitLabInstanceService instanceService = GitLabInstanceService.getInstance(project);
            GitLabInstance activeInstance = instanceService.getInstances().stream()
                    .filter(GitLabInstance::isActive)
                    .findFirst()
                    .orElse(null);
            if (activeInstance != null && activeInstance.getUserDisplayName() != null) {
                userName = activeInstance.getUserDisplayName();
            } else if (activeInstance != null && activeInstance.getUserName() != null) {
                userName = activeInstance.getUserName();
            }
        }

        // Build variable map
        Map<String, String> variables = new HashMap<>();
        variables.put("user", userName);
        variables.put("weekStart", report.getWeekStart().format(SUBJECT_DATE_FORMATTER));
        variables.put("weekEnd", report.getWeekEnd().format(SUBJECT_DATE_FORMATTER));
        variables.put("year", String.valueOf(report.getWeekStart().getYear()));
        variables.put("month", String.valueOf(report.getWeekStart().getMonthValue()));
        variables.put("day", String.valueOf(report.getWeekStart().getDayOfMonth()));

        // Replace variables
        String result = format;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }

        return result;
    }

    /**
     * Build plain text content of the email
     */
    private String buildTextContent(WeeklyReport report) {
        StringBuilder sb = new StringBuilder();

        sb.append("Weekly Development Report\n");
        sb.append("========================\n\n");

        sb.append(String.format("Period: %s to %s\n\n",
                report.getWeekStart().format(DATE_FORMATTER),
                report.getWeekEnd().format(DATE_FORMATTER)));

        if (report.getStatistics() != null) {
            sb.append("Statistics:\n");
            sb.append(String.format("  Total Commits: %d\n", report.getStatistics().getTotalCommits()));
            sb.append(String.format("  Lines Added: %d\n", report.getStatistics().getTotalInsertions()));
            sb.append(String.format("  Lines Deleted: %d\n", report.getStatistics().getTotalDeletions()));
            sb.append(String.format("  Files Changed: %d\n\n", report.getStatistics().getTotalFilesChanged()));
        }

        if (report.getSummary() != null && !report.getSummary().isEmpty()) {
            sb.append("Summary:\n");
            sb.append(report.getSummary()).append("\n\n");
        }

        sb.append("---\n");
        sb.append("Generated by Midas IntelliJ Plugin\n");
        sb.append("\n");
        sb.append("========================================\n");
        sb.append("📢 ABOUT THIS EMAIL\n");
        sb.append("========================================\n");
        sb.append("This email was generated by Midas, an IntelliJ IDEA plugin.\n\n");
        sb.append("Midas helps you:\n");
        sb.append("  • Automatically track Git commits\n");
        sb.append("  • Generate professional weekly reports using AI\n");
        sb.append("  • Send reports via email with scheduled reminders\n");
        sb.append("  • Encrypt sensitive data for privacy protection\n\n");
        sb.append("Learn more: https://github.com/ZhaoRuidong/Midas\n");
        sb.append("✨ Make weekly reporting easier and boost productivity!\n");

        return sb.toString();
    }

    /**
     * Validate email configuration
     */
    public boolean isConfigurationValid() {
        return config.getSmtpHost() != null && !config.getSmtpHost().isEmpty()
                && config.getSmtpPort() > 0
                && config.getSmtpUsername() != null && !config.getSmtpUsername().isEmpty()
                && config.getSmtpPassword() != null && !config.getSmtpPassword().isEmpty()
                && config.getFromEmail() != null && !config.getFromEmail().isEmpty()
                && !config.getToEmails().isEmpty();
    }
}
