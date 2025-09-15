package de.kneitzel;

import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;

/**
 * System Tray implementation of the MenuSystem interface.
 * Used on Windows and Linux platforms.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 */
@Slf4j
public class SystemTrayMenuSystem implements MenuSystem {

    private SystemTray systemTray;

    private TrayIcon trayIcon;

    private Image connectedIcon;

    private Image unconnectedIcon;

    @Override
    public boolean initialize(Image connectedIcon, Image unconnectedIcon) {
        this.connectedIcon = connectedIcon;
        this.unconnectedIcon = unconnectedIcon;

        if (!SystemTray.isSupported()) {
            log.error("System Tray not supported on this platform");
            return false;
        }

        try {
            systemTray = SystemTray.getSystemTray();
            trayIcon = new TrayIcon(unconnectedIcon, "SSH Auto Connect");
            trayIcon.setImageAutoSize(true);

            systemTray.add(trayIcon);
            log.info("System Tray initialized successfully");
            return true;

        } catch (AWTException e) {
            log.error("Failed to initialize System Tray", e);
            return false;
        }
    }

    @Override
    public void updateMenu(SshConfigManager configManager,
                           Map<String, SshConnection> connections,
                           Consumer<String> connectionToggler,
                           Consumer<String> configEditor) {

        PopupMenu popupMenu = new PopupMenu();

        // Add connection entries
        for (String configName : configManager.getConfigNames()) {
            SshConfig config = configManager.getConfig(configName);
            boolean isConnected = connections.containsKey(configName) &&
                    connections.get(configName).isConnected();

            Menu configMenu = new Menu(configName + (isConnected ? " ✓" : ""));

            // Connect/Disconnect item
            MenuItem connectItem = new MenuItem(isConnected ? "Disconnect" : "Connect");
            connectItem.addActionListener(e -> connectionToggler.accept(configName));
            configMenu.add(connectItem);

            if (isConnected) {
                SshConnection connection = connections.get(configName);
                addConnectionMenuItems(configMenu, connection);
            }

            configMenu.addSeparator();

            // Edit configuration item
            MenuItem editItem = new MenuItem("Edit Configuration");
            editItem.addActionListener(e -> configEditor.accept(configName));
            configMenu.add(editItem);

            popupMenu.add(configMenu);
        }

        popupMenu.addSeparator();

        // Add configuration item
        MenuItem addConfigItem = new MenuItem("Add New Configuration");
        addConfigItem.addActionListener(e -> configEditor.accept(null));
        popupMenu.add(addConfigItem);

        popupMenu.addSeparator();

        // Exit item
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        popupMenu.add(exitItem);

        trayIcon.setPopupMenu(popupMenu);
    }

    /**
     * Adds connection-specific menu items when a connection is active.
     *
     * @param menu       The menu to add items to
     * @param connection The active SSH connection
     */
    private void addConnectionMenuItems(Menu menu, SshConnection connection) {
        SshConfig config = connection.getConfig();

        // Terminal item
        MenuItem terminalItem = new MenuItem("Open Terminal");
        terminalItem.addActionListener(e -> openTerminal(connection));
        menu.add(terminalItem);

        // Upload item
        MenuItem uploadItem = new MenuItem("Upload File");
        uploadItem.addActionListener(e -> openUploadDialog(connection));
        menu.add(uploadItem);

        // Download item
        MenuItem downloadItem = new MenuItem("Download File");
        downloadItem.addActionListener(e -> openDownloadDialog(connection));
        menu.add(downloadItem);

        // Port forwarding items
        if (!config.getPortMappings().isEmpty()) {
            menu.addSeparator();
            Menu portMenu = new Menu("Port Forwardings");

            for (PortMapping mapping : config.getPortMappings()) {
                MenuItem portItem = new MenuItem(mapping.toString());
                portItem.setEnabled(false); // Just for display
                portMenu.add(portItem);
            }
            menu.add(portMenu);
        }
    }

    /**
     * Opens a terminal for the given SSH connection.
     *
     * @param connection The SSH connection to open terminal for
     */
    private void openTerminal(SshConnection connection) {
        try {
            SshConfig config = connection.getConfig();
            String command = String.format("%s -e ssh %s@%s -p %d",
                    config.getXtermCommand(),
                    config.getUsername(),
                    config.getHostname(),
                    config.getPort());

            Runtime.getRuntime().exec(command);
            log.info("Terminal opened for connection: {}", config.getName());

        } catch (Exception e) {
            log.error("Failed to open terminal", e);
        }
    }

    /**
     * Opens the upload dialog for the given SSH connection.
     *
     * @param connection The SSH connection for file upload
     */
    private void openUploadDialog(SshConnection connection) {
        SwingUtilities.invokeLater(() -> {
            UploadFrame uploadFrame = new UploadFrame(
                    () -> connection.getSession(),
                    connection.getConfig().getUploadDirectory()
            );
            uploadFrame.setVisible(true);
        });
    }

    /**
     * Opens the download dialog for the given SSH connection.
     *
     * @param connection The SSH connection for file download
     */
    private void openDownloadDialog(SshConnection connection) {
        SwingUtilities.invokeLater(() -> {
            DownloadFrame downloadFrame = new DownloadFrame(
                    () -> connection.getSession(),
                    connection.getConfig().getUploadDirectory()
            );
            downloadFrame.setVisible(true);
        });
    }

    @Override
    public void updateIcon(boolean hasActiveConnections) {
        if (trayIcon != null) {
            trayIcon.setImage(hasActiveConnections ? connectedIcon : unconnectedIcon);
        }
    }

    @Override
    public void showNotification(String title, String message, TrayIcon.MessageType messageType) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, messageType);
        }
    }

    @Override
    public boolean isSupported() {
        return SystemTray.isSupported();
    }

    @Override
    public void cleanup() {
        if (trayIcon != null && systemTray != null) {
            systemTray.remove(trayIcon);
        }
    }
}
