package de.kneitzel;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * SSH Auto Connect - Main application class for managing multiple SSH connections.
 * <p>
 * This application provides a graphical user interface via System Tray (Windows/Linux)
 * or Dock menu (macOS) for managing and controlling SSH connections with port forwarding,
 * file transfer, and terminal integration.
 * <p>
 * Features:
 * - Management of multiple SSH configurations
 * - Simultaneous SSH connections
 * - Port forwarding with start/stop commands
 * - SFTP file transfer (upload/download)
 * - Terminal integration
 * - Cross-platform menu integration (System Tray/Dock)
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 * @since 1.0
 */
@Slf4j
public class SSHAutoConnect {

    /**
     * Path to icon for active connections in classpath
     */
    private static final String CONNECTED_ICON = "/connectedIcon.png";

    /**
     * Path to icon for inactive connections in classpath
     */
    private static final String UNCONNECTED_ICON = "/unconnectedIcon.png";

    /**
     * Path to executable application file, determined at startup
     */
    public static String APP_EXECUTABLE;

    /**
     * Cross-platform menu system (System Tray or macOS Dock)
     */
    private MenuSystem menuSystem;

    /**
     * SSH configuration manager
     */
    private SshConfigManager configManager;

    /**
     * Map of active SSH connections, key is the configuration name
     */
    private Map<String, SshConnection> connections;

    /**
     * Icon image for "connected" state
     */
    private Image connectedImage;

    /**
     * Icon image for "disconnected" state
     */
    private Image unconnectedImage;

    /**
     * Main entry point of the application.
     * <p>
     * Initializes the look-and-feel, determines the application path and starts
     * the SSH Auto Connect application.
     *
     * @param args Command line arguments (currently not used)
     */
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            log.warn("Could not set system look and feel", e);
        }

        // Log platform information
        log.info("Platform Information:\n{}", MenuSystemFactory.getPlatformInfo());

        Path appPath = getAppImagePath();
        APP_EXECUTABLE = appPath != null ? appPath.toString() : null;
        new SSHAutoConnect().startUp();
    }

    /**
     * Determines the path to the currently executed application.
     * <p>
     * Uses ProcessHandle to determine the path to the executable file of the current
     * process. This is needed for later functions like auto-start.
     *
     * @return Path to executable application file or null on errors
     */
    public static Path getAppImagePath() {
        try {
            ProcessHandle currentProcess = ProcessHandle.current();
            Optional<String> cmd = currentProcess.info().command();
            if (cmd.isPresent()) {
                return Paths.get(cmd.get());
            }
        } catch (Exception e) {
            log.error("Error determining application path", e);
        }
        return null;
    }

    /**
     * Starts the SSH Auto Connect application.
     * <p>
     * Initializes all components of the application:
     * - Configuration manager
     * - Connection collection
     * - Icons
     * - Cross-platform menu system
     * - Auto-connect for appropriately configured connections
     */
    public void startUp() {
        this.configManager = new SshConfigManager();
        this.connections = new HashMap<>();

        if (!loadIcons()) {
            log.error("Failed to load application icons");
            return;
        }

        if (!initializeMenuSystem()) {
            log.error("Failed to initialize menu system");
            showFallbackWindow();
            return;
        }

        log.info("SSH Auto Connect started successfully");

        // Auto-connect configured connections
        for (SshConfig config : configManager.getAllConfigs()) {
            if (config.isAutoConnect()) {
                toggleConnection(config.getName());
            }
        }

        // Initial menu update
        updateMenu();
    }

    /**
     * Loads application icons from resources.
     *
     * @return true if icons were loaded successfully
     */
    private boolean loadIcons() {
        try {
            connectedImage = ImageIO.read(getClass().getResourceAsStream(CONNECTED_ICON));
            unconnectedImage = ImageIO.read(getClass().getResourceAsStream(UNCONNECTED_ICON));

            if (connectedImage == null || unconnectedImage == null) {
                log.error("Icon files not found in resources");
                return false;
            }

            // Scale icons for different platforms
            connectedImage = scaleIcon(connectedImage);
            unconnectedImage = scaleIcon(unconnectedImage);

            log.info("Application icons loaded successfully");
            return true;

        } catch (IOException e) {
            log.error("Failed to load application icons", e);
            return false;
        }
    }

    /**
     * Scales an icon to the appropriate size for the current platform.
     *
     * @param originalImage The original icon image
     * @return Scaled icon image
     */
    private Image scaleIcon(Image originalImage) {
        // Default size for most platforms
        int size = 16;

        // macOS typically uses larger dock icons
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("mac")) {
            size = 32;
        }

        return originalImage.getScaledInstance(size, size, Image.SCALE_SMOOTH);
    }

    /**
     * Initializes the cross-platform menu system.
     *
     * @return true if menu system was initialized successfully
     */
    private boolean initializeMenuSystem() {
        menuSystem = MenuSystemFactory.createMenuSystem();

        if (menuSystem == null) {
            log.error("No supported menu system found for this platform");
            return false;
        }

        boolean initialized = menuSystem.initialize(connectedImage, unconnectedImage);
        if (initialized) {
            log.info("Menu system initialized successfully");
        } else {
            log.error("Failed to initialize menu system");
        }

        return initialized;
    }

    /**
     * Shows a fallback window when no menu system is available.
     * This provides basic functionality when neither System Tray nor Dock menu is supported.
     */
    private void showFallbackWindow() {
        SwingUtilities.invokeLater(() -> {
            JFrame fallbackFrame = new JFrame("SSH Auto Connect");
            fallbackFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            fallbackFrame.setSize(400, 300);

            JLabel messageLabel = new JLabel("<html><center>SSH Auto Connect is running<br>" +
                    "Menu system not supported on this platform<br>" +
                    "Close this window to exit the application</center></html>");
            messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

            fallbackFrame.add(messageLabel);
            fallbackFrame.setLocationRelativeTo(null);
            fallbackFrame.setVisible(true);

            log.info("Fallback window displayed");
        });
    }

    /**
     * Updates the menu with current configuration and connection states.
     */
    private void updateMenu() {
        if (menuSystem != null) {
            menuSystem.updateMenu(configManager, connections,
                    this::toggleConnection, this::editConfiguration);

            // Update icon based on connection states
            boolean hasActiveConnections = connections.values().stream()
                    .anyMatch(SshConnection::isConnected);
            menuSystem.updateIcon(hasActiveConnections);
        }
    }

    /**
     * Toggles the connection state for a given configuration.
     *
     * @param configName Name of the configuration to toggle
     */
    private void toggleConnection(String configName) {
        SshConfig config = configManager.getConfig(configName);
        if (config == null) {
            log.warn("Configuration not found: {}", configName);
            return;
        }

        SshConnection connection = connections.get(configName);

        if (connection != null && connection.isConnected()) {
            // Disconnect
            connection.disconnect();
            connections.remove(configName);
            log.info("Disconnected from: {}", configName);

            if (menuSystem != null) {
                menuSystem.showNotification("SSH Auto Connect",
                        "Disconnected from " + configName, TrayIcon.MessageType.INFO);
            }
        } else {
            // Connect
            connection = new SshConnection(config);
            if (connection.connect()) {
                connections.put(configName, connection);
                log.info("Connected to: {}", configName);

                if (menuSystem != null) {
                    menuSystem.showNotification("SSH Auto Connect",
                            "Connected to " + configName, TrayIcon.MessageType.INFO);
                }
            } else {
                log.error("Failed to connect to: {}", configName);

                if (menuSystem != null) {
                    menuSystem.showNotification("SSH Auto Connect",
                            "Failed to connect to " + configName, TrayIcon.MessageType.ERROR);
                }
            }
        }

        updateMenu();
    }

    /**
     * Opens the configuration editor for the specified configuration.
     *
     * @param configName Name of configuration to edit, or null to create new
     */
    private void editConfiguration(String configName) {
        SwingUtilities.invokeLater(() -> {
            SshConfig config = configName != null ? configManager.getConfig(configName) : null;
            SshConfigFrame configFrame = new SshConfigFrame(config, updatedConfig -> {
                if (updatedConfig != null) {
                    if (configName == null) {
                        // New configuration
                        configManager.addConfig(updatedConfig);
                    } else {
                        // Updated existing configuration
                        configManager.addConfig(updatedConfig); // This will overwrite the existing one
                    }
                    updateMenu();
                }
            });
            configFrame.setVisible(true);
        });
    }

    /**
     * Shuts down the application and cleans up resources.
     */
    public void shutdown() {
        log.info("Shutting down SSH Auto Connect");

        // Disconnect all connections
        for (SshConnection connection : connections.values()) {
            if (connection.isConnected()) {
                connection.disconnect();
            }
        }
        connections.clear();

        // Cleanup menu system
        if (menuSystem != null) {
            menuSystem.cleanup();
        }

        log.info("SSH Auto Connect shutdown complete");
    }
}
