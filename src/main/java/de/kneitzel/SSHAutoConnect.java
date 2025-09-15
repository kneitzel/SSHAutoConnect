package de.kneitzel;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
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
     * System tray icon for Windows/Linux systems
     */
    private TrayIcon trayIcon;

    /**
     * System tray instance for Windows/Linux systems
     */
    private SystemTray systemTray;

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
     * Flag for macOS detection
     */
    private boolean isMac = false;

    /**
     * Reference to com.apple.eawt.Application for macOS Dock menu (via Reflection)
     */
    private Object macApplication;

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
            log.warn("Konnte System Look and Feel nicht setzen", e);
        }
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
            log.error("Fehler beim Ermitteln des App-Pfads", e);
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
     * - System tray or dock menu
     * - Auto-connect for appropriately configured connections
     */
    public void startUp() {
        this.configManager = new SshConfigManager();
        this.connections = new HashMap<>();
        loadIcons();
        initializeSystemTray();
        log.info("SSH Auto Connect gestartet");
        for (SshConfig config : configManager.getAllConfigs()) {
            if (config.isAutoConnect()) {
                toggleConnection(config.getName());
            }
        }
    }

    /**
     * Loads icon images from resources.
     * <p>
     * Attempts to load connection icons from the classpath. On errors,
     * empty BufferedImage instances are created as fallback.
     */
    private void loadIcons() {
        try {
            connectedImage = ImageIO.read(getClass().getResource(CONNECTED_ICON));
            unconnectedImage = ImageIO.read(getClass().getResource(UNCONNECTED_ICON));
        } catch (IOException e) {
            log.error("Icons konnten nicht geladen werden", e);
            connectedImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            unconnectedImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }
    }

    /**
     * Initializes the System Tray (Windows/Linux) or Dock menu (macOS).
     * <p>
     * Detects the operating system and initializes accordingly:
     * - macOS: Dock menu via com.apple.eawt.Application (Reflection)
     * - Others: System Tray with TrayIcon
     * <p>
     * On errors or unsupported systems, the application is terminated.
     */
    private void initializeSystemTray() {
        String os = System.getProperty("os.name").toLowerCase();
        isMac = os.contains("mac");
        if (isMac) {
            log.info("macOS erkannt – Dock Menü wird gesetzt");
            try {
                Class<?> appClass = Class.forName("com.apple.eawt.Application");
                macApplication = appClass.getMethod("getApplication").invoke(null);
                PopupMenu popup = createPopupMenu();
                appClass.getMethod("setDockMenu", PopupMenu.class).invoke(macApplication, popup);
            } catch (Exception e) {
                log.error("Konnte Dock Menü nicht setzen", e);
            }
        } else {
            if (!SystemTray.isSupported()) {
                log.error("SystemTray wird nicht unterstützt");
                System.exit(1);
            }
            systemTray = SystemTray.getSystemTray();
            trayIcon = new TrayIcon(unconnectedImage, "SSH Auto Connect", createPopupMenu());
            trayIcon.setImageAutoSize(true);
            try {
                systemTray.add(trayIcon);
            } catch (AWTException e) {
                log.error("Fehler beim Hinzufügen des Tray Icons", e);
                System.exit(1);
            }
        }
    }

    /**
     * Creates the popup menu for System Tray or Dock.
     * <p>
     * Builds the main menu with the following structure:
     * - Add Configuration
     * - [Separator]
     * - [Configuration-specific submenus]
     * - [Separator]
     * - Exit
     *
     * @return PopupMenu instance with all menu entries
     */
    private PopupMenu createPopupMenu() {
        PopupMenu popup = new PopupMenu();
        MenuItem addConfig = new MenuItem("Add Configuration");
        addConfig.addActionListener(e -> showAddConfigDialog());
        popup.add(addConfig);
        popup.addSeparator();
        updateConfigMenuItems(popup);
        popup.addSeparator();
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            disconnectAll();
            System.exit(0);
        });
        popup.add(exitItem);
        return popup;
    }

    /**
     * Adds configuration-specific menu entries to the popup menu.
     * <p>
     * For each SSH configuration, a submenu is created with:
     * - Connect/Disconnect
     * - Configure
     * - Open Terminal
     * - Upload/Download Files (only when connection is active)
     * - Port Commands (Start/Stop for port mappings)
     * - Delete Configuration
     *
     * @param popup The PopupMenu to which entries are added
     */
    private void updateConfigMenuItems(PopupMenu popup) {
        for (String configName : configManager.getConfigNames()) {
            Menu configMenu = new Menu(configName);
            SshConnection connection = connections.get(configName);
            MenuItem connectItem = new MenuItem((connection != null && connection.isConnected()) ? "Disconnect" : "Connect");
            connectItem.addActionListener(e -> toggleConnection(configName));
            configMenu.add(connectItem);
            MenuItem configureItem = new MenuItem("Configure");
            configureItem.addActionListener(e -> showConfigDialog(configName));
            configMenu.add(configureItem);
            MenuItem terminalItem = new MenuItem("Open Terminal");
            terminalItem.addActionListener(e -> openTerminal(configName));
            configMenu.add(terminalItem);
            if (connection != null && connection.isConnected()) {
                MenuItem uploadItem = new MenuItem("Upload Files");
                uploadItem.addActionListener(e -> showUpload(configName));
                configMenu.add(uploadItem);
                MenuItem downloadItem = new MenuItem("Download Files");
                downloadItem.addActionListener(e -> showDownload(configName));
                configMenu.add(downloadItem);
            }
            configMenu.addSeparator();
            SshConfig cfg = configManager.getConfig(configName);
            if (cfg != null && !cfg.getPortMappings().isEmpty()) {
                Menu portMenu = new Menu("Port Commands");
                for (PortMapping mapping : cfg.getPortMappings()) {
                    if (mapping.getStartCommand() != null && !mapping.getStartCommand().isEmpty()) {
                        MenuItem startItem = new MenuItem("Start " + mapping);
                        startItem.addActionListener(e -> executeCommand(configName, mapping.getStartCommand()));
                        portMenu.add(startItem);
                    }
                    if (mapping.getStopCommand() != null && !mapping.getStopCommand().isEmpty()) {
                        MenuItem stopItem = new MenuItem("Stop " + mapping);
                        stopItem.addActionListener(e -> executeCommand(configName, mapping.getStopCommand()));
                        portMenu.add(stopItem);
                    }
                }
                configMenu.add(portMenu);
                configMenu.addSeparator();
            }
            MenuItem deleteItem = new MenuItem("Delete Configuration");
            deleteItem.addActionListener(e -> deleteConfiguration(configName));
            configMenu.add(deleteItem);
            popup.add(configMenu);
        }
    }

    /**
     * Shows the dialog for adding a new SSH configuration.
     * <p>
     * Prompts the user for a name for the new configuration,
     * checks for uniqueness and creates a new configuration with
     * default values. Then opens the configuration dialog.
     */
    private void showAddConfigDialog() {
        SwingUtilities.invokeLater(() -> {
            String name = JOptionPane.showInputDialog(null, "Name der neuen Konfiguration:", "Neue Konfiguration", JOptionPane.QUESTION_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                if (configManager.hasConfig(name.trim())) {
                    JOptionPane.showMessageDialog(null, "Eine Konfiguration mit diesem Namen existiert bereits!", "Fehler", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                SshConfig newConfig = new SshConfig(name.trim());
                newConfig.setHostname("example.com");
                newConfig.setUsername("user");
                newConfig.getPortMappings().add(new PortMapping(8080, 8080, "", ""));
                configManager.addConfig(newConfig);
                showConfigDialog(name.trim());
                refreshMenus();
            }
        });
    }

    /**
     * Opens the configuration dialog for a specific SSH configuration.
     *
     * @param configName Name of the configuration to be edited
     */
    private void showConfigDialog(String configName) {
        SwingUtilities.invokeLater(() -> {
            SshConfig config = configManager.getConfig(configName);
            if (config != null) {
                SshConfigFrame configFrame = new SshConfigFrame(config, this::onConfigUpdated);
                configFrame.setVisible(true);
            }
        });
    }

    /**
     * Callback method for configuration updates.
     * <p>
     * Called when a configuration has been changed in the dialog.
     * Saves the changes and updates the menus.
     *
     * @param config The updated SSH configuration
     */
    private void onConfigUpdated(SshConfig config) {
        configManager.updateConfig(config);
        refreshMenus();
    }

    /**
     * Toggles an SSH connection on or off.
     * <p>
     * For existing connections, they are disconnected; for missing connections,
     * a new connection is established. Updates the tray icon and menus
     * accordingly.
     *
     * @param configName Name of the configuration for the connection
     */
    private void toggleConnection(String configName) {
        SshConnection connection = connections.get(configName);
        if (connection != null && connection.isConnected()) {
            connection.disconnect();
            connections.remove(configName);
            if (trayIcon != null) {
                trayIcon.displayMessage("SSH Auto Connect", "Verbindung zu " + configName + " getrennt", TrayIcon.MessageType.INFO);
            }
            log.info("Verbindung zu {} getrennt", configName);
        } else {
            SshConfig config = configManager.getConfig(configName);
            if (config != null) {
                connection = new SshConnection(config);
                if (connection.connect()) {
                    connections.put(configName, connection);
                    if (trayIcon != null) {
                        trayIcon.displayMessage("SSH Auto Connect", "Verbindung zu " + configName + " hergestellt", TrayIcon.MessageType.INFO);
                    }
                    log.info("Verbindung zu {} hergestellt", configName);
                } else {
                    showErrorMessage("Verbindung zu " + configName + " konnte nicht hergestellt werden!");
                }
            }
        }
        updateTrayIcon();
        refreshMenus();
    }

    /**
     * Executes a command on an SSH connection.
     *
     * @param configName Name of the configuration with the active connection
     * @param command    The SSH command to execute
     */
    private void executeCommand(String configName, String command) {
        SshConnection connection = connections.get(configName);
        if (connection != null && connection.isConnected()) {
            connection.executeCommand(command);
        } else {
            showErrorMessage("Keine aktive Verbindung zu " + configName);
        }
    }

    /**
     * Opens a terminal for an SSH connection.
     *
     * @param configName Name of the configuration for the terminal
     */
    private void openTerminal(String configName) {
        SshConnection connection = connections.get(configName);
        if (connection != null) {
            connection.openTerminal();
        } else {
            showErrorMessage("Keine aktive Verbindung zu " + configName);
        }
    }

    /**
     * Opens the upload dialog for file transfers.
     *
     * @param configName Name of the configuration with active connection
     */
    private void showUpload(String configName) {
        SshConnection connection = connections.get(configName);
        if (connection != null && connection.isConnected()) {
            SwingUtilities.invokeLater(() -> {
                SshConfig config = configManager.getConfig(configName);
                UploadFrame uploadFrame = new UploadFrame(() -> connection.getSession(), config.getUploadDirectory());
                uploadFrame.setVisible(true);
            });
        } else {
            showErrorMessage("Keine aktive Verbindung zu " + configName);
        }
    }

    /**
     * Opens the download dialog for file transfers.
     *
     * @param configName Name of the configuration with active connection
     */
    private void showDownload(String configName) {
        SshConnection connection = connections.get(configName);
        if (connection != null && connection.isConnected()) {
            SwingUtilities.invokeLater(() -> {
                SshConfig config = configManager.getConfig(configName);
                DownloadFrame downloadFrame = new DownloadFrame(() -> connection.getSession(), config.getUploadDirectory());
                downloadFrame.setVisible(true);
            });
        } else {
            showErrorMessage("Keine aktive Verbindung zu " + configName);
        }
    }

    /**
     * Deletes an SSH configuration after user confirmation.
     * <p>
     * Shows a confirmation dialog and deletes the configuration upon approval.
     * Disconnects any active connection beforehand.
     *
     * @param configName Name of the configuration to be deleted
     */
    private void deleteConfiguration(String configName) {
        int result = JOptionPane.showConfirmDialog(null, "Möchten Sie die Konfiguration '" + configName + "' wirklich löschen?", "Konfiguration löschen", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            SshConnection connection = connections.get(configName);
            if (connection != null && connection.isConnected()) {
                connection.disconnect();
                connections.remove(configName);
            }
            configManager.removeConfig(configName);
            refreshMenus();
            updateTrayIcon();
        }
    }

    /**
     * Disconnects all active SSH connections.
     * <p>
     * Called when exiting the application to properly release all resources.
     */
    private void disconnectAll() {
        for (SshConnection connection : connections.values()) {
            if (connection.isConnected()) {
                connection.disconnect();
            }
        }
        connections.clear();
    }

    /**
     * Updates all menus (System Tray and Dock).
     * <p>
     * Recreates the popup menu and sets it for both the System Tray
     * (Windows/Linux) and the Dock menu (macOS).
     */
    private void refreshMenus() {
        SwingUtilities.invokeLater(() -> {
            PopupMenu newMenu = createPopupMenu();
            if (trayIcon != null) {
                trayIcon.setPopupMenu(newMenu);
            }
            if (isMac && macApplication != null) {
                try {
                    macApplication.getClass().getMethod("setDockMenu", PopupMenu.class).invoke(macApplication, newMenu);
                } catch (Exception e) {
                    log.error("Aktualisieren des Dock Menüs fehlgeschlagen", e);
                }
            }
        });
    }

    /**
     * Updates the System Tray icon based on connection status.
     * <p>
     * Shows the "connected" icon when at least one SSH connection is active,
     * otherwise the "disconnected" icon. Ignored on macOS without TrayIcon.
     */
    private void updateTrayIcon() {
        if (trayIcon == null) return; // Auf macOS ohne TrayIcon ignorieren
        boolean anyConnected = connections.values().stream().anyMatch(SshConnection::isConnected);
        trayIcon.setImage(anyConnected ? connectedImage : unconnectedImage);
    }

    /**
     * Shows an error message in a dialog.
     *
     * @param message The error message to display
     */
    private void showErrorMessage(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, message, "Fehler", JOptionPane.ERROR_MESSAGE));
    }
}
