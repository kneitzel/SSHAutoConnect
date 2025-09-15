package de.kneitzel;

import java.awt.*;
import java.util.function.Consumer;

/**
 * Interface for cross-platform menu system abstraction.
 * This allows the application to work with both System Tray (Windows/Linux)
 * and macOS Dock menu through a unified interface.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 */
public interface MenuSystem {

    /**
     * Initializes the menu system for the current platform.
     *
     * @param connectedIcon   Icon to display when connections are active
     * @param unconnectedIcon Icon to display when no connections are active
     * @return true if initialization was successful, false otherwise
     */
    boolean initialize(Image connectedIcon, Image unconnectedIcon);

    /**
     * Updates the menu with current SSH configurations.
     * This rebuilds the entire menu structure based on available configurations.
     *
     * @param configManager     The SSH configuration manager containing all configs
     * @param connections       Map of active connections
     * @param connectionToggler Function to toggle connection state
     * @param configEditor      Function to edit a configuration
     */
    void updateMenu(SshConfigManager configManager,
                    java.util.Map<String, SshConnection> connections,
                    Consumer<String> connectionToggler,
                    Consumer<String> configEditor);

    /**
     * Updates the icon to reflect current connection state.
     *
     * @param hasActiveConnections true if any connections are active
     */
    void updateIcon(boolean hasActiveConnections);

    /**
     * Shows a notification message to the user.
     *
     * @param title       Title of the notification
     * @param message     Message content
     * @param messageType Type of message (info, warning, error)
     */
    void showNotification(String title, String message, TrayIcon.MessageType messageType);

    /**
     * Checks if the menu system is supported on the current platform.
     *
     * @return true if supported, false otherwise
     */
    boolean isSupported();

    /**
     * Cleans up resources when the application shuts down.
     */
    void cleanup();
}
