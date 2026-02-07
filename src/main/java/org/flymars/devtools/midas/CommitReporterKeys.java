package org.flymars.devtools.midas;

import com.intellij.util.messages.Topic;

/**
 * Keys for data passing in the plugin
 */
public interface CommitReporterKeys {
    // Add any data keys needed for the plugin

    /**
     * Message bus topic for settings changed events
     * Published when user saves settings, tool windows should refresh
     */
    Topic<SettingsChangedListener> SETTINGS_CHANGED_TOPIC = Topic.create(
        "SettingsChanged",
        SettingsChangedListener.class
    );

    /**
     * Listener interface for settings changed events
     */
    interface SettingsChangedListener {
        /**
         * Called when settings are changed and saved
         */
        void onSettingsChanged();
    }
}
