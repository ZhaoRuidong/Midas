package org.flymars.devtools.midas.gitlab.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a GitLab server configuration
 * Supports both gitlab.com and self-managed instances
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitLabInstance {
    /**
     * Unique identifier for this instance configuration
     * User-defined or auto-generated UUID
     */
    private String id;

    /**
     * Display name for this instance (e.g., "Company GitLab", "Personal GitLab")
     */
    private String name;

    /**
     * GitLab server URL
     * e.g., "https://gitlab.com" or "https://gitlab.company.com"
     */
    private String serverUrl;

    /**
     * Personal Access Token for authentication
     * Note: This should be stored securely in PasswordSafe, not in plain text
     */
    private String accessToken;

    /**
     * Current authenticated username for this instance
     * Fetched from GitLab API when connection is tested
     * Maps to GitLabUser.username from /api/v4/user
     */
    private String userName;

    /**
     * Current authenticated user's display name for this instance
     * Fetched from GitLab API when connection is tested
     * Maps to GitLabUser.name from /api/v4/user
     */
    private String userDisplayName;

    /**
     * Current authenticated user's email for this instance
     * Fetched from GitLab API when connection is tested
     * Maps to GitLabUser.email from /api/v4/user
     */
    private String userEmail;

    /**
     * Whether this instance is currently active/selected
     */
    @Builder.Default
    private boolean isActive = false;

    /**
     * Validates if this instance configuration is complete
     */
    public boolean isValid() {
        return id != null && !id.isEmpty()
                && name != null && !name.isEmpty()
                && serverUrl != null && !serverUrl.isEmpty()
                && accessToken != null && !accessToken.isEmpty();
    }

    /**
     * Normalizes the server URL to ensure consistent format
     */
    public String getNormalizedUrl() {
        String url = serverUrl.trim();
        // Remove trailing slash
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        // Ensure protocol
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        return url;
    }

    /**
     * Gets the API base URL for this instance
     */
    public String getApiBaseUrl() {
        return getNormalizedUrl() + "/api/v4";
    }

    // Explicit getters and setters for user info fields
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserDisplayName() {
        return userDisplayName;
    }

    public void setUserDisplayName(String userDisplayName) {
        this.userDisplayName = userDisplayName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
}
